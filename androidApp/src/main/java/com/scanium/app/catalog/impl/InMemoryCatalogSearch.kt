package com.scanium.app.catalog.impl

import com.scanium.app.catalog.CatalogEntry
import com.scanium.app.catalog.CatalogSearch
import com.scanium.app.catalog.CatalogSearchResult
import com.scanium.app.catalog.CatalogSearchResult.MatchType
import com.scanium.app.catalog.CatalogSource
import com.scanium.app.catalog.CatalogType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import javax.inject.Inject

class InMemoryCatalogSearch
    @Inject
    constructor(
        private val source: CatalogSource,
    ) : CatalogSearch {
        private val catalogCache = mutableMapOf<CatalogType, List<CatalogEntry>>()

        override suspend fun search(
            type: CatalogType,
            query: String,
            limit: Int,
        ): List<CatalogSearchResult> {
            val normalizedQuery = query.trim().lowercase()
            if (normalizedQuery.isBlank()) return emptyList()

            val catalog = loadCatalog(type)
            return catalog
                .mapNotNull { entry -> scoreEntry(entry, normalizedQuery) }
                .sortedWith(
                    compareBy<CatalogSearchResult> { it.matchType.ordinal }
                        .thenByDescending { it.entry.popularity }
                        .thenBy { it.entry.displayLabel.lowercase() },
                )
                .take(limit)
        }

        override fun searchFlow(
            type: CatalogType,
            queryFlow: Flow<String>,
            limit: Int,
        ): Flow<List<CatalogSearchResult>> =
            queryFlow
                .mapLatest { it.trim() }
                .distinctUntilChanged()
                .debounce(200)
                .mapLatest { query -> search(type, query, limit) }

        override suspend fun getById(
            type: CatalogType,
            id: String,
        ): CatalogEntry? {
            val catalog = loadCatalog(type)
            return catalog.firstOrNull { it.id == id }
        }

        override suspend fun exists(
            type: CatalogType,
            id: String,
        ): Boolean = getById(type, id) != null

        private suspend fun loadCatalog(type: CatalogType): List<CatalogEntry> =
            catalogCache[type] ?: withContext(Dispatchers.IO) {
                val entries = source.loadCatalog(type)
                catalogCache[type] = entries
                entries
            }

        private fun scoreEntry(
            entry: CatalogEntry,
            query: String,
        ): CatalogSearchResult? {
            val label = entry.displayLabel.lowercase()
            if (label == query) return result(entry, MatchType.EXACT, 1.0f)
            if (label.startsWith(query)) return result(entry, MatchType.PREFIX, scoreRatio(query, label))

            val wordBoundaryMatches =
                label
                    .split(Regex("[\\s\\-_]+"))
                    .any { it.startsWith(query) }
            if (wordBoundaryMatches) return result(entry, MatchType.WORD_BOUNDARY, 0.7f)

            if (label.contains(query)) return result(entry, MatchType.CONTAINS, 0.4f)

            val aliasMatch = entry.aliases.any { alias -> alias.lowercase().contains(query) }
            if (aliasMatch) return result(entry, MatchType.ALIAS, 0.5f)

            return null
        }

        private fun scoreRatio(
            query: String,
            label: String,
        ): Float = if (label.isEmpty()) 0f else query.length.toFloat() / label.length.toFloat()

        private fun result(
            entry: CatalogEntry,
            matchType: MatchType,
            score: Float,
        ): CatalogSearchResult = CatalogSearchResult(entry = entry, matchScore = score, matchType = matchType)
    }
