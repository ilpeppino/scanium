package com.scanium.app.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scanium.app.ui.theme.LightningYellow

/**
 * Direction for the lightning pulse travel.
 */
enum class PulseDirection {
    /** Pulse travels from top to bottom */
    VERTICAL_DOWN,

    /** Pulse travels from left to right */
    HORIZONTAL_RIGHT,

    /** Pulse travels diagonally from top-left to bottom-right */
    DIAGONAL_DOWN_RIGHT,
}

/**
 * Single yellow accent "pulse" that travels once across the frame.
 *
 * Brand motion rules:
 * - 200-300ms single pass, no looping
 * - Yellow (#FFD400) as action accent only
 * - Subtle thin line or small glow
 * - No continuous looping animations
 *
 * @param rect The normalized rectangle (0-1 range) defining the pulse bounds
 * @param triggerKey Unique key that triggers a new pulse when changed
 * @param direction Direction of pulse travel
 * @param pulseColor Color for the pulse (default: LightningYellow)
 * @param modifier Modifier for the canvas
 * @param onPulseComplete Callback when pulse animation completes
 */
@Composable
fun LightningScanPulse(
    rect: Rect,
    triggerKey: Any,
    direction: PulseDirection = PulseDirection.VERTICAL_DOWN,
    pulseColor: Color = LightningYellow,
    modifier: Modifier = Modifier,
    onPulseComplete: (() -> Unit)? = null,
) {
    if (!MotionConfig.isMotionOverlaysEnabled) {
        return
    }

    // Debounce: track last pulse time to prevent spam
    var lastPulseTime by remember { mutableLongStateOf(0L) }

    // Animation progress (0 = start, 1 = end)
    val progress = remember { Animatable(0f) }

    // Track if animation is active
    var isAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(triggerKey) {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastPulse = currentTime - lastPulseTime

        // Debounce check
        if (timeSinceLastPulse < MotionConstants.PULSE_DEBOUNCE_MS && lastPulseTime > 0) {
            return@LaunchedEffect
        }

        lastPulseTime = currentTime
        isAnimating = true

        // Reset and animate
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec =
                tween(
                    durationMillis = MotionConstants.LIGHTNING_PULSE_DURATION_MS,
                    easing = LinearEasing,
                ),
        )

        isAnimating = false
        onPulseComplete?.invoke()
    }

    if (isAnimating || progress.value in 0.01f..0.99f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Map normalized rect to canvas coordinates
            val frameLeft = rect.left * canvasWidth
            val frameTop = rect.top * canvasHeight
            val frameWidth = rect.width * canvasWidth
            val frameHeight = rect.height * canvasHeight
            val frameRight = frameLeft + frameWidth
            val frameBottom = frameTop + frameHeight

            val pulseWidth = MotionConstants.LIGHTNING_PULSE_WIDTH_DP.dp.toPx()
            val glowWidth = MotionConstants.LIGHTNING_PULSE_GLOW_WIDTH_DP.dp.toPx()

            // Calculate pulse position based on direction and progress
            val (startPoint, endPoint) =
                when (direction) {
                    PulseDirection.VERTICAL_DOWN -> {
                        val y = frameTop + frameHeight * progress.value
                        Offset(frameLeft, y) to Offset(frameRight, y)
                    }
                    PulseDirection.HORIZONTAL_RIGHT -> {
                        val x = frameLeft + frameWidth * progress.value
                        Offset(x, frameTop) to Offset(x, frameBottom)
                    }
                    PulseDirection.DIAGONAL_DOWN_RIGHT -> {
                        val x = frameLeft + frameWidth * progress.value
                        val y = frameTop + frameHeight * progress.value
                        // Short diagonal line segment
                        val segmentLength = minOf(frameWidth, frameHeight) * 0.1f
                        Offset(x - segmentLength, y - segmentLength) to Offset(x + segmentLength, y + segmentLength)
                    }
                }

            // Calculate alpha based on position (fade in at start, fade out at end)
            val fadeZone = 0.15f
            val alpha =
                when {
                    progress.value < fadeZone -> progress.value / fadeZone
                    progress.value > (1 - fadeZone) -> (1 - progress.value) / fadeZone
                    else -> 1f
                } * MotionConstants.LIGHTNING_PULSE_ALPHA

            // Draw glow layer (wider, more transparent)
            drawLine(
                brush =
                    Brush.linearGradient(
                        colors =
                            listOf(
                                pulseColor.copy(alpha = 0f),
                                pulseColor.copy(alpha = alpha * MotionConstants.LIGHTNING_PULSE_GLOW_ALPHA),
                                pulseColor.copy(alpha = 0f),
                            ),
                    ),
                start = startPoint,
                end = endPoint,
                strokeWidth = glowWidth,
                cap = StrokeCap.Round,
            )

            // Draw core pulse line
            drawLine(
                color = pulseColor.copy(alpha = alpha),
                start = startPoint,
                end = endPoint,
                strokeWidth = pulseWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Composable that manages pulse triggers with proper debouncing.
 *
 * Use this wrapper when you need to trigger pulses from external events
 * (like item confirmations) with automatic debouncing.
 *
 * @param rect The normalized rectangle for the pulse bounds
 * @param shouldTrigger When true, attempts to trigger a pulse (debounced)
 * @param direction Direction of pulse travel
 * @param pulseColor Color for the pulse
 * @param modifier Modifier for the canvas
 * @param onPulseComplete Callback when pulse completes
 */
@Composable
fun DebouncedLightningScanPulse(
    rect: Rect,
    shouldTrigger: Boolean,
    direction: PulseDirection = PulseDirection.VERTICAL_DOWN,
    pulseColor: Color = LightningYellow,
    modifier: Modifier = Modifier,
    onPulseComplete: (() -> Unit)? = null,
) {
    // Generate a unique key only when shouldTrigger transitions to true
    var triggerKey by remember { mutableLongStateOf(0L) }

    LaunchedEffect(shouldTrigger) {
        if (shouldTrigger) {
            triggerKey = System.currentTimeMillis()
        }
    }

    if (triggerKey > 0) {
        LightningScanPulse(
            rect = rect,
            triggerKey = triggerKey,
            direction = direction,
            pulseColor = pulseColor,
            modifier = modifier,
            onPulseComplete = onPulseComplete,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LightningScanPulsePreview() {
    LightningScanPulse(
        rect = Rect(0.1f, 0.2f, 0.9f, 0.8f),
        triggerKey = "preview",
        direction = PulseDirection.VERTICAL_DOWN,
    )
}
