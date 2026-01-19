package com.scanium.app.items.summary

import com.scanium.app.items.ItemCondition
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.app.ItemCategory

/**
 * Generates and parses the attribute summary text format.
 *
 * The summary text is a human-readable, user-editable representation of item attributes.
 * It follows a consistent format that can be parsed back into structured attributes.
 *
 * Format:
 * ```
 * Category: Fashion > Tops > T-Shirt
 * Brand: Nike
 * Color: Gray
 * Condition: Used (good)
 * Material: Cotton
 * Size: (missing)
 * Notes:
 * ```
 */
object AttributeSummaryGenerator {
    private val ATTRIBUTE_ORDER =
        listOf(
            "category",
            "brand",
            "color",
            "condition",
            "material",
            "size",
            "model",
            "itemType",
            "notes",
        )

    private val ATTRIBUTE_DISPLAY_NAMES =
        mapOf(
            "category" to "Category",
            "brand" to "Brand",
            "color" to "Color",
            "condition" to "Condition",
            "material" to "Material",
            "size" to "Size",
            "model" to "Model",
            "itemType" to "Type",
            "notes" to "Notes",
            "secondaryColor" to "Secondary Color",
        )

    /**
     * Generate summary text from structured attributes.
     *
     * @param attributes Current item attributes
     * @param category Item category (optional)
     * @param condition Item condition (optional)
     * @param includeEmptyFields Whether to include fields with "(missing)" value
     * @return Formatted summary text
     */
    fun generateSummaryText(
        attributes: Map<String, ItemAttribute>,
        category: ItemCategory? = null,
        condition: ItemCondition? = null,
        includeEmptyFields: Boolean = true,
    ): String =
        buildString {
            // Category
            val categoryPath = category?.let { buildCategoryPath(it) }
            appendField("Category", categoryPath, includeEmptyFields)

            // Core attributes in order
            for (key in ATTRIBUTE_ORDER) {
                if (key == "category") continue // Already handled
                if (key == "condition") {
                    appendField("Condition", condition?.displayName, includeEmptyFields)
                    continue
                }
                if (key == "notes") continue // Handle at the end

                val displayName = ATTRIBUTE_DISPLAY_NAMES[key] ?: key.replaceFirstChar { it.uppercase() }
                val value = attributes[key]?.value
                appendField(displayName, value, includeEmptyFields)
            }

            // Any additional attributes not in the standard order
            val handledKeys = ATTRIBUTE_ORDER.toSet() + setOf("category", "secondaryColor")
            for ((key, attr) in attributes) {
                if (key in handledKeys) continue
                val displayName = ATTRIBUTE_DISPLAY_NAMES[key] ?: key.replaceFirstChar { it.uppercase() }
                appendField(displayName, attr.value, includeEmptyFields)
            }

            // Secondary color if present
            attributes["secondaryColor"]?.let { attr ->
                appendField("Secondary Color", attr.value, includeEmptyFields)
            }

            // Notes at the end (always include even if empty)
            appendField("Notes", attributes["notes"]?.value, includeEmptyFields = true)
        }.trimEnd()

    /**
     * Generate a concise summary for display in lists.
     */
    fun generateConciseSummary(
        attributes: Map<String, ItemAttribute>,
        category: ItemCategory? = null,
    ): String {
        val parts = mutableListOf<String>()

        // Brand
        attributes["brand"]?.value?.takeIf { it.isNotBlank() }?.let {
            parts.add(it)
        }

        // Item type or category
        val itemType =
            attributes["itemType"]?.value?.takeIf { it.isNotBlank() }
                ?: category?.displayName
        itemType?.let { parts.add(it) }

        // Color
        attributes["color"]?.value?.takeIf { it.isNotBlank() }?.let {
            parts.add(it)
        }

        return parts.joinToString(" - ")
    }

