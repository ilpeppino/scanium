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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.Button
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
import com.scanium.app.items.components.AttributeChip
import com.scanium.app.model.toImageBitmap
import com.scanium.shared.core.models.items.AttributeConfidenceTier
import com.scanium.shared.core.models.items.ItemAttribute
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
 *
 * @param item The scanned item to display
 * @param onDismiss Callback when sheet is dismissed
 * @param onAttributeEdit Callback when user wants to edit an attribute
 * @param onGenerateListing Callback when user taps "Generate Listing"
 * @param sheetState State for the modal bottom sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailSheet(
    item: ScannedItem,
    onDismiss: () -> Unit,
    onAttributeEdit: (String, ItemAttribute) -> Unit,
    onGenerateListing: (() -> Unit)? = null,
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

            // Attributes section
            if (item.attributes.isNotEmpty()) {
                AttributesSection(
                    attributes = item.attributes,
                    onEditAttribute = onAttributeEdit,
                )
            } else {
                Text(
                    text = "No attributes extracted yet. Attributes will be extracted during cloud classification.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Generate listing button (if attributes available)
            if (item.attributes.isNotEmpty() && onGenerateListing != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onGenerateListing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Generate Listing")
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
    onEditAttribute: (String, ItemAttribute) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Extracted Attributes",
            style = MaterialTheme.typography.titleMedium,
        )

        // Priority order for attribute display
        val priorityOrder = listOf("brand", "model", "color", "secondaryColor", "material")

        // Sort attributes by priority, then alphabetically
        val sortedAttributes = attributes.entries
            .sortedWith(
                compareBy { entry ->
                    val index = priorityOrder.indexOf(entry.key)
                    if (index >= 0) index else priorityOrder.size
                }
            )

        sortedAttributes.forEach { (key, attribute) ->
            AttributeRow(
                attributeKey = key,
                attribute = attribute,
                onEdit = { onEditAttribute(key, attribute) },
            )
        }
    }
}

@Composable
private fun AttributeRow(
    attributeKey: String,
    attribute: ItemAttribute,
    onEdit: () -> Unit,
) {
    val accessibilityDescription = buildString {
        append(formatAttributeLabel(attributeKey))
        append(": ")
        append(attribute.value)
        append(". ")
        append(attribute.confidenceTier.description)
        if (attribute.source != null) {
            append(". Source: ")
            append(attribute.source)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
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
            ConfidenceIndicator(tier = attribute.confidenceTier)

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

                // Source and confidence
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
private fun ConfidenceIndicator(tier: AttributeConfidenceTier) {
    val (icon, tint, backgroundColor) = when (tier) {
        AttributeConfidenceTier.HIGH -> Triple(
            Icons.Default.Check,
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.primaryContainer,
        )
        AttributeConfidenceTier.MEDIUM -> Triple(
            Icons.Default.QuestionMark,
            MaterialTheme.colorScheme.onTertiaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
        )
        AttributeConfidenceTier.LOW -> Triple(
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
            contentDescription = tier.description,
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
        "model" -> "Model"
        "color" -> "Color"
        "secondaryColor" -> "Secondary Color"
        "material" -> "Material"
        else -> key.replaceFirstChar { it.uppercase() }
    }
}
