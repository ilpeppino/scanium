package com.scanium.domain.repository

import com.scanium.app.ml.ItemCategory

/**
 * Interface for mapping domain category IDs to UI-displayable categories.
 *
 * Responsibilities:
 * - Map fine-grained domainCategoryId (e.g., "furniture_sofa") to coarse ItemCategory enum
 * - Provide display names and metadata for categories
 * - Handle category hierarchies (parent-child relationships)
 *
 * Implementations:
 * - BasicCategoryEngine: Simple string matching (current implementation in core-domainpack)
 * - DomainPackCategoryEngine: Uses Domain Pack JSON configuration
 * - CachedCategoryEngine: Wraps another engine with caching
 *
 * Usage:
 * ```
 * val engine: CategoryEngine = DomainPackCategoryEngine(repository)
 * val mapping = engine.mapCategory("furniture_sofa")
 * println("${mapping.displayName} -> ${mapping.itemCategory}")
 * // Output: "Sofa -> HOME_GOOD"
 * ```
 */
interface CategoryEngine {
    /**
     * Map domain category ID to UI category.
     *
     * @param domainCategoryId Fine-grained category ID (e.g., "furniture_sofa")
     * @return Category mapping with display name and coarse category
     */
    suspend fun mapCategory(domainCategoryId: String): CategoryMapping

    /**
     * Get all available categories in priority order.
     * Useful for category selection UI, filters, etc.
     *
     * @return List of category mappings, sorted by priority (high → low)
     */
    suspend fun getAllCategories(): List<CategoryMapping> = emptyList()

    /**
     * Search categories by query string.
     * Useful for search/autocomplete UI.
     *
     * @param query Search query (matches display name, tags, etc.)
     * @return Matching categories, sorted by relevance
     */
    suspend fun searchCategories(query: String): List<CategoryMapping> = emptyList()
}

/**
 * Mapping from domain category ID to UI representation.
 *
 * @property domainCategoryId Fine-grained ID (e.g., "furniture_sofa")
 * @property itemCategory Coarse category enum (e.g., HOME_GOOD)
 * @property displayName Human-readable name (e.g., "Sofa")
 * @property description Optional description for category
 * @property iconName Optional icon identifier for UI
 * @property tags Searchable tags (e.g., ["couch", "seating", "living room"])
 * @property parentCategoryId Optional parent in hierarchy (e.g., "furniture" for "furniture_sofa")
 * @property priority Sorting priority (higher = more important)
 */
data class CategoryMapping(
    val domainCategoryId: String,
    val itemCategory: ItemCategory,
    val displayName: String,
    val description: String? = null,
    val iconName: String? = null,
    val tags: List<String> = emptyList(),
    val parentCategoryId: String? = null,
    val priority: Int = 0,
) {
    /**
     * Check if category matches search query.
     * Matches against: displayName, tags, description
     */
    fun matches(query: String): Boolean {
        val q = query.trim().lowercase()
        return displayName.lowercase().contains(q) ||
            tags.any { it.lowercase().contains(q) } ||
            description?.lowercase()?.contains(q) == true
    }
}

/**
 * Exception thrown when category mapping fails.
 */
class CategoryMappingException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
