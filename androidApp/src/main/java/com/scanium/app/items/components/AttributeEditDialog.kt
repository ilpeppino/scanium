package com.scanium.app.items.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scanium.app.R
import com.scanium.shared.core.models.items.AttributeConfidenceTier
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.items.VisionAttributes

/**
 * Dialog for editing an extracted attribute value.
 *
 * When a user confirms a value (even without changing it), the confidence
 * is upgraded to HIGH since the user has verified it.
 *
 * Provides suggestions based on:
 * - Brand: brandCandidates from vision + detected logos
 * - Model: modelCandidates from vision (OCR extracted)
 * - Color: detected colors from vision
 *
 * @param attributeKey The key of the attribute being edited (e.g., "brand")
 * @param attribute The current attribute value and metadata
 * @param visionAttributes Optional vision attributes for suggestions
 * @param detectedValue Optional original detected value (shown for reference)
 * @param onDismiss Callback when dialog is dismissed without saving
 * @param onConfirm Callback with the updated attribute value when confirmed
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttributeEditDialog(
    attributeKey: String,
    attribute: ItemAttribute,
    visionAttributes: VisionAttributes = VisionAttributes.EMPTY,
    detectedValue: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (ItemAttribute) -> Unit,
) {
    var editedValue by remember { mutableStateOf(attribute.value) }

    // Generate suggestions based on attribute key
    val suggestions = remember(attributeKey, visionAttributes) {
        when (attributeKey) {
            "brand" -> {
                val candidates = mutableListOf<String>()
                // Add brand candidates from OCR analysis
                candidates.addAll(visionAttributes.brandCandidates)
                // Add logo names as they often indicate brand
                candidates.addAll(visionAttributes.logos.map { it.name })
                candidates.distinct().take(5)
            }
            "model" -> {
                // Model candidates typically come from OCR
                visionAttributes.modelCandidates.take(5)
            }
            "color", "secondaryColor" -> {
                // Color suggestions from detected colors
                visionAttributes.colors.map { it.name }.distinct().take(5)
            }
            "itemType" -> {
                buildList {
                    visionAttributes.itemType?.let { add(it) }
                    addAll(visionAttributes.labels.map { it.name })
                }.distinct().take(5)
            }
            else -> emptyList()
        }
    }

    val attributeLabel = getAttributeLabelResource(attributeKey)?.let { stringResource(it) }
        ?: attributeKey.replaceFirstChar { it.uppercase() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.items_attribute_edit, attributeLabel))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Current confidence indicator
                CurrentConfidenceRow(attribute = attribute)

                // Show original detected value if different
                if (detectedValue != null && detectedValue != attribute.value) {
                    Text(
                        text = stringResource(R.string.items_attribute_originally_detected, detectedValue),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Edit field
                OutlinedTextField(
                    value = editedValue,
                    onValueChange = { editedValue = it },
                    label = { Text(attributeLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Suggestions section
                if (suggestions.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.attribute_suggestions_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            suggestions.forEach { suggestion ->
                                SuggestionChip(
                                    onClick = { editedValue = suggestion },
                                    label = { Text(suggestion) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = if (editedValue == suggestion) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }

                // Help text
                if (attribute.confidenceTier != AttributeConfidenceTier.HIGH &&
                    attribute.source != "user"
                ) {
                    Text(
                        text = stringResource(R.string.attribute_confirm_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Source info
                val source = attribute.source
                if (source != null && source != "user") {
                    Text(
                        text = stringResource(R.string.attribute_extracted_via, source),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Create updated attribute with HIGH confidence (user verified)
                    val updatedAttribute = ItemAttribute(
                        value = editedValue.trim(),
                        confidence = 1.0f, // User verification = maximum confidence
                        source = "user", // Mark as user-verified
                    )
                    onConfirm(updatedAttribute)
                },
                enabled = editedValue.isNotBlank(),
            ) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun CurrentConfidenceRow(attribute: ItemAttribute) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small,
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Confidence icon
        val (icon, backgroundColor, iconTint) = when (attribute.confidenceTier) {
            AttributeConfidenceTier.HIGH -> Triple(
                Icons.Default.Check,
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
            )
            AttributeConfidenceTier.MEDIUM -> Triple(
                Icons.Default.QuestionMark,
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
            )
            AttributeConfidenceTier.LOW -> Triple(
                Icons.Default.QuestionMark,
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            modifier = Modifier
                .size(24.dp)
                .background(backgroundColor, shape = MaterialTheme.shapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = iconTint,
            )
        }

        val confidenceTierDesc = getConfidenceTierDescriptionResource(attribute.confidenceTier)?.let { stringResource(it) }
            ?: attribute.confidenceTier.description

        Column {
            Text(
                text = stringResource(R.string.attribute_current_value, attribute.value),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = confidenceTierDesc,
                style = MaterialTheme.typography.bodySmall,
                color = when (attribute.confidenceTier) {
                    AttributeConfidenceTier.HIGH -> MaterialTheme.colorScheme.primary
                    AttributeConfidenceTier.MEDIUM -> MaterialTheme.colorScheme.tertiary
                    AttributeConfidenceTier.LOW -> MaterialTheme.colorScheme.outline
                },
            )
        }
    }
}

/**
 * Map attribute key to string resource ID.
 * Returns null if no mapping exists (will fall back to formatted key).
 */
private fun getAttributeLabelResource(key: String): Int? {
    return when (key) {
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
}

/**
 * Map confidence tier to localized description string resource.
 */
private fun getConfidenceTierDescriptionResource(tier: AttributeConfidenceTier): Int? {
    return when (tier) {
        AttributeConfidenceTier.HIGH -> R.string.confidence_tier_high_desc
        AttributeConfidenceTier.MEDIUM -> R.string.confidence_tier_medium_desc
        AttributeConfidenceTier.LOW -> R.string.confidence_tier_low_desc
    }
}
