package com.scanium.app.domain.repository

import android.content.Context
import android.util.Log
import com.scanium.app.domain.R
import com.scanium.app.domain.config.DomainPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Repository implementation that loads Domain Packs from local app resources.
 *
 * This implementation:
 * - Reads JSON from res/raw/home_resale_domain_pack.json
 * - Parses using Kotlinx Serialization
 * - Caches the result in memory
 * - Provides fallback to a minimal default pack on error
 *
 * Thread-safety: The cache is synchronized to ensure thread-safe access.
 *
 * @param context Android context for accessing resources
 */
class LocalDomainPackRepository(
    private val context: Context,
) : DomainPackRepository {
    // Thread-safe cache for parsed Domain Pack
    @Volatile
    private var cachedPack: DomainPack? = null
    private val cacheLock = Any()

    // JSON parser with lenient settings for better error tolerance
    private val json =
        Json {
            ignoreUnknownKeys = true // Ignore unknown JSON fields
            isLenient = true // Allow relaxed JSON syntax
            prettyPrint = false
        }

    /**
     * Get the active Domain Pack, loading from resources if not cached.
     *
     * This method is safe to call repeatedly; the pack is only parsed once.
     */
    override suspend fun getActiveDomainPack(): DomainPack =
        withContext(Dispatchers.IO) {
            // Double-checked locking for thread-safe caching
            cachedPack?.let { return@withContext it }

            synchronized(cacheLock) {
                // Check again inside synchronized block
                cachedPack?.let { return@withContext it }

                try {
                    Log.d(TAG, "Loading Domain Pack from resources...")

                    // Read JSON from res/raw
                    val jsonString =
                        context.resources
                            .openRawResource(R.raw.home_resale_domain_pack)
                            .bufferedReader()
                            .use { it.readText() }

                    // Parse JSON to DomainPack
                    val pack = json.decodeFromString<DomainPack>(jsonString)

                    // Validate the pack
                    validateDomainPack(pack)

                    Log.d(
                        TAG,
                        "Domain Pack loaded: ${pack.id} v${pack.version} " +
                            "(${pack.categories.size} categories, ${pack.attributes.size} attributes)",
                    )

                    // Cache and return
                    cachedPack = pack
                    pack
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load Domain Pack", e)

                    // Determine specific error type
                    val exception =
                        when (e) {
                            is SerializationException ->
                                DomainPackLoadException(
                                    "Failed to parse Domain Pack JSON: ${e.message}",
                                    e,
                                )

                            else ->
                                DomainPackLoadException(
                                    "Failed to load Domain Pack: ${e.message}",
                                    e,
                                )
                        }

                    // Return fallback pack instead of throwing
                    Log.w(TAG, "Using fallback empty Domain Pack")
                    val fallback = createFallbackDomainPack()
                    cachedPack = fallback
                    fallback
                }
            }
        }

    /**
     * Validate the loaded Domain Pack for correctness.
     *
     * Checks:
     * - At least one enabled category exists
     * - Category IDs are unique
     * - itemCategoryName values are valid (logged as warnings if invalid)
     *
     * @throws DomainPackLoadException if validation fails
     */
    private fun validateDomainPack(pack: DomainPack) {
        // Check for at least one enabled category
        if (pack.getEnabledCategories().isEmpty()) {
            Log.w(TAG, "Domain Pack has no enabled categories")
        }

        // Check for duplicate category IDs
        val categoryIds = pack.categories.map { it.id }
        val duplicates = categoryIds.groupingBy { it }.eachCount().filter { it.value > 1 }
        if (duplicates.isNotEmpty()) {
            throw DomainPackLoadException(
                "Domain Pack contains duplicate category IDs: ${duplicates.keys}",
            )
        }

        // Validate itemCategoryName values (log warnings for invalid ones)
        pack.categories.forEach { category ->
            try {
                // This will throw if the enum value doesn't exist
                com.scanium.app.ml.ItemCategory.valueOf(category.itemCategoryName)
            } catch (e: IllegalArgumentException) {
                Log.w(
                    TAG,
                    "Category '${category.id}' has invalid itemCategoryName: " +
                        "'${category.itemCategoryName}' (not a valid ItemCategory enum value)",
                )
            }
        }

        Log.d(TAG, "Domain Pack validation passed")
    }

    /**
     * Create a minimal fallback Domain Pack for error cases.
     *
     * This ensures the app never crashes due to a missing or invalid pack.
     * The fallback pack has no categories or attributes.
     */
    private fun createFallbackDomainPack(): DomainPack {
        return DomainPack(
            id = "fallback",
            name = "Fallback Pack",
            version = "0.0.0",
            description = "Emergency fallback pack used when primary pack fails to load",
            categories = emptyList(),
            attributes = emptyList(),
        )
    }

    /**
     * Clear the cached Domain Pack (useful for testing or forcing reload).
     */
    fun clearCache() {
        synchronized(cacheLock) {
            cachedPack = null
        }
        Log.d(TAG, "Domain Pack cache cleared")
    }

    companion object {
        private const val TAG = "LocalDomainPackRepo"
    }
}
