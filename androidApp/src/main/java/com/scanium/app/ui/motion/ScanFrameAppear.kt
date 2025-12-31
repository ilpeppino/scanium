package com.scanium.app.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scanium.app.ui.theme.ScaniumBluePrimary

/**
 * Quick fade-in rounded-rect frame overlay for the scan frame appear animation.
 *
 * Brand motion rules:
 * - <= 100ms fade-in to feel instant
 * - Linear easing (no bounce/spring)
 * - Minimal, confidence-inspiring motion
 *
 * @param rect The normalized rectangle (0-1 range) defining the frame bounds
 * @param isVisible Whether the frame should be visible (triggers animation)
 * @param frameColor Color for the frame stroke (default: ScaniumBluePrimary)
 * @param modifier Modifier for the canvas
 * @param onAnimationComplete Callback when fade-in completes
 */
@Composable
fun ScanFrameAppear(
    rect: Rect,
    isVisible: Boolean,
    frameColor: Color = ScaniumBluePrimary,
    modifier: Modifier = Modifier,
    onAnimationComplete: (() -> Unit)? = null
) {
    if (!MotionConfig.isMotionOverlaysEnabled) {
        // Motion disabled - show static frame if visible
        if (isVisible) {
            StaticScanFrame(rect = rect, frameColor = frameColor, modifier = modifier)
        }
        return
    }

    val alpha = remember { Animatable(MotionConstants.SCAN_FRAME_ALPHA_START) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            alpha.animateTo(
                targetValue = MotionConstants.SCAN_FRAME_ALPHA_END,
                animationSpec = tween(
                    durationMillis = MotionConstants.SCAN_FRAME_APPEAR_MS,
                    easing = LinearEasing
                )
            )
            onAnimationComplete?.invoke()
        } else {
            // Quick fade out when hiding
            alpha.animateTo(
                targetValue = MotionConstants.SCAN_FRAME_ALPHA_START,
                animationSpec = tween(
                    durationMillis = MotionConstants.SCAN_FRAME_APPEAR_MS / 2,
                    easing = LinearEasing
                )
            )
        }
    }

    if (alpha.value > 0.01f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Map normalized rect to canvas coordinates
            val frameLeft = rect.left * canvasWidth
            val frameTop = rect.top * canvasHeight
            val frameWidth = rect.width * canvasWidth
            val frameHeight = rect.height * canvasHeight

            val strokeWidth = MotionConstants.SCAN_FRAME_STROKE_WIDTH_DP.dp.toPx()
            val cornerRadius = MotionConstants.SCAN_FRAME_CORNER_RADIUS_DP.dp.toPx()

            // Draw outer glow
            drawRoundRect(
                color = frameColor.copy(alpha = alpha.value * 0.3f),
                topLeft = Offset(frameLeft, frameTop),
                size = Size(frameWidth, frameHeight),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                style = Stroke(width = strokeWidth * 2f)
            )

            // Draw main frame stroke
            drawRoundRect(
                color = frameColor.copy(alpha = alpha.value),
                topLeft = Offset(frameLeft, frameTop),
                size = Size(frameWidth, frameHeight),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

/**
 * Static scan frame without animation (for when motion is disabled).
 */
@Composable
private fun StaticScanFrame(
    rect: Rect,
    frameColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val frameLeft = rect.left * canvasWidth
        val frameTop = rect.top * canvasHeight
        val frameWidth = rect.width * canvasWidth
        val frameHeight = rect.height * canvasHeight

        val strokeWidth = MotionConstants.SCAN_FRAME_STROKE_WIDTH_DP.dp.toPx()
        val cornerRadius = MotionConstants.SCAN_FRAME_CORNER_RADIUS_DP.dp.toPx()

        drawRoundRect(
            color = frameColor.copy(alpha = MotionConstants.SCAN_FRAME_ALPHA_END),
            topLeft = Offset(frameLeft, frameTop),
            size = Size(frameWidth, frameHeight),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = strokeWidth)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ScanFrameAppearPreview() {
    ScanFrameAppear(
        rect = Rect(0.1f, 0.2f, 0.9f, 0.8f),
        isVisible = true
    )
}
