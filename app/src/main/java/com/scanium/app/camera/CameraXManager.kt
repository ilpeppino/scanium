package com.scanium.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.YuvImage
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.scanium.app.items.ScannedItem
import com.scanium.app.ml.BarcodeScannerClient
import com.scanium.app.ml.DetectionResult
import com.scanium.app.ml.DocumentTextRecognitionClient
import com.scanium.app.ml.ObjectDetectorClient
import com.scanium.app.tracking.ObjectTracker
import com.scanium.app.tracking.TrackerConfig
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages CameraX lifecycle, preview, and image analysis.
 *
 * Responsibilities:
 * - Bind camera preview to PreviewView
 * - Set up ImageAnalysis for object detection
 * - Handle single-shot capture and continuous scanning
 */
class CameraXManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "CameraXManager"
    }

    data class CameraBindResult(
        val success: Boolean,
        val lensFacingUsed: Int,
        val error: Throwable? = null
    )

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null

    private val objectDetector = ObjectDetectorClient()
    private val barcodeScanner = BarcodeScannerClient()
    private val textRecognizer = DocumentTextRecognitionClient()

    // Object tracker for de-duplication
    // Using very permissive thresholds to ensure items are actually promoted
    private val objectTracker = ObjectTracker(
        config = TrackerConfig(
            minFramesToConfirm = 1,      // Confirm immediately (rely on session-level dedup)
            minConfidence = 0.2f,         // Very low confidence threshold (20%)
            minBoxArea = 0.0005f,         // Very small box area (0.05% of frame)
            maxFrameGap = 8,              // Allow 8 frames gap for matching (more forgiving)
            minMatchScore = 0.2f,         // Lower match score threshold for better spatial matching
            expiryFrames = 15             // Keep candidates longer (15 frames)
        )
    )

    // Executor for camera operations
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Coroutine scope for async detection
    private val detectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // State for scanning mode
    private var isScanning = false
    private var scanJob: Job? = null
    private var currentScanMode: ScanMode? = null

    // Frame counter for periodic cleanup and stats logging
    private var frameCounter = 0

    // Model initialization flag
    private var modelInitialized = false

    /**
     * Ensures ML Kit models are downloaded and ready.
     * Should be called before starting detection.
     */
    suspend fun ensureModelsReady() {
        if (modelInitialized) return

        Log.i(TAG, "========================================")
        Log.i(TAG, "Ensuring ML Kit models are ready...")
        Log.i(TAG, "========================================")

        try {
            // Ensure object detection model is downloaded
            val objectDetectorReady = objectDetector.ensureModelDownloaded()
            Log.i(TAG, "Object detection model ready: $objectDetectorReady")

            modelInitialized = true
            Log.i(TAG, "All ML Kit models initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring models ready", e)
        }
    }

    /**
     * Initializes and binds camera to the PreviewView.
     */
    suspend fun startCamera(
        previewView: PreviewView,
        lensFacing: Int
    ): CameraBindResult = withContext(Dispatchers.Main) {
        // Ensure models are downloaded before starting camera
        ensureModelsReady()

        val provider = awaitCameraProvider(context)
        cameraProvider = provider

        // Setup preview
        preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        // Setup image analysis for object detection
        imageAnalysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(previewView.display.rotation)
            .setTargetResolution(android.util.Size(1280, 720)) // Higher resolution for better detection
            .build()

        Log.d(TAG, "ImageAnalysis configured with target resolution 1280x720")

        val requestedSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
        val fallbackLensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        val fallbackSelector = CameraSelector.Builder()
            .requireLensFacing(fallbackLensFacing)
            .build()

        val selectorToUse = when {
            provider.hasCamera(requestedSelector) -> requestedSelector
            provider.hasCamera(fallbackSelector) -> {
                Log.w(TAG, "Requested lens $lensFacing not available. Falling back to $fallbackLensFacing")
                fallbackSelector
            }

            else -> {
                Log.e(TAG, "No available camera for requested or fallback lens")
                return@withContext CameraBindResult(
                    success = false,
                    lensFacingUsed = lensFacing,
                    error = IllegalStateException("No available camera for requested or fallback lens")
                )
            }
        }

        val resolvedLensFacing = if (selectorToUse == requestedSelector) lensFacing else fallbackLensFacing

        try {
            // Unbind any existing use cases
            provider.unbindAll()
            stopScanning()

            // Bind use cases to lifecycle
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                selectorToUse,
                preview,
                imageAnalysis
            )
            CameraBindResult(success = true, lensFacingUsed = resolvedLensFacing)
        } catch (e: Exception) {
            // Handle camera binding failure (Log.e includes full stack trace)
            Log.e(TAG, "Failed to bind camera use cases", e)
            CameraBindResult(success = false, lensFacingUsed = lensFacing, error = e)
        }
    }

    /**
     * Captures a single frame and runs detection based on the current scan mode.
     * Single-frame captures bypass the candidate tracker for immediate results.
     */
    fun captureSingleFrame(
        scanMode: ScanMode,
        onResult: (List<ScannedItem>) -> Unit,
        onDetectionResult: (List<DetectionResult>) -> Unit = {},
        onFrameSize: (Size) -> Unit = {}
    ) {
        Log.d(TAG, "captureSingleFrame: Starting single frame capture with mode $scanMode")
        // Clear any previous analyzer to avoid mixing modes
        imageAnalysis?.clearAnalyzer()
        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            Log.d(TAG, "captureSingleFrame: Received image proxy ${imageProxy.width}x${imageProxy.height}")
            detectionScope.launch {
                // Report actual frame dimensions
                val frameSize = Size(imageProxy.width, imageProxy.height)
                withContext(Dispatchers.Main) {
                    onFrameSize(frameSize)
                }

                // Single-frame capture uses direct detection (no candidate tracking)
                val (items, detections) = processImageProxy(imageProxy, scanMode, useStreamMode = false)
                Log.d(TAG, "captureSingleFrame: Got ${items.size} items")
                withContext(Dispatchers.Main) {
                    onResult(items)
                    onDetectionResult(detections)
                }
                imageAnalysis?.clearAnalyzer()
            }
        }
    }

    /**
     * Starts continuous scanning mode with multi-frame candidate tracking.
     * Captures frames periodically and uses ObjectTracker to promote only stable detections.
     */
    fun startScanning(
        scanMode: ScanMode,
        onResult: (List<ScannedItem>) -> Unit,
        onDetectionResult: (List<DetectionResult>) -> Unit = {},
        onFrameSize: (Size) -> Unit = {}
    ) {
        if (isScanning) {
            Log.d(TAG, "startScanning: Already scanning, ignoring")
            return
        }

        Log.d(TAG, "startScanning: Starting continuous scanning mode with $scanMode")

        // Reset tracker when starting a new scan session or switching modes
        if (currentScanMode != scanMode || currentScanMode == null) {
            Log.i(TAG, "startScanning: Resetting tracker (mode change: $currentScanMode -> $scanMode)")
            objectTracker.reset()
            currentScanMode = scanMode
        }

        isScanning = true
        frameCounter = 0

        var lastAnalysisTime = 0L
        val analysisIntervalMs = 800L // Analyze every 800ms for better tracking
        var isProcessing = false // Prevent overlapping processing

        // Log detection configuration (debug builds only)
        com.scanium.app.ml.DetectionLogger.logConfiguration(
            minSeenCount = 1,
            minConfidence = 0.0f,
            candidateTimeoutMs = 3000L,
            analysisIntervalMs = analysisIntervalMs
        )

        // Clear any previous analyzer to avoid stale callbacks
        imageAnalysis?.clearAnalyzer()

        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            if (!isScanning) {
                Log.d(TAG, "startScanning: Not scanning anymore, closing proxy")
                imageProxy.close()
                return@setAnalyzer
            }

            val currentTime = System.currentTimeMillis()

            // Only process if enough time has passed AND we're not already processing
            if (currentTime - lastAnalysisTime >= analysisIntervalMs && !isProcessing) {
                lastAnalysisTime = currentTime
                isProcessing = true
                frameCounter++
                Log.d(TAG, "startScanning: Processing frame ***REMOVED***$frameCounter ${imageProxy.width}x${imageProxy.height}")

                detectionScope.launch {
                    try {
                        // Report actual frame dimensions
                        val frameSize = Size(imageProxy.width, imageProxy.height)
                        withContext(Dispatchers.Main) {
                            onFrameSize(frameSize)
                        }

                        // Use STREAM_MODE for object detection with tracking during continuous scanning
                        // CRITICAL: Use SINGLE_IMAGE_MODE to avoid ML Kit frame buffer crash
                        // STREAM_MODE causes native crashes when frames get recycled
                        val (items, detections) = processImageProxy(imageProxy, scanMode, useStreamMode = false)
                        Log.i(TAG, ">>> startScanning: processImageProxy returned ${items.size} items")
                        withContext(Dispatchers.Main) {
                            if (items.isNotEmpty()) {
                                Log.i(TAG, ">>> startScanning: Calling onResult callback with ${items.size} items")
                                onResult(items)
                            } else {
                                Log.i(TAG, ">>> startScanning: No items to report, skipping onResult callback")
                            }
                            // Always send detection results for overlay (even if empty to clear old detections)
                            onDetectionResult(detections)
                        }

                        // Periodic stats logging (every 10 frames)
                        if (frameCounter % 10 == 0) {
                            val stats = objectTracker.getStats()
                            Log.i(TAG, "Tracker stats: active=${stats.activeCandidates}, confirmed=${stats.confirmedCandidates}, frame=${stats.currentFrame}")
                        }
                    } finally {
                        isProcessing = false
                    }
                }
            } else {
                // Close image proxy immediately if we're not processing it
                imageProxy.close()
            }
        }
    }

    /**
     * Stops continuous scanning mode.
     */
    fun stopScanning() {
        Log.d(TAG, "stopScanning: Stopping continuous scanning mode")
        isScanning = false
        imageAnalysis?.clearAnalyzer()
        scanJob?.cancel()
        scanJob = null
        // Reset tracker when stopping scan
        objectTracker.reset()
        currentScanMode = null
    }

    /**
     * Processes an ImageProxy: converts to Bitmap and runs ML Kit detection based on scan mode.
     * Used for single-frame captures (bypasses candidate tracking).
     */
    private suspend fun processImageProxy(
        imageProxy: ImageProxy,
        scanMode: ScanMode,
        useStreamMode: Boolean = false
    ): Pair<List<ScannedItem>, List<DetectionResult>> {
        return try {
            Log.i(TAG, ">>> processImageProxy: START - scanMode=$scanMode, useStreamMode=$useStreamMode, isScanning=$isScanning")

            // Convert ImageProxy to Bitmap
            val mediaImage = imageProxy.image ?: run {
                Log.e(TAG, "processImageProxy: mediaImage is null")
                return Pair(emptyList(), emptyList())
            }
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            Log.i(TAG, ">>> processImageProxy: image=${imageProxy.width}x${imageProxy.height}, rotation=$rotationDegrees")

            // Build ML Kit image directly from camera buffer
            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            // Optional bitmap for thumbnails
            val bitmapForThumb = runCatching {
                val bitmap = imageProxy.toBitmap()
                Log.i(TAG, ">>> processImageProxy: Created bitmap ${bitmap.width}x${bitmap.height}")
                rotateBitmap(bitmap, rotationDegrees)
            }.getOrElse { e ->
                Log.w(TAG, "processImageProxy: Failed to create bitmap", e)
                null
            }

            // Route to the appropriate scanner based on mode
            when (scanMode) {
                ScanMode.OBJECT_DETECTION -> {
                    // Use tracking pipeline when in STREAM_MODE and scanning
                    if (useStreamMode && isScanning) {
                        Log.i(TAG, ">>> processImageProxy: Taking TRACKING PATH (useStreamMode=$useStreamMode, isScanning=$isScanning)")
                        val items = processObjectDetectionWithTracking(inputImage, bitmapForThumb)
                        // For now, return empty detection results when using tracking
                        // (overlay visualization can be added later if needed)
                        Log.i(TAG, ">>> processImageProxy: Tracking path returned ${items.size} items")
                        Pair(items, emptyList())
                    } else {
                        // Single-shot detection without tracking
                        Log.i(TAG, ">>> processImageProxy: Taking SINGLE-SHOT PATH (useStreamMode=$useStreamMode, isScanning=$isScanning)")
                        val response = objectDetector.detectObjects(
                            image = inputImage,
                            sourceBitmap = bitmapForThumb,
                            useStreamMode = useStreamMode
                        )
                        Log.i(TAG, ">>> processImageProxy: Single-shot path returned ${response.scannedItems.size} items")
                        Pair(response.scannedItems, response.detectionResults)
                    }
                }
                ScanMode.BARCODE -> {
                    // Barcode and text scanners don't return DetectionResults yet
                    val items = barcodeScanner.scanBarcodes(
                        image = inputImage,
                        sourceBitmap = bitmapForThumb
                    )
                    Pair(items, emptyList())
                }
                ScanMode.DOCUMENT_TEXT -> {
                    val items = textRecognizer.recognizeText(
                        image = inputImage,
                        sourceBitmap = bitmapForThumb
                    )
                    Pair(items, emptyList())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, ">>> processImageProxy: ERROR", e)
            Pair(emptyList(), emptyList())
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Process object detection with tracking to reduce duplicates.
     */
    private suspend fun processObjectDetectionWithTracking(
        inputImage: InputImage,
        sourceBitmap: Bitmap?
    ): List<ScannedItem> {
        Log.i(TAG, ">>> processObjectDetectionWithTracking: CALLED")

        // Get raw detections with tracking metadata
        val detections = objectDetector.detectObjectsWithTracking(
            image = inputImage,
            sourceBitmap = sourceBitmap,
            useStreamMode = true
        )

        Log.i(TAG, ">>> processObjectDetectionWithTracking: Got ${detections.size} raw DetectionInfo objects from ObjectDetectorClient")

        // Process through tracker
        val confirmedCandidates = objectTracker.processFrame(detections)

        Log.i(TAG, ">>> processObjectDetectionWithTracking: ObjectTracker returned ${confirmedCandidates.size} newly confirmed candidates")

        // Log tracker stats
        val stats = objectTracker.getStats()
        Log.i(TAG, ">>> Tracker stats: active=${stats.activeCandidates}, confirmed=${stats.confirmedCandidates}, frame=${stats.currentFrame}")

        // Convert confirmed candidates to ScannedItems
        val items = confirmedCandidates.map { candidate ->
            objectDetector.candidateToScannedItem(candidate)
        }

        Log.i(TAG, ">>> processObjectDetectionWithTracking: Converted to ${items.size} ScannedItems")
        items.forEachIndexed { index, item ->
            Log.i(TAG, "    ScannedItem $index: id=${item.id}, category=${item.category}, priceRange=${item.priceRange}")
        }

        Log.i(TAG, ">>> processObjectDetectionWithTracking: RETURNING ${items.size} items")
        return items
    }

    /**
     * Converts ImageProxy to Bitmap.
     */
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        // NV21 format: Y + VU
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 90, out)
        val jpegBytes = out.toByteArray()

        return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Rotates a bitmap by the specified degrees.
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap

        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())

        return Bitmap.createBitmap(
            bitmap, 0, 0,
            bitmap.width, bitmap.height,
            matrix, true
        )
    }

    /**
     * Cleanup resources.
     */
    fun shutdown() {
        stopScanning()
        objectTracker.reset() // Ensure tracker is cleaned up
        objectDetector.close()
        barcodeScanner.close()
        textRecognizer.close()
        cameraExecutor.shutdown()
        detectionScope.cancel()
    }

    /**
     * Resets the object tracker.
     * Useful when the user wants to start a fresh scan session.
     */
    fun resetTracker() {
        Log.i(TAG, "resetTracker: Manually resetting object tracker")
        objectTracker.reset()
    }
}

/**
 * Suspending function to await ProcessCameraProvider.
 */
private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider {
    return suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                continuation.resume(future.get(), null)
            } catch (e: Exception) {
                continuation.cancel(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
