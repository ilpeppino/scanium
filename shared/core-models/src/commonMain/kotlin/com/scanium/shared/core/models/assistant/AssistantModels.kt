package com.scanium.shared.core.models.assistant

import com.scanium.shared.core.models.listing.ExportProfileDefinition
import com.scanium.shared.core.models.listing.ExportProfileId
import com.scanium.shared.core.models.listing.ListingDraft
import com.scanium.shared.core.models.listing.DraftFieldKey
import kotlinx.serialization.Serializable

enum class AssistantRole {
    USER,
    ASSISTANT,
    SYSTEM
}

@Serializable
data class AssistantMessage(
    val role: AssistantRole,
    val content: String,
    val timestamp: Long,
    val itemContextIds: List<String> = emptyList()
)

enum class AssistantActionType {
    APPLY_DRAFT_UPDATE,
    COPY_TEXT,
    OPEN_POSTING_ASSIST,
    OPEN_SHARE,
    OPEN_URL
}

@Serializable
data class AssistantAction(
    val type: AssistantActionType,
    val payload: Map<String, String> = emptyMap()
)

/**
 * Safety information returned by the AI Gateway.
 * Contains stable reason codes for client handling.
 */
@Serializable
data class SafetyResponse(
    val blocked: Boolean = false,
    val reasonCode: String? = null,
    val requestId: String? = null
)

/**
 * Response from the AI Gateway.
 * The gateway may use 'reply' or 'content' for the text response.
 */
@Serializable
data class AssistantResponse(
    /** Primary response field from gateway (newer API) */
    val reply: String? = null,
    /** Legacy response field (for backwards compatibility) */
    val content: String? = null,
    val actions: List<AssistantAction> = emptyList(),
    val citationsMetadata: Map<String, String>? = null,
    val safety: SafetyResponse? = null,
    val correlationId: String? = null
) {
    /** Get the response text, preferring 'reply' over 'content' */
    val text: String get() = reply ?: content ?: ""
}

@Serializable
data class ItemAttributeSnapshot(
    val key: String,
    val value: String,
    val confidence: Float? = null
)

@Serializable
data class ItemContextSnapshot(
    val itemId: String,
    val title: String?,
    val description: String?,
    val category: String?,
    val confidence: Float?,
    val attributes: List<ItemAttributeSnapshot> = emptyList(),
    val priceEstimate: Double? = null,
    val photosCount: Int = 0,
    val exportProfileId: ExportProfileId = ExportProfileId.GENERIC
)

@Serializable
data class ExportProfileSnapshot(
    val id: ExportProfileId,
    val displayName: String
)

@Serializable
data class AssistantPromptRequest(
    val items: List<ItemContextSnapshot>,
    val conversation: List<AssistantMessage>,
    val userMessage: String,
    val exportProfile: ExportProfileSnapshot,
    val systemPrompt: String
)

object AssistantPromptBuilder {
    private const val SYSTEM_PROMPT = """
You are Scanium Seller Assistant. Provide safe, honest guidance for listing items.
- Do not scrape third-party sites without official APIs.
- Do not auto-fill forms, automate posting, or ask for passwords.
- Use provided item context and export profile; if data is missing, ask clarifying questions.
"""

    fun buildRequest(
        items: List<ItemContextSnapshot>,
        userMessage: String,
        exportProfile: ExportProfileDefinition,
        conversation: List<AssistantMessage> = emptyList()
    ): AssistantPromptRequest {
        return AssistantPromptRequest(
            items = items,
            conversation = conversation,
            userMessage = userMessage.trim(),
            exportProfile = ExportProfileSnapshot(
                id = exportProfile.id,
                displayName = exportProfile.displayName
            ),
            systemPrompt = SYSTEM_PROMPT.trim()
        )
    }
}

object ItemContextSnapshotBuilder {
    fun fromDraft(draft: ListingDraft): ItemContextSnapshot {
        val sortedAttributes = draft.fields.entries
            .sortedBy { it.key.wireValue }
            .mapNotNull { (key, field) ->
                val value = field.value?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ItemAttributeSnapshot(
                    key = key.wireValue,
                    value = value,
                    confidence = field.confidence
                )
            }

        val categoryField = draft.fields[DraftFieldKey.CATEGORY]
        val confidence = categoryField?.confidence ?: draft.title.confidence

        return ItemContextSnapshot(
            itemId = draft.itemId,
            title = draft.title.value?.takeIf { it.isNotBlank() },
            description = draft.description.value?.takeIf { it.isNotBlank() },
            category = categoryField?.value?.takeIf { it.isNotBlank() },
            confidence = confidence,
            attributes = sortedAttributes,
            priceEstimate = draft.price.value,
            photosCount = draft.photos.size,
            exportProfileId = draft.profile
        )
    }
}
