package com.scanium.app.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * State representing the document scan operation status.
 */
sealed class DocumentScanState {
    /** No scan in progress - ready to scan */
    data object Idle : DocumentScanState()

    /** Scan in progress */
    data object Scanning : DocumentScanState()
}

/**
 * Overlay pill that appears when a document candidate is detected.
 *
 * Shows a clickable "Scan document" button that triggers a heavy document scan
 * when tapped. During scanning, displays a progress indicator and disables interaction.
 *
 * @param isVisible Whether to show the overlay (based on document candidate presence)
 * @param scanState Current state of the document scan operation
 * @param onScanClick Callback when user taps the scan button
 * @param modifier Modifier for positioning
 */
@Composable
fun DocumentScanOverlay(
    isVisible: Boolean,
    scanState: DocumentScanState,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val isScanning = scanState is DocumentScanState.Scanning

        Surface(
            modifier =
                Modifier
                    .clickable(enabled = !isScanning, onClick = onScanClick)
                    .padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.small,
            color =
                if (isScanning) {
                    Color.Black.copy(alpha = 0.5f)
                } else {
                    Color.Black.copy(alpha = 0.7f)
                },
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Text(
                        text = "Scanning...",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                } else {
                    Text(
                        text = "Scan document",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
