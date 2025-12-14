package com.scanium.app.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Result of saving images to the Gallery.
 */
data class SaveResult(
    val successCount: Int,
    val failureCount: Int,
    val errors: List<String> = emptyList()
) {
    val totalCount: Int = successCount + failureCount
    val isSuccess: Boolean = failureCount == 0

    fun getStatusMessage(): String {
        return when {
            failureCount == 0 -> "Saved $successCount ${if (successCount == 1) "image" else "images"} to Gallery"
            successCount == 0 -> "Failed to save $failureCount ${if (failureCount == 1) "image" else "images"}"
            else -> "Saved $successCount, failed $failureCount"
        }
    }
}

/**
 * Utility for saving images to the device Gallery using MediaStore.
 *
 * Handles Android 10+ (RELATIVE_PATH) and earlier versions gracefully.
 * No permissions required for Android 10+ when saving to MediaStore.
 */
object MediaStoreSaver {
    private const val TAG = "MediaStoreSaver"
    private const val ALBUM_NAME = "Scanium"

    /**
     * Saves a list of bitmaps to the Gallery.
     *
     * @param context Application context
     * @param bitmaps List of bitmaps to save with their associated item IDs
     * @return SaveResult with success/failure counts and status message
     */
    suspend fun saveBitmapsToGallery(
        context: Context,
        bitmaps: List<Pair<String, Bitmap>>
    ): SaveResult = withContext(Dispatchers.IO) {
        var successCount = 0
        var failureCount = 0
        val errors = mutableListOf<String>()

        Log.i(TAG, "Starting save operation for ${bitmaps.size} images")

        bitmaps.forEachIndexed { index, (itemId, bitmap) ->
            try {
                saveSingleBitmap(context, bitmap, itemId, index)
                successCount++
                Log.d(TAG, "Successfully saved image $index for item $itemId")
            } catch (e: Exception) {
                failureCount++
                val errorMsg = "Failed to save image $index: ${e.message}"
                errors.add(errorMsg)
                Log.e(TAG, errorMsg, e)
            }
        }

        val result = SaveResult(successCount, failureCount, errors)
        Log.i(TAG, "Save operation completed: ${result.getStatusMessage()}")

        return@withContext result
    }

    /**
     * Saves a single bitmap to MediaStore.
     */
    private fun saveSingleBitmap(
        context: Context,
        bitmap: Bitmap,
        itemId: String,
        index: Int
    ) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "Scanium_${timestamp}_${itemId.take(8)}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())

            // For Android 10+ (API 29+), use RELATIVE_PATH
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$ALBUM_NAME")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IOException("Failed to create MediaStore entry for $displayName")

        try {
            resolver.openOutputStream(imageUri)?.use { outputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                    throw IOException("Failed to compress bitmap for $displayName")
                }
            } ?: throw IOException("Failed to open output stream for $displayName")

            // Mark the image as ready (not pending) for Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }

            Log.d(TAG, "Saved image to Gallery: $displayName at $imageUri")
        } catch (e: Exception) {
            // If we fail, try to delete the entry we created
            resolver.delete(imageUri, null, null)
            throw e
        }
    }
}
