package com.example.objecta.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.objecta.items.ScannedItem
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.tasks.await

/**
 * Client for ML Kit Object Detection and Tracking.
 *
 * Configures ML Kit to detect multiple objects with classification enabled.
 * Provides methods to process images and convert detection results to ScannedItems.
 */
class ObjectDetectorClient {

    // ML Kit object detector configured for multiple objects and classification
    private val objectDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE) // Optimized for video frames
            .enableMultipleObjects() // Detect multiple objects per frame
            .enableClassification() // Enable coarse classification
            .build()

        ObjectDetection.getClient(options)
    }

    /**
     * Processes an image and detects objects.
     *
     * @param image InputImage to process (from CameraX)
     * @param sourceBitmap Original bitmap for cropping thumbnails
     * @return List of ScannedItems with detected objects
     */
    suspend fun detectObjects(image: InputImage, sourceBitmap: Bitmap): List<ScannedItem> {
        return try {
            // Run detection
            val detectedObjects = objectDetector.process(image).await()

            // Convert each detected object to ScannedItem
            detectedObjects.mapNotNull { obj ->
                convertToScannedItem(obj, sourceBitmap)
            }
        } catch (e: Exception) {
            // Log error in production; for PoC, return empty list
            emptyList()
        }
    }

    /**
     * Converts a DetectedObject from ML Kit to a ScannedItem.
     */
    private fun convertToScannedItem(
        detectedObject: DetectedObject,
        sourceBitmap: Bitmap
    ): ScannedItem? {
        return try {
            // Extract tracking ID (null if not available)
            val trackingId = detectedObject.trackingId?.toString()
                ?: java.util.UUID.randomUUID().toString()

            // Get bounding box
            val boundingBox = detectedObject.boundingBox

            // Crop thumbnail from source bitmap
            val thumbnail = cropThumbnail(sourceBitmap, boundingBox)

            // Determine category from labels
            val category = extractCategory(detectedObject)

            // Calculate normalized bounding box area for pricing
            val boxArea = calculateNormalizedArea(boundingBox, sourceBitmap.width, sourceBitmap.height)

            // Generate price range
            val priceRange = PricingEngine.generatePriceRange(category, boxArea)

            ScannedItem(
                id = trackingId,
                thumbnail = thumbnail,
                category = category,
                priceRange = priceRange
            )
        } catch (e: Exception) {
            // If cropping or processing fails, skip this object
            null
        }
    }

    /**
     * Extracts the best category from ML Kit labels.
     * ML Kit provides a list of labels with confidence scores.
     */
    private fun extractCategory(detectedObject: DetectedObject): ItemCategory {
        // Get the label with highest confidence
        val bestLabel = detectedObject.labels.maxByOrNull { it.confidence }

        return if (bestLabel != null && bestLabel.confidence > 0.5f) {
            ItemCategory.fromMlKitLabel(bestLabel.text)
        } else {
            ItemCategory.UNKNOWN
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

            Bitmap.createBitmap(source, left, top, width, height)
        } catch (e: Exception) {
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
        objectDetector.close()
    }
}
