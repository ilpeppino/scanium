package com.scanium.app.selling.assistant

import android.util.Log
import com.scanium.app.BuildConfig
import com.scanium.app.model.AssistantAction
import com.scanium.app.model.AssistantActionType
import com.scanium.app.model.AssistantMessage
import com.scanium.app.model.AssistantPrefs
import com.scanium.app.model.AssistantPromptBuilder
import com.scanium.app.model.AssistantResponse
import com.scanium.app.model.AssistantRole
import com.scanium.app.model.ItemContextSnapshot
import com.scanium.app.model.ExportProfileSnapshot
import com.scanium.app.listing.ExportProfileDefinition
import com.scanium.app.listing.ExportProfiles
import com.scanium.app.network.security.RequestSigner
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
import java.util.concurrent.TimeUnit

/**
 * Image data to attach to an assistant request.
 * Images are sent as multipart form data when present.
 */
data class ItemImageAttachment(
    val itemId: String,
    val imageBytes: ByteArray,
    val mimeType: String,
    val filename: String = "thumbnail.jpg"
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
        assistantPrefs: AssistantPrefs? = null
    ): AssistantResponse
}

private class AssistantRepositoryLogger {
    fun log(message: String) {
        Log.d("AssistantRepo", message)
    }
}

class AssistantRepositoryFactory(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    fun create(): AssistantRepository {
        val baseUrl = BuildConfig.SCANIUM_API_BASE_URL.orEmpty()
        val apiKey = BuildConfig.SCANIUM_API_KEY.takeIf { it.isNotBlank() }
        val cloud = CloudAssistantRepository(client, baseUrl, apiKey)
        val fallback = LocalAssistantRepository()
        return FallbackAssistantRepository(primary = cloud, fallback = fallback)
    }
}

private class FallbackAssistantRepository(
    private val primary: AssistantRepository,
    private val fallback: AssistantRepository
) : AssistantRepository {
    override suspend fun send(
        items: List<ItemContextSnapshot>,
        history: List<AssistantMessage>,
        userMessage: String,
        exportProfile: ExportProfileDefinition,
        correlationId: String,
        imageAttachments: List<ItemImageAttachment>,
        assistantPrefs: AssistantPrefs?
    ): AssistantResponse {
        return try {
            primary.send(items, history, userMessage, exportProfile, correlationId, imageAttachments, assistantPrefs)
        } catch (error: Exception) {
            // Fallback doesn't support images - just pass empty list
            fallback.send(items, history, userMessage, exportProfile, correlationId, emptyList(), assistantPrefs)
        }
    }
}

private class CloudAssistantRepository(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val apiKey: String?
) : AssistantRepository {
    private val json = Json {
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
        assistantPrefs: AssistantPrefs?
    ): AssistantResponse = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) {
            throw IllegalStateException("Assistant backend not configured")
        }

        val requestPayload = AssistantChatRequest(
            items = items.map { ItemContextSnapshotDto.fromModel(it) },
            history = history.map { AssistantMessageDto.fromModel(it) },
            message = userMessage,
            exportProfile = ExportProfileSnapshotDto.fromModel(
                ExportProfileSnapshot(exportProfile.id, exportProfile.displayName)
            ),
            assistantPrefs = assistantPrefs?.let { AssistantPrefsDto.fromModel(it) }
        )

        val endpoint = "${baseUrl.trimEnd('/')}/v1/assist/chat"
        val payloadJson = json.encodeToString(AssistantChatRequest.serializer(), requestPayload)

        // Use multipart when images are attached, otherwise use JSON
        val request = if (imageAttachments.isNotEmpty()) {
            buildMultipartRequest(endpoint, payloadJson, imageAttachments, correlationId)
        } else {
            buildJsonRequest(endpoint, payloadJson, correlationId)
        }

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            if (response.isSuccessful && responseBody != null) {
                val parsed = json.decodeFromString(AssistantChatResponse.serializer(), responseBody)
                return@withContext parsed.toModel()
            }
            logger.log("Assistant backend error: ${response.code} ${responseBody.orEmpty()}")
            throw IOException("Assistant backend error ${response.code}")
        }
    }

    private fun buildJsonRequest(
        endpoint: String,
        payloadJson: String,
        correlationId: String
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
        correlationId: String
    ): Request {
        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("payload", payloadJson)

        // Add images with field naming scheme: itemImages[itemId]
        imageAttachments.forEachIndexed { index, attachment ->
            val fieldName = "itemImages[${attachment.itemId}]"
            val mediaType = attachment.mimeType.toMediaType()
            val filename = "image_${index}_${attachment.filename}"
            multipartBuilder.addFormDataPart(
                fieldName,
                filename,
                attachment.imageBytes.toRequestBody(mediaType)
            )
        }

        val multipartBody = multipartBuilder.build()

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
        correlationId: String
    ) {
        apiKey?.let { key ->
            builder.header("X-API-Key", key)
            // Add HMAC signature for replay protection (SEC-004)
            RequestSigner.addSignatureHeaders(
                builder = builder,
                apiKey = key,
                requestBody = payloadJson
            )
        }
        builder.header("X-Scanium-Correlation-Id", correlationId)
        builder.header("X-Client", "Scanium-Android")
        builder.header("X-App-Version", BuildConfig.VERSION_NAME)
    }
}

