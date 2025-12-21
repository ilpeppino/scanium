package com.scanium.app.ml

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.scanium.app.items.ScannedItem
import com.scanium.app.tracking.DetectionInfo
import com.scanium.android.platform.adapters.toImageRefJpeg
import com.scanium.android.platform.adapters.toNormalizedRect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.UUID

/**
 * Wrapper class containing both ScannedItems (for list) and DetectionResults (for overlay).
 */
data class DetectionResponse(
    val scannedItems: List<ScannedItem>,
    val detectionResults: List<DetectionResult>
)

/**
 * Wrapper class containing both DetectionInfo (for tracking) and DetectionResults (for overlay).
 * This eliminates the need for double detection when tracking is enabled.
 */
data class TrackingDetectionResponse(
    val detectionInfos: List<DetectionInfo>,
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
        private const val MAX_THUMBNAIL_DIMENSION_PX = 512
        private const val BOUNDING_BOX_TIGHTEN_RATIO = 0.12f

        // Flag to track if model download has been checked
        @Volatile
        private var modelDownloadChecked = false

        // Rate-limited logging for dropped detections
        private var lastEdgeDropLogTime = 0L
        private const val EDGE_DROP_LOG_INTERVAL_MS = 5000L

        /**
         * PHASE 3: Geometry-based filtering (ZERO image cropping)
         * Checks if a detection's center falls within the visible cropRect with inset margin.
         * This eliminates detections at screen edges that are likely partial/cut-off objects.
         *
         * @param bbox Detection bounding box in absolute pixel coordinates
         * @param cropRect Visible viewport rect (from ImageProxy.cropRect)
         * @param edgeInsetRatio Margin ratio for edge gating (0.0 to 0.5)
         * @return true if detection should be kept, false if it should be dropped
         */
        private fun isDetectionInsideSafeZone(
            bbox: Rect,
            cropRect: Rect?,
            edgeInsetRatio: Float
        ): Boolean {
            // If no cropRect provided, accept all detections (no filtering)
            if (cropRect == null || edgeInsetRatio <= 0f) return true

            // Calculate detection center (no allocations - use primitives)
            val centerX = (bbox.left + bbox.right) / 2
            val centerY = (bbox.top + bbox.bottom) / 2

            // Calculate inset safe zone (inset from each edge)
            val insetX = (cropRect.width() * edgeInsetRatio).toInt()
            val insetY = (cropRect.height() * edgeInsetRatio).toInt()

            val safeLeft = cropRect.left + insetX
            val safeRight = cropRect.right - insetX
            val safeTop = cropRect.top + insetY
            val safeBottom = cropRect.bottom - insetY

            // Check if center is inside safe zone
            val isInside = centerX >= safeLeft && centerX <= safeRight &&
                    centerY >= safeTop && centerY <= safeBottom

            // Rate-limited logging for edge drops
            if (!isInside) {
                val now = System.currentTimeMillis()
                if (now - lastEdgeDropLogTime >= EDGE_DROP_LOG_INTERVAL_MS) {
                    Log.d(TAG, "[EDGE_FILTER] Dropped detection at edge: center=($centerX,$centerY), safeZone=($safeLeft,$safeTop)-($safeRight,$safeBottom)")
                    lastEdgeDropLogTime = now
                }
            }

            return isInside
        }
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
     * @param sourceBitmap Lazy provider for bitmap (only invoked if detections require thumbnails)
     * @param useStreamMode If true, uses STREAM_MODE detector; if false, uses SINGLE_IMAGE_MODE
     * @param cropRect Visible viewport rect (from ImageProxy.cropRect set by ViewPort)
     * @param edgeInsetRatio Margin ratio for edge gating (0.0 to 0.5)
     * @return DetectionResponse containing both ScannedItems and DetectionResults
     */
    suspend fun detectObjects(
        image: InputImage,
        sourceBitmap: () -> Bitmap?,
        useStreamMode: Boolean = false,
        cropRect: Rect? = null,
        edgeInsetRatio: Float = 0.0f
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

            // OPTIMIZATION: Only generate bitmap if we have detections
            // This avoids expensive bitmap allocation when no objects are detected
            val bitmap = if (detectedObjects.isNotEmpty()) {
                sourceBitmap()
            } else {
                Log.d(TAG, "No detections - skipping bitmap generation")
                null
            }

            // Convert to both ScannedItems and DetectionResults
            val scannedItems = mutableListOf<ScannedItem>()
            val detectionResults = mutableListOf<DetectionResult>()

            detectedObjects.forEach { obj ->
                // PHASE 3: Filter detections using cropRect (no image cropping - pure geometry)
                if (!isDetectionInsideSafeZone(obj.boundingBox, cropRect, edgeInsetRatio)) {
                    // Detection is outside visible area or too close to edge - skip it
                    return@forEach
                }

                val scannedItem = convertToScannedItem(
                    detectedObject = obj,
                    sourceBitmap = bitmap,
                    imageRotationDegrees = image.rotationDegrees,
                    fallbackWidth = image.width,
                    fallbackHeight = image.height
                )
                val detectionResult = convertToDetectionResult(
                    detectedObject = obj,
                    imageWidth = bitmap?.width ?: image.width,
                    imageHeight = bitmap?.height ?: image.height
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
     * This method returns both DetectionInfo objects (for ObjectTracker) and DetectionResult
     * objects (for overlay visualization) from a SINGLE detection pass, eliminating duplicate
     * ML workload.
     *
     * @param image InputImage to process (from CameraX)
     * @param sourceBitmap Lazy provider for bitmap (only invoked if detections require thumbnails)
     * @param useStreamMode If true, uses STREAM_MODE detector; if false, uses SINGLE_IMAGE_MODE
     * @param cropRect Visible viewport rect (from ImageProxy.cropRect set by ViewPort)
     * @param edgeInsetRatio Margin ratio for edge gating (0.0 to 0.5)
     * @return TrackingDetectionResponse with both tracking metadata and overlay data
     */
    suspend fun detectObjectsWithTracking(
        image: InputImage,
        sourceBitmap: () -> Bitmap?,
        useStreamMode: Boolean = true, // Default to STREAM_MODE for tracking
        cropRect: Rect? = null,
        edgeInsetRatio: Float = 0.0f
    ): TrackingDetectionResponse {
        return try {
            val mode = if (useStreamMode) "STREAM" else "SINGLE_IMAGE"
            Log.i(TAG, "========================================")
            Log.i(TAG, ">>> detectObjectsWithTracking START")
            Log.i(TAG, ">>> Mode: $mode")
            Log.i(TAG, ">>> Image: ${image.width}x${image.height}, rotation=${image.rotationDegrees}, format=${image.format}")

            // Choose the appropriate detector
            val detector = if (useStreamMode) streamDetector else singleImageDetector
            Log.i(TAG, ">>> Detector: $detector")

            // Try detection with original image first
            Log.i(TAG, ">>> Attempting detection with original InputImage...")
            val task = detector.process(image)

            // Add failure listener to catch any errors
            task.addOnFailureListener { exception ->
                Log.e(TAG, ">>> ML Kit process() FAILED", exception)
                Log.e(TAG, ">>> Exception type: ${exception.javaClass.simpleName}")
                Log.e(TAG, ">>> Exception message: ${exception.message}")
            }

            task.addOnSuccessListener { objects ->
                Log.i(TAG, ">>> ML Kit process() SUCCESS - returned ${objects.size} objects")
            }

            // Await the result
            val detectedObjects = task.await()

            Log.i(TAG, ">>> ML Kit returned ${detectedObjects.size} raw objects from original image")

            // If zero objects, log diagnostic info and return early (no need to create bitmap)
            if (detectedObjects.isEmpty()) {
                Log.d(TAG, "No detections - skipping bitmap generation for tracking")
                return TrackingDetectionResponse(emptyList(), emptyList())
            }

            // OPTIMIZATION: Only generate bitmap if we have detections that need thumbnails
            val bitmap = sourceBitmap()
            if (bitmap != null) {
                val bitmapAnalysis = analyzeBitmap(bitmap)
                Log.i(TAG, ">>> $bitmapAnalysis")
            } else {
                Log.w(TAG, ">>> WARNING: sourceBitmap provider returned NULL - thumbnails won't be available")
            }

            // Log each detected object
            detectedObjects.forEachIndexed { index, obj ->
                val labels = obj.labels.joinToString { "${it.text}:${it.confidence}" }
                Log.i(TAG, "    Object $index: trackingId=${obj.trackingId}, labels=[$labels], box=${obj.boundingBox}")
            }

            // Convert each detected object to both DetectionInfo and DetectionResult
            // This single pass provides data for both tracker and overlay
            val detectionInfos = mutableListOf<DetectionInfo>()
            val detectionResults = mutableListOf<DetectionResult>()

            detectedObjects.forEach { obj ->
                // PHASE 3: Filter detections using cropRect (no image cropping - pure geometry)
                if (!isDetectionInsideSafeZone(obj.boundingBox, cropRect, edgeInsetRatio)) {
                    // Detection is outside visible area or too close to edge - skip it
                    return@forEach
                }

                // Extract DetectionInfo for tracking
                val detectionInfo = extractDetectionInfo(
                    detectedObject = obj,
                    sourceBitmap = bitmap,
                    imageRotationDegrees = image.rotationDegrees,
                    fallbackWidth = image.width,
                    fallbackHeight = image.height
                )

                // Convert to DetectionResult for overlay
                val detectionResult = convertToDetectionResult(
                    detectedObject = obj,
                    imageWidth = bitmap?.width ?: image.width,
                    imageHeight = bitmap?.height ?: image.height
                )

                if (detectionInfo != null) detectionInfos.add(detectionInfo)
                if (detectionResult != null) detectionResults.add(detectionResult)
            }

            Log.i(TAG, ">>> Extracted ${detectionInfos.size} DetectionInfo objects and ${detectionResults.size} DetectionResult objects")
            detectionInfos.forEachIndexed { index, info ->
                Log.i(TAG, "    DetectionInfo $index: trackingId=${info.trackingId}, category=${info.category}, confidence=${info.confidence}, area=${info.normalizedBoxArea}")
            }

            TrackingDetectionResponse(detectionInfos, detectionResults)
        } catch (e: Exception) {
            // Log.e() with exception parameter automatically includes full stack trace
            Log.e(TAG, ">>> ERROR in detectObjectsWithTracking", e)
            TrackingDetectionResponse(emptyList(), emptyList())
        }
    }

    /**
     * Extracts DetectionInfo from a DetectedObject for tracking purposes.
     */
    private fun extractDetectionInfo(
        detectedObject: DetectedObject,
        sourceBitmap: Bitmap?,
        imageRotationDegrees: Int,
        fallbackWidth: Int,
        fallbackHeight: Int,
        onRawDetection: (RawDetection) -> Unit = {}
    ): DetectionInfo? {
        return try {
            // Extract tracking ID (may be null)
            val trackingId = detectedObject.trackingId?.toString()

            // Get bounding box and convert to normalized coordinates
            val boundingBox = detectedObject.boundingBox

            val labels = detectedObject.labels.mapIndexed { index, label ->
                LabelWithConfidence(
                    text = label.text,
                    confidence = label.confidence,
                    index = index,
                )
            }

            // Get best label and confidence
            val bestLabel = labels.maxByOrNull { it.confidence }
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

            // Crop thumbnail with rotation for correct display orientation
            val thumbnail = sourceBitmap?.let { cropThumbnail(it, boundingBox, imageRotationDegrees) }
            val thumbnailRef = thumbnail?.toImageRefJpeg(quality = 85)

            val frameWidth = sourceBitmap?.width ?: fallbackWidth
            val frameHeight = sourceBitmap?.height ?: fallbackHeight
            val bboxNorm = boundingBox.toNormalizedRect(frameWidth, frameHeight)

            // Calculate normalized area
            val normalizedBoxArea = bboxNorm.area

            Log.d(TAG, "extractDetectionInfo: trackingId=$trackingId, confidence=$confidence (label=$labelConfidence), category=$category, area=$normalizedBoxArea")

            onRawDetection(
                RawDetection(
                    trackingId = trackingId ?: "gen_${UUID.randomUUID()}",
                    bboxNorm = bboxNorm,
                    labels = labels,
                    thumbnailRef = thumbnailRef
                )
            )

            DetectionInfo(
                trackingId = trackingId,
                boundingBox = bboxNorm,
                confidence = confidence,
                category = category,
                labelText = labelText,
                thumbnail = thumbnailRef,
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
        imageRotationDegrees: Int,
        fallbackWidth: Int,
        fallbackHeight: Int
    ): ScannedItem? {
        return try {
            // Extract tracking ID (null if not available)
            val trackingId = detectedObject.trackingId?.toString()
                ?: java.util.UUID.randomUUID().toString()

            // Get bounding box
            val boundingBox = detectedObject.boundingBox
            val frameWidth = sourceBitmap?.width ?: fallbackWidth
            val frameHeight = sourceBitmap?.height ?: fallbackHeight

            // Crop thumbnail from source bitmap with rotation for correct display
            val thumbnail = sourceBitmap?.let { cropThumbnail(it, boundingBox, imageRotationDegrees) }
            val thumbnailRef = thumbnail?.toImageRefJpeg(quality = 85)

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
                imageWidth = frameWidth,
                imageHeight = frameHeight
            )

            // Normalize bounding box to 0-1 coordinates for session deduplication
            val imageWidth = (sourceBitmap?.width ?: fallbackWidth).toFloat()
            val imageHeight = (sourceBitmap?.height ?: fallbackHeight).toFloat()
            val normalizedBox = boundingBox.toNormalizedRect(imageWidth.toInt(), imageHeight.toInt())

            // Generate price range
            val priceRange = PricingEngine.generatePriceRange(category, boxArea)

            ScannedItem(
                id = trackingId,
                thumbnail = thumbnailRef,
                thumbnailRef = thumbnailRef,
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
                bboxNorm = boundingBox.toNormalizedRect(imageWidth, imageHeight),
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
     *
     * @param source Source bitmap (in original sensor orientation)
     * @param boundingBox Bounding box in source bitmap coordinates
     * @param rotationDegrees Rotation to apply after cropping for correct display orientation
     */
    private fun cropThumbnail(source: Bitmap, boundingBox: Rect, rotationDegrees: Int = 0): Bitmap? {
        return try {
            val adjustedBox = boundingBox.tighten(
                insetRatio = BOUNDING_BOX_TIGHTEN_RATIO,
                frameWidth = source.width,
                frameHeight = source.height
            )
            // Ensure bounding box is within bitmap bounds
            val left = adjustedBox.left.coerceIn(0, source.width - 1)
            val top = adjustedBox.top.coerceIn(0, source.height - 1)
            val width = (adjustedBox.width()).coerceIn(1, source.width - left)
            val height = (adjustedBox.height()).coerceIn(1, source.height - top)

            // CRITICAL: Limit thumbnail size to save memory
            // Higher resolution crops (up to 512px) preserve detail for cloud classification
            val maxDimension = MAX_THUMBNAIL_DIMENSION_PX
            val scale = minOf(1.0f, maxDimension.toFloat() / maxOf(width, height))
            val thumbnailWidth = (width * scale).toInt().coerceAtLeast(1)
            val thumbnailHeight = (height * scale).toInt().coerceAtLeast(1)

            // Create small thumbnail with independent pixel data
            val croppedBitmap = Bitmap.createBitmap(thumbnailWidth, thumbnailHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(croppedBitmap)
            val srcRect = android.graphics.Rect(left, top, left + width, top + height)
            val dstRect = android.graphics.Rect(0, 0, thumbnailWidth, thumbnailHeight)
            canvas.drawBitmap(source, srcRect, dstRect, null)

            // Rotate thumbnail to match display orientation
            val rotatedBitmap = if (rotationDegrees != 0) {
                val matrix = android.graphics.Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
                val rotated = Bitmap.createBitmap(
                    croppedBitmap, 0, 0,
                    croppedBitmap.width, croppedBitmap.height,
                    matrix, true
                )
                croppedBitmap.recycle() // Free the unrotated bitmap
                rotated
            } else {
                croppedBitmap
            }

            Log.d(TAG, "Created thumbnail: ${rotatedBitmap.width}x${rotatedBitmap.height} (cropped: ${thumbnailWidth}x${thumbnailHeight}, rotation: ${rotationDegrees}°)")
            rotatedBitmap
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
            thumbnailRef = candidate.thumbnail,
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

private fun Rect.tighten(insetRatio: Float, frameWidth: Int, frameHeight: Int): Rect {
    if (insetRatio <= 0f) return Rect(this)
    if (frameWidth <= 0 || frameHeight <= 0) return Rect(this)
    val currentWidth = width().coerceAtLeast(1)
    val currentHeight = height().coerceAtLeast(1)

    if (currentWidth < 4 || currentHeight < 4) {
        return Rect(
            left.coerceIn(0, frameWidth - 1),
            top.coerceIn(0, frameHeight - 1),
            right.coerceIn(1, frameWidth),
            bottom.coerceIn(1, frameHeight)
        )
    }

    val widthRatio = currentWidth.toFloat() / frameWidth.toFloat()
    val heightRatio = currentHeight.toFloat() / frameHeight.toFloat()
    val dominantRatio = maxOf(widthRatio, heightRatio)
    val adaptiveBoost = when {
        dominantRatio > 0.65f -> 0.08f
        dominantRatio > 0.45f -> 0.05f
        dominantRatio > 0.30f -> 0.03f
        dominantRatio < 0.12f -> -0.04f
        else -> 0f
    }
    val effectiveRatio = (insetRatio + adaptiveBoost).coerceIn(0f, 0.35f)

    val insetX = (currentWidth * effectiveRatio / 2f).roundToInt().coerceAtMost(currentWidth / 2 - 1)
    val insetY = (currentHeight * effectiveRatio / 2f).roundToInt().coerceAtMost(currentHeight / 2 - 1)

    val newLeft = (left + insetX).coerceIn(0, frameWidth - 1)
    val newTop = (top + insetY).coerceIn(0, frameHeight - 1)
    val newRight = (right - insetX).coerceIn(newLeft + 1, frameWidth)
    val newBottom = (bottom - insetY).coerceIn(newTop + 1, frameHeight)

    return Rect(newLeft, newTop, newRight, newBottom)
}
