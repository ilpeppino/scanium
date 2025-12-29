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
 *
 * Uses CENTER_CROP logic to match CameraX Preview behavior:
 * - Scale uniformly to fill the preview (no letterboxing)
 * - Center the scaled image (crop excess on edges)
 *
 * This ensures bounding boxes align correctly with the camera preview which uses
 * FILL_CENTER (center-crop) mode.
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

    // CENTER_CROP: Scale to FILL preview, then center (crop excess)
    // This matches CameraX Preview's FILL_CENTER behavior
    if (imageAspect > previewAspect) {
        // Image is wider than preview - scale by height to fill, crop width
        scaleY = previewHeight / imageHeight
        scaleX = scaleY  // Uniform scale
        // Center horizontally - excess image width is cropped
        offsetX = (previewWidth - imageWidth * scaleX) / 2f
        offsetY = 0f
    } else {
        // Image is taller than preview - scale by width to fill, crop height
        scaleX = previewWidth / imageWidth
        scaleY = scaleX  // Uniform scale
        // Center vertically - excess image height is cropped
        offsetX = 0f
        offsetY = (previewHeight - imageHeight * scaleY) / 2f
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
