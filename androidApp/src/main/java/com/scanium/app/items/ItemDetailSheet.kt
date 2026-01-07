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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scanium.app.config.FeatureFlags
import com.scanium.app.items.components.AttributeChip
import com.scanium.app.model.toImageBitmap
import com.scanium.shared.core.models.items.AttributeConfidenceTier
import com.scanium.shared.core.models.items.ItemAttribute
import com.scanium.shared.core.models.items.VisionAttributes
import kotlinx.coroutines.launch

/**
 * Bottom sheet displaying full item details including all extracted attributes.
 *
 * Features:
 * - Large thumbnail image
 * - Category, label, and price information
 * - Full list of extracted attributes with confidence indicators
 * - Edit button for each attribute
 * - "Generate Listing" action button
 * - "Refresh Attributes" button to re-run classification
 *
 * @param item The scanned item to display
 * @param onDismiss Callback when sheet is dismissed
 * @param onAttributeEdit Callback when user wants to edit an attribute
 * @param onGenerateListing Callback when user taps "Generate Listing"
 * @param onRefreshAttributes Callback when user wants to re-run classification
 * @param sheetState State for the modal bottom sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailSheet(
    item: ScannedItem,
    onDismiss: () -> Unit,
    onAttributeEdit: (String, ItemAttribute) -> Unit,
    onGenerateListing: (() -> Unit)? = null,
    onRefreshAttributes: (() -> Unit)? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header with thumbnail and basic info
            ItemDetailHeader(item = item)

            HorizontalDivider()

            // Barcode section (if available) with AI assistant button (dev only)
            item.barcodeValue?.let { barcode ->
                BarcodeSection(
                    barcodeValue = barcode,
                    onAskAi = if (FeatureFlags.allowAiAssistant && onGenerateListing != null) {
                        onGenerateListing
                    } else {
                        null
                    },
                )

                HorizontalDivider()
            }

            // Attributes section
            if (item.attributes.isNotEmpty()) {
                AttributesSection(
                    attributes = item.attributes,
                    detectedAttributes = item.detectedAttributes,
                    onEditAttribute = onAttributeEdit,
                )
            } else {
                Text(
                    text = "No attributes detected yet. Attributes will be extracted during cloud classification.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Vision details section (OCR, colors, candidates)
            if (!item.visionAttributes.isEmpty) {
                HorizontalDivider()
                VisionDetailsSection(
                    visionAttributes = item.visionAttributes,
                    attributes = item.attributes,
                    onAttributeEdit = onAttributeEdit,
                )
            }

            // Action buttons
            if (item.attributes.isNotEmpty() || onRefreshAttributes != null) {
                Spacer(modifier = Modifier.height(8.dp))

                // Generate listing button (if attributes available)
                // Only show here if barcode section didn't already show the AI button
                if (item.attributes.isNotEmpty() && onGenerateListing != null &&
                    FeatureFlags.allowAiAssistant && item.barcodeValue == null) {
                    Button(
                        onClick = onGenerateListing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Generate Listing")
                    }
                }

                // Refresh attributes button
                if (onRefreshAttributes != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRefreshAttributes,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Text("Refresh Attributes")
                    }
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
private fun BarcodeSection(
    barcodeValue: String,
    onAskAi: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Barcode value row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.small,
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Barcode Value",
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
                ElevatedButton(
                    onClick = onAskAi,
                    modifier = Modifier
                        .semantics {
                            contentDescription = "Ask AI assistant about this item"
                        }
                        .height(56.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    elevation = ButtonDefaults.elevatedButtonElevation(
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
                        text = "Ask AI",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemDetailHeader(item: ScannedItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Thumbnail
        val thumbnailBitmap = (item.thumbnailRef ?: item.thumbnail).toImageBitmap()

        thumbnailBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = "Item thumbnail",
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                    )
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Fit,
            )
        } ?: Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("?", style = MaterialTheme.typography.headlineLarge)
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
                    text = "Confidence: ${item.formattedConfidence}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (item.classificationStatus == "SUCCESS") {
                    Text(
                        text = "Cloud",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttributesSection(
    attributes: Map<String, ItemAttribute>,
    detectedAttributes: Map<String, ItemAttribute>,
    onEditAttribute: (String, ItemAttribute) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Detected Attributes",
            style = MaterialTheme.typography.titleMedium,
        )

        // Priority order for attribute display
        val priorityOrder = listOf("brand", "itemType", "model", "color", "secondaryColor", "material")

        // Sort attributes by priority, then alphabetically
        val sortedAttributes = attributes.entries
            .sortedWith(
                compareBy { entry ->
                    val index = priorityOrder.indexOf(entry.key)
                    if (index >= 0) index else priorityOrder.size
                }
            )

        sortedAttributes.forEach { (key, attribute) ->
            // Check if user has overridden the detected value
            val detectedAttribute = detectedAttributes[key]
            val isUserOverride = attribute.source == "user" && detectedAttribute != null &&
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
    val accessibilityDescription = buildString {
        append(formatAttributeLabel(attributeKey))
        append(": ")
        append(attribute.value)
        append(". ")
        if (isUserOverride) {
            append("User edited. ")
        }
        append(attribute.confidenceTier.description)
        if (detectedValue != null) {
            append(". Originally detected as: ")
            append(detectedValue)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isUserOverride) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                },
                shape = MaterialTheme.shapes.small,
            )
            .padding(12.dp)
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
                    text = formatAttributeLabel(attributeKey),
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
                        text = "Detected: $detectedValue",
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
                            text = "User edited",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    } else {
                        Text(
                            text = attribute.confidenceTier.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (attribute.confidenceTier) {
                                AttributeConfidenceTier.HIGH -> MaterialTheme.colorScheme.primary
                                AttributeConfidenceTier.MEDIUM -> MaterialTheme.colorScheme.tertiary
                                AttributeConfidenceTier.LOW -> MaterialTheme.colorScheme.outline
                            },
                        )

                        attribute.source?.let { source ->
                            Text(
                                text = "via $source",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Edit button
        IconButton(
            onClick = onEdit,
            modifier = Modifier.semantics {
                contentDescription = "Edit ${formatAttributeLabel(attributeKey)}"
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
    val (icon, tint, backgroundColor) = when {
        isUserOverride -> Triple(
            Icons.Default.Check,
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
        )
        tier == AttributeConfidenceTier.HIGH -> Triple(
            Icons.Default.Check,
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primaryContainer,
        )
        tier == AttributeConfidenceTier.MEDIUM -> Triple(
            Icons.Default.QuestionMark,
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
        )
        else -> Triple(
            Icons.Default.QuestionMark,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .background(backgroundColor, shape = MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (isUserOverride) "User verified" else tier.description,
            modifier = Modifier.size(16.dp),
            tint = tint,
        )
    }
}

/**
 * Format attribute key to human-readable label.
 */
