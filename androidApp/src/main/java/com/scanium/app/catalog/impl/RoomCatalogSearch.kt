package com.scanium.app.catalog.impl

import com.scanium.app.catalog.CatalogEntry
import com.scanium.app.catalog.CatalogSearch
import com.scanium.app.catalog.CatalogSearchResult
import com.scanium.app.catalog.CatalogType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RoomCatalogSearch
@Inject
constructor() : CatalogSearch {
    override suspend fun search(type: CatalogType, query: String, limit: Int): List<CatalogSearchResult> =
        TODO("Implement when catalog exceeds in-memory thresholds")

    override fun searchFlow(
        type: CatalogType,
        queryFlow: Flow<String>,
        limit: Int,
    ): Flow<List<CatalogSearchResult>> =
        TODO("Implement when catalog exceeds in-memory thresholds")

    override suspend fun getById(type: CatalogType, id: String): CatalogEntry? =
        TODO("Implement when catalog exceeds in-memory thresholds")

    override suspend fun exists(type: CatalogType, id: String): Boolean =
        TODO("Implement when catalog exceeds in-memory thresholds")
}
