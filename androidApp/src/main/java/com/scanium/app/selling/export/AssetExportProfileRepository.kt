package com.scanium.app.selling.export

import android.content.Context
import com.scanium.app.listing.ExportProfileDefinition
import com.scanium.app.listing.ExportProfileId
import com.scanium.app.listing.ExportProfileRepository
import com.scanium.app.listing.ExportProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class AssetExportProfileRepository(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : ExportProfileRepository {

    @Volatile
    private var cachedProfiles: List<ExportProfileDefinition>? = null
    @Volatile
    private var cachedDefaultId: ExportProfileId? = null

    override suspend fun getProfiles(): List<ExportProfileDefinition> {
        return loadProfiles().first
    }

    override suspend fun getProfile(id: ExportProfileId): ExportProfileDefinition? {
        return loadProfiles().first.firstOrNull { it.id == id }
    }

    override suspend fun getDefaultProfileId(): ExportProfileId {
        return loadProfiles().second
    }

    private suspend fun loadProfiles(): Pair<List<ExportProfileDefinition>, ExportProfileId> {
        cachedProfiles?.let { profiles ->
            val defaultId = cachedDefaultId ?: ExportProfiles.generic().id
            return profiles to defaultId
        }

        return withContext(Dispatchers.IO) {
            val fallback = listOf(ExportProfiles.generic())
            val result: Pair<List<ExportProfileDefinition>, ExportProfileId> = runCatching {
                val indexJson = readAssetText(INDEX_PATH)
                val index = json.decodeFromString<ExportProfilesIndex>(indexJson)
                val definitions = index.profiles.mapNotNull { fileName ->
                    runCatching {
                        val content = readAssetText("export_profiles/$fileName")
                        json.decodeFromString<ExportProfileDefinition>(content)
                    }.getOrNull()
                }.ifEmpty { fallback }
                val defaultProfileId = index.defaultProfileId
                    .takeIf { it.isNotBlank() }
                    ?.let { ExportProfileId(it) }
                    ?: ExportProfiles.generic().id
                definitions to defaultProfileId
            }.getOrElse { fallback to ExportProfiles.generic().id }

            cachedProfiles = result.first
            cachedDefaultId = result.second
            result
        }
    }

    private fun readAssetText(path: String): String {
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }

    companion object {
        private const val INDEX_PATH = "export_profiles/index.json"
    }
}

@Serializable
data class ExportProfilesIndex(
    val defaultProfileId: String,
    val profiles: List<String>
)
