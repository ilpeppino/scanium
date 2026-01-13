package com.scanium.app.ml.classification

import android.graphics.Bitmap
import android.util.Log
import com.scanium.app.BuildConfig
import com.scanium.app.config.SecureApiKeyStore
import com.scanium.app.logging.CorrelationIds
import com.scanium.app.network.security.RequestSigner
import com.scanium.shared.core.models.config.CloudClassifierConfig
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.min

/**
 * Pure API layer for cloud classification requests.
 *
 * This class handles:
 * - HTTP request building (multipart form, headers, authentication)
 * - Request execution with retries
 * - Response parsing
 * - Error categorization (retryable vs non-retryable)
 * - Backoff delay calculation
 *
 * Separated from CloudClassifier to enable:
 * - Easy testing of API logic in isolation
 * - Experimentation with different retry strategies
 * - Clean separation of concerns (API vs. telemetry vs. coordination)
 *
 * @param client OkHttpClient configured with appropriate timeouts and security
 * @param apiKeyStore Secure storage for API key
 * @param domainPackId Domain pack to use for classification
 * @param maxAttempts Maximum number of retry attempts
 * @param baseDelayMs Base delay for exponential backoff
 * @param maxDelayMs Maximum delay for exponential backoff
 */
class CloudClassifierApi(
    private val client: OkHttpClient,
    private val apiKeyStore: SecureApiKeyStore?,
    private val domainPackId: String = "home_resale",
    private val maxAttempts: Int = 3,
    private val baseDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 8_000L,
) {
    companion object {
        private const val TAG = "CloudClassifierApi"
        private const val JPEG_QUALITY = 85
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /**
     * Execute classification request with retries.
     *
     * @param bitmap Image to classify
     * @param config API configuration
     * @param onAttempt Callback for each attempt (attempt number, error or null)
     * @return ApiResult with response or error
     */
    suspend fun classify(
        bitmap: Bitmap,
        config: CloudClassifierConfig,
        onAttempt: (suspend (Int, ApiError?) -> Unit)? = null,
    ): ApiResult {
        if (!config.isConfigured) {
            return ApiResult.ConfigError("Cloud classification disabled")
        }

        val endpoint = "${config.baseUrl.trimEnd('/')}/v1/classify?enrichAttributes=true"
        val correlationId = CorrelationIds.currentClassificationSessionId()
        val imageBytes = bitmap.toJpegBytes()

        Log.d(TAG, "Classifying endpoint=$endpoint domainPack=$domainPackId correlationId=$correlationId")

        var attempt = 1
        var lastError: ApiError? = null

        while (attempt <= maxAttempts) {
            try {
                val response = executeRequest(endpoint, imageBytes, config.apiKey, correlationId)

                if (response is ApiResult.Success) {
                    onAttempt?.invoke(attempt, null)
                    return response
                }

                // Handle error response
                val error = (response as? ApiResult.Error)?.error
                lastError = error

                // Check if retryable
                if (error != null && error.isRetryable && attempt < maxAttempts) {
                    Log.w(TAG, "Retryable error ${error.statusCode} attempt=$attempt correlationId=$correlationId")
                    onAttempt?.invoke(attempt, error)
                    delay(calculateBackoffDelay(attempt))
                    attempt++
                    continue
                }

                // Non-retryable or last attempt
                onAttempt?.invoke(attempt, error)
                return response
            } catch (e: SocketTimeoutException) {
                val error = ApiError.Timeout("Request timeout", e)
                lastError = error
                Log.w(TAG, "Timeout classifying image (attempt $attempt)", e)
                onAttempt?.invoke(attempt, error)
            } catch (e: UnknownHostException) {
                val error = ApiError.Offline("Offline - check your connection", e)
                lastError = error
                Log.w(TAG, "Network unavailable (attempt $attempt)", e)
                onAttempt?.invoke(attempt, error)
            } catch (e: IOException) {
                val error = ApiError.Network("Network error: ${e.message}", e)
                lastError = error
                Log.w(TAG, "I/O error classifying image (attempt $attempt)", e)
                onAttempt?.invoke(attempt, error)
            } catch (e: Exception) {
                val error = ApiError.Unexpected("Unexpected error: ${e.message}", e)
                lastError = error
                Log.e(TAG, "Unexpected error classifying image", e)
                onAttempt?.invoke(attempt, error)
                return ApiResult.Error(error)
            }

            if (attempt >= maxAttempts) {
                break
            }

            Log.d(TAG, "Retrying after failure (attempt $attempt)")
            delay(calculateBackoffDelay(attempt))
            attempt++
        }

        // All retries exhausted
        return ApiResult.Error(lastError ?: ApiError.Unexpected("Unable to classify"))
    }

    /**
     * Execute a single HTTP request.
     */
    private suspend fun executeRequest(
        endpoint: String,
        imageBytes: ByteArray,
        apiKey: String?,
        correlationId: String,
    ): ApiResult {
        val requestBody =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    name = "image",
                    filename = "item.jpg",
                    body = imageBytes.toRequestBody("image/jpeg".toMediaType()),
                )
                .addFormDataPart("domainPackId", domainPackId)
                .build()

        // Log API key status
        Log.d("ScaniumNet", "CloudClassifierApi: endpoint=$endpoint")
        if (apiKey != null) {
            Log.d("ScaniumAuth", "CloudClassifierApi: apiKey present len=${apiKey.length} prefix=${apiKey.take(6)}...")
        } else {
            Log.w("ScaniumAuth", "CloudClassifierApi: apiKey is NULL - X-API-Key header will NOT be added!")
        }

        val request =
            Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .apply {
                    if (apiKey != null) {
                        Log.d("ScaniumAuth", "CloudClassifierApi: Adding X-API-Key header")
                        header("X-API-Key", apiKey)
                        // Add HMAC signature for replay protection (SEC-004)
                        RequestSigner.addSignatureHeaders(
                            builder = this,
                            apiKey = apiKey,
                            params = mapOf("domainPackId" to domainPackId),
                            binaryContentSize = imageBytes.size.toLong(),
                        )
                    } else {
                        Log.w("ScaniumAuth", "CloudClassifierApi: SKIPPING X-API-Key header (null)")
                    }
                    header("X-Scanium-Correlation-Id", correlationId)
                    header("X-Client", "Scanium-Android")
                    header("X-App-Version", BuildConfig.VERSION_NAME)
                }
                .build()

        client.newCall(request).execute().use { response ->
            val responseCode = response.code
            val responseBody = response.body?.string()
            Log.d(TAG, "Response: code=$responseCode")

            return when {
                responseCode == 200 && responseBody != null -> {
                    try {
                        val apiResponse = json.decodeFromString<CloudClassificationResponse>(responseBody)
                        ApiResult.Success(apiResponse)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse response", e)
                        ApiResult.Error(ApiError.ParseError("Failed to parse response: ${e.message}", e))
                    }
                }
                responseCode == 503 || responseCode == 504 -> {
                    val message = "Classification failed (HTTP $responseCode)${responseBody?.let { ": $it" } ?: ""}"
                    ApiResult.Error(ApiError.ServerError(message, responseCode, isOffline = true))
                }
                isRetryableError(responseCode) -> {
                    val message = "Classification failed (HTTP $responseCode)${responseBody?.let { ": $it" } ?: ""}"
                    ApiResult.Error(ApiError.ServerError(message, responseCode))
                }
                else -> {
                    val message = "Classification failed (HTTP $responseCode)${responseBody?.let { ": $it" } ?: ""}"
                    ApiResult.Error(ApiError.ClientError(message, responseCode))
                }
            }
        }
    }

    /**
     * Check if HTTP status code indicates a retryable error.
     *
     * Retryable errors:
     * - 408 Request Timeout
     * - 429 Too Many Requests
     * - 5xx Server errors
     *
     * Non-retryable errors:
     * - 400 Bad Request
     * - 401 Unauthorized
     * - 403 Forbidden
     * - 404 Not Found
     */
    private fun isRetryableError(statusCode: Int): Boolean {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500
    }

    /**
     * Calculate exponential backoff delay.
     */
    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponentialDelay = baseDelayMs * (1 shl (attempt - 1))
        return min(exponentialDelay, maxDelayMs)
    }

    /**
     * Convert bitmap to JPEG bytes.
     *
     * This strips EXIF metadata for privacy (JPEG compression creates a new image
     * without preserving original metadata).
     */
    private fun Bitmap.toJpegBytes(): ByteArray {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return stream.toByteArray()
    }
}

