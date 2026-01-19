package com.scanium.app.ml.classification

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.scanium.app.BuildConfig
import com.scanium.app.config.SecureApiKeyStore
import com.scanium.app.domain.DomainPackProvider
import com.scanium.app.logging.ScaniumLog
import com.scanium.app.ml.ItemCategory
import com.scanium.shared.core.models.config.CloudClassifierConfig
import com.scanium.shared.core.models.items.ItemAttribute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.scanium.shared.core.models.items.VisionAttributes as SharedVisionAttributes
import com.scanium.shared.core.models.items.VisionColor as SharedVisionColor
import com.scanium.shared.core.models.items.VisionLabel as SharedVisionLabel
import com.scanium.shared.core.models.items.VisionLogo as SharedVisionLogo

/**
 * Cloud-based classifier that uploads cropped item images to a backend API.
 *
 * Refactored architecture:
 * - CloudClassifierApi: Handles HTTP requests, retries, and response parsing
 * - ClassificationTelemetry: Manages spans, metrics, and error tracking
 * - CloudClassifier: Coordinates API calls, telemetry, and result conversion
 *
 * ***REMOVED******REMOVED*** Privacy
 * - Only uploads cropped item thumbnails (not full frames)
 * - Strips EXIF metadata by re-compressing to JPEG
 * - No location or device information sent
 *
 * ***REMOVED******REMOVED*** API Contract
 * - Endpoint: POST {SCANIUM_API_BASE_URL}/v1/classify
 * - Request: multipart/form-data
 *   - `image`: JPEG file (cropped item)
 *   - `domainPackId`: string (default: "home_resale")
 * - Response: JSON (see CloudClassificationResponse in CloudClassifierApi)
 *
 * ***REMOVED******REMOVED*** Error Handling
 * - Retryable: 408, 429, 5xx, network I/O errors
 * - Non-retryable: 400, 401, 403, 404
 * - Timeouts: 10s connect, 30s read, 30s write (aligned Phase 3 policy)
 *
 * ***REMOVED******REMOVED*** Configuration
 * Set in local.properties (not committed):
 * ```
 * scanium.api.base.url=https://your-backend.com/api/v1
 * scanium.api.key=your-dev-api-key
 * ```
 *
 * @property context Android context (nullable) for debug crop saving
 * @property domainPackId Domain pack to use for classification (default: "home_resale")
 * @property maxAttempts Maximum number of retry attempts
 * @property baseDelayMs Base delay for exponential backoff
 * @property maxDelayMs Maximum delay for exponential backoff
 * @property telemetry Telemetry facade for recording metrics and spans
 */
