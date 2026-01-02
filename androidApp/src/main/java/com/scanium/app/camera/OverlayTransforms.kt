package com.scanium.app.camera

import android.graphics.RectF
import android.util.Log
import com.scanium.core.models.geometry.NormalizedRect

private const val TAG = "OverlayTransforms"

/**
 * Scale type for preview - determines how the image fills the preview area.
 */
enum class PreviewScaleType {
    /** Letterbox: scales to fit entirely within the preview, may have padding */
    FIT_CENTER,

    /** Center-crop: scales to fill the preview, may crop edges */
    FILL_CENTER,
}

/**
 * Transformation parameters for converting image coordinates to preview coordinates.
 */
data class Transform(
    val scaleX: Float,
    val scaleY: Float,
    val offsetX: Float,
    val offsetY: Float,
)

/**
 * Extended transformation parameters including rotation handling.
 */
data class BboxMappingTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val rotationDegrees: Int,
    val effectiveImageWidth: Int,
    val effectiveImageHeight: Int,
    val scaleType: PreviewScaleType,
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
 *
 * @deprecated Use calculateTransformWithRotation for rotation-aware mapping that also
 * handles portrait/landscape orientation changes.
 */
fun calculateTransform(
    imageWidth: Int,
    imageHeight: Int,
    previewWidth: Float,
    previewHeight: Float,
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
        scaleX = scaleY // Uniform scale
        // Center horizontally - excess image width is cropped
        offsetX = (previewWidth - imageWidth * scaleX) / 2f
        offsetY = 0f
    } else {
        // Image is taller than preview - scale by width to fill, crop height
        scaleX = previewWidth / imageWidth
        scaleY = scaleX // Uniform scale
        // Center vertically - excess image height is cropped
        offsetX = 0f
        offsetY = (previewHeight - imageHeight * scaleY) / 2f
    }

    return Transform(scaleX, scaleY, offsetX, offsetY)
}

/**
 * Calculates the transformation needed to map upright bbox coordinates to preview coordinates.
 *
 * IMPORTANT COORDINATE CONTRACT:
 * - ML Kit returns bboxes in InputImage coordinate space (upright, post-rotation)
 * - Bboxes are stored and processed in upright normalized coordinates
 * - This function calculates scale/offset for mapping upright coords to preview
 *
 * This function handles:
 * 1. Effective dimensions: For portrait (90/270°), effective dims are swapped sensor dims
 * 2. Scale type: CameraX PreviewView defaults to FILL_CENTER (center-crop), not FIT_CENTER
 *
 * @param imageWidth Raw sensor image width (before rotation)
 * @param imageHeight Raw sensor image height (before rotation)
 * @param previewWidth Preview composable width in pixels
 * @param previewHeight Preview composable height in pixels
 * @param rotationDegrees Rotation from ImageProxy.imageInfo.rotationDegrees (0, 90, 180, 270)
 * @param scaleType How the preview fills its container
 * @return BboxMappingTransform with all parameters needed for coordinate transformation
 */
fun calculateTransformWithRotation(
    imageWidth: Int,
    imageHeight: Int,
    previewWidth: Float,
    previewHeight: Float,
    rotationDegrees: Int,
    scaleType: PreviewScaleType = PreviewScaleType.FILL_CENTER,
): BboxMappingTransform {
    // Step 1: Determine effective image dimensions after rotation
    // When rotation is 90 or 270, width and height are swapped for display
    val isRotated90or270 = rotationDegrees == 90 || rotationDegrees == 270
    val effectiveWidth = if (isRotated90or270) imageHeight else imageWidth
    val effectiveHeight = if (isRotated90or270) imageWidth else imageHeight

    // Step 2: Calculate scale factor based on scale type
    val imageAspect = effectiveWidth.toFloat() / effectiveHeight.toFloat()
    val previewAspect = previewWidth / previewHeight

    val scale: Float
    val offsetX: Float
    val offsetY: Float

    when (scaleType) {
        PreviewScaleType.FILL_CENTER -> {
            // Center-crop: scale to FILL the preview (larger scale), crop overflow
            scale =
                if (imageAspect > previewAspect) {
                    // Image is wider than preview - scale by height to fill vertically
                    previewHeight / effectiveHeight
                } else {
                    // Image is taller than preview - scale by width to fill horizontally
                    previewWidth / effectiveWidth
                }
            // Center the scaled image (negative offset means cropping)
            offsetX = (previewWidth - effectiveWidth * scale) / 2f
            offsetY = (previewHeight - effectiveHeight * scale) / 2f
        }
        PreviewScaleType.FIT_CENTER -> {
            // Letterbox: scale to FIT within preview (smaller scale), add padding
            scale =
                if (imageAspect > previewAspect) {
                    // Image is wider than preview - scale by width to fit horizontally
                    previewWidth / effectiveWidth
                } else {
                    // Image is taller than preview - scale by height to fit vertically
                    previewHeight / effectiveHeight
                }
            // Center the scaled image (positive offset means padding)
            offsetX = (previewWidth - effectiveWidth * scale) / 2f
            offsetY = (previewHeight - effectiveHeight * scale) / 2f
        }
    }

    return BboxMappingTransform(
        scale = scale,
        offsetX = offsetX,
        offsetY = offsetY,
        rotationDegrees = rotationDegrees,
        effectiveImageWidth = effectiveWidth,
        effectiveImageHeight = effectiveHeight,
        scaleType = scaleType,
    )
}

