package com.scanium.app.items

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.scanium.app.R

/**
 * Localizes item attribute VALUES (not just labels) for display.
 *
 * This handles the localization of constrained vocabulary values like:
 * - Colors (e.g., "orange" -> "arancione" for Italian)
 * - Materials (e.g., "plastic" -> "plastica" for Italian)
 * - Conditions (e.g., "USED" -> "Usato" for Italian)
 *
 * ARCHITECTURE:
 * - Internal values: Always stored in English canonical form (e.g., "orange")
 * - Display values: Localized for UI display (e.g., "arancione")
 * - The localization happens at the presentation layer
 *
 * NON-NEGOTIABLE LANGUAGE CONTRACT:
 * - Any user-visible attribute value MUST be localized when languageTag != "en"
 * - This localizer provides the canonical <-> localized mapping
 *
 * Usage:
 * ```kotlin
 * // Display (canonical -> localized)
 * val displayColor = ItemAttributeLocalizer.localizeColor("orange") // Returns "arancione" in Italian
 *
 * // Storage (localized -> canonical) - for saving user input
 * val canonicalColor = ItemAttributeLocalizer.canonicalizeColor("arancione") // Returns "orange"
 * ```
 */
object ItemAttributeLocalizer {
    private const val TAG = "ItemAttributeLocalizer"

    // ==================== COLOR LOCALIZATION ====================

    /**
     * Canonical color names mapped to their string resource IDs.
     * These are the color values that may come from Vision API or backend.
     */
    private val colorResourceMap: Map<String, Int> =
        mapOf(
            "red" to R.string.attr_color_red,
            "orange" to R.string.attr_color_orange,
            "yellow" to R.string.attr_color_yellow,
            "green" to R.string.attr_color_green,
            "blue" to R.string.attr_color_blue,
            "purple" to R.string.attr_color_purple,
            "pink" to R.string.attr_color_pink,
            "brown" to R.string.attr_color_brown,
            "black" to R.string.attr_color_black,
            "white" to R.string.attr_color_white,
            "gray" to R.string.attr_color_gray,
            "grey" to R.string.attr_color_gray, // Alias
            "beige" to R.string.attr_color_beige,
            "gold" to R.string.attr_color_gold,
            "silver" to R.string.attr_color_silver,
            "navy" to R.string.attr_color_navy,
            "teal" to R.string.attr_color_teal,
            "turquoise" to R.string.attr_color_turquoise,
            "maroon" to R.string.attr_color_maroon,
            "olive" to R.string.attr_color_olive,
            "coral" to R.string.attr_color_coral,
            "cream" to R.string.attr_color_cream,
            "tan" to R.string.attr_color_tan,
            "multicolor" to R.string.attr_color_multicolor,
        )

    /**
     * Localize a color value for display.
     * If the color is not in our vocabulary, returns it as-is.
     */
    @Composable
    fun localizeColor(canonicalValue: String): String {
        val normalized = canonicalValue.trim().lowercase()
        val resId = colorResourceMap[normalized] ?: return canonicalValue
        return stringResource(resId)
    }

    /**
     * Localize a color value for display (Context variant).
     */
    fun localizeColor(
        context: Context,
        canonicalValue: String,
    ): String {
        val normalized = canonicalValue.trim().lowercase()
        val resId = colorResourceMap[normalized] ?: return canonicalValue
        return context.getString(resId)
    }

    /**
     * Convert a localized color back to its canonical form.
     * Used when saving user input.
     */
    fun canonicalizeColor(
        context: Context,
        localizedValue: String,
    ): String {
        val normalized = localizedValue.trim().lowercase()

        // First check if it's already a canonical value
        if (colorResourceMap.containsKey(normalized)) {
            return normalized
        }

        // Search through all colors to find a match
        for ((canonical, resId) in colorResourceMap) {
            val localized = context.getString(resId).lowercase()
            if (localized == normalized) {
                return canonical
            }
        }

        // Not found - return as-is (user-entered free text)
        return localizedValue
    }

    /**
     * Check if a color value is in our known vocabulary.
     */
    fun isKnownColor(value: String): Boolean = colorResourceMap.containsKey(value.trim().lowercase())

    // ==================== MATERIAL LOCALIZATION ====================

