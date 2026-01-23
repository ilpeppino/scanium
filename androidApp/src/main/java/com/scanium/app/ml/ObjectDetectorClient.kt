package com.scanium.app.ml

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.scanium.app.ScannedItem
import com.scanium.app.ml.detector.DetectionMapping
import com.scanium.app.ml.detector.ObjectDetectionEngine

/**
 * Wrapper class containing both ScannedItems (for list) and DetectionResults (for overlay).
 */
data class DetectionResponse(
    val scannedItems: List<ScannedItem>,
    val detectionResults: List<DetectionResult>,
)

/**
 * Wrapper class containing both DetectionInfo (for tracking) and DetectionResults (for overlay).
 * This eliminates the need for double detection when tracking is enabled.
 */
data class TrackingDetectionResponse(
    val detectionInfos: List<com.scanium.app.tracking.DetectionInfo>,
    val detectionResults: List<DetectionResult>,
)

/**
 * Client for ML Kit Object Detection and Tracking.
 *
 * Configures ML Kit to detect multiple objects with classification enabled.
 * Provides methods to process images and convert detection results to ScannedItems.
 *
 * This class orchestrates the detection pipeline by:
 * - Using [ObjectDetectionEngine] for ML Kit invocation
 * - Using [DetectionMapping] for result conversion
 */
class ObjectDetectorClient {
    companion object {
        private const val TAG = "ObjectDetectorClient"
    }

