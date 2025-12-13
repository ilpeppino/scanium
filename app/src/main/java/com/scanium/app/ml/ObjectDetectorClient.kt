package com.scanium.app.ml

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.scanium.app.items.ScannedItem
import com.scanium.app.tracking.DetectionInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

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
        private const val CONFIDENCE_THRESHOLD = 0.3f // Category assignment threshold

        // Flag to track if model download has been checked
        @Volatile
        private var modelDownloadChecked = false
    }

    // ML Kit object detector configured for single image mode (more accurate)
    private val singleImageDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE) // Better for tap capture
            .enableMultipleObjects() // Detect multiple objects per frame
            .enableClassification() // Enable labels for category mapping
            .build()

        Log.i(TAG, "======================================")
        Log.i(TAG, "Creating SINGLE_IMAGE_MODE detector")
        Log.i(TAG, "Config: mode=SINGLE_IMAGE, multipleObjects=true, classification=TRUE")
        Log.i(TAG, "Classification enabled for richer categories")
        Log.i(TAG, "======================================")
        ObjectDetection.getClient(options)
    }

    // ML Kit object detector for streaming (scanning mode)
    private val streamDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE) // Optimized for video frames
            .enableMultipleObjects() // Detect multiple objects per frame
            .enableClassification() // Enable labels for category mapping
            .build()

        Log.i(TAG, "======================================")
        Log.i(TAG, "Creating STREAM_MODE detector")
        Log.i(TAG, "Config: mode=STREAM, multipleObjects=true, classification=TRUE")
        Log.i(TAG, "Classification enabled for richer categories")
        Log.i(TAG, "======================================")
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
     * Checks if ML Kit object detection model is downloaded.
     * This should be called before first detection to ensure the model is ready.
     */
    suspend fun ensureModelDownloaded(): Boolean {
        if (modelDownloadChecked) {
            Log.d(TAG, "Model download already checked, skipping")
            return true
        }

        return try {
            Log.i(TAG, "========================================")
            Log.i(TAG, "Checking ML Kit Object Detection model download status...")
            Log.i(TAG, "========================================")

            // Try to get the model manager (this may not work for base models)
            // For base object detection, the model is typically bundled or auto-downloaded

            // Force initialization of both detectors to trigger model download
            val streamOptions = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build()

            val singleOptions = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build()

            Log.i(TAG, "Initializing detectors to trigger model download...")

            // Initialize detectors (this triggers model download if needed)
            val streamDetectorTest = ObjectDetection.getClient(streamOptions)
            val singleDetectorTest = ObjectDetection.getClient(singleOptions)

            // Close test detectors
            streamDetectorTest.close()
            singleDetectorTest.close()

            modelDownloadChecked = true

            Log.i(TAG, "========================================")
            Log.i(TAG, "ML Kit Object Detection model initialization complete")
            Log.i(TAG, "Note: Model download happens automatically on first use")
            Log.i(TAG, "========================================")

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking/downloading model", e)
            false
        }
    }

    /**
     * Analyzes bitmap to check if it's valid for detection.
     * Returns diagnostic information about the image.
     */
    private fun analyzeBitmap(bitmap: Bitmap): String {
        val width = bitmap.width
        val height = bitmap.height
        val config = bitmap.config

        // Sample pixels from different regions
        val centerX = width / 2
        val centerY = height / 2

        val topLeft = if (width > 0 && height > 0) bitmap.getPixel(0, 0) else 0
        val center = if (centerX < width && centerY < height) bitmap.getPixel(centerX, centerY) else 0
        val bottomRight = if (width > 1 && height > 1) bitmap.getPixel(width - 1, height - 1) else 0

        // Calculate color variance to detect blank images
        val redVariance = abs(android.graphics.Color.red(topLeft) - android.graphics.Color.red(center))
        val greenVariance = abs(android.graphics.Color.green(topLeft) - android.graphics.Color.green(center))
        val blueVariance = abs(android.graphics.Color.blue(topLeft) - android.graphics.Color.blue(center))
        val totalVariance = redVariance + greenVariance + blueVariance

        val isLikelyBlank = totalVariance < 30 // Very low variance suggests blank image

        return buildString {
            append("Bitmap Analysis: ")
            append("size=${width}x${height}, ")
            append("config=$config, ")
            append("variance=$totalVariance, ")
            append("isLikelyBlank=$isLikelyBlank, ")
            append("samplePixels=[0x${topLeft.toString(16)}, 0x${center.toString(16)}, 0x${bottomRight.toString(16)}]")
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
            Log.i(TAG, "========================================")
            Log.i(TAG, ">>> detectObjectsWithTracking START")
            Log.i(TAG, ">>> Mode: $mode")
            Log.i(TAG, ">>> Image: ${image.width}x${image.height}, rotation=${image.rotationDegrees}, format=${image.format}")

            // Analyze bitmap if available
            if (sourceBitmap != null) {
                val bitmapAnalysis = analyzeBitmap(sourceBitmap)
                Log.i(TAG, ">>> $bitmapAnalysis")
            } else {
                Log.w(TAG, ">>> WARNING: sourceBitmap is NULL - thumbnails won't be available")
            }

            // Choose the appropriate detector
            val detector = if (useStreamMode) streamDetector else singleImageDetector
            Log.i(TAG, ">>> Detector: $detector")

            // CRITICAL FIX: Try creating InputImage from bitmap instead of MediaImage
            // This can sometimes work better when MediaImage detection fails
            val alternativeImage = if (sourceBitmap != null) {
                try {
                    val altImage = InputImage.fromBitmap(sourceBitmap, image.rotationDegrees)
                    Log.i(TAG, ">>> Created alternative InputImage from bitmap: ${altImage.width}x${altImage.height}")
                    altImage
                } catch (e: Exception) {
                    Log.w(TAG, ">>> Failed to create alternative InputImage from bitmap", e)
                    null
                }
            } else {
                null
            }

            // Try detection with original image first
            Log.i(TAG, ">>> Attempting detection with original InputImage...")
            val task = detector.process(image)

            // Add failure listener to catch any errors
            task.addOnFailureListener { exception ->
                Log.e(TAG, ">>> ML Kit process() FAILED", exception)
                Log.e(TAG, ">>> Exception type: ${exception.javaClass.simpleName}")
                Log.e(TAG, ">>> Exception message: ${exception.message}")
                Log.e(TAG, ">>> Will try alternative InputImage if available")
            }

            task.addOnSuccessListener { objects ->
                Log.i(TAG, ">>> ML Kit process() SUCCESS - returned ${objects.size} objects")
            }

            // Await the result
            var detectedObjects = task.await()

            Log.i(TAG, ">>> ML Kit returned ${detectedObjects.size} raw objects from original image")

            // If zero objects and we have alternative image, try with that
            if (detectedObjects.isEmpty() && alternativeImage != null) {
                Log.w(TAG, ">>> Zero objects detected, trying with bitmap-based InputImage...")
                try {
                    val altTask = detector.process(alternativeImage)
                    val altObjects = altTask.await()
                    Log.i(TAG, ">>> Alternative detection returned ${altObjects.size} objects")
                    if (altObjects.isNotEmpty()) {
                        Log.i(TAG, ">>> SUCCESS: Alternative InputImage detected objects!")
                        detectedObjects = altObjects
                    }
                } catch (e: Exception) {
                    Log.e(TAG, ">>> Alternative detection also failed", e)
                }
            }

            // DISABLED: Don't mix STREAM and SINGLE_IMAGE modes - causes ML Kit crashes
            // If still zero objects, try SINGLE_IMAGE_MODE as a fallback
            if (false && detectedObjects.isEmpty() && useStreamMode) {
                Log.w(TAG, ">>> Zero objects in STREAM mode, trying SINGLE_IMAGE_MODE...")
                try {
                    val singleModeTask = singleImageDetector.process(image)
                    val singleModeObjects = singleModeTask.await()
                    Log.i(TAG, ">>> SINGLE_IMAGE_MODE returned ${singleModeObjects.size} objects")
                    if (singleModeObjects.isNotEmpty()) {
                        Log.i(TAG, ">>> SUCCESS: SINGLE_IMAGE_MODE detected objects!")
                        detectedObjects = singleModeObjects
                    }
                } catch (e: Exception) {
                    Log.e(TAG, ">>> SINGLE_IMAGE_MODE detection also failed", e)
                }
            }

            // If zero objects, log comprehensive diagnostic info
            if (detectedObjects.isEmpty()) {
                Log.e(TAG, "========================================")
                Log.e(TAG, ">>> CRITICAL: ZERO OBJECTS DETECTED AFTER ALL ATTEMPTS")
                Log.e(TAG, ">>> Image details: ${image.width}x${image.height}, rotation=${image.rotationDegrees}, format=${image.format}")
                Log.e(TAG, ">>> Bitmap details: ${sourceBitmap?.width}x${sourceBitmap?.height}")
                Log.e(TAG, ">>> Tried: original InputImage, alternative bitmap-based InputImage, SINGLE_IMAGE_MODE")
                Log.e(TAG, ">>> ")
                Log.e(TAG, ">>> Possible causes:")
                Log.e(TAG, ">>>   1. ML Kit model not downloaded (first run requires network)")
                Log.e(TAG, ">>>   2. Scene doesn't contain detectable objects")
                Log.e(TAG, ">>>   3. Image too dark, blurry, or low contrast")
                Log.e(TAG, ">>>   4. Objects too small or too large in frame")
                Log.e(TAG, ">>>   5. ML Kit's detection thresholds too strict")
                Log.e(TAG, ">>> ")
                Log.e(TAG, ">>> Recommendations:")
                Log.e(TAG, ">>>   - Ensure device has internet connection for model download")
                Log.e(TAG, ">>>   - Point camera at clear, well-lit objects")
                Log.e(TAG, ">>>   - Try objects with distinct shapes (bottles, books, boxes)")
                Log.e(TAG, ">>>   - Ensure objects fill 10-50% of frame")
                Log.e(TAG, "========================================")
            }

            // Log each detected object
            detectedObjects.forEachIndexed { index, obj ->
                val labels = obj.labels.joinToString { "${it.text}:${it.confidence}" }
                Log.i(TAG, "    Object $index: trackingId=${obj.trackingId}, labels=[$labels], box=${obj.boundingBox}")
            }

            // Convert each detected object to DetectionInfo
            val detectionInfos = detectedObjects.mapNotNull { obj ->
                extractDetectionInfo(
                    detectedObject = obj,
                    sourceBitmap = sourceBitmap,
                    fallbackWidth = image.width,
                    fallbackHeight = image.height
                )
            }

            Log.i(TAG, ">>> Extracted ${detectionInfos.size} DetectionInfo objects")
            detectionInfos.forEachIndexed { index, info ->
                Log.i(TAG, "    DetectionInfo $index: trackingId=${info.trackingId}, category=${info.category}, confidence=${info.confidence}, area=${info.normalizedBoxArea}")
            }

            detectionInfos
        } catch (e: Exception) {
            // Log.e() with exception parameter automatically includes full stack trace
            Log.e(TAG, ">>> ERROR in detectObjectsWithTracking", e)
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
            val labelConfidence = bestLabel?.confidence ?: 0f

            // CRITICAL: Use effective confidence for objects without classification
            // ML Kit's object detection is reliable even without classification
            val confidence = labelConfidence.takeIf { it > 0f } ?: run {
                // Objects detected without classification get a reasonable confidence
                if (detectedObject.trackingId != null) {
                    0.6f // Good confidence for tracked but unlabeled objects
                } else {
                    0.4f // Moderate confidence for objects without tracking
                }
            }

            val labelText = bestLabel?.text ?: "Object"

            // Determine category
            val category = if (labelConfidence >= CONFIDENCE_THRESHOLD) {
                ItemCategory.fromMlKitLabel(bestLabel?.text)
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

            Log.d(TAG, "extractDetectionInfo: trackingId=$trackingId, confidence=$confidence (label=$labelConfidence), category=$category, area=$normalizedBoxArea")

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
            val labelConfidence = bestLabel?.confidence ?: 0f
            val category = extractCategory(detectedObject)

            // Use effective confidence (fallback for objects without classification)
            val confidence = labelConfidence.takeIf { it > 0f } ?: run {
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

            // Normalize bounding box to 0-1 coordinates for session deduplication
            val imageWidth = (sourceBitmap?.width ?: fallbackWidth).toFloat()
            val imageHeight = (sourceBitmap?.height ?: fallbackHeight).toFloat()
            val normalizedBox = android.graphics.RectF(
                boundingBox.left / imageWidth,
                boundingBox.top / imageHeight,
                boundingBox.right / imageWidth,
                boundingBox.bottom / imageHeight
            )

            // Generate price range
            val priceRange = PricingEngine.generatePriceRange(category, boxArea)

            ScannedItem(
                id = trackingId,
                thumbnail = thumbnail,
                category = category,
                priceRange = priceRange,
                confidence = confidence,
                boundingBox = normalizedBox,
                labelText = bestLabel?.text
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
        val labelConfidence = bestLabel?.confidence ?: 0f

        return if (labelConfidence >= CONFIDENCE_THRESHOLD) {
            Log.d(TAG, "Using label: ${bestLabel?.text} (confidence: $labelConfidence)")
            ItemCategory.fromMlKitLabel(bestLabel?.text)
        } else {
            Log.d(TAG, "No confident label found (best: ${bestLabel?.text}:$labelConfidence)")
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

            // CRITICAL: Limit thumbnail size to save memory
            // Max 200x200 pixels is plenty for display
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

            Log.d(TAG, "Created thumbnail: ${thumbnailWidth}x${thumbnailHeight} (original: ${width}x${height})")
            thumbnail
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create thumbnail", e)
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
        candidate: com.scanium.app.tracking.ObjectCandidate
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
            confidence = candidate.maxConfidence,
            timestamp = System.currentTimeMillis(),
            recognizedText = null,
            barcodeValue = null,
            boundingBox = candidate.boundingBox,
            labelText = candidate.labelText.takeIf { it.isNotBlank() }
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
