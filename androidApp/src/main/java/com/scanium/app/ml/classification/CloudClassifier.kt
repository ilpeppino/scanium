package com.scanium.app.ml.classification

import android.graphics.Bitmap
import android.util.Log
import com.scanium.app.BuildConfig
import com.scanium.app.domain.DomainPackProvider
import com.scanium.app.ml.ItemCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import java.util.concurrent.TimeUnit

/**
 * Cloud-based classifier that uploads cropped item images to a backend API.
 *
 * ## Privacy
 * - Only uploads cropped item thumbnails (not full frames)
 * - Strips EXIF metadata by re-compressing to JPEG
 * - No location or device information sent
 *
 * ## API Contract
 * - Endpoint: POST {SCANIUM_API_BASE_URL}/classify
 * - Request: multipart/form-data
 *   - `image`: JPEG file (cropped item)
 *   - `domainPackId`: string (default: "home_resale")
 * - Response: JSON
 *   ```json
 *   {
 *     "domainCategoryId": "furniture_sofa",
 *     "confidence": 0.87,
 *     "label": "Sofa",
 *     "attributes": {"color": "brown", "condition": "good"},
 *     "requestId": "req_abc123"
 *   }
 *   ```
 *
 * ## Error Handling
 * - Retryable: 408, 429, 5xx, network I/O errors
 * - Non-retryable: 400, 401, 403, 404
 * - Timeouts: 10s connect, 10s read
 *
 * ## Configuration
 * Set in local.properties (not committed):
 * ```
 * scanium.api.base.url=https://your-backend.com/api/v1
 * scanium.api.key=your-dev-api-key
 * ```
 *
 * @property domainPackId Domain pack to use for classification (default: "home_resale")
 */
class CloudClassifier(
    private val domainPackId: String = "home_resale"
) : ItemClassifier {
    companion object {
        private const val TAG = "CloudClassifier"
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 10L
        private const val JPEG_QUALITY = 85
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun classifySingle(bitmap: Bitmap): ClassificationResult? = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.SCANIUM_API_BASE_URL
        if (baseUrl.isBlank()) {
            Log.d(TAG, "Cloud classifier not configured (SCANIUM_API_BASE_URL is empty)")
            return@withContext null
        }

        val endpoint = "${baseUrl.trimEnd('/')}/v1/classify"
        Log.d(TAG, "Classifying with endpoint: $endpoint, domainPack: $domainPackId")

        try {
            // Convert bitmap to JPEG bytes (strips EXIF metadata)
            val imageBytes = bitmap.toJpegBytes()

            // Build multipart request
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    name = "image",
                    filename = "item.jpg",
                    body = imageBytes.toRequestBody("image/jpeg".toMediaType())
                )
                .addFormDataPart("domainPackId", domainPackId)
                .build()

            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .apply {
                    // Add API key if configured
                    val apiKey = BuildConfig.SCANIUM_API_KEY
                    if (apiKey.isNotBlank()) {
                        header("X-API-Key", apiKey)
                    }

                    // Add client metadata for server-side logging
                    header("X-Client", "Scanium-Android")
                    header("X-App-Version", BuildConfig.VERSION_NAME)
                }
                .build()

            // Execute request
            val response = client.newCall(request).execute()
            val responseCode = response.code
            val responseBody = response.body?.string()

            Log.d(TAG, "Response: code=$responseCode, body=${responseBody?.take(200)}")

            when {
                responseCode == 200 && responseBody != null -> {
                    // Parse successful response
                    val apiResponse = json.decodeFromString<CloudClassificationResponse>(responseBody)
                    val result = parseSuccessResponse(apiResponse)
                    Log.i(TAG, "Classification success: ${result.domainCategoryId} @ ${result.confidence}")
                    result
                }

                isRetryableError(responseCode) -> {
                    // Retryable error (will be handled by orchestrator)
                    Log.w(TAG, "Retryable error: HTTP $responseCode")
                    ClassificationResult(
                        label = null,
                        confidence = 0f,
                        category = ItemCategory.UNKNOWN,
                        mode = ClassificationMode.CLOUD,
                        status = ClassificationStatus.FAILED,
                        errorMessage = "Server error (HTTP $responseCode)"
                    )
                }

                else -> {
                    // Non-retryable error
                    Log.e(TAG, "Non-retryable error: HTTP $responseCode, body: $responseBody")
                    ClassificationResult(
                        label = null,
                        confidence = 0f,
                        category = ItemCategory.UNKNOWN,
                        mode = ClassificationMode.CLOUD,
                        status = ClassificationStatus.FAILED,
                        errorMessage = "Classification failed (HTTP $responseCode)"
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Timeout classifying image", e)
            ClassificationResult(
                label = null,
                confidence = 0f,
                category = ItemCategory.UNKNOWN,
                mode = ClassificationMode.CLOUD,
                status = ClassificationStatus.FAILED,
                errorMessage = "Request timeout"
            )
        } catch (e: UnknownHostException) {
            Log.w(TAG, "Network unavailable", e)
            ClassificationResult(
                label = null,
                confidence = 0f,
                category = ItemCategory.UNKNOWN,
                mode = ClassificationMode.CLOUD,
                status = ClassificationStatus.FAILED,
                errorMessage = "No network connection"
            )
        } catch (e: IOException) {
            Log.w(TAG, "I/O error classifying image", e)
            ClassificationResult(
                label = null,
                confidence = 0f,
                category = ItemCategory.UNKNOWN,
                mode = ClassificationMode.CLOUD,
                status = ClassificationStatus.FAILED,
                errorMessage = "Network error: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error classifying image", e)
            ClassificationResult(
                label = null,
                confidence = 0f,
                category = ItemCategory.UNKNOWN,
                mode = ClassificationMode.CLOUD,
                status = ClassificationStatus.FAILED,
                errorMessage = "Unexpected error: ${e.message}"
            )
        }
    }

    /**
     * Parse successful API response into ClassificationResult.
     */
    private suspend fun parseSuccessResponse(
        apiResponse: CloudClassificationResponse
    ): ClassificationResult {
        val domainCategoryId = apiResponse.domainCategoryId
        val confidence = apiResponse.confidence ?: 0f
        val label = apiResponse.label ?: domainCategoryId
        val attributes = apiResponse.attributes
        val requestId = apiResponse.requestId

        // Map domain category ID to ItemCategory
        val itemCategory = if (domainCategoryId != null && DomainPackProvider.isInitialized) {
            try {
                val domainPack = DomainPackProvider.repository.getActiveDomainPack()
                val domainCategory = domainPack.categories.find { it.id == domainCategoryId }
                domainCategory?.let { category ->
                    ItemCategory.valueOf(category.itemCategoryName)
                } ?: ItemCategory.fromClassifierLabel(label)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to map domain category: $domainCategoryId", e)
                ItemCategory.fromClassifierLabel(label)
            }
        } else {
            ItemCategory.fromClassifierLabel(label)
        }

        return ClassificationResult(
            label = label,
            confidence = confidence,
            category = itemCategory,
            mode = ClassificationMode.CLOUD,
            domainCategoryId = domainCategoryId,
            attributes = attributes,
            status = ClassificationStatus.SUCCESS,
            errorMessage = null,
            requestId = requestId
        )
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
 * API response model for cloud classification.
 */
@Serializable
private data class CloudClassificationResponse(
    val domainCategoryId: String? = null,
    val confidence: Float? = null,
    val label: String? = null,
    val attributes: Map<String, String>? = null,
    val requestId: String? = null
)
