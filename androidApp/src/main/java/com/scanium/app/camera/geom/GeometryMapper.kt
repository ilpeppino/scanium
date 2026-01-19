package com.scanium.app.camera.geom

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.scanium.app.camera.geom.GeometryMapper.uprightToSensor
import com.scanium.app.NormalizedRect
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Canonical Geometry Mapper for bbox↔snapshot correlation.
 *
 * This class enforces a single source of truth for coordinate transformations
 * throughout the Scanium detection pipeline. All bbox operations use the
 * "upright detector space" as the canonical coordinate system.
 *
 * ***REMOVED******REMOVED*** Coordinate Spaces
 *
 * 1. **Sensor Space**: Raw camera sensor coordinates (e.g., 1280×720 landscape)
 * 2. **Upright Space**: Post-rotation coordinates matching user's view (e.g., 720×1280 portrait)
 * 3. **Normalized Space**: Upright coordinates in [0,1] range
 * 4. **Preview Space**: Composable screen coordinates with scale+offset applied
 * 5. **Bitmap Space**: Actual bitmap pixel coordinates (may differ from sensor due to resolution)
 *
 * ***REMOVED******REMOVED*** ML Kit Coordinate Contract
 *
 * When using `InputImage.fromMediaImage(mediaImage, rotationDegrees)`:
 * - ML Kit applies rotation metadata internally
 * - Returned bboxes are in UPRIGHT pixel coordinates
 * - `InputImage.width/height` are the upright (post-rotation) dimensions
 *
 * ***REMOVED******REMOVED*** Usage
 *
 * ```kotlin
 * // 1. Get bbox from ML Kit (already in upright space)
 * val uprightBbox = detectedObject.boundingBox
 *
 * // 2. Normalize using upright dimensions
 * val normalizedBbox = GeometryMapper.normalizeToUpright(uprightBbox, inputImage.width, inputImage.height)
 *
 * // 3. For overlay: map to preview
 * val previewRect = GeometryMapper.uprightToPreview(normalizedBbox, previewWidth, previewHeight, transform)
 *
 * // 4. For snapshot crop: map to bitmap
 * val cropRect = GeometryMapper.uprightToBitmap(normalizedBbox, bitmap, rotationDegrees)
 * ```
 */
object GeometryMapper {
    private const val TAG = "GeometryMapper"

    /**
     * Aspect ratio tolerance for correlation validation.
     * Bbox AR and crop AR should be within 2% of each other.
     */
    const val ASPECT_RATIO_TOLERANCE = 0.02f

    // =========================================================================
    // Upright Space Normalization
    // =========================================================================

    /**
     * Normalizes a pixel bbox from upright space to [0,1] normalized space.
     *
     * @param bbox Bounding box in upright pixel coordinates
     * @param uprightWidth Width of upright coordinate space (InputImage.width)
     * @param uprightHeight Height of upright coordinate space (InputImage.height)
     * @return Normalized bbox in [0,1] range
     */
    fun normalizeToUpright(
        bbox: Rect,
        uprightWidth: Int,
        uprightHeight: Int,
    ): NormalizedRect {
        if (uprightWidth <= 0 || uprightHeight <= 0) {
            Log.w(TAG, "Invalid upright dimensions: ${uprightWidth}x$uprightHeight")
            return NormalizedRect(0f, 0f, 0f, 0f)
        }

        return NormalizedRect(
            left = bbox.left.toFloat() / uprightWidth,
            top = bbox.top.toFloat() / uprightHeight,
            right = bbox.right.toFloat() / uprightWidth,
            bottom = bbox.bottom.toFloat() / uprightHeight,
        ).clampToUnit()
    }

    /**
     * Normalizes a pixel bbox from upright space to [0,1] normalized space.
     */
    fun normalizeToUpright(
        bbox: RectF,
        uprightWidth: Int,
        uprightHeight: Int,
    ): NormalizedRect {
        if (uprightWidth <= 0 || uprightHeight <= 0) {
            Log.w(TAG, "Invalid upright dimensions: ${uprightWidth}x$uprightHeight")
            return NormalizedRect(0f, 0f, 0f, 0f)
        }

        return NormalizedRect(
            left = bbox.left / uprightWidth,
            top = bbox.top / uprightHeight,
            right = bbox.right / uprightWidth,
            bottom = bbox.bottom / uprightHeight,
        ).clampToUnit()
    }

