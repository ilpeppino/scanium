package com.scanium.app.ml

import android.graphics.Bitmap
import com.scanium.app.BuildConfig
import com.scanium.app.logging.CorrelationIds
import com.scanium.app.logging.ScaniumLog
import com.scanium.app.network.security.RequestSigner
import com.scanium.shared.core.models.items.VisionAttributes
import com.scanium.shared.core.models.items.VisionColor
import com.scanium.shared.core.models.items.VisionLabel
import com.scanium.shared.core.models.items.VisionLogo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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
 * Response from the vision insights endpoint.
 */
@Serializable
data class VisionInsightsResponse(
    val success: Boolean,
    val requestId: String? = null,
    val correlationId: String? = null,
    val ocrSnippets: List<String> = emptyList(),
    val logoHints: List<LogoHintResponse> = emptyList(),
    val dominantColors: List<ColorResponse> = emptyList(),
    val labelHints: List<String> = emptyList(),
    val suggestedLabel: String? = null,
    val categoryHint: String? = null,
    val error: VisionInsightsError? = null,
)

@Serializable
data class LogoHintResponse(
    val name: String,
    val confidence: Float,
)

@Serializable
data class ColorResponse(
    val name: String,
    val hex: String,
    val pct: Float,
)

@Serializable
data class VisionInsightsError(
    val code: String,
    val message: String,
    val correlationId: String? = null,
)

/**
 * Exception thrown when vision insights extraction fails.
 */
class VisionInsightsException(
    val errorCode: String,
    val userMessage: String,
    val retryable: Boolean = false,
) : Exception(userMessage)

/**
 * Result of vision insights extraction that can be applied to an item.
 */
data class VisionInsightsResult(
    /** VisionAttributes to populate on the item */
    val visionAttributes: VisionAttributes,
    /** Suggested label/name derived from OCR + brand */
    val suggestedLabel: String?,
    /** Category hint from labels */
    val categoryHint: String?,
    /** Request ID for debugging */
    val requestId: String?,
)

/**
 * Repository for extracting vision insights from images.
 *
 * This repository calls the /v1/vision/insights endpoint to extract:
 * - OCR text snippets
 * - Logo/brand detection
 * - Dominant colors
 * - Label hints
 *
 * The results are used for immediate prefill of item attributes
 * when a user scans a product photo.
 *
 * @param apiKeyProvider Provider for the API key
 * @param getDeviceId Provider for the device ID (for rate limiting)
 */
