package com.scanium.app.ml.classification

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.scanium.app.BuildConfig
import com.scanium.app.config.SecureApiKeyStore
import com.scanium.app.domain.DomainPackProvider
import com.scanium.app.logging.CorrelationIds
import com.scanium.app.logging.ScaniumLog
import com.scanium.app.ml.ItemCategory
import com.scanium.app.network.security.RequestSigner
import com.scanium.shared.core.models.config.CloudClassifierConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.items.VisionAttributes as SharedVisionAttributes
import com.scanium.shared.core.models.items.VisionColor as SharedVisionColor
import com.scanium.shared.core.models.items.VisionLabel as SharedVisionLabel
import com.scanium.shared.core.models.items.VisionLogo as SharedVisionLogo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Cloud-based classifier that uploads cropped item images to a backend API.
 *
 * ## Privacy
 * - Only uploads cropped item thumbnails (not full frames)
 * - Strips EXIF metadata by re-compressing to JPEG
 * - No location or device information sent
 *
 * ## API Contract
 * - Endpoint: POST {SCANIUM_API_BASE_URL}/v1/classify
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
 * @property context Android context (nullable) for debug crop saving
 * @property domainPackId Domain pack to use for classification (default: "home_resale")
 */
class CloudClassifier(
    private val context: Context? = null,
    private val domainPackId: String = "home_resale",
    private val maxAttempts: Int = 3,
    private val baseDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 8_000L,
) : ItemClassifier {
    companion object {
        private const val TAG = "CloudClassifier"
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 10L
        private const val JPEG_QUALITY = 85
        private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
    }

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .apply {
                // SEC-003: Add certificate pinning for MITM protection
                val certificatePin = BuildConfig.SCANIUM_API_CERTIFICATE_PIN
                val baseUrl = BuildConfig.SCANIUM_API_BASE_URL
                if (certificatePin.isNotBlank() && baseUrl.isNotBlank()) {
                    val host =
                        try {
                            URI(baseUrl).host ?: ""
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse host from base URL for certificate pinning", e)
                            ""
                        }
                    if (host.isNotBlank()) {
                        val pinner =
                            CertificatePinner.Builder()
                                .add(host, certificatePin)
                                .build()
                        certificatePinner(pinner)
                        Log.d(TAG, "Certificate pinning enabled for host: $host")
                    }
                } else if (certificatePin.isBlank() && baseUrl.isNotBlank()) {
                    Log.w(TAG, "Certificate pinning not configured - set SCANIUM_API_CERTIFICATE_PIN for production")
                }
            }
            .build()

    private val apiKeyStore = context?.applicationContext?.let { SecureApiKeyStore(it) }

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    override suspend fun classifySingle(input: ClassificationInput): ClassificationResult? =
        withContext(Dispatchers.IO) {
            val config = currentConfig()
            if (!config.isConfigured) {
                ScaniumLog.w(TAG, "Cloud classifier not configured (SCANIUM_API_BASE_URL is empty)")
                return@withContext failureResult(
                    message = "Cloud classification disabled",
                    offline = true,
                )
            }

            val bitmap = input.bitmap
            val endpoint = "${config.baseUrl.trimEnd('/')}/v1/classify?enrichAttributes=true"
            val correlationId = CorrelationIds.currentClassificationSessionId()
            ScaniumLog.d(TAG, "Classifying endpoint=$endpoint domainPack=$domainPackId correlationId=$correlationId")

            maybeSaveDebugCrop(input)

            val imageBytes = bitmap.toJpegBytes()
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

            // DIAG: Log endpoint and API key status
            val apiKey = config.apiKey
            Log.d("ScaniumNet", "CloudClassifier: endpoint=$endpoint")
            if (apiKey != null) {
                Log.d("ScaniumAuth", "CloudClassifier: apiKey present len=${apiKey.length} prefix=${apiKey.take(6)}...")
            } else {
                Log.w("ScaniumAuth", "CloudClassifier: apiKey is NULL - X-API-Key header will NOT be added!")
            }

            var attempt = 1
            var lastError: String? = null
            while (attempt <= maxAttempts) {
                val request =
                    Request.Builder()
                        .url(endpoint)
                        .post(requestBody)
                        .apply {
                            if (apiKey != null) {
                                Log.d("ScaniumAuth", "CloudClassifier: Adding X-API-Key header")
                                header("X-API-Key", apiKey)
                                // Add HMAC signature for replay protection (SEC-004)
                                RequestSigner.addSignatureHeaders(
                                    builder = this,
                                    apiKey = apiKey,
                                    params = mapOf("domainPackId" to domainPackId),
                                    binaryContentSize = imageBytes.size.toLong(),
                                )
                            } else {
                                Log.w("ScaniumAuth", "CloudClassifier: SKIPPING X-API-Key header (null)")
                            }
                            header("X-Scanium-Correlation-Id", correlationId)
                            header("X-Client", "Scanium-Android")
                            header("X-App-Version", BuildConfig.VERSION_NAME)
                        }
                        .build()

                try {
                    var retry = false
                    var offlineError = false
                    var errorMessage: String? = null

                    client.newCall(request).execute().use { response ->
                        val responseCode = response.code
                        val responseBody = response.body?.string()
                        Log.d(TAG, "Response: code=$responseCode")

                        if (responseCode == 200 && responseBody != null) {
                            val apiResponse = json.decodeFromString<CloudClassificationResponse>(responseBody)
                            val result = parseSuccessResponse(apiResponse)
                            ScaniumLog.i(
                                TAG,
                                "Classification success correlationId=$correlationId requestId=${apiResponse.requestId}",
                            )
                            return@withContext result
                        }

                        errorMessage =
                            if (responseBody.isNullOrBlank()) {
                                "Classification failed (HTTP $responseCode)"
                            } else {
                                "Classification failed (HTTP $responseCode): $responseBody"
                            }

                        if (isRetryableError(responseCode) && attempt < maxAttempts) {
                            ScaniumLog.w(TAG, "Retryable error HTTP $responseCode attempt=$attempt correlationId=$correlationId")
                            retry = true
                            return@use
                        }

                        offlineError = responseCode == 503 || responseCode == 504
                    }

                    if (retry) {
                        lastError = errorMessage
                        delay(calculateBackoffDelay(attempt))
                        attempt++
                        continue
                    }

                    if (errorMessage != null) {
                        return@withContext failureResult(errorMessage, offlineError)
                    }
                } catch (e: SocketTimeoutException) {
                    lastError = "Request timeout"
                    Log.w(TAG, "Timeout classifying image (attempt $attempt)", e)
                } catch (e: UnknownHostException) {
                    lastError = "Offline - check your connection"
                    Log.w(TAG, "Network unavailable (attempt $attempt)", e)
                } catch (e: IOException) {
                    lastError = "Network error: ${e.message}"
                    Log.w(TAG, "I/O error classifying image (attempt $attempt)", e)
                } catch (e: Exception) {
                    lastError = "Unexpected error: ${e.message}"
                    Log.e(TAG, "Unexpected error classifying image", e)
                    return@withContext failureResult(lastError ?: "Unexpected error")
                }

                if (attempt >= maxAttempts) {
                    break
                }

                Log.d(TAG, "Retrying after failure (attempt $attempt)")
                delay(calculateBackoffDelay(attempt))
                attempt++
            }

            return@withContext failureResult(lastError ?: "Unable to classify", offline = lastError?.contains("Offline", true) == true)
        }

    private fun currentConfig(): CloudClassifierConfig =
        CloudClassifierConfig(
            baseUrl = BuildConfig.SCANIUM_API_BASE_URL,
            apiKey = apiKeyStore?.getApiKey(),
            domainPackId = domainPackId,
        )

    private fun failureResult(
        message: String,
        offline: Boolean = false,
    ): ClassificationResult {
        val errorMessage =
            if (offline) {
                "$message – using on-device labels"
            } else {
                message
            }

        return ClassificationResult(
            label = null,
            confidence = 0f,
            category = ItemCategory.UNKNOWN,
            mode = ClassificationMode.CLOUD,
            status = ClassificationStatus.FAILED,
            errorMessage = errorMessage,
        )
    }

    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponentialDelay = baseDelayMs * (1 shl (attempt - 1))
        return min(exponentialDelay, maxDelayMs)
    }

    /**
     * Parse successful API response into ClassificationResult.
     */
    private suspend fun parseSuccessResponse(apiResponse: CloudClassificationResponse): ClassificationResult {
        val domainCategoryId = apiResponse.domainCategoryId
        val confidence = apiResponse.confidence ?: 0f
        val label = apiResponse.label ?: domainCategoryId
        val attributes = apiResponse.attributes
        val requestId = apiResponse.requestId

        // Map domain category ID to ItemCategory
        val itemCategory =
            if (domainCategoryId != null && DomainPackProvider.isInitialized) {
                try {
                    val domainPack = DomainPackProvider.repository.getActiveDomainPack()
                    val domainCategory = domainPack.categories.find { it.id == domainCategoryId }
                    domainCategory?.let { category ->
                        ItemCategory.valueOf(category.itemCategoryName)
                    } ?: ItemCategory.fromClassifierLabel(label)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to map domain category", e)
                    ItemCategory.fromClassifierLabel(label)
                }
            } else {
                ItemCategory.fromClassifierLabel(label)
            }

        // Convert enriched attributes to ItemAttribute map
        val enrichedAttributes = parseEnrichedAttributes(apiResponse.enrichedAttributes)

        // Convert vision attributes
        val visionAttributes = parseVisionAttributes(apiResponse.visionAttributes)

        return ClassificationResult(
            label = label,
            confidence = confidence,
            category = itemCategory,
            mode = ClassificationMode.CLOUD,
            domainCategoryId = domainCategoryId,
            attributes = attributes,
            enrichedAttributes = enrichedAttributes,
            visionAttributes = visionAttributes,
            status = ClassificationStatus.SUCCESS,
            errorMessage = null,
            requestId = requestId,
        )
    }

    /**
     * Convert enriched attributes response to ItemAttribute map.
     */
    private fun parseEnrichedAttributes(
        enriched: EnrichedAttributesResponse?
    ): Map<String, ItemAttribute> {
        if (enriched == null) return emptyMap()

        val result = mutableMapOf<String, ItemAttribute>()

        enriched.brand?.let { attr ->
            result["brand"] = attr.toItemAttribute()
        }
        enriched.model?.let { attr ->
            result["model"] = attr.toItemAttribute()
        }
        enriched.color?.let { attr ->
            result["color"] = attr.toItemAttribute()
        }
        enriched.secondaryColor?.let { attr ->
            result["secondaryColor"] = attr.toItemAttribute()
        }
        enriched.material?.let { attr ->
            result["material"] = attr.toItemAttribute()
        }

        return result
    }

    /**
     * Convert API response attribute to ItemAttribute.
     */
    private fun EnrichedAttributeResponse.toItemAttribute(): ItemAttribute {
        // Get primary evidence source
        val source = evidenceRefs.firstOrNull()?.type

        return ItemAttribute(
            value = value,
            confidence = confidenceScore,
            source = source,
        )
    }

    /**
     * Convert vision attributes response to shared VisionAttributes model.
     */
    private fun parseVisionAttributes(
        visionAttrs: VisionAttributesResponse?
    ): SharedVisionAttributes {
        if (visionAttrs == null) return SharedVisionAttributes.EMPTY

        return SharedVisionAttributes(
            colors = visionAttrs.colors.map { color ->
                SharedVisionColor(
                    name = color.name,
                    hex = color.hex,
                    score = color.score,
                )
            },
            ocrText = visionAttrs.ocrText,
            logos = visionAttrs.logos.map { logo ->
                SharedVisionLogo(
                    name = logo.name,
                    score = logo.score,
                )
            },
            labels = visionAttrs.labels.map { label ->
                SharedVisionLabel(
                    name = label.name,
                    score = label.score,
                )
            },
            brandCandidates = visionAttrs.brandCandidates,
            modelCandidates = visionAttrs.modelCandidates,
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

    /**
     * Debug helper: saves crop to cache if enabled.
     * Only works in DEBUG builds and when saveCloudCropsEnabled is true.
     */
    private fun maybeSaveDebugCrop(input: ClassificationInput) {
        if (!BuildConfig.DEBUG || !ClassifierDebugFlags.saveCloudCropsEnabled) return
        val ctx = context ?: return
        runCatching {
            val dir =
                File(ctx.cacheDir, "classifier_crops").apply {
                    if (!exists() && !mkdirs()) {
                        Log.w(TAG, "Failed to create classifier crop directory: $absolutePath")
                    }
                }

            val timestamp = TIMESTAMP_FORMAT.format(Date())
            val safeId = input.aggregatedId.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val file = File(dir, "${safeId}_$timestamp.jpg")
            FileOutputStream(file).use { output ->
                input.bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            }
            Log.d(TAG, "Saved classifier crop for ${input.aggregatedId} to ${file.absolutePath}")
        }.onFailure { error ->
            Log.w(TAG, "Failed to save classifier crop for ${input.aggregatedId}", error)
        }
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
    val enrichedAttributes: EnrichedAttributesResponse? = null,
    val visionAttributes: VisionAttributesResponse? = null,
    val requestId: String? = null,
)

/**
 * Enriched attributes from VisionExtractor.
 */
@Serializable
private data class EnrichedAttributesResponse(
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
private data class EnrichedAttributeResponse(
    val value: String,
    val confidence: String, // "HIGH", "MED", "LOW"
    val confidenceScore: Float,
    val evidenceRefs: List<AttributeEvidenceResponse> = emptyList(),
)

/**
 * Evidence reference for an extracted attribute.
 */
@Serializable
private data class AttributeEvidenceResponse(
    val type: String, // "logo", "ocr", "color", "label"
    val value: String,
    val score: Float? = null,
)

/**
 * Vision attributes from Google Vision API processing.
 * Contains raw extracted data: colors, OCR text, logos, labels, and candidate values.
 */
@Serializable
private data class VisionAttributesResponse(
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
private data class VisionColorResponse(
    val name: String,
    val hex: String,
    val score: Float,
)

/**
 * Logo detected by Vision API.
 */
@Serializable
private data class VisionLogoResponse(
    val name: String,
    val score: Float,
)

/**
 * Label detected by Vision API.
 */
@Serializable
private data class VisionLabelResponse(
    val name: String,
    val score: Float,
)
