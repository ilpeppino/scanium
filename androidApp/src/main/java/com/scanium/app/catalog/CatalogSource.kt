package com.scanium.app.catalog

interface CatalogSource {
    suspend fun loadCatalog(type: CatalogType): List<CatalogEntry>

    suspend fun hasUpdate(type: CatalogType): Boolean

    suspend fun getVersion(type: CatalogType): Int
}