private class LocalAssistantRepository : AssistantRepository {
    override suspend fun send(
        items: List<ItemContextSnapshot>,
        history: List<AssistantMessage>,
        userMessage: String,
        exportProfile: ExportProfileDefinition,
        correlationId: String,
        imageAttachments: List<ItemImageAttachment>,
        assistantPrefs: AssistantPrefs?
    ): AssistantResponse {
        // Local fallback doesn't process images - they are ignored
        AssistantPromptBuilder.buildRequest(
            items = items,
            userMessage = userMessage,
            exportProfile = exportProfile,
            conversation = history
        )

        val primary = items.firstOrNull()
        if (primary == null) {
            return AssistantResponse(
                content = "Attach at least one item so I can tailor the listing advice."
            )
        }

        val message = userMessage.lowercase()
        if (message.contains("price") || message.contains("estimate")) {
            val estimate = primary.priceEstimate
            val content = if (estimate != null && estimate > 0.0) {
                val low = (estimate * 0.85).toInt()
                val high = (estimate * 1.15).toInt().coerceAtLeast(low + 1)
                "Based on the current draft estimate, a reasonable range is EUR $low-$high."
            } else {
                "I do not have enough data for a reliable price range yet. Share condition, brand, and comparable prices to refine."
            }
            return AssistantResponse(content = content)
        }

        if (message.contains("title")) {
            val base = primary.title ?: primary.category ?: "Item"
            val suggestion = "Used $base".trim()
            return AssistantResponse(
                content = "Suggested title: \"$suggestion\".",
                actions = listOf(
                    AssistantAction(
                        type = AssistantActionType.APPLY_DRAFT_UPDATE,
                        payload = mapOf("itemId" to primary.itemId, "title" to suggestion)
                    ),
                    AssistantAction(
                        type = AssistantActionType.COPY_TEXT,
                        payload = mapOf("label" to "Title", "text" to suggestion)
                    )
                )
            )
        }

        if (message.contains("detail") || message.contains("missing")) {
            return AssistantResponse(
                content = "Confirm brand/model, note condition issues, capture measurements, and take clear photos of labels and defects."
            )
        }

        return AssistantResponse(
            content = "I can help with listing improvements, missing details, and safe pricing guidance. What should we refine?"
        )
    }
}

@Serializable
private data class AssistantChatRequest(
    val items: List<ItemContextSnapshotDto>,
    val history: List<AssistantMessageDto> = emptyList(),
    val message: String,
    val exportProfile: ExportProfileSnapshotDto? = null,
    val assistantPrefs: AssistantPrefsDto? = null
)

@Serializable
private data class AssistantPrefsDto(
    val language: String? = null,
    val tone: String? = null,
    val region: String? = null,
    val units: String? = null,
    val verbosity: String? = null
) {
    companion object {
        fun fromModel(model: AssistantPrefs): AssistantPrefsDto {
            return AssistantPrefsDto(
                language = model.language,
                tone = model.tone?.name,
                region = model.region?.name,
                units = model.units?.name,
                verbosity = model.verbosity?.name
            )
        }
    }
}

