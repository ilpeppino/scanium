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
import androidx.camera.core.*
import androidx.camera.core.CameraUnavailableException
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.scanium.app.BuildConfig
import com.scanium.app.camera.detection.DetectionEvent
import com.scanium.app.camera.detection.DetectionRouter
import com.scanium.app.camera.detection.DetectionRouterConfig
import com.scanium.app.camera.detection.DetectorType
import com.scanium.app.camera.detection.DocumentCandidate
import com.scanium.app.camera.detection.DocumentCandidateDetector
import com.scanium.app.camera.detection.DocumentCandidateState
import com.scanium.app.camera.detection.ScanPipelineDiagnostics
import com.scanium.app.items.ScannedItem
import com.scanium.app.ml.BarcodeDetectorClient
import com.scanium.app.ml.DetectionResult
import com.scanium.app.ml.DocumentTextRecognitionClient
import com.scanium.app.ml.ObjectDetectorClient
import com.scanium.app.perf.PerformanceMonitor
import com.scanium.app.tracking.ObjectTracker
import com.scanium.app.tracking.TrackerConfig
import com.scanium.core.models.scanning.GuidanceState
import com.scanium.core.models.scanning.ScanGuidanceState
import com.scanium.core.models.scanning.ScanRoi
import com.scanium.core.tracking.CandidateInfo
import com.scanium.core.tracking.ScanGuidanceManager
import com.scanium.telemetry.facade.Telemetry
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
@OptIn(ExperimentalGetImage::class)
class CameraXManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val telemetry: Telemetry? = null,
) {
    companion object {
        private const val TAG = "CameraXManager"
        private const val CAM_FRAME_TAG = "CAM_FRAME"
        private const val DEFAULT_MOTION_SCORE = 0.2
        private const val LUMA_SAMPLE_STEP = 8
        private const val DOCUMENT_CANDIDATE_TTL_MS = 800L
        private const val DOCUMENT_CANDIDATE_MIN_CONFIDENCE = 0.45f

        // PHASE 3: Edge gating margin
        // Detections whose center falls within this margin from the cropRect edge are dropped
        // This prevents partial/cut-off objects at screen edges from being promoted
        // Range: 0.0 (no margin) to 0.5 (very aggressive filtering)
        // Default: 0.10 (10% inset from each edge)
        private const val EDGE_INSET_MARGIN_RATIO = 0.10f

        // NO_FRAMES watchdog timeouts
        private const val WATCHDOG_INITIAL_DELAY_MS = 600L
        private const val WATCHDOG_RETRY_DELAY_MS = 800L
        private const val MAX_RECOVERY_ATTEMPTS = 2
    }

    data class CameraBindResult(
        val success: Boolean,
        val lensFacingUsed: Int,
        val error: Throwable? = null,
    )

    @Volatile
    private var cameraProvider: ProcessCameraProvider? = null

    @Volatile
    private var camera: Camera? = null

    @Volatile
    private var preview: Preview? = null

    @Volatile
    private var imageAnalysis: ImageAnalysis? = null

    @Volatile
    private var imageCapture: ImageCapture? = null

    private val objectDetector = ObjectDetectorClient()
    private val barcodeDetector = BarcodeDetectorClient()
    private val textRecognizer = DocumentTextRecognitionClient()

    // Detection router for throttling and future multi-detector orchestration
    private val detectionRouter =
        DetectionRouter(
            config =
                DetectionRouterConfig(
                    throttleIntervals =
                        mapOf(
                            DetectorType.DOCUMENT to 400L,
                        ),
                    enableVerboseLogging = BuildConfig.DEBUG,
                    enableDebugLogging = BuildConfig.DEBUG,
                ),
        )

    private val documentCandidateDetector = DocumentCandidateDetector()

    // Scan guidance manager for coordinated UX
    private val scanGuidanceManager = ScanGuidanceManager()

    private val _analysisFps = MutableStateFlow(0.0)

    /** Real-time analysis FPS for performance monitoring */
    val analysisFps: StateFlow<Double> = _analysisFps.asStateFlow()

    private val _scanGuidanceState = MutableStateFlow(ScanGuidanceState.initial())

    /** Current scan guidance state for UI overlay */
    val scanGuidanceState: StateFlow<ScanGuidanceState> = _scanGuidanceState.asStateFlow()

    private val _scanDiagnosticsEnabled = MutableStateFlow(false)

    /** Whether scan diagnostics overlay should be shown */
    val scanDiagnosticsEnabled: StateFlow<Boolean> = _scanDiagnosticsEnabled.asStateFlow()

    private val _documentCandidateState = MutableStateFlow<DocumentCandidateState?>(null)
    val documentCandidateState: StateFlow<DocumentCandidateState?> = _documentCandidateState.asStateFlow()

    // Object tracker for de-duplication
    // Using very permissive thresholds to ensure items are actually promoted
    private val objectTracker =
        ObjectTracker(
            config =
                TrackerConfig(
                    minFramesToConfirm = 1,
// Confirm immediately (rely on session-level dedup)
                    minConfidence = 0.2f,
// Very low confidence threshold (20%)
                    minBoxArea = 0.0005f,
// Very small box area (0.05% of frame)
                    maxFrameGap = 8,
// Allow 8 frames gap for matching (more forgiving)
                    minMatchScore = 0.2f,
// Lower match score threshold for better spatial matching
                    expiryFrames = 15,
// Keep candidates longer (15 frames)
                    enableVerboseLogging = BuildConfig.DEBUG,
                ),
            telemetry = telemetry,
        )

    // Executor for camera operations
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Coroutine scope for async detection - recreated on each session start
    @Volatile
    private var detectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Session controller for lifecycle management and diagnostics
    private val sessionController = CameraSessionController()

    /** Exposed diagnostics for debug overlay */
    val pipelineDiagnostics: StateFlow<CameraPipelineDiagnostics> = sessionController.diagnostics

    // NO_FRAMES watchdog state
    @Volatile
    private var watchdogJob: Job? = null

    @Volatile
    private var recoveryAttempts = 0

    // Track first frame received in current session for analysisFlowing
    @Volatile
    private var hasReceivedFirstFrame = false

    // Pending preview detection callback - set when startPreviewDetection() is called before camera bind
    @Volatile
    private var pendingPreviewDetectionCallback: PreviewDetectionCallback? = null

    // Callback holder for preview detection
    private data class PreviewDetectionCallback(
        val onDetectionResult: (List<DetectionResult>) -> Unit,
        val onFrameSize: (Size) -> Unit,
        val onRotation: (Int) -> Unit,
    )

    private val lifecycleObserver =
        object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                shutdown()
            }
        }

    init {
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
    }

    // =========================================================================
    // Session Management - Key fix for frozen bboxes
    // =========================================================================

    /**
     * Starts a new camera session.
     *
     * Call this when the camera screen becomes active (ON_RESUME).
     * Creates a new detection scope and invalidates old callbacks.
     *
     * @return The new session ID for callback validation.
     */
    fun startCameraSession(): Int {
        sessionController.logEvent("START_SESSION", "recreating scope")

        // Cancel watchdog from previous session
        watchdogJob?.cancel()
        watchdogJob = null

        // Cancel the old scope completely and create a fresh one
        detectionScope.cancel()
        detectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        // Reset session-level state
        hasReceivedFirstFrame = false
        recoveryAttempts = 0

        val sessionId = sessionController.startSession()

        // Update diagnostic states
        sessionController.updateAnalysisAttached(false)
        sessionController.updateAnalysisFlowing(false)
        sessionController.updateStallReason(StallReason.NONE)
        sessionController.updateRecoveryAttempts(0)

        return sessionId
    }

    /**
     * Stops the current camera session.
     *
     * Call this when the camera screen becomes inactive (ON_PAUSE, background).
     * Cancels the detection scope and clears the analyzer.
     */
    fun stopCameraSession() {
        sessionController.logEvent("STOP_SESSION", "isScanning=$isScanning, isPreviewDetectionActive=$isPreviewDetectionActive")

        // Cancel watchdog
        watchdogJob?.cancel()
        watchdogJob = null

        // Stop all detection modes
        if (isScanning) {
            stopScanning()
        }
        if (isPreviewDetectionActive) {
            isPreviewDetectionActive = false
        }

        // Clear any pending callbacks
        pendingPreviewDetectionCallback = null

        // Clear the analyzer to stop frame processing
        imageAnalysis?.clearAnalyzer()

        // Cancel the scope to stop any in-flight coroutines
        detectionScope.cancel()

        // Reset session-level state
        hasReceivedFirstFrame = false
        recoveryAttempts = 0

        // Update session state
        sessionController.stopSession()
        sessionController.updateAnalysisRunning(false)
        sessionController.updateAnalysisAttached(false)
        sessionController.updateAnalysisFlowing(false)
        sessionController.updateStallReason(StallReason.NONE)
    }

    /**
     * Check if the current session is still valid.
     * Use this to ignore callbacks from stale sessions.
     */
    fun isCurrentSessionValid(sessionId: Int): Boolean = sessionController.isSessionValid(sessionId)

    /**
     * Get the current session ID.
     */
    fun getCurrentSessionId(): Int = sessionController.getCurrentSessionId()

    /**
     * Update diagnostics for debug overlay.
     */
    fun updateDiagnosticsLifecycleState(state: String) {
        sessionController.updateLifecycleState(state)
    }

    fun updateDiagnosticsNavDestination(destination: String) {
        sessionController.updateNavDestination(destination)
    }

    // State for scanning mode
    // These variables are accessed from both Main thread (UI) and camera executor thread
    @Volatile
    private var isScanning = false

    @Volatile
    private var isPreviewDetectionActive = false
    private var scanJob: Job? = null

    @Volatile
    private var currentScanMode: ScanMode? = null

    @Volatile
    private var targetRotation: Int? = null

    // Frame counter for periodic cleanup and stats logging
    @Volatile
    private var frameCounter = 0

    // Model initialization flag
    @Volatile
    private var modelInitialized = false

    // Performance metrics tracking
    private var sessionStartTime = 0L
    private var totalFramesProcessed = 0
    private var lastFpsReportTime = 0L
    private var framesInWindow = 0

    // Motion-aware analysis interval
    private var lastMotionScore = DEFAULT_MOTION_SCORE

    // Double-buffered luma samples for motion detection
    // Pre-allocated to avoid per-frame GC pressure (~14KB per buffer at 1280x720 with step=8)
    private val lumaSampleBuffers = arrayOfNulls<ByteArray>(2)
    private var currentLumaBufferIndex = 0
    private var lumaBufferSize = 0
    private var hasValidPreviousLumaSample = false

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
        captureResolution: CaptureResolution = CaptureResolution.DEFAULT,
    ): CameraBindResult =
        withContext(Dispatchers.Main) {
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                Log.e(TAG, "No camera hardware detected on this device")
                return@withContext CameraBindResult(
                    success = false,
                    lensFacingUsed = lensFacing,
                    error = IllegalStateException("No camera hardware available"),
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
                    error = IllegalStateException("No available camera from provider"),
                )
            }

            // Setup preview
            preview =
                Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

            // Setup image analysis for object detection
            val displayRotation = previewView.display.rotation
            targetRotation = displayRotation

            imageAnalysis =
                ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(displayRotation)
                    .setTargetResolution(android.util.Size(1280, 720)) // Higher resolution for better detection
                    .build()

            Log.d(TAG, "ImageAnalysis configured with target resolution 1280x720")

            // Setup high-resolution image capture for saving high-quality item images
            imageCapture = buildImageCapture(captureResolution, displayRotation)
            Log.d(TAG, "ImageCapture configured for resolution: $captureResolution (rotation=$displayRotation)")

            val requestedSelector =
                CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()
            val fallbackLensFacing =
                if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            val fallbackSelector =
                CameraSelector.Builder()
                    .requireLensFacing(fallbackLensFacing)
                    .build()

            val selectorToUse =
                when {
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
                            error = IllegalStateException("No available camera for requested or fallback lens"),
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
                camera =
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        selectorToUse,
                        preview,
                        imageAnalysis,
                        imageCapture,
                    )

                // PHASE 5: Configuration log
                if (!viewportLoggedOnce) {
                    Log.i(
                        TAG,
                        "[CONFIG] Camera bound: Preview=${previewView.width}x${previewView.height}, rotation=$displayRotation, edgeInset=${EDGE_INSET_MARGIN_RATIO}",
                    )
                    Log.i(TAG, "[CONFIG] ML Kit sees full frame for classification; geometry filtering applied after detection")
                    viewportLoggedOnce = true
                }

                // Update diagnostics
                sessionController.updateCameraBound(true)
                sessionController.logEvent("CAMERA_BIND_SUCCESS", "lens=$resolvedLensFacing")

                // CRITICAL FIX: Apply any pending preview detection callback AFTER camera bind
                // This ensures the analyzer is set on the newly created imageAnalysis instance
                pendingPreviewDetectionCallback?.let { callback ->
                    Log.i(CAM_FRAME_TAG, "Applying pending preview detection callback after camera bind")
                    pendingPreviewDetectionCallback = null
                    applyPreviewDetectionAnalyzer(
                        onDetectionResult = callback.onDetectionResult,
                        onFrameSize = callback.onFrameSize,
                        onRotation = callback.onRotation,
                    )
                }

                // Start NO_FRAMES watchdog to detect analyzer stalls
                startNoFramesWatchdog()

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
        onDetectionEvent: (DetectionEvent) -> Unit = {},
        onFrameSize: (Size) -> Unit = {},
        onRotation: (Int) -> Unit = {},
    ) {
        Log.d(TAG, "captureSingleFrame: Starting single frame capture with mode $scanMode")
        // Stop preview detection if running (capture takes over temporarily)
        if (isPreviewDetectionActive) {
            Log.d(TAG, "captureSingleFrame: Stopping preview detection for capture")
            isPreviewDetectionActive = false
        }
        // Clear any previous analyzer to avoid mixing modes
        imageAnalysis?.clearAnalyzer()
        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return@setAnalyzer
            }

            // Apply viewport crop consistently
            val cropRect =
                calculateVisibleViewport(
                    imageWidth = imageProxy.width,
                    imageHeight = imageProxy.height,
                    previewWidth = previewViewWidth,
                    previewHeight = previewViewHeight,
                )
            imageProxy.setCropRect(cropRect)

            detectionScope.launch {
                try {
                    // CRITICAL FIX: Report FULL IMAGE dimensions, not cropRect.
                    // ML Kit's InputImage.fromMediaImage() does NOT honor cropRect - it processes
                    // the full MediaImage buffer and returns bounding boxes in full image coordinates.
                    val frameSize = Size(imageProxy.width, imageProxy.height)
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    withContext(Dispatchers.Main) {
                        onFrameSize(frameSize)
                        onRotation(rotationDegrees)
                    }

                    // Single-frame capture uses direct detection (no candidate tracking)
                    val (items, detections) =
                        processImageProxy(
                            imageProxy = imageProxy,
                            scanMode = scanMode,
                            useStreamMode = false,
                            onDetectionEvent = onDetectionEvent,
                        )
                    Log.d(TAG, "captureSingleFrame: Got ${items.size} items")
                    withContext(Dispatchers.Main) {
                        onResult(items)
                        onDetectionResult(detections)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in captureSingleFrame coroutine", e)
                } finally {
                    // processImageProxy already closes the proxy, but we should be careful.
                    // Actually, processImageProxy has a finally { imageProxy.close() } block.
                    imageAnalysis?.clearAnalyzer()
                }
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
        onDetectionEvent: (DetectionEvent) -> Unit = {},
        onFrameSize: (Size) -> Unit = {},
        onRotation: (Int) -> Unit = {},
    ) {
        if (isScanning) {
            Log.d(TAG, "startScanning: Already scanning, ignoring")
            return
        }

        // Stop preview detection if running (scanning takes over)
        if (isPreviewDetectionActive) {
            Log.d(TAG, "startScanning: Stopping preview detection before starting scan")
            isPreviewDetectionActive = false
            sessionController.updatePreviewDetectionActive(false)
        }

        Log.d(TAG, "startScanning: Starting continuous scanning mode with $scanMode")

        // CRITICAL FIX: Ensure scope is ready before starting
        if (!detectionScope.isActive) {
            sessionController.logEvent("SCOPE_RECREATE", "detectionScope was inactive for scanning, recreating")
            detectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        }

        sessionController.updateScanningActive(true)
        sessionController.updateAnalysisRunning(true)
        sessionController.updateAnalysisAttached(true)
        sessionController.updateAnalysisFlowing(false) // Will be set true on first frame

        // Reset first frame flag for this scanning session
        hasReceivedFirstFrame = false

        // Reset tracker and guidance when starting a new scan session or switching modes
        if (currentScanMode != scanMode || currentScanMode == null) {
            Log.i(TAG, "startScanning: Resetting tracker (mode change: $currentScanMode -> $scanMode)")
            objectTracker.reset(scanMode = scanMode.name)
            scanGuidanceManager.reset()
            _scanGuidanceState.value = ScanGuidanceState.initial()
            currentScanMode = scanMode
        }

        isScanning = true
        frameCounter = 0

        // Start detection router session for metrics tracking
        detectionRouter.startSession()

        // Start diagnostics session
        ScanPipelineDiagnostics.startSession()

        // Initialize performance metrics
        sessionStartTime = SystemClock.elapsedRealtime()
        totalFramesProcessed = 0
        lastFpsReportTime = sessionStartTime
        framesInWindow = 0
        lastMotionScore = DEFAULT_MOTION_SCORE
        // Note: lumaSampleBuffers are intentionally NOT reset here to avoid
        // per-session allocations. They are reused across sessions and only
        // reallocated if the camera resolution changes.

        var lastAnalysisTime = 0L
        var isProcessing = false // Prevent overlapping processing

        // Log initial configuration
        val initialIntervalMs = analysisIntervalMsForMotion(lastMotionScore)
        com.scanium.app.ml.DetectionLogger.logConfiguration(
            minSeenCount = 1,
            minConfidence = 0.0f,
            candidateTimeoutMs = 3000L,
            analysisIntervalMs = initialIntervalMs,
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

            // Dynamic throttling based on motion
            val motionScore = computeMotionScore(imageProxy)
            val analysisIntervalMs = analysisIntervalMsForMotion(motionScore)
            val currentTime = System.currentTimeMillis()
            val timeSinceLastAnalysis = currentTime - lastAnalysisTime
            val willProcess = timeSinceLastAnalysis >= analysisIntervalMs && !isProcessing

            // Log frame arrival with throttle decision
            ScanPipelineDiagnostics.logFrameArrival(
                motionScore = motionScore,
                analysisIntervalMs = analysisIntervalMs,
                timeSinceLastAnalysis = timeSinceLastAnalysis,
                willProcess = willProcess,
                dropReason =
                    when {
                        isProcessing -> "already_processing"
                        timeSinceLastAnalysis < analysisIntervalMs -> "throttled_motion"
                        else -> null
                    },
            )

            // Only process if enough time has passed AND we're not already processing
            if (willProcess) {
                lastAnalysisTime = currentTime
                isProcessing = true
                frameCounter++

                // [METRICS] Start analyzer latency measurement
                val frameReceiveTime = SystemClock.elapsedRealtime()
                Trace.beginSection("CameraXManager.analyzeFrame")

                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    imageProxy.close()
                    isProcessing = false
                    return@setAnalyzer
                }

                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val inputImage =
                    com.google.mlkit.vision.common.InputImage.fromMediaImage(
                        mediaImage,
                        rotationDegrees,
                    )

                // CRITICAL: ML Kit does NOT honor the MediaImage's cropRect - it processes the
                // full buffer and returns bounding boxes in full image coordinates.
                // For edge filtering, use full image bounds.
                val imageBoundsForFiltering = android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height)

                detectionScope.launch {
                    try {
                        // CRITICAL: Mark first frame received for analysisFlowing
                        if (!hasReceivedFirstFrame) {
                            hasReceivedFirstFrame = true
                            sessionController.updateAnalysisFlowing(true)
                            sessionController.updateStallReason(StallReason.NONE)
                            Log.i(CAM_FRAME_TAG, "FIRST_FRAME: Scanning received first frame")
                        }

                        // Update frame timestamp for diagnostics
                        val frameTimestamp = System.currentTimeMillis()
                        sessionController.updateLastFrameTimestamp(frameTimestamp)

                        // CRITICAL FIX: Report FULL IMAGE dimensions, not cropRect.
                        // ML Kit's InputImage.fromMediaImage() does NOT honor cropRect - it processes
                        // the full MediaImage buffer and returns bounding boxes in full image coordinates.
                        // The overlay transform will handle the aspect ratio difference.
                        val frameSize = Size(imageProxy.width, imageProxy.height)
                        val frameRotation = imageProxy.imageInfo.rotationDegrees
                        withContext(Dispatchers.Main) {
                            onFrameSize(frameSize)
                            onRotation(frameRotation)
                        }

                        val documentCandidate =
                            if (detectionRouter.tryInvokeDocumentDetection(frameReceiveTime)) {
                                documentCandidateDetector.detect(imageProxy, frameReceiveTime)
                            } else {
                                null
                            }
                        updateDocumentCandidateState(documentCandidate, frameReceiveTime)

                        // Route through detection router for metrics tracking
                        // Note: Currently just records invocation, does not change detection behavior
                        detectionRouter.routeDetection(scanMode, frameReceiveTime)

                        // Log detection invocation with full image dimensions
                        ScanPipelineDiagnostics.logDetectionInvoked(
                            mode = scanMode.name,
                            imageWidth = imageProxy.width,
                            imageHeight = imageProxy.height,
                            rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                        )

                        // Use SINGLE_IMAGE_MODE for object detection with tracking during continuous scanning
                        // CRITICAL: Use SINGLE_IMAGE_MODE to avoid blinking bounding boxes
                        // STREAM_MODE produces unstable tracking IDs from ML Kit that change between frames
                        val (items, detections) =
                            processImageProxy(
                                imageProxy = imageProxy,
                                scanMode = scanMode,
                                useStreamMode = false,
                                onDetectionEvent = onDetectionEvent,
                            )

                        // [METRICS] Calculate analyzer latency
                        val analyzerLatencyMs = SystemClock.elapsedRealtime() - frameReceiveTime

                        // Log detection result
                        ScanPipelineDiagnostics.logDetectionResult(
                            detectionCount = detections.size,
                            topConfidence = detections.maxOfOrNull { it.confidence } ?: 0f,
                            inferenceTimeMs = analyzerLatencyMs,
                            itemsAdded = items.size,
                        )

                        // Update bbox timestamp for diagnostics
                        if (detections.isNotEmpty()) {
                            sessionController.updateLastBboxTimestamp(System.currentTimeMillis(), detections.size)
                        }

                        withContext(Dispatchers.Main) {
                            if (items.isNotEmpty()) {
                                onResult(items)
                            }
                            onDetectionResult(detections)
                        }
                        totalFramesProcessed++
                        framesInWindow++

                        // Feed processing time to adaptive throttle policy
                        detectionRouter.recordFrameProcessingTime(analyzerLatencyMs)

                        // [METRICS] Calculate and log frame rate every 1 second
                        val now = SystemClock.elapsedRealtime()
                        val timeSinceLastReport = now - lastFpsReportTime
                        if (timeSinceLastReport >= 1000L) {
                            val fps = (framesInWindow * 1000.0) / timeSinceLastReport
                            _analysisFps.value = fps
                            sessionController.updateAnalysisFps(fps)
                            lastFpsReportTime = now
                            framesInWindow = 0

                            // Log adaptive throttle state periodically (beta troubleshooting)
                            if (BuildConfig.DEBUG) {
                                val adaptiveStats = detectionRouter.getAdaptiveStats()
                                if (adaptiveStats.isThrottling) {
                                    Log.i(
                                        TAG,
                                        "[LOW_POWER] Adaptive throttling active: multiplier=${"%.2f".format(
                                            adaptiveStats.adaptiveMultiplier,
                                        )}, avgLatency=${adaptiveStats.rollingAverageMs}ms",
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in analyzer coroutine", e)
                    } finally {
                        imageProxy.close() // CRITICAL: Release the buffer
                        isProcessing = false
                        Trace.endSection()
                    }
                }
            } else {
                imageProxy.close()
            }
        }
    }

    /**
     * Compute analysis interval based on motion score.
     *
     * FIX: Previously used 2000ms for steady scenes (motion <= 0.1), which caused
     * detection to feel unresponsive when camera is held still. Reduced to 600ms
     * to ensure detection happens within a reasonable time while still providing
     * battery savings compared to max rate.
     *
     * @see docs/SCAN_VS_PICTURE_ASSESSMENT.md for root cause analysis
     */
    private fun analysisIntervalMsForMotion(motionScore: Double): Long =
        when {
            motionScore <= 0.1 -> 600L // Steady scene: was 2000ms, now 600ms (~1.7 fps)
            motionScore <= 0.5 -> 500L // Low motion: was 800ms, now 500ms (~2 fps)
            else -> 400L // High motion: unchanged (~2.5 fps)
        }

    private fun updateDocumentCandidateState(
        candidate: DocumentCandidate?,
        timestampMs: Long,
    ) {
        val current = _documentCandidateState.value
        if (candidate != null && candidate.confidence >= DOCUMENT_CANDIDATE_MIN_CONFIDENCE) {
            _documentCandidateState.value =
                DocumentCandidateState(
                    candidate = candidate,
                    lastSeenMs = timestampMs,
                    averageProcessingMs = documentCandidateDetector.averageProcessingMs(),
                )
            return
        }

        if (current != null && timestampMs - current.lastSeenMs > DOCUMENT_CANDIDATE_TTL_MS) {
            _documentCandidateState.value = null
        }
    }

    private fun computeMotionScore(imageProxy: ImageProxy): Double {
        val plane = imageProxy.planes.firstOrNull() ?: return lastMotionScore
        val width = imageProxy.width
        val height = imageProxy.height
        if (width == 0 || height == 0) return lastMotionScore

        val sampleWidth = (width + LUMA_SAMPLE_STEP - 1) / LUMA_SAMPLE_STEP
        val sampleHeight = (height + LUMA_SAMPLE_STEP - 1) / LUMA_SAMPLE_STEP
        val sampleSize = sampleWidth * sampleHeight

        // Ensure buffers are allocated at the correct size
        // Only reallocates if resolution changes (rare during a session)
        if (lumaBufferSize != sampleSize) {
            lumaSampleBuffers[0] = ByteArray(sampleSize)
            lumaSampleBuffers[1] = ByteArray(sampleSize)
            lumaBufferSize = sampleSize
            currentLumaBufferIndex = 0
            hasValidPreviousLumaSample = false
            Log.d(TAG, "Allocated luma sample buffers: $sampleSize bytes each")
        }

        // Get current buffer for writing and previous buffer for comparison
        val currentSample = lumaSampleBuffers[currentLumaBufferIndex]!!
        val previousSample = lumaSampleBuffers[1 - currentLumaBufferIndex]!!

        // Sample luma values from the Y plane
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        var sampleIndex = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val bufferIndex = y * rowStride + x * pixelStride
                currentSample[sampleIndex] = buffer.get(bufferIndex)
                sampleIndex++
                x += LUMA_SAMPLE_STEP
            }
            y += LUMA_SAMPLE_STEP
        }

        // Compute motion score by comparing with previous frame
        // Skip comparison on first frame when we don't have valid previous data
        val motionScore =
            if (hasValidPreviousLumaSample) {
                var diffSum = 0L
                for (i in 0 until sampleIndex) {
                    diffSum +=
                        kotlin.math.abs(
                            (currentSample[i].toInt() and 0xFF) - (previousSample[i].toInt() and 0xFF),
                        )
                }
                diffSum.toDouble() / (sampleIndex * 255.0)
            } else {
                lastMotionScore
            }

        // Swap buffer index for next frame and mark that we now have valid data
        currentLumaBufferIndex = 1 - currentLumaBufferIndex
        hasValidPreviousLumaSample = true
        lastMotionScore = motionScore
        return motionScore
    }

    /**
     * Stops continuous scanning mode.
     */
    fun stopScanning() {
        Log.d(TAG, "stopScanning: Stopping continuous scanning mode")
        isScanning = false
        sessionController.updateScanningActive(false)

        detectionScope.coroutineContext.cancelChildren()
        imageAnalysis?.clearAnalyzer()
        sessionController.updateAnalysisRunning(false)
        sessionController.updateAnalysisAttached(false)
        sessionController.updateAnalysisFlowing(false)

        scanJob?.cancel()
        scanJob = null
        // Stop detection router session (logs stats)
        detectionRouter.stopSession()
        // Stop diagnostics session (logs summary)
        ScanPipelineDiagnostics.stopSession()
        _documentCandidateState.value = null
        // Reset tracker when stopping scan
        objectTracker.stopSession("user_stopped")
        objectTracker.reset()
        currentScanMode = null
        // Reset guidance state
        scanGuidanceManager.reset()
        _scanGuidanceState.value = ScanGuidanceState.initial()
    }

    /**
     * Starts lightweight preview detection mode.
     * This shows bounding boxes on the camera preview without adding items to the list.
     * Used to give users immediate visual feedback when the camera starts.
     *
     * CRITICAL FIX: This function now handles the race condition where it may be called
     * BEFORE camera binding completes. If imageAnalysis is null or camera is not bound,
     * the callback is stored and applied after binding completes in startCamera().
     *
     * @param onDetectionResult Callback for detection results (for overlay display)
     * @param onFrameSize Callback for frame dimensions
     */
    fun startPreviewDetection(
        onDetectionResult: (List<DetectionResult>) -> Unit = {},
        onFrameSize: (Size) -> Unit = {},
        onRotation: (Int) -> Unit = {},
    ) {
        // Don't start if already scanning (scanning takes priority)
        if (isScanning) {
            Log.d(TAG, "startPreviewDetection: Skipping - scanning is active")
            return
        }

        Log.i(CAM_FRAME_TAG, "startPreviewDetection: Called, imageAnalysis=${imageAnalysis != null}, cameraBound=${cameraProvider != null}")

        // CRITICAL FIX: Ensure scope is ready before starting
        if (!detectionScope.isActive) {
            sessionController.logEvent("SCOPE_RECREATE", "detectionScope was inactive, recreating")
            detectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        }

        // Mark preview detection as active regardless of camera state
        isPreviewDetectionActive = true
        sessionController.updatePreviewDetectionActive(true)

        // CRITICAL FIX: If imageAnalysis is null (camera not yet bound), store the callback
        // to be applied after camera binding completes
        if (imageAnalysis == null) {
            Log.i(CAM_FRAME_TAG, "startPreviewDetection: Camera not bound yet, storing callback for later")
            pendingPreviewDetectionCallback =
                PreviewDetectionCallback(
                    onDetectionResult = onDetectionResult,
                    onFrameSize = onFrameSize,
                    onRotation = onRotation,
                )
            return
        }

        // Camera is bound, apply analyzer directly
        applyPreviewDetectionAnalyzer(
            onDetectionResult = onDetectionResult,
            onFrameSize = onFrameSize,
            onRotation = onRotation,
        )
    }

    /**
     * Internal function to apply the preview detection analyzer to imageAnalysis.
     * This is called either directly from startPreviewDetection() or from startCamera()
     * when the pending callback is applied after binding.
     */
    private fun applyPreviewDetectionAnalyzer(
        onDetectionResult: (List<DetectionResult>) -> Unit,
        onFrameSize: (Size) -> Unit,
        onRotation: (Int) -> Unit,
    ) {
        Log.i(CAM_FRAME_TAG, "applyPreviewDetectionAnalyzer: Setting analyzer on imageAnalysis")

        val analysis = imageAnalysis
        if (analysis == null) {
            Log.e(CAM_FRAME_TAG, "applyPreviewDetectionAnalyzer: imageAnalysis is null, cannot set analyzer!")
            return
        }

        sessionController.updateAnalysisRunning(true)
        sessionController.updateAnalysisAttached(true)
        sessionController.updateAnalysisFlowing(false) // Will be set true on first frame

        var lastAnalysisTime = 0L
        var isProcessing = false

        // Use a longer interval for preview detection (less aggressive, battery-friendly)
        val previewAnalysisIntervalMs = 400L

        // Capture session ID at analyzer setup time for callback validation
        val sessionIdAtSetup = sessionController.getCurrentSessionId()

        analysis.clearAnalyzer()
        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
            // CRITICAL: Validate session to ignore callbacks from stale analyzers
            if (!sessionController.isSessionValid(sessionIdAtSetup)) {
                imageProxy.close()
                return@setAnalyzer
            }

            // Stop if scanning started or preview detection was stopped
            if (isScanning || !isPreviewDetectionActive) {
                imageProxy.close()
                return@setAnalyzer
            }

            val currentTime = System.currentTimeMillis()
            val timeSinceLastAnalysis = currentTime - lastAnalysisTime

            // Throttle analysis
            if (timeSinceLastAnalysis < previewAnalysisIntervalMs || isProcessing) {
                imageProxy.close()
                return@setAnalyzer
            }

            lastAnalysisTime = currentTime
            isProcessing = true

            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                isProcessing = false
                return@setAnalyzer
            }

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            detectionScope.launch {
                try {
                    // CRITICAL: Mark first frame received
                    if (!hasReceivedFirstFrame) {
                        hasReceivedFirstFrame = true
                        sessionController.updateAnalysisFlowing(true)
                        sessionController.updateStallReason(StallReason.NONE)
                        Log.i(CAM_FRAME_TAG, "FIRST_FRAME: Preview detection received first frame")
                    }

                    // Update frame timestamp for diagnostics
                    val frameTimestamp = System.currentTimeMillis()
                    sessionController.updateLastFrameTimestamp(frameTimestamp)

                    // Report frame size and rotation for correct overlay mapping
                    val frameSize = Size(imageProxy.width, imageProxy.height)
                    withContext(Dispatchers.Main) {
                        onFrameSize(frameSize)
                        onRotation(rotationDegrees)
                    }

                    // Run detection (preview only - no item creation)
                    val imageBoundsForFiltering = android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height)
                    val response =
                        objectDetector.detectObjects(
                            image = inputImage,
                            sourceBitmap = { null },
// No bitmap needed for preview-only
                            useStreamMode = false,
                            cropRect = imageBoundsForFiltering,
                            edgeInsetRatio = EDGE_INSET_MARGIN_RATIO,
                        )

                    // Update bbox timestamp for diagnostics
                    if (response.detectionResults.isNotEmpty()) {
                        sessionController.updateLastBboxTimestamp(System.currentTimeMillis(), response.detectionResults.size)
                    }

                    withContext(Dispatchers.Main) {
                        onDetectionResult(response.detectionResults)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in preview detection", e)
                } finally {
                    imageProxy.close()
                    isProcessing = false
                }
            }
        }

        Log.i(CAM_FRAME_TAG, "applyPreviewDetectionAnalyzer: Analyzer set successfully")
    }

    /**
     * Stops preview detection mode.
     * Called when user starts scanning/capturing or when leaving the camera screen.
     */
    fun stopPreviewDetection() {
        if (!isPreviewDetectionActive) {
            return
        }
        Log.d(TAG, "stopPreviewDetection: Stopping preview detection mode")
        isPreviewDetectionActive = false
        sessionController.updatePreviewDetectionActive(false)

        // Clear pending callback
        pendingPreviewDetectionCallback = null

        // Only clear analyzer if not scanning (scanning manages its own analyzer)
        if (!isScanning) {
            imageAnalysis?.clearAnalyzer()
            sessionController.updateAnalysisRunning(false)
            sessionController.updateAnalysisAttached(false)
            sessionController.updateAnalysisFlowing(false)
        }
    }

    /**
     * Check if preview detection is currently active.
     */
    fun isPreviewDetectionActive(): Boolean = isPreviewDetectionActive

    // =========================================================================
    // NO_FRAMES Watchdog - Self-heal for analyzer stalls
    // =========================================================================

    /**
     * Starts the NO_FRAMES watchdog coroutine.
     * This monitors for the case where analyzer is attached but no frames flow.
     *
     * Called after camera binding completes.
     */
    private fun startNoFramesWatchdog() {
        watchdogJob?.cancel()
        watchdogJob =
            detectionScope.launch {
                Log.i(CAM_FRAME_TAG, "WATCHDOG: Started, waiting ${WATCHDOG_INITIAL_DELAY_MS}ms for first frame")
                delay(WATCHDOG_INITIAL_DELAY_MS)

                // Check if we've received frames
                if (hasReceivedFirstFrame) {
                    Log.i(CAM_FRAME_TAG, "WATCHDOG: First frame already received, no stall detected")
                    return@launch
                }

                // Check if camera is still active and analyzer is attached
                val diagnostics = sessionController.diagnostics.value
                if (!diagnostics.isCameraBound || !diagnostics.isAnalysisAttached) {
                    Log.i(CAM_FRAME_TAG, "WATCHDOG: Camera not bound or analyzer not attached, skipping recovery")
                    return@launch
                }

                // STALL DETECTED: Analyzer attached but no frames
                Log.w(CAM_FRAME_TAG, "WATCHDOG: STALL_NO_FRAMES detected! cameraBound=${diagnostics.isCameraBound}, analysisAttached=${diagnostics.isAnalysisAttached}, analysisFlowing=${diagnostics.isAnalysisFlowing}")
                sessionController.updateStallReason(StallReason.NO_FRAMES)

                // Attempt recovery
                while (recoveryAttempts < MAX_RECOVERY_ATTEMPTS && !hasReceivedFirstFrame) {
                    recoveryAttempts++
                    sessionController.updateRecoveryAttempts(recoveryAttempts)
                    sessionController.updateStallReason(StallReason.RECOVERING)
                    Log.i(CAM_FRAME_TAG, "WATCHDOG: Recovery attempt $recoveryAttempts/$MAX_RECOVERY_ATTEMPTS")

                    // Re-apply the analyzer on the current imageAnalysis
                    rebindAnalysisPipeline()

                    // Wait for frames
                    delay(WATCHDOG_RETRY_DELAY_MS)

                    if (hasReceivedFirstFrame) {
                        Log.i(CAM_FRAME_TAG, "WATCHDOG: Recovery successful on attempt $recoveryAttempts")
                        sessionController.updateStallReason(StallReason.NONE)
                        return@launch
                    }
                }

                // Recovery failed
                if (!hasReceivedFirstFrame) {
                    Log.e(CAM_FRAME_TAG, "WATCHDOG: Recovery FAILED after $MAX_RECOVERY_ATTEMPTS attempts")
                    sessionController.updateStallReason(StallReason.FAILED)
                }
            }
    }

    /**
     * Rebinds the analysis pipeline to recover from stall.
     *
     * This is a lightweight rebind that only clears and re-sets the analyzer,
     * without fully recreating all use cases.
     *
     * If there's a pending preview detection callback, it will be applied.
     * Otherwise, a simple pass-through analyzer is set to verify frame flow.
     */
    private fun rebindAnalysisPipeline() {
        Log.i(CAM_FRAME_TAG, "rebindAnalysisPipeline: Starting lightweight rebind")

        val analysis = imageAnalysis
        if (analysis == null) {
            Log.e(CAM_FRAME_TAG, "rebindAnalysisPipeline: imageAnalysis is null, cannot rebind")
            return
        }

        // Clear the existing analyzer
        analysis.clearAnalyzer()
        sessionController.updateAnalysisAttached(false)

        // Reset the first frame flag to detect new frames
        hasReceivedFirstFrame = false

        // Check if we have a pending callback to apply
        val pendingCallback = pendingPreviewDetectionCallback
        if (pendingCallback != null) {
            Log.i(CAM_FRAME_TAG, "rebindAnalysisPipeline: Applying pending preview detection callback")
            applyPreviewDetectionAnalyzer(
                onDetectionResult = pendingCallback.onDetectionResult,
                onFrameSize = pendingCallback.onFrameSize,
                onRotation = pendingCallback.onRotation,
            )
        } else if (isPreviewDetectionActive) {
            // No pending callback but preview detection is active
            // This shouldn't happen normally, but set a minimal analyzer to verify frame flow
            Log.i(CAM_FRAME_TAG, "rebindAnalysisPipeline: Setting minimal verification analyzer")
            sessionController.updateAnalysisAttached(true)

            val sessionIdAtSetup = sessionController.getCurrentSessionId()
            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                if (!sessionController.isSessionValid(sessionIdAtSetup)) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                // Mark first frame received
                if (!hasReceivedFirstFrame) {
                    hasReceivedFirstFrame = true
                    sessionController.updateAnalysisFlowing(true)
                    sessionController.updateLastFrameTimestamp(System.currentTimeMillis())
                    Log.i(CAM_FRAME_TAG, "rebindAnalysisPipeline: Minimal analyzer received first frame")
                }

                imageProxy.close()
            }
        }
    }

    /**
     * Enable or disable scan diagnostics overlay.
     */
    fun setScanDiagnosticsEnabled(enabled: Boolean) {
        _scanDiagnosticsEnabled.value = enabled
    }

    /**
     * Notify that an item was added (for guidance lock release).
     */
    fun onItemAdded() {
        scanGuidanceManager.onItemAdded()
    }

    /**
     * Get the current scan ROI for external use.
     */
    fun getCurrentScanRoi(): ScanRoi {
        return scanGuidanceManager.getCurrentRoi()
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
        useStreamMode: Boolean = false,
        onDetectionEvent: (DetectionEvent) -> Unit = {},
    ): Pair<List<ScannedItem>, List<DetectionResult>> {
        var cachedBitmap: Bitmap? = null
        val frameStartTime = SystemClock.elapsedRealtime()
        val span =
            telemetry?.beginSpan(
                PerformanceMonitor.Spans.FRAME_ANALYSIS,
                mapOf(
                    "scan_mode" to scanMode.name,
                    "stream_mode" to useStreamMode.toString(),
                ),
            )
        return try {
            Log.i(TAG, ">>> processImageProxy: START - scanMode=$scanMode, useStreamMode=$useStreamMode, isScanning=$isScanning")

            // Get MediaImage from ImageProxy
            val mediaImage =
                imageProxy.image ?: run {
                    Log.e(TAG, "processImageProxy: mediaImage is null")
                    return Pair(emptyList(), emptyList())
                }
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            // NOTE: We previously applied cropRect here thinking ML Kit would honor it.
            // However, InputImage.fromMediaImage() does NOT honor cropRect - it processes
            // the full MediaImage buffer. ML Kit returns bounding boxes in full image coordinates.
            // The cropRect is still calculated for reference but not applied to the ImageProxy.

            // PHASE 5: Rate-limited viewport logging
            val now = System.currentTimeMillis()
            if (now - lastCropRectLogTime >= cropRectLogIntervalMs) {
                Log.i(TAG, "[VIEWPORT] image=${imageProxy.width}x${imageProxy.height}, rotation=$rotationDegrees")
                lastCropRectLogTime = now
            }

            // Build ML Kit image from camera buffer (full frame)
            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            // OPTIMIZATION: Lazy bitmap provider - only creates bitmap when invoked
            // IMPORTANT: Do NOT rotate the bitmap! ML Kit's InputImage already has rotation
            // metadata, so bounding boxes will be in the original (unrotated) coordinate space.
            // Rotating the bitmap would cause a coordinate mismatch when cropping thumbnails.
            val lazyBitmapProvider: () -> Bitmap? = {
                if (cachedBitmap == null) {
                    cachedBitmap =
                        runCatching {
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

            // CRITICAL: ML Kit returns bounding boxes in full image coordinates.
            // Use full image dimensions for edge filtering.
            val imageBoundsForFiltering = android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height)

            // Route to the appropriate scanner based on mode
            when (scanMode) {
                ScanMode.OBJECT_DETECTION -> {
                    // Use tracking pipeline when in STREAM_MODE and scanning
                    if (useStreamMode && isScanning) {
                        Log.i(TAG, ">>> processImageProxy: Taking TRACKING PATH (useStreamMode=$useStreamMode, isScanning=$isScanning)")
                        val (items, detections) =
                            processObjectDetectionWithTracking(
                                inputImage = inputImage,
                                lazyBitmapProvider = lazyBitmapProvider,
                                cropRect = imageBoundsForFiltering,
                                edgeInsetRatio = EDGE_INSET_MARGIN_RATIO,
                            )
                        Log.i(
                            TAG,
                            ">>> processImageProxy: Tracking path returned ${items.size} items and ${detections.size} detection results",
                        )
                        val event = detectionRouter.processObjectResults(items, detections)
                        onDetectionEvent(event)
                        Pair(items, detections)
                    } else {
                        // Single-shot detection without tracking
                        Log.i(TAG, ">>> processImageProxy: Taking SINGLE-SHOT PATH (useStreamMode=$useStreamMode, isScanning=$isScanning)")
                        val response =
                            objectDetector.detectObjects(
                                image = inputImage,
                                sourceBitmap = lazyBitmapProvider,
                                useStreamMode = useStreamMode,
                                cropRect = imageBoundsForFiltering,
                                edgeInsetRatio = EDGE_INSET_MARGIN_RATIO,
                            )
                        Log.i(TAG, ">>> processImageProxy: Single-shot path returned ${response.scannedItems.size} items")
                        val event =
                            detectionRouter.processObjectResults(
                                response.scannedItems,
                                response.detectionResults,
                            )
                        onDetectionEvent(event)
                        Pair(response.scannedItems, response.detectionResults)
                    }
                }
                ScanMode.BARCODE -> {
                    // Check throttle before running barcode detection
                    val canRun = detectionRouter.tryInvokeBarcodeDetection()
                    if (!canRun) {
                        Log.d(TAG, "[BARCODE] Throttled - skipping frame")
                        Pair(emptyList(), emptyList())
                    } else {
                        // Run barcode detection
                        val rawItems =
                            barcodeDetector.scanBarcodes(
                                image = inputImage,
                                sourceBitmap = lazyBitmapProvider,
                            )

                        if (rawItems.isEmpty()) {
                            Pair(emptyList(), emptyList())
                        } else {
                            // Process through router for deduplication
                            val (event, uniqueItems) = detectionRouter.processBarcodeResults(rawItems)
                            onDetectionEvent(event)
                            Log.i(TAG, "[BARCODE] Detected ${rawItems.size} barcodes, ${uniqueItems.size} unique after dedupe")
                            Pair(uniqueItems, emptyList())
                        }
                    }
                }
                ScanMode.DOCUMENT_TEXT -> {
                    val items =
                        textRecognizer.recognizeText(
                            image = inputImage,
                            sourceBitmap = lazyBitmapProvider,
                        )
                    val event = detectionRouter.processDocumentResults(items)
                    onDetectionEvent(event)
                    Pair(items, emptyList())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, ">>> processImageProxy: ERROR", e)
            span?.recordError(e.message ?: "Unknown error")
            Pair(emptyList(), emptyList())
        } finally {
            // Record frame analysis duration
            val frameDuration = SystemClock.elapsedRealtime() - frameStartTime
            PerformanceMonitor.recordTimer(
                PerformanceMonitor.Metrics.FRAME_ANALYSIS_LATENCY_MS,
                frameDuration,
                mapOf("scan_mode" to scanMode.name),
            )
            span?.end()

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
        edgeInsetRatio: Float,
        analyzerLatencyMs: Long = 0,
    ): Pair<List<ScannedItem>, List<DetectionResult>> {
        Log.i(TAG, ">>> processObjectDetectionWithTracking: CALLED")

        // SINGLE DETECTION PASS: Get both tracking metadata and overlay data together
        val trackingResponse =
            objectDetector.detectObjectsWithTracking(
                image = inputImage,
                sourceBitmap = lazyBitmapProvider,
                useStreamMode = true,
                cropRect = cropRect,
                edgeInsetRatio = edgeInsetRatio,
            )

        Log.i(
            TAG,
            ">>> processObjectDetectionWithTracking: Got ${trackingResponse.detectionInfos.size} DetectionInfo objects and ${trackingResponse.detectionResults.size} DetectionResult objects from a SINGLE detection pass",
        )

        // Calculate sharpness score from bitmap (for center-weighted gating)
        val frameSharpness =
            lazyBitmapProvider()?.let { bitmap ->
                SharpnessCalculator.calculateSharpness(bitmap)
            } ?: 0f

        // Log sharpness if diagnostics enabled
        val frameId = com.scanium.app.camera.detection.LiveScanDiagnostics.nextFrameId()
        if (com.scanium.app.camera.detection.LiveScanDiagnostics.enabled) {
            com.scanium.app.camera.detection.LiveScanDiagnostics.logSharpness(
                frameId = frameId,
                sharpnessScore = frameSharpness,
                isBlurry = frameSharpness < SharpnessCalculator.DEFAULT_MIN_SHARPNESS,
                threshold = SharpnessCalculator.DEFAULT_MIN_SHARPNESS,
            )
        }

        // Get current scan ROI from guidance manager
        val currentRoi = scanGuidanceManager.getCurrentRoi()

        // Create candidate info for guidance state update (from best detection)
        val bestCandidateInfo =
            trackingResponse.detectionInfos.maxByOrNull { it.confidence }?.let { detection ->
                val boxCenterX = (detection.boundingBox.left + detection.boundingBox.right) / 2f
                val boxCenterY = (detection.boundingBox.top + detection.boundingBox.bottom) / 2f
                CandidateInfo(
                    trackingId = detection.trackingId,
                    boxCenterX = boxCenterX,
                    boxCenterY = boxCenterY,
                    boxArea = detection.normalizedBoxArea,
                    confidence = detection.confidence,
                )
            }

        // Update guidance state (uses the last motion score for motion detection)
        val guidanceState =
            scanGuidanceManager.processFrame(
                candidate = bestCandidateInfo,
                motionScore = lastMotionScore.toFloat(),
                sharpnessScore = frameSharpness,
                currentTimeMs = System.currentTimeMillis(),
            )
        _scanGuidanceState.value = guidanceState

        // Process detections through tracker with ROI filtering
        val trackingStartTime = SystemClock.elapsedRealtime()
        val confirmedCandidates =
            objectTracker.processFrameWithRoi(
                detections = trackingResponse.detectionInfos,
                scanRoi = currentRoi,
                inferenceLatencyMs = analyzerLatencyMs,
                frameSharpness = frameSharpness,
            )
        PerformanceMonitor.recordTimer(
            PerformanceMonitor.Metrics.TRACKING_LATENCY_MS,
            SystemClock.elapsedRealtime() - trackingStartTime,
            mapOf("detection_count" to trackingResponse.detectionInfos.size.toString()),
        )

        Log.i(TAG, ">>> processObjectDetectionWithTracking: ObjectTracker returned ${confirmedCandidates.size} newly confirmed candidates")

        // Log tracker stats
        val stats = objectTracker.getStats()
        Log.i(
            TAG,
            ">>> Tracker stats: active=${stats.activeCandidates}, confirmed=${stats.confirmedCandidates}, frame=${stats.currentFrame}",
        )

        // PHASE 5: Assertions for ROI enforcement and LOCKED state gating
        // Items can ONLY be added when guidance state allows it (LOCKED state)
        val canAddItems = guidanceState.canAddItem
        val isLocked = guidanceState.state == GuidanceState.LOCKED

        // Assert: If we have confirmed candidates but guidance doesn't allow add, don't return them
        // This ensures visual preview (bbox) != item add (requires LOCKED state)
        val itemsToAdd =
            if (canAddItems && isLocked) {
                // Guidance allows add - verify candidates are inside ROI (debug assertion)
                confirmedCandidates.mapNotNull { candidate ->
                    val bbox = candidate.boundingBoxNorm ?: return@mapNotNull null
                    val centerX = (bbox.left + bbox.right) / 2f
                    val centerY = (bbox.top + bbox.bottom) / 2f

                    // Debug assertion: candidate should be inside ROI
                    val isInsideRoi = currentRoi.containsBoxCenter(centerX, centerY)
                    if (!isInsideRoi) {
                        Log.e(
                            TAG,
                            "!!! ASSERTION FAILED: Confirmed candidate ${candidate.internalId} is OUTSIDE ROI (center=$centerX,$centerY, roi=$currentRoi)",
                        )
                        // In debug builds, this could be a hard failure
                        // In release, we skip the item to maintain UX
                        if (com.scanium.app.BuildConfig.DEBUG) {
                            throw IllegalStateException("Confirmed candidate outside ROI - this should never happen")
                        }
                        null
                    } else {
                        objectDetector.candidateToScannedItem(candidate)
                    }
                }
            } else {
                // Guidance doesn't allow add (not LOCKED) - don't add items even if tracker confirmed them
                if (confirmedCandidates.isNotEmpty()) {
                    Log.d(
                        TAG,
                        ">>> Not adding ${confirmedCandidates.size} confirmed candidates: canAddItem=$canAddItems, isLocked=$isLocked",
                    )
                }
                emptyList()
            }

        Log.i(TAG, ">>> processObjectDetectionWithTracking: Converted to ${itemsToAdd.size} ScannedItems (gated by LOCKED=$isLocked)")
        itemsToAdd.forEachIndexed { index, item ->
            Log.i(TAG, "    ScannedItem $index: id=${item.id}, category=${item.category}, priceRange=${item.priceRange}")
        }

        Log.i(
            TAG,
            ">>> processObjectDetectionWithTracking: RETURNING ${itemsToAdd.size} items and ${trackingResponse.detectionResults.size} detection results",
        )
        return Pair(itemsToAdd, trackingResponse.detectionResults)
    }

    /**
     * Converts ImageProxy to Bitmap.
     *
     * CRITICAL: Creates a FULL FRAME bitmap, not cropped to cropRect.
     * ML Kit's InputImage.fromMediaImage() does NOT honor cropRect - it processes the
     * full MediaImage buffer and returns bounding boxes in full frame coordinates.
     * Therefore, the bitmap used for thumbnail cropping must also be full frame.
     */
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
        // CRITICAL FIX: Use FULL FRAME rect, not cropRect.
        // ML Kit returns bounding boxes in full image coordinates, so the bitmap
        // used for thumbnail cropping must match those coordinates.
        val fullFrameRect = android.graphics.Rect(0, 0, width, height)
        yuvImage.compressToJpeg(fullFrameRect, 90, out)
        val jpegBytes = out.toByteArray()

        return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Rotates a bitmap by the specified degrees.
     */
    private fun rotateBitmap(
        bitmap: Bitmap,
        degrees: Int,
    ): Bitmap {
        if (degrees == 0) return bitmap

        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true,
        )
    }

    /**
     * Builds an ImageCapture use case configured for the specified resolution.
     */
    private fun buildImageCapture(
        resolution: CaptureResolution,
        rotation: Int,
    ): ImageCapture {
        val builder =
            ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(rotation)

        // Configure target resolution based on setting
        val targetSize =
            when (resolution) {
                CaptureResolution.LOW -> android.util.Size(1280, 720) // HD
                CaptureResolution.NORMAL -> android.util.Size(1920, 1080) // Full HD
                CaptureResolution.HIGH -> android.util.Size(3840, 2160) // 4K
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
    suspend fun captureHighResImage(): Uri? =
        withContext(Dispatchers.IO) {
            val capture =
                imageCapture ?: run {
                    Log.e(TAG, "captureHighResImage: ImageCapture not initialized")
                    return@withContext null
                }

            try {
                // Create output file
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
                val photoFile =
                    File(
                        context.cacheDir,
                        "SCANIUM_$timestamp.jpg",
                    )

                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                // Capture image
                val result =
                    suspendCancellableCoroutine<ImageCapture.OutputFileResults> { continuation ->
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
                            },
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
     * Result of a document scan operation.
     */
    sealed class DocumentScanResult {
        /** Scan completed successfully with a document item */
        data class Success(val item: ScannedItem, val imageUri: Uri) : DocumentScanResult()

        /** No text was detected in the document */
        data object NoTextDetected : DocumentScanResult()

        /** User cancelled the operation */
        data object Cancelled : DocumentScanResult()

        /** An error occurred during scanning */
        data class Error(val message: String, val exception: Exception? = null) : DocumentScanResult()
    }

    /**
     * Performs a heavy document scan by capturing a high-resolution image
     * and running text recognition on it.
     *
     * This is an on-demand operation that only runs when explicitly triggered,
     * not continuously during camera preview.
     *
     * @return DocumentScanResult containing the scanned item or error information
     */
    suspend fun scanDocument(): DocumentScanResult =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "scanDocument: Starting heavy document scan")

            try {
                // Step 1: Capture high-resolution image
                val imageUri = captureHighResImage()
                if (imageUri == null) {
                    Log.e(TAG, "scanDocument: Failed to capture high-res image")
                    return@withContext DocumentScanResult.Error("Failed to capture image")
                }

                Log.i(TAG, "scanDocument: Captured image at $imageUri")

                // Step 2: Load the captured image as a bitmap for text recognition
                val bitmap =
                    try {
                        context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                            android.graphics.BitmapFactory.decodeStream(inputStream)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "scanDocument: Failed to load captured image", e)
                        return@withContext DocumentScanResult.Error("Failed to load image", e)
                    }

                if (bitmap == null) {
                    Log.e(TAG, "scanDocument: Decoded bitmap is null")
                    return@withContext DocumentScanResult.Error("Failed to decode image")
                }

                Log.i(TAG, "scanDocument: Loaded bitmap ${bitmap.width}x${bitmap.height}")

                // Step 3: Create InputImage for ML Kit
                val inputImage = InputImage.fromBitmap(bitmap, 0)

                // Step 4: Run text recognition
                val items = textRecognizer.recognizeText(inputImage) { bitmap }

                // Recycle the bitmap after processing
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }

                if (items.isEmpty()) {
                    Log.i(TAG, "scanDocument: No text detected in document")
                    return@withContext DocumentScanResult.NoTextDetected
                }

                // Step 5: Return the first item with the full image URI attached
                val documentItem = items.first().copy(fullImageUri = imageUri)

                Log.i(TAG, "scanDocument: Successfully scanned document with ${documentItem.recognizedText?.length ?: 0} characters")
                DocumentScanResult.Success(documentItem, imageUri)
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.i(TAG, "scanDocument: Scan was cancelled")
                DocumentScanResult.Cancelled
            } catch (e: Exception) {
                Log.e(TAG, "scanDocument: Error during document scan", e)
                DocumentScanResult.Error(e.message ?: "Unknown error", e)
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
        barcodeDetector.close()
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

    // =========================================================================
    // Detection Router Controls (for developer settings)
    // =========================================================================

    /**
     * Enables or disables barcode/QR detection.
     */
    fun setBarcodeDetectionEnabled(enabled: Boolean) {
        detectionRouter.setBarcodeDetectionEnabled(enabled)
    }

    /**
     * Checks if barcode detection is enabled.
     */
    fun isBarcodeDetectionEnabled(): Boolean = detectionRouter.isBarcodeDetectionEnabled()

    /**
     * Enables or disables document candidate detection.
     */
    fun setDocumentDetectionEnabled(enabled: Boolean) {
        detectionRouter.setDocumentDetectionEnabled(enabled)
    }

    /**
     * Checks if document detection is enabled.
     */
    fun isDocumentDetectionEnabled(): Boolean = detectionRouter.isDocumentDetectionEnabled()

    /**
     * Enables or disables adaptive throttling (low-power mode).
     */
    fun setAdaptiveThrottlingEnabled(enabled: Boolean) {
        detectionRouter.setAdaptiveThrottlingEnabled(enabled)
    }

    /**
     * Checks if adaptive throttling is enabled.
     */
    fun isAdaptiveThrottlingEnabled(): Boolean = detectionRouter.isAdaptiveThrottlingEnabled()

    /**
     * Gets the current adaptive throttle multiplier.
     * Returns 1.0 if not throttling, >1.0 if throttling.
     */
    val adaptiveThrottleMultiplier: StateFlow<Float> = detectionRouter.adaptiveMultiplier

    /**
     * Gets whether adaptive throttling is currently active.
     */
    val isAdaptiveThrottling: StateFlow<Boolean> = detectionRouter.isAdaptiveThrottling

    /**
     * Enables or disables scanning diagnostics logging.
     * When enabled, detailed ScanPipeline and LiveScan logs are emitted.
     */
    fun setScanningDiagnosticsEnabled(enabled: Boolean) {
        ScanPipelineDiagnostics.enabled = enabled
        com.scanium.app.camera.detection.LiveScanDiagnostics.enabled = enabled
    }

    /**
     * Checks if scanning diagnostics is enabled.
     */
    fun isScanningDiagnosticsEnabled(): Boolean = ScanPipelineDiagnostics.enabled

    /**
     * Gets the current scan pipeline metrics state flow for optional overlay display.
     */
    val scanMetricsState: StateFlow<com.scanium.app.camera.detection.ScanMetricsState> = ScanPipelineDiagnostics.metricsState

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
        previewHeight: Int,
    ): android.graphics.Rect {
        if (previewWidth == 0 || previewHeight == 0) {
            // Fallback: return full frame if preview dimensions not available
            return android.graphics.Rect(0, 0, imageWidth, imageHeight)
        }

        // Calculate aspect ratios
        val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
        val previewAspect = previewWidth.toFloat() / previewHeight.toFloat()

        val cropRect =
            if (imageAspect > previewAspect) {
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
