package com.scanium.app.camera

import android.graphics.RectF
import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import com.scanium.app.ml.DetectionResult
import com.scanium.app.platform.toRectF
import com.scanium.app.ui.theme.DeepNavy
import com.scanium.app.ui.theme.CyanGlow
import com.scanium.app.ui.theme.ScaniumBlue
import kotlin.math.max

/**
 * Overlay that renders center point markers for detected objects.
 *
 * Uses small square markers instead of full bounding boxes to reduce visual clutter
 * while still providing location feedback for each detected item.
 *
 * @param detections List of detection results to render
 * @param imageSize Size of the analyzed image (from ML Kit)
 * @param previewSize Size of the preview view on screen
 */
@Composable
fun DetectionOverlay(
    detections: List<DetectionResult>,
    imageSize: Size,
    previewSize: Size,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelTextStyle = MaterialTheme.typography.labelMedium.copy(color = Color.White)

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

        // Square marker appearance constants (changed from circles to squares)
        val squareSize = 10.dp.toPx() // Slightly bigger than previous circle (was 8dp radius)
        val glowSize = 14.dp.toPx() // Subtle glow effect (was 12dp radius)
        val strokeWidth = 2.dp.toPx()
        val labelHorizontalPadding = 8.dp.toPx()
        val labelVerticalPadding = 4.dp.toPx()
        val labelCornerRadius = 10.dp.toPx()
        val labelMargin = 6.dp.toPx()
        val labelBackgroundColor = DeepNavy.copy(alpha = 0.85f)

        detections.forEach { detection ->
            // Convert normalized bbox to image space coordinates
            val imageSpaceRect = detection.bboxNorm.toRectF(imageSize.width, imageSize.height)
            // Transform bounding box from image coordinates to preview coordinates
            val transformedBox = transformBoundingBox(imageSpaceRect, transform)

            // Calculate center point of the bounding box
            val centerX = transformedBox.left + transformedBox.width() / 2f
            val centerY = transformedBox.top + transformedBox.height() / 2f
            val center = Offset(centerX, centerY)

            // Draw subtle glow effect (outer square with transparency)
            drawRect(
                color = CyanGlow.copy(alpha = 0.2f),
                topLeft = Offset(centerX - glowSize / 2, centerY - glowSize / 2),
                size = ComposeSize(glowSize, glowSize)
            )

            // Draw main filled square marker
            drawRect(
                color = ScaniumBlue,
                topLeft = Offset(centerX - squareSize / 2, centerY - squareSize / 2),
                size = ComposeSize(squareSize, squareSize)
            )

            // Draw white border for better visibility against all backgrounds
            drawRect(
                color = Color.White,
                topLeft = Offset(centerX - squareSize / 2, centerY - squareSize / 2),
                size = ComposeSize(squareSize, squareSize),
                style = Stroke(width = strokeWidth)
            )

            // Draw small inner square for precise center indication
            val innerSquareSize = 3.dp.toPx()
            drawRect(
                color = Color.White,
                topLeft = Offset(centerX - innerSquareSize / 2, centerY - innerSquareSize / 2),
                size = ComposeSize(innerSquareSize, innerSquareSize)
            )

            // Price label overlay - Disabled to reduce visual clutter
            /*
            val priceLabel = detection.formattedPriceRange.ifBlank { "N/A" }
            val textLayoutResult = textMeasurer.measure(
                text = AnnotatedString(priceLabel),
                style = labelTextStyle
            )

            val textWidth = textLayoutResult.size.width.toFloat()
            val textHeight = textLayoutResult.size.height.toFloat()
            val labelWidth = textWidth + labelHorizontalPadding * 2
            val labelHeight = textHeight + labelVerticalPadding * 2

            val maxLabelLeft = max(0f, canvasWidth - labelWidth)
            var labelLeft = (centerX - labelWidth / 2f).coerceIn(0f, maxLabelLeft)

            val preferredTop = centerY - squareSize - labelMargin - labelHeight
            var labelTop = if (preferredTop < 0f) {
                centerY + squareSize + labelMargin
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
            */
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
