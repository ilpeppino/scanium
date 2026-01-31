package com.scanium.app.ai

import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.items.VisionAttributes

/**
 * Request payload for AI generation of listing content.
 *
 * This is used when the user taps "AI Generate" on the edit page.
 * The AI will generate a title and description based on the provided attributes.
 *
 * @property attributesStructured Structured attributes (brand, color, etc.)
 * @property attributesSummaryText The human-readable summary text
 * @property summaryTextUserEdited Whether the user has manually edited the summary
 * @property photosCount Number of photos attached to the item
 * @property visionFacts Optional vision attributes from Google Vision API
 * @property category Item category for context
 * @property condition Item condition (New, Like New, Good, etc.)
 */
data class AIGenerationRequest(
    val attributesStructured: Map<String, ItemAttribute>,
    val attributesSummaryText: String,
    val summaryTextUserEdited: Boolean,
    val photosCount: Int,
    val visionFacts: VisionAttributes?,
    val category: String? = null,
    val condition: String? = null,
)

/**
 * Response from AI generation containing the listing content.
 *
 * @property title Generated listing title (concise, ~80 chars max)
 * @property description Generated listing description (detailed, ~500 chars)
 * @property verifyList List of missing or uncertain fields the user should verify
 * @property suggestedPrice Optional price suggestion based on detected attributes
 * @property confidence Overall confidence score (0.0-1.0) of the generation
 */
data class AIGenerationResponse(
    val title: String,
    val description: String,
    val verifyList: List<String> = emptyList(),
    val suggestedPrice: Double? = null,
    val confidence: Float = 0.0f,
)

/**
 * Status of an AI generation request.
 */
sealed class AIGenerationStatus {
    /** No generation in progress */
    data object Idle : AIGenerationStatus()

    /** Generation is in progress */
    data object Loading : AIGenerationStatus()

    /** Generation completed successfully */
    data class Success(
        val response: AIGenerationResponse,
    ) : AIGenerationStatus()

    /** Generation failed */
    data class Error(
        val message: String,
    ) : AIGenerationStatus()
}

/**
 * Extension to convert ScannedItem to AIGenerationRequest.
 */
fun com.scanium.app.items.ScannedItem.toAIGenerationRequest(): AIGenerationRequest {
    // +1 for primary photo
    val photoCount = additionalPhotos.size + 1
    return AIGenerationRequest(
        attributesStructured = attributes,
        attributesSummaryText = attributesSummaryText,
        summaryTextUserEdited = summaryTextUserEdited,
        photosCount = photoCount,
        visionFacts = visionAttributes,
        category = category.displayName,
        // Populated by caller if available
        condition = null,
    )
}