    /**
     * Denormalizes a bbox to upright pixel coordinates.
     */
    fun denormalizeToUpright(
        normalizedBbox: NormalizedRect,
        uprightWidth: Int,
        uprightHeight: Int,
    ): RectF {
        val clamped = normalizedBbox.clampToUnit()
        return RectF(
            clamped.left * uprightWidth,
            clamped.top * uprightHeight,
            clamped.right * uprightWidth,
            clamped.bottom * uprightHeight,
        )
    }

    // =========================================================================
    // Sensor ↔ Upright Conversions
    // =========================================================================

    /**
     * Converts an upright bbox to sensor space for bitmap cropping.
     *
     * When the sensor bitmap hasn't been rotated yet, we need to convert the
     * upright bbox back to sensor coordinates for correct cropping.
     *
     * @param uprightBbox Bbox in upright pixel coordinates
     * @param uprightWidth Width of upright space (InputImage.width)
     * @param uprightHeight Height of upright space (InputImage.height)
     * @param rotationDegrees Rotation from sensor to upright (0, 90, 180, 270)
     * @return Bbox in sensor pixel coordinates
     */
    fun uprightToSensor(
        uprightBbox: Rect,
        uprightWidth: Int,
        uprightHeight: Int,
        rotationDegrees: Int,
    ): Rect =
        when (rotationDegrees) {
            0 -> {
                uprightBbox
            }

            // No rotation needed

            90 -> {
                // Sensor is landscape, upright is portrait
                // sensorX = uprightY
                // sensorY = uprightWidth - uprightRight
                val sensorWidth = uprightHeight
                val sensorHeight = uprightWidth
                Rect(
                    uprightBbox.top,
                    sensorHeight - uprightBbox.right,
                    uprightBbox.bottom,
                    sensorHeight - uprightBbox.left,
                )
            }

            180 -> {
                // Both landscape, but flipped
                Rect(
                    uprightWidth - uprightBbox.right,
                    uprightHeight - uprightBbox.bottom,
                    uprightWidth - uprightBbox.left,
                    uprightHeight - uprightBbox.top,
                )
            }

            270 -> {
                // Sensor is landscape, upright is portrait (rotated other way)
                val sensorWidth = uprightHeight
                val sensorHeight = uprightWidth
                Rect(
                    sensorWidth - uprightBbox.bottom,
                    uprightBbox.left,
                    sensorWidth - uprightBbox.top,
                    uprightBbox.right,
                )
            }

            else -> {
                Log.w(TAG, "Unexpected rotation: $rotationDegrees, treating as 0")
                uprightBbox
            }
        }

    /**
     * Converts a sensor bbox to upright space.
     * This is the inverse of [uprightToSensor].
     */
    fun sensorToUpright(
        sensorBbox: Rect,
        sensorWidth: Int,
        sensorHeight: Int,
        rotationDegrees: Int,
    ): Rect =
        when (rotationDegrees) {
            0 -> {
                sensorBbox
            }

            90 -> {
                // Rotate 90° clockwise: sensor→upright
                val uprightWidth = sensorHeight
                val uprightHeight = sensorWidth
                Rect(
                    uprightWidth - sensorBbox.bottom,
                    sensorBbox.left,
                    uprightWidth - sensorBbox.top,
                    sensorBbox.right,
                )
            }

            180 -> {
                Rect(
                    sensorWidth - sensorBbox.right,
                    sensorHeight - sensorBbox.bottom,
                    sensorWidth - sensorBbox.left,
                    sensorHeight - sensorBbox.top,
                )
            }

            270 -> {
                val uprightWidth = sensorHeight
                val uprightHeight = sensorWidth
                Rect(
                    sensorBbox.top,
                    uprightHeight - sensorBbox.right,
                    sensorBbox.bottom,
                    uprightHeight - sensorBbox.left,
                )
            }

            else -> {
                Log.w(TAG, "Unexpected rotation: $rotationDegrees, treating as 0")
                sensorBbox
            }
        }

    // =========================================================================
    // Bitmap Crop Operations
    // =========================================================================