@Serializable
private data class AssistantChatResponse(
    val content: String,
    val actions: List<AssistantActionDto> = emptyList(),
    @SerialName("citationsMetadata")
    val citationsMetadata: Map<String, String>? = null,
    val confidenceTier: String? = null,
    val evidence: List<EvidenceBulletDto> = emptyList(),
    val suggestedAttributes: List<SuggestedAttributeDto> = emptyList(),
    val suggestedDraftUpdates: List<SuggestedDraftUpdateDto> = emptyList(),
    val suggestedNextPhoto: String? = null
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
            suggestedNextPhoto = suggestedNextPhoto
        )
    }

    private fun parseConfidenceTier(tier: String): com.scanium.app.model.ConfidenceTier? {
        return runCatching { com.scanium.app.model.ConfidenceTier.valueOf(tier) }.getOrNull()
    }
}

@Serializable
private data class AssistantMessageDto(
    val role: String,
    val content: String,
    val timestamp: Long,
    val itemContextIds: List<String> = emptyList()
) {
    companion object {
        fun fromModel(model: AssistantMessage): AssistantMessageDto {
            return AssistantMessageDto(
                role = model.role.name,
                content = model.content,
                timestamp = model.timestamp,
                itemContextIds = model.itemContextIds
            )
        }
    }
}

@Serializable
private data class AssistantActionDto(
    val type: String,
    val payload: Map<String, String> = emptyMap(),
    val label: String? = null,
    val requiresConfirmation: Boolean = false
) {
    fun toModel(): AssistantAction {
        val actionType = runCatching { AssistantActionType.valueOf(type) }
            .getOrElse { AssistantActionType.COPY_TEXT }
        return AssistantAction(
            type = actionType,
            payload = payload,
            label = label,
            requiresConfirmation = requiresConfirmation
        )
    }
}

@Serializable
private data class EvidenceBulletDto(
    val type: String,
    val text: String
) {
    fun toModel() = com.scanium.app.model.EvidenceBullet(type = type, text = text)
}

@Serializable
private data class SuggestedAttributeDto(
    val key: String,
    val value: String,
    val confidence: String,
    val source: String? = null
) {
    fun toModel() = com.scanium.app.model.SuggestedAttribute(
        key = key,
        value = value,
        confidence = parseConfidence(confidence),
        source = source
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
    val requiresConfirmation: Boolean = false
) {
    fun toModel() = com.scanium.app.model.SuggestedDraftUpdate(
        field = field,
        value = value,
        confidence = parseConfidence(confidence),
        requiresConfirmation = requiresConfirmation
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
    val category: String? = null,
    val confidence: Float? = null,
    val attributes: List<ItemAttributeSnapshotDto> = emptyList(),
    val priceEstimate: Double? = null,
    val photosCount: Int = 0,
    val exportProfileId: String? = null
) {
    companion object {
        fun fromModel(model: ItemContextSnapshot): ItemContextSnapshotDto {
            return ItemContextSnapshotDto(
                itemId = model.itemId,
                title = model.title,
                category = model.category,
                confidence = model.confidence,
                attributes = model.attributes.map { ItemAttributeSnapshotDto.fromModel(it) },
                priceEstimate = model.priceEstimate,
                photosCount = model.photosCount,
                exportProfileId = model.exportProfileId.value
            )
        }
    }
}

@Serializable
private data class ItemAttributeSnapshotDto(
    val key: String,
    val value: String,
    val confidence: Float? = null
) {
    companion object {
        fun fromModel(model: com.scanium.app.model.ItemAttributeSnapshot): ItemAttributeSnapshotDto {
            return ItemAttributeSnapshotDto(
                key = model.key,
                value = model.value,
                confidence = model.confidence
            )
        }
    }
}

@Serializable
private data class ExportProfileSnapshotDto(
    val id: String,
    val displayName: String
) {
    companion object {
        fun fromModel(model: ExportProfileSnapshot): ExportProfileSnapshotDto {
            return ExportProfileSnapshotDto(
                id = model.id.value,
                displayName = model.displayName
            )
        }
    }
}
