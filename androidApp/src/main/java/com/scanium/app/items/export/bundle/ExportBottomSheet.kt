package com.scanium.app.items.export.bundle

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File

/**
 * Export progress state.
 */
sealed class ExportState {
    /** Initial state - ready to start export */
    object Idle : ExportState()

    /** Preparing export bundles */
    object Preparing : ExportState()

    /** Creating ZIP file */
    data class CreatingZip(val progress: Float, val currentItem: Int, val totalItems: Int) : ExportState()

    /** Export ready to share */
    data class Ready(
        val result: ExportBundleResult,
        val zipFile: File? = null,
        val text: String? = null,
    ) : ExportState()

    /** Export failed */
    data class Error(val message: String) : ExportState()
}

/**
 * Bottom sheet for export operations.
 *
 * Shows:
 * - Export summary (items, status)
 * - Progress during export
 * - Share options when ready
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    bundleResult: ExportBundleResult?,
    exportState: ExportState,
    onDismiss: () -> Unit,
    onExportText: () -> Unit,
    onExportZip: () -> Unit,
    onCopyText: () -> Unit,
    onShareZip: (File) -> Unit,
    onShareText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Title
            Text(
                text = "Export Items",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            when (exportState) {
                is ExportState.Idle, is ExportState.Preparing -> {
                    // Summary section
                    bundleResult?.let { result ->
                        ExportSummary(result = result)
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Export options
                    if (exportState is ExportState.Preparing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Preparing export...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        ExportOptions(
                            onExportText = onExportText,
                            onExportZip = onExportZip,
                            hasPhotos = bundleResult?.noPhotosCount != bundleResult?.totalItems,
                        )
                    }
                }

                is ExportState.CreatingZip -> {
                    // Progress indicator
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LinearProgressIndicator(
                            progress = { exportState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Creating ZIP... (${exportState.currentItem}/${exportState.totalItems})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is ExportState.Ready -> {
                    // Success state with share options
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Export Ready!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Summary of what was exported
                    val summary = buildString {
                        append("${exportState.result.totalItems} item(s)")
                        if (exportState.result.needsAiCount > 0) {
                            append(" (${exportState.result.needsAiCount} needs AI)")
                        }
                    }
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Share actions
                    ShareActions(
                        zipFile = exportState.zipFile,
                        text = exportState.text,
                        onShareZip = onShareZip,
                        onShareText = onShareText,
                        onCopyText = onCopyText,
                    )
                }

                is ExportState.Error -> {
                    // Error state
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Export Failed",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = exportState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

/**
 * Summary of export contents.
 */
@Composable
private fun ExportSummary(result: ExportBundleResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Items to export",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${result.totalItems}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (result.readyCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Ready (AI-generated)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${result.readyCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            if (result.needsAiCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Needs AI processing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Text(
                        text = "${result.needsAiCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            if (result.noPhotosCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Without photos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${result.noPhotosCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // Warning for items needing AI
    if (result.needsAiCount > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Items without AI-generated descriptions will use fallback text and be marked as 'NEEDS_AI' in the export.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Export format options.
 */
@Composable
private fun ExportOptions(
    onExportText: () -> Unit,
    onExportZip: () -> Unit,
    hasPhotos: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Share Text option
        OutlinedButton(
            onClick = onExportText,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Share as text" },
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share as Text")
        }

        // ZIP option (only if items have photos)
        if (hasPhotos) {
            Button(
                onClick = onExportZip,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Export as ZIP with photos" },
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderZip,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export ZIP with Photos")
            }
        }
    }
}

/**
 * Share actions after export is complete.
 */
@Composable
private fun ShareActions(
    zipFile: File?,
    text: String?,
    onShareZip: (File) -> Unit,
    onShareText: (String) -> Unit,
    onCopyText: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Share ZIP
        zipFile?.let { file ->
            Button(
                onClick = { onShareZip(file) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share ZIP")
            }

            // File size info
            val sizeKb = file.length() / 1024
            val sizeText = if (sizeKb > 1024) {
                "${sizeKb / 1024} MB"
            } else {
                "$sizeKb KB"
            }
            Text(
                text = "File size: $sizeText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        // Share Text
        text?.let { exportText ->
            OutlinedButton(
                onClick = { onShareText(exportText) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Text")
            }

            // Copy to clipboard
            OutlinedButton(
                onClick = onCopyText,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy to Clipboard")
            }
        }
    }
}
