package com.scanium.app.debug

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.scanium.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug utility for verifying which images are used for classification.
 *
 * This class provides dev-only instrumentation to:
 * - Log image dimensions, byte length, and SHA-256 hash
 * - Optionally save classifier input images to filesDir/debug for visual verification
 *
 * IMPORTANT: This class is only active in dev builds.
 */
@Singleton
class ImageClassifierDebugger
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "ImageClassifierDebug"
            private const val DEBUG_DIR = "debug/classifier_inputs"
            private const val JPEG_QUALITY = 85

            /**
             * Enable/disable saving debug images to disk.
             * Set to true to save classifier input images for visual verification.
             */
            var SAVE_DEBUG_IMAGES = false
        }

        /**
         * Log details about an image used for classification and optionally save it.
         *
         * @param bitmap The image being used for classification
         * @param source Description of the image source (e.g., "ML Kit input", "Cloud Vision - thumbnail")
         * @param itemId Optional item ID to include in logs and filename
         * @param originPath Optional original file path or URI
         */
        suspend fun logClassifierInput(
            bitmap: Bitmap,
            source: String,
            itemId: String? = null,
            originPath: String? = null,
        ) {
            // Only run in dev builds
            if (!BuildConfig.DEBUG && BuildConfig.FLAVOR != "dev") {
                return
            }

            withContext(Dispatchers.IO) {
                try {
                    // Convert bitmap to JPEG bytes
                    val jpegBytes = bitmap.toJpegBytes()

                    // Calculate SHA-256 hash
                    val hash = calculateSHA256(jpegBytes)

                    // Log details
                    val itemPrefix = itemId?.let { "[$it] " } ?: ""
                    val pathSuffix = originPath?.let { " (origin: $it)" } ?: ""
                    Log.i(
                        TAG,
                        "${itemPrefix}$source: ${bitmap.width}x${bitmap.height}, " +
                            "${jpegBytes.size} bytes, SHA-256: $hash$pathSuffix",
                    )

                    // Save debug image if enabled
                    if (SAVE_DEBUG_IMAGES) {
                        val savedPath = saveDebugImage(bitmap, source, itemId, hash)
                        Log.i(TAG, "${itemPrefix}Debug image saved: $savedPath")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error logging classifier input", e)
                }
            }
        }

        /**
         * Log details about a thumbnail used for display in the item list.
         *
         * @param bitmap The thumbnail bitmap
         * @param itemId Item ID
         * @param source Description (e.g., "List thumbnail", "Generated thumbnail")
         */
        suspend fun logThumbnail(
            bitmap: Bitmap,
            itemId: String,
            source: String = "List thumbnail",
        ) {
            // Only run in dev builds
            if (!BuildConfig.DEBUG && BuildConfig.FLAVOR != "dev") {
                return
            }

            withContext(Dispatchers.IO) {
                try {
                    val jpegBytes = bitmap.toJpegBytes()
                    val hash = calculateSHA256(jpegBytes)

                    Log.i(
                        TAG,
                        "[$itemId] $source: ${bitmap.width}x${bitmap.height}, " +
                            "${jpegBytes.size} bytes, SHA-256: $hash",
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error logging thumbnail", e)
                }
            }
        }

        /**
         * Save a debug image to filesDir/debug/classifier_inputs.
         *
         * @return Path to the saved file
         */
        private fun saveDebugImage(
            bitmap: Bitmap,
            source: String,
            itemId: String?,
            hash: String,
        ): String {
            val debugDir = File(context.filesDir, DEBUG_DIR)
            debugDir.mkdirs()

            // Create filename with timestamp and hash prefix
            val timestamp = System.currentTimeMillis()
            val itemPrefix = itemId?.let { "${it}_" } ?: ""
            val sourceSafe = source.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val hashPrefix = hash.take(8)
            val filename = "${itemPrefix}${sourceSafe}_${timestamp}_$hashPrefix.jpg"

            val file = File(debugDir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }

            return file.absolutePath
        }

        /**
         * Clear all saved debug images.
         */
        fun clearDebugImages() {
            if (!BuildConfig.DEBUG && BuildConfig.FLAVOR != "dev") {
                return
            }

            try {
                val debugDir = File(context.filesDir, DEBUG_DIR)
                if (debugDir.exists()) {
                    debugDir.listFiles()?.forEach { it.delete() }
                    Log.i(TAG, "Cleared debug images from $debugDir")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing debug images", e)
            }
        }

        /**
         * Get the path to the debug images directory.
         */
        fun getDebugImagesDir(): String {
            return File(context.filesDir, DEBUG_DIR).absolutePath
        }

        /**
         * Calculate SHA-256 hash of byte array.
         */
        private fun calculateSHA256(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes)
            return hash.joinToString("") { "%02x".format(it) }
        }

        /**
         * Convert bitmap to JPEG bytes.
         */
        private fun Bitmap.toJpegBytes(): ByteArray {
            val stream = ByteArrayOutputStream()
            compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            return stream.toByteArray()
        }
    }
