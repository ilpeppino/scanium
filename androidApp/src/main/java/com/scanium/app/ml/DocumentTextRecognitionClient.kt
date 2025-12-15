package com.scanium.app.ml

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.scanium.app.items.ScannedItem
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.scanium.app.platform.toImageRefJpeg
import kotlinx.coroutines.tasks.await

/**
 * Client for ML Kit Text Recognition.
 *
 * Configures ML Kit to recognize text in documents and images.
 * Provides methods to process images and convert text recognition results to ScannedItems.
 */
class DocumentTextRecognitionClient {

    companion object {
        private const val TAG = "DocumentTextRecognitionClient"
        private const val MIN_TEXT_LENGTH = 3 // Minimum text length to consider valid
        private const val MAX_TEXT_LENGTH = 10_000 // Maximum text length (10KB) - SEC-006
    }

    // ML Kit text recognizer configured for Latin script
    private val recognizer: TextRecognizer by lazy {
        Log.d(TAG, "Creating text recognizer with Latin script")
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Processes an image and recognizes text.
     *
     * @param image InputImage to process (from CameraX)
     * @param sourceBitmap Original bitmap for creating thumbnails
     * @return List of ScannedItems with recognized text (typically one item per document)
     */
    suspend fun recognizeText(
        image: InputImage,
        sourceBitmap: Bitmap?
    ): List<ScannedItem> {
        return try {
            Log.d(TAG, "Starting text recognition on image ${image.width}x${image.height}, rotation=${image.rotationDegrees}")

            // Run text recognition
            val visionText = recognizer.process(image).await()

            val rawText = visionText.text
            Log.d(TAG, "Recognized text length: ${rawText.length} characters")

            if (rawText.isBlank() || rawText.length < MIN_TEXT_LENGTH) {
                Log.d(TAG, "No meaningful text detected")
                return emptyList()
            }

            // SEC-006: Limit text length to 10KB for memory/UI performance
            val fullText = if (rawText.length > MAX_TEXT_LENGTH) {
                Log.w(TAG, "Text exceeds maximum length (${rawText.length} > $MAX_TEXT_LENGTH), truncating")
                rawText.take(MAX_TEXT_LENGTH) + "..."
            } else {
                rawText
            }

            Log.d(TAG, "Recognized text preview: ${fullText.take(100)}...")
            Log.d(TAG, "Text blocks: ${visionText.textBlocks.size}")

            // Get bounding box of the entire text region (or use whole image if not available)
            val boundingBox = calculateOverallBoundingBox(visionText.textBlocks.mapNotNull { it.boundingBox })
                ?: Rect(0, 0, image.width, image.height)

            // Create thumbnail from source bitmap
            val thumbnail = sourceBitmap?.let {
                if (boundingBox.width() > 0 && boundingBox.height() > 0) {
                    cropThumbnail(it, boundingBox)
                } else {
                    // Use the whole image as thumbnail
                    it
                }
            }

            // Generate unique ID from text hash
            val id = "document_${fullText.hashCode()}_${System.currentTimeMillis()}"

            // Calculate normalized area for pricing
            val boxArea = calculateNormalizedArea(
                box = boundingBox,
                imageWidth = sourceBitmap?.width ?: image.width,
                imageHeight = sourceBitmap?.height ?: image.height
            )

            // Generate price range (documents have symbolic pricing)
            val priceRange = PricingEngine.generatePriceRange(ItemCategory.DOCUMENT, boxArea)

            val thumbnailRef = thumbnail?.toImageRefJpeg(quality = 85)
            val item = ScannedItem(
                id = id,
                thumbnail = thumbnailRef,
                thumbnailRef = thumbnailRef,
                category = ItemCategory.DOCUMENT,
                priceRange = priceRange,
                recognizedText = fullText,
                boundingBox = boundingBox?.toNormalizedRect(
                    frameWidth = sourceBitmap?.width ?: image.width,
                    frameHeight = sourceBitmap?.height ?: image.height
                )
            )

            Log.d(TAG, "Created document item with ${fullText.length} characters")
            listOf(item)
        } catch (e: Exception) {
            Log.e(TAG, "Error recognizing text", e)
            emptyList()
        }
    }

    /**
     * Calculates the overall bounding box that encompasses all text blocks.
     */
    private fun calculateOverallBoundingBox(boxes: List<Rect>): Rect? {
        if (boxes.isEmpty()) return null

        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE

        boxes.forEach { box ->
            left = minOf(left, box.left)
            top = minOf(top, box.top)
            right = maxOf(right, box.right)
            bottom = maxOf(bottom, box.bottom)
        }

        return if (left < right && top < bottom) {
            Rect(left, top, right, bottom)
        } else {
            null
        }
    }

    /**
     * Crops a thumbnail from the source bitmap using the bounding box.
     */
    private fun cropThumbnail(source: Bitmap, boundingBox: Rect): Bitmap? {
        return try {
            // Ensure bounding box is within bitmap bounds
            val left = boundingBox.left.coerceIn(0, source.width - 1)
            val top = boundingBox.top.coerceIn(0, source.height - 1)
            val width = (boundingBox.width()).coerceIn(1, source.width - left)
            val height = (boundingBox.height()).coerceIn(1, source.height - top)

            if (width > 0 && height > 0) {
                // CRITICAL: Limit thumbnail size to save memory (max 200x200)
                val maxDimension = 200
                val scale = minOf(1.0f, maxDimension.toFloat() / maxOf(width, height))
                val thumbnailWidth = (width * scale).toInt().coerceAtLeast(1)
                val thumbnailHeight = (height * scale).toInt().coerceAtLeast(1)

                // Create small thumbnail with independent pixel data
                val thumbnail = Bitmap.createBitmap(thumbnailWidth, thumbnailHeight, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(thumbnail)
                val srcRect = android.graphics.Rect(left, top, left + width, top + height)
                val dstRect = android.graphics.Rect(0, 0, thumbnailWidth, thumbnailHeight)
                canvas.drawBitmap(source, srcRect, dstRect, null)
                thumbnail
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to crop thumbnail", e)
            null
        }
    }

    /**
     * Calculates normalized bounding box area (0.0 to 1.0).
     */
    private fun calculateNormalizedArea(box: Rect, imageWidth: Int, imageHeight: Int): Float {
        val boxArea = box.width() * box.height()
        val totalArea = imageWidth * imageHeight
        return if (totalArea > 0) {
            (boxArea.toFloat() / totalArea).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    /**
     * Cleanup resources when done.
     */
    fun close() {
        try {
            recognizer.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing recognizer", e)
        }
    }
}
