package com.scanium.app.camera

import android.os.SystemClock
import android.os.Trace
import android.util.Size
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.scanium.app.perf.PerformanceMonitor
import com.scanium.app.platform.toRectF
import com.scanium.app.ui.theme.DeepNavy
import com.scanium.app.ui.theme.CyanGlow
import com.scanium.app.ui.theme.ScaniumBlue
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import kotlin.math.max

/**
 * Overlay that renders bounding boxes for detected objects so users can see what the
 * camera believes is being scanned.
 *
 * @param detections List of detection results to render
 * @param imageSize Size of the analyzed image (from ML Kit)
 * @param previewSize Size of the preview view on screen
 */
/**
 * Visual colors for bounding box states.
 */
private object BboxColors {
    /** Preview state: neutral blue - object detected */
    val PreviewOutline = ScaniumBlue.copy(alpha = 0.6f)
    val PreviewGlow = CyanGlow.copy(alpha = 0.2f)

    /** Ready state: accent color - conditions met, hold steady */
    val ReadyOutline = Color(0xFF1DB954).copy(alpha = 0.85f)
    val ReadyGlow = Color(0xFF1DB954).copy(alpha = 0.4f)

    /** Locked state: bright accent - scan ready */
    val LockedOutline = Color(0xFF1DB954)
    val LockedGlow = Color(0xFF1DB954).copy(alpha = 0.5f)
}