private fun formatAttributeLabel(key: String): String {
    return when (key) {
        "brand" -> "Brand"
        "itemType" -> "Item Type"
        "model" -> "Model"
        "color" -> "Color"
        "secondaryColor" -> "Secondary Color"
        "material" -> "Material"
        "labelHints" -> "Label Hints"
        "ocrText" -> "OCR Text"
        else -> key.replaceFirstChar { it.uppercase() }
    }
}

/**
 * Section displaying raw vision data including OCR text, colors, and candidates.
 */
@Composable
private fun VisionDetailsSection(
    visionAttributes: VisionAttributes,
    attributes: Map<String, ItemAttribute>,
    onAttributeEdit: (String, ItemAttribute) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Vision Details",
            style = MaterialTheme.typography.titleMedium,
        )

        visionAttributes.itemType?.takeIf { it.isNotBlank() }?.let { itemType ->
            VisionDetailRow(
                label = "Item Type",
                value = itemType,
            )
        }

        // OCR Text
        val ocrSnippet = visionAttributes.ocrText
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
                label = "OCR Text",
                value = editableOcrAttribute?.value ?: ocrSnippet,
                onEdit = editableOcrAttribute?.let { attribute ->
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
                    text = "Colors:",
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
                label = "Labels",
                value = editableLabelAttribute?.value ?: labelHints,
                onEdit = editableLabelAttribute?.let { attribute ->
                    { onAttributeEdit("labelHints", attribute) }
                },
            )
        }

        // Brand Candidates (if not already in enriched attributes)
        if (visionAttributes.brandCandidates.isNotEmpty()) {
            VisionDetailRow(
                label = "Brand Candidates",
                value = visionAttributes.brandCandidates.take(3).joinToString(", "),
            )
        }

        // Model Candidates
        if (visionAttributes.modelCandidates.isNotEmpty()) {
            VisionDetailRow(
                label = "Model Candidates",
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
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.small,
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label:",
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
            IconButton(
                onClick = onEdit,
                modifier = Modifier.semantics { contentDescription = "Edit $label" },
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
    val color = try {
        androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Row(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, shape = MaterialTheme.shapes.extraSmall),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