/**
 * API result wrapper.
 */
sealed class ApiResult {
    data class Success(val response: CloudClassificationResponse) : ApiResult()
    data class Error(val error: ApiError) : ApiResult()
    data class ConfigError(val message: String) : ApiResult()
}

/**
 * API error types with categorization.
 */
sealed class ApiError(
    open val message: String,
    open val cause: Throwable? = null,
) {
    abstract val isRetryable: Boolean
    open val isOffline: Boolean = false
    open val statusCode: Int? = null

    data class Timeout(
        override val message: String,
        override val cause: Throwable? = null,
    ) : ApiError(message, cause) {
        override val isRetryable = true
    }

    data class Offline(
        override val message: String,
        override val cause: Throwable? = null,
    ) : ApiError(message, cause) {
        override val isRetryable = true
        override val isOffline = true
    }

    data class Network(
        override val message: String,
        override val cause: Throwable? = null,
    ) : ApiError(message, cause) {
        override val isRetryable = true
    }

    data class ServerError(
        override val message: String,
        override val statusCode: Int,
        override val isOffline: Boolean = false,
    ) : ApiError(message) {
        override val isRetryable = true
    }

    data class ClientError(
        override val message: String,
        override val statusCode: Int,
    ) : ApiError(message) {
        override val isRetryable = false
    }

    data class ParseError(
        override val message: String,
        override val cause: Throwable? = null,
    ) : ApiError(message, cause) {
        override val isRetryable = false
    }

    data class Unexpected(
        override val message: String,
        override val cause: Throwable? = null,
    ) : ApiError(message, cause) {
        override val isRetryable = false
    }
}

