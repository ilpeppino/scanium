package com.scanium.app.selling.assistant

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.scanium.app.BuildConfig
import com.scanium.app.config.SecureApiKeyStore
import com.scanium.app.listing.ExportProfileDefinition
import com.scanium.app.model.AssistantAction
import com.scanium.app.model.AssistantActionType
import com.scanium.app.model.AssistantMessage
import com.scanium.app.model.AssistantPrefs
import com.scanium.app.model.AssistantResponse
import com.scanium.app.model.ExportProfileSnapshot
import com.scanium.app.model.ItemContextSnapshot
import com.scanium.app.selling.assistant.network.AssistantHttpConfig
import com.scanium.app.selling.assistant.network.AssistantOkHttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

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
        includePricing: Boolean = false,
        pricingCountryCode: String? = null,
    ): AssistantResponse
}

enum class AssistantBackendErrorType {
    PROVIDER_UNAVAILABLE,
    PROVIDER_NOT_CONFIGURED,
    UNAUTHORIZED, // Generic unauthorized (kept for backward compatibility)
    AUTH_REQUIRED, // Phase B: Auth required but not provided
    AUTH_INVALID, // Phase B: Auth provided but invalid/expired
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

@OptIn(ExperimentalSerializationApi::class)
internal val ASSISTANT_JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false  // Don't encode null values for optional fields
}

internal fun buildAssistantRequestPayload(
    items: List<ItemContextSnapshot>,
    history: List<AssistantMessage>,
    userMessage: String,
    exportProfile: ExportProfileDefinition,
    assistantPrefs: AssistantPrefs?,
    includePricing: Boolean,
    pricingCountryCode: String?,
): AssistantChatRequest {
    return AssistantChatRequest(
        items = items.map { ItemContextSnapshotDto.fromModel(it) },
        history = history.map { AssistantMessageDto.fromModel(it) },
        message = userMessage,
        exportProfile = ExportProfileSnapshotDto.fromModel(
            ExportProfileSnapshot(exportProfile.id, exportProfile.displayName),
        ),
        assistantPrefs = assistantPrefs?.let { AssistantPrefsDto.fromModel(it) },
        includePricing = includePricing,
        pricingPrefs = pricingCountryCode?.let { PricingPrefsDto(countryCode = it) },
    )
}

@VisibleForTesting
internal object AssistantContractCodec {
    fun encodeRequest(
        items: List<ItemContextSnapshot>,
        history: List<AssistantMessage>,
        userMessage: String,
        exportProfile: ExportProfileDefinition,
        assistantPrefs: AssistantPrefs?,
        includePricing: Boolean,
        pricingCountryCode: String?,
    ): String {
        val payload = buildAssistantRequestPayload(
            items = items,
            history = history,
            userMessage = userMessage,
            exportProfile = exportProfile,
            assistantPrefs = assistantPrefs,
            includePricing = includePricing,
            pricingCountryCode = pricingCountryCode,
        )
        return ASSISTANT_JSON.encodeToString(AssistantChatRequest.serializer(), payload)
    }

    fun decodeResponse(responseBody: String): AssistantResponse {
        val parsed = ASSISTANT_JSON.decodeFromString(AssistantChatResponse.serializer(), responseBody)
        return parsed.toModel()
    }
}

@OptIn(ExperimentalSerializationApi::class)
private class CloudAssistantRepository(
    private val context: Context,
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val apiKey: String?,
) : AssistantRepository {
    private val api = AssistantApi(context, client, apiKey)

    override suspend fun send(
        items: List<ItemContextSnapshot>,
        history: List<AssistantMessage>,
        userMessage: String,
        exportProfile: ExportProfileDefinition,
        correlationId: String,
        imageAttachments: List<ItemImageAttachment>,
        assistantPrefs: AssistantPrefs?,
        includePricing: Boolean,
        pricingCountryCode: String?,
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

            val requestPayload = buildAssistantRequestPayload(
                items = items,
                history = history,
                userMessage = userMessage,
                exportProfile = exportProfile,
                assistantPrefs = assistantPrefs,
                includePricing = includePricing,
                pricingCountryCode = pricingCountryCode,
            )

            val endpoint = "${baseUrl.trimEnd('/')}/v1/assist/chat"
            return@withContext api.send(
                endpoint = endpoint,
                requestPayload = requestPayload,
                imageAttachments = imageAttachments,
                correlationId = correlationId,
            )
        }
}

@Serializable
internal data class AssistantChatRequest(
    val items: List<ItemContextSnapshotDto>,
    val history: List<AssistantMessageDto> = emptyList(),
    val message: String,
    val exportProfile: ExportProfileSnapshotDto? = null,
    val assistantPrefs: AssistantPrefsDto? = null,
    val includePricing: Boolean = false,
    val pricingPrefs: PricingPrefsDto? = null,
)

@Serializable
internal data class AssistantPrefsDto(
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
internal data class PricingPrefsDto(
    val countryCode: String,
)

@Serializable
internal data class AssistantChatResponse(
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
    @SerialName("marketPrice")
    val marketPrice: com.scanium.shared.core.models.assistant.PricingInsights? = null,
    val pricingInsights: com.scanium.shared.core.models.assistant.PricingInsights? = null,
    val assistantError: AssistantErrorDto? = null,
) {
    fun toModel(): AssistantResponse {
        val pricing = marketPrice ?: pricingInsights
        return AssistantResponse(
            reply = content,
            actions = actions.map { it.toModel() },
            citationsMetadata = citationsMetadata,
            confidenceTier = confidenceTier?.let { parseConfidenceTier(it) },
            evidence = evidence.map { it.toModel() },
            suggestedAttributes = suggestedAttributes.map { it.toModel() },
            suggestedDraftUpdates = suggestedDraftUpdates.map { it.toModel() },
            suggestedNextPhoto = suggestedNextPhoto,
            marketPrice = pricing,
            pricingInsights = pricing,
        )
    }

    private fun parseConfidenceTier(tier: String): com.scanium.app.model.ConfidenceTier? {
        return runCatching { com.scanium.app.model.ConfidenceTier.valueOf(tier) }.getOrNull()
    }
}

@Serializable
internal data class AssistantMessageDto(
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
internal data class AssistantActionDto(
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
internal data class EvidenceBulletDto(
    val type: String,
    val text: String,
) {
    fun toModel() = com.scanium.app.model.EvidenceBullet(type = type, text = text)
}

@Serializable
internal data class SuggestedAttributeDto(
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
internal data class SuggestedDraftUpdateDto(
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
internal data class ItemContextSnapshotDto(
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
internal data class ItemAttributeSnapshotDto(
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
internal data class ExportProfileSnapshotDto(
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
