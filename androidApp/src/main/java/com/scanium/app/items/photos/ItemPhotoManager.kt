package com.scanium.app.items.photos

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.scanium.shared.core.models.items.ItemPhoto
import com.scanium.shared.core.models.items.PhotoType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages photo storage for items.
 *
 * Handles:
 * - Saving high-res photos to disk
 * - Per-item deduplication via perceptual hashing
 * - Photo cleanup when items are deleted
 */
@Singleton
class ItemPhotoManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dedupeHelper: PerItemDedupeHelper,
) {
    companion object {
        private const val TAG = "ItemPhotoManager"
        private const val PHOTOS_DIR = "item_photos"
        private const val JPEG_QUALITY = 90
    }

    private val photosBaseDir: File by lazy {
        File(context.filesDir, PHOTOS_DIR).also { it.mkdirs() }
    }

    /**
     * Add a photo to an item with per-item deduplication.
     *
     * @param itemId The item to add the photo to
     * @param bitmap The photo bitmap
     * @param existingPhotos Existing photos for this item (for dedup check)
     * @param photoType Type of photo (PRIMARY, CLOSEUP, FULL_SHOT)
     * @return The saved ItemPhoto, or null if the photo is a duplicate
     */
    suspend fun addPhotoToItem(
        itemId: String,
        bitmap: Bitmap,
        existingPhotos: List<ItemPhoto>,
        photoType: PhotoType = PhotoType.CLOSEUP,
    ): ItemPhoto? = withContext(Dispatchers.IO) {
        // Compute perceptual hash for deduplication
        val hash = dedupeHelper.computeHash(bitmap)

        // Check for duplicates within this item's photos
        if (dedupeHelper.isDuplicateForItem(hash, existingPhotos)) {
            Log.d(TAG, "Photo is duplicate for item $itemId, skipping")
            return@withContext null
        }

        // Generate unique photo ID
        val photoId = UUID.randomUUID().toString()

        // Save to disk
        val photoFile = savePhotoToFile(itemId, photoId, bitmap)
        if (photoFile == null) {
            Log.e(TAG, "Failed to save photo for item $itemId")
            return@withContext null
        }

        Log.d(TAG, "Saved photo $photoId for item $itemId at ${photoFile.absolutePath}")

        ItemPhoto(
            id = photoId,
            uri = photoFile.absolutePath,
            mimeType = "image/jpeg",
            width = bitmap.width,
            height = bitmap.height,
            photoHash = hash,
            photoType = photoType,
        )
    }

    /**
     * Save a bitmap to the item's photo directory.
     */
    private fun savePhotoToFile(
        itemId: String,
        photoId: String,
        bitmap: Bitmap,
    ): File? {
        return try {
            val itemDir = File(photosBaseDir, itemId).also { it.mkdirs() }
            val photoFile = File(itemDir, "$photoId.jpg")

            FileOutputStream(photoFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }

            photoFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save photo: ${e.message}", e)
            null
        }
    }

    /**
     * Delete all photos for an item.
     */
    suspend fun deletePhotosForItem(itemId: String) = withContext(Dispatchers.IO) {
        try {
            val itemDir = File(photosBaseDir, itemId)
            if (itemDir.exists()) {
                itemDir.deleteRecursively()
                Log.d(TAG, "Deleted photos directory for item $itemId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete photos for item $itemId: ${e.message}", e)
        }
    }

    /**
     * Delete a specific photo.
     */
    suspend fun deletePhoto(itemId: String, photoId: String) = withContext(Dispatchers.IO) {
        try {
            val photoFile = File(File(photosBaseDir, itemId), "$photoId.jpg")
            if (photoFile.exists()) {
                photoFile.delete()
                Log.d(TAG, "Deleted photo $photoId for item $itemId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete photo $photoId: ${e.message}", e)
        }
    }

    /**
     * Get the file for a photo.
     */
    fun getPhotoFile(itemId: String, photoId: String): File? {
        val photoFile = File(File(photosBaseDir, itemId), "$photoId.jpg")
        return if (photoFile.exists()) photoFile else null
    }

    /**
     * Clean up orphaned photo directories (items that no longer exist).
     *
     * @param validItemIds Set of item IDs that currently exist
     */
    suspend fun cleanupOrphanedPhotos(validItemIds: Set<String>) = withContext(Dispatchers.IO) {
        try {
            val itemDirs = photosBaseDir.listFiles() ?: return@withContext
            var deletedCount = 0

            for (dir in itemDirs) {
                if (dir.isDirectory && dir.name !in validItemIds) {
                    dir.deleteRecursively()
                    deletedCount++
                }
            }

            if (deletedCount > 0) {
                Log.i(TAG, "Cleaned up $deletedCount orphaned photo directories")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup orphaned photos: ${e.message}", e)
        }
    }

    /**
     * Get total storage used by item photos.
     */
    suspend fun getTotalStorageBytes(): Long = withContext(Dispatchers.IO) {
        try {
            photosBaseDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate storage: ${e.message}", e)
            0L
        }
    }
}
