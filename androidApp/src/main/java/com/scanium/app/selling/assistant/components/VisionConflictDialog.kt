package com.scanium.app.selling.assistant.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanium.app.model.SuggestedAttribute

/**
 * Dialog shown when a vision-detected attribute conflicts with an existing user value.
 * Offers options to replace the existing value or add as an alternative attribute.
 *
 * Conflict resolution strategy:
 * - Replace: Overwrites the existing attribute with the detected value
 * - Add as Alternative: Creates a secondary attribute (e.g., secondaryColor, brand2)
 * - Cancel: Dismisses without making changes
 *
 * @param attribute The suggested attribute that conflicts.
 * @param existingValue The current value set by the user.
 * @param alternativeKey The key to use for "Add as Alternative" (e.g., "secondaryColor").
 * @param onReplace Callback when user chooses to replace existing value.
 * @param onAddAlternative Callback when user chooses to add as alternative.
 * @param onDismiss Callback when dialog is dismissed without action.
 */
@Composable
fun VisionConflictDialog(
    attribute: SuggestedAttribute,
    existingValue: String,
    alternativeKey: String,
    onReplace: () -> Unit,
    onAddAlternative: () -> Unit,
    onDismiss: () -> Unit,
) {
    val displayKey = attribute.key.replaceFirstChar { it.uppercase() }
    val displayAltKey = alternativeKey.replaceFirstChar { it.uppercase() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Attribute Already Set",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column {
                Text(
                    text = "You already have a $displayKey value. How would you like to handle the detected value?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Current vs Detected comparison
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Current value card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Text(
                                text = "Current",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = existingValue,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    // Detected value card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Text(
                                text = "Detected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                text = attribute.value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onReplace,
                modifier = Modifier.semantics {
                    contentDescription = "Replace $displayKey with ${attribute.value}"
                },
            ) {
                Text("Replace")
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics {
                        contentDescription = "Cancel and keep current value"
                    },
                ) {
                    Text("Cancel")
                }
                TextButton(
                    onClick = onAddAlternative,
                    modifier = Modifier.semantics {
                        contentDescription = "Add ${attribute.value} as $displayAltKey"
                    },
                ) {
                    Text("Add as $displayAltKey")
                }
            }
        },
    )
}
