package com.scanium.app.items.photos

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.scanium.shared.core.models.items.ItemPhoto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles per-item photo deduplication using perceptual hashing (pHash).
 *
 * Unlike global deduplication (which prevents duplicate items from the same shot),
 * this prevents adding the same close-up photo twice to the SAME item.
 *
 * Uses a simplified average hash (aHash) algorithm for fast, fuzzy matching.
 * The algorithm:
 * 1. Resize image to 16x16
 * 2. Convert to grayscale
 * 3. Compute average brightness
 * 4. Generate 256-bit hash (each bit = pixel brighter than average)
 *
 * Two images are considered duplicates if their Hamming distance is below threshold.
 */
@Singleton
class PerItemDedupeHelper
    @Inject
    constructor() {
        companion object {
            private const val TAG = "PerItemDedupeHelper"
            private const val HASH_SIZE = 16 // 16x16 = 256 bits
            private const val MAX_HAMMING_DISTANCE = 25 // ~90% similarity threshold (25/256 ≈ 10% difference)
        }

        /**
         * Compute a perceptual hash for an image.
         *
         * @param bitmap The image to hash
         * @return Hex string representing the 256-bit hash
         */
        fun computeHash(bitmap: Bitmap): String =
            try {
                // Resize to small square
                val scaled = Bitmap.createScaledBitmap(bitmap, HASH_SIZE, HASH_SIZE, true)

                // Convert to grayscale and compute average
                val pixels = IntArray(HASH_SIZE * HASH_SIZE)
                scaled.getPixels(pixels, 0, HASH_SIZE, 0, 0, HASH_SIZE, HASH_SIZE)

                val grayscale =
                    pixels.map { pixel ->
                        // Luminance formula: 0.299R + 0.587G + 0.114B
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val b = Color.blue(pixel)
                        (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                    }

                val average = grayscale.average()

                // Generate hash: 1 if pixel >= average, 0 otherwise
                val hashBits = grayscale.map { if (it >= average) 1 else 0 }

                // Convert to hex string (256 bits = 64 hex chars)
                val hexChars = StringBuilder()
                for (i in hashBits.indices step 4) {
                    val nibble =
                        (hashBits.getOrElse(i) { 0 } shl 3) or
                            (hashBits.getOrElse(i + 1) { 0 } shl 2) or
                            (hashBits.getOrElse(i + 2) { 0 } shl 1) or
                            (hashBits.getOrElse(i + 3) { 0 })
                    hexChars.append(Integer.toHexString(nibble))
                }

                // Recycle scaled bitmap if it's different from original
                if (scaled != bitmap) {
                    scaled.recycle()
                }

                hexChars.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to compute hash: ${e.message}", e)
                // Return empty hash on failure (won't match anything)
                ""
            }

        /**
         * Check if a photo hash is a duplicate within an item's existing photos.
         *
         * @param hash The hash of the new photo
         * @param existingPhotos Existing photos for this item
         * @return true if the photo is a duplicate, false otherwise
         */
        fun isDuplicateForItem(
            hash: String,
            existingPhotos: List<ItemPhoto>,
        ): Boolean {
            if (hash.isEmpty()) return false

            for (photo in existingPhotos) {
                val existingHash = photo.photoHash ?: continue
                if (existingHash.isEmpty()) continue

                val distance = hammingDistance(hash, existingHash)
                if (distance <= MAX_HAMMING_DISTANCE) {
                    Log.d(TAG, "Found duplicate: distance=$distance (threshold=$MAX_HAMMING_DISTANCE)")
                    return true
                }
            }

            return false
        }

        /**
         * Compute Hamming distance between two hex hash strings.
         * Returns the number of differing bits.
         */
        private fun hammingDistance(
            hash1: String,
            hash2: String,
        ): Int {
            if (hash1.length != hash2.length) {
                // Different lengths, consider them different
                return Int.MAX_VALUE
            }

            var distance = 0
            for (i in hash1.indices) {
                val char1 = hash1[i].digitToIntOrNull(16) ?: 0
                val char2 = hash2[i].digitToIntOrNull(16) ?: 0
                // Count differing bits in this nibble
                distance += Integer.bitCount(char1 xor char2)
            }

            return distance
        }

        /**
         * Compute similarity percentage between two images.
         *
         * @param hash1 First image hash
         * @param hash2 Second image hash
         * @return Similarity as percentage (0.0 to 1.0)
         */
        fun computeSimilarity(
            hash1: String,
            hash2: String,
        ): Float {
            if (hash1.isEmpty() || hash2.isEmpty()) return 0f
            if (hash1.length != hash2.length) return 0f

            val maxBits = hash1.length * 4 // Each hex char = 4 bits
            val distance = hammingDistance(hash1, hash2)

            return if (distance == Int.MAX_VALUE) {
                0f
            } else {
                1f - (distance.toFloat() / maxBits)
            }
        }
    }