    /**
     * Canonical material names mapped to their string resource IDs.
     */
    private val materialResourceMap: Map<String, Int> =
        mapOf(
            "cotton" to R.string.attr_material_cotton,
            "polyester" to R.string.attr_material_polyester,
            "wool" to R.string.attr_material_wool,
            "silk" to R.string.attr_material_silk,
            "leather" to R.string.attr_material_leather,
            "denim" to R.string.attr_material_denim,
            "linen" to R.string.attr_material_linen,
            "nylon" to R.string.attr_material_nylon,
            "plastic" to R.string.attr_material_plastic,
            "metal" to R.string.attr_material_metal,
            "wood" to R.string.attr_material_wood,
            "glass" to R.string.attr_material_glass,
            "rubber" to R.string.attr_material_rubber,
            "ceramic" to R.string.attr_material_ceramic,
            "fabric" to R.string.attr_material_fabric,
            "velvet" to R.string.attr_material_velvet,
            "suede" to R.string.attr_material_suede,
            "canvas" to R.string.attr_material_canvas,
            "synthetic" to R.string.attr_material_synthetic,
            "acrylic" to R.string.attr_material_acrylic,
            "stainless steel" to R.string.attr_material_stainless_steel,
            "aluminum" to R.string.attr_material_aluminum,
            "brass" to R.string.attr_material_brass,
            "copper" to R.string.attr_material_copper,
            "porcelain" to R.string.attr_material_porcelain,
        )

    /**
     * Localize a material value for display.
     */
    @Composable
    fun localizeMaterial(canonicalValue: String): String {
        val normalized = canonicalValue.trim().lowercase()
        val resId = materialResourceMap[normalized] ?: return canonicalValue
        return stringResource(resId)
    }

    /**
     * Localize a material value for display (Context variant).
     */
    fun localizeMaterial(
        context: Context,
        canonicalValue: String,
    ): String {
        val normalized = canonicalValue.trim().lowercase()
        val resId = materialResourceMap[normalized] ?: return canonicalValue
        return context.getString(resId)
    }

    /**
     * Convert a localized material back to its canonical form.
     */
    fun canonicalizeMaterial(
        context: Context,
        localizedValue: String,
    ): String {
        val normalized = localizedValue.trim().lowercase()

        if (materialResourceMap.containsKey(normalized)) {
            return normalized
        }

        for ((canonical, resId) in materialResourceMap) {
            val localized = context.getString(resId).lowercase()
            if (localized == normalized) {
                return canonical
            }
        }

        return localizedValue
    }

    /**
     * Check if a material value is in our known vocabulary.
     */
    fun isKnownMaterial(value: String): Boolean = materialResourceMap.containsKey(value.trim().lowercase())

    // ==================== CONDITION LOCALIZATION ====================

    /**
     * Canonical condition names mapped to their string resource IDs.
     */
    private val conditionResourceMap: Map<String, Int> =
        mapOf(
            "new_sealed" to R.string.item_condition_new_sealed,
            "new_with_tags" to R.string.item_condition_new_with_tags,
            "new_without_tags" to R.string.item_condition_new_without_tags,
            "like_new" to R.string.item_condition_like_new,
            "good" to R.string.item_condition_good,
            "fair" to R.string.item_condition_fair,
            "poor" to R.string.item_condition_poor,
        )

    /**
     * Localize a condition value for display.
     */
    @Composable
    fun localizeCondition(canonicalValue: String): String {
        val normalized = canonicalValue.trim().lowercase()
        val resId = conditionResourceMap[normalized] ?: return canonicalValue
        return stringResource(resId)
    }

    /**
     * Localize a condition value for display (Context variant).
     */
    fun localizeCondition(
        context: Context,
        canonicalValue: String,
    ): String {
        val normalized = canonicalValue.trim().lowercase()
        val resId = conditionResourceMap[normalized] ?: return canonicalValue
        return context.getString(resId)
    }

    /**
     * Convert a localized condition back to its canonical form.
     */
    fun canonicalizeCondition(
        context: Context,
        localizedValue: String,
    ): String {
        val normalized = localizedValue.trim().lowercase()

        if (conditionResourceMap.containsKey(normalized)) {
            return normalized.uppercase()
        }

        for ((canonical, resId) in conditionResourceMap) {
            val localized = context.getString(resId).lowercase()
            if (localized == normalized) {
                return canonical.uppercase()
            }
        }

        return localizedValue.uppercase()
    }

    /**
     * Check if a condition value is in our known vocabulary.
     */
    fun isKnownCondition(value: String): Boolean = conditionResourceMap.containsKey(value.trim().lowercase())

    // ==================== GENERIC ATTRIBUTE LOCALIZATION ====================

    /**
     * Localize an attribute value based on its key.
     * Dispatches to the appropriate localizer based on attribute type.
     */
    @Composable
    fun localizeAttributeValue(
        key: String,
        value: String,
    ): String =
        when (key.lowercase()) {
            "color", "primarycolor", "secondarycolor" -> localizeColor(value)
            "material" -> localizeMaterial(value)
            "condition" -> localizeCondition(value)
            else -> value // Not a constrained vocabulary - return as-is
        }

