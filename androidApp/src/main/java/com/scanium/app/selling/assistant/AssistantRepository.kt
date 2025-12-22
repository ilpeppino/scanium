package com.scanium.app.selling.assistant

import android.util.Log
import com.scanium.app.BuildConfig
import com.scanium.app.model.AssistantAction
import com.scanium.app.model.AssistantActionType
import com.scanium.app.model.AssistantMessage
import com.scanium.app.model.AssistantPromptBuilder
import com.scanium.app.model.AssistantResponse
import com.scanium.app.model.AssistantRole
import com.scanium.app.model.ItemContextSnapshot
import com.scanium.app.model.ExportProfileSnapshot
import com.scanium.app.listing.ExportProfileDefinition
import com.scanium.app.listing.ExportProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

interface AssistantRepository {
    suspend fun send(
        items: List<ItemContextSnapshot>,
        history: List<AssistantMessage>,
        userMessage: String,
        exportProfile: ExportProfileDefinition,
        correlationId: String
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
        correlationId: String
    ): AssistantResponse {
        return try {
            primary.send(items, history, userMessage, exportProfile, correlationId)
        } catch (error: Exception) {
            fallback.send(items, history, userMessage, exportProfile, correlationId)
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
        correlationId: String
    ): AssistantResponse = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) {
            throw IllegalStateException("Assistant backend not configured")
        }

        val requestBody = AssistantChatRequest(
            items = items.map { ItemContextSnapshotDto.fromModel(it) },
            history = history.map { AssistantMessageDto.fromModel(it) },
            message = userMessage,
            exportProfile = ExportProfileSnapshotDto.fromModel(
                ExportProfileSnapshot(exportProfile.id, exportProfile.displayName)
            )
        )

        val endpoint = "${baseUrl.trimEnd('/')}/v1/assist/chat"
        val body = json.encodeToString(AssistantChatRequest.serializer(), requestBody)
        val request = Request.Builder()
            .url(endpoint)
            .post(body.toRequestBody("application/json".toMediaType()))
            .apply {
                apiKey?.let { header("X-API-Key", it) }
                header("X-Scanium-Correlation-Id", correlationId)
                header("X-Client", "Scanium-Android")
                header("X-App-Version", BuildConfig.VERSION_NAME)
            }
            .build()

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
}

private class LocalAssistantRepository : AssistantRepository {
    override suspend fun send(
        items: List<ItemContextSnapshot>,
        history: List<AssistantMessage>,
        userMessage: String,
        exportProfile: ExportProfileDefinition,
        correlationId: String
    ): AssistantResponse {
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
    val exportProfile: ExportProfileSnapshotDto? = null
)

@Serializable
private data class AssistantChatResponse(
    val content: String,
    val actions: List<AssistantActionDto> = emptyList(),
    @SerialName("citationsMetadata")
    val citationsMetadata: Map<String, String>? = null
) {
    fun toModel(): AssistantResponse {
        return AssistantResponse(
            content = content,
            actions = actions.map { it.toModel() },
            citationsMetadata = citationsMetadata
        )
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
    val payload: Map<String, String> = emptyMap()
) {
    fun toModel(): AssistantAction {
        val actionType = runCatching { AssistantActionType.valueOf(type) }
            .getOrElse { AssistantActionType.COPY_TEXT }
        return AssistantAction(type = actionType, payload = payload)
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
