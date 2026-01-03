package com.scanium.app.items.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scanium.shared.core.models.items.AttributeConfidenceTier
import com.scanium.shared.core.models.items.ItemAttribute

/**
 * Dialog for editing an extracted attribute value.
 *
 * When a user confirms a value (even without changing it), the confidence
 * is upgraded to HIGH since the user has verified it.
 *
 * @param attributeKey The key of the attribute being edited (e.g., "brand")
 * @param attribute The current attribute value and metadata
 * @param onDismiss Callback when dialog is dismissed without saving
 * @param onConfirm Callback with the updated attribute value when confirmed
 */
@Composable
fun AttributeEditDialog(
    attributeKey: String,
    attribute: ItemAttribute,
    onDismiss: () -> Unit,
    onConfirm: (ItemAttribute) -> Unit,
) {
    var editedValue by remember { mutableStateOf(attribute.value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Edit ${formatAttributeLabel(attributeKey)}")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Current confidence indicator
                CurrentConfidenceRow(attribute = attribute)

                Spacer(modifier = Modifier.height(8.dp))

                // Edit field
                OutlinedTextField(
                    value = editedValue,
                    onValueChange = { editedValue = it },
                    label = { Text(formatAttributeLabel(attributeKey)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Help text
                if (attribute.confidenceTier != AttributeConfidenceTier.HIGH) {
                    Text(
                        text = "Confirming will mark this as verified (high confidence).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Source info
                attribute.source?.let { source ->
                    Text(
                        text = "Originally extracted via: $source",
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
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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

        Column {
            Text(
                text = "Current: ${attribute.value}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = attribute.confidenceTier.description,
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
