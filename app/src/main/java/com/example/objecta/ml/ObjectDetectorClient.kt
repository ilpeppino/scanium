package com.example.objecta.ml

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
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

    companion object {
        private const val TAG = "ObjectDetectorClient"
        private const val CONFIDENCE_THRESHOLD = 0.3f // Lowered from 0.5 for better detection
    }

    // ML Kit object detector configured for single image mode (more accurate)
    private val singleImageDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE) // Better for tap capture
            .enableMultipleObjects() // Detect multiple objects per frame
            .enableClassification() // Enable coarse classification
            .build()

        Log.d(TAG, "Creating SINGLE_IMAGE_MODE detector")
        ObjectDetection.getClient(options)
    }

    // ML Kit object detector for streaming (scanning mode)
    private val streamDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE) // Optimized for video frames
            .enableMultipleObjects() // Detect multiple objects per frame
            .enableClassification() // Enable coarse classification
            .build()

        Log.d(TAG, "Creating STREAM_MODE detector")
        ObjectDetection.getClient(options)
    }

    /**
     * Processes an image and detects objects.
     *
     * @param image InputImage to process (from CameraX)
     * @param sourceBitmap Original bitmap for cropping thumbnails
     * @param useStreamMode If true, uses STREAM_MODE detector; if false, uses SINGLE_IMAGE_MODE
     * @return List of ScannedItems with detected objects
     */
    suspend fun detectObjects(
        image: InputImage,
        sourceBitmap: Bitmap?,
        useStreamMode: Boolean = false
    ): List<ScannedItem> {
        return try {
            val mode = if (useStreamMode) "STREAM" else "SINGLE_IMAGE"
            Log.d(TAG, "Starting object detection ($mode) on image ${image.width}x${image.height}, rotation=${image.rotationDegrees}")

            // Choose the appropriate detector
            val detector = if (useStreamMode) streamDetector else singleImageDetector

            // Run detection
            val detectedObjects = detector.process(image).await()

            Log.d(TAG, "Detected ${detectedObjects.size} objects")
            detectedObjects.forEachIndexed { index, obj ->
                val labels = obj.labels.joinToString { "${it.text}:${it.confidence}" }
                Log.d(TAG, "Object $index: labels=[$labels], box=${obj.boundingBox}")
            }

            // Convert each detected object to ScannedItem
            val items = detectedObjects.mapNotNull { obj ->
                convertToScannedItem(
                    detectedObject = obj,
                    sourceBitmap = sourceBitmap,
                    fallbackWidth = image.width,
                    fallbackHeight = image.height
                )
            }

            Log.d(TAG, "Converted to ${items.size} scanned items")
            items
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting objects", e)
            emptyList()
        }
    }

    /**
     * Processes an image and returns raw detection data (for multi-frame pipeline).
     *
     * @param image InputImage to process (from CameraX)
     * @param sourceBitmap Original bitmap for cropping thumbnails
     * @param useStreamMode If true, uses STREAM_MODE detector; if false, uses SINGLE_IMAGE_MODE
     * @return List of RawDetection objects with all detection metadata
     */
    suspend fun detectObjectsRaw(
        image: InputImage,
        sourceBitmap: Bitmap?,
        useStreamMode: Boolean = false
    ): List<RawDetection> {
        return try {
            val mode = if (useStreamMode) "STREAM" else "SINGLE_IMAGE"
            Log.d(TAG, "Starting raw object detection ($mode) on image ${image.width}x${image.height}")

            // Choose the appropriate detector
            val detector = if (useStreamMode) streamDetector else singleImageDetector

            // Run detection
            val detectedObjects = detector.process(image).await()

            Log.d(TAG, "Detected ${detectedObjects.size} raw objects")

            // Convert each detected object to RawDetection
            detectedObjects.map { obj ->
                convertToRawDetection(obj, sourceBitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting raw objects", e)
            emptyList()
        }
    }

    /**
     * Converts a DetectedObject from ML Kit to RawDetection.
     */
    private fun convertToRawDetection(
        detectedObject: DetectedObject,
        sourceBitmap: Bitmap?
    ): RawDetection {
        // Extract tracking ID (generate UUID if not available)
        val trackingId = detectedObject.trackingId?.toString()
            ?: java.util.UUID.randomUUID().toString()

        // Get bounding box
        val boundingBox = detectedObject.boundingBox

        // Crop thumbnail from source bitmap
        val thumbnail = sourceBitmap?.let { cropThumbnail(it, boundingBox) }

        // Extract all labels with confidences
        val labels = detectedObject.labels.map { label ->
            LabelWithConfidence(
                text = label.text,
                confidence = label.confidence,
                index = label.index
            )
        }

        return RawDetection(
            trackingId = trackingId,
            boundingBox = boundingBox,
            labels = labels,
            thumbnail = thumbnail
        )
    }

    /**
     * Converts a DetectedObject from ML Kit to a ScannedItem.
     */
    private fun convertToScannedItem(
        detectedObject: DetectedObject,
        sourceBitmap: Bitmap?,
        fallbackWidth: Int,
        fallbackHeight: Int
    ): ScannedItem? {
        return try {
            // Extract tracking ID (null if not available)
            val trackingId = detectedObject.trackingId?.toString()
                ?: java.util.UUID.randomUUID().toString()

            // Get bounding box
            val boundingBox = detectedObject.boundingBox

            // Crop thumbnail from source bitmap
            val thumbnail = sourceBitmap?.let { cropThumbnail(it, boundingBox) }

            // Determine category from labels and get confidence
            val bestLabel = detectedObject.labels.maxByOrNull { it.confidence }
            val category = extractCategory(detectedObject)

            // Use effective confidence (fallback for objects without classification)
            val confidence = bestLabel?.confidence ?: run {
                // Objects detected without classification get a higher confidence
                // ML Kit's object detection is reliable even without classification
                if (detectedObject.trackingId != null) {
                    0.6f // Good confidence for tracked but unlabeled objects
                } else {
                    0.4f // Moderate confidence for objects without tracking
                }
            }

            // Calculate normalized bounding box area for pricing
            val boxArea = calculateNormalizedArea(
                box = boundingBox,
                imageWidth = sourceBitmap?.width ?: fallbackWidth,
                imageHeight = sourceBitmap?.height ?: fallbackHeight
            )

            // Generate price range
            val priceRange = PricingEngine.generatePriceRange(category, boxArea)

            ScannedItem(
                id = trackingId,
                thumbnail = thumbnail,
                category = category,
                priceRange = priceRange,
                confidence = confidence
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

        return if (bestLabel != null && bestLabel.confidence > CONFIDENCE_THRESHOLD) {
            Log.d(TAG, "Using label: ${bestLabel.text} (confidence: ${bestLabel.confidence})")
            ItemCategory.fromMlKitLabel(bestLabel.text)
        } else {
            Log.d(TAG, "No confident label found (best: ${bestLabel?.text}:${bestLabel?.confidence})")
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
        try {
            singleImageDetector.close()
            streamDetector.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing detectors", e)
        }
    }
}
