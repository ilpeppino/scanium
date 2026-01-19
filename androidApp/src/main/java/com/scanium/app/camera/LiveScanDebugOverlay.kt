package com.scanium.app.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Debug overlay for live scan centering investigation.
 *
 * Shows:
 * - Center crosshair
 * - Selected candidate bounding box with info label
 * - Sharpness score
 * - Stability status
 *
 * Only visible when Developer Options > Live Scan Diagnostics is enabled.
 */
@Composable
fun LiveScanDebugOverlay(
    selectedBox: SelectedBoxInfo?,
    sharpnessScore: Float,
    frameId: Long,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Draw center crosshair and selected box
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val crosshairSize = 20.dp.toPx()

            // Draw center crosshair
            drawLine(
                color = Color.Cyan.copy(alpha = 0.8f),
                start = Offset(centerX - crosshairSize, centerY),
                end = Offset(centerX + crosshairSize, centerY),
                strokeWidth = 2f,
            )
            drawLine(
                color = Color.Cyan.copy(alpha = 0.8f),
                start = Offset(centerX, centerY - crosshairSize),
                end = Offset(centerX, centerY + crosshairSize),
                strokeWidth = 2f,
            )

            // Draw selected candidate box (if any)
            selectedBox?.let { box ->
                // Convert normalized coordinates (0-1) to screen coordinates
                val left = box.left * size.width
                val top = box.top * size.height
                val right = box.right * size.width
                val bottom = box.bottom * size.height

                val boxColor = if (box.isStable) Color.Green else Color.Yellow

                // Draw bounding box
                drawRect(
                    color = boxColor,
                    topLeft = Offset(left, top),
                    size =
                        androidx.compose.ui.geometry
                            .Size(right - left, bottom - top),
                    style = Stroke(width = 3f),
                )

                // Draw center dot of box
                val boxCenterX = (left + right) / 2
                val boxCenterY = (top + bottom) / 2
                drawCircle(
                    color = boxColor,
                    radius = 6f,
                    center = Offset(boxCenterX, boxCenterY),
                )

                // Draw line from screen center to box center
                drawLine(
                    color = boxColor.copy(alpha = 0.5f),
                    start = Offset(centerX, centerY),
                    end = Offset(boxCenterX, boxCenterY),
                    strokeWidth = 1f,
                )
            }
        }

        // Info panel in top-left corner
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp))
                    .padding(6.dp),
        ) {
            Text(
                text = "LiveScan Debug",
                color = Color.Cyan,
                fontSize = 10.sp,
                lineHeight = 12.sp,
            )
            Text(
                text = "Frame: $frameId",
                color = Color.White,
                fontSize = 9.sp,
                lineHeight = 11.sp,
            )
            Text(
                text = "Sharpness: ${"%.0f".format(sharpnessScore)}",
                color = if (sharpnessScore < 100f) Color.Red else Color.Green,
                fontSize = 9.sp,
                lineHeight = 11.sp,
            )

            selectedBox?.let { box ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Selected: ${box.category}",
                    color = Color.White,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                )
                Text(
                    text = "Conf: ${"%.2f".format(box.confidence)}",
                    color = Color.White,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                )
                Text(
                    text = "CenterDist: ${"%.3f".format(box.centerDistance)}",
                    color = if (box.centerDistance > 0.35f) Color.Yellow else Color.Green,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                )
                Text(
                    text = "Area: ${"%.1f".format(box.area * 100)}%",
                    color = if (box.area < 0.03f) Color.Yellow else Color.Green,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                )
                Text(
                    text = "Stable: ${if (box.isStable) "YES (${box.stableFrames}f)" else "NO (${box.stableFrames}f)"}",
                    color = if (box.isStable) Color.Green else Color.Yellow,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                )
            } ?: run {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No selection",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                )
            }
        }
    }
}

/**
 * Information about the selected candidate box for overlay display.
 */
data class SelectedBoxInfo(
    val left: Float,
// Normalized 0-1
    val top: Float,
// Normalized 0-1
    val right: Float,
// Normalized 0-1
    val bottom: Float,
// Normalized 0-1
    val confidence: Float,
    val centerDistance: Float,
    val area: Float,
    val category: String,
    val isStable: Boolean,
    val stableFrames: Int,
)
