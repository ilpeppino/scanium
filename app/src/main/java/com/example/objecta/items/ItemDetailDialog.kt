package com.example.objecta.items

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dialog showing detailed information about a scanned item.
 */
@Composable
fun ItemDetailDialog(
    item: ScannedItem,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = "Item Details",
                    style = MaterialTheme.typography.headlineSmall
                )

                Divider()

                // Thumbnail (larger)
                item.thumbnail?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Item detail thumbnail",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium
                            ),
                        contentScale = ContentScale.Fit
                    )
                } ?: run {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No image", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                // Details
                DetailRow(label = "Category", value = item.category.displayName)
                DetailRow(label = "Price Range", value = item.formattedPriceRange)
                DetailRow(label = "Confidence", value = item.formattedConfidence)
                DetailRow(
                    label = "Detected At",
                    value = formatDetailTimestamp(item.timestamp)
                )
                DetailRow(label = "Item ID", value = item.id.take(8))

                Divider()

                // Close button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

/**
 * Row for displaying a label-value pair.
 */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Formats timestamp with full date and time.
 */
private fun formatDetailTimestamp(timestamp: Long): String {
    val format = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
    return format.format(Date(timestamp))
}