@Composable
fun DetectionOverlay(
    detections: List<OverlayTrack>,
    imageSize: Size,
    previewSize: Size,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelTextStyle = MaterialTheme.typography.labelMedium.copy(color = Color.White)
    val readyColor = Color(0xFF1DB954)
    val hasEstimating = detections.any { it.priceEstimationStatus is PriceEstimationStatus.Estimating }
    val hasLocked = detections.any { it.boxStyle == OverlayBoxStyle.LOCKED }

    // Pulse animation for price estimation - only animate when actually estimating
    val infiniteTransition = rememberInfiniteTransition(label = "pricePulse")
    val animatedPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pricePulseValue"
    )

    // Single pulse animation for LOCKED state transition (once)
    // This creates a brief "pop" effect when lock is achieved
    val lockedPulseScale = remember { Animatable(1f) }
    LaunchedEffect(hasLocked) {
        if (hasLocked) {
            // Brief pulse: scale up slightly then back to normal
            lockedPulseScale.animateTo(
                targetValue = 1.15f,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
            )
            lockedPulseScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            )
        }
    }

    // Use animated alpha when estimating, otherwise full opacity
    val pulseAlpha = if (hasEstimating) animatedPulseAlpha else 1f

    Canvas(modifier = modifier.fillMaxSize()) {
        val drawStartTime = SystemClock.elapsedRealtime()
        Trace.beginSection("DetectionOverlay.draw")

        val canvasWidth = size.width
        val canvasHeight = size.height

        // Calculate transformation parameters
        val transform = calculateTransform(
            imageWidth = imageSize.width,
            imageHeight = imageSize.height,
            previewWidth = canvasWidth,
            previewHeight = canvasHeight
        )

        // Bounding box appearance constants
        val minBoxStrokeWidth = 2.dp.toPx()
        val maxBoxStrokeWidth = 4.dp.toPx()
        val boxCornerRadius = 12.dp.toPx()
        val minGlowStrokeWidth = 4.dp.toPx()
        val maxGlowStrokeWidth = 8.dp.toPx()
        val minInnerStrokeWidth = 1.dp.toPx()
        val maxInnerStrokeWidth = 2.dp.toPx()
        val labelHorizontalPadding = 8.dp.toPx()
        val labelVerticalPadding = 4.dp.toPx()
        val labelCornerRadius = 10.dp.toPx()
        val labelMargin = 6.dp.toPx()
        val labelBackgroundColor = DeepNavy.copy(alpha = 0.85f)

        detections.forEach { detection ->
            val status = detection.priceEstimationStatus
            val isReady = detection.isReady
            val isEstimating = status is PriceEstimationStatus.Estimating
            val boxStyle = detection.boxStyle

            // Bounding boxes are always fully visible (no pulsing)
            val boxAlpha = 1f

            // Label pulses when classification incomplete or estimating price
            val shouldPulseLabel = !isReady || isEstimating
            val labelAlpha = if (shouldPulseLabel) pulseAlpha else 1f

            // PHASE 1: Explicit visual states for bbox
            // Visual progression: PREVIEW → READY → LOCKED
            // - PREVIEW: thin stroke, neutral color (detected but not ready)
            // - READY: medium stroke, accent color (conditions met, holding)
            // - LOCKED: thick stroke, bright accent with pulse (scan-ready)
            val (outlineColor, glowColor, strokeMultiplier) = when (boxStyle) {
                OverlayBoxStyle.LOCKED -> Triple(
                    BboxColors.LockedOutline,
                    BboxColors.LockedGlow,
                    1.4f * lockedPulseScale.value  // Thick stroke with pulse effect
                )
                OverlayBoxStyle.READY -> Triple(
                    BboxColors.ReadyOutline,
                    BboxColors.ReadyGlow,
                    1.1f  // Medium stroke for ready
                )
                OverlayBoxStyle.PREVIEW -> Triple(
                    BboxColors.PreviewOutline,
                    BboxColors.PreviewGlow,
                    0.75f  // Thin stroke for preview
                )
            }

            val clampedConfidence = detection.confidence.coerceIn(0f, 1f)
            val boxStrokeWidth = (minBoxStrokeWidth +
                (maxBoxStrokeWidth - minBoxStrokeWidth) * clampedConfidence) * strokeMultiplier
            val glowStrokeWidth = (minGlowStrokeWidth +
                (maxGlowStrokeWidth - minGlowStrokeWidth) * clampedConfidence) * strokeMultiplier
            val innerStrokeWidth = (minInnerStrokeWidth +
                (maxInnerStrokeWidth - minInnerStrokeWidth) * clampedConfidence) * strokeMultiplier

            // Convert normalized bbox to image space coordinates
            val imageSpaceRect = detection.bboxNorm.toRectF(imageSize.width, imageSize.height)
            // Transform bounding box from image coordinates to preview coordinates
            val transformedBox = transformBoundingBox(imageSpaceRect, transform)

            val topLeft = Offset(transformedBox.left, transformedBox.top)
            val boxSize = ComposeSize(transformedBox.width(), transformedBox.height())

            // Draw glow stroke for better visibility on bright scenes
            drawRoundRect(
                color = glowColor.copy(alpha = glowColor.alpha * boxAlpha),
                topLeft = topLeft,
                size = boxSize,
                cornerRadius = CornerRadius(boxCornerRadius, boxCornerRadius),
                style = Stroke(width = glowStrokeWidth)
            )

            // Draw main bounding box stroke
            drawRoundRect(
                color = outlineColor.copy(alpha = boxAlpha),
                topLeft = topLeft,
                size = boxSize,
                cornerRadius = CornerRadius(boxCornerRadius, boxCornerRadius),
                style = Stroke(width = boxStrokeWidth)
            )

            // Draw crisp white border inside for contrast
            drawRoundRect(
                color = Color.White.copy(alpha = boxAlpha),
                topLeft = topLeft,
                size = boxSize,
                cornerRadius = CornerRadius(boxCornerRadius, boxCornerRadius),
                style = Stroke(width = innerStrokeWidth)
            )

            // Category + price label near the bounding box
            val labelText = buildString {
                append(detection.label)
                val price = detection.priceText
                if (price.isNotBlank()) {
                    append(" • ")
                    append(price)
                }
            }

            if (labelText.isNotBlank()) {
                val textLayoutResult = textMeasurer.measure(
                    text = AnnotatedString(labelText),
                    style = labelTextStyle
                )

                val textWidth = textLayoutResult.size.width.toFloat()
                val textHeight = textLayoutResult.size.height.toFloat()
                val labelWidth = textWidth + labelHorizontalPadding * 2
                val labelHeight = textHeight + labelVerticalPadding * 2

                val maxLabelLeft = max(0f, canvasWidth - labelWidth)
                var labelLeft = transformedBox.left.coerceIn(0f, maxLabelLeft)

                val preferredTop = transformedBox.top - labelMargin - labelHeight
                var labelTop = if (preferredTop < 0f) {
                    transformedBox.bottom + labelMargin
                } else {
                    preferredTop
                }
                val maxLabelTop = max(0f, canvasHeight - labelHeight)
                labelTop = labelTop.coerceIn(0f, maxLabelTop)

                // Label background with pulsing alpha when scanning/estimating
                drawRoundRect(
                    color = labelBackgroundColor.copy(alpha = labelBackgroundColor.alpha * labelAlpha),
                    topLeft = Offset(labelLeft, labelTop),
                    size = ComposeSize(labelWidth, labelHeight),
                    cornerRadius = CornerRadius(labelCornerRadius, labelCornerRadius)
                )

                // Label text with pulsing alpha when scanning/estimating
                drawText(
                    textLayoutResult = textLayoutResult,
                    alpha = labelAlpha,
                    topLeft = Offset(
                        x = labelLeft + labelHorizontalPadding,
                        y = labelTop + labelVerticalPadding
                    )
                )
            }
        }

        // Record overlay draw timing
        Trace.endSection()
        val drawDuration = SystemClock.elapsedRealtime() - drawStartTime
        if (detections.isNotEmpty()) {
            PerformanceMonitor.recordTimer(
                PerformanceMonitor.Metrics.OVERLAY_DRAW_LATENCY_MS,
                drawDuration,
                mapOf("detection_count" to detections.size.toString())
            )
        }
    }
}

/**
 * Transformation parameters for converting image coordinates to preview coordinates.
 */
