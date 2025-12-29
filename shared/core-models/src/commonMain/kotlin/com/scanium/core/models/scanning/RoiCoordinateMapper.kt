package com.scanium.core.models.scanning

import kotlin.math.roundToInt

/**
 * Utilities for mapping ROI coordinates between different coordinate spaces.
 *
 * This is the single source of truth for coordinate transformations, ensuring
 * that the UI overlay and analyzer use the same ROI region.
 *
 * Coordinate spaces:
 * 1. Normalized (0.0 - 1.0): Used by ScanRoi, portable across resolutions
 * 2. Preview space (px): UI preview composable dimensions
 * 3. Analyzer space (px): ImageAnalysis frame dimensions
 *
 * Important: CameraX may apply different cropping/scaling to Preview vs ImageAnalysis.
 * This mapper accounts for those differences when the preview and analyzer have
 * different aspect ratios.
 */
object RoiCoordinateMapper {

    /**
     * Map a normalized ROI to preview pixel coordinates.
     *
     * @param roi The normalized ROI (0-1 coordinates)
     * @param previewWidth Preview width in pixels
     * @param previewHeight Preview height in pixels
     * @return RoiPixelRect with pixel coordinates for UI rendering
     */
    fun toPreviewPixels(
        roi: ScanRoi,
        previewWidth: Int,
        previewHeight: Int
    ): RoiPixelRect {
        return RoiPixelRect(
            left = (roi.left * previewWidth).roundToInt(),
            top = (roi.top * previewHeight).roundToInt(),
            right = (roi.right * previewWidth).roundToInt(),
            bottom = (roi.bottom * previewHeight).roundToInt()
        )
    }

    /**
     * Map a normalized ROI to preview float coordinates (for Compose drawing).
     *
     * @param roi The normalized ROI (0-1 coordinates)
     * @param previewWidth Preview width in pixels
     * @param previewHeight Preview height in pixels
     * @return RoiFloatRect with float coordinates for Compose Canvas
     */
    fun toPreviewFloats(
        roi: ScanRoi,
        previewWidth: Float,
        previewHeight: Float
    ): RoiFloatRect {
        return RoiFloatRect(
            left = roi.left * previewWidth,
            top = roi.top * previewHeight,
            right = roi.right * previewWidth,
            bottom = roi.bottom * previewHeight
        )
    }

    /**
     * Map a normalized ROI to analyzer image pixel coordinates.
     *
     * Accounts for aspect ratio differences between preview and analyzer.
     * CameraX uses center-crop scaling, so we need to adjust for that.
     *
     * @param roi The normalized ROI (0-1 coordinates)
     * @param analyzerWidth Analyzer image width in pixels
     * @param analyzerHeight Analyzer image height in pixels
     * @param previewAspectRatio Optional preview aspect ratio (width/height) for adjustment
     * @return RoiPixelRect with pixel coordinates for analyzer filtering
     */
    fun toAnalyzerPixels(
        roi: ScanRoi,
        analyzerWidth: Int,
        analyzerHeight: Int,
        previewAspectRatio: Float? = null
    ): RoiPixelRect {
        // If no aspect ratio adjustment needed, direct mapping
        if (previewAspectRatio == null) {
            val left = (roi.left * analyzerWidth).toInt()
            val top = (roi.top * analyzerHeight).toInt()
            val width = (roi.clampedWidth * analyzerWidth).toInt()
            val height = (roi.clampedHeight * analyzerHeight).toInt()

            return RoiPixelRect(
                left = left,
                top = top,
                right = (left + width).coerceAtMost(analyzerWidth),
                bottom = (top + height).coerceAtMost(analyzerHeight)
            )
        }

        // Account for center-crop scaling differences
        val analyzerAspectRatio = analyzerWidth.toFloat() / analyzerHeight.toFloat()

        return if (analyzerAspectRatio > previewAspectRatio) {
            // Analyzer is wider - horizontal cropping applied
            val visibleWidth = analyzerHeight * previewAspectRatio
            val cropX = (analyzerWidth - visibleWidth) / 2f

            RoiPixelRect(
                left = (cropX + roi.left * visibleWidth).roundToInt(),
                top = (roi.top * analyzerHeight).roundToInt(),
                right = (cropX + roi.right * visibleWidth).roundToInt(),
                bottom = (roi.bottom * analyzerHeight).roundToInt()
            )
        } else {
            // Analyzer is taller - vertical cropping applied
            val visibleHeight = analyzerWidth / previewAspectRatio
            val cropY = (analyzerHeight - visibleHeight) / 2f

            RoiPixelRect(
                left = (roi.left * analyzerWidth).roundToInt(),
                top = (cropY + roi.top * visibleHeight).roundToInt(),
                right = (roi.right * analyzerWidth).roundToInt(),
                bottom = (cropY + roi.bottom * visibleHeight).roundToInt()
            )
        }
    }

