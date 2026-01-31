package com.scanium.app.catalog

import com.scanium.app.catalog.api.CatalogApi
import com.scanium.app.catalog.model.CatalogModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class CatalogRepository(
    private val api: CatalogApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val brandsCache = mutableMapOf<String, List<String>>()
    private val modelsCache = mutableMapOf<ModelsKey, List<CatalogModel>>()

    suspend fun brands(subtype: String): List<String> =
        withContext(ioDispatcher) {
            val cached = mutex.withLock { brandsCache[subtype] }
            if (cached != null) return@withContext cached

            val response = api.getBrands(subtype)
            val brands = response.brands
            mutex.withLock { brandsCache[subtype] = brands }
            brands
        }

    suspend fun models(
        subtype: String,
        brand: String,
    ): List<CatalogModel> =
        withContext(ioDispatcher) {
            val key = ModelsKey(subtype = subtype, brand = brand)
            val cached = mutex.withLock { modelsCache[key] }
            if (cached != null) return@withContext cached

            val response = api.getModels(subtype, brand)
            val models = response.models
            mutex.withLock { modelsCache[key] = models }
            models
        }

    private data class ModelsKey(
        val subtype: String,
        val brand: String,
    )
}