    /**
     * Localize an attribute value based on its key (Context variant).
     */
    fun localizeAttributeValue(
        context: Context,
        key: String,
        value: String,
    ): String =
        when (key.lowercase()) {
            "color", "primarycolor", "secondarycolor" -> localizeColor(context, value)
            "material" -> localizeMaterial(context, value)
            "condition" -> localizeCondition(context, value)
            else -> value
        }

    /**
     * Canonicalize an attribute value based on its key.
     * Used when saving user-edited values to storage.
     */
    fun canonicalizeAttributeValue(
        context: Context,
        key: String,
        value: String,
    ): String =
        when (key.lowercase()) {
            "color", "primarycolor", "secondarycolor" -> canonicalizeColor(context, value)
            "material" -> canonicalizeMaterial(context, value)
            "condition" -> canonicalizeCondition(context, value)
            else -> value
        }

    // ==================== ATTRIBUTE LABEL LOCALIZATION ====================

    /**
     * Attribute key to label string resource mapping.
     * These are the field labels shown in the Edit Item screen.
     */
    private val attributeLabelResourceMap: Map<String, Int> =
        mapOf(
            "brand" to R.string.edit_item_field_brand,
            "itemtype" to R.string.edit_item_field_product_type,
            "producttype" to R.string.edit_item_field_product_type,
            "model" to R.string.edit_item_field_model,
            "color" to R.string.edit_item_field_color,
            "size" to R.string.edit_item_field_size,
            "material" to R.string.edit_item_field_material,
            "condition" to R.string.edit_item_field_condition,
            "notes" to R.string.edit_item_field_notes,
        )

    /**
     * Get the localized label for an attribute key.
     */
    @Composable
    fun getAttributeLabel(key: String): String {
        val resId =
            attributeLabelResourceMap[key.lowercase()]
                ?: return key.replaceFirstChar { it.uppercase() }
        return stringResource(resId)
    }

    /**
     * Get the localized label for an attribute key (Context variant).
     */
    fun getAttributeLabel(
        context: Context,
        key: String,
    ): String {
        val resId =
            attributeLabelResourceMap[key.lowercase()]
                ?: return key.replaceFirstChar { it.uppercase() }
        return context.getString(resId)
    }

    // ==================== LANGUAGE VIOLATION DETECTION ====================

    /**
     * Common English words that indicate an attribute value is in English.
     * Used for debug-mode language violation detection.
     */
    private val englishIndicatorWords =
        setOf(
            // Colors
            "red",
            "orange",
            "yellow",
            "green",
            "blue",
            "purple",
            "pink",
            "brown",
            "black",
            "white",
            "gray",
            "grey",
            "beige",
            "gold",
            "silver",
            "navy",
            // Materials
            "cotton",
            "polyester",
            "wool",
            "silk",
            "leather",
            "plastic",
            "metal",
            "wood",
            "glass",
            "rubber",
            "fabric",
            // Conditions
            "new",
            "sealed",
            "tags",
            "tag",
            "like",
            "good",
            "fair",
            "poor",
            "used",
            "refurbished",
        )

    /**
     * Check if an attribute value contains obvious English words.
     * Returns true if the value appears to be in English.
     *
     * Note: This is a heuristic check for DEBUG mode language violation detection.
     * It does NOT catch all English text, just common attribute vocabulary.
     */
    fun containsEnglishVocabulary(value: String): Boolean {
        val words = value.lowercase().split(Regex("[\\s,;]+"))
        return words.any { it in englishIndicatorWords }
    }

    /**
     * Validate that an attribute map contains localized values.
     * Returns a list of attribute keys that appear to have English values.
     *
     * @param attributes Map of attribute key to value
     * @param languageTag Current language tag (e.g., "it", "de", "fr")
     * @return List of attribute keys with suspected English values
     */
    fun detectEnglishViolations(
        attributes: Map<String, String>,
        languageTag: String,
    ): List<String> {
        // Skip check for English
        if (languageTag.startsWith("en", ignoreCase = true)) {
            return emptyList()
        }

        val violations = mutableListOf<String>()

        for ((key, value) in attributes) {
            // Only check constrained vocabulary attributes
            if (key.lowercase() !in listOf("color", "material", "condition")) {
                continue
            }

            // Check if the value is a known English vocabulary word
            if (containsEnglishVocabulary(value)) {
                violations.add(key)
                Log.w(
                    TAG,
                    "PROMPT_LANGUAGE_VIOLATION: Attribute '$key' has English value '$value' " +
                        "but languageTag is '$languageTag'",
                )
            }
        }

        return violations
    }
}
