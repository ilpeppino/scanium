package com.scanium.app.catalog.impl

import android.content.Context
import android.util.Log
import com.scanium.app.catalog.CatalogEntry
import com.scanium.app.catalog.CatalogSource
import com.scanium.app.catalog.CatalogType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
private data class CatalogPayload(
    val version: Int = 0,
    val lastUpdated: String? = null,
    val entries: List<CatalogEntryDto> = emptyList(),
)

@Serializable
private data class CatalogEntryDto(
    val id: String,
    val displayLabel: String,
    val aliases: List<String> = emptyList(),
    val popularity: Int = 50,
    val category: String? = null,
    val parentCategory: String? = null,
)

class AssetCatalogSource
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val json: Json = DEFAULT_JSON,
) : CatalogSource {
    private val cache = mutableMapOf<CatalogType, CatalogPayload>()
    private val entriesCache = mutableMapOf<CatalogType, List<CatalogEntry>>()

    override suspend fun loadCatalog(type: CatalogType): List<CatalogEntry> =
        withContext(Dispatchers.IO) {
            entriesCache[type]?.let { return@withContext it }
            val payload = loadPayload(type) ?: return@withContext emptyList()
            val entries = payload.entries.map { dto -> dto.toEntry() }
            entriesCache[type] = entries
            entries
        }

    override suspend fun hasUpdate(type: CatalogType): Boolean = false

    override suspend fun getVersion(type: CatalogType): Int =
        withContext(Dispatchers.IO) {
            loadPayload(type)?.version ?: 0
        }

    private fun loadPayload(type: CatalogType): CatalogPayload? {
        cache[type]?.let { return it }
        return runCatching {
            context.assets.open(type.assetPath).bufferedReader().use { reader ->
                json.decodeFromString(CatalogPayload.serializer(), reader.readText())
            }
        }.onFailure { error ->
            Log.w("AssetCatalogSource", "Failed to load catalog ${type.assetPath}", error)
        }.getOrNull()?.also { payload ->
            cache[type] = payload
        }
    }

    private fun CatalogEntryDto.toEntry(): CatalogEntry {
        val metadata = buildMap {
            category?.let { put("category", it) }
            parentCategory?.let { put("parentCategory", it) }
        }
        return CatalogEntry(
            id = id,
            displayLabel = displayLabel,
            aliases = aliases,
            popularity = popularity,
            metadata = metadata,
        )
    }

    companion object {
        private val DEFAULT_JSON =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
    }
}