    /**
     * Calculates the crop rect for a bbox in an upright-oriented bitmap.
     *
     * This is used when the bitmap has already been rotated to upright orientation
     * (e.g., via EXIF rotation handling).
     *
     * @param normalizedBbox Bbox in normalized upright space
     * @param bitmapWidth Width of the upright bitmap
     * @param bitmapHeight Height of the upright bitmap
     * @param padding Optional padding ratio (0.12 = 12% padding)
     * @return Crop rect in bitmap pixel coordinates
     */
    fun uprightToBitmapCrop(
        normalizedBbox: NormalizedRect,
        bitmapWidth: Int,
        bitmapHeight: Int,
        padding: Float = 0f,
    ): Rect {
        val clamped = normalizedBbox.clampToUnit()

        // Denormalize to bitmap pixels
        val left = (clamped.left * bitmapWidth).roundToInt()
        val top = (clamped.top * bitmapHeight).roundToInt()
        val right = (clamped.right * bitmapWidth).roundToInt()
        val bottom = (clamped.bottom * bitmapHeight).roundToInt()

        // Apply padding
        val width = right - left
        val height = bottom - top
        val padX = (width * padding).roundToInt()
        val padY = (height * padding).roundToInt()

        // Clamp to bitmap bounds
        return Rect(
            (left - padX).coerceAtLeast(0),
            (top - padY).coerceAtLeast(0),
            (right + padX).coerceAtMost(bitmapWidth),
            (bottom + padY).coerceAtMost(bitmapHeight),
        )
    }

    /**
     * Calculates the crop rect for a bbox in a sensor-oriented (unrotated) bitmap.
     *
     * This is used when the bitmap is still in sensor orientation and needs
     * coordinate transformation before cropping.
     *
     * @param normalizedBbox Bbox in normalized upright space
     * @param sensorBitmap Bitmap in sensor orientation
     * @param rotationDegrees Rotation from sensor to upright
     * @param padding Optional padding ratio
     * @return Crop rect in sensor bitmap coordinates
     */
    fun uprightToSensorBitmapCrop(
        normalizedBbox: NormalizedRect,
        sensorBitmap: Bitmap,
        rotationDegrees: Int,
        padding: Float = 0f,
    ): Rect {
        val sensorWidth = sensorBitmap.width
        val sensorHeight = sensorBitmap.height

        // Determine upright dimensions based on rotation
        val (uprightWidth, uprightHeight) =
            when (rotationDegrees) {
                90, 270 -> Pair(sensorHeight, sensorWidth)
                else -> Pair(sensorWidth, sensorHeight)
            }

        // Denormalize to upright pixel coordinates
        val clamped = normalizedBbox.clampToUnit()
        val uprightRect =
            Rect(
                (clamped.left * uprightWidth).roundToInt(),
                (clamped.top * uprightHeight).roundToInt(),
                (clamped.right * uprightWidth).roundToInt(),
                (clamped.bottom * uprightHeight).roundToInt(),
            )

        // Apply padding in upright space
        val width = uprightRect.width()
        val height = uprightRect.height()
        val padX = (width * padding).roundToInt()
        val padY = (height * padding).roundToInt()

        val paddedUpright =
            Rect(
                (uprightRect.left - padX).coerceAtLeast(0),
                (uprightRect.top - padY).coerceAtLeast(0),
                (uprightRect.right + padX).coerceAtMost(uprightWidth),
                (uprightRect.bottom + padY).coerceAtMost(uprightHeight),
            )

        // Convert to sensor coordinates
        return uprightToSensor(paddedUpright, uprightWidth, uprightHeight, rotationDegrees)
    }

