package com.scanium.app.selling.assistant

import android.content.Context
import android.util.Log
import com.scanium.app.BuildConfig
import com.scanium.app.config.SecureApiKeyStore
import com.scanium.app.listing.ExportProfileDefinition
import com.scanium.app.logging.ScaniumLog
import com.scanium.app.model.AssistantAction
import com.scanium.app.model.AssistantActionType
import com.scanium.app.model.AssistantMessage
import com.scanium.app.model.AssistantPrefs
import com.scanium.app.model.AssistantResponse
import com.scanium.app.model.ExportProfileSnapshot
import com.scanium.app.model.ItemContextSnapshot
import com.scanium.app.network.DeviceIdProvider
import com.scanium.app.network.security.RequestSigner
import com.scanium.app.selling.assistant.network.AssistantHttpConfig
import com.scanium.app.selling.assistant.network.AssistantOkHttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Image data to attach to an assistant request.
 * Images are sent as multipart form data when present.
 */
data class ItemImageAttachment(
    val itemId: String,
    val imageBytes: ByteArray,
    val mimeType: String,
    val filename: String = "thumbnail.jpg",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ItemImageAttachment) return false
        return itemId == other.itemId && imageBytes.contentEquals(other.imageBytes)
    }

    override fun hashCode(): Int = itemId.hashCode() * 31 + imageBytes.contentHashCode()
}

interface AssistantRepository {
    suspend fun send(
        items: List<ItemContextSnapshot>,
        history: List<AssistantMessage>,
        userMessage: String,
        exportProfile: ExportProfileDefinition,
        correlationId: String,
        imageAttachments: List<ItemImageAttachment> = emptyList(),
        assistantPrefs: AssistantPrefs? = null,
    ): AssistantResponse
}

enum class AssistantBackendErrorType {
    PROVIDER_UNAVAILABLE,
    PROVIDER_NOT_CONFIGURED,
    UNAUTHORIZED,
    RATE_LIMITED,
    NETWORK_TIMEOUT,
    NETWORK_UNREACHABLE,
    VISION_UNAVAILABLE,
    VALIDATION_ERROR,
}

enum class AssistantBackendErrorCategory {
    TEMPORARY,
    POLICY,
}

data class AssistantBackendFailure(
    val type: AssistantBackendErrorType,
    val category: AssistantBackendErrorCategory,
    val retryable: Boolean,
    val retryAfterSeconds: Int? = null,
    val message: String? = null,
) {
    fun toMetadata(): Map<String, String> {
        val metadata =
            mutableMapOf(
                "assistantErrorType" to type.name.lowercase(),
                "assistantErrorCategory" to category.name.lowercase(),
                "assistantErrorRetryable" to retryable.toString(),
            )
        retryAfterSeconds?.let { metadata["assistantErrorRetryAfter"] = it.toString() }
        message?.let { metadata["assistantErrorMessage"] = it }
        return metadata
    }
}

class AssistantBackendException(
    val failure: AssistantBackendFailure,
    cause: Throwable? = null,
) : Exception(failure.message, cause)

private class AssistantRepositoryLogger {
    fun log(message: String) {
        Log.d("AssistantRepo", message)
    }
}

/**
 * Factory for creating AssistantRepository instances with unified HTTP configuration.
 *
 * @param context Application context for accessing secure storage and device info
 * @param httpConfig HTTP timeout and retry configuration (defaults to production settings)
 * @param client Optional pre-configured OkHttpClient (primarily for testing)
 */
class AssistantRepositoryFactory(
    private val context: Context,
    private val httpConfig: AssistantHttpConfig = AssistantHttpConfig.DEFAULT,
    private val client: OkHttpClient = AssistantOkHttpClientFactory.create(httpConfig),
) {
    init {
        // Log configuration on factory creation for diagnostics
        AssistantOkHttpClientFactory.logConfigurationUsage("chat", httpConfig)
    }

    fun create(): AssistantRepository {
        val baseUrl = BuildConfig.SCANIUM_API_BASE_URL.orEmpty()
        val apiKey = SecureApiKeyStore(context).getApiKey()
        return CloudAssistantRepository(context, client, baseUrl, apiKey)
    }
}

