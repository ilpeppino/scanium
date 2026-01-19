package com.scanium.app.selling.assistant.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanium.app.model.ConfidenceTier
import com.scanium.app.model.SuggestedAttribute
import com.scanium.shared.core.models.items.ItemAttribute

/**
 * Attribute key to human-readable display name mapping.
 *
 * This mapping table defines how attribute keys from vision analysis
 * are displayed to users and how conflicts are resolved:
 *
 * | Source  | Key            | Display Name      | Alternative Key   |
 * |---------|----------------|-------------------|-------------------|
 * | color   | color          | Color             | secondaryColor    |
 * | logo    | brand          | Brand             | brand2            |
 * | brand   | brand          | Brand             | brand2            |
 * | ocr     | model          | Model             | model2            |
 * | label   | category       | Category          | subcategory       |
 * | *       | (as-is)        | (capitalized)     | key + "2"         |
 */
private val ATTRIBUTE_DISPLAY_NAMES =
    mapOf(
        "color" to "Color",
        "secondaryColor" to "Secondary Color",
        "brand" to "Brand",
        "brand2" to "Alternative Brand",
        "model" to "Model",
        "model2" to "Alternative Model",
        "category" to "Category",
        "subcategory" to "Subcategory",
    )

/**
 * Displays vision analysis results as interactive chips.
 *
 * Groups attributes by type:
 * - Colors (chips with color indicators)
 * - Brands/Logos (chips)
 * - Labels/Text (collapsible)
 * - OCR snippets (collapsible text)
 *
 * Each chip has an "Apply" action that converts to an item attribute.
 *
 * @param suggestedAttributes List of vision-derived attribute suggestions.
 * @param onApplyAttribute Callback when user taps a chip to apply (no conflict).
 * @param onAttributeConflict Callback when there's a conflict with existing value.
 * @param getExistingAttribute Function to check if an attribute already exists.
 * @param enabled Whether interaction is enabled.
 * @param modifier Modifier for the section.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisionInsightsSection(
    suggestedAttributes: List<SuggestedAttribute>,
    onApplyAttribute: (SuggestedAttribute) -> Unit,
    onAttributeConflict: (SuggestedAttribute, existingValue: String) -> Unit,
    getExistingAttribute: (String) -> ItemAttribute?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (suggestedAttributes.isEmpty()) return

    // Group by source type
    val colorAttributes = suggestedAttributes.filter { it.source == "color" }
    val brandAttributes = suggestedAttributes.filter { it.source in listOf("logo", "brand") }
    val labelAttributes = suggestedAttributes.filter { it.source == "label" }
    val ocrAttributes = suggestedAttributes.filter { it.source == "ocr" }
    val otherAttributes =
        suggestedAttributes.filter {
            it.source !in listOf("color", "logo", "brand", "label", "ocr")
        }

    var expandedSection by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Vision Insights",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Colors Section
            if (colorAttributes.isNotEmpty()) {
                AttributeChipsRow(
                    title = "Detected Colors",
                    attributes = colorAttributes,
                    onApply = { attr ->
                        val existing = getExistingAttribute(attr.key)
                        if (existing != null) {
                            onAttributeConflict(attr, existing.value)
                        } else {
                            onApplyAttribute(attr)
                        }
                    },
                    enabled = enabled,
                )
            }

            // Brands Section
            if (brandAttributes.isNotEmpty()) {
                AttributeChipsRow(
                    title = "Detected Brands",
                    attributes = brandAttributes,
                    onApply = { attr ->
                        val existing = getExistingAttribute(attr.key)
                        if (existing != null) {
                            onAttributeConflict(attr, existing.value)
                        } else {
                            onApplyAttribute(attr)
                        }
                    },
                    enabled = enabled,
                )
            }

            // Labels Section (collapsible if many)
            if (labelAttributes.isNotEmpty()) {
                CollapsibleAttributeSection(
                    title = "Label Hints",
                    attributes = labelAttributes,
                    isExpanded = expandedSection == "labels",
                    onToggle = {
                        expandedSection = if (expandedSection == "labels") null else "labels"
                    },
                    onApply = { attr ->
                        val existing = getExistingAttribute(attr.key)
                        if (existing != null) {
                            onAttributeConflict(attr, existing.value)
                        } else {
                            onApplyAttribute(attr)
                        }
                    },
                    enabled = enabled,
                    collapsedThreshold = 3,
                )
            }

            // OCR Section (collapsible)
            if (ocrAttributes.isNotEmpty()) {
                CollapsibleAttributeSection(
                    title = "OCR Text",
                    attributes = ocrAttributes,
                    isExpanded = expandedSection == "ocr",
                    onToggle = {
                        expandedSection = if (expandedSection == "ocr") null else "ocr"
                    },
                    onApply = { attr ->
                        val existing = getExistingAttribute(attr.key)
                        if (existing != null) {
                            onAttributeConflict(attr, existing.value)
                        } else {
                            onApplyAttribute(attr)
                        }
                    },
                    enabled = enabled,
                    collapsedThreshold = 2,
                )
            }

            // Other attributes
            if (otherAttributes.isNotEmpty()) {
                AttributeChipsRow(
                    title = "Other Detections",
                    attributes = otherAttributes,
                    onApply = { attr ->
                        val existing = getExistingAttribute(attr.key)
                        if (existing != null) {
                            onAttributeConflict(attr, existing.value)
                        } else {
                            onApplyAttribute(attr)
                        }
                    },
                    enabled = enabled,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttributeChipsRow(
    title: String,
    attributes: List<SuggestedAttribute>,
    onApply: (SuggestedAttribute) -> Unit,
    enabled: Boolean,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            attributes.forEach { attr ->
                VisionAttributeChip(
                    attribute = attr,
                    onApply = { onApply(attr) },
                    enabled = enabled,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollapsibleAttributeSection(
    title: String,
    attributes: List<SuggestedAttribute>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onApply: (SuggestedAttribute) -> Unit,
    enabled: Boolean,
    collapsedThreshold: Int = 3,
) {
    val needsCollapse = attributes.size > collapsedThreshold
    val displayAttributes = if (!needsCollapse || isExpanded) attributes else attributes.take(collapsedThreshold)

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (needsCollapse) {
                TextButton(
                    onClick = onToggle,
                    modifier =
                        Modifier.semantics {
                            contentDescription = if (isExpanded) "Collapse $title" else "Expand $title"
                        },
                ) {
                    Text(
                        text = if (isExpanded) "Less" else "+${attributes.size - collapsedThreshold} more",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        AnimatedVisibility(
            visible = true,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                displayAttributes.forEach { attr ->
                    VisionAttributeChip(
                        attribute = attr,
                        onApply = { onApply(attr) },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun VisionAttributeChip(
    attribute: SuggestedAttribute,
    onApply: () -> Unit,
    enabled: Boolean,
) {
    val (backgroundColor, borderColor) =
        when (attribute.confidence) {
            ConfidenceTier.HIGH -> {
                Pair(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                )
            }

            ConfidenceTier.MED -> {
                Pair(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                )
            }

            ConfidenceTier.LOW -> {
                Pair(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                )
            }
        }

    val displayName =
        ATTRIBUTE_DISPLAY_NAMES[attribute.key]
            ?: attribute.key.replaceFirstChar { it.uppercase() }

    SuggestionChip(
        onClick = onApply,
        enabled = enabled,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "$displayName: ${attribute.value}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Apply",
                    modifier = Modifier.size(14.dp),
                )
            }
        },
        colors =
            SuggestionChipDefaults.suggestionChipColors(
                containerColor = backgroundColor,
            ),
        border =
            SuggestionChipDefaults.suggestionChipBorder(
                enabled = enabled,
                borderColor = borderColor,
            ),
        modifier =
            Modifier.semantics {
                contentDescription = "Apply $displayName: ${attribute.value}"
            },
    )
}
