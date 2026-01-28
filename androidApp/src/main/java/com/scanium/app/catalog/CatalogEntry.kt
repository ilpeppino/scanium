package com.scanium.app.catalog

data class CatalogEntry(
    val id: String,
    val displayLabel: String,
    val aliases: List<String> = emptyList(),
    val popularity: Int = 50,
    val metadata: Map<String, String> = emptyMap(),
) {
    val searchTerms: List<String>
        get() = listOf(displayLabel) + aliases
}

data class CatalogSearchResult(
    val entry: CatalogEntry,
    val matchScore: Float,
    val matchType: MatchType,
) {
    enum class MatchType {
        EXACT,
        PREFIX,
        WORD_BOUNDARY,
        CONTAINS,
        ALIAS,
    }
}