private class CloudAssistantRepository(
    private val context: Context,
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val apiKey: String?,
) : AssistantRepository {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private val logger = AssistantRepositoryLogger()

    override suspend fun send(
        items: List<ItemContextSnapshot>,
        history: List<AssistantMessage>,
        userMessage: String,
        exportProfile: ExportProfileDefinition,
        correlationId: String,
        imageAttachments: List<ItemImageAttachment>,
        assistantPrefs: AssistantPrefs?,
    ): AssistantResponse =
        withContext(Dispatchers.IO) {
            if (baseUrl.isBlank()) {
                throw AssistantBackendException(
                    AssistantBackendFailure(
                        type = AssistantBackendErrorType.PROVIDER_NOT_CONFIGURED,
                        category = AssistantBackendErrorCategory.POLICY,
                        retryable = false,
                        message = "Assistant backend not configured",
                    ),
                )
            }

            val requestPayload =
                AssistantChatRequest(
                    items = items.map { ItemContextSnapshotDto.fromModel(it) },
                    history = history.map { AssistantMessageDto.fromModel(it) },
                    message = userMessage,
                    exportProfile =
                        ExportProfileSnapshotDto.fromModel(
                            ExportProfileSnapshot(exportProfile.id, exportProfile.displayName),
                        ),
                    assistantPrefs = assistantPrefs?.let { AssistantPrefsDto.fromModel(it) },
                )

            val endpoint = "${baseUrl.trimEnd('/')}/v1/assist/chat"
            // DIAG: Log endpoint and request summary
            Log.d("ScaniumNet", "AssistantRepo: endpoint=$endpoint")
            val payloadJson = json.encodeToString(AssistantChatRequest.serializer(), requestPayload)
            // Log request shape for debugging (not full content)
            Log.d("ScaniumAssist", "Request: items=${requestPayload.items.size} " +
                "history=${requestPayload.history.size} " +
                "message.length=${requestPayload.message.length} " +
                "hasExportProfile=${requestPayload.exportProfile != null} " +
                "hasPrefs=${requestPayload.assistantPrefs != null}")

            // Use multipart when images are attached, otherwise use JSON
            val request =
                if (imageAttachments.isNotEmpty()) {
                    buildMultipartRequest(endpoint, payloadJson, imageAttachments, correlationId)
                } else {
                    buildJsonRequest(endpoint, payloadJson, correlationId)
                }

            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        val parsed = json.decodeFromString(AssistantChatResponse.serializer(), responseBody)
                        parsed.assistantError?.let { throw AssistantBackendException(it.toFailure()) }
                        return@withContext parsed.toModel()
                    }
                    // Enhanced error logging for debugging validation failures
                    if (response.code == 400) {
                        Log.e("ScaniumAssist", "Validation error (HTTP 400): correlationId=$correlationId")
                        Log.e("ScaniumAssist", "Response body: ${responseBody?.take(500)}")
                    }
                    logger.log("Assistant backend error: ${response.code} ${ScaniumLog.sanitizeResponseBody(responseBody)}")
                    throw mapHttpFailure(response.code, responseBody)
                }
            } catch (error: AssistantBackendException) {
                throw error
            } catch (error: SocketTimeoutException) {
                throw AssistantBackendException(
                    AssistantBackendFailure(
                        type = AssistantBackendErrorType.NETWORK_TIMEOUT,
                        category = AssistantBackendErrorCategory.TEMPORARY,
                        retryable = true,
                        message = "Assistant request timed out",
                    ),
                    error,
                )
            } catch (error: java.net.UnknownHostException) {
                throw AssistantBackendException(
                    AssistantBackendFailure(
                        type = AssistantBackendErrorType.NETWORK_UNREACHABLE,
                        category = AssistantBackendErrorCategory.TEMPORARY,
                        retryable = true,
                        message = "Unable to reach assistant server",
                    ),
                    error,
                )
            } catch (error: java.net.ConnectException) {
                throw AssistantBackendException(
                    AssistantBackendFailure(
                        type = AssistantBackendErrorType.NETWORK_UNREACHABLE,
                        category = AssistantBackendErrorCategory.TEMPORARY,
                        retryable = true,
                        message = "Could not connect to assistant server",
                    ),
                    error,
                )
            } catch (error: IOException) {
                throw AssistantBackendException(
                    AssistantBackendFailure(
                        type = AssistantBackendErrorType.NETWORK_UNREACHABLE,
                        category = AssistantBackendErrorCategory.TEMPORARY,
                        retryable = true,
                        message = "Network error contacting assistant",
                    ),
                    error,
                )
            }
        }

    private fun buildJsonRequest(
        endpoint: String,
        payloadJson: String,
        correlationId: String,
    ): Request {
        return Request.Builder()
            .url(endpoint)
            .post(payloadJson.toRequestBody("application/json".toMediaType()))
            .apply { addCommonHeaders(this, payloadJson, correlationId) }
            .build()
    }

    private fun buildMultipartRequest(
        endpoint: String,
        payloadJson: String,
        imageAttachments: List<ItemImageAttachment>,
        correlationId: String,
    ): Request {
        val multipartBuilder =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("payload", payloadJson)

        // Track per-item image counts and total bytes
        val itemImageCounts = mutableMapOf<String, Int>()
        var totalImageBytes = 0L

        // Add images with field naming scheme: itemImages[itemId]
        imageAttachments.forEachIndexed { index, attachment ->
            val fieldName = "itemImages[${attachment.itemId}]"
            val mediaType = attachment.mimeType.toMediaType()
            val filename = "image_${index}_${attachment.filename}"
            multipartBuilder.addFormDataPart(
                fieldName,
                filename,
                attachment.imageBytes.toRequestBody(mediaType),
            )
            itemImageCounts[attachment.itemId] = (itemImageCounts[attachment.itemId] ?: 0) + 1
            totalImageBytes += attachment.imageBytes.size
        }

        val multipartBody = multipartBuilder.build()

        // Detailed logging (no image content, just metadata)
        Log.i(
            "ScaniumAssist",
            "Multipart request: correlationId=$correlationId " +
                "imageCount=${imageAttachments.size} totalBytes=$totalImageBytes " +
                "itemImageCounts=$itemImageCounts",
        )
        logger.log("Sending multipart request with ${imageAttachments.size} images")

        return Request.Builder()
            .url(endpoint)
            .post(multipartBody)
            .apply { addCommonHeaders(this, payloadJson, correlationId) }
            .build()
    }

    private fun addCommonHeaders(
        builder: Request.Builder,
        payloadJson: String,
        correlationId: String,
    ) {
        // DIAG: Log API key status
        if (apiKey != null) {
            Log.d("ScaniumAuth", "AssistantRepo: apiKey present len=${apiKey!!.length} prefix=${apiKey!!.take(6)}...")
            Log.d("ScaniumAuth", "AssistantRepo: Adding X-API-Key header")
            builder.header("X-API-Key", apiKey!!)
            // Add HMAC signature for replay protection (SEC-004)
            RequestSigner.addSignatureHeaders(
                builder = builder,
                apiKey = apiKey!!,
                requestBody = payloadJson,
            )
        } else {
            Log.w("ScaniumAuth", "AssistantRepo: apiKey is NULL - X-API-Key header will NOT be added!")
        }
        builder.header("X-Scanium-Correlation-Id", correlationId)
        builder.header("X-Client", "Scanium-Android")
        builder.header("X-App-Version", BuildConfig.VERSION_NAME)

        // Add device ID header for rate limiting
        // Backend expects raw device ID (not hashed) in this header
        val deviceId = DeviceIdProvider.getRawDeviceId(context)
        if (deviceId.isNotBlank()) {
            builder.header("X-Scanium-Device-Id", deviceId)
        }
    }

    private fun mapHttpFailure(
        code: Int,
        responseBody: String?,
    ): AssistantBackendException {
        val assistantError =
            responseBody?.let {
                runCatching { json.decodeFromString(AssistantErrorResponse.serializer(), it) }.getOrNull()
            }?.assistantError

        if (assistantError != null) {
            return AssistantBackendException(assistantError.toFailure())
        }

        val failure =
            when (code) {
                400 ->
                    AssistantBackendFailure(
                        type = AssistantBackendErrorType.VALIDATION_ERROR,
                        category = AssistantBackendErrorCategory.POLICY,
                        retryable = false,
                        message = "Assistant request invalid",
                    )
                401 ->
                    AssistantBackendFailure(
                        type = AssistantBackendErrorType.UNAUTHORIZED,
                        category = AssistantBackendErrorCategory.POLICY,
                        retryable = false,
                        message = "Not authorized to use assistant",
                    )
                403 ->
                    AssistantBackendFailure(
                        type = AssistantBackendErrorType.UNAUTHORIZED,
                        category = AssistantBackendErrorCategory.POLICY,
                        retryable = false,
                        message = "Access to assistant denied",
                    )
                429 ->
                    AssistantBackendFailure(
                        type = AssistantBackendErrorType.RATE_LIMITED,
                        category = AssistantBackendErrorCategory.POLICY,
                        retryable = true,
                        message = "Assistant rate limit exceeded",
                    )
                503 ->
                    AssistantBackendFailure(
                        type = AssistantBackendErrorType.PROVIDER_UNAVAILABLE,
                        category = AssistantBackendErrorCategory.TEMPORARY,
                        retryable = true,
                        message = "Assistant provider unavailable",
                    )
                504 ->
                    AssistantBackendFailure(
                        type = AssistantBackendErrorType.NETWORK_TIMEOUT,
                        category = AssistantBackendErrorCategory.TEMPORARY,
                        retryable = true,
                        message = "Assistant gateway timeout",
                    )
                else ->
                    AssistantBackendFailure(
                        type = AssistantBackendErrorType.PROVIDER_UNAVAILABLE,
                        category = AssistantBackendErrorCategory.TEMPORARY,
                        retryable = true,
                        message = "Assistant backend error",
                    )
            }
        return AssistantBackendException(failure)
    }
}

