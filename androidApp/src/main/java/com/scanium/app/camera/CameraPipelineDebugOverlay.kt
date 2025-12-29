package com.scanium.app.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Debug overlay showing camera pipeline lifecycle state.
 *
 * Displays:
 * - Session ID
 * - isCameraBound (bool)
 * - isAnalysisRunning (bool)
 * - lastFrameTimestamp (ms ago)
 * - lastBboxTimestamp (ms ago)
 * - framesPerSecond (analysis)
 * - currentLifecycleState
 * - navDestination
 * - bboxCount
 *
 * This helps diagnose which part of the pipeline is failing when bboxes freeze:
 * A) camera bound but analyzer not running
 * B) analyzer running but bbox state not updating
 * C) bbox updating but UI not recomposing
 * D) scope cancelled and never restarted
 */
@Composable
fun CameraPipelineDebugOverlay(
    diagnostics: CameraPipelineDiagnostics,
    modifier: Modifier = Modifier
) {
    // Track current time for "ms ago" calculations
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Update current time every 100ms for smooth "ms ago" updates
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMs = System.currentTimeMillis()
            delay(100)
        }
    }

    val frameAgo = if (diagnostics.lastFrameTimestampMs > 0) {
        currentTimeMs - diagnostics.lastFrameTimestampMs
    } else {
        -1L
    }

    val bboxAgo = if (diagnostics.lastBboxTimestampMs > 0) {
        currentTimeMs - diagnostics.lastBboxTimestampMs
    } else {
        -1L
    }

    // Determine pipeline status based on diagnostics
    val pipelineStatus = when {
        !diagnostics.isCameraBound -> "CAMERA NOT BOUND"
        !diagnostics.isAnalysisRunning && !diagnostics.isPreviewDetectionActive && !diagnostics.isScanningActive -> "ANALYZER NOT RUNNING"
        frameAgo > 2000 -> "FRAMES STALE (${frameAgo}ms)"
        bboxAgo > 2000 && diagnostics.bboxCount == 0 -> "NO BBOXES"
        else -> "OK"
    }

    val statusColor = when {
        pipelineStatus == "OK" -> Color.Green
        pipelineStatus.startsWith("FRAMES STALE") -> Color.Yellow
        pipelineStatus == "NO BBOXES" -> Color.Yellow
        else -> Color.Red
    }

    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.75f), shape = MaterialTheme.shapes.small)
            .padding(8.dp)
    ) {
        Text(
            text = "CAM PIPELINE DEBUG",
            color = Color.Cyan,
            fontSize = 10.sp,
            style = MaterialTheme.typography.labelSmall
        )

        Text(
            text = "Status: $pipelineStatus",
            color = statusColor,
            fontSize = 10.sp,
            style = MaterialTheme.typography.labelSmall
        )

        Text(
            text = "SessionID: ${diagnostics.sessionId}",
            color = Color.White,
            fontSize = 9.sp,
            style = MaterialTheme.typography.labelSmall
        )

        Text(
            text = "CameraBound: ${diagnostics.isCameraBound}",
            color = if (diagnostics.isCameraBound) Color.Green else Color.Red,
            fontSize = 9.sp,
            style = MaterialTheme.typography.labelSmall
        )

        Text(
            text = "PreviewDetection: ${diagnostics.isPreviewDetectionActive}",
            color = if (diagnostics.isPreviewDetectionActive) Color.Green else Color.Gray,
            fontSize = 9.sp,
            style = MaterialTheme.typography.labelSmall
        )

        Text(
            text = "Scanning: ${diagnostics.isScanningActive}",
            color = if (diagnostics.isScanningActive) Color.Green else Color.Gray,
            fontSize = 9.sp,
            style = MaterialTheme.typography.labelSmall
        )

        Text(
            text = "AnalysisRunning: ${diagnostics.isAnalysisRunning}",
            color = if (diagnostics.isAnalysisRunning) Color.Green else Color.Red,
            fontSize = 9.sp,
            style = MaterialTheme.typography.labelSmall
        )

        Text(
            text = "LastFrame: ${if (frameAgo >= 0) "${frameAgo}ms ago" else "never"}",
            color = when {
                frameAgo < 0 -> Color.Gray
                frameAgo < 500 -> Color.Green
                frameAgo < 2000 -> Color.Yellow
                else -> Color.Red
            },
            fontSize = 9.sp,
            style = MaterialTheme.typography.labelSmall
        )

        Text(
            text = "LastBbox: ${if (bboxAgo >= 0) "${bboxAgo}ms ago" else "never"}",
            color = when {
                bboxAgo < 0 -> Color.Gray
                bboxAgo < 500 -> Color.Green
                bboxAgo < 2000 -> Color.Yellow
                else -> Color.Red
            },
            fontSize = 9.sp,
            style = MaterialTheme.typography.labelSmall
        )

        Text(
            text = "BboxCount: ${diagnostics.bboxCount}",
            color = if (diagnostics.bboxCount > 0) Color.Green else Color.Gray,
            fontSize = 9.sp,
            style = MaterialTheme.typography.labelSmall
        )

        Text(
            text = "FPS: ${"%.1f".format(diagnostics.analysisFramesPerSecond)}",
            color = when {
                diagnostics.analysisFramesPerSecond >= 1.5 -> Color.Green
                diagnostics.analysisFramesPerSecond >= 0.5 -> Color.Yellow
                else -> Color.Red
            },
            fontSize = 9.sp,
            style = MaterialTheme.typography.labelSmall
        )

        Text(
            text = "Lifecycle: ${diagnostics.lifecycleState}",
            color = when (diagnostics.lifecycleState) {
                "RESUMED" -> Color.Green
                "STARTED" -> Color.Yellow
                else -> Color.Gray
            },
            fontSize = 9.sp,
            style = MaterialTheme.typography.labelSmall
        )

        if (diagnostics.navDestination.isNotEmpty()) {
            Text(
                text = "Nav: ${diagnostics.navDestination}",
                color = Color.White,
                fontSize = 9.sp,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