class VisionInsightsRepository(
    private val apiKeyProvider: () -> String? = { null },
    private val getDeviceId: () -> String = { "" },
) {
    companion object {
        private const val TAG = "VisionInsightsRepo"
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 15L
        private const val JPEG_QUALITY = 85
        private const val MAX_IMAGE_DIMENSION = 1024 // Resize large images
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
     * Extract vision insights from a bitmap image.
     *
     * @param bitmap The image to analyze
     * @param itemId Optional item ID to associate with the extraction
     * @return Result containing VisionInsightsResult or failure
     */
    suspend fun extractInsights(
        bitmap: Bitmap,
        itemId: String? = null,
    ): Result<VisionInsightsResult> = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.SCANIUM_API_BASE_URL.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(
                VisionInsightsException(
                    errorCode = "CONFIG_ERROR",
                    userMessage = "Vision service not configured.",
                    retryable = false,
                )
            )

        val apiKey = apiKeyProvider()?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(
                VisionInsightsException(
                    errorCode = "CONFIG_ERROR",
                    userMessage = "API key is missing.",
                    retryable = false,
                )
            )

        val endpoint = "${baseUrl.trimEnd('/')}/v1/vision/insights"
        val correlationId = CorrelationIds.currentClassificationSessionId()

        try {
            // Prepare image - resize if needed and compress to JPEG
            val processedBitmap = resizeIfNeeded(bitmap)
            val imageBytes = processedBitmap.toJpegBytes()

            // Build multipart request
            val requestBodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    name = "image",
                    filename = "scan.jpg",
                    body = imageBytes.toRequestBody("image/jpeg".toMediaType()),
                )

            if (itemId != null) {
                requestBodyBuilder.addFormDataPart("itemId", itemId)
            }

            val requestBody = requestBodyBuilder.build()

            val httpRequestBuilder = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .header("X-API-Key", apiKey)
                .header("X-Scanium-Correlation-Id", correlationId)
                .header("X-Client", "Scanium-Android")
                .header("X-App-Version", BuildConfig.VERSION_NAME)

            // Add HMAC signature for replay protection
            RequestSigner.addSignatureHeaders(
                builder = httpRequestBuilder,
                apiKey = apiKey,
                params = itemId?.let { mapOf("itemId" to it) } ?: emptyMap(),
                binaryContentSize = imageBytes.size.toLong(),
            )

            // Add device ID for rate limiting (hashed)
            val deviceId = getDeviceId()
            if (deviceId.isNotBlank()) {
                httpRequestBuilder.header("X-Scanium-Device-Id", hashDeviceId(deviceId))
            }

            val httpRequest = httpRequestBuilder.build()

            ScaniumLog.d(TAG, "Extracting vision insights correlationId=$correlationId")
            val startTime = System.currentTimeMillis()

            client.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string()
                val latencyMs = System.currentTimeMillis() - startTime

                when {
                    response.isSuccessful -> {
                        if (responseBody == null) {
                            return@use Result.failure(
                                VisionInsightsException(
                                    errorCode = "EMPTY_RESPONSE",
                                    userMessage = "No response received.",
                                    retryable = true,
                                )
                            )
                        }

                        val apiResponse = json.decodeFromString<VisionInsightsResponse>(responseBody)

                        if (!apiResponse.success) {
                            return@use Result.failure(
                                VisionInsightsException(
                                    errorCode = apiResponse.error?.code ?: "EXTRACTION_FAILED",
                                    userMessage = apiResponse.error?.message ?: "Extraction failed.",
                                    retryable = false,
                                )
                            )
                        }

                        val result = parseResponse(apiResponse)
                        ScaniumLog.i(
                            TAG,
                            "Vision insights extracted latency=${latencyMs}ms ocrSnippets=${apiResponse.ocrSnippets.size} logos=${apiResponse.logoHints.size} correlationId=$correlationId"
                        )

                        Result.success(result)
                    }

                    response.code == 401 -> {
                        Result.failure(
                            VisionInsightsException(
                                errorCode = "UNAUTHORIZED",
                                userMessage = "Authentication failed.",
                                retryable = false,
                            )
                        )
                    }

                    response.code == 429 -> {
                        ScaniumLog.w(TAG, "Rate limited correlationId=$correlationId")
                        Result.failure(
                            VisionInsightsException(
                                errorCode = "RATE_LIMITED",
                                userMessage = "Too many requests. Please wait.",
                                retryable = true,
                            )
                        )
                    }

                    response.code == 413 -> {
                        Result.failure(
                            VisionInsightsException(
                                errorCode = "IMAGE_TOO_LARGE",
                                userMessage = "Image is too large.",
                                retryable = false,
                            )
                        )
                    }

                    response.code == 503 -> {
                        ScaniumLog.w(TAG, "Service unavailable correlationId=$correlationId")
                        Result.failure(
                            VisionInsightsException(
                                errorCode = "SERVICE_UNAVAILABLE",
                                userMessage = "Vision service temporarily unavailable.",
                                retryable = true,
                            )
                        )
                    }

                    else -> {
                        ScaniumLog.e(TAG, "Unexpected error: ${response.code}")
                        Result.failure(
                            VisionInsightsException(
                                errorCode = "UNKNOWN_ERROR",
                                userMessage = "Something went wrong.",
                                retryable = true,
                            )
                        )
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            ScaniumLog.w(TAG, "Timeout extracting vision insights", e)
            Result.failure(
                VisionInsightsException(
                    errorCode = "TIMEOUT",
                    userMessage = "Request timed out.",
                    retryable = true,
                )
            )
        } catch (e: UnknownHostException) {
            ScaniumLog.w(TAG, "No network for vision insights", e)
            Result.failure(
                VisionInsightsException(
                    errorCode = "OFFLINE",
                    userMessage = "No network connection.",
                    retryable = true,
                )
            )
        } catch (e: IOException) {
            ScaniumLog.e(TAG, "Network error extracting vision insights", e)
            Result.failure(
                VisionInsightsException(
                    errorCode = "NETWORK_ERROR",
                    userMessage = "Network error. Please try again.",
                    retryable = true,
                )
            )
        } catch (e: Exception) {
            ScaniumLog.e(TAG, "Failed to extract vision insights", e)
            Result.failure(
                VisionInsightsException(
                    errorCode = "UNKNOWN_ERROR",
                    userMessage = "Something went wrong.",
                    retryable = false,
                )
            )
        }
    }

    private fun parseResponse(response: VisionInsightsResponse): VisionInsightsResult {
        // Convert to VisionAttributes
        val visionAttributes = VisionAttributes(
            colors = response.dominantColors.map { color ->
                VisionColor(
                    name = color.name,
                    hex = color.hex,
                    score = color.pct / 100f, // Convert percentage to 0-1 score
                )
            },
            ocrText = response.ocrSnippets.joinToString("\n").takeIf { it.isNotBlank() },
            logos = response.logoHints.map { logo ->
                VisionLogo(
                    name = logo.name,
                    score = logo.confidence,
                )
            },
            labels = response.labelHints.map { label ->
                VisionLabel(
                    name = label,
                    score = 1.0f, // Labels from insights don't have scores
                )
            },
            brandCandidates = response.logoHints.map { it.name },
            modelCandidates = emptyList(), // Model detection not yet available
        )

        return VisionInsightsResult(
            visionAttributes = visionAttributes,
            suggestedLabel = response.suggestedLabel,
            categoryHint = response.categoryHint,
            requestId = response.requestId,
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
