package com.example.objecta.ml

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.example.objecta.items.ScannedItem
import com.example.objecta.tracking.DetectionInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.tasks.await

/**
 * Wrapper class containing both ScannedItems (for list) and DetectionResults (for overlay).
 */
data class DetectionResponse(
    val scannedItems: List<ScannedItem>,
    val detectionResults: List<DetectionResult>
)

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
     * @return DetectionResponse containing both ScannedItems and DetectionResults
     */
    suspend fun detectObjects(
        image: InputImage,
        sourceBitmap: Bitmap?,
        useStreamMode: Boolean = false
    ): DetectionResponse {
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

            // Convert to both ScannedItems and DetectionResults
            val scannedItems = mutableListOf<ScannedItem>()
            val detectionResults = mutableListOf<DetectionResult>()

            detectedObjects.forEach { obj ->
                val scannedItem = convertToScannedItem(
                    detectedObject = obj,
                    sourceBitmap = sourceBitmap,
                    fallbackWidth = image.width,
                    fallbackHeight = image.height
                )
                val detectionResult = convertToDetectionResult(
                    detectedObject = obj,
                    imageWidth = sourceBitmap?.width ?: image.width,
                    imageHeight = sourceBitmap?.height ?: image.height
                )

                if (scannedItem != null) scannedItems.add(scannedItem)
                if (detectionResult != null) detectionResults.add(detectionResult)
            }

            Log.d(TAG, "Converted to ${scannedItems.size} scanned items and ${detectionResults.size} detection results")
            DetectionResponse(scannedItems, detectionResults)
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting objects", e)
            DetectionResponse(emptyList(), emptyList())
        }
    }

    /**
     * Processes an image and extracts raw detection information for tracking.
     *
     * This method returns DetectionInfo objects that can be fed into ObjectTracker
     * for de-duplication and confirmation logic.
     *
     * @param image InputImage to process (from CameraX)
     * @param sourceBitmap Original bitmap for cropping thumbnails
     * @param useStreamMode If true, uses STREAM_MODE detector; if false, uses SINGLE_IMAGE_MODE
     * @return List of DetectionInfo with tracking metadata
     */
    suspend fun detectObjectsWithTracking(
        image: InputImage,
        sourceBitmap: Bitmap?,
        useStreamMode: Boolean = true // Default to STREAM_MODE for tracking
    ): List<DetectionInfo> {
        return try {
            val mode = if (useStreamMode) "STREAM" else "SINGLE_IMAGE"
            Log.d(TAG, "Starting object detection with tracking ($mode) on image ${image.width}x${image.height}")

            // Choose the appropriate detector
            val detector = if (useStreamMode) streamDetector else singleImageDetector

            // Run detection
            val detectedObjects = detector.process(image).await()

            Log.d(TAG, "Detected ${detectedObjects.size} objects for tracking")

            // Convert each detected object to DetectionInfo
            val detectionInfos = detectedObjects.mapNotNull { obj ->
                extractDetectionInfo(
                    detectedObject = obj,
                    sourceBitmap = sourceBitmap,
                    fallbackWidth = image.width,
                    fallbackHeight = image.height
                )
            }

            Log.d(TAG, "Extracted ${detectionInfos.size} detection infos")
            detectionInfos
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting objects with tracking", e)
            emptyList()
        }
    }

    /**
     * Extracts DetectionInfo from a DetectedObject for tracking purposes.
     */
    private fun extractDetectionInfo(
        detectedObject: DetectedObject,
        sourceBitmap: Bitmap?,
        fallbackWidth: Int,
        fallbackHeight: Int
    ): DetectionInfo? {
        return try {
            // Extract tracking ID (may be null)
            val trackingId = detectedObject.trackingId?.toString()

            // Get bounding box and convert to RectF
            val boundingBox = detectedObject.boundingBox
            val boundingBoxF = RectF(
                boundingBox.left.toFloat(),
                boundingBox.top.toFloat(),
                boundingBox.right.toFloat(),
                boundingBox.bottom.toFloat()
            )

            // Get best label and confidence
            val bestLabel = detectedObject.labels.maxByOrNull { it.confidence }
            val confidence = bestLabel?.confidence ?: 0f
            val labelText = bestLabel?.text ?: "Unknown"

            // Determine category
            val category = if (bestLabel != null && bestLabel.confidence > CONFIDENCE_THRESHOLD) {
                ItemCategory.fromMlKitLabel(bestLabel.text)
            } else {
                ItemCategory.UNKNOWN
            }

            // Crop thumbnail
            val thumbnail = sourceBitmap?.let { cropThumbnail(it, boundingBox) }

            // Calculate normalized area
            val normalizedBoxArea = calculateNormalizedArea(
                box = boundingBox,
                imageWidth = sourceBitmap?.width ?: fallbackWidth,
                imageHeight = sourceBitmap?.height ?: fallbackHeight
            )

            DetectionInfo(
                trackingId = trackingId,
                boundingBox = boundingBoxF,
                confidence = confidence,
                category = category,
                labelText = labelText,
                thumbnail = thumbnail,
                normalizedBoxArea = normalizedBoxArea
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting detection info", e)
            null
        }
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
     * Converts a DetectedObject from ML Kit to a DetectionResult for overlay rendering.
     */
    private fun convertToDetectionResult(
        detectedObject: DetectedObject,
        imageWidth: Int,
        imageHeight: Int
    ): DetectionResult? {
        return try {
            val boundingBox = detectedObject.boundingBox
            val category = extractCategory(detectedObject)

            // Calculate normalized bounding box area for pricing
            val boxArea = calculateNormalizedArea(boundingBox, imageWidth, imageHeight)

            // Generate price range
            val priceRange = PricingEngine.generatePriceRange(category, boxArea)

            // Get best confidence score
            val confidence = detectedObject.labels.maxByOrNull { it.confidence }?.confidence ?: 0f

            DetectionResult(
                boundingBox = boundingBox,
                category = category,
                priceRange = priceRange,
                confidence = confidence,
                trackingId = detectedObject.trackingId
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error converting to DetectionResult", e)
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
     * Converts a confirmed ObjectCandidate to a ScannedItem.
     *
     * This is used by the tracking pipeline to create final items from
     * candidates that have met the confirmation threshold.
     */
    fun candidateToScannedItem(
        candidate: com.example.objecta.tracking.ObjectCandidate
    ): ScannedItem {
        // Calculate normalized area from average box area
        val boxArea = candidate.averageBoxArea

        // Generate price range based on category and size
        val priceRange = PricingEngine.generatePriceRange(candidate.category, boxArea)

        return ScannedItem(
            id = candidate.internalId,
            thumbnail = candidate.thumbnail,
            category = candidate.category,
            priceRange = priceRange,
            timestamp = System.currentTimeMillis(),
            recognizedText = null,
            barcodeValue = null
        )
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
