package com.scanium.core.models.scanning

import kotlin.math.sqrt

/**
 * Scan Region of Interest (ROI) - the canonical model used by BOTH UI and analyzer.
 *
 * This is the single source of truth for where scanning is happening.
 * The overlay draws this region, and the analyzer filters detections to this region.
 *
 * All coordinates are normalized (0.0 - 1.0) relative to the preview/image dimensions.
 */
data class ScanRoi(
    /** Center X position (0.0 = left edge, 1.0 = right edge) */
    val centerXNorm: Float,
    /** Center Y position (0.0 = top edge, 1.0 = bottom edge) */
    val centerYNorm: Float,
    /** Width as fraction of frame width (0.0 - 1.0) */
    val widthNorm: Float,
    /** Height as fraction of frame height (0.0 - 1.0) */
    val heightNorm: Float,
) {
    /** Left edge of ROI (clamped to 0.0) */
    val left: Float
        get() = (centerXNorm - widthNorm / 2f).coerceAtLeast(0f)

    /** Right edge of ROI (clamped to 1.0) */
    val right: Float
        get() = (centerXNorm + widthNorm / 2f).coerceAtMost(1f)

    /** Top edge of ROI (clamped to 0.0) */
    val top: Float
        get() = (centerYNorm - heightNorm / 2f).coerceAtLeast(0f)

    /** Bottom edge of ROI (clamped to 1.0) */
    val bottom: Float
        get() = (centerYNorm + heightNorm / 2f).coerceAtMost(1f)

    /** Actual width after clamping */
    val clampedWidth: Float
        get() = right - left

    /** Actual height after clamping */
    val clampedHeight: Float
        get() = bottom - top

    /** Area of the ROI as fraction of total frame (0.0 - 1.0) */
    val area: Float
        get() = clampedWidth * clampedHeight

    /**
     * Check if a point (in normalized coordinates) is inside the ROI.
     */
    fun containsPoint(
        x: Float,
        y: Float,
    ): Boolean {
        return x in left..right && y in top..bottom
    }

    /**
     * Check if a bounding box center is inside the ROI.
     *
     * @param boxCenterX Center X of the bounding box (normalized)
     * @param boxCenterY Center Y of the bounding box (normalized)
     */
    fun containsBoxCenter(
        boxCenterX: Float,
        boxCenterY: Float,
    ): Boolean {
        return containsPoint(boxCenterX, boxCenterY)
    }

    /**
     * Check if an ENTIRE bounding box is contained within the ROI.
     * All four corners of the box must be inside the ROI boundaries.
     *
     * @param boxLeft Left edge of the bounding box (normalized)
     * @param boxTop Top edge of the bounding box (normalized)
     * @param boxRight Right edge of the bounding box (normalized)
     * @param boxBottom Bottom edge of the bounding box (normalized)
     * @return True if the entire box is contained within the ROI
     */
    fun containsBox(
        boxLeft: Float,
        boxTop: Float,
        boxRight: Float,
        boxBottom: Float,
    ): Boolean {
        return boxLeft >= left && boxTop >= top && boxRight <= right && boxBottom <= bottom
    }

    /**
     * Calculate the distance from a point to the ROI center.
     * Returns value in range [0, ~0.707] where 0 = centered, ~0.707 = corner.
     */
    fun distanceFromCenter(
        x: Float,
        y: Float,
    ): Float {
        val dx = x - centerXNorm
        val dy = y - centerYNorm
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Calculate how well-centered a bounding box is within the ROI.
     * Returns 1.0 if perfectly centered, approaches 0.0 as distance increases.
     */
    fun centerScore(
        boxCenterX: Float,
        boxCenterY: Float,
    ): Float {
        val distance = distanceFromCenter(boxCenterX, boxCenterY)
        // Normalize by max theoretical distance (~0.707 for diagonal)
        val normalizedDistance = (distance / 0.707f).coerceIn(0f, 1f)
        return 1f - normalizedDistance
    }

    /**
     * Calculate intersection over union (IoU) with a bounding box.
     */
    fun iouWith(
        boxLeft: Float,
        boxTop: Float,
        boxRight: Float,
        boxBottom: Float,
    ): Float {
        val intersectLeft = maxOf(left, boxLeft)
        val intersectTop = maxOf(top, boxTop)
        val intersectRight = minOf(right, boxRight)
        val intersectBottom = minOf(bottom, boxBottom)

        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) {
            return 0f
        }

        val intersectionArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
        val boxArea = (boxRight - boxLeft) * (boxBottom - boxTop)
        val unionArea = area + boxArea - intersectionArea

        return if (unionArea > 0f) intersectionArea / unionArea else 0f
    }

    /**
     * Calculate what fraction of a bounding box overlaps with this ROI.
     */
    fun overlapRatio(
        boxLeft: Float,
        boxTop: Float,
        boxRight: Float,
        boxBottom: Float,
    ): Float {
        val intersectLeft = maxOf(left, boxLeft)
        val intersectTop = maxOf(top, boxTop)
        val intersectRight = minOf(right, boxRight)
        val intersectBottom = minOf(bottom, boxBottom)

        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) {
            return 0f
        }

        val intersectionArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
        val boxArea = (boxRight - boxLeft) * (boxBottom - boxTop)

        return if (boxArea > 0f) intersectionArea / boxArea else 0f
    }

    companion object {
        /** Default ROI - centered, 65% of frame width */
        val DEFAULT =
            ScanRoi(
                centerXNorm = 0.5f,
                centerYNorm = 0.5f,
                widthNorm = 0.65f,
                heightNorm = 0.65f,
            )

        /** Minimum allowed ROI size (45% of frame) */
        const val MIN_SIZE_NORM = 0.45f

        /** Maximum allowed ROI size (75% of frame) */
        const val MAX_SIZE_NORM = 0.75f

        /** Area threshold - boxes larger than this are "too close" */
        const val MAX_CLOSE_AREA = 0.35f

        /** Area threshold - boxes smaller than this are "too far" */
        const val MIN_FAR_AREA = 0.04f

        /**
         * Create a ROI centered in the frame with the specified size.
         */
        fun centered(sizeNorm: Float): ScanRoi {
            val clampedSize = sizeNorm.coerceIn(MIN_SIZE_NORM, MAX_SIZE_NORM)
            return ScanRoi(
                centerXNorm = 0.5f,
                centerYNorm = 0.5f,
                widthNorm = clampedSize,
                heightNorm = clampedSize,
            )
        }

        /**
         * Create a ROI based on aspect ratio of the preview.
         *
         * @param previewAspectRatio Width/height ratio of the preview
         * @param baseSize Base size for the shorter dimension
         */
        fun forAspectRatio(
            previewAspectRatio: Float,
            baseSize: Float = 0.65f,
        ): ScanRoi {
            val clampedBase = baseSize.coerceIn(MIN_SIZE_NORM, MAX_SIZE_NORM)
            return if (previewAspectRatio > 1f) {
                // Landscape: width > height, so height is shorter
                ScanRoi(
                    centerXNorm = 0.5f,
                    centerYNorm = 0.5f,
                    widthNorm = clampedBase * previewAspectRatio.coerceAtMost(1.5f),
                    heightNorm = clampedBase,
                )
            } else {
                // Portrait: height > width, so width is shorter
                ScanRoi(
                    centerXNorm = 0.5f,
                    centerYNorm = 0.5f,
                    widthNorm = clampedBase,
                    heightNorm = clampedBase / previewAspectRatio.coerceAtLeast(0.67f),
                )
            }
        }
    }
}