    /**
     * Crops a sensor-oriented bitmap using an upright-space bbox and rotates to upright.
     *
     * This is the canonical method for creating thumbnails that match the bbox exactly.
     *
     * @param sensorBitmap Bitmap in sensor orientation
     * @param normalizedBbox Bbox in normalized upright space
     * @param rotationDegrees Rotation from sensor to upright
     * @param padding Optional padding ratio
     * @return Cropped and rotated bitmap in upright orientation, or null if crop fails
     */
    fun cropAndRotateToUpright(
        sensorBitmap: Bitmap,
        normalizedBbox: NormalizedRect,
        rotationDegrees: Int,
        padding: Float = 0f,
    ): Bitmap? {
        return try {
            // Get sensor-space crop rect
            val cropRect =
                uprightToSensorBitmapCrop(
                    normalizedBbox = normalizedBbox,
                    sensorBitmap = sensorBitmap,
                    rotationDegrees = rotationDegrees,
                    padding = padding,
                )

            // Validate crop rect
            if (cropRect.width() <= 0 || cropRect.height() <= 0) {
                Log.w(TAG, "Invalid crop rect: $cropRect")
                return null
            }

            // Crop from sensor bitmap
            val cropped =
                Bitmap.createBitmap(
                    sensorBitmap,
                    cropRect.left.coerceIn(0, sensorBitmap.width - 1),
                    cropRect.top.coerceIn(0, sensorBitmap.height - 1),
                    cropRect.width().coerceAtMost(sensorBitmap.width - cropRect.left),
                    cropRect.height().coerceAtMost(sensorBitmap.height - cropRect.top),
                )

            // Rotate to upright orientation
            if (rotationDegrees == 0) {
                cropped
            } else {
                val matrix =
                    Matrix().apply {
                        postRotate(rotationDegrees.toFloat())
                    }
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
                if (rotated != cropped) {
                    cropped.recycle()
                }
                rotated
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in cropAndRotateToUpright", e)
            null
        }
    }

    // =========================================================================
    // Aspect Ratio Validation
    // =========================================================================

    /**
     * Calculates the aspect ratio (width / height) of a normalized bbox.
     */
    fun aspectRatio(bbox: NormalizedRect): Float {
        val height = bbox.height
        return if (height > 0.0001f) bbox.width / height else 0f
    }

    /**
     * Calculates the aspect ratio (width / height) of a rect.
     */
    fun aspectRatio(rect: Rect): Float = if (rect.height() > 0) rect.width().toFloat() / rect.height() else 0f

    /**
     * Validates that two aspect ratios are within tolerance.
     */
    fun validateAspectRatio(
        bboxAR: Float,
        cropAR: Float,
        tolerance: Float = ASPECT_RATIO_TOLERANCE,
    ): Boolean {
        val diff = abs(bboxAR - cropAR)
        val maxAR = maxOf(bboxAR, cropAR, 0.001f)
        return diff / maxAR <= tolerance
    }

    // =========================================================================
    // Debug/Logging Utilities
    // =========================================================================

    /**
     * Data class for correlation debug info.
     */
    data class CorrelationDebugInfo(
        val normalizedBbox: NormalizedRect,
        val uprightBboxPx: RectF,
        val cropRectPx: Rect,
        val bboxAspectRatio: Float,
        val cropAspectRatio: Float,
        val aspectRatioMatch: Boolean,
        val rotationDegrees: Int,
        val uprightWidth: Int,
        val uprightHeight: Int,
        val bitmapWidth: Int,
        val bitmapHeight: Int,
    ) {
        fun toLogString(): String =
            buildString {
                append("[CORR] ")
                append("rot=$rotationDegrees, ")
                append("upright=${uprightWidth}x$uprightHeight, ")
                append("bitmap=${bitmapWidth}x$bitmapHeight, ")
                append("bboxAR=${"%.3f".format(bboxAspectRatio)}, ")
                append("cropAR=${"%.3f".format(cropAspectRatio)}, ")
                append("match=$aspectRatioMatch")
            }
    }

    /**
     * Generates debug info for bbox↔crop correlation validation.
     */
    fun generateCorrelationDebugInfo(
        normalizedBbox: NormalizedRect,
        rotationDegrees: Int,
        uprightWidth: Int,
        uprightHeight: Int,
        bitmapWidth: Int,
        bitmapHeight: Int,
        padding: Float = 0f,
    ): CorrelationDebugInfo {
        val uprightBboxPx = denormalizeToUpright(normalizedBbox, uprightWidth, uprightHeight)
        val cropRectPx = uprightToBitmapCrop(normalizedBbox, bitmapWidth, bitmapHeight, padding)

        val bboxAR = aspectRatio(normalizedBbox)
        val cropAR = aspectRatio(cropRectPx)
        val match = validateAspectRatio(bboxAR, cropAR)

        return CorrelationDebugInfo(
            normalizedBbox = normalizedBbox,
            uprightBboxPx = uprightBboxPx,
            cropRectPx = cropRectPx,
            bboxAspectRatio = bboxAR,
            cropAspectRatio = cropAR,
            aspectRatioMatch = match,
            rotationDegrees = rotationDegrees,
            uprightWidth = uprightWidth,
            uprightHeight = uprightHeight,
            bitmapWidth = bitmapWidth,
            bitmapHeight = bitmapHeight,
        )
    }
}
