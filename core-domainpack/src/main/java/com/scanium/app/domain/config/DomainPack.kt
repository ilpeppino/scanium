package com.scanium.app.domain.config

import kotlinx.serialization.Serializable

/**
 * Represents a complete Domain Pack configuration.
 *
 * A Domain Pack is a self-contained, config-driven specification of:
 * - Category taxonomy (fine-grained categories like "sofa", "laptop", "running shoes")
 * - Attribute definitions (properties to extract like brand, color, condition)
 * - Extraction methods (OCR, CLIP, barcode, cloud, etc.)
 * - CLIP prompts for on-device classification (future use)
 *
 * Domain Packs enable:
 * - **Multi-tenancy**: Different business domains can have different category sets
 * - **Configuration-driven**: Add/modify categories without code changes
 * - **Extensibility**: Support for future classification methods (CLIP, cloud)
 * - **Versioning**: Track changes to category definitions over time
 *
 * Example use cases:
 * - "home_resale": Second-hand furniture, electronics, clothing
 * - "retail_inventory": Store inventory management categories
 * - "warehouse_logistics": Shipping and logistics categories
 *
 * The Domain Pack is loaded from JSON (e.g., res/raw/home_resale_domain_pack.json)
 * and parsed at runtime. It provides a bridge between:
 * - Fine-grained domain categories (config-driven)
 * - Coarse-grained ItemCategory enum (code-based)
 *
 * @property id Unique identifier for this domain pack (e.g., "home_resale")
 * @property name Human-readable name (e.g., "Home Resale")
 * @property version Semantic version string (e.g., "1.0.0")
 * @property description Optional description of the domain pack's purpose
 * @property categories List of category definitions
 * @property attributes List of attribute definitions
 */
@Serializable
data class DomainPack(
    val id: String,
    val name: String,
    val version: String,
    val description: String? = null,
    val categories: List<DomainCategory>,
    val attributes: List<DomainAttribute>,
) {
    /**
     * Get all enabled categories.
     */
    fun getEnabledCategories(): List<DomainCategory> {
        return categories.filter { it.enabled }
    }

    /**
     * Find a category by its ID.
     */
    fun getCategoryById(categoryId: String): DomainCategory? {
        return categories.find { it.id == categoryId }
    }

    /**
     * Get all categories that map to a specific ItemCategory.
     */
    fun getCategoriesForItemCategory(itemCategoryName: String): List<DomainCategory> {
        return categories.filter { it.itemCategoryName == itemCategoryName && it.enabled }
    }

    /**
     * Get attributes that apply to a specific category.
     */
    fun getAttributesForCategory(categoryId: String): List<DomainAttribute> {
        return attributes.filter { categoryId in it.appliesToCategoryIds }
    }

    /**
     * Get all categories sorted by priority (descending).
     * Categories without priority are placed at the end.
     */
    fun getCategoriesByPriority(): List<DomainCategory> {
        return categories
            .filter { it.enabled }
            .sortedByDescending { it.priority ?: Int.MIN_VALUE }
    }
}
