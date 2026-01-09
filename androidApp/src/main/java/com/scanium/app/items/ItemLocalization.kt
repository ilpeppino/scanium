package com.scanium.app.items

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.scanium.app.R
import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.items.ItemCondition

/**
 * Localization helper for item categories, conditions, and attribute sources.
 *
 * Provides localized display strings for:
 * - ItemCategory enums (Fashion, Electronics, etc.)
 * - ItemCondition enums (New, Used, etc.)
 * - Attribute sources (vision, user, etc.)
 *
 * ARCHITECTURE:
 * - Internal IDs: Enum values remain stable and language-neutral
 * - Display strings: Resolved from string resources using app's selected locale
 * - Localization happens at presentation boundary (UI layer)
 *
 * Usage:
 * ```kotlin
 * // In Composable context:
 * val categoryName = ItemLocalizer.getCategoryName(item.category)
 *
 * // In View/Context:
 * val categoryName = ItemLocalizer.getCategoryName(context, item.category)
 * ```
 */
object ItemLocalizer {

    /**
     * Get localized category display name
     */
    @Composable
    fun getCategoryName(category: ItemCategory): String {
        return stringResource(getCategoryStringRes(category))
    }

    /**
     * Get localized category display name from Context
     */
    fun getCategoryName(context: Context, category: ItemCategory): String {
        return context.getString(getCategoryStringRes(category))
    }

    /**
     * Get string resource ID for category
     */
    @StringRes
    fun getCategoryStringRes(category: ItemCategory): Int {
        return when (category) {
            ItemCategory.FASHION -> R.string.category_fashion
            ItemCategory.HOME_GOOD -> R.string.category_home_good
            ItemCategory.FOOD -> R.string.category_food
            ItemCategory.PLACE -> R.string.category_place
            ItemCategory.PLANT -> R.string.category_plant
            ItemCategory.ELECTRONICS -> R.string.category_electronics
            ItemCategory.DOCUMENT -> R.string.category_document
            ItemCategory.BARCODE -> R.string.category_barcode
            ItemCategory.QR_CODE -> R.string.category_qr_code
            ItemCategory.UNKNOWN -> R.string.category_unknown
        }
    }

    /**
     * Get localized condition display name
     */
    @Composable
    fun getConditionName(condition: ItemCondition): String {
        return stringResource(getConditionStringRes(condition))
    }

    /**
     * Get localized condition display name from Context
     */
    fun getConditionName(context: Context, condition: ItemCondition): String {
        return context.getString(getConditionStringRes(condition))
    }

    /**
     * Get string resource ID for condition
     */
    @StringRes
    fun getConditionStringRes(condition: ItemCondition): Int {
        return when (condition) {
            ItemCondition.NEW -> R.string.item_condition_new
            ItemCondition.AS_GOOD_AS_NEW -> R.string.item_condition_as_good_as_new
            ItemCondition.USED -> R.string.item_condition_used
            ItemCondition.REFURBISHED -> R.string.item_condition_refurbished
        }
    }

    /**
     * Get localized condition description
     */
    @Composable
    fun getConditionDescription(condition: ItemCondition): String {
        return stringResource(getConditionDescriptionStringRes(condition))
    }

    /**
     * Get localized condition description from Context
     */
    fun getConditionDescription(context: Context, condition: ItemCondition): String {
        return context.getString(getConditionDescriptionStringRes(condition))
    }

    /**
     * Get string resource ID for condition description
     */
    @StringRes
    fun getConditionDescriptionStringRes(condition: ItemCondition): Int {
        return when (condition) {
            ItemCondition.NEW -> R.string.item_condition_new_desc
            ItemCondition.AS_GOOD_AS_NEW -> R.string.item_condition_as_good_as_new_desc
            ItemCondition.USED -> R.string.item_condition_used_desc
            ItemCondition.REFURBISHED -> R.string.item_condition_refurbished_desc
        }
    }

    /**
     * Get localized attribute source label
     */
    @Composable
    fun getSourceLabel(source: String?): String {
        return stringResource(getSourceStringRes(source))
    }

    /**
     * Get localized attribute source label from Context
     */
    fun getSourceLabel(context: Context, source: String?): String {
        return context.getString(getSourceStringRes(source))
    }

    /**
     * Get string resource ID for attribute source
     */
    @StringRes
    fun getSourceStringRes(source: String?): Int {
        return when (source?.lowercase()) {
            "vision" -> R.string.attribute_source_vision
            "vision-color" -> R.string.attribute_source_vision_color
            "vision-logo" -> R.string.attribute_source_vision_logo
            "vision-label" -> R.string.attribute_source_vision_label
            "vision-ocr" -> R.string.attribute_source_vision_ocr
            "user" -> R.string.attribute_source_user
            "enrichment" -> R.string.attribute_source_enrichment
            null -> R.string.attribute_source_unknown
            else -> {
                // For unknown sources, try to match common patterns
                when {
                    source.contains("vision", ignoreCase = true) -> R.string.attribute_source_vision
                    source.contains("user", ignoreCase = true) -> R.string.attribute_source_user
                    source.contains("enrich", ignoreCase = true) -> R.string.attribute_source_enrichment
                    else -> R.string.attribute_source_unknown
                }
            }
        }
    }
}