    /**
     * Parse summary text back to structured attributes.
     *
     * @param text The summary text to parse
     * @return Parsed summary containing attributes map and extracted values
     */
    fun parseSummaryText(text: String): ParsedSummary {
        val attributes = mutableMapOf<String, String>()
        var category: String? = null
        var condition: String? = null
        var notes: String? = null

        // Build reverse lookup for display names
        val displayNameToKey =
            ATTRIBUTE_DISPLAY_NAMES.entries
                .associate { (k, v) -> v.lowercase() to k }
                .toMutableMap()
        displayNameToKey["type"] = "itemType"
        displayNameToKey["secondary color"] = "secondaryColor"

        val lines = text.lines()
        var currentField: String? = null
        val multiLineValue = StringBuilder()

        for (line in lines) {
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                // Save previous multi-line field if any
                currentField?.let { field ->
                    if (multiLineValue.isNotBlank()) {
                        storeValue(field, multiLineValue.toString().trim(), attributes) { cat, cond ->
                            category = cat
                            condition = cond
                        }
                    }
                }
                multiLineValue.clear()

                val fieldName = line.substring(0, colonIndex).trim().lowercase()
                val value = line.substring(colonIndex + 1).trim()

                if (fieldName == "notes") {
                    currentField = "notes"
                    if (value.isNotBlank() && value != "(missing)") {
                        multiLineValue.append(value)
                    }
                } else {
                    currentField = null
                    if (value.isNotBlank() && value != "(missing)") {
                        storeValue(fieldName, value, attributes) { cat, cond ->
                            category = cat
                            condition = cond
                        }
                    }
                }
            } else if (currentField == "notes") {
                // Continue multi-line notes
                if (multiLineValue.isNotEmpty()) {
                    multiLineValue.append("\n")
                }
                multiLineValue.append(line)
            }
        }

        // Save final notes if any
        if (currentField == "notes" && multiLineValue.isNotBlank()) {
            notes = multiLineValue.toString().trim()
            attributes["notes"] = notes
        }

        return ParsedSummary(
            attributes = attributes,
            category = category,
            condition = condition,
            notes = notes,
        )
    }

    private fun storeValue(
        fieldName: String,
        value: String,
        attributes: MutableMap<String, String>,
        onSpecial: (category: String?, condition: String?) -> Unit,
    ) {
        when (fieldName) {
            "category" -> {
                onSpecial(value, null)
            }

            "condition" -> {
                onSpecial(null, value)
            }

            else -> {
                // Map display name to key
                val displayNameToKey =
                    ATTRIBUTE_DISPLAY_NAMES.entries
                        .associate { (k, v) -> v.lowercase() to k }
                        .toMutableMap()
                displayNameToKey["type"] = "itemType"
                displayNameToKey["secondary color"] = "secondaryColor"

                val key = displayNameToKey[fieldName] ?: fieldName
                attributes[key] = value
            }
        }
    }

    private fun StringBuilder.appendField(
        label: String,
        value: String?,
        includeEmptyFields: Boolean,
    ) {
        val displayValue = value?.takeIf { it.isNotBlank() }
        if (displayValue != null) {
            appendLine("$label: $displayValue")
        } else if (includeEmptyFields) {
            appendLine("$label: (missing)")
        }
    }

    private fun buildCategoryPath(category: ItemCategory): String {
        // Build a category path like "Fashion > Tops > T-Shirt"
        return when (category) {
            ItemCategory.FASHION -> "Fashion"
            ItemCategory.HOME_GOOD -> "Home & Garden"
            ItemCategory.FOOD -> "Food & Beverages"
            ItemCategory.PLACE -> "Places"
            ItemCategory.PLANT -> "Plants & Garden"
            ItemCategory.ELECTRONICS -> "Electronics"
            ItemCategory.DOCUMENT -> "Documents"
            ItemCategory.BARCODE -> "Barcode Item"
            ItemCategory.QR_CODE -> "QR Code Item"
            ItemCategory.UNKNOWN -> "Uncategorized"
        }
    }

    /**
     * Get the list of missing required fields.
     */
    fun getMissingFields(
        attributes: Map<String, ItemAttribute>,
        category: ItemCategory? = null,
        condition: ItemCondition? = null,
    ): List<String> {
        val required = listOf("brand", "color", "condition", "itemType")
        val missing = mutableListOf<String>()

        for (field in required) {
            if (field == "condition") {
                if (condition == null) {
                    missing.add("Condition")
                }
            } else if (field == "itemType") {
                if (attributes["itemType"]?.value.isNullOrBlank() && category == null) {
                    missing.add("Type")
                }
            } else {
                if (attributes[field]?.value.isNullOrBlank()) {
                    missing.add(ATTRIBUTE_DISPLAY_NAMES[field] ?: field.replaceFirstChar { it.uppercase() })
                }
            }
        }

        return missing
    }

    /**
     * Convert parsed attributes to ItemAttribute map with user source.
     */
    fun toAttributeMap(
        parsed: ParsedSummary,
        existingAttributes: Map<String, ItemAttribute> = emptyMap(),
    ): Map<String, ItemAttribute> {
        val result = existingAttributes.toMutableMap()

        for ((key, value) in parsed.attributes) {
            val existing = result[key]
            result[key] =
                ItemAttribute(
                    value = value,
                    confidence = 1.0f, // User-provided = high confidence
                    source = "user",
                )
        }

        return result
    }
}

/**
 * Result of parsing summary text.
 */
data class ParsedSummary(
    val attributes: Map<String, String>,
    val category: String?,
    val condition: String?,
    val notes: String?,
)
