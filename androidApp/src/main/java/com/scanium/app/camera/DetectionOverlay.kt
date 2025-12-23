package com.scanium.app.camera

import android.graphics.RectF
import android.util.Size
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
import com.scanium.app.camera.OverlayTrack
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
    val pulseAlpha by if (hasEstimating) {
        rememberInfiniteTransition(label = "pricePulse").animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pricePulseValue"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
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
        val boxStrokeWidth = 3.dp.toPx()
        val boxCornerRadius = 12.dp.toPx()
        val glowStrokeWidth = 6.dp.toPx()
        val labelHorizontalPadding = 8.dp.toPx()
        val labelVerticalPadding = 4.dp.toPx()
        val labelCornerRadius = 10.dp.toPx()
        val labelMargin = 6.dp.toPx()
        val labelBackgroundColor = DeepNavy.copy(alpha = 0.85f)

        detections.forEach { detection ->
            val status = detection.priceEstimationStatus
            val isReady = detection.isReady
            val isEstimating = status is PriceEstimationStatus.Estimating
            val overlayAlpha = if (isEstimating) pulseAlpha else 1f
            val outlineColor = if (isReady) readyColor else ScaniumBlue
            val glowColor = if (isReady) readyColor.copy(alpha = 0.45f) else CyanGlow.copy(alpha = 0.35f)

            // Convert normalized bbox to image space coordinates
            val imageSpaceRect = detection.bboxNorm.toRectF(imageSize.width, imageSize.height)
            // Transform bounding box from image coordinates to preview coordinates
            val transformedBox = transformBoundingBox(imageSpaceRect, transform)

            val topLeft = Offset(transformedBox.left, transformedBox.top)
            val boxSize = ComposeSize(transformedBox.width(), transformedBox.height())

            // Draw glow stroke for better visibility on bright scenes
            drawRoundRect(
                color = glowColor.copy(alpha = glowColor.alpha * overlayAlpha),
                topLeft = topLeft,
                size = boxSize,
                cornerRadius = CornerRadius(boxCornerRadius, boxCornerRadius),
                style = Stroke(width = glowStrokeWidth)
            )

            // Draw main bounding box stroke
            drawRoundRect(
                color = outlineColor.copy(alpha = overlayAlpha),
                topLeft = topLeft,
                size = boxSize,
                cornerRadius = CornerRadius(boxCornerRadius, boxCornerRadius),
                style = Stroke(width = boxStrokeWidth)
            )

            // Draw crisp white border inside for contrast
            drawRoundRect(
                color = Color.White.copy(alpha = overlayAlpha),
                topLeft = topLeft,
                size = boxSize,
                cornerRadius = CornerRadius(boxCornerRadius, boxCornerRadius),
                style = Stroke(width = boxStrokeWidth / 2f)
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

                drawRoundRect(
                    color = labelBackgroundColor,
                    topLeft = Offset(labelLeft, labelTop),
                    size = ComposeSize(labelWidth, labelHeight),
                    cornerRadius = CornerRadius(labelCornerRadius, labelCornerRadius)
                )

                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        x = labelLeft + labelHorizontalPadding,
                        y = labelTop + labelVerticalPadding
                    )
                )
            }
        }
    }
}

/**
 * Transformation parameters for converting image coordinates to preview coordinates.
 */
private data class Transform(
    val scaleX: Float,
    val scaleY: Float,
    val offsetX: Float,
    val offsetY: Float
)

/**
 * Calculates the transformation needed to map image coordinates to preview coordinates.
 * Handles aspect ratio differences and scaling.
 */
private fun calculateTransform(
    imageWidth: Int,
    imageHeight: Int,
    previewWidth: Float,
    previewHeight: Float
): Transform {
    // Calculate aspect ratios
    val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
    val previewAspect = previewWidth / previewHeight

    val scaleX: Float
    val scaleY: Float
    val offsetX: Float
    val offsetY: Float

    if (imageAspect > previewAspect) {
        // Image is wider than preview - fit by width
        scaleX = previewWidth / imageWidth
        scaleY = scaleX
        offsetX = 0f
        offsetY = (previewHeight - imageHeight * scaleY) / 2f
    } else {
        // Image is taller than preview - fit by height
        scaleY = previewHeight / imageHeight
        scaleX = scaleY
        offsetX = (previewWidth - imageWidth * scaleX) / 2f
        offsetY = 0f
    }

    return Transform(scaleX, scaleY, offsetX, offsetY)
}

/**
 * Transforms a bounding box from image coordinates to preview coordinates.
 */
private fun transformBoundingBox(
    box: RectF,
    transform: Transform
): RectF {
    val left = box.left * transform.scaleX + transform.offsetX
    val top = box.top * transform.scaleY + transform.offsetY
    val right = box.right * transform.scaleX + transform.offsetX
    val bottom = box.bottom * transform.scaleY + transform.offsetY

    return RectF(left, top, right, bottom)
}
