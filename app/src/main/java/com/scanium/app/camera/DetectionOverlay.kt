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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.scanium.app.ml.DetectionResult
import com.scanium.app.ui.theme.ScaniumBlue
import com.scanium.app.ui.theme.CyanGlow
import kotlin.math.max
import kotlin.math.min

/**
 * Overlay that renders center point markers for detected objects.
 *
 * Uses small circular markers instead of full bounding boxes to reduce visual clutter
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

        // Circle appearance constants
        val circleRadius = 8.dp.toPx() // Small but visible marker
        val glowRadius = 12.dp.toPx() // Subtle glow effect
        val strokeWidth = 2.dp.toPx()

        detections.forEach { detection ->
            // Transform bounding box from image coordinates to preview coordinates
            val transformedBox = transformBoundingBox(detection.boundingBox, transform)

            // Calculate center point of the bounding box
            val centerX = transformedBox.left + transformedBox.width() / 2f
            val centerY = transformedBox.top + transformedBox.height() / 2f
            val center = Offset(centerX, centerY)

            // Draw subtle glow effect (outer circle with transparency)
            drawCircle(
                color = CyanGlow.copy(alpha = 0.2f),
                radius = glowRadius,
                center = center
            )

            // Draw main filled circle marker
            drawCircle(
                color = ScaniumBlue,
                radius = circleRadius,
                center = center
            )

            // Draw white border for better visibility against all backgrounds
            drawCircle(
                color = Color.White,
                radius = circleRadius,
                center = center,
                style = Stroke(width = strokeWidth)
            )

            // Draw small inner dot for precise center indication
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = center
            )
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
