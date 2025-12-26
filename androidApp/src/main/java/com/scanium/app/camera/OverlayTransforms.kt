package com.scanium.app.camera

import android.graphics.RectF

/**
 * Transformation parameters for converting image coordinates to preview coordinates.
 */
data class Transform(
    val scaleX: Float,
    val scaleY: Float,
    val offsetX: Float,
    val offsetY: Float
)

/**
 * Calculates the transformation needed to map image coordinates to preview coordinates.
 * Handles aspect ratio differences and scaling.
 */
fun calculateTransform(
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
fun transformBoundingBox(
    box: RectF,
    transform: Transform
): RectF {
    val left = box.left * transform.scaleX + transform.offsetX
    val top = box.top * transform.scaleY + transform.offsetY
    val right = box.right * transform.scaleX + transform.offsetX
    val bottom = box.bottom * transform.scaleY + transform.offsetY

    return RectF(left, top, right, bottom)
}
