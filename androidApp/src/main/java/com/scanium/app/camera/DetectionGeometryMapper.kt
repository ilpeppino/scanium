package com.scanium.app.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.util.Size
import com.scanium.core.models.geometry.NormalizedRect

private const val TAG = "GeomMap"

/**
 * Unified geometry mapper for detection coordinate transformations.
 *
 * Handles the complete coordinate pipeline from ML Kit detection to:
 * 1. Overlay display (live bounding boxes)
 * 2. Thumbnail cropping (item previews)
 *
 * COORDINATE CONTRACT (as of portrait bbox fix):
 * - ML Kit returns bboxes in InputImage coordinate space (upright, post-rotation)
 * - When using InputImage.fromMediaImage(image, rotationDegrees), ML Kit applies
 *   the rotation internally and returns bboxes that match InputImage.width/height
 * - All normalized bboxes are stored in UPRIGHT coordinate space
 *
 * Key coordinate spaces:
 * - Upright space: Display-oriented coordinates (720x1280 in portrait) - PRIMARY
 * - Sensor space: Raw sensor coordinates (1280x720 landscape typical) - for bitmap cropping only
 * - Preview space: Screen pixel coordinates in the preview composable
 */
object DetectionGeometryMapper {
    // Rate-limited logging state
    private var lastDebugLogTime = 0L
    private const val DEBUG_LOG_INTERVAL_MS = 1000L

    /**
     * Context for geometry transformations, containing all parameters needed for mapping.
     */
    data class GeometryContext(
        val sensorWidth: Int,
        val sensorHeight: Int,
        val rotationDegrees: Int,
        val previewWidth: Float,
        val previewHeight: Float,
        val scaleType: PreviewScaleType = PreviewScaleType.FILL_CENTER,
    ) {
        val isPortrait: Boolean get() = rotationDegrees == 90 || rotationDegrees == 270

        val uprightWidth: Int get() = if (isPortrait) sensorHeight else sensorWidth
        val uprightHeight: Int get() = if (isPortrait) sensorWidth else sensorHeight
    }

    /**
     * Debug information for geometry overlay display.
     */
    data class GeometryDebugInfo(
        val sensorSize: Size,
        val rotationDegrees: Int,
        val previewSize: Size,
        val effectiveSize: Size,
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float,
        val scaleType: PreviewScaleType,
        val topDetectionRaw: NormalizedRect?,
        val topDetectionMapped: RectF?,
    )

    /**
     * @deprecated This function was based on the incorrect assumption that ML Kit returns
     * bboxes in sensor space. ML Kit actually returns bboxes in InputImage (upright) space.
     * Use ObjectDetectorClient.uprightBboxToSensorBbox() for converting upright bboxes
     * to sensor space for bitmap cropping.
     */
    @Deprecated(
        message = "ML Kit returns upright bboxes, not sensor bboxes. Use uprightBboxToSensorBbox in ObjectDetectorClient.",
        level = DeprecationLevel.WARNING,
    )
    fun mlKitBboxToSensorSpace(
        mlKitBbox: Rect,
        rotationDegrees: Int,
        mlKitImageWidth: Int,
        mlKitImageHeight: Int,
        sensorWidth: Int,
        sensorHeight: Int,
    ): Rect {
        // First normalize in ML Kit's coordinate space (which is upright)
        val normLeft = mlKitBbox.left.toFloat() / mlKitImageWidth
        val normTop = mlKitBbox.top.toFloat() / mlKitImageHeight
        val normRight = mlKitBbox.right.toFloat() / mlKitImageWidth
        val normBottom = mlKitBbox.bottom.toFloat() / mlKitImageHeight

        // Apply inverse rotation to get sensor-space normalized coordinates
        val (sensorNormLeft, sensorNormTop, sensorNormRight, sensorNormBottom) =
            when (rotationDegrees) {
                0 -> listOf(normLeft, normTop, normRight, normBottom)
                90 -> {
                    // Inverse of 90° clockwise rotation
                    listOf(normTop, 1f - normRight, normBottom, 1f - normLeft)
                }
                180 -> {
                    // Inverse of 180° is 180°
                    listOf(1f - normRight, 1f - normBottom, 1f - normLeft, 1f - normTop)
                }
                270 -> {
                    // Inverse of 270° clockwise rotation
                    listOf(1f - normBottom, normLeft, 1f - normTop, normRight)
                }
                else -> {
                    Log.w(TAG, "Unexpected rotation: $rotationDegrees, using identity")
                    listOf(normLeft, normTop, normRight, normBottom)
                }
            }

        // Convert back to pixel coordinates in sensor space
        return Rect(
            (sensorNormLeft * sensorWidth).toInt(),
            (sensorNormTop * sensorHeight).toInt(),
            (sensorNormRight * sensorWidth).toInt(),
            (sensorNormBottom * sensorHeight).toInt(),
        )
    }

