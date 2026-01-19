package com.scanium.app.items

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scanium.app.R
import com.scanium.app.model.toImageBitmap
import com.scanium.shared.core.models.items.AttributeConfidenceTier
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.items.VisionAttributes

/**
 * Header section displaying thumbnail and basic item info.
 */
@Composable
internal fun ItemDetailHeader(item: ScannedItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Thumbnail
        val thumbnailBitmap = (item.thumbnailRef ?: item.thumbnail).toImageBitmap()

        thumbnailBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.items_thumbnail),
                modifier =
                    Modifier
                        .size(120.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                        ).clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Fit,
            )
        } ?: Box(
            modifier =
                Modifier
                    .size(120.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.common_question_mark), style = MaterialTheme.typography.headlineLarge)
        }

        // Item info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.displayLabel,
                style = MaterialTheme.typography.headlineSmall,
            )

            if (item.formattedPriceRange.isNotBlank()) {
                Text(
                    text = item.formattedPriceRange,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.items_confidence_label, item.formattedConfidence),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (item.classificationStatus == "SUCCESS") {
                    Text(
                        text = stringResource(R.string.items_status_cloud),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * Barcode section with AI assistant button (dev flavor only).
 * Displays the barcode value and a prominent "Ask AI" button centered below it.
 */
@Composable
internal fun BarcodeSection(
    barcodeValue: String,
    onAskAi: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Barcode value row
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.small,
                    ).padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.items_barcode_value_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = barcodeValue,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        // AI assistant button (dev flavor only)
        if (onAskAi != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                val askAiDescription = stringResource(R.string.items_ask_ai_description)
                ElevatedButton(
                    onClick = onAskAi,
                    modifier =
                        Modifier
                            .semantics {
                                contentDescription = askAiDescription
                            }.height(56.dp),
                    colors =
                        ButtonDefaults.elevatedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    elevation =
                        ButtonDefaults.elevatedButtonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 8.dp,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.items_ask_ai),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

/**
 * Section displaying detected attributes with edit capabilities.
 */
@Composable
internal fun AttributesSection(
    attributes: Map<String, ItemAttribute>,
    detectedAttributes: Map<String, ItemAttribute>,
    onEditAttribute: (String, ItemAttribute) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.items_detected_attributes_title),
            style = MaterialTheme.typography.titleMedium,
        )

        // Priority order for attribute display
        val priorityOrder = listOf("brand", "itemType", "model", "color", "secondaryColor", "material")

        // Sort attributes by priority, then alphabetically
        val sortedAttributes =
            attributes.entries
                .sortedWith(
                    compareBy { entry ->
                        val index = priorityOrder.indexOf(entry.key)
                        if (index >= 0) index else priorityOrder.size
                    },
                )

        sortedAttributes.forEach { (key, attribute) ->
            // Check if user has overridden the detected value
            val detectedAttribute = detectedAttributes[key]
            val isUserOverride =
                attribute.source == "user" && detectedAttribute != null &&
                    detectedAttribute.value != attribute.value

            AttributeRow(
                attributeKey = key,
                attribute = attribute,
                detectedValue = if (isUserOverride) detectedAttribute?.value else null,
                onEdit = { onEditAttribute(key, attribute) },
            )
        }
    }
}

@Composable
private fun AttributeRow(
    attributeKey: String,
    attribute: ItemAttribute,
    detectedValue: String? = null,
    onEdit: () -> Unit,
) {
    val isUserOverride = attribute.source == "user"
    val label = formatAttributeLabel(attributeKey)
    val confidenceLabel = formatConfidenceLabel(attribute.confidenceTier)
    val accessibilityDescription =
        buildString {
            append(label)
            append(": ")
            append(attribute.value)
            append(". ")
            if (isUserOverride) {
                append(stringResource(R.string.items_attribute_user_edited))
                append(". ")
            }
            append(confidenceLabel)
            if (detectedValue != null) {
                append(". ")
                append(stringResource(R.string.items_attribute_originally_detected, detectedValue))
            }
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    if (isUserOverride) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    },
                    shape = MaterialTheme.shapes.small,
                ).padding(12.dp)
                .semantics { contentDescription = accessibilityDescription },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Confidence indicator icon
            ConfidenceIndicator(tier = attribute.confidenceTier, isUserOverride = isUserOverride)

            Column {
                // Attribute label
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Attribute value
                Text(
                    text = attribute.value,
                    style = MaterialTheme.typography.bodyLarge,
                )

                // Show detected value if user has overridden
                if (detectedValue != null) {
                    Text(
                        text = stringResource(R.string.items_attribute_detected_value, detectedValue),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                // Source and confidence
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isUserOverride) {
                        Text(
                            text = stringResource(R.string.items_attribute_user_edited),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    } else {
                        Text(
                            text = confidenceLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                when (attribute.confidenceTier) {
                                    AttributeConfidenceTier.HIGH -> MaterialTheme.colorScheme.primary
                                    AttributeConfidenceTier.MEDIUM -> MaterialTheme.colorScheme.tertiary
                                    AttributeConfidenceTier.LOW -> MaterialTheme.colorScheme.outline
                                },
                        )

                        attribute.source?.let { source ->
                            Text(
                                text = stringResource(R.string.items_attribute_via_source, source),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Edit button
        val editLabel = stringResource(R.string.items_attribute_edit, label)
        IconButton(
            onClick = onEdit,
            modifier =
                Modifier.semantics {
                    contentDescription = editLabel
                },
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ConfidenceIndicator(
    tier: AttributeConfidenceTier,
    isUserOverride: Boolean = false,
) {
    val (icon, tint, backgroundColor) =
        when {
            isUserOverride -> {
                Triple(
                    Icons.Default.Check,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                    MaterialTheme.colorScheme.tertiaryContainer,
                )
            }

            tier == AttributeConfidenceTier.HIGH -> {
                Triple(
                    Icons.Default.Check,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    MaterialTheme.colorScheme.primaryContainer,
                )
            }

            tier == AttributeConfidenceTier.MEDIUM -> {
                Triple(
                    Icons.Default.QuestionMark,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                    MaterialTheme.colorScheme.tertiaryContainer,
                )
            }

            else -> {
                Triple(
                    Icons.Default.QuestionMark,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }

    Box(
        modifier =
            Modifier
                .size(32.dp)
                .background(backgroundColor, shape = MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription =
                if (isUserOverride) {
                    stringResource(R.string.items_attribute_user_verified)
                } else {
                    formatConfidenceLabel(tier)
                },
            modifier = Modifier.size(16.dp),
            tint = tint,
        )
    }
}

/**
 * Format attribute key to human-readable label.
 */
@Composable
private fun formatAttributeLabel(key: String): String =
    when (key) {
        "brand" -> stringResource(R.string.items_attribute_brand)
        "itemType" -> stringResource(R.string.items_attribute_item_type)
        "model" -> stringResource(R.string.items_attribute_model)
        "color" -> stringResource(R.string.items_attribute_color)
        "secondaryColor" -> stringResource(R.string.items_attribute_secondary_color)
        "material" -> stringResource(R.string.items_attribute_material)
        "labelHints" -> stringResource(R.string.items_attribute_label_hints)
        "ocrText" -> stringResource(R.string.items_attribute_ocr_text)
        else -> key.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

@Composable
private fun formatConfidenceLabel(tier: AttributeConfidenceTier): String =
    when (tier) {
        AttributeConfidenceTier.HIGH -> stringResource(R.string.items_attribute_confidence_high)
        AttributeConfidenceTier.MEDIUM -> stringResource(R.string.items_attribute_confidence_medium)
        AttributeConfidenceTier.LOW -> stringResource(R.string.items_attribute_confidence_low)
    }

/**
 * Section displaying raw vision data including OCR text, colors, and candidates.
 */
@Composable
internal fun VisionDetailsSection(
    visionAttributes: VisionAttributes,
    attributes: Map<String, ItemAttribute>,
    onAttributeEdit: (String, ItemAttribute) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.items_vision_details_title),
            style = MaterialTheme.typography.titleMedium,
        )

        visionAttributes.itemType?.takeIf { it.isNotBlank() }?.let { itemType ->
            VisionDetailRow(
                label = stringResource(R.string.items_attribute_item_type),
                value = itemType,
            )
        }

        // OCR Text
        val ocrSnippet =
            visionAttributes.ocrText
                ?.lineSequence()
                ?.map { it.trim() }
                ?.firstOrNull { it.isNotEmpty() }
                ?.take(80)
        if (!ocrSnippet.isNullOrBlank()) {
            val editableOcrAttribute =
                resolveEditableAttribute(
                    key = "ocrText",
                    attributes = attributes,
                    fallbackValue = ocrSnippet,
                    fallbackConfidence = 0.8f,
                    fallbackSource = "vision-ocr",
                )
            VisionDetailRow(
                label = stringResource(R.string.items_attribute_ocr_text),
                value = editableOcrAttribute?.value ?: ocrSnippet,
                onEdit =
                    editableOcrAttribute?.let { attribute ->
                        { onAttributeEdit("ocrText", attribute) }
                    },
            )
        }

        // Detected Colors
        if (visionAttributes.colors.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.items_colors_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                visionAttributes.colors.take(3).forEach { color ->
                    ColorChip(name = color.name, hex = color.hex)
                }
            }
        }

        // Detected Labels
        if (visionAttributes.labels.isNotEmpty()) {
            val labelHints = visionAttributes.labels.take(3).joinToString(", ") { it.name }
            val editableLabelAttribute =
                resolveEditableAttribute(
                    key = "labelHints",
                    attributes = attributes,
                    fallbackValue = labelHints,
                    fallbackConfidence = visionAttributes.labels.maxOfOrNull { it.score } ?: 0.5f,
                    fallbackSource = "vision-label",
                )
            VisionDetailRow(
                label = stringResource(R.string.items_labels_label),
                value = editableLabelAttribute?.value ?: labelHints,
                onEdit =
                    editableLabelAttribute?.let { attribute ->
                        { onAttributeEdit("labelHints", attribute) }
                    },
            )
        }

        // Brand Candidates (if not already in enriched attributes)
        if (visionAttributes.brandCandidates.isNotEmpty()) {
            VisionDetailRow(
                label = stringResource(R.string.items_brand_candidates_label),
                value = visionAttributes.brandCandidates.take(3).joinToString(", "),
            )
        }

        // Model Candidates
        if (visionAttributes.modelCandidates.isNotEmpty()) {
            VisionDetailRow(
                label = stringResource(R.string.items_model_candidates_label),
                value = visionAttributes.modelCandidates.take(3).joinToString(", "),
            )
        }
    }
}

@Composable
private fun VisionDetailRow(
    label: String,
    value: String,
    onEdit: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small,
                ).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.items_label_with_colon, label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (onEdit != null) {
            val editLabel = stringResource(R.string.items_attribute_edit, label)
            IconButton(
                onClick = onEdit,
                modifier = Modifier.semantics { contentDescription = editLabel },
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun resolveEditableAttribute(
    key: String,
    attributes: Map<String, ItemAttribute>,
    fallbackValue: String,
    fallbackConfidence: Float,
    fallbackSource: String,
): ItemAttribute? {
    val existing = attributes[key]
    if (existing != null) return existing
    if (fallbackValue.isBlank()) return null
    return ItemAttribute(
        value = fallbackValue,
        confidence = fallbackConfidence,
        source = fallbackSource,
    )
}

@Composable
private fun ColorChip(
    name: String,
    hex: String,
) {
    val color =
        try {
            androidx.compose.ui.graphics
                .Color(android.graphics.Color.parseColor(hex))
        } catch (e: Exception) {
            MaterialTheme.colorScheme.surfaceVariant
        }

    Row(
        modifier =
            Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(12.dp)
                    .background(color, shape = MaterialTheme.shapes.extraSmall),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
