package com.scanium.app.camera

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.YuvImage
import android.net.Uri
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.util.Size
import android.util.Rational
import androidx.camera.core.*
import androidx.camera.core.CameraUnavailableException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.scanium.app.items.ScannedItem
import com.scanium.app.ml.BarcodeScannerClient
import com.scanium.app.ml.DetectionResult
import com.scanium.app.ml.DocumentTextRecognitionClient
import com.scanium.app.ml.ObjectDetectorClient
import com.scanium.app.tracking.ObjectTracker
import com.scanium.app.tracking.TrackerConfig
import com.scanium.android.platform.adapters.toNormalizedRect
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

        // PHASE 3: Edge gating margin
        // Detections whose center falls within this margin from the cropRect edge are dropped
        // This prevents partial/cut-off objects at screen edges from being promoted
        // Range: 0.0 (no margin) to 0.5 (very aggressive filtering)
        // Default: 0.10 (10% inset from each edge)
        private const val EDGE_INSET_MARGIN_RATIO = 0.10f
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
    private var imageCapture: ImageCapture? = null

    private val objectDetector = ObjectDetectorClient()
    private val barcodeScanner = BarcodeScannerClient()
    private val textRecognizer = DocumentTextRecognitionClient()

    private val _analysisFps = MutableStateFlow(0.0)
    /** Real-time analysis FPS for performance monitoring */
    val analysisFps: StateFlow<Double> = _analysisFps.asStateFlow()

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

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            shutdown()
        }
    }

    init {
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
    }

    // State for scanning mode
    private var isScanning = false
    private var scanJob: Job? = null
    private var currentScanMode: ScanMode? = null
    private var targetRotation: Int? = null

    // Frame counter for periodic cleanup and stats logging
    private var frameCounter = 0

    // Model initialization flag
    private var modelInitialized = false

    // Performance metrics tracking
    private var sessionStartTime = 0L
    private var totalFramesProcessed = 0
    private var lastFpsReportTime = 0L
    private var framesInWindow = 0

    // PHASE 5: Rate-limited logging
    private var viewportLoggedOnce = false
    private var lastCropRectLogTime = 0L
    private val cropRectLogIntervalMs = 5000L // Log cropRect info every 5 seconds

    // PHASE 2: Store PreviewView dimensions for calculating visible viewport
    private var previewViewWidth = 0
    private var previewViewHeight = 0

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
        lensFacing: Int,
        captureResolution: CaptureResolution = CaptureResolution.DEFAULT
    ): CameraBindResult = withContext(Dispatchers.Main) {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            Log.e(TAG, "No camera hardware detected on this device")
            return@withContext CameraBindResult(
                success = false,
                lensFacingUsed = lensFacing,
                error = IllegalStateException("No camera hardware available")
            )
        }

        // Ensure models are downloaded before starting camera
        ensureModelsReady()

        // Store PreviewView dimensions for viewport calculation
        previewViewWidth = previewView.width
        previewViewHeight = previewView.height

        val provider = awaitCameraProvider(context)
        cameraProvider = provider

        if (provider.availableCameraInfos.isEmpty()) {
            Log.e(TAG, "Camera provider returned no available camera infos")
            return@withContext CameraBindResult(
                success = false,
                lensFacingUsed = lensFacing,
                error = IllegalStateException("No available camera from provider")
            )
        }

        // Setup preview
        preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        // Setup image analysis for object detection
        val displayRotation = previewView.display.rotation
        targetRotation = displayRotation

        imageAnalysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(displayRotation)
            .setTargetResolution(android.util.Size(1280, 720)) // Higher resolution for better detection
            .build()

        Log.d(TAG, "ImageAnalysis configured with target resolution 1280x720")

        // Setup high-resolution image capture for saving high-quality item images
        imageCapture = buildImageCapture(captureResolution, displayRotation)
        Log.d(TAG, "ImageCapture configured for resolution: $captureResolution (rotation=$displayRotation)")

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

            // PHASE 2: Bind use cases to lifecycle
            // NOTE: We do NOT apply ViewPort to ImageAnalysis - ML Kit needs full frame context
            // for accurate classification. Instead, we filter detections geometrically AFTER
            // ML Kit analysis using cropRect from Preview's ViewPort.
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                selectorToUse,
                preview,
                imageAnalysis,
                imageCapture
            )

            // PHASE 5: Configuration log
            if (!viewportLoggedOnce) {
                Log.i(TAG, "[CONFIG] Camera bound: Preview=${previewView.width}x${previewView.height}, rotation=$displayRotation, edgeInset=${EDGE_INSET_MARGIN_RATIO}")
                Log.i(TAG, "[CONFIG] ML Kit sees full frame for classification; geometry filtering applied after detection")
                viewportLoggedOnce = true
            }
            CameraBindResult(success = true, lensFacingUsed = resolvedLensFacing)
        } catch (e: CameraUnavailableException) {
            Log.e(TAG, "Camera unavailable during binding", e)
            CameraBindResult(success = false, lensFacingUsed = resolvedLensFacing, error = e)
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

        // Initialize performance metrics
        sessionStartTime = SystemClock.elapsedRealtime()
        totalFramesProcessed = 0
        lastFpsReportTime = sessionStartTime
        framesInWindow = 0

        var lastAnalysisTime = 0L
        var isProcessing = false // Prevent overlapping processing

        // Log initial configuration
        val initialIntervalMs = 1000L / com.scanium.app.ml.classification.ClassifierDebugFlags.analysisFps
        com.scanium.app.ml.DetectionLogger.logConfiguration(
            minSeenCount = 1,
            minConfidence = 0.0f,
            candidateTimeoutMs = 3000L,
            analysisIntervalMs = initialIntervalMs
        )

        Log.i(TAG, "[METRICS] Camera session started, tracking analyzer latency and frame rate")

        // Clear any previous analyzer to avoid stale callbacks
        imageAnalysis?.clearAnalyzer()

        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            if (!isScanning) {
                Log.d(TAG, "startScanning: Not scanning anymore, closing proxy")
                imageProxy.close()
                return@setAnalyzer
            }

            // Dynamic throttling based on debug flags
            val analysisIntervalMs = 1000L / com.scanium.app.ml.classification.ClassifierDebugFlags.analysisFps
            val currentTime = System.currentTimeMillis()

            // Only process if enough time has passed AND we're not already processing
            if (currentTime - lastAnalysisTime >= analysisIntervalMs && !isProcessing) {
                lastAnalysisTime = currentTime
                isProcessing = true
                frameCounter++

                // [METRICS] Start analyzer latency measurement
                val frameReceiveTime = SystemClock.elapsedRealtime()
                Trace.beginSection("CameraXManager.analyzeFrame")

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

                        // [METRICS] Calculate analyzer latency
                        val analyzerLatencyMs = SystemClock.elapsedRealtime() - frameReceiveTime
                        totalFramesProcessed++
                        framesInWindow++

                        Log.i(TAG, "[METRICS] Frame ***REMOVED***$frameCounter analyzer latency: ${analyzerLatencyMs}ms")

                        // [METRICS] Calculate and log frame rate every 1 second
                        val now = SystemClock.elapsedRealtime()
                        val timeSinceLastReport = now - lastFpsReportTime
                        if (timeSinceLastReport >= 1000L) {
                            val fps = (framesInWindow * 1000.0) / timeSinceLastReport
                            val avgFps = (totalFramesProcessed * 1000.0) / (now - sessionStartTime)
                            
                            _analysisFps.value = fps
                            
                            // Log only every 5 seconds to reduce spam
                            if (totalFramesProcessed % 50 == 0) {
                                Log.i(TAG, "[METRICS] Frame rate: current=${String.format("%.2f", fps)} fps, " +
                                    "session_avg=${String.format("%.2f", avgFps)} fps, total_frames=$totalFramesProcessed")
                            }
                            
                            lastFpsReportTime = now
                            framesInWindow = 0
                        }

                        // Periodic stats logging (every 10 frames)
                        if (frameCounter % 10 == 0) {
                            val stats = objectTracker.getStats()
                            Log.i(TAG, "Tracker stats: active=${stats.activeCandidates}, confirmed=${stats.confirmedCandidates}, frame=${stats.currentFrame}")
                        }
                    } finally {
                        Trace.endSection()
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
        detectionScope.coroutineContext.cancelChildren()
        imageAnalysis?.clearAnalyzer()
        scanJob?.cancel()
        scanJob = null
        // Reset tracker when stopping scan
        objectTracker.reset()
        currentScanMode = null
    }

    /**
     * Updates target rotation for capture and analysis when the display orientation changes.
     */
    fun updateTargetRotation(rotation: Int) {
        if (targetRotation == rotation) return
        targetRotation = rotation
        imageCapture?.targetRotation = rotation
        imageAnalysis?.targetRotation = rotation
        Log.d(TAG, "Updated target rotation to $rotation")
    }

    /**
     * Processes an ImageProxy: converts to Bitmap and runs ML Kit detection based on scan mode.
     * Used for single-frame captures (bypasses candidate tracking).
     *
     * OPTIMIZATION: Bitmap creation is deferred until we know detections exist (lazy generation).
     * This reduces memory allocations and GC pressure on frames with no detections.
     */
    private suspend fun processImageProxy(
        imageProxy: ImageProxy,
        scanMode: ScanMode,
        useStreamMode: Boolean = false
    ): Pair<List<ScannedItem>, List<DetectionResult>> {
        var cachedBitmap: Bitmap? = null
        return try {
            Log.i(TAG, ">>> processImageProxy: START - scanMode=$scanMode, useStreamMode=$useStreamMode, isScanning=$isScanning")

            // Get MediaImage from ImageProxy
            val mediaImage = imageProxy.image ?: run {
                Log.e(TAG, "processImageProxy: mediaImage is null")
                return Pair(emptyList(), emptyList())
            }
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            // PHASE 3: Calculate visible viewport based on PreviewView aspect ratio
            // Apply cropRect to ImageProxy so ML Kit ONLY analyzes the visible region
            val cropRect = calculateVisibleViewport(
                imageWidth = imageProxy.width,
                imageHeight = imageProxy.height,
                previewWidth = previewViewWidth,
                previewHeight = previewViewHeight
            )

            // CRITICAL: Set cropRect on ImageProxy BEFORE creating InputImage
            // This is metadata-only (zero-copy) and ensures ML Kit only sees the visible viewport
            imageProxy.setCropRect(cropRect)

            // PHASE 5: Rate-limited viewport logging
            val now = System.currentTimeMillis()
            if (now - lastCropRectLogTime >= cropRectLogIntervalMs) {
                Log.i(TAG, "[VIEWPORT] image=${imageProxy.width}x${imageProxy.height}, rotation=$rotationDegrees, appliedCrop=$cropRect")
                lastCropRectLogTime = now
            }

            // Build ML Kit image from camera buffer with applied cropRect
            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            // OPTIMIZATION: Lazy bitmap provider - only creates bitmap when invoked
            // IMPORTANT: Do NOT rotate the bitmap! ML Kit's InputImage already has rotation
            // metadata, so bounding boxes will be in the original (unrotated) coordinate space.
            // Rotating the bitmap would cause a coordinate mismatch when cropping thumbnails.
            val lazyBitmapProvider: () -> Bitmap? = {
                if (cachedBitmap == null) {
                    cachedBitmap = runCatching {
                        val bitmap = imageProxy.toBitmap()
                        Log.i(TAG, ">>> processImageProxy: [LAZY] Created bitmap ${bitmap.width}x${bitmap.height}, rotation=$rotationDegrees")
                        bitmap // Keep original orientation to match ML Kit's coordinate space
                    }.getOrElse { e ->
                        Log.w(TAG, "processImageProxy: Failed to create bitmap", e)
                        null
                    }
                }
                cachedBitmap
            }

            // Since ImageProxy is already cropped to visible viewport via setCropRect(),
            // pass full image bounds for edge inset filtering (filters detections too close to edges)
            val imageBoundsForFiltering = android.graphics.Rect(0, 0, cropRect.width(), cropRect.height())

            // Route to the appropriate scanner based on mode
            when (scanMode) {
                ScanMode.OBJECT_DETECTION -> {
                    // Use tracking pipeline when in STREAM_MODE and scanning
                    if (useStreamMode && isScanning) {
                        Log.i(TAG, ">>> processImageProxy: Taking TRACKING PATH (useStreamMode=$useStreamMode, isScanning=$isScanning)")
                        val (items, detections) = processObjectDetectionWithTracking(
                            inputImage = inputImage,
                            lazyBitmapProvider = lazyBitmapProvider,
                            cropRect = imageBoundsForFiltering,
                            edgeInsetRatio = EDGE_INSET_MARGIN_RATIO
                        )
                        Log.i(TAG, ">>> processImageProxy: Tracking path returned ${items.size} items and ${detections.size} detection results")
                        Pair(items, detections)
                    } else {
                        // Single-shot detection without tracking
                        Log.i(TAG, ">>> processImageProxy: Taking SINGLE-SHOT PATH (useStreamMode=$useStreamMode, isScanning=$isScanning)")
                        val response = objectDetector.detectObjects(
                            image = inputImage,
                            sourceBitmap = lazyBitmapProvider,
                            useStreamMode = useStreamMode,
                            cropRect = imageBoundsForFiltering,
                            edgeInsetRatio = EDGE_INSET_MARGIN_RATIO
                        )
                        Log.i(TAG, ">>> processImageProxy: Single-shot path returned ${response.scannedItems.size} items")
                        Pair(response.scannedItems, response.detectionResults)
                    }
                }
                ScanMode.BARCODE -> {
                    // Barcode and text scanners don't return DetectionResults yet
                    val items = barcodeScanner.scanBarcodes(
                        image = inputImage,
                        sourceBitmap = lazyBitmapProvider
                    )
                    Pair(items, emptyList())
                }
                ScanMode.DOCUMENT_TEXT -> {
                    val items = textRecognizer.recognizeText(
                        image = inputImage,
                        sourceBitmap = lazyBitmapProvider
                    )
                    Pair(items, emptyList())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, ">>> processImageProxy: ERROR", e)
            Pair(emptyList(), emptyList())
        } finally {
            cachedBitmap?.let { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
                cachedBitmap = null
            }
            imageProxy.close()
        }
    }

    /**
     * Process object detection with tracking to reduce duplicates.
     * Uses a SINGLE detection pass to generate both tracking data and overlay data.
     */
    private suspend fun processObjectDetectionWithTracking(
        inputImage: InputImage,
        lazyBitmapProvider: () -> Bitmap?,
        cropRect: android.graphics.Rect,
        edgeInsetRatio: Float
    ): Pair<List<ScannedItem>, List<DetectionResult>> {
        Log.i(TAG, ">>> processObjectDetectionWithTracking: CALLED")

        // SINGLE DETECTION PASS: Get both tracking metadata and overlay data together
        val trackingResponse = objectDetector.detectObjectsWithTracking(
            image = inputImage,
            sourceBitmap = lazyBitmapProvider,
            useStreamMode = true,
            cropRect = cropRect,
            edgeInsetRatio = edgeInsetRatio
        )

        Log.i(TAG, ">>> processObjectDetectionWithTracking: Got ${trackingResponse.detectionInfos.size} DetectionInfo objects and ${trackingResponse.detectionResults.size} DetectionResult objects from a SINGLE detection pass")

        // Process detections through tracker
        val confirmedCandidates = objectTracker.processFrame(trackingResponse.detectionInfos)

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

        Log.i(TAG, ">>> processObjectDetectionWithTracking: RETURNING ${items.size} items and ${trackingResponse.detectionResults.size} detection results")
        return Pair(items, trackingResponse.detectionResults)
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
     * Builds an ImageCapture use case configured for the specified resolution.
     */
    private fun buildImageCapture(resolution: CaptureResolution, rotation: Int): ImageCapture {
        val builder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetRotation(rotation)

        // Configure target resolution based on setting
        val targetSize = when (resolution) {
            CaptureResolution.LOW -> android.util.Size(1280, 720)      // HD
            CaptureResolution.NORMAL -> android.util.Size(1920, 1080)  // Full HD
            CaptureResolution.HIGH -> android.util.Size(3840, 2160)    // 4K
        }

        builder.setTargetResolution(targetSize)

        return builder.build()
    }

    /**
     * Captures a high-resolution image to a file and returns the URI.
     * This is used for storing the full-quality source image for items.
     *
     * @return Uri of the saved image file, or null if capture failed
     */
    suspend fun captureHighResImage(): Uri? = withContext(Dispatchers.IO) {
        val capture = imageCapture ?: run {
            Log.e(TAG, "captureHighResImage: ImageCapture not initialized")
            return@withContext null
        }

        try {
            // Create output file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
            val photoFile = File(
                context.cacheDir,
                "SCANIUM_${timestamp}.jpg"
            )

            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            // Capture image
            val result = suspendCancellableCoroutine<ImageCapture.OutputFileResults> { continuation ->
                capture.takePicture(
                    outputOptions,
                    cameraExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            continuation.resume(outputFileResults)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            continuation.resumeWithException(exception)
                        }
                    }
                )
            }

            val savedUri = result.savedUri ?: Uri.fromFile(photoFile)
            Log.i(TAG, "High-res image captured: $savedUri (size: ${photoFile.length()} bytes)")
            savedUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture high-res image", e)
            null
        }
    }

    /**
     * Cleanup resources.
     */
    fun shutdown() {
        stopScanning()
        lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
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

    /**
     * PHASE 3: Calculate the visible viewport rect based on PreviewView aspect ratio.
     * This determines which portion of the full camera frame is actually visible to the user,
     * allowing us to filter detections while letting ML Kit see the full frame for classification.
     *
     * Uses center-crop logic to match CameraX's default scaling.
     */
    private fun calculateVisibleViewport(
        imageWidth: Int,
        imageHeight: Int,
        previewWidth: Int,
        previewHeight: Int
    ): android.graphics.Rect {
        if (previewWidth == 0 || previewHeight == 0) {
            // Fallback: return full frame if preview dimensions not available
            return android.graphics.Rect(0, 0, imageWidth, imageHeight)
        }

        // Calculate aspect ratios
        val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
        val previewAspect = previewWidth.toFloat() / previewHeight.toFloat()

        val cropRect = if (imageAspect > previewAspect) {
            // Image is wider than preview - crop sides (center crop horizontally)
            val visibleWidth = (imageHeight * previewAspect).toInt()
            val cropX = (imageWidth - visibleWidth) / 2
            android.graphics.Rect(cropX, 0, cropX + visibleWidth, imageHeight)
        } else {
            // Image is taller than preview - crop top/bottom (center crop vertically)
            val visibleHeight = (imageWidth / previewAspect).toInt()
            val cropY = (imageHeight - visibleHeight) / 2
            android.graphics.Rect(0, cropY, imageWidth, cropY + visibleHeight)
        }

        return cropRect
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