@Serializable
private data class AssistantChatRequest(
    val items: List<ItemContextSnapshotDto>,
    val history: List<AssistantMessageDto> = emptyList(),
    val message: String,
    val exportProfile: ExportProfileSnapshotDto? = null,
    val assistantPrefs: AssistantPrefsDto? = null,
)

@Serializable
private data class AssistantPrefsDto(
    val language: String? = null,
    val tone: String? = null,
    val region: String? = null,
    val units: String? = null,
    val verbosity: String? = null,
) {
    companion object {
        fun fromModel(model: AssistantPrefs): AssistantPrefsDto {
            return AssistantPrefsDto(
                language = model.language,
                tone = model.tone?.name,
                region = model.region?.name,
                units = model.units?.name,
                verbosity = model.verbosity?.name,
            )
        }
    }
}

@Serializable
private data class AssistantChatResponse(
    @SerialName("reply")
    val content: String,
    val actions: List<AssistantActionDto> = emptyList(),
    @SerialName("citationsMetadata")
    val citationsMetadata: Map<String, String>? = null,
    val fromCache: Boolean = false,
    val confidenceTier: String? = null,
    val evidence: List<EvidenceBulletDto> = emptyList(),
    val suggestedAttributes: List<SuggestedAttributeDto> = emptyList(),
    val suggestedDraftUpdates: List<SuggestedDraftUpdateDto> = emptyList(),
    val suggestedNextPhoto: String? = null,
    val assistantError: AssistantErrorDto? = null,
) {
    fun toModel(): AssistantResponse {
        return AssistantResponse(
            content = content,
            actions = actions.map { it.toModel() },
            citationsMetadata = citationsMetadata,
            confidenceTier = confidenceTier?.let { parseConfidenceTier(it) },
            evidence = evidence.map { it.toModel() },
            suggestedAttributes = suggestedAttributes.map { it.toModel() },
            suggestedDraftUpdates = suggestedDraftUpdates.map { it.toModel() },
            suggestedNextPhoto = suggestedNextPhoto,
        )
    }

    private fun parseConfidenceTier(tier: String): com.scanium.app.model.ConfidenceTier? {
        return runCatching { com.scanium.app.model.ConfidenceTier.valueOf(tier) }.getOrNull()
    }
}