    /**
     * Convert a detection bounding box from analyzer space to normalized coordinates.
     *
     * This is the inverse operation used when ML Kit returns detection boxes
     * in analyzer pixel coordinates.
     *
     * @param boxLeft Detection box left in analyzer pixels
     * @param boxTop Detection box top in analyzer pixels
     * @param boxRight Detection box right in analyzer pixels
     * @param boxBottom Detection box bottom in analyzer pixels
     * @param analyzerWidth Analyzer image width
     * @param analyzerHeight Analyzer image height
     * @param previewAspectRatio Optional preview aspect ratio for adjustment
     * @return NormalizedBox in 0-1 coordinate space
     */
    fun detectionToNormalized(
        boxLeft: Int,
        boxTop: Int,
        boxRight: Int,
        boxBottom: Int,
        analyzerWidth: Int,
        analyzerHeight: Int,
        previewAspectRatio: Float? = null
    ): NormalizedBox {
        if (analyzerWidth == 0 || analyzerHeight == 0) {
            return NormalizedBox(0f, 0f, 0f, 0f)
        }

        // If no aspect ratio adjustment needed, direct mapping
        if (previewAspectRatio == null) {
            return NormalizedBox(
                left = boxLeft.toFloat() / analyzerWidth,
                top = boxTop.toFloat() / analyzerHeight,
                right = boxRight.toFloat() / analyzerWidth,
                bottom = boxBottom.toFloat() / analyzerHeight
            )
        }

        val analyzerAspectRatio = analyzerWidth.toFloat() / analyzerHeight.toFloat()

        return if (analyzerAspectRatio > previewAspectRatio) {
            // Analyzer was wider - horizontal cropping was applied
            val visibleWidth = analyzerHeight * previewAspectRatio
            val cropX = (analyzerWidth - visibleWidth) / 2f

            NormalizedBox(
                left = ((boxLeft - cropX) / visibleWidth).coerceIn(0f, 1f),
                top = (boxTop.toFloat() / analyzerHeight).coerceIn(0f, 1f),
                right = ((boxRight - cropX) / visibleWidth).coerceIn(0f, 1f),
                bottom = (boxBottom.toFloat() / analyzerHeight).coerceIn(0f, 1f)
            )
        } else {
            // Analyzer was taller - vertical cropping was applied
            val visibleHeight = analyzerWidth / previewAspectRatio
            val cropY = (analyzerHeight - visibleHeight) / 2f

            NormalizedBox(
                left = (boxLeft.toFloat() / analyzerWidth).coerceIn(0f, 1f),
                top = ((boxTop - cropY) / visibleHeight).coerceIn(0f, 1f),
                right = (boxRight.toFloat() / analyzerWidth).coerceIn(0f, 1f),
                bottom = ((boxBottom - cropY) / visibleHeight).coerceIn(0f, 1f)
            )
        }
    }

    /**
     * Check if a detection center point is inside the ROI.
     *
     * Convenience method that handles coordinate conversion internally.
     *
     * @param detectionCenterX Detection center X in analyzer pixels
     * @param detectionCenterY Detection center Y in analyzer pixels
     * @param roi The scan ROI in normalized coordinates
     * @param analyzerWidth Analyzer image width
     * @param analyzerHeight Analyzer image height
     * @return true if the detection center is inside the ROI
     */
    fun isDetectionInsideRoi(
        detectionCenterX: Int,
        detectionCenterY: Int,
        roi: ScanRoi,
        analyzerWidth: Int,
        analyzerHeight: Int
    ): Boolean {
        if (analyzerWidth == 0 || analyzerHeight == 0) return false

        val normalizedX = detectionCenterX.toFloat() / analyzerWidth
        val normalizedY = detectionCenterY.toFloat() / analyzerHeight

        return roi.containsPoint(normalizedX, normalizedY)
    }