/**
 * Configuration for the scan ROI sizing behavior.
 */
data class ScanRoiConfig(
    /** Initial ROI size (fraction of frame) */
    val initialSize: Float = 0.65f,
    /** Minimum ROI size */
    val minSize: Float = ScanRoi.MIN_SIZE_NORM,
    /** Maximum ROI size */
    val maxSize: Float = ScanRoi.MAX_SIZE_NORM,
    /** Box area above which object is "too close" */
    val tooCloseAreaThreshold: Float = ScanRoi.MAX_CLOSE_AREA,
    /** Box area below which object is "too far" */
    val tooFarAreaThreshold: Float = ScanRoi.MIN_FAR_AREA,
    /** Size adjustment step when resizing */
    val sizeAdjustmentStep: Float = 0.02f,
    /** Animation duration for size changes (ms) */
    val animationDurationMs: Int = 200,
    /** PHASE 4: Category-specific configurations (optional, for future tuning) */
    val categoryConfigs: Map<ScanObjectCategory, CategoryRoiConfig> = emptyMap(),
)

/**
 * PHASE 4: Object category hints for ROI tuning.
 *
 * Internal category hints that allow category-specific ROI behavior.
 * Default behavior uses UNKNOWN which applies generic settings.
 */
enum class ScanObjectCategory {
    /** Mobile phone or tablet */
    PHONE,

    /** Toys, collectibles, small items */
    TOY,

    /** Documents, books, papers */
    DOCUMENT,

    /** Electronics (excluding phones) */
    ELECTRONICS,

    /** Furniture, large items */
    FURNITURE,

    /** Default/unknown - uses generic settings */
    UNKNOWN,
}

/**
 * PHASE 4: Category-specific ROI configuration.
 *
 * Allows different area thresholds and lock durations per category.
 * This enables future tuning without refactoring.
 */
data class CategoryRoiConfig(
    /** Category this config applies to */
    val category: ScanObjectCategory,
    /** Ideal area range (min) - bbox area should be >= this */
    val idealAreaMin: Float = ScanRoi.MIN_FAR_AREA,
    /** Ideal area range (max) - bbox area should be <= this */
    val idealAreaMax: Float = ScanRoi.MAX_CLOSE_AREA,
    /** Minimum stable time for lock (ms) - may vary by category */
    val minStableTimeForLockMs: Long = 400L,
    /** Optional: preferred initial ROI size for this category */
    val preferredRoiSize: Float? = null,
) {
    companion object {
        /** Default configuration for unknown/generic objects */
        val DEFAULT =
            CategoryRoiConfig(
                category = ScanObjectCategory.UNKNOWN,
                idealAreaMin = ScanRoi.MIN_FAR_AREA,
                idealAreaMax = ScanRoi.MAX_CLOSE_AREA,
                minStableTimeForLockMs = 400L,
            )

        /** Example: Phone-specific config (tighter area range, faster lock) */
        val PHONE =
            CategoryRoiConfig(
                category = ScanObjectCategory.PHONE,
                idealAreaMin = 0.08f,
// Phones should fill more of frame
                idealAreaMax = 0.25f,
// But not too close
                minStableTimeForLockMs = 300L,
// Faster lock for phones
            )

        /** Example: Document-specific config (larger area allowed) */
        val DOCUMENT =
            CategoryRoiConfig(
                category = ScanObjectCategory.DOCUMENT,
                idealAreaMin = 0.15f,
// Documents should fill frame
                idealAreaMax = 0.50f,
// Allow larger docs
                minStableTimeForLockMs = 500L,
// Slower lock for alignment
            )
    }
}