@Serializable
private data class AssistantErrorResponse(
    val assistantError: AssistantErrorDto? = null,
)

@Serializable
private data class AssistantErrorDto(
    val type: String,
    val category: String,
    val retryable: Boolean,
    val retryAfterSeconds: Int? = null,
    val message: String? = null,
) {
    fun toFailure(): AssistantBackendFailure {
        return AssistantBackendFailure(
            type = parseType(type),
            category = parseCategory(category),
            retryable = retryable,
            retryAfterSeconds = retryAfterSeconds,
            message = message,
        )
    }

    private fun parseType(raw: String): AssistantBackendErrorType {
        return when (raw.lowercase()) {
            "unauthorized" -> AssistantBackendErrorType.UNAUTHORIZED
            "provider_not_configured" -> AssistantBackendErrorType.PROVIDER_NOT_CONFIGURED
            "rate_limited" -> AssistantBackendErrorType.RATE_LIMITED
            "network_timeout" -> AssistantBackendErrorType.NETWORK_TIMEOUT
            "network_unreachable" -> AssistantBackendErrorType.NETWORK_UNREACHABLE
            "vision_unavailable" -> AssistantBackendErrorType.VISION_UNAVAILABLE
            "validation_error" -> AssistantBackendErrorType.VALIDATION_ERROR
            else -> AssistantBackendErrorType.PROVIDER_UNAVAILABLE
        }
    }

    private fun parseCategory(raw: String): AssistantBackendErrorCategory {
        return when (raw.lowercase()) {
            "policy" -> AssistantBackendErrorCategory.POLICY
            else -> AssistantBackendErrorCategory.TEMPORARY
        }
    }
}

