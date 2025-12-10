package com.scanium.app.camera

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
    // Track slider dimensions
    var sliderHeight by remember { mutableStateOf(0f) }

    // Animated value for smooth transitions
    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "threshold_value"
    )

    // Interaction state for visual feedback
    var isDragging by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.7f),
                shape = MaterialTheme.shapes.medium
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Label
        Text(
            text = "THRESH",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 9.sp,
            letterSpacing = 0.5.sp
        )

        // Value display
        Text(
            text = "${(animatedValue * 100).roundToInt()}%",
            style = MaterialTheme.typography.titleMedium,
            color = if (isDragging) CyanGlow else Color.White,
            fontSize = 14.sp
        )

        // Vertical slider track
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(200.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            isDragging = true
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()

                            // Calculate new value based on drag
                            // Dragging up increases value, dragging down decreases
                            val delta = -dragAmount / sliderHeight
                            val newValue = (value + delta).coerceIn(0f, 1f)
                            onValueChange(newValue)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        sliderHeight = size.height.toFloat()
                    }
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

        // Min/Max labels
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "HIGH",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 8.sp
            )
            Text(
                text = "↕",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 10.sp
            )
            Text(
                text = "LOW",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 8.sp
            )
        }
    }
}
