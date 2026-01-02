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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.scanium.app.camera.geom.CorrelationDebug
import com.scanium.app.perf.PerformanceMonitor
import com.scanium.app.ui.theme.CyanGlow
import com.scanium.app.ui.theme.DeepNavy
import com.scanium.app.ui.theme.ScaniumBlue
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import kotlin.math.max
import androidx.compose.ui.geometry.Size as ComposeSize

/**
 * Overlay that renders bounding boxes for detected objects so users can see what the
 * camera believes is being scanned.
 *
 * @param detections List of detection results to render
 * @param imageSize Size of the analyzed image (from ML Kit) - raw sensor dimensions
 * @param previewSize Size of the preview view on screen
 * @param rotationDegrees Image rotation from ImageProxy (0, 90, 180, 270) for coordinate mapping
 */

/**
 * Visual colors for bounding box states.
 *
 * Eye Mode vs Focus Mode visual hierarchy:
 * - EYE: Very subtle, global awareness (detections anywhere)
 * - SELECTED: Highlighted, user intent (detection inside ROI)
 * - READY: Accent green, conditions met
 * - LOCKED: Bright green, scan-ready
 */
private object BboxColors {
    /** Eye state: very subtle - global detection anywhere in frame */
    val EyeOutline = Color.White.copy(alpha = 0.35f)
    val EyeGlow = Color.White.copy(alpha = 0.1f)

    /** Selected state: accent blue - object center inside ROI (user intent) */
    val SelectedOutline = ScaniumBlue.copy(alpha = 0.85f)
    val SelectedGlow = CyanGlow.copy(alpha = 0.4f)

    /** Ready state: accent green - conditions met, hold steady */
    val ReadyOutline = Color(0xFF1DB954).copy(alpha = 0.85f)
    val ReadyGlow = Color(0xFF1DB954).copy(alpha = 0.4f)

    /** Locked state: bright green - scan ready */
    val LockedOutline = Color(0xFF1DB954)
    val LockedGlow = Color(0xFF1DB954).copy(alpha = 0.5f)
}

