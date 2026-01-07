package com.scanium.app.enrichment

import android.graphics.Bitmap
import android.util.Log
import com.scanium.app.BuildConfig
import com.scanium.app.logging.CorrelationIds
import com.scanium.app.network.security.RequestSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Repository for item enrichment via backend.
 *
 * This repository calls the /v1/items/enrich endpoint to submit images
 * for enrichment (vision extraction, attribute normalization, draft generation)
 * and polls for results.
 *
 * @param apiKeyProvider Provider for the API key
 * @param getDeviceId Provider for the device ID (for rate limiting)
 */
class EnrichmentRepository(
    private val apiKeyProvider: () -> String? = { null },
    private val getDeviceId: () -> String = { "" },
) {
    companion object {
        private const val TAG = "EnrichmentRepo"
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val JPEG_QUALITY = 85
        private const val MAX_IMAGE_DIMENSION = 1024

        // Polling configuration
        private const val POLL_INITIAL_DELAY_MS = 500L
        private const val POLL_MAX_DELAY_MS = 5000L
        private const val POLL_BACKOFF_FACTOR = 1.5
        private const val POLL_MAX_ATTEMPTS = 30
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .apply {
            // Certificate pinning for MITM protection
            val certificatePin = BuildConfig.SCANIUM_API_CERTIFICATE_PIN
            val baseUrl = BuildConfig.SCANIUM_API_BASE_URL
            if (certificatePin.isNotBlank() && baseUrl.isNotBlank()) {
                val host = runCatching { URI(baseUrl).host }.getOrNull()
                if (!host.isNullOrBlank()) {
                    certificatePinner(
                        CertificatePinner.Builder()
                            .add(host, certificatePin)
                            .build()
                    )
                }
            }
        }
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Submit an item for enrichment and poll until complete.
     *
     * @param bitmap The image to analyze
     * @param itemId Item ID to associate with the enrichment
     * @param onProgress Callback for progress updates
     * @return Final enrichment status
     */
    suspend fun enrichItem(
        bitmap: Bitmap,
        itemId: String,
        onProgress: (EnrichmentStatus) -> Unit = {},
    ): Result<EnrichmentStatus> = withContext(Dispatchers.IO) {
        // Step 1: Submit enrichment request
        val submitResult = submitEnrichment(bitmap, itemId)
        if (submitResult.isFailure) {
            return@withContext Result.failure(submitResult.exceptionOrNull()!!)
        }

        val requestId = submitResult.getOrThrow()

        // Step 2: Poll for status until complete
        pollForCompletion(requestId, onProgress)
    }

    /**
     * Submit an item for enrichment (non-blocking).
     *
     * @param bitmap The image to analyze
     * @param itemId Item ID to associate with the enrichment
     * @return Request ID for polling
     */
    suspend fun submitEnrichment(
        bitmap: Bitmap,
        itemId: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        val baseUrlRaw = BuildConfig.SCANIUM_API_BASE_URL
        val baseUrl = baseUrlRaw.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(
                EnrichmentException(
                    errorCode = "CONFIG_ERROR",
                    userMessage = "Enrichment service not configured.",
                    retryable = false,
                )
            )

        val apiKeyRaw = apiKeyProvider()
        val apiKey = apiKeyRaw?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(
                EnrichmentException(
                    errorCode = "CONFIG_ERROR",
                    userMessage = "API key is missing.",
                    retryable = false,
                )
            )

        val endpoint = "${baseUrl.trimEnd('/')}/v1/items/enrich"
        val correlationId = CorrelationIds.currentClassificationSessionId()

        try {
            // Prepare image
            val processedBitmap = resizeIfNeeded(bitmap)
            val imageBytes = processedBitmap.toJpegBytes()

            // Build multipart request
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    name = "image",
                    filename = "scan.jpg",
                    body = imageBytes.toRequestBody("image/jpeg".toMediaType()),
                )
                .addFormDataPart("itemId", itemId)
                .build()

            val httpRequestBuilder = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .header("X-API-Key", apiKey)
                .header("X-Scanium-Correlation-Id", correlationId)
                .header("X-Client", "Scanium-Android")
                .header("X-App-Version", BuildConfig.VERSION_NAME)

            // Add HMAC signature
            RequestSigner.addSignatureHeaders(
                builder = httpRequestBuilder,
                apiKey = apiKey,
                params = mapOf("itemId" to itemId),
                binaryContentSize = imageBytes.size.toLong(),
            )

            // Add device ID
            val deviceId = getDeviceId()
            if (deviceId.isNotBlank()) {
                httpRequestBuilder.header("X-Scanium-Device-Id", hashDeviceId(deviceId))
            }

            val httpRequest = httpRequestBuilder.build()
            val startTime = System.currentTimeMillis()

            client.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string()
                val latencyMs = System.currentTimeMillis() - startTime

                Log.d(TAG, "ENRICH: Submit response ${response.code} in ${latencyMs}ms")

                when {
                    response.code == 202 -> {
                        if (responseBody == null) {
                            return@use Result.failure(
                                EnrichmentException(
                                    errorCode = "EMPTY_RESPONSE",
                                    userMessage = "No response received.",
                                    retryable = true,
                                )
                            )
                        }

                        val apiResponse = json.decodeFromString<EnrichSubmitResponse>(responseBody)

                        if (!apiResponse.success || apiResponse.requestId == null) {
                            return@use Result.failure(
                                EnrichmentException(
                                    errorCode = apiResponse.error?.code ?: "SUBMIT_FAILED",
                                    userMessage = apiResponse.error?.message ?: "Failed to submit enrichment.",
                                    retryable = false,
                                )
                            )
                        }

                        Log.d(TAG, "ENRICH: Submitted successfully, requestId=${apiResponse.requestId}")
                        Result.success(apiResponse.requestId)
                    }

                    response.code == 401 -> {
                        Log.e(TAG, "ENRICH: 401 UNAUTHORIZED")
                        Result.failure(
                            EnrichmentException(
                                errorCode = "UNAUTHORIZED",
                                userMessage = "Authentication failed.",
                                retryable = false,
                            )
                        )
                    }

                    response.code == 429 -> {
                        Log.w(TAG, "ENRICH: Rate limited (429)")
                        Result.failure(
                            EnrichmentException(
                                errorCode = "RATE_LIMITED",
                                userMessage = "Too many requests. Please wait.",
                                retryable = true,
                            )
                        )
                    }

                    response.code == 413 -> {
                        Log.e(TAG, "ENRICH: Image too large (413)")
                        Result.failure(
                            EnrichmentException(
                                errorCode = "IMAGE_TOO_LARGE",
                                userMessage = "Image is too large.",
                                retryable = false,
                            )
                        )
                    }

                    else -> {
                        Log.e(TAG, "ENRICH: Unexpected HTTP ${response.code}")
                        Result.failure(
                            EnrichmentException(
                                errorCode = "UNKNOWN_ERROR",
                                userMessage = "Something went wrong.",
                                retryable = true,
                            )
                        )
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "ENRICH: Request timed out", e)
            Result.failure(
                EnrichmentException(
                    errorCode = "TIMEOUT",
                    userMessage = "Request timed out.",
                    retryable = true,
                )
            )
        } catch (e: UnknownHostException) {
            Log.e(TAG, "ENRICH: Offline", e)
            Result.failure(
                EnrichmentException(
                    errorCode = "OFFLINE",
                    userMessage = "No network connection.",
                    retryable = true,
                )
            )
        } catch (e: IOException) {
            Log.e(TAG, "ENRICH: Network error", e)
            Result.failure(
                EnrichmentException(
                    errorCode = "NETWORK_ERROR",
                    userMessage = "Network error. Please try again.",
                    retryable = true,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "ENRICH: Unexpected error", e)
            Result.failure(
                EnrichmentException(
                    errorCode = "UNKNOWN_ERROR",
                    userMessage = "Something went wrong.",
                    retryable = false,
                )
            )
        }
    }

    /**
     * Get the status of an enrichment request.
     *
     * @param requestId The request ID to check
     * @return Current enrichment status
     */
    suspend fun getStatus(requestId: String): Result<EnrichmentStatus> = withContext(Dispatchers.IO) {
        val baseUrlRaw = BuildConfig.SCANIUM_API_BASE_URL
        val baseUrl = baseUrlRaw.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(
                EnrichmentException(
                    errorCode = "CONFIG_ERROR",
                    userMessage = "Enrichment service not configured.",
                    retryable = false,
                )
            )

        val apiKeyRaw = apiKeyProvider()
        val apiKey = apiKeyRaw?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(
                EnrichmentException(
                    errorCode = "CONFIG_ERROR",
                    userMessage = "API key is missing.",
                    retryable = false,
                )
            )

        val endpoint = "${baseUrl.trimEnd('/')}/v1/items/enrich/status/$requestId"

        try {
            val httpRequest = Request.Builder()
                .url(endpoint)
                .get()
                .header("X-API-Key", apiKey)
                .header("X-Client", "Scanium-Android")
                .build()

            client.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string()

                when {
                    response.isSuccessful -> {
                        if (responseBody == null) {
                            return@use Result.failure(
                                EnrichmentException(
                                    errorCode = "EMPTY_RESPONSE",
                                    userMessage = "No response received.",
                                    retryable = true,
                                )
                            )
                        }

                        val apiResponse = json.decodeFromString<EnrichStatusResponse>(responseBody)

                        if (!apiResponse.success || apiResponse.status == null) {
                            return@use Result.failure(
                                EnrichmentException(
                                    errorCode = apiResponse.error?.code ?: "STATUS_FAILED",
                                    userMessage = apiResponse.error?.message ?: "Failed to get status.",
                                    retryable = false,
                                )
                            )
                        }

                        Result.success(apiResponse.status)
                    }

                    response.code == 404 -> {
                        Result.failure(
                            EnrichmentException(
                                errorCode = "NOT_FOUND",
                                userMessage = "Request not found.",
                                retryable = false,
                            )
                        )
                    }

                    response.code == 401 -> {
                        Result.failure(
                            EnrichmentException(
                                errorCode = "UNAUTHORIZED",
                                userMessage = "Authentication failed.",
                                retryable = false,
                            )
                        )
                    }

                    else -> {
                        Result.failure(
                            EnrichmentException(
                                errorCode = "UNKNOWN_ERROR",
                                userMessage = "Something went wrong.",
                                retryable = true,
                            )
                        )
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(
                EnrichmentException(
                    errorCode = "TIMEOUT",
                    userMessage = "Request timed out.",
                    retryable = true,
                )
            )
        } catch (e: IOException) {
            Result.failure(
                EnrichmentException(
                    errorCode = "NETWORK_ERROR",
                    userMessage = "Network error.",
                    retryable = true,
                )
            )
        } catch (e: Exception) {
            Result.failure(
                EnrichmentException(
                    errorCode = "UNKNOWN_ERROR",
                    userMessage = "Something went wrong.",
                    retryable = false,
                )
            )
        }
    }

    /**
     * Poll for enrichment completion with exponential backoff.
     */
    private suspend fun pollForCompletion(
        requestId: String,
        onProgress: (EnrichmentStatus) -> Unit,
    ): Result<EnrichmentStatus> {
        var delayMs = POLL_INITIAL_DELAY_MS
        var attempt = 0

        while (attempt < POLL_MAX_ATTEMPTS) {
            delay(delayMs)
            attempt++

            val statusResult = getStatus(requestId)

            if (statusResult.isFailure) {
                val exception = statusResult.exceptionOrNull()
                if (exception is EnrichmentException && !exception.retryable) {
                    return statusResult
                }
                // Retryable error - continue polling
                delayMs = (delayMs * POLL_BACKOFF_FACTOR).toLong().coerceAtMost(POLL_MAX_DELAY_MS)
                continue
            }

            val status = statusResult.getOrThrow()
            onProgress(status)

            if (status.isComplete) {
                Log.d(TAG, "ENRICH: Complete after $attempt polls, stage=${status.stage}")
                return Result.success(status)
            }

            // Increase delay with backoff
            delayMs = (delayMs * POLL_BACKOFF_FACTOR).toLong().coerceAtMost(POLL_MAX_DELAY_MS)
        }

        // Timeout after max attempts
        return Result.failure(
            EnrichmentException(
                errorCode = "POLL_TIMEOUT",
                userMessage = "Enrichment took too long.",
                retryable = true,
            )
        )
    }

    private fun resizeIfNeeded(bitmap: Bitmap): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= MAX_IMAGE_DIMENSION) {
            return bitmap
        }

        val scale = MAX_IMAGE_DIMENSION.toFloat() / maxDim
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun Bitmap.toJpegBytes(): ByteArray {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return stream.toByteArray()
    }

    private fun hashDeviceId(deviceId: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(deviceId.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            deviceId
        }
    }
}
