package com.scanium.app.model

/**
 * Unified settings model for "Primary region & language" feature.
 * This model provides a single source of truth for region/language preferences,
 * with follow-or-customize overrides for app language, AI language, marketplace country, and TTS.
 */

/**
 * Generic sealed class for settings that can either follow the primary value
 * or use a custom override.
 *
 * @param T The type of the custom value
 */
sealed class FollowOrCustom<T> {
    /**
     * Follow the primary setting (no override).
     */
    data class FollowPrimary<T>(
        val unit: Unit = Unit,
    ) : FollowOrCustom<T>()

    /**
     * Use a custom value (override the primary setting).
     */
    data class Custom<T>(
        val value: T,
    ) : FollowOrCustom<T>()

    /**
     * Get the effective value, resolving FollowPrimary to the provided primary value.
     */
    fun resolve(primaryValue: T): T =
        when (this) {
            is FollowPrimary -> primaryValue
            is Custom -> value
        }

    companion object {
        /**
         * Create a FollowPrimary instance (type-safe helper).
         */
        fun <T> followPrimary(): FollowOrCustom<T> = FollowPrimary()

        /**
         * Create a Custom instance with the given value.
         */
        fun <T> custom(value: T): FollowOrCustom<T> = Custom(value)
    }
}

/**
 * AI language choice - can be a specific language tag or auto-detect mode.
 */
sealed class AiLanguageChoice {
    /**
     * Use a specific language tag for AI output.
     */
    data class LanguageTag(
        val tag: String,
    ) : AiLanguageChoice()

    /**
     * Auto-detect language from speech input (fallback to primary for output).
     */
    data object AutoDetect : AiLanguageChoice()

    fun toLanguageTag(fallback: String): String =
        when (this) {
            is LanguageTag -> tag
            is AutoDetect -> fallback
        }
}

/**
 * TTS voice language choice - can follow AI language, follow primary, or use custom.
 */
sealed class TtsLanguageChoice {
    /**
     * Follow the effective AI output language.
     */
    data object FollowAiLanguage : TtsLanguageChoice()

    /**
     * Follow the primary language setting.
     */
    data object FollowPrimary : TtsLanguageChoice()

    /**
     * Use a custom language tag for TTS.
     */
    data class Custom(
        val languageTag: String,
    ) : TtsLanguageChoice()
}

/**
 * Data class representing the complete unified settings state.
 * This is the source of truth for all language and region preferences.
 */
data class UnifiedSettingsState(
    // Primary settings (source of truth)
    // ISO 2-letter country code ("NL", "DE", etc.)
    val primaryRegionCountry: String,
    // Language tag ("en", "nl", "it", "fr", "de", "pt-BR")
    val primaryLanguage: String,
    // Override wrappers (follow primary or customize)
    // Language tag
    val appLanguageSetting: FollowOrCustom<String>,
    val aiLanguageSetting: FollowOrCustom<AiLanguageChoice>,
    // Country code
    val marketplaceCountrySetting: FollowOrCustom<String>,
    // TTS alignment
    val ttsLanguageSetting: TtsLanguageChoice,
    // Optional: Last detected spoken language for AutoDetect fallback
    val lastDetectedSpokenLanguage: String? = null,
)
