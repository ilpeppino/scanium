package com.scanium.app.ml.detector

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.scanium.app.perf.PerformanceMonitor
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

/**
 * ML Kit object detection engine.
 *
 * This class encapsulates ML Kit detector initialization, detection invocation,
 * and performance measurement. It does NOT handle conversion to application models -
 * that responsibility belongs to [DetectionMapping].
 *
 * Responsibilities:
 * - Detector lifecycle management (lazy init, close)
 * - ML Kit process() invocation with timing/metrics
 * - Model download verification
 * - Raw detection result delivery
 *
 * Threading: Detection is suspending and uses ML Kit's internal threading.
 * The caller should invoke from an appropriate dispatcher (typically IO or Default).
 */
class ObjectDetectionEngine {
    companion object {
        private const val TAG = "ObjectDetectionEngine"

        // Flag to track if model download has been checked
        @Volatile
        private var modelDownloadChecked = false
    }

    // ML Kit object detector configured for single image mode (more accurate)
    private val singleImageDetector: ObjectDetector by lazy {
        val options =
            ObjectDetectorOptions
                .Builder()
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
    private val streamDetector: ObjectDetector by lazy {
        val options =
            ObjectDetectorOptions
                .Builder()
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
     * Runs ML Kit object detection on the provided image.
     *
     * @param image InputImage to process (from CameraX)
     * @param useStreamMode If true, uses STREAM_MODE detector; if false, uses SINGLE_IMAGE_MODE
     * @param metricTag Tag for performance metrics (e.g., "object_detection" or "object_detection_tracking")
     * @return List of raw DetectedObjects from ML Kit
     */
    suspend fun detect(
        image: InputImage,
        useStreamMode: Boolean = false,
        metricTag: String = "object_detection",
    ): List<DetectedObject> {
        val mode = if (useStreamMode) "STREAM" else "SINGLE_IMAGE"
        Log.d(TAG, "Starting object detection ($mode) on image ${image.width}x${image.height}, rotation=${image.rotationDegrees}")

        // Choose the appropriate detector
        val detector = if (useStreamMode) streamDetector else singleImageDetector

        // Run detection with performance measurement
        val detectedObjects =
            PerformanceMonitor.measureMlInference(metricTag) {
                detector.process(image).await()
            }

        Log.d(TAG, "Detected ${detectedObjects.size} objects")
        detectedObjects.forEachIndexed { index, obj ->
            val labels = obj.labels.joinToString { "${it.text}:${it.confidence}" }
            Log.d(TAG, "Object $index: labels=[$labels], box=${obj.boundingBox}")
        }

        return detectedObjects
    }

    /**
     * Runs ML Kit object detection with detailed logging for tracking mode.
     *
     * @param image InputImage to process (from CameraX)
     * @param useStreamMode If true, uses STREAM_MODE detector; if false, uses SINGLE_IMAGE_MODE
     * @return List of raw DetectedObjects from ML Kit
     */
    suspend fun detectWithTrackingLogs(
        image: InputImage,
        useStreamMode: Boolean = true,
    ): List<DetectedObject> {
        val mode = if (useStreamMode) "STREAM" else "SINGLE_IMAGE"
        Log.i(TAG, "========================================")
        Log.i(TAG, ">>> detectWithTracking START")
        Log.i(TAG, ">>> Mode: $mode")
        Log.i(TAG, ">>> Image: ${image.width}x${image.height}, rotation=${image.rotationDegrees}, format=${image.format}")

        // Choose the appropriate detector
        val detector = if (useStreamMode) streamDetector else singleImageDetector
        Log.i(TAG, ">>> Detector: $detector")

        Log.i(TAG, ">>> Attempting detection with original InputImage...")

        // Run detection with performance measurement
        val detectedObjects =
            PerformanceMonitor.measureMlInference("object_detection_tracking") {
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
                task.await()
            }

        Log.i(TAG, ">>> ML Kit returned ${detectedObjects.size} raw objects from original image")

        // Log each detected object
        detectedObjects.forEachIndexed { index, obj ->
            val labels = obj.labels.joinToString { "${it.text}:${it.confidence}" }
            Log.i(TAG, "    Object $index: trackingId=${obj.trackingId}, labels=[$labels], box=${obj.boundingBox}")
        }

        return detectedObjects
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
            val streamOptions =
                ObjectDetectorOptions
                    .Builder()
                    .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                    .enableMultipleObjects()
                    .enableClassification()
                    .build()

            val singleOptions =
                ObjectDetectorOptions
                    .Builder()
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
    fun analyzeBitmap(bitmap: Bitmap): String {
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
            append("size=${width}x$height, ")
            append("config=$config, ")
            append("variance=$totalVariance, ")
            append("isLikelyBlank=$isLikelyBlank, ")
            append("samplePixels=[0x${topLeft.toString(16)}, 0x${center.toString(16)}, 0x${bottomRight.toString(16)}]")
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
