package com.scanium.app.items

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.scanium.app.R
import com.scanium.app.audio.AppSound
import com.scanium.app.audio.LocalSoundManager
import com.scanium.app.model.toImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.scanium.shared.core.models.model.ImageRef as CoreImageRef

/**
 * Dialog showing detailed information about a scanned item.
 */
@Composable
fun ItemDetailDialog(
    item: ScannedItem,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val soundManager = LocalSoundManager.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Title with Share button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.item_detail_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    IconButton(
                        onClick = {
                            scope.launch {
                                soundManager.play(AppSound.SELECT)
                                shareItemImage(context, item)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.item_detail_share),
                        )
                    }
                }

                HorizontalDivider()

                // Thumbnail (larger)
                val thumbnailBitmap = (item.thumbnailRef ?: item.thumbnail).toImageBitmap()

                thumbnailBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = stringResource(R.string.item_detail_thumbnail),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.medium,
                                ),
                        contentScale = ContentScale.Fit,
                    )
                } ?: run {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.medium,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.item_detail_no_image), style = MaterialTheme.typography.bodyLarge)
                    }
                }

                // Details
                DetailRow(label = stringResource(R.string.item_detail_classification_label), value = item.displayLabel)
                DetailRow(label = stringResource(R.string.item_detail_category_label), value = ItemLocalizer.getCategoryName(item.category))
                DetailRow(label = stringResource(R.string.item_detail_price_range_label), value = item.formattedPriceRange)
                DetailRow(label = stringResource(R.string.item_detail_confidence_label), value = item.formattedConfidence)
                DetailRow(
                    label = stringResource(R.string.item_detail_detected_at_label),
                    value = formatDetailTimestamp(item.timestamp),
                )
                item.domainCategoryId?.let { id ->
                    DetailRow(
                        label = stringResource(R.string.item_detail_domain_category_label),
                        value = formatDomainCategory(id, stringResource(R.string.common_unknown)),
                    )
                }
                DetailRow(label = stringResource(R.string.item_detail_id_label), value = item.id.take(8))

                // Show recognized text for documents
                item.recognizedText?.let { text ->
                    HorizontalDivider()
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.item_detail_recognized_text_label),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        ) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }

                // Show barcode value for barcodes
                item.barcodeValue?.let { barcode ->
                    HorizontalDivider()
                    DetailRow(label = stringResource(R.string.item_detail_barcode_value_label), value = barcode)
                }

                HorizontalDivider()

                // Close button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.common_close))
                }
            }
        }
    }
}

/**
 * Row for displaying a label-value pair.
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
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

private fun formatDomainCategory(
    raw: String,
    unknownLabel: String,
): String {
    if (raw.isBlank()) return unknownLabel
    val words = raw.split('_').filter { it.isNotBlank() }
    return words.joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

/**
 * Share an item's image to external apps (WhatsApp, Messages, Email, etc.)
 */
private suspend fun shareItemImage(
    context: Context,
    item: ScannedItem,
) = withContext(Dispatchers.IO) {
    val authority = "${context.packageName}.fileprovider"
    val shareDir =
        File(context.cacheDir, "share_items").apply {
            if (!exists()) mkdirs()
        }

    val imageRef = item.thumbnailRef ?: item.thumbnail
    val imageUri: Uri? =
        when (imageRef) {
            is CoreImageRef.CacheKey -> {
                val cacheFile = File(context.cacheDir, imageRef.key)
                if (cacheFile.exists()) {
                    val shareFile = File(shareDir, "item_${item.id.take(8)}.jpg")
                    cacheFile.copyTo(shareFile, overwrite = true)
                    FileProvider.getUriForFile(context, authority, shareFile)
                } else {
                    null
                }
            }
            is CoreImageRef.Bytes -> {
                val shareFile = File(shareDir, "item_${item.id.take(8)}.jpg")
                shareFile.writeBytes(imageRef.bytes)
                FileProvider.getUriForFile(context, authority, shareFile)
            }
            else -> null
        }

    // Build share text with item details
    val shareText =
        buildString {
            appendLine(item.displayLabel)
            if (item.formattedPriceRange.isNotBlank()) {
                appendLine("Price: ${item.formattedPriceRange}")
            }
            item.recognizedText?.let { text ->
                appendLine()
                appendLine(text)
            }
            item.barcodeValue?.let { barcode ->
                appendLine("Barcode: $barcode")
            }
        }

    val intent =
        if (imageUri != null) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, "Item image", imageUri)
            }
        } else {
            // Fallback to text-only if no image
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
        }

    val chooser = Intent.createChooser(intent, "Share item")
    if (context !is Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    withContext(Dispatchers.Main) {
        runCatching { context.startActivity(chooser) }
    }
}
