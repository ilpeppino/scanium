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
import com.example.objecta.ml.ObjectDetectorClient
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

    // Executor for camera operations
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Coroutine scope for async detection
    private val detectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // State for scanning mode
    private var isScanning = false
    private var scanJob: Job? = null

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
     * Captures a single frame and runs object detection.
     */
    fun captureSingleFrame(onResult: (List<ScannedItem>) -> Unit) {
        Log.d(TAG, "captureSingleFrame: Starting single frame capture")
        // Clear any previous analyzer to avoid mixing modes
        imageAnalysis?.clearAnalyzer()
        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            Log.d(TAG, "captureSingleFrame: Received image proxy ${imageProxy.width}x${imageProxy.height}")
            detectionScope.launch {
                val items = processImageProxy(imageProxy, useStreamMode = false)  // Use SINGLE_IMAGE_MODE
                Log.d(TAG, "captureSingleFrame: Got ${items.size} items")
                withContext(Dispatchers.Main) {
                    onResult(items)
                }
                imageAnalysis?.clearAnalyzer()
            }
        }
    }

    /**
     * Starts continuous scanning mode.
     * Captures frames periodically (every ~1000ms) and runs detection.
     * Uses SINGLE_IMAGE_MODE for better accuracy.
     */
    fun startScanning(onResult: (List<ScannedItem>) -> Unit) {
        if (isScanning) {
            Log.d(TAG, "startScanning: Already scanning, ignoring")
            return
        }

        Log.d(TAG, "startScanning: Starting continuous scanning mode with SINGLE_IMAGE_MODE")
        isScanning = true

        var lastAnalysisTime = 0L
        val analysisIntervalMs = 1000L // Analyze every 1 second (slower but more accurate)
        var isProcessing = false // Prevent overlapping processing

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
                Log.d(TAG, "startScanning: Processing frame ${imageProxy.width}x${imageProxy.height}")

                detectionScope.launch {
                    try {
                        // Use SINGLE_IMAGE_MODE for better detection (same as tap capture)
                        val items = processImageProxy(imageProxy, useStreamMode = false)
                        Log.d(TAG, "startScanning: Got ${items.size} items")
                        if (items.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                onResult(items)
                            }
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
    }

    /**
     * Processes an ImageProxy: converts to Bitmap and runs ML Kit detection.
     */
    private suspend fun processImageProxy(imageProxy: ImageProxy, useStreamMode: Boolean = false): List<ScannedItem> {
        return try {
            // Convert ImageProxy to Bitmap
            val mediaImage = imageProxy.image ?: run {
                Log.e(TAG, "processImageProxy: mediaImage is null")
                return emptyList()
            }
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            Log.d(TAG, "processImageProxy: Processing ${imageProxy.width}x${imageProxy.height}, rotation=$rotationDegrees")

            // Build ML Kit image directly from camera buffer (avoids brittle YUV->Bitmap issues)
            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            // Optional bitmap for thumbnails; if conversion fails, we still return detections
            val bitmapForThumb = runCatching {
                val bitmap = imageProxy.toBitmap()
                Log.d(TAG, "processImageProxy: Created bitmap ${bitmap.width}x${bitmap.height}")
                rotateBitmap(bitmap, rotationDegrees)
            }.getOrElse { e ->
                Log.w(TAG, "processImageProxy: Failed to create bitmap", e)
                null
            }

            objectDetector.detectObjects(
                image = inputImage,
                sourceBitmap = bitmapForThumb,
                useStreamMode = useStreamMode
            )
        } catch (e: Exception) {
            Log.e(TAG, "processImageProxy: Error processing image", e)
            emptyList()
        } finally {
            imageProxy.close()
        }
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
        objectDetector.close()
        cameraExecutor.shutdown()
        detectionScope.cancel()
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