class CloudClassifier(
    private val context: Context? = null,
    private val domainPackId: String = "home_resale",
    maxAttempts: Int = 3,
    baseDelayMs: Long = 1_000L,
    maxDelayMs: Long = 8_000L,
    private val telemetry: com.scanium.telemetry.facade.Telemetry? = null,
) : ItemClassifier {
    companion object {
        private const val TAG = "CloudClassifier"
        private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
    }

    // Phase 2: Use centralized timeout factory for consistency across assistant-related HTTP clients
    private val client =
        com.scanium.app.selling.assistant.network.AssistantOkHttpClientFactory
            .create(
                config = com.scanium.app.selling.assistant.network.AssistantHttpConfig.VISION,
                logStartupPolicy = false,
            ).newBuilder()
            .addInterceptor(
                com.scanium.app.telemetry
                    .TraceContextInterceptor(),
            ).apply {
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
                            CertificatePinner
                                .Builder()
                                .add(host, certificatePin)
                                .build()
                        certificatePinner(pinner)
                        Log.d(TAG, "Certificate pinning enabled for host: $host")
                    }
                } else if (certificatePin.isBlank() && baseUrl.isNotBlank()) {
                    Log.w(TAG, "Certificate pinning not configured - set SCANIUM_API_CERTIFICATE_PIN for production")
                }
            }.build()

    private val apiKeyStore = context?.applicationContext?.let { SecureApiKeyStore(it) }

    // API layer for HTTP requests and retries
    private val api =
        CloudClassifierApi(
            client = client,
            apiKeyStore = apiKeyStore,
            domainPackId = domainPackId,
            maxAttempts = maxAttempts,
            baseDelayMs = baseDelayMs,
            maxDelayMs = maxDelayMs,
        )

    // Telemetry helper for span and metric management
    private val telemetryHelper = ClassificationTelemetry(telemetry, domainPackId)

    override suspend fun classifySingle(input: ClassificationInput): ClassificationResult? =
        withContext(Dispatchers.IO) {
            val config = currentConfig()

            // Check configuration
            if (!config.isConfigured) {
                ScaniumLog.w(TAG, "Cloud classifier not configured (SCANIUM_API_BASE_URL is empty)")
                return@withContext failureResult(
                    message = "Cloud classification disabled",
                    offline = true,
                )
            }

            maybeSaveDebugCrop(input)

            // Begin telemetry span
            val classifySpan = telemetryHelper.beginClassificationSpan()
            val startTime = System.currentTimeMillis()

            try {
                // Execute API request with retries
                val apiResult =
                    api.classify(
                        bitmap = input.bitmap,
                        config = config,
                    ) { attempt, error ->
                        // Callback for each attempt
                        if (error != null) {
                            val errorType =
                                when (error) {
                                    is ApiError.Timeout -> "timeout"
                                    is ApiError.Offline -> "offline"
                                    is ApiError.Network -> "network"
                                    is ApiError.ServerError -> "server_error"
                                    is ApiError.ClientError -> "client_error"
                                    is ApiError.ParseError -> "parse_error"
                                    is ApiError.Unexpected -> error.cause?.javaClass?.simpleName ?: "unexpected"
                                }
                            telemetryHelper.recordAttemptError(errorType, attempt)
                            classifySpan?.recordError(error.message, mapOf("attempt" to attempt.toString()))
                        }
                    }

                // Process API result
                when (apiResult) {
                    is ApiResult.Success -> {
                        val response = apiResult.response
                        val result = parseSuccessResponse(response)
                        val latencyMs = System.currentTimeMillis() - startTime

                        // Record success metrics
                        telemetryHelper.recordSuccess(latencyMs, response.requestId, 1)
                        classifySpan?.endSuccess(latencyMs, response.requestId, 1)

                        ScaniumLog.i(TAG, "Classification success requestId=${response.requestId}")
                        return@withContext result
                    }

                    is ApiResult.Error -> {
                        val error = apiResult.error
                        val latencyMs = System.currentTimeMillis() - startTime
                        val errorType =
                            when (error) {
                                is ApiError.Timeout -> "timeout"
                                is ApiError.Offline -> "offline"
                                is ApiError.Network -> "network"
                                else -> "api_error"
                            }

                        telemetryHelper.recordError(latencyMs, error.statusCode, errorType)
                        classifySpan?.endError(error.message, error.statusCode)

                        return@withContext failureResult(error.message, error.isOffline)
                    }

                    is ApiResult.ConfigError -> {
                        return@withContext failureResult(apiResult.message, offline = true)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in classification coordinator", e)
                classifySpan?.endException()
                return@withContext failureResult("Unexpected error: ${e.message}")
            } finally {
                classifySpan?.ensureCleanup()
            }
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
    private fun parseEnrichedAttributes(enriched: EnrichedAttributesResponse?): Map<String, ItemAttribute> {
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
    private fun parseVisionAttributes(visionAttrs: VisionAttributesResponse?): SharedVisionAttributes {
        if (visionAttrs == null) return SharedVisionAttributes.EMPTY

        return SharedVisionAttributes(
            colors =
                visionAttrs.colors.map { color ->
                    SharedVisionColor(
                        name = color.name,
                        hex = color.hex,
                        score = color.score,
                    )
                },
            ocrText = visionAttrs.ocrText,
            logos =
                visionAttrs.logos.map { logo ->
                    SharedVisionLogo(
                        name = logo.name,
                        score = logo.score,
                    )
                },
            labels =
                visionAttrs.labels.map { label ->
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
