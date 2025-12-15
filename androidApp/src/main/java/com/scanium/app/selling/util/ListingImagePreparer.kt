package com.scanium.app.selling.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Utility for preparing high-quality images for eBay listings.
 *
 * Handles:
 * - Image quality optimization
 * - Resolution validation
 * - File size management
 * - Background thread processing
 * - Detailed logging for verification
 */
class ListingImagePreparer(private val context: Context) {

    companion object {
        private const val TAG = "ListingImagePreparer"

        // Image quality settings
        private const val MIN_WIDTH = 500
        private const val MIN_HEIGHT = 500
        private const val PREFERRED_WIDTH = 1600
        private const val PREFERRED_HEIGHT = 1600
        private const val JPEG_QUALITY = 85 // High quality, reasonable file size

        // File paths
        private const val LISTING_IMAGES_DIR = "listing_images"
    }

    /**
     * Result of image preparation operation.
     */
    sealed class PrepareResult {
        data class Success(
            val uri: Uri,
            val width: Int,
            val height: Int,
            val fileSizeBytes: Long,
            val quality: Int,
            val source: String
        ) : PrepareResult()

        data class Failure(val reason: String) : PrepareResult()
    }

    /**
     * Prepares a listing image from available sources.
     *
     * Priority order:
     * 1. fullImageUri (if available and valid)
     * 2. thumbnail bitmap (scaled up if needed)
     *
     * All processing happens on a background dispatcher.
     *
     * @param itemId Unique identifier for the scanned item
     * @param fullImageUri Optional URI to a full-resolution image
     * @param thumbnail Fallback bitmap (typically lower resolution)
     * @return PrepareResult with details or failure reason
     */
    suspend fun prepareListingImage(
        itemId: String,
        fullImageUri: Uri? = null,
        thumbnail: Bitmap? = null
    ): PrepareResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "╔═══════════════════════════════════════════════════════════════")
        Log.i(TAG, "║ PREPARING LISTING IMAGE: $itemId")
        Log.i(TAG, "║ fullImageUri: $fullImageUri")
        Log.i(TAG, "║ thumbnail: ${thumbnail?.width}x${thumbnail?.height}")

        // Try full image URI first
        if (fullImageUri != null) {
            try {
                val bitmap = loadBitmapFromUri(fullImageUri)
                if (bitmap != null) {
                    val result = saveBitmapAsListingImage(itemId, bitmap, "fullImageUri")
                    bitmap.recycle()
                    logResult(result)
                    return@withContext result
                }
                Log.w(TAG, "║ Failed to load bitmap from fullImageUri, falling back to thumbnail")
            } catch (e: Exception) {
                Log.e(TAG, "║ Error loading fullImageUri: ${e.message}")
            }
        }

        // Fall back to thumbnail
        if (thumbnail != null) {
            val result = if (thumbnail.width < MIN_WIDTH || thumbnail.height < MIN_HEIGHT) {
                // Scale up thumbnail to minimum dimensions
                Log.i(TAG, "║ Thumbnail too small (${thumbnail.width}x${thumbnail.height}), scaling up")
                val scaledBitmap = scaleUpBitmap(thumbnail)
                val saveResult = saveBitmapAsListingImage(itemId, scaledBitmap, "thumbnail_scaled")
                scaledBitmap.recycle()
                saveResult
            } else {
                saveBitmapAsListingImage(itemId, thumbnail, "thumbnail")
            }
            logResult(result)
            return@withContext result
        }

        val failure = PrepareResult.Failure("No valid image source available")
        Log.e(TAG, "║ FAILURE: ${(failure as PrepareResult.Failure).reason}")
        Log.i(TAG, "╚═══════════════════════════════════════════════════════════════")
        failure
    }

    /**
     * Loads a bitmap from a Uri (content:// or file://).
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from URI: ${e.message}")
            null
        }
    }

    /**
     * Scales up a bitmap to meet minimum dimensions.
     */
    private fun scaleUpBitmap(bitmap: Bitmap): Bitmap {
        val scaleWidth = PREFERRED_WIDTH.toFloat() / bitmap.width
        val scaleHeight = PREFERRED_HEIGHT.toFloat() / bitmap.height
        val scale = max(scaleWidth, scaleHeight)

        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()

        Log.i(TAG, "║ Scaling: ${bitmap.width}x${bitmap.height} → ${newWidth}x${newHeight} (scale=${String.format("%.2f", scale)})")

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Saves a bitmap as a JPEG file for listing purposes.
     */
    private fun saveBitmapAsListingImage(
        itemId: String,
        bitmap: Bitmap,
        source: String
    ): PrepareResult {
        val outputDir = File(context.cacheDir, LISTING_IMAGES_DIR)
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val outputFile = File(outputDir, "${itemId}_listing.jpg")

        return try {
            FileOutputStream(outputFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            }

            val uri = Uri.fromFile(outputFile)
            val fileSize = outputFile.length()

            PrepareResult.Success(
                uri = uri,
                width = bitmap.width,
                height = bitmap.height,
                fileSizeBytes = fileSize,
                quality = JPEG_QUALITY,
                source = source
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap: ${e.message}")
            PrepareResult.Failure("Failed to save image: ${e.message}")
        }
    }

    /**
     * Logs the result of image preparation for verification.
     */
    private fun logResult(result: PrepareResult) {
        when (result) {
            is PrepareResult.Success -> {
                val sizeKb = result.fileSizeBytes / 1024.0
                Log.i(TAG, "║ SUCCESS:")
                Log.i(TAG, "║   Source: ${result.source}")
                Log.i(TAG, "║   Resolution: ${result.width}x${result.height}")
                Log.i(TAG, "║   File size: ${String.format("%.2f", sizeKb)} KB")
                Log.i(TAG, "║   Quality: ${result.quality}")
                Log.i(TAG, "║   URI: ${result.uri}")
                Log.i(TAG, "╚═══════════════════════════════════════════════════════════════")
            }
            is PrepareResult.Failure -> {
                Log.e(TAG, "║ FAILURE: ${result.reason}")
                Log.i(TAG, "╚═══════════════════════════════════════════════════════════════")
            }
        }
    }

    /**
     * Cleans up old listing images to free space.
     * Call this periodically or after posting completes.
     */
    fun cleanupOldImages(maxAgeMs: Long = 24 * 60 * 60 * 1000L) {
        val outputDir = File(context.cacheDir, LISTING_IMAGES_DIR)
        if (!outputDir.exists()) return

        val now = System.currentTimeMillis()
        val files = outputDir.listFiles() ?: return

        var deletedCount = 0
        for (file in files) {
            if (now - file.lastModified() > maxAgeMs) {
                if (file.delete()) {
                    deletedCount++
                }
            }
        }

        if (deletedCount > 0) {
            Log.i(TAG, "Cleaned up $deletedCount old listing images")
        }
    }
}