@Composable
fun DetectionOverlay(
    detections: List<OverlayTrack>,
    imageSize: Size,
    previewSize: Size,
    rotationDegrees: Int = 90,
// Default to portrait mode (most common on phones)
    showGeometryDebug: Boolean = false,
// Developer toggle for geometry debug overlay
    modifier: Modifier = Modifier,
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
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pricePulseValue",
    )

    // Single pulse animation for LOCKED state transition (once)
    // This creates a brief "pop" effect when lock is achieved
    val lockedPulseScale = remember { Animatable(1f) }
    LaunchedEffect(hasLocked) {
        if (hasLocked) {
            // Brief pulse: scale up slightly then back to normal
            lockedPulseScale.animateTo(
                targetValue = 1.15f,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            )
            lockedPulseScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
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

        // Calculate rotation-aware transformation parameters
        // This handles: (1) rotation of bbox coordinates for portrait/landscape
        //               (2) FILL_CENTER scaling (center-crop) used by PreviewView
        val transform =
            calculateTransformWithRotation(
                imageWidth = imageSize.width,
                imageHeight = imageSize.height,
                previewWidth = canvasWidth,
                previewHeight = canvasHeight,
                rotationDegrees = rotationDegrees,
                scaleType = PreviewScaleType.FILL_CENTER,
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

            // Eye Mode vs Focus Mode visual states
            // Visual progression: EYE → SELECTED → READY → LOCKED
            // - EYE: very thin, subtle (global vision - detected anywhere)
            // - SELECTED: medium stroke, accent (user intent - inside ROI)
            // - READY: medium-thick, green (conditions met, holding)
            // - LOCKED: thick, bright green with pulse (scan-ready)
            val (outlineColor, glowColor, strokeMultiplier) =
                when (boxStyle) {
                    OverlayBoxStyle.LOCKED ->
                        Triple(
                            BboxColors.LockedOutline,
                            BboxColors.LockedGlow,
                            1.4f * lockedPulseScale.value,
// Thick stroke with pulse effect
                        )
                    OverlayBoxStyle.READY ->
                        Triple(
                            BboxColors.ReadyOutline,
                            BboxColors.ReadyGlow,
                            1.1f,
// Medium-thick stroke for ready
                        )
                    OverlayBoxStyle.SELECTED ->
                        Triple(
                            BboxColors.SelectedOutline,
                            BboxColors.SelectedGlow,
                            0.9f,
// Medium stroke for selected
                        )
                    OverlayBoxStyle.EYE ->
                        Triple(
                            BboxColors.EyeOutline,
                            BboxColors.EyeGlow,
                            0.5f,
// Very thin stroke for eye mode
                        )
                }

            val clampedConfidence = detection.confidence.coerceIn(0f, 1f)
            val boxStrokeWidth =
                (
                    minBoxStrokeWidth +
                        (maxBoxStrokeWidth - minBoxStrokeWidth) * clampedConfidence
                ) * strokeMultiplier
            val glowStrokeWidth =
                (
                    minGlowStrokeWidth +
                        (maxGlowStrokeWidth - minGlowStrokeWidth) * clampedConfidence
                ) * strokeMultiplier
            val innerStrokeWidth =
                (
                    minInnerStrokeWidth +
                        (maxInnerStrokeWidth - minInnerStrokeWidth) * clampedConfidence
                ) * strokeMultiplier

            // Map normalized bbox to preview coordinates with rotation handling
            // This correctly handles portrait mode where sensor coords need 90° rotation
            val transformedBox = mapBboxToPreview(detection.bboxNorm, transform)

            // Debug logging (rate-limited to once per second)
            logBboxMappingDebug(detection.bboxNorm, transform, transformedBox)

            val topLeft = Offset(transformedBox.left, transformedBox.top)
            val boxSize = ComposeSize(transformedBox.width(), transformedBox.height())

            // Draw glow stroke for better visibility on bright scenes
            drawRoundRect(
                color = glowColor.copy(alpha = glowColor.alpha * boxAlpha),
                topLeft = topLeft,
                size = boxSize,
                cornerRadius = CornerRadius(boxCornerRadius, boxCornerRadius),
                style = Stroke(width = glowStrokeWidth),
            )

            // Draw main bounding box stroke
            drawRoundRect(
                color = outlineColor.copy(alpha = boxAlpha),
                topLeft = topLeft,
                size = boxSize,
                cornerRadius = CornerRadius(boxCornerRadius, boxCornerRadius),
                style = Stroke(width = boxStrokeWidth),
            )

            // Draw crisp white border inside for contrast
            drawRoundRect(
                color = Color.White.copy(alpha = boxAlpha),
                topLeft = topLeft,
                size = boxSize,
                cornerRadius = CornerRadius(boxCornerRadius, boxCornerRadius),
                style = Stroke(width = innerStrokeWidth),
            )

            // Category + price label near the bounding box
            val labelText =
                buildString {
                    append(detection.label)
                    val price = detection.priceText
                    if (price.isNotBlank()) {
                        append(" • ")
                        append(price)
                    }
                }

            if (labelText.isNotBlank()) {
                val textLayoutResult =
                    textMeasurer.measure(
                        text = AnnotatedString(labelText),
                        style = labelTextStyle,
                    )

                val textWidth = textLayoutResult.size.width.toFloat()
                val textHeight = textLayoutResult.size.height.toFloat()
                val labelWidth = textWidth + labelHorizontalPadding * 2
                val labelHeight = textHeight + labelVerticalPadding * 2

                val maxLabelLeft = max(0f, canvasWidth - labelWidth)
                var labelLeft = transformedBox.left.coerceIn(0f, maxLabelLeft)

                val preferredTop = transformedBox.top - labelMargin - labelHeight
                var labelTop =
                    if (preferredTop < 0f) {
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
                    cornerRadius = CornerRadius(labelCornerRadius, labelCornerRadius),
                )

                // Label text with pulsing alpha when scanning/estimating
                drawText(
                    textLayoutResult = textLayoutResult,
                    alpha = labelAlpha,
                    topLeft =
                        Offset(
                            x = labelLeft + labelHorizontalPadding,
                            y = labelTop + labelVerticalPadding,
                        ),
                )
            }
        }

        // Geometry debug overlay (developer toggle)
        if (showGeometryDebug) {
            val debugPaint =
                android.graphics.Paint().apply {
                    color = android.graphics.Color.YELLOW
                    textSize = 32f
                    isAntiAlias = true
                }
            val debugBgPaint =
                android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(180, 0, 0, 0)
                    style = android.graphics.Paint.Style.FILL
                }

            val context =
                DetectionGeometryMapper.GeometryContext(
                    sensorWidth = imageSize.width,
                    sensorHeight = imageSize.height,
                    rotationDegrees = rotationDegrees,
                    previewWidth = canvasWidth,
                    previewHeight = canvasHeight,
                    scaleType = PreviewScaleType.FILL_CENTER,
                )

            // Get top detection info if available
            val topDetection = detections.firstOrNull()
            val topDetectionScreen =
                topDetection?.let {
                    mapBboxToPreview(it.bboxNorm, transform)
                }

            // Log debug info (rate-limited)
            DetectionGeometryMapper.logDebug(
                context = context,
                detectionCount = detections.size,
                topDetectionNorm = topDetection?.bboxNorm,
                topDetectionScreen = topDetectionScreen,
            )

            // Draw debug info on canvas
            drawContext.canvas.nativeCanvas.apply {
                // Calculate bbox aspect ratio (height/width - tall objects should be > 1)
                val bboxAspectRatio =
                    topDetection?.let {
                        val width = it.bboxNorm.right - it.bboxNorm.left
                        val height = it.bboxNorm.bottom - it.bboxNorm.top
                        if (width > 0) height / width else 0f
                    }
                val screenAspectRatio =
                    topDetectionScreen?.let {
                        if (it.width() > 0) it.height() / it.width() else 0f
                    }

                val lines =
                    listOf(
                        "UPRIGHT COORDS (fixed)",
                        "Preview: ${canvasWidth.toInt()}x${canvasHeight.toInt()}",
                        "Sensor: ${imageSize.width}x${imageSize.height}",
                        "Rotation: $rotationDegrees°",
                        "Effective: ${transform.effectiveImageWidth}x${transform.effectiveImageHeight}",
                        "Scale: ${String.format("%.3f", transform.scale)}",
                        "Offset: (${transform.offsetX.toInt()}, ${transform.offsetY.toInt()})",
                        "Detections: ${detections.size}",
                        topDetection?.let {
                            "Bbox (norm): (${String.format("%.2f", it.bboxNorm.left)}, " +
                                "${String.format("%.2f", it.bboxNorm.top)}) - " +
                                "(${String.format("%.2f", it.bboxNorm.right)}, " +
                                "${String.format("%.2f", it.bboxNorm.bottom)})"
                        } ?: "Bbox: N/A",
                        "Bbox aspect (h/w): ${bboxAspectRatio?.let { String.format("%.2f", it) } ?: "N/A"} ${if ((bboxAspectRatio ?: 0f) > 1f) "(TALL)" else "(wide)"}",
                        topDetectionScreen?.let {
                            "Screen: (${it.left.toInt()}, ${it.top.toInt()}) - " +
                                "(${it.right.toInt()}, ${it.bottom.toInt()})"
                        } ?: "Screen: N/A",
                        "Screen aspect: ${screenAspectRatio?.let { String.format("%.2f", it) } ?: "N/A"}",
                    )

                val padding = 12f
                val lineHeight = 36f
                val boxHeight = lines.size * lineHeight + padding * 2
                val boxWidth = 420f

                // Draw background
                drawRect(
                    padding,
                    padding,
                    padding + boxWidth,
                    padding + boxHeight,
                    debugBgPaint,
                )

                // Draw text lines
                lines.forEachIndexed { index, line ->
                    drawText(
                        line,
                        padding * 2,
                        padding + lineHeight * (index + 1),
                        debugPaint,
                    )
                }

                // Draw effective content rect outline (magenta)
                val effectivePaint =
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.MAGENTA
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 3f
                    }
                val effectiveLeft = transform.offsetX
                val effectiveTop = transform.offsetY
                val effectiveRight = transform.offsetX + transform.effectiveImageWidth * transform.scale
                val effectiveBottom = transform.offsetY + transform.effectiveImageHeight * transform.scale
                drawRect(effectiveLeft, effectiveTop, effectiveRight, effectiveBottom, effectivePaint)
            }
        }

        // Correlation debug recording (for CORR tag logging)
        if (CorrelationDebug.enabled && detections.isNotEmpty()) {
            val topDetection = detections.first()
            CorrelationDebug.recordCorrelation(
                normalizedBbox = topDetection.bboxNorm,
                rotationDegrees = rotationDegrees,
                proxyWidth = imageSize.width,
                proxyHeight = imageSize.height,
                inputImageWidth = transform.effectiveImageWidth,
                inputImageHeight = transform.effectiveImageHeight,
                previewWidth = canvasWidth.toInt(),
                previewHeight = canvasHeight.toInt(),
                // Bitmap dimensions for snapshot will be similar to capture resolution
                // Use effective image dimensions as proxy since we don't have actual bitmap here
                bitmapWidth = transform.effectiveImageWidth,
                bitmapHeight = transform.effectiveImageHeight,
            )
        }

        // Record overlay draw timing
        Trace.endSection()
        val drawDuration = SystemClock.elapsedRealtime() - drawStartTime
        if (detections.isNotEmpty()) {
            PerformanceMonitor.recordTimer(
                PerformanceMonitor.Metrics.OVERLAY_DRAW_LATENCY_MS,
                drawDuration,
                mapOf("detection_count" to detections.size.toString()),
            )
        }
    }
}

/**
 * Transformation parameters for converting image coordinates to preview coordinates.
 */
