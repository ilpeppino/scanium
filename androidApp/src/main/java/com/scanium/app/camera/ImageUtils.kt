package com.scanium.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Utility functions for image processing and thumbnail generation.
 */
object ImageUtils {
    private const val TAG = "ImageUtils"

    /**
     * Maximum dimension (width or height) for thumbnail bitmaps.
     * Keeps memory usage reasonable while maintaining acceptable UI quality.
     */
    private const val THUMBNAIL_MAX_DIMENSION = 800

    /**
     * Creates a memory-safe thumbnail from a high-resolution image URI.
     *
     * Uses inSampleSize to decode only the required resolution, avoiding
     * OutOfMemoryError on large images.
     *
     * @param context Application context
     * @param uri URI of the high-resolution image
     * @param maxDimension Maximum width/height for the thumbnail (default: 800px)
     * @return Scaled thumbnail bitmap, or null if loading fails
     */
    suspend fun createThumbnailFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = THUMBNAIL_MAX_DIMENSION
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver

            // First pass: Decode image bounds without loading the full bitmap
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            val imageWidth = options.outWidth
            val imageHeight = options.outHeight

            if (imageWidth <= 0 || imageHeight <= 0) {
                Log.e(TAG, "Invalid image dimensions: ${imageWidth}x${imageHeight}")
                return@withContext null
            }

            // Calculate inSampleSize to reduce memory footprint
            val maxOriginalDimension = maxOf(imageWidth, imageHeight)
            val sampleSize = if (maxOriginalDimension > maxDimension) {
                maxOriginalDimension / maxDimension
            } else {
                1
            }

            // Second pass: Decode the scaled-down bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inJustDecodeBounds = false
            }

            val bitmap = contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            }

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from URI: $uri")
                return@withContext null
            }

            // Final resize if needed (inSampleSize is a power of 2, so we may still be over target)
            val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val scale = min(
                    maxDimension.toFloat() / bitmap.width,
                    maxDimension.toFloat() / bitmap.height
                )
                val targetWidth = (bitmap.width * scale).toInt()
                val targetHeight = (bitmap.height * scale).toInt()

                val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                if (scaled != bitmap) {
                    bitmap.recycle() // Recycle the intermediate bitmap
                }
                scaled
            } else {
                bitmap
            }

            val rotationDegrees = readExifRotation(context, uri)
            val finalBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(
                    scaledBitmap,
                    0,
                    0,
                    scaledBitmap.width,
                    scaledBitmap.height,
                    matrix,
                    true
                ).also {
                    if (it != scaledBitmap) {
                        scaledBitmap.recycle()
                    }
                }
            } else {
                scaledBitmap
            }

            Log.d(
                TAG,
                "Created thumbnail: ${finalBitmap.width}x${finalBitmap.height} from ${imageWidth}x${imageHeight} (sample: $sampleSize, rotation: $rotationDegrees)"
            )
            finalBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create thumbnail from URI: $uri", e)
            null
        }
    }

    private fun readExifRotation(context: Context, uri: Uri): Int {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrElse { exception ->
            Log.w(TAG, "Unable to read EXIF rotation for $uri", exception)
            0
        }
    }

    /**
     * Calculates a sharpness score for the given bitmap using Laplacian variance.
     * Higher score = sharper image.
     *
     * Uses a simplified 3x3 Laplacian kernel on grayscale pixels.
     * To ensure performance, this operates on a downscaled version if the input is large.
     */
    fun calculateSharpness(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        
        // Operate on a smaller bitmap for performance if needed
        val targetSize = 256
        val (processBitmap, w, h) = if (width > targetSize || height > targetSize) {
            val scale = min(targetSize.toFloat() / width, targetSize.toFloat() / height)
            val sw = (width * scale).toInt()
            val sh = (height * scale).toInt()
            Triple(Bitmap.createScaledBitmap(bitmap, sw, sh, true), sw, sh)
        } else {
            Triple(bitmap, width, height)
        }

        val pixels = IntArray(w * h)
        processBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        
        if (processBitmap != bitmap) {
            processBitmap.recycle()
        }

        // Convert to grayscale
        val grayPixels = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // Luminance formula: 0.299R + 0.587G + 0.114B
            grayPixels[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        // Apply Laplacian kernel
        // Kernel:
        // 0  1  0
        // 1 -4  1
        // 0  1  0
        
        val laplacian = IntArray(w * h)
        var sum = 0L
        var count = 0
        
        // Skip borders
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val valTop = grayPixels[idx - w]
                val valBottom = grayPixels[idx + w]
                val valLeft = grayPixels[idx - 1]
                val valRight = grayPixels[idx + 1]
                val valCenter = grayPixels[idx]
                
                val lap = valTop + valBottom + valLeft + valRight - (4 * valCenter)
                laplacian[idx] = lap
                sum += lap
                count++
            }
        }
        
        if (count == 0) return 0.0
        
        val mean = sum.toDouble() / count
        var sqDiffSum = 0.0
        
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val diff = laplacian[idx] - mean
                sqDiffSum += diff * diff
            }
        }
        
        return sqDiffSum / count
    }
}