    /**
     * Converts a normalized rect from sensor space to bitmap crop coordinates.
     *
     * The bitmap should be in sensor orientation (unrotated). After cropping,
     * the result should be rotated to display orientation.
     *
     * @param sensorBboxNorm Normalized bbox in sensor (unrotated) space
     * @param bitmapWidth Width of the bitmap to crop from
     * @param bitmapHeight Height of the bitmap to crop from
     * @param paddingRatio Optional padding to add around the bbox (0.06 = 6%)
     * @return Crop rect in bitmap pixel coordinates, clamped to bitmap bounds
     */
    fun sensorBboxToBitmapCrop(
        sensorBboxNorm: NormalizedRect,
        bitmapWidth: Int,
        bitmapHeight: Int,
        paddingRatio: Float = 0.06f,
    ): Rect {
        // Convert normalized to pixel coordinates
        var left = sensorBboxNorm.left * bitmapWidth
        var top = sensorBboxNorm.top * bitmapHeight
        var right = sensorBboxNorm.right * bitmapWidth
        var bottom = sensorBboxNorm.bottom * bitmapHeight

        // Add padding
        val width = right - left
        val height = bottom - top
        val paddingX = width * paddingRatio
        val paddingY = height * paddingRatio

        left -= paddingX
        top -= paddingY
        right += paddingX
        bottom += paddingY

        // Clamp to bitmap bounds
        return Rect(
            left.toInt().coerceIn(0, bitmapWidth - 1),
            top.toInt().coerceIn(0, bitmapHeight - 1),
            right.toInt().coerceIn(1, bitmapWidth),
            bottom.toInt().coerceIn(1, bitmapHeight),
        )
    }

