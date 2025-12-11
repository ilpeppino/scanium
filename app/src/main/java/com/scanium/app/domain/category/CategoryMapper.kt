package com.scanium.app.domain.category

import android.util.Log
import com.scanium.app.domain.config.DomainCategory
import com.scanium.app.ml.ItemCategory

/**
 * Utility for mapping between Domain Pack categories and ItemCategory enum.
 *
 * This mapper provides the bridge between:
 * - Fine-grained domain categories (config-driven, e.g., "electronics_laptop")
 * - Coarse-grained ItemCategory enum (code-based, e.g., ELECTRONICS)
 *
 * The mapping is defined in the Domain Pack JSON via the `itemCategoryName` field.
 * This class provides safe conversion with graceful error handling.
 *
 * Example:
 * ```
 * val domainCategory = DomainCategory(
 *   id = "electronics_laptop",
 *   itemCategoryName = "ELECTRONICS",
 *   ...
 * )
 * val itemCategory = domainCategory.toItemCategory() // Returns ItemCategory.ELECTRONICS
 * ```
 */
object CategoryMapper {
    private const val TAG = "CategoryMapper"

    /**
     * Convert a DomainCategory to an ItemCategory enum value.
     *
     * This method looks up the ItemCategory enum by the name specified in
     * the DomainCategory's `itemCategoryName` field.
     *
     * @receiver The DomainCategory to convert
     * @return The corresponding ItemCategory, or null if the mapping is invalid
     */
    fun DomainCategory.toItemCategory(): ItemCategory? {
        return try {
            ItemCategory.valueOf(itemCategoryName)
        } catch (e: IllegalArgumentException) {
            Log.w(
                TAG,
                "Invalid itemCategoryName '$itemCategoryName' for category '$id'. " +
                        "Valid values: ${ItemCategory.values().joinToString()}"
            )
            null
        }
    }

    /**
     * Convert a DomainCategory to an ItemCategory, with a fallback default.
     *
     * @receiver The DomainCategory to convert
     * @param default Fallback ItemCategory if mapping fails (default: UNKNOWN)
     * @return The corresponding ItemCategory, or the default if mapping fails
     */
    fun DomainCategory.toItemCategoryOrDefault(
        default: ItemCategory = ItemCategory.UNKNOWN
    ): ItemCategory {
        return toItemCategory() ?: default
    }

    /**
     * Validate that a DomainCategory has a valid itemCategoryName.
     *
     * @receiver The DomainCategory to validate
     * @return true if the itemCategoryName maps to a valid ItemCategory
     */
    fun DomainCategory.hasValidItemCategory(): Boolean {
        return toItemCategory() != null
    }

    /**
     * Get all ItemCategory enum values as strings (for validation).
     *
     * Useful for error messages and configuration validation.
     */
    fun getValidItemCategoryNames(): List<String> {
        return ItemCategory.values().map { it.name }
    }
}
