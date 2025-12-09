package com.scanium.app.camera

import android.graphics.RectF
import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.scanium.app.ml.DetectionResult
import kotlin.math.max
import kotlin.math.min

/**
 * Overlay that renders bounding boxes and price labels for detected objects.
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

        detections.forEach { detection ->
            // Transform bounding box from image coordinates to preview coordinates
            val transformedBox = transformBoundingBox(detection.boundingBox, transform)

            // Draw bounding box
            drawRect(
                color = Color.Green,
                topLeft = Offset(transformedBox.left, transformedBox.top),
                size = androidx.compose.ui.geometry.Size(
                    transformedBox.width(),
                    transformedBox.height()
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
            )

            // Draw price label with background
            val labelText = "${detection.category.name}: ${detection.formattedPriceRange}"

            drawIntoCanvas { canvas ->
                val paint = Paint().asFrameworkPaint().apply {
                    isAntiAlias = true
                    textSize = 40f
                    color = Color.White.toArgb()
                }

                val backgroundPaint = Paint().asFrameworkPaint().apply {
                    color = Color.Green.copy(alpha = 0.8f).toArgb()
                    style = android.graphics.Paint.Style.FILL
                }

                // Measure text
                val textBounds = android.graphics.Rect()
                paint.getTextBounds(labelText, 0, labelText.length, textBounds)

                // Position label above the bounding box (or below if too close to top)
                val labelX = transformedBox.left
                val labelY = if (transformedBox.top > textBounds.height() + 20) {
                    transformedBox.top - 10
                } else {
                    transformedBox.bottom + textBounds.height() + 10
                }

                // Draw background rectangle
                val padding = 8f
                canvas.nativeCanvas.drawRect(
                    labelX - padding,
                    labelY - textBounds.height() - padding,
                    labelX + textBounds.width() + padding,
                    labelY + padding,
                    backgroundPaint
                )

                // Draw text
                canvas.nativeCanvas.drawText(
                    labelText,
                    labelX,
                    labelY,
                    paint
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
    box: android.graphics.Rect,
    transform: Transform
): RectF {
    val left = box.left * transform.scaleX + transform.offsetX
    val top = box.top * transform.scaleY + transform.offsetY
    val right = box.right * transform.scaleX + transform.offsetX
    val bottom = box.bottom * transform.scaleY + transform.offsetY

    return RectF(left, top, right, bottom)
}