    /**
     * Crops and rotates a thumbnail from an unrotated sensor bitmap.
     *
     * @param sourceBitmap The unrotated bitmap from the sensor
     * @param sensorBboxNorm Normalized bbox in sensor space
     * @param rotationDegrees Rotation to apply for display orientation
     * @param paddingRatio Padding around the bbox
     * @param maxDimension Maximum dimension for the output (scales down if larger)
     * @return Rotated cropped bitmap ready for display, or null on error
     */
    fun cropAndRotateThumbnail(
        sourceBitmap: Bitmap,
        sensorBboxNorm: NormalizedRect,
        rotationDegrees: Int,
        paddingRatio: Float = 0.06f,
        maxDimension: Int = 512,
    ): Bitmap? {
        return try {
            // Get crop rect in sensor bitmap coordinates
            val cropRect =
                sensorBboxToBitmapCrop(
                    sensorBboxNorm,
                    sourceBitmap.width,
                    sourceBitmap.height,
                    paddingRatio,
                )

            // Ensure valid dimensions
            val width = (cropRect.right - cropRect.left).coerceAtLeast(1)
            val height = (cropRect.bottom - cropRect.top).coerceAtLeast(1)

            if (width <= 0 || height <= 0 ||
                cropRect.left < 0 || cropRect.top < 0 ||
                cropRect.right > sourceBitmap.width || cropRect.bottom > sourceBitmap.height
            ) {
                Log.w(TAG, "Invalid crop rect: $cropRect for bitmap ${sourceBitmap.width}x${sourceBitmap.height}")
                return null
            }

            // Crop from sensor bitmap
            val cropped =
                Bitmap.createBitmap(
                    sourceBitmap,
                    cropRect.left,
                    cropRect.top,
                    width,
                    height,
                )

            // Rotate to display orientation
            val rotatedBitmap =
                if (rotationDegrees != 0) {
                    val matrix = Matrix()
                    matrix.postRotate(rotationDegrees.toFloat())
                    val rotated =
                        Bitmap.createBitmap(
                            cropped,
                            0,
                            0,
                            cropped.width,
                            cropped.height,
                            matrix,
                            true,
                        )
                    if (rotated !== cropped) {
                        cropped.recycle()
                    }
                    rotated
                } else {
                    cropped
                }

            // Scale down if needed
            val maxDim = maxOf(rotatedBitmap.width, rotatedBitmap.height)
            if (maxDim > maxDimension) {
                val scale = maxDimension.toFloat() / maxDim
                val scaledWidth = (rotatedBitmap.width * scale).toInt().coerceAtLeast(1)
                val scaledHeight = (rotatedBitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(rotatedBitmap, scaledWidth, scaledHeight, true)
                if (scaled !== rotatedBitmap) {
                    rotatedBitmap.recycle()
                }
                scaled
            } else {
                rotatedBitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cropping thumbnail: ${e.message}")
            null
        }
    }

    /**
     * Logs geometry mapping debug information (rate-limited to once per second).
     */
    fun logDebug(
        context: GeometryContext,
        detectionCount: Int,
        topDetectionNorm: NormalizedRect? = null,
        topDetectionScreen: RectF? = null,
    ) {
        val now = System.currentTimeMillis()
        if (now - lastDebugLogTime < DEBUG_LOG_INTERVAL_MS) return
        lastDebugLogTime = now

        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(
            TAG,
            "Frame: sensor=${context.sensorWidth}x${context.sensorHeight}, " +
                "rotation=${context.rotationDegrees}°, " +
                "preview=${context.previewWidth.toInt()}x${context.previewHeight.toInt()}",
        )
        Log.d(
            TAG,
            "Upright: ${context.uprightWidth}x${context.uprightHeight}, " +
                "isPortrait=${context.isPortrait}, " +
                "scaleType=${context.scaleType}",
        )

        if (topDetectionNorm != null) {
            Log.d(
                TAG,
                "Top detection (norm): " +
                    "(${String.format("%.3f", topDetectionNorm.left)}, " +
                    "${String.format("%.3f", topDetectionNorm.top)}) - " +
                    "(${String.format("%.3f", topDetectionNorm.right)}, " +
                    "${String.format("%.3f", topDetectionNorm.bottom)})",
            )
        }

        if (topDetectionScreen != null) {
            Log.d(
                TAG,
                "Top detection (screen): " +
                    "(${topDetectionScreen.left.toInt()}, ${topDetectionScreen.top.toInt()}) - " +
                    "(${topDetectionScreen.right.toInt()}, ${topDetectionScreen.bottom.toInt()})",
            )
        }

        Log.d(TAG, "Detection count: $detectionCount")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    /**
     * Creates debug info for geometry overlay display.
     */
    fun createDebugInfo(
        context: GeometryContext,
        transform: BboxMappingTransform,
        topDetectionNorm: NormalizedRect?,
        topDetectionScreen: RectF?,
    ): GeometryDebugInfo {
        return GeometryDebugInfo(
            sensorSize = Size(context.sensorWidth, context.sensorHeight),
            rotationDegrees = context.rotationDegrees,
            previewSize = Size(context.previewWidth.toInt(), context.previewHeight.toInt()),
            effectiveSize = Size(transform.effectiveImageWidth, transform.effectiveImageHeight),
            scale = transform.scale,
            offsetX = transform.offsetX,
            offsetY = transform.offsetY,
            scaleType = transform.scaleType,
            topDetectionRaw = topDetectionNorm,
            topDetectionMapped = topDetectionScreen,
        )
    }

    /**
     * Validates that a mapped rect is within reasonable bounds of the preview.
     *
     * @param rect The mapped screen rect
     * @param previewWidth Preview width
     * @param previewHeight Preview height
     * @param tolerance Tolerance in pixels for bounds check
     * @return True if rect is within bounds (with tolerance)
     */
    fun validateMappedRect(
        rect: RectF,
        previewWidth: Float,
        previewHeight: Float,
        tolerance: Float = 50f,
    ): Boolean {
        val minX = -tolerance
        val minY = -tolerance
        val maxX = previewWidth + tolerance
        val maxY = previewHeight + tolerance

        val isValid =
            rect.left >= minX && rect.top >= minY &&
                rect.right <= maxX && rect.bottom <= maxY &&
                rect.width() > 0 && rect.height() > 0

        if (!isValid) {
            Log.w(TAG, "Invalid mapped rect: $rect for preview ${previewWidth}x$previewHeight")
        }

        return isValid
    }
}