    /**
     * Calculate center score for a detection relative to the ROI.
     *
     * @param detectionCenterX Detection center X in analyzer pixels
     * @param detectionCenterY Detection center Y in analyzer pixels
     * @param roi The scan ROI
     * @param analyzerWidth Analyzer image width
     * @param analyzerHeight Analyzer image height
     * @return Score from 0.0 (far from center) to 1.0 (perfectly centered)
     */
    fun calculateCenterScore(
        detectionCenterX: Int,
        detectionCenterY: Int,
        roi: ScanRoi,
        analyzerWidth: Int,
        analyzerHeight: Int
    ): Float {
        if (analyzerWidth == 0 || analyzerHeight == 0) return 0f

        val normalizedX = detectionCenterX.toFloat() / analyzerWidth
        val normalizedY = detectionCenterY.toFloat() / analyzerHeight

        return roi.centerScore(normalizedX, normalizedY)
    }

    /**
     * Calculate the visible viewport when analyzer has different aspect ratio than preview.
     *
     * This helps understand what portion of the analyzer frame is actually visible
     * to the user in the preview.
     *
     * @param analyzerWidth Analyzer image width
     * @param analyzerHeight Analyzer image height
     * @param previewWidth Preview width
     * @param previewHeight Preview height
     * @return RoiPixelRect representing the visible region in analyzer coordinates
     */
    fun calculateVisibleViewport(
        analyzerWidth: Int,
        analyzerHeight: Int,
        previewWidth: Int,
        previewHeight: Int
    ): RoiPixelRect {
        if (previewWidth == 0 || previewHeight == 0) {
            return RoiPixelRect(0, 0, analyzerWidth, analyzerHeight)
        }

        val analyzerAspect = analyzerWidth.toFloat() / analyzerHeight.toFloat()
        val previewAspect = previewWidth.toFloat() / previewHeight.toFloat()

        return if (analyzerAspect > previewAspect) {
            // Analyzer is wider - crop horizontally
            val visibleWidth = (analyzerHeight * previewAspect).roundToInt()
            val cropX = (analyzerWidth - visibleWidth) / 2
            RoiPixelRect(cropX, 0, cropX + visibleWidth, analyzerHeight)
        } else {
            // Analyzer is taller - crop vertically
            val visibleHeight = (analyzerWidth / previewAspect).roundToInt()
            val cropY = (analyzerHeight - visibleHeight) / 2
            RoiPixelRect(0, cropY, analyzerWidth, cropY + visibleHeight)
        }
    }
}

/**
 * ROI rectangle in pixel coordinates (integer).
 */
data class RoiPixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    /**
     * Convert to Android Rect (for use in Android-specific code).
     */
    fun toAndroidRectValues(): List<Int> = listOf(left, top, right, bottom)

    /**
     * Check if a point is inside this rect.
     */
    fun contains(x: Int, y: Int): Boolean {
        return x in left..right && y in top..bottom
    }
}

/**
 * ROI rectangle in float coordinates (for Compose Canvas).
 */
data class RoiFloatRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /**
     * Check if a point is inside this rect.
     */
    fun contains(x: Float, y: Float): Boolean {
        return x in left..right && y in top..bottom
    }
}

/**
 * Normalized bounding box (0-1 coordinates).
 */
data class NormalizedBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val area: Float get() = width * height

    /**
     * Check if this box's center is inside a ScanRoi.
     */
    fun isCenterInsideRoi(roi: ScanRoi): Boolean {
        return roi.containsPoint(centerX, centerY)
    }

    /**
     * Get the center score relative to a ScanRoi.
     */
    fun centerScoreInRoi(roi: ScanRoi): Float {
        return roi.centerScore(centerX, centerY)
    }
}
