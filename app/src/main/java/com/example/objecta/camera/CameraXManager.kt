package com.example.objecta.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.objecta.items.ScannedItem
import com.example.objecta.ml.ObjectDetectorClient
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.*
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
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

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
        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            detectionScope.launch {
                val items = processImageProxy(imageProxy)
                withContext(Dispatchers.Main) {
                    onResult(items)
                }
                imageAnalysis?.clearAnalyzer()
            }
        }
    }

    /**
     * Starts continuous scanning mode.
     * Captures frames periodically (every ~600ms) and runs detection.
     */
    fun startScanning(onResult: (List<ScannedItem>) -> Unit) {
        if (isScanning) return
        isScanning = true

        var lastAnalysisTime = 0L
        val analysisIntervalMs = 600L // Analyze every 600ms

        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            val currentTime = System.currentTimeMillis()

            if (currentTime - lastAnalysisTime >= analysisIntervalMs) {
                lastAnalysisTime = currentTime

                detectionScope.launch {
                    val items = processImageProxy(imageProxy)
                    withContext(Dispatchers.Main) {
                        onResult(items)
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
        isScanning = false
        imageAnalysis?.clearAnalyzer()
        scanJob?.cancel()
        scanJob = null
    }

    /**
     * Processes an ImageProxy: converts to Bitmap and runs ML Kit detection.
     */
    private suspend fun processImageProxy(imageProxy: ImageProxy): List<ScannedItem> {
        return try {
            // Convert ImageProxy to Bitmap
            val bitmap = imageProxy.toBitmap()

            // Rotate bitmap if needed based on image rotation
            val rotatedBitmap = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)

            // Create InputImage for ML Kit
            val inputImage = InputImage.fromBitmap(rotatedBitmap, 0)

            // Run detection
            val items = objectDetector.detectObjects(inputImage, rotatedBitmap)

            items
        } catch (e: Exception) {
            e.printStackTrace()
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
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // For YUV format, we need to convert. For simplicity in PoC, we'll use a basic approach.
        // In production, use a proper YUV to RGB converter.
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
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
