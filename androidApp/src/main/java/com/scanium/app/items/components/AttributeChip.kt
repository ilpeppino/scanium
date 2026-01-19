package com.scanium.app.items.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanium.app.R
import com.scanium.shared.core.models.items.AttributeConfidenceTier
import com.scanium.shared.core.models.items.ItemAttribute

/**
 * A chip component that displays an extracted attribute with confidence indicator.
 *
 * Color coding based on confidence tier:
 * - HIGH (green): Verified with high confidence
 * - MEDIUM (yellow/amber): Likely correct, may need verification
 * - LOW (gray): Needs confirmation
 *
 * @param attributeKey The attribute key (e.g., "brand", "color", "model")
 * @param attribute The extracted attribute with value and confidence
 * @param onClick Optional callback when chip is tapped (for editing)
 * @param modifier Modifier for customization
 * @param compact If true, only show value without icon (for list view)
 */
@Composable
fun AttributeChip(
    attributeKey: String,
    attribute: ItemAttribute,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val (backgroundColor, contentColor, borderColor) =
        when (attribute.confidenceTier) {
            AttributeConfidenceTier.HIGH -> {
                Triple(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                )
            }

            AttributeConfidenceTier.MEDIUM -> {
                Triple(
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                    MaterialTheme.colorScheme.onTertiaryContainer,
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                )
            }

            AttributeConfidenceTier.LOW -> {
                Triple(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                )
            }
        }

    val icon =
        when (attribute.confidenceTier) {
            AttributeConfidenceTier.HIGH -> Icons.Default.Check
            AttributeConfidenceTier.MEDIUM, AttributeConfidenceTier.LOW -> Icons.Default.QuestionMark
        }

    val context = LocalContext.current
    val attributeLabel =
        getAttributeLabelResource(attributeKey)?.let { context.getString(it) }
            ?: attributeKey.replaceFirstChar { it.uppercase() }
    val confidenceTierDesc =
        getConfidenceTierDescriptionResource(attribute.confidenceTier)?.let { context.getString(it) }
            ?: attribute.confidenceTier.description

    val accessibilityDescription =
        buildString {
            append(attributeLabel)
            append(": ")
            append(attribute.value)
            append(". ")
            append(confidenceTierDesc)
            if (attribute.source != null) {
                append(". ")
                append(context.getString(R.string.attribute_source_label, attribute.source))
            }
            if (onClick != null) {
                append(". ")
                append(context.getString(R.string.attribute_tap_to_edit_hint))
            }
        }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = backgroundColor,
        modifier =
            modifier
                .then(
                    if (onClick != null) {
                        Modifier.clickable { onClick() }
                    } else {
                        Modifier
                    },
                ).border(
                    width = 1.dp,
                    color = borderColor,
                    shape = MaterialTheme.shapes.small,
                ).semantics { contentDescription = accessibilityDescription },
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = if (compact) 6.dp else 8.dp,
                    vertical = if (compact) 2.dp else 4.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Value text
            Text(
                text = attribute.value,
                style =
                    if (compact) {
                        MaterialTheme.typography.labelSmall
                    } else {
                        MaterialTheme.typography.labelMedium
                    },
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Confidence icon (only in non-compact mode)
            if (!compact) {
                Icon(
                    imageVector = icon,
                    contentDescription = null, // Covered by surface semantics
                    modifier = Modifier.size(12.dp),
                    tint =
                        when (attribute.confidenceTier) {
                            AttributeConfidenceTier.HIGH -> MaterialTheme.colorScheme.primary
                            AttributeConfidenceTier.MEDIUM -> MaterialTheme.colorScheme.tertiary
                            AttributeConfidenceTier.LOW -> MaterialTheme.colorScheme.outline
                        },
                )
            }
        }
    }
}

/**
 * A row of attribute chips for displaying multiple attributes.
 *
 * @param attributes Map of attribute key to ItemAttribute
 * @param maxVisible Maximum number of chips to show (default 3)
 * @param onAttributeClick Callback when an attribute chip is tapped
 * @param modifier Modifier for customization
 * @param compact If true, use compact chip style
 */
@Composable
fun AttributeChipsRow(
    attributes: Map<String, ItemAttribute>,
    maxVisible: Int = 3,
    onAttributeClick: ((String, ItemAttribute) -> Unit)? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
) {
    if (attributes.isEmpty()) return

    // Priority order for attribute display
    val priorityOrder = listOf("brand", "model", "color", "material", "secondaryColor")

    // Sort attributes by priority, then by confidence
    val sortedAttributes =
        attributes.entries
            .sortedWith(
                compareBy<Map.Entry<String, ItemAttribute>> { entry ->
                    val index = priorityOrder.indexOf(entry.key)
                    if (index >= 0) index else priorityOrder.size
                }.thenByDescending { it.value.confidence },
            ).take(maxVisible)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        sortedAttributes.forEach { (key, attribute) ->
            AttributeChip(
                attributeKey = key,
                attribute = attribute,
                onClick = onAttributeClick?.let { { it(key, attribute) } },
                compact = compact,
            )
        }

        // Show "+N more" indicator if there are more attributes
        val remaining = attributes.size - maxVisible
        if (remaining > 0) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Text(
                    text = "+$remaining",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * Map attribute key to string resource ID.
 * Returns null if no mapping exists (will fall back to formatted key).
 */
private fun getAttributeLabelResource(key: String): Int? =
    when (key) {
        "brand" -> R.string.items_attribute_brand
        "itemType" -> R.string.items_attribute_item_type
        "model" -> R.string.items_attribute_model
        "color" -> R.string.items_attribute_color
        "secondaryColor" -> R.string.items_attribute_secondary_color
        "material" -> R.string.items_attribute_material
        "labelHints" -> R.string.items_attribute_label_hints
        "ocrText" -> R.string.items_attribute_ocr_text
        else -> null
    }

/**
 * Map confidence tier to localized description string resource.
 */
private fun getConfidenceTierDescriptionResource(tier: AttributeConfidenceTier): Int? =
    when (tier) {
        AttributeConfidenceTier.HIGH -> R.string.confidence_tier_high_desc
        AttributeConfidenceTier.MEDIUM -> R.string.confidence_tier_medium_desc
        AttributeConfidenceTier.LOW -> R.string.confidence_tier_low_desc
    }
