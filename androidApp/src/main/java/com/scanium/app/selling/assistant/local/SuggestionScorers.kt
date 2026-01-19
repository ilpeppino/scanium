package com.scanium.app.selling.assistant.local

internal enum class PhotoSuggestionTier {
    ESSENTIAL,
    DETAIL,
}

internal object SuggestionScorers {
    fun isTitleShort(
        title: String?,
        minLength: Int,
    ): Boolean {
        val trimmed = title?.trim().orEmpty()
        return trimmed.isBlank() || trimmed.length < minLength
    }

    fun shouldSuggestTitle(
        title: String?,
        minLength: Int,
    ): Boolean {
        val trimmed = title?.trim().orEmpty()
        return trimmed.isBlank() || trimmed.length < minLength
    }

    fun photoSuggestionTier(photoCount: Int): PhotoSuggestionTier =
        if (photoCount < 2) PhotoSuggestionTier.ESSENTIAL else PhotoSuggestionTier.DETAIL
}
