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
    ADD_ATTRIBUTES,
    APPLY_DRAFT_UPDATE,
    COPY_TEXT,
    OPEN_POSTING_ASSIST,
    OPEN_SHARE,
    OPEN_URL,
    SUGGEST_NEXT_PHOTO
}

@Serializable
data class AssistantAction(
    val type: AssistantActionType,
    val payload: Map<String, String> = emptyMap(),
    /** Label to display on the action button/chip */
    val label: String? = null,
    /** Whether this action requires user confirmation (e.g., for LOW confidence) */
    val requiresConfirmation: Boolean = false
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
 * Confidence tier for assistant responses.
 * HIGH: Strong visual evidence supports the answer.
 * MED: Some evidence, but not conclusive.
 * LOW: Insufficient evidence; response is speculative.
 */
enum class ConfidenceTier {
    HIGH,
    MED,
    LOW
}

/**
 * Evidence bullet derived from VisualFacts.
 */
@Serializable
data class EvidenceBullet(
    /** Type of evidence */
    val type: String,
    /** Human-readable evidence statement */
    val text: String
)

/**
 * Suggested attribute with confidence.
 */
@Serializable
data class SuggestedAttribute(
    /** Attribute key (brand, model, color, etc.) */
    val key: String,
    /** Suggested value */
    val value: String,
    /** Confidence tier for this suggestion */
    val confidence: ConfidenceTier,
    /** Source of suggestion (ocr, logo, color, etc.) */
    val source: String? = null
)

/**
 * Suggested draft updates (title, description).
 */
@Serializable
data class SuggestedDraftUpdate(
    /** Field to update */
    val field: String,
    /** Suggested value */
    val value: String,
    /** Confidence tier for this suggestion */
    val confidence: ConfidenceTier,
    /** Whether this requires user confirmation before applying */
    val requiresConfirmation: Boolean = false
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
    val correlationId: String? = null,
    /** Confidence tier for the response (when visual evidence is used) */
    val confidenceTier: ConfidenceTier? = null,
    /** Evidence bullets referencing VisualFacts */
    val evidence: List<EvidenceBullet> = emptyList(),
    /** Suggested attributes derived from visual evidence */
    val suggestedAttributes: List<SuggestedAttribute> = emptyList(),
    /** Suggested draft updates (title, description) */
    val suggestedDraftUpdates: List<SuggestedDraftUpdate> = emptyList(),
    /** Suggested next photo instruction when evidence is insufficient */
    val suggestedNextPhoto: String? = null
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
