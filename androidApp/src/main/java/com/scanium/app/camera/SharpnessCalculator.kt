package com.scanium.app.camera

import android.graphics.Bitmap

/**
 * Lightweight sharpness calculator for camera frames.
 *
 * Uses a Laplacian-like gradient approximation to estimate image sharpness.
 * Higher values indicate sharper (more in-focus) images.
 *
 * Implementation notes:
 * - Samples a central region for efficiency (default 128x128)
 * - Uses grayscale approximation for speed
 * - No heavy dependencies (pure Kotlin/Android APIs)
 */
object SharpnessCalculator {
    // Default sample region size (in pixels)
    private const val DEFAULT_SAMPLE_SIZE = 128

    // Minimum variance threshold to consider an image "sharp enough"
    // Empirically tuned - adjust based on testing
    const val DEFAULT_MIN_SHARPNESS = 100f

    /**
     * Calculate sharpness score for a bitmap.
     *
     * @param bitmap Source bitmap (any size)
     * @param sampleSize Size of central crop to analyze (default 128x128)
     * @return Sharpness score (higher = sharper). Typical range: 0-500+
     */
    fun calculateSharpness(
        bitmap: Bitmap,
        sampleSize: Int = DEFAULT_SAMPLE_SIZE,
    ): Float {
        if (bitmap.isRecycled || bitmap.width < 4 || bitmap.height < 4) {
            return 0f
        }

        // Calculate the central crop region
        val cropWidth = minOf(sampleSize, bitmap.width)
        val cropHeight = minOf(sampleSize, bitmap.height)
        val startX = (bitmap.width - cropWidth) / 2
        val startY = (bitmap.height - cropHeight) / 2

        // Compute Laplacian variance over the central region
        return computeLaplacianVariance(bitmap, startX, startY, cropWidth, cropHeight)
    }

    /**
     * Check if a frame is considered blurry.
     *
     * @param bitmap Source bitmap
     * @param minSharpness Minimum sharpness threshold
     * @return true if the frame is blurry (below threshold)
     */
    fun isBlurry(
        bitmap: Bitmap,
        minSharpness: Float = DEFAULT_MIN_SHARPNESS,
    ): Boolean = calculateSharpness(bitmap) < minSharpness

    /**
     * Compute the variance of Laplacian approximation.
     *
     * Uses a simplified 3x3 Laplacian kernel approximation:
     *   L(x,y) = 4*I(x,y) - I(x-1,y) - I(x+1,y) - I(x,y-1) - I(x,y+1)
     *
     * The variance of L values indicates edge strength = sharpness.
     */
    private fun computeLaplacianVariance(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
    ): Float {
        if (width < 3 || height < 3) return 0f

        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        // Iterate over interior pixels (skip 1px border for kernel)
        for (y in (startY + 1) until (startY + height - 1)) {
            for (x in (startX + 1) until (startX + width - 1)) {
                // Get grayscale values (fast approximation using green channel)
                val center = getGray(bitmap, x, y)
                val left = getGray(bitmap, x - 1, y)
                val right = getGray(bitmap, x + 1, y)
                val top = getGray(bitmap, x, y - 1)
                val bottom = getGray(bitmap, x, y + 1)

                // Laplacian approximation
                val laplacian = 4 * center - left - right - top - bottom

                sum += laplacian
                sumSq += laplacian.toDouble() * laplacian.toDouble()
                count++
            }
        }

        if (count == 0) return 0f

        // Variance = E[X^2] - E[X]^2
        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)

        return variance.toFloat().coerceAtLeast(0f)
    }

    /**
     * Extract grayscale value from a pixel.
     *
     * Uses green channel as a fast approximation for luminance
     * (green contributes ~60% to perceived brightness).
     */
    private fun getGray(
        bitmap: Bitmap,
        x: Int,
        y: Int,
    ): Int {
        val pixel = bitmap.getPixel(x, y)
        // Extract green channel (fast luminance approximation)
        return (pixel shr 8) and 0xFF
    }

    /**
     * Calculate sharpness from raw YUV byte array.
     *
     * More efficient for ImageProxy analysis since it avoids bitmap conversion.
     *
     * @param yPlane Y (luma) plane data
     * @param width Image width
     * @param height Image height
     * @param rowStride Row stride for the Y plane
     * @param sampleSize Size of central crop to analyze
     * @return Sharpness score
     */
    fun calculateSharpnessFromYuv(
        yPlane: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        sampleSize: Int = DEFAULT_SAMPLE_SIZE,
    ): Float {
        if (width < 4 || height < 4) return 0f

        // Calculate the central crop region
        val cropWidth = minOf(sampleSize, width)
        val cropHeight = minOf(sampleSize, height)
        val startX = (width - cropWidth) / 2
        val startY = (height - cropHeight) / 2

        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        // Iterate over interior pixels
        for (y in (startY + 1) until (startY + cropHeight - 1)) {
            for (x in (startX + 1) until (startX + cropWidth - 1)) {
                // Get grayscale values from Y plane
                val center = getYuv(yPlane, x, y, rowStride)
                val left = getYuv(yPlane, x - 1, y, rowStride)
                val right = getYuv(yPlane, x + 1, y, rowStride)
                val top = getYuv(yPlane, x, y - 1, rowStride)
                val bottom = getYuv(yPlane, x, y + 1, rowStride)

                // Laplacian approximation
                val laplacian = 4 * center - left - right - top - bottom

                sum += laplacian
                sumSq += laplacian.toDouble() * laplacian.toDouble()
                count++
            }
        }

        if (count == 0) return 0f

        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)

        return variance.toFloat().coerceAtLeast(0f)
    }

    /**
     * Get Y value from YUV plane.
     */
    private fun getYuv(
        yPlane: ByteArray,
        x: Int,
        y: Int,
        rowStride: Int,
    ): Int {
        val index = y * rowStride + x
        return if (index >= 0 && index < yPlane.size) {
            yPlane[index].toInt() and 0xFF
        } else {
            0
        }
    }
}