    // Detection engine handles ML Kit operations
    private val engine = ObjectDetectionEngine()

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
        edgeInsetRatio: Float = 0.0f,
    ): DetectionResponse {
        return try {
            val mode = if (useStreamMode) "STREAM" else "SINGLE_IMAGE"
            Log.d(TAG, "Starting object detection ($mode) on image ${image.width}x${image.height}, rotation=${image.rotationDegrees}")

            // Run detection using engine
            val detectedObjects = engine.detect(image, useStreamMode, "object_detection")

            Log.d(TAG, "Detected ${detectedObjects.size} objects")

            // OPTIMIZATION: Only generate bitmap if we have detections
            // This avoids expensive bitmap allocation when no objects are detected
            val bitmap =
                if (detectedObjects.isNotEmpty()) {
                    sourceBitmap()
                } else {
                    Log.d(TAG, "No detections - skipping bitmap generation")
                    null
                }

            // Convert to both ScannedItems and DetectionResults using DetectionMapping
            val scannedItems = mutableListOf<ScannedItem>()
            val detectionResults = mutableListOf<DetectionResult>()

            // COORDINATE FIX: ML Kit returns bboxes in the ROTATED coordinate space.
            // InputImage.width/height are the ORIGINAL buffer dimensions (before rotation).
            // For 90°/270° rotation, we must SWAP dimensions to match the rotated space.
            // For thumbnail cropping, we convert upright bbox to sensor space separately.
            val isRotated = image.rotationDegrees == 90 || image.rotationDegrees == 270
            val uprightWidth = if (isRotated) image.height else image.width
            val uprightHeight = if (isRotated) image.width else image.height
            Log.d(
                TAG,
                "Normalization dims: rotated=$isRotated, upright=${uprightWidth}x$uprightHeight (raw=${image.width}x${image.height})",
            )

            detectedObjects.forEach { obj ->
                // PHASE 3: Filter detections using cropRect (no image cropping - pure geometry)
                if (!DetectionMapping.isDetectionInsideSafeZone(obj.boundingBox, cropRect, edgeInsetRatio)) {
                    // Detection is outside visible area or too close to edge - skip it
                    return@forEach
                }

                // Validate bounding box size and aspect ratio
                if (!DetectionMapping.isBoundingBoxValid(obj.boundingBox, uprightWidth, uprightHeight)) {
                    // Detection has invalid bbox (too large or extreme aspect ratio) - skip it
                    return@forEach
                }

                val scannedItem =
                    DetectionMapping.convertToScannedItem(
                        detectedObject = obj,
                        sourceBitmap = bitmap,
                        imageRotationDegrees = image.rotationDegrees,
                        uprightWidth = uprightWidth,
                        uprightHeight = uprightHeight,
                    )
                val detectionResult =
                    DetectionMapping.convertToDetectionResult(
                        detectedObject = obj,
                        imageWidth = uprightWidth,
                        imageHeight = uprightHeight,
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
    suspend fun ensureModelDownloaded(): Boolean = engine.ensureModelDownloaded()

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
        useStreamMode: Boolean = true,
// Default to STREAM_MODE for tracking
        cropRect: Rect? = null,
        edgeInsetRatio: Float = 0.0f,
    ): TrackingDetectionResponse {
        return try {
            val mode = if (useStreamMode) "STREAM" else "SINGLE_IMAGE"
            Log.i(TAG, "========================================")
            Log.i(TAG, ">>> detectObjectsWithTracking START")
            Log.i(TAG, ">>> Mode: $mode")
            Log.i(TAG, ">>> Image: ${image.width}x${image.height}, rotation=${image.rotationDegrees}, format=${image.format}")

            // Run detection using engine with tracking logs
            val detectedObjects = engine.detectWithTrackingLogs(image, useStreamMode)

            Log.i(TAG, ">>> ML Kit returned ${detectedObjects.size} raw objects from original image")

            // If zero objects, log diagnostic info and return early (no need to create bitmap)
            if (detectedObjects.isEmpty()) {
                Log.d(TAG, "No detections - skipping bitmap generation for tracking")
                return TrackingDetectionResponse(emptyList(), emptyList())
            }

            // OPTIMIZATION: Only generate bitmap if we have detections that need thumbnails
            val bitmap = sourceBitmap()
            if (bitmap != null) {
                val bitmapAnalysis = engine.analyzeBitmap(bitmap)
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
            val detectionInfos = mutableListOf<com.scanium.app.tracking.DetectionInfo>()
            val detectionResults = mutableListOf<DetectionResult>()

            // COORDINATE FIX: ML Kit returns bboxes in the ROTATED coordinate space.
            // InputImage.width/height are the ORIGINAL buffer dimensions (before rotation).
            // For 90°/270° rotation, we must SWAP dimensions to match the rotated space.
            val isRotated = image.rotationDegrees == 90 || image.rotationDegrees == 270
            val uprightWidth = if (isRotated) image.height else image.width
            val uprightHeight = if (isRotated) image.width else image.height

            detectedObjects.forEach { obj ->
                // PHASE 3: Filter detections using cropRect (no image cropping - pure geometry)
                if (!DetectionMapping.isDetectionInsideSafeZone(obj.boundingBox, cropRect, edgeInsetRatio)) {
                    // Detection is outside visible area or too close to edge - skip it
                    return@forEach
                }

                // Validate bounding box size and aspect ratio
                if (!DetectionMapping.isBoundingBoxValid(obj.boundingBox, uprightWidth, uprightHeight)) {
                    // Detection has invalid bbox (too large or extreme aspect ratio) - skip it
                    return@forEach
                }

                // Extract DetectionInfo for tracking using DetectionMapping
                val detectionInfo =
                    DetectionMapping.extractDetectionInfo(
                        detectedObject = obj,
                        sourceBitmap = bitmap,
                        imageRotationDegrees = image.rotationDegrees,
                        uprightWidth = uprightWidth,
                        uprightHeight = uprightHeight,
                    )

                // Convert to DetectionResult for overlay using DetectionMapping
                val detectionResult =
                    DetectionMapping.convertToDetectionResult(
                        detectedObject = obj,
                        imageWidth = uprightWidth,
                        imageHeight = uprightHeight,
                    )

                if (detectionInfo != null) detectionInfos.add(detectionInfo)
                if (detectionResult != null) detectionResults.add(detectionResult)
            }

            Log.i(TAG, ">>> Extracted ${detectionInfos.size} DetectionInfo objects and ${detectionResults.size} DetectionResult objects")
            detectionInfos.forEachIndexed { index, info ->
                Log.i(
                    TAG,
                    "    DetectionInfo $index: trackingId=${info.trackingId}, category=${info.category}, confidence=${info.confidence}, area=${info.normalizedBoxArea}",
                )
            }

            TrackingDetectionResponse(detectionInfos, detectionResults)
        } catch (e: Exception) {
            // Log.e() with exception parameter automatically includes full stack trace
            Log.e(TAG, ">>> ERROR in detectObjectsWithTracking", e)
            TrackingDetectionResponse(emptyList(), emptyList())
        }
    }

    /**
     * Converts a confirmed ObjectCandidate to a ScannedItem.
     *
     * This is used by the tracking pipeline to create final items from
     * candidates that have met the confirmation threshold.
     */
    fun candidateToScannedItem(candidate: com.scanium.app.tracking.ObjectCandidate): ScannedItem =
        DetectionMapping.candidateToScannedItem(candidate)

    /**
     * Cleanup resources when done.
     */
    fun close() {
        engine.close()
    }
}