/**
 * API response model for cloud classification.
 */
@Serializable
data class CloudClassificationResponse(
    val domainCategoryId: String? = null,
    val confidence: Float? = null,
    val label: String? = null,
    val attributes: Map<String, String>? = null,
    val enrichedAttributes: EnrichedAttributesResponse? = null,
    val visionAttributes: VisionAttributesResponse? = null,
    val requestId: String? = null,
)

/**
 * Enriched attributes from VisionExtractor.
 */
@Serializable
data class EnrichedAttributesResponse(
    val brand: EnrichedAttributeResponse? = null,
    val model: EnrichedAttributeResponse? = null,
    val color: EnrichedAttributeResponse? = null,
    val secondaryColor: EnrichedAttributeResponse? = null,
    val material: EnrichedAttributeResponse? = null,
    val suggestedNextPhoto: String? = null,
)

/**
 * Single enriched attribute with confidence and evidence.
 */
@Serializable
data class EnrichedAttributeResponse(
    val value: String,
    val confidence: String, // "HIGH", "MED", "LOW"
    val confidenceScore: Float,
    val evidenceRefs: List<AttributeEvidenceResponse> = emptyList(),
)

/**
 * Evidence reference for an extracted attribute.
 */
@Serializable
data class AttributeEvidenceResponse(
    val type: String, // "logo", "ocr", "color", "label"
    val value: String,
    val score: Float? = null,
)

/**
 * Vision attributes from Google Vision API processing.
 * Contains raw extracted data: colors, OCR text, logos, labels, and candidate values.
 */
@Serializable
data class VisionAttributesResponse(
    val colors: List<VisionColorResponse> = emptyList(),
    val ocrText: String? = null,
    val logos: List<VisionLogoResponse> = emptyList(),
    val labels: List<VisionLabelResponse> = emptyList(),
    val brandCandidates: List<String> = emptyList(),
    val modelCandidates: List<String> = emptyList(),
)

/**
 * Color extracted from Vision API.
 */
@Serializable
data class VisionColorResponse(
    val name: String,
    val hex: String,
    val score: Float,
)

/**
 * Logo detected by Vision API.
 */
@Serializable
data class VisionLogoResponse(
    val name: String,
    val score: Float,
)

/**
 * Label detected by Vision API.
 */
@Serializable
data class VisionLabelResponse(
    val name: String,
    val score: Float,
)
