package com.scanium.app.camera

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanium.app.ui.theme.CyanGlow
import com.scanium.app.ui.theme.DeepNavy
import com.scanium.app.ui.theme.ScaniumBlue
import kotlin.math.roundToInt

/**
 * Vertical threshold slider for real-time detection tuning.
 *
 * Features:
 * - Vertical orientation (drag up/down to adjust)
 * - Shows current value as percentage
 * - Smooth animations and feedback
 * - Uses Scanium brand colors
 * - Touch-friendly size
 *
 * @param value Current threshold value (0.0 - 1.0)
 * @param onValueChange Callback when value changes
 * @param modifier Modifier for positioning and sizing
 */
@Composable
fun VerticalThresholdSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Animated value for smooth transitions
    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "threshold_value"
    )

    // Interaction state for visual feedback
    var isDragging by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.7f),
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical slider track
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(200.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // Wait for touch down
                        val down = awaitFirstDown()
                        isDragging = true

                        // Calculate initial value from touch position
                        val touchY = down.position.y
                        val height = size.height.toFloat()
                        if (height > 0) {
                            // Map Y position to value (0 at top = 1.0, bottom = 0.0)
                            val newValue = (1f - (touchY / height)).coerceIn(0f, 1f)
                            onValueChange(newValue)
                        }

                        // Track drag movements
                        drag(down.id) { change ->
                            change.consume()

                            val dragY = change.position.y
                            val height = size.height.toFloat()
                            if (height > 0) {
                                // Map Y position to value (0 at top = 1.0, bottom = 0.0)
                                val newValue = (1f - (dragY / height)).coerceIn(0f, 1f)
                                onValueChange(newValue)
                            }
                        }

                        // Drag ended
                        isDragging = false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val trackWidth = 12.dp.toPx()
                val centerX = size.width / 2f

                // Background track
                drawLine(
                    color = DeepNavy,
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.height),
                    strokeWidth = trackWidth,
                    cap = StrokeCap.Round
                )

                // Active track (from bottom to current value)
                val activeHeight = size.height * (1f - animatedValue)
                drawLine(
                    color = if (isDragging) CyanGlow else ScaniumBlue,
                    start = Offset(centerX, activeHeight),
                    end = Offset(centerX, size.height),
                    strokeWidth = trackWidth,
                    cap = StrokeCap.Round
                )

                // Thumb indicator
                val thumbY = size.height * (1f - animatedValue)
                val thumbRadius = if (isDragging) 14.dp.toPx() else 12.dp.toPx()

                // Thumb outer glow (when dragging)
                if (isDragging) {
                    drawCircle(
                        color = CyanGlow.copy(alpha = 0.3f),
                        radius = thumbRadius * 1.5f,
                        center = Offset(centerX, thumbY)
                    )
                }

                // Thumb
                drawCircle(
                    color = if (isDragging) CyanGlow else ScaniumBlue,
                    radius = thumbRadius,
                    center = Offset(centerX, thumbY)
                )

                // Thumb border
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(centerX, thumbY),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Inner dot
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = Offset(centerX, thumbY)
                )
            }
        }

        // Vertical labels parallel to slider
        Column(
            modifier = Modifier.height(200.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // HIGH label at top
            Text(
                text = "HI",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 8.sp,
                modifier = Modifier.graphicsLayer(rotationZ = -90f)
            )

            // Center section with threshold label and value
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Vertical "THRESHOLD" text
                Text(
                    text = "THRESHOLD",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 7.sp,
                    letterSpacing = 0.3.sp,
                    modifier = Modifier.graphicsLayer(rotationZ = -90f)
                )

                // Value display - keep horizontal for readability
                Text(
                    text = "${(animatedValue * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDragging) CyanGlow else Color.White,
                    fontSize = 13.sp
                )
            }

            // LOW label at bottom
            Text(
                text = "LO",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 8.sp,
                modifier = Modifier.graphicsLayer(rotationZ = -90f)
            )
        }
    }
}
