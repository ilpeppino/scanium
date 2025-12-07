package com.example.objecta.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.objecta.items.ScannedItem
import com.example.objecta.ml.BarcodeScannerClient
import com.example.objecta.ml.DocumentTextRecognitionClient
import com.example.objecta.ml.ObjectDetectorClient
import com.example.objecta.tracking.ObjectTracker
import com.example.objecta.tracking.TrackerConfig
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

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null

    private val objectDetector = ObjectDetectorClient()
    private val barcodeScanner = BarcodeScannerClient()
    private val textRecognizer = DocumentTextRecognitionClient()

    // Object tracker for de-duplication
    private val objectTracker = ObjectTracker(
        config = TrackerConfig(
            minFramesToConfirm = 1,      // Require 1 frame to confirm (instant detection)
            minConfidence = 0.3f,         // Minimum confidence 0.3
            minBoxArea = 0.001f,          // Minimum 0.1% of frame
            maxFrameGap = 5,              // Allow 5 frames gap for matching
            minMatchScore = 0.3f,         // Minimum 0.3 match score for spatial matching
            expiryFrames = 10             // Expire after 10 frames without detection
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

    /**
     * Initializes and binds camera to the PreviewView.
     */
    suspend fun startCamera(
        previewView: PreviewView,
        onDetectionResult: (List<ScannedItem>) -> Unit
    ) = withContext(Dispatchers.Main) {
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

        // Select back camera
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            // Unbind any existing use cases
            provider.unbindAll()

            // Bind use cases to lifecycle
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            // Handle camera binding failure
            e.printStackTrace()
        }
    }

    /**
     * Captures a single frame and runs detection based on the current scan mode.
     * Single-frame captures bypass the candidate tracker for immediate results.
     */
    fun captureSingleFrame(scanMode: ScanMode, onResult: (List<ScannedItem>) -> Unit) {
        Log.d(TAG, "captureSingleFrame: Starting single frame capture with mode $scanMode")
        // Clear any previous analyzer to avoid mixing modes
        imageAnalysis?.clearAnalyzer()
        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            Log.d(TAG, "captureSingleFrame: Received image proxy ${imageProxy.width}x${imageProxy.height}")
            detectionScope.launch {
                // Single-frame capture uses direct detection (no candidate tracking)
                val items = processImageProxy(imageProxy, scanMode, useStreamMode = false)
                Log.d(TAG, "captureSingleFrame: Got ${items.size} items")
                withContext(Dispatchers.Main) {
                    onResult(items)
                }
                imageAnalysis?.clearAnalyzer()
            }
        }
    }

    /**
     * Starts continuous scanning mode with multi-frame candidate tracking.
     * Captures frames periodically and uses CandidateTracker to promote only stable detections.
     */
    fun startScanning(scanMode: ScanMode, onResult: (List<ScannedItem>) -> Unit) {
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
        com.example.objecta.ml.DetectionLogger.logConfiguration(
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
                Log.d(TAG, "startScanning: Processing frame #$frameCounter ${imageProxy.width}x${imageProxy.height}")

                detectionScope.launch {
                    try {
                        // Use SINGLE_IMAGE_MODE for all modes (more accurate than STREAM_MODE)
                        val items = processImageProxy(imageProxy, scanMode, useStreamMode = false)
                        Log.d(TAG, "startScanning: Got ${items.size} items")
                        if (items.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                onResult(items)
                            }
                        }

                        // Periodic stats logging (every 10 frames)
                        if (frameCounter % 10 == 0) {
                            val stats = objectTracker.getStats()
                            Log.d(TAG, "Tracker stats: active=${stats.activeCandidates}, confirmed=${stats.confirmedCandidates}, frame=${stats.currentFrame}")
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
    ): List<ScannedItem> {
        return try {
            // Convert ImageProxy to Bitmap
            val mediaImage = imageProxy.image ?: run {
                Log.e(TAG, "processImageProxy: mediaImage is null")
                return emptyList()
            }
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            Log.d(TAG, "processImageProxy: Processing ${imageProxy.width}x${imageProxy.height}, rotation=$rotationDegrees, mode=$scanMode")

            // Build ML Kit image directly from camera buffer
            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            // Optional bitmap for thumbnails
            val bitmapForThumb = runCatching {
                val bitmap = imageProxy.toBitmap()
                Log.d(TAG, "processImageProxy: Created bitmap ${bitmap.width}x${bitmap.height}")
                rotateBitmap(bitmap, rotationDegrees)
            }.getOrElse { e ->
                Log.w(TAG, "processImageProxy: Failed to create bitmap", e)
                null
            }

            // Route to the appropriate scanner based on mode
            when (scanMode) {
                ScanMode.OBJECT_DETECTION -> {
                    // Use tracking pipeline for object detection in scanning mode
                    if (useStreamMode && isScanning) {
                        processObjectDetectionWithTracking(inputImage, bitmapForThumb)
                    } else {
                        // Single-shot detection without tracking
                        objectDetector.detectObjects(
                            image = inputImage,
                            sourceBitmap = bitmapForThumb,
                            useStreamMode = useStreamMode
                        )
                    }
                }
                ScanMode.BARCODE -> {
                    barcodeScanner.scanBarcodes(
                        image = inputImage,
                        sourceBitmap = bitmapForThumb
                    )
                }
                ScanMode.DOCUMENT_TEXT -> {
                    textRecognizer.recognizeText(
                        image = inputImage,
                        sourceBitmap = bitmapForThumb
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "processImageProxy: Error processing image", e)
            emptyList()
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
        // Get raw detections with tracking metadata
        val detections = objectDetector.detectObjectsWithTracking(
            image = inputImage,
            sourceBitmap = sourceBitmap,
            useStreamMode = true
        )

        Log.d(TAG, "processObjectDetectionWithTracking: Got ${detections.size} raw detections")

        // Process through tracker
        val confirmedCandidates = objectTracker.processFrame(detections)

        Log.d(TAG, "processObjectDetectionWithTracking: ${confirmedCandidates.size} newly confirmed candidates")

        // Log tracker stats
        val stats = objectTracker.getStats()
        Log.d(TAG, "Tracker stats: active=${stats.activeCandidates}, confirmed=${stats.confirmedCandidates}, frame=${stats.currentFrame}")

        // Convert confirmed candidates to ScannedItems
        val items = confirmedCandidates.map { candidate ->
            objectDetector.candidateToScannedItem(candidate)
        }

        Log.d(TAG, "processObjectDetectionWithTracking: Returning ${items.size} scanned items")
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
