package com.scanium.app.selling.assistant.local

import com.scanium.app.model.ItemContextSnapshot

/**
 * Structured local suggestions for offline assistant fallback.
 * All fields are deterministic and generated from item context.
 */
data class LocalSuggestions(
    /** Questions the user should consider answering */
    val suggestedQuestions: List<String>,
    /** Bullet points for the listing description */
    val suggestedBullets: List<String>,
    /** A template for the description field */
    val suggestedDescriptionTemplate: String,
    /** Category-specific defects to check for */
    val suggestedDefectsChecklist: List<String>,
    /** Prompts about missing information */
    val missingInfoPrompts: List<MissingInfoPrompt>,
    /** Best photos to take next based on category */
    val suggestedPhotos: List<String>,
    /** Suggested title if current title is weak */
    val suggestedTitle: String?,
)

/**
 * A prompt about missing information with explanation.
 */
data class MissingInfoPrompt(
    val field: String,
    val prompt: String,
    val benefit: String,
)

/**
 * Lightweight, deterministic suggestion engine for offline assistant fallback.
 *
 * This engine analyzes ItemContextSnapshot data and generates actionable suggestions
 * without any ML models or network calls. All logic is rule-based and explainable.
 *
 * Part of OFFLINE-ASSIST: Provides useful guidance when online assistant is unavailable.
 */
class LocalSuggestionEngine {
    /**
     * Generate structured suggestions from item context.
     * Output is fully deterministic: same input always yields same output.
     */
    fun generateSuggestions(snapshot: ItemContextSnapshot): LocalSuggestions {
        val category = CategoryClassifier.classify(snapshot.category)
        val attributes = snapshot.attributes.associate { it.key.lowercase() to it.value }

        return LocalSuggestions(
            suggestedQuestions = SuggestionRules.suggestedQuestions(snapshot, category, attributes),
            suggestedBullets = SuggestionRules.suggestedBullets(snapshot, category, attributes),
            suggestedDescriptionTemplate = SuggestionRules.descriptionTemplate(snapshot, category),
            suggestedDefectsChecklist = SuggestionRules.defectsChecklist(category),
            missingInfoPrompts = SuggestionRules.missingInfoPrompts(snapshot, category, attributes),
            suggestedPhotos = SuggestionRules.photoSuggestions(category, snapshot.photosCount),
            suggestedTitle = SuggestionRules.suggestedTitle(snapshot, category, attributes),
        )
    }

    /**
     * Generate suggestions for multiple items, using first as primary.
     */
    fun generateSuggestions(snapshots: List<ItemContextSnapshot>): LocalSuggestions? {
        val primary = snapshots.firstOrNull() ?: return null
        return generateSuggestions(primary)
    }
}

/**
 * Classifies raw category strings into normalized categories.
 */
internal object CategoryClassifier {
    fun classify(rawCategory: String?): ItemCategory {
        val normalized = rawCategory?.lowercase()?.trim() ?: return ItemCategory.GENERAL

        return when {
            normalized.containsAny(
                "electronic",
                "phone",
                "computer",
                "laptop",
                "tablet",
                "camera",
                "audio",
                "gaming",
                "console",
                "tv",
                "television",
            ) -> {
                ItemCategory.ELECTRONICS
            }

            normalized.containsAny(
                "fashion",
                "clothing",
                "clothes",
                "shoes",
                "apparel",
                "dress",
                "shirt",
                "pants",
                "jacket",
                "accessories",
                "bags",
                "jewelry",
                "watch",
            ) -> {
                ItemCategory.FASHION
            }

            normalized.containsAny(
                "furniture",
                "home",
                "decor",
                "lighting",
                "chair",
                "table",
                "sofa",
                "bed",
                "desk",
                "storage",
                "shelf",
                "cabinet",
            ) -> {
                ItemCategory.HOME_FURNITURE
            }

            normalized.containsAny("kitchen", "cookware", "appliance", "dining", "bakeware") -> {
                ItemCategory.KITCHEN
            }

            normalized.containsAny("toy", "game", "puzzle", "lego", "doll", "action figure", "board game") -> {
                ItemCategory.TOYS_GAMES
            }

            normalized.containsAny("sport", "fitness", "outdoor", "bike", "bicycle", "camping", "hiking", "exercise", "gym") -> {
                ItemCategory.SPORTS_OUTDOOR
            }

            normalized.containsAny("book", "media", "dvd", "cd", "vinyl", "record", "magazine", "comic") -> {
                ItemCategory.BOOKS_MEDIA
            }

            else -> {
                ItemCategory.GENERAL
            }
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean = keywords.any { this.contains(it) }
}

/**
 * Normalized item categories for suggestion logic.
 */
enum class ItemCategory {
    ELECTRONICS,
    FASHION,
    HOME_FURNITURE,
    KITCHEN,
    TOYS_GAMES,
    SPORTS_OUTDOOR,
    BOOKS_MEDIA,
    GENERAL,
}