@Serializable
private data class AssistantMessageDto(
    val role: String,
    val content: String,
    val timestamp: Long,
    val itemContextIds: List<String> = emptyList(),
) {
    companion object {
        fun fromModel(model: AssistantMessage): AssistantMessageDto {
            return AssistantMessageDto(
                role = model.role.name,
                content = model.content,
                timestamp = model.timestamp,
                itemContextIds = model.itemContextIds,
            )
        }
    }
}

@Serializable
private data class AssistantActionDto(
    val type: String,
    val payload: Map<String, String> = emptyMap(),
    val label: String? = null,
    val requiresConfirmation: Boolean = false,
) {
    fun toModel(): AssistantAction {
        val actionType =
            runCatching { AssistantActionType.valueOf(type) }
                .getOrElse { AssistantActionType.COPY_TEXT }
        return AssistantAction(
            type = actionType,
            payload = payload,
            label = label,
            requiresConfirmation = requiresConfirmation,
        )
    }
}

@Serializable
private data class EvidenceBulletDto(
    val type: String,
    val text: String,
) {
    fun toModel() = com.scanium.app.model.EvidenceBullet(type = type, text = text)
}

@Serializable
private data class SuggestedAttributeDto(
    val key: String,
    val value: String,
    val confidence: String,
    val source: String? = null,
) {
    fun toModel() =
        com.scanium.app.model.SuggestedAttribute(
            key = key,
            value = value,
            confidence = parseConfidence(confidence),
            source = source,
        )

    private fun parseConfidence(tier: String): com.scanium.app.model.ConfidenceTier {
        return runCatching { com.scanium.app.model.ConfidenceTier.valueOf(tier) }
            .getOrElse { com.scanium.app.model.ConfidenceTier.LOW }
    }
}

@Serializable
private data class SuggestedDraftUpdateDto(
    val field: String,
    val value: String,
    val confidence: String,
    val requiresConfirmation: Boolean = false,
) {
    fun toModel() =
        com.scanium.app.model.SuggestedDraftUpdate(
            field = field,
            value = value,
            confidence = parseConfidence(confidence),
            requiresConfirmation = requiresConfirmation,
        )

    private fun parseConfidence(tier: String): com.scanium.app.model.ConfidenceTier {
        return runCatching { com.scanium.app.model.ConfidenceTier.valueOf(tier) }
            .getOrElse { com.scanium.app.model.ConfidenceTier.LOW }
    }
}

@Serializable
private data class ItemContextSnapshotDto(
    val itemId: String,
    val title: String? = null,
    val description: String? = null,
    val category: String? = null,
    val confidence: Float? = null,
    val attributes: List<ItemAttributeSnapshotDto> = emptyList(),
    val priceEstimate: Double? = null,
    val photosCount: Int = 0,
    val exportProfileId: String? = null,
) {
    companion object {
        fun fromModel(model: ItemContextSnapshot): ItemContextSnapshotDto {
            return ItemContextSnapshotDto(
                itemId = model.itemId,
                title = model.title,
                description = model.description,
                category = model.category,
                confidence = model.confidence,
                attributes = model.attributes.map { ItemAttributeSnapshotDto.fromModel(it) },
                priceEstimate = model.priceEstimate,
                photosCount = model.photosCount,
                exportProfileId = model.exportProfileId.value,
            )
        }
    }
}

@Serializable
private data class ItemAttributeSnapshotDto(
    val key: String,
    val value: String,
    val confidence: Float? = null,
    val source: String? = null,
) {
    companion object {
        fun fromModel(model: com.scanium.app.model.ItemAttributeSnapshot): ItemAttributeSnapshotDto {
            return ItemAttributeSnapshotDto(
                key = model.key,
                value = model.value,
                confidence = model.confidence,
                // Map source enum to backend-expected string values
                source = model.source?.name,
            )
        }
    }
}

@Serializable
private data class ExportProfileSnapshotDto(
    val id: String,
    val displayName: String,
) {
    companion object {
        fun fromModel(model: ExportProfileSnapshot): ExportProfileSnapshotDto {
            return ExportProfileSnapshotDto(
                id = model.id.value,
                displayName = model.displayName,
            )
        }
    }
}