/**
 * Maps a normalized bounding box from upright coordinates to preview display coordinates.
 *
 * The coordinate transformation pipeline:
 * 1. Start with normalized bbox (0-1 range) ALREADY in upright coordinate space
 *    (ML Kit returns bboxes in InputImage space when rotation metadata is provided)
 * 2. Scale to preview dimensions with appropriate scale type (FILL/FIT)
 * 3. Apply offset for centering
 *
 * NOTE: No rotation is applied here because bbox is already in upright space.
 * The rotation was handled when InputImage was created with rotation metadata.
 *
 * @param bboxNorm Normalized bounding box in upright coordinate space
 * @param transform Transformation parameters from calculateTransformWithRotation
 * @return RectF in preview pixel coordinates, ready for drawing
 */
fun mapBboxToPreview(
    bboxNorm: NormalizedRect,
    transform: BboxMappingTransform,
): RectF {
    // NO ROTATION: bbox is already in upright coordinate space
    // ML Kit returns bboxes that match InputImage dimensions (post-rotation)

    // Step 1: Convert normalized coords to pixel coordinates in effective (upright) space
    val pixelLeft = bboxNorm.left * transform.effectiveImageWidth
    val pixelTop = bboxNorm.top * transform.effectiveImageHeight
    val pixelRight = bboxNorm.right * transform.effectiveImageWidth
    val pixelBottom = bboxNorm.bottom * transform.effectiveImageHeight

    // Step 2: Apply scale and offset to get preview coordinates
    val previewLeft = pixelLeft * transform.scale + transform.offsetX
    val previewTop = pixelTop * transform.scale + transform.offsetY
    val previewRight = pixelRight * transform.scale + transform.offsetX
    val previewBottom = pixelBottom * transform.scale + transform.offsetY

    return RectF(previewLeft, previewTop, previewRight, previewBottom)
}

/**
 * Rotates normalized coordinates to match display orientation.
 *
 * Rotation transformations for normalized [0-1] coordinates:
 * - 0°:   (x, y) -> (x, y)           - no change
 * - 90°:  (x, y) -> (y, 1-x)         - rotate clockwise
 * - 180°: (x, y) -> (1-x, 1-y)       - flip both axes
 * - 270°: (x, y) -> (1-y, x)         - rotate counter-clockwise
 *
 * Note: These transformations account for the fact that the sensor captures in landscape
 * but the device displays in portrait (or vice versa).
 */
fun rotateNormalizedRect(
    rect: NormalizedRect,
    rotationDegrees: Int,
): NormalizedRect {
    return when (rotationDegrees) {
        0 -> rect
        90 -> {
            // Rotate 90° clockwise: (x, y) -> (y, 1-x)
            // left->top, top->(1-right), right->bottom, bottom->(1-left)
            NormalizedRect(
                left = rect.top,
                top = 1f - rect.right,
                right = rect.bottom,
                bottom = 1f - rect.left,
            )
        }
        180 -> {
            // Rotate 180°: (x, y) -> (1-x, 1-y)
            NormalizedRect(
                left = 1f - rect.right,
                top = 1f - rect.bottom,
                right = 1f - rect.left,
                bottom = 1f - rect.top,
            )
        }
        270 -> {
            // Rotate 270° clockwise (90° counter-clockwise): (x, y) -> (1-y, x)
            NormalizedRect(
                left = 1f - rect.bottom,
                top = rect.left,
                right = 1f - rect.top,
                bottom = rect.right,
            )
        }
        else -> {
            Log.w(TAG, "Unexpected rotation degrees: $rotationDegrees, using 0")
            rect
        }
    }
}

/**
 * Transforms a bounding box from image coordinates to preview coordinates.
 *
 * @deprecated Use mapBboxToPreview for rotation-aware mapping
 */
fun transformBoundingBox(
    box: RectF,
    transform: Transform,
): RectF {
    val left = box.left * transform.scaleX + transform.offsetX
    val top = box.top * transform.scaleY + transform.offsetY
    val right = box.right * transform.scaleX + transform.offsetX
    val bottom = box.bottom * transform.scaleY + transform.offsetY

    return RectF(left, top, right, bottom)
}

// Rate-limited logging for bbox mapping debug
private var lastBboxDebugLogTime = 0L
private const val BBOX_DEBUG_LOG_INTERVAL_MS = 1000L

/**
 * Debug logging for bbox mapping (rate-limited to once per second).
 */
fun logBboxMappingDebug(
    bboxNorm: NormalizedRect,
    transform: BboxMappingTransform,
    resultRect: RectF,
    tag: String = "BboxMap",
) {
    val now = System.currentTimeMillis()
    if (now - lastBboxDebugLogTime >= BBOX_DEBUG_LOG_INTERVAL_MS) {
        lastBboxDebugLogTime = now
        Log.d(
            tag,
            "[MAPPING] rotation=${transform.rotationDegrees}°, " +
                "effectiveDims=${transform.effectiveImageWidth}x${transform.effectiveImageHeight}, " +
                "scale=${transform.scale}, offset=(${transform.offsetX}, ${transform.offsetY}), " +
                "scaleType=${transform.scaleType}",
        )
        Log.d(
            tag,
            "[MAPPING] input=(${bboxNorm.left},${bboxNorm.top})-(${bboxNorm.right},${bboxNorm.bottom}) " +
                "-> output=(${resultRect.left},${resultRect.top})-(${resultRect.right},${resultRect.bottom})",
        )
    }
}
