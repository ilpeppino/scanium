package com.scanium.app.selling.assistant

/**
 * Represents a structured listing display model for assistant responses.
 *
 * When an assistant response contains structured listing information (JSON or heuristic),
 * this model encapsulates the parsed fields for specialized UI rendering.
 *
 * @property title The listing title (required, bold, prominent display)
 * @property priceSuggestion Optional pricing suggestion (primary color, semibold)
 * @property condition Optional condition description
 * @property description Optional detailed description
 * @property highlights Optional list of key highlights (rendered as bullets)
 * @property tags Optional list of category tags (rendered as chips)
 */
data class AssistantDisplayModel(
    val title: String,
    val priceSuggestion: String? = null,
    val condition: String? = null,
    val description: String? = null,
    val highlights: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)
