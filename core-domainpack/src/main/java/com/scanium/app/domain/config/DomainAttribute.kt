package com.scanium.app.domain.config

import kotlinx.serialization.Serializable

/**
 * Represents an attribute definition in a Domain Pack.
 *
 * Attributes describe extractable properties of items (e.g., brand, color, condition).
 * They specify:
 * - What type of data they hold
 * - How to extract the value (OCR, CLIP, barcode, etc.)
 * - Which categories they apply to
 * - Whether they're required or optional
 *
 * Example: A "brand" attribute might use OCR extraction and apply to electronics categories.
 *
 * @property name Attribute name (e.g., "brand", "color", "condition")
 * @property type Data type of the attribute value
 * @property extractionMethod How to extract this attribute from the scanned item
 * @property appliesToCategoryIds List of category IDs this attribute applies to
 * @property required Whether this attribute is mandatory for the applicable categories
 */
@Serializable
data class DomainAttribute(
    val name: String,
    val type: AttributeType,
    val extractionMethod: ExtractionMethod,
    val appliesToCategoryIds: List<String>,
    val required: Boolean,
)
