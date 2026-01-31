package com.scanium.app.catalog

import kotlinx.coroutines.flow.Flow

interface CatalogSearch {
    suspend fun search(
        type: CatalogType,
        query: String,
        limit: Int = 20,
    ): List<CatalogSearchResult>

    fun searchFlow(
        type: CatalogType,
        queryFlow: Flow<String>,
        limit: Int = 20,
    ): Flow<List<CatalogSearchResult>>

    suspend fun getById(
        type: CatalogType,
        id: String,
    ): CatalogEntry?

    suspend fun exists(
        type: CatalogType,
        id: String,
    ): Boolean
}
