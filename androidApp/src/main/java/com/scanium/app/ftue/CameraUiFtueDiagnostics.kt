package com.scanium.app.ftue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * DEV-ONLY: FTUE Diagnostics Overlay
 *
 * Shows real-time anchor status and allows forcing specific steps for testing.
 *
 * Features:
 * - Anchor Inspector: Shows which anchors are registered (OK/NULL)
 * - Current Step display
 * - Force Step buttons: Manually trigger any step for isolated testing
 * - Debug Borders: Draw magenta rectangles around registered anchors
 *
 * Only visible in DEV flavor builds.
 */
@Composable
fun CameraUiFtueDiagnosticsOverlay(
    currentStep: CameraUiFtueViewModel.CameraUiFtueStep,
    anchors: Map<String, Rect>,
    showDebugBorders: Boolean,
    onForceStep: (CameraUiFtueViewModel.CameraUiFtueStep) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Debug borders for all registered anchors
        if (showDebugBorders) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zIndex(998f) // Just below overlay (999f)
                        .drawBehind {
                            anchors.forEach { (id, rect) ->
                                // Draw magenta border around anchor
                                drawRect(
                                    color = Color.Magenta,
                                    topLeft = Offset(rect.left, rect.top),
                                    size = Size(rect.width, rect.height),
                                    style = Stroke(width = 3f),
                                )

                                // Draw anchor ID label
                                drawRect(
                                    color = Color.Magenta.copy(alpha = 0.8f),
                                    topLeft = Offset(rect.left, rect.top - 20f),
                                    size = Size(100f, 20f),
                                )
                            }
                        },
            )
        }

        // Center-left vertical stack of diagnostic panels
        Column(
            modifier =
                Modifier
                    .align(androidx.compose.ui.Alignment.CenterStart)
                    .padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Anchor Inspector Panel
            AnchorInspectorPanel(
                currentStep = currentStep,
                anchors = anchors,
            )

            // Force Step Buttons Panel
            ForceStepButtonsPanel(
                onForceStep = onForceStep,
            )
        }
    }
}

/**
 * Anchor Inspector Panel - shows real-time anchor status
 */
@Composable
private fun AnchorInspectorPanel(
    currentStep: CameraUiFtueViewModel.CameraUiFtueStep,
    anchors: Map<String, Rect>,
) {
    Column(
        modifier =
            Modifier
                .background(
                    Color.Black.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.small,
                ).border(
                    width = 1.dp,
                    color = Color.Yellow,
                    shape = MaterialTheme.shapes.small,
                ).padding(8.dp),
    ) {
        Text(
            text = "FTUE DIAGNOSTICS",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.Yellow,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Show step index + name for actual execution tracking
        val stepIndex =
            when (currentStep) {
                CameraUiFtueViewModel.CameraUiFtueStep.IDLE -> -1
                CameraUiFtueViewModel.CameraUiFtueStep.SHUTTER -> 0
                CameraUiFtueViewModel.CameraUiFtueStep.FLIP_CAMERA -> 1
                CameraUiFtueViewModel.CameraUiFtueStep.ITEM_LIST -> 2
                CameraUiFtueViewModel.CameraUiFtueStep.SETTINGS -> 3
                CameraUiFtueViewModel.CameraUiFtueStep.COMPLETED -> 4
            }

        Text(
            text = "Step: [$stepIndex] ${currentStep.name}",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Anchor status for each expected anchor
        listOf(
            CameraUiFtueViewModel.ANCHOR_SHUTTER to "Shutter",
            CameraUiFtueViewModel.ANCHOR_FLIP to "Flip",
            CameraUiFtueViewModel.ANCHOR_ITEMS to "Items",
            CameraUiFtueViewModel.ANCHOR_SETTINGS to "Settings",
        ).forEach { (anchorId, label) ->
            val status =
                if (anchors.containsKey(anchorId)) {
                    val rect = anchors[anchorId]!!
                    "OK (${rect.width.toInt()}x${rect.height.toInt()})"
                } else {
                    "NULL"
                }

            val statusColor = if (anchors.containsKey(anchorId)) Color.Green else Color.Red

            Text(
                text = "$label: $status",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = statusColor,
            )
        }
    }
}

/**
 * Force Step Buttons Panel - allows manually triggering any step
 */
@Composable
private fun ForceStepButtonsPanel(onForceStep: (CameraUiFtueViewModel.CameraUiFtueStep) -> Unit) {
    Column(
        modifier =
            Modifier
                .background(
                    Color.Black.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.small,
                ).border(
                    width = 1.dp,
                    color = Color.Cyan,
                    shape = MaterialTheme.shapes.small,
                ).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "FORCE STEP",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.Cyan,
        )

        listOf(
            CameraUiFtueViewModel.CameraUiFtueStep.SHUTTER to "SHUTTER",
            CameraUiFtueViewModel.CameraUiFtueStep.FLIP_CAMERA to "FLIP",
            CameraUiFtueViewModel.CameraUiFtueStep.ITEM_LIST to "ITEMS",
            CameraUiFtueViewModel.CameraUiFtueStep.SETTINGS to "SETTINGS",
        ).forEach { (step, label) ->
            Button(
                onClick = { onForceStep(step) },
                modifier = Modifier.height(28.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E88E5).copy(alpha = 0.8f),
                    ),
            ) {
                Text(
                    text = label,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
