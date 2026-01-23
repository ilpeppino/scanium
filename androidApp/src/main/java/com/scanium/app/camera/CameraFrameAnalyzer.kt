package com.scanium.app.camera

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.scanium.android.platform.adapters.toImageRefJpeg
import com.scanium.app.BuildConfig
import com.scanium.app.ObjectTracker
import com.scanium.app.ScannedItem
import com.scanium.app.items.CaptureType
import com.scanium.app.items.RawDetection
import com.scanium.app.camera.detection.DetectionEvent
import com.scanium.app.camera.detection.DetectionRouter
import com.scanium.app.camera.detection.DocumentCandidate
import com.scanium.app.camera.detection.DocumentCandidateDetector
import com.scanium.app.camera.detection.DocumentCandidateState
import com.scanium.app.ml.BarcodeDetectorClient
import com.scanium.app.ml.DetectionResult
import com.scanium.app.ml.DocumentTextRecognitionClient
import com.scanium.app.ml.ObjectDetectorClient
import com.scanium.app.perf.PerformanceMonitor
import com.scanium.android.platform.adapters.toRect
import com.scanium.shared.core.models.model.NormalizedRect
import com.scanium.core.models.scanning.GuidanceState
import com.scanium.core.models.scanning.ScanGuidanceState
import com.scanium.core.tracking.CandidateInfo
import com.scanium.core.tracking.ScanGuidanceManager
import com.scanium.shared.core.models.model.ImageRef
import com.scanium.telemetry.facade.Telemetry

internal class CameraFrameAnalyzer(
    private val telemetry: Telemetry?,
    private val objectDetector: ObjectDetectorClient,
    private val barcodeDetector: BarcodeDetectorClient,
    private val textRecognizer: DocumentTextRecognitionClient,
    private val detectionRouter: DetectionRouter,
    private val objectTracker: ObjectTracker,
    private val scanGuidanceManager: ScanGuidanceManager,
    private val documentCandidateDetector: DocumentCandidateDetector,
    private val imageConverter: CameraImageConverter,
    private val scanDiagnostics: CameraScanDiagnostics,
    private val getDocumentCandidateState: () -> DocumentCandidateState?,
    private val updateDocumentCandidateState: (DocumentCandidateState?) -> Unit,
    private val updateScanGuidanceState: (ScanGuidanceState) -> Unit,
) {
    companion object {
        private const val TAG = "CameraXManager"
        private const val DEFAULT_MOTION_SCORE = 0.2
        private const val LUMA_SAMPLE_STEP = 8
        private const val DOCUMENT_CANDIDATE_TTL_MS = 800L
        private const val DOCUMENT_CANDIDATE_MIN_CONFIDENCE = 0.45f
    }

    private var lastMotionScore = DEFAULT_MOTION_SCORE
    private val lumaSampleBuffers = arrayOfNulls<ByteArray>(2)
    private var currentLumaBufferIndex = 0
    private var lumaBufferSize = 0
    private var hasValidPreviousLumaSample = false

    private var lastCropRectLogTime = 0L
    private val cropRectLogIntervalMs = 5000L

    fun resetMotionTracking() {
        lastMotionScore = DEFAULT_MOTION_SCORE
    }

    fun getLastMotionScore(): Double = lastMotionScore

    fun analysisIntervalMsForMotion(motionScore: Double): Long =
        when {
            motionScore <= 0.1 -> 600L
            motionScore <= 0.5 -> 500L
            else -> 400L
        }

    fun computeMotionScore(imageProxy: ImageProxy): Double {
        val plane = imageProxy.planes.firstOrNull() ?: return lastMotionScore
        val width = imageProxy.width
        val height = imageProxy.height
        if (width == 0 || height == 0) return lastMotionScore

        val sampleWidth = (width + LUMA_SAMPLE_STEP - 1) / LUMA_SAMPLE_STEP
        val sampleHeight = (height + LUMA_SAMPLE_STEP - 1) / LUMA_SAMPLE_STEP
        val sampleSize = sampleWidth * sampleHeight

        if (lumaBufferSize != sampleSize) {
            lumaSampleBuffers[0] = ByteArray(sampleSize)
            lumaSampleBuffers[1] = ByteArray(sampleSize)
            lumaBufferSize = sampleSize
            currentLumaBufferIndex = 0
            hasValidPreviousLumaSample = false
            Log.d(TAG, "Allocated luma sample buffers: $sampleSize bytes each")
        }

        val currentSample = lumaSampleBuffers[currentLumaBufferIndex]!!
        val previousSample = lumaSampleBuffers[1 - currentLumaBufferIndex]!!

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

        currentLumaBufferIndex = 1 - currentLumaBufferIndex
        hasValidPreviousLumaSample = true
        lastMotionScore = motionScore
        return motionScore
    }

    fun updateDocumentCandidateState(
        candidate: DocumentCandidate?,
        timestampMs: Long,
    ) {
        val current = getDocumentCandidateState()
        if (candidate != null && candidate.confidence >= DOCUMENT_CANDIDATE_MIN_CONFIDENCE) {
            updateDocumentCandidateState(
                DocumentCandidateState(
                    candidate = candidate,
                    lastSeenMs = timestampMs,
                    averageProcessingMs = documentCandidateDetector.averageProcessingMs(),
                ),
            )
            return
        }

        if (current != null && timestampMs - current.lastSeenMs > DOCUMENT_CANDIDATE_TTL_MS) {
            updateDocumentCandidateState(null)
        }
    }

    /**
     * Creates a thumbnail from a bitmap using exact bounding box coordinates without tightening.
     *
     * This function implements the WYSIWYG principle: thumbnails match exactly what the user
     * sees in camera overlay bounding boxes. Unlike ML Kit's cropThumbnail which applies a
     * 4% tightening ratio, this preserves the exact bbox shown to the user.
     *
     * @param sourceBitmap Source bitmap (full frame)
     * @param normalizedBbox Bounding box in normalized coordinates (0-1)
     * @param rotationDegrees Rotation to apply for display orientation
     * @return ImageRef.Bytes containing the cropped and rotated thumbnail, or null on error
     */
    private fun createWysiwygThumbnail(
        sourceBitmap: Bitmap,
        normalizedBbox: NormalizedRect,
        rotationDegrees: Int = 0,
    ): ImageRef.Bytes? {
        return try {
            Log.d(
                TAG,
                "WYSIWYG: Creating thumbnail from ${sourceBitmap.width}x${sourceBitmap.height} bitmap, " +
                    "bbox=(${"%.3f".format(normalizedBbox.left)},${"%.3f".format(normalizedBbox.top)})-" +
                    "(${"%.3f".format(normalizedBbox.right)},${"%.3f".format(normalizedBbox.bottom)}), " +
                    "rotation=$rotationDegrees°"
            )

            // CRITICAL FIX: normalizedBbox is in UPRIGHT (display-oriented) coordinates,
            // but sourceBitmap is in SENSOR (pre-rotation) coordinates.
            // We need to apply inverse rotation to get sensor-space normalized coordinates.
            val (sensorNormLeft, sensorNormTop, sensorNormRight, sensorNormBottom) =
                when (rotationDegrees) {
                    0 -> {
                        listOf(normalizedBbox.left, normalizedBbox.top, normalizedBbox.right, normalizedBbox.bottom)
                    }
                    90 -> {
                        // Inverse of 90° clockwise rotation
                        // Upright (x, y) -> Sensor (y, 1-x)
                        listOf(normalizedBbox.top, 1f - normalizedBbox.right, normalizedBbox.bottom, 1f - normalizedBbox.left)
                    }
                    180 -> {
                        // Inverse of 180°
                        listOf(1f - normalizedBbox.right, 1f - normalizedBbox.bottom, 1f - normalizedBbox.left, 1f - normalizedBbox.top)
                    }
                    270 -> {
                        // Inverse of 270° clockwise rotation
                        // Upright (x, y) -> Sensor (1-y, x)
                        listOf(1f - normalizedBbox.bottom, normalizedBbox.left, 1f - normalizedBbox.top, normalizedBbox.right)
                    }
                    else -> {
                        listOf(normalizedBbox.left, normalizedBbox.top, normalizedBbox.right, normalizedBbox.bottom)
                    }
                }

            // Convert sensor-space normalized bbox to pixel coordinates
            val sensorBbox = NormalizedRect(
                left = sensorNormLeft,
                top = sensorNormTop,
                right = sensorNormRight,
                bottom = sensorNormBottom
            )
            val pixelBbox = sensorBbox.toRect(sourceBitmap.width, sourceBitmap.height)

            // Ensure bounding box is within bitmap bounds
            val left = pixelBbox.left.coerceIn(0, sourceBitmap.width - 1)
            val top = pixelBbox.top.coerceIn(0, sourceBitmap.height - 1)
            val width = pixelBbox.width().coerceIn(1, sourceBitmap.width - left)
            val height = pixelBbox.height().coerceIn(1, sourceBitmap.height - top)

            Log.d(
                TAG,
                "WYSIWYG: Pixel bbox ($left,$top) ${width}x$height from ${sourceBitmap.width}x${sourceBitmap.height} frame"
            )

            // Limit thumbnail size to save memory (match ML Kit's MAX_THUMBNAIL_DIMENSION_PX)
            val maxDimension = 512
            val scale = minOf(1.0f, maxDimension.toFloat() / maxOf(width, height))
            val thumbnailWidth = (width * scale).toInt().coerceAtLeast(1)
            val thumbnailHeight = (height * scale).toInt().coerceAtLeast(1)

            // Create thumbnail with scaled dimensions
            val croppedBitmap = Bitmap.createBitmap(thumbnailWidth, thumbnailHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(croppedBitmap)
            val srcRect = android.graphics.Rect(left, top, left + width, top + height)
            val dstRect = android.graphics.Rect(0, 0, thumbnailWidth, thumbnailHeight)
            canvas.drawBitmap(sourceBitmap, srcRect, dstRect, null)

            // Rotate thumbnail to match display orientation
            val rotatedBitmap = if (rotationDegrees != 0) {
                val matrix = android.graphics.Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
                val rotated = Bitmap.createBitmap(
                    croppedBitmap,
                    0,
                    0,
                    croppedBitmap.width,
                    croppedBitmap.height,
                    matrix,
                    true
                )
                croppedBitmap.recycle() // Free the unrotated bitmap
                rotated
            } else {
                croppedBitmap
            }

            // Convert to ImageRef.Bytes
            val imageRef = rotatedBitmap.toImageRefJpeg(quality = 85)
            rotatedBitmap.recycle() // Clean up after conversion

            Log.d(
                TAG,
                "Created WYSIWYG thumbnail: ${imageRef.width}x${imageRef.height} (rotation: $rotationDegrees°)"
            )
            imageRef
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create WYSIWYG thumbnail", e)
            null
        }
    }

    suspend fun processImageProxy(
        imageProxy: ImageProxy,
        scanMode: ScanMode,
        isScanning: Boolean,
        edgeInsetRatio: Float,
        useStreamMode: Boolean = false,
        onDetectionEvent: (DetectionEvent) -> Unit = {},
    ): Pair<List<RawDetection>, List<DetectionResult>> {
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

            val mediaImage =
                imageProxy.image ?: run {
                    Log.e(TAG, "processImageProxy: mediaImage is null")
                    return Pair(emptyList<RawDetection>(), emptyList<DetectionResult>())
                }
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            val now = System.currentTimeMillis()
            if (now - lastCropRectLogTime >= cropRectLogIntervalMs) {
                Log.i(TAG, "[VIEWPORT] image=${imageProxy.width}x${imageProxy.height}, rotation=$rotationDegrees")
                lastCropRectLogTime = now
            }

            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            val lazyBitmapProvider: () -> Bitmap? = {
                if (cachedBitmap == null) {
                    cachedBitmap =
                        runCatching {
                            val bitmap = imageConverter.toBitmap(imageProxy)
                            Log.i(
                                TAG,
                                ">>> processImageProxy: [LAZY] Created bitmap ${bitmap.width}x${bitmap.height}, rotation=$rotationDegrees",
                            )
                            bitmap
                        }.getOrElse { e ->
                            Log.w(TAG, "processImageProxy: Failed to create bitmap", e)
                            null
                        }
                }
                cachedBitmap
            }

            val imageBoundsForFiltering = android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height)

            when (scanMode) {
                ScanMode.OBJECT_DETECTION -> {
                    processObjectDetectionMode(
                        inputImage = inputImage,
                        lazyBitmapProvider = lazyBitmapProvider,
                        imageBoundsForFiltering = imageBoundsForFiltering,
                        onDetectionEvent = onDetectionEvent,
                        edgeInsetRatio = edgeInsetRatio,
                        isScanning = isScanning,
                    )
                }

                ScanMode.BARCODE -> {
                    processBarcodeMode(inputImage, lazyBitmapProvider, onDetectionEvent)
                }

                ScanMode.DOCUMENT_TEXT -> {
                    processDocumentTextMode(inputImage, lazyBitmapProvider, onDetectionEvent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, ">>> processImageProxy: ERROR", e)
            span?.recordError(e.message ?: "Unknown error")
            Pair(emptyList<RawDetection>(), emptyList<DetectionResult>())
        } finally {
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

    private suspend fun processObjectDetectionMode(
        inputImage: InputImage,
        lazyBitmapProvider: () -> Bitmap?,
        imageBoundsForFiltering: android.graphics.Rect,
        onDetectionEvent: (DetectionEvent) -> Unit,
        edgeInsetRatio: Float,
        isScanning: Boolean,
    ): Pair<List<RawDetection>, List<DetectionResult>> =
        if (isScanning) {
            Log.i(TAG, ">>> processImageProxy: Taking TRACKING PATH (isScanning=$isScanning)")
            val (rawDetections, detections) =
                processObjectDetectionWithTracking(
                    inputImage = inputImage,
                    lazyBitmapProvider = lazyBitmapProvider,
                    cropRect = imageBoundsForFiltering,
                    edgeInsetRatio = edgeInsetRatio,
                )
            Log.i(
                TAG,
                ">>> processImageProxy: Tracking path returned ${rawDetections.size} detections and ${detections.size} detection results",
            )
            // Detection router now gets empty list since items don't exist yet (pending state)
            val event = detectionRouter.processObjectResults(emptyList(), detections)
            onDetectionEvent(event)
            Pair(rawDetections, detections)
        } else {
            Log.i(TAG, ">>> processImageProxy: Taking SINGLE-SHOT PATH (isScanning=$isScanning)")
            val response =
                objectDetector.detectObjects(
                    image = inputImage,
                    sourceBitmap = lazyBitmapProvider,
                    useStreamMode = false,
                    cropRect = imageBoundsForFiltering,
                    edgeInsetRatio = edgeInsetRatio,
                )
            Log.i(TAG, ">>> processImageProxy: Single-shot path returned ${response.scannedItems.size} items")

            // Capture bitmap once for all detections (for cloud classification and WYSIWYG thumbnails)
            val fullFrameBitmap = lazyBitmapProvider()

            // Generate unique capture ID - all detections from this frame share the same ID
            // This prevents aggregation from merging multiple objects from the same capture
            val captureId = java.util.UUID.randomUUID().toString()

            // Convert ScannedItems to RawDetections for pending state
            val rawDetections = response.scannedItems.map { item ->
                // CRITICAL: Each detection needs its OWN copy of the bitmap
                // If multiple detections share the same bitmap reference, deleting one item
                // will recycle the bitmap that other pending detections are still using,
                // causing thumbnail corruption. Create a separate copy for each detection.
                val bitmapCopy = fullFrameBitmap?.copy(
                    fullFrameBitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888,
                    false
                )

                // WYSIWYG FIX: Create thumbnail from exact bounding box (no tightening)
                // This ensures thumbnail matches what user sees in camera overlay
                val bbox = item.boundingBox
                val wysiwygThumbnail = if (fullFrameBitmap != null && bbox != null) {
                    createWysiwygThumbnail(
                        sourceBitmap = fullFrameBitmap,
                        normalizedBbox = bbox,
                        rotationDegrees = inputImage.rotationDegrees
                    )
                } else null

                RawDetection(
                    boundingBox = item.boundingBox,
                    confidence = item.confidence,
                    onDeviceLabel = item.labelText ?: "Unknown",
                    onDeviceCategory = item.category,
                    trackingId = item.id, // Use item ID as tracking ID
                    frameSharpness = 1.0f, // TODO: Get actual sharpness if available
                    captureType = CaptureType.SINGLE_SHOT,
                    thumbnailRef = wysiwygThumbnail, // WYSIWYG thumbnail from exact bbox
                    fullFrameBitmap = bitmapCopy
                )
            }

            // Detection router gets empty list since items don't exist yet (pending state)
            val event = detectionRouter.processObjectResults(emptyList(), response.detectionResults)
            onDetectionEvent(event)
            Pair(rawDetections, response.detectionResults)
        }

    private suspend fun processBarcodeMode(
        inputImage: InputImage,
        lazyBitmapProvider: () -> Bitmap?,
        onDetectionEvent: (DetectionEvent) -> Unit,
    ): Pair<List<RawDetection>, List<DetectionResult>> {
        val canRun = detectionRouter.tryInvokeBarcodeDetection()
        return if (!canRun) {
            Log.d(TAG, "[BARCODE] Throttled - skipping frame")
            Pair(emptyList<RawDetection>(), emptyList<DetectionResult>())
        } else {
            val rawItems =
                barcodeDetector.scanBarcodes(
                    image = inputImage,
                    sourceBitmap = lazyBitmapProvider,
                )

            // TODO Phase 1: Barcode mode returns empty for now
            // Will be updated in future phases to create RawDetections
            Log.i(TAG, "[BARCODE] Detected ${rawItems.size} barcodes (Phase 1: not creating items yet)")
            Pair(emptyList<RawDetection>(), emptyList<DetectionResult>())
        }
    }

    private suspend fun processDocumentTextMode(
        inputImage: InputImage,
        lazyBitmapProvider: () -> Bitmap?,
        onDetectionEvent: (DetectionEvent) -> Unit,
    ): Pair<List<RawDetection>, List<DetectionResult>> {
        // TODO Phase 1: Document text mode returns empty for now
        // Will be updated in future phases to create RawDetections
        Log.i(TAG, "[DOCUMENT] Document text mode (Phase 1: not creating items yet)")
        return Pair(emptyList<RawDetection>(), emptyList<DetectionResult>())
    }

    private suspend fun processObjectDetectionWithTracking(
        inputImage: InputImage,
        lazyBitmapProvider: () -> Bitmap?,
        cropRect: android.graphics.Rect,
        edgeInsetRatio: Float,
        analyzerLatencyMs: Long = 0,
    ): Pair<List<RawDetection>, List<DetectionResult>> {
        Log.i(TAG, ">>> processObjectDetectionWithTracking: CALLED")

        val trackingResponse =
            objectDetector.detectObjectsWithTracking(
                image = inputImage,
                sourceBitmap = lazyBitmapProvider,
                useStreamMode = false,
                cropRect = cropRect,
                edgeInsetRatio = edgeInsetRatio,
            )

        Log.i(
            TAG,
            ">>> processObjectDetectionWithTracking: Got ${trackingResponse.detectionInfos.size} DetectionInfo objects and ${trackingResponse.detectionResults.size} DetectionResult objects from a SINGLE detection pass",
        )

        // Capture bitmap for sharpness calculation and cloud classification
        val fullFrameBitmap = lazyBitmapProvider()
        val frameSharpness =
            fullFrameBitmap?.let { bitmap ->
                SharpnessCalculator.calculateSharpness(bitmap)
            } ?: 0f

        val frameId =
            com.scanium.app.camera.detection.LiveScanDiagnostics
                .nextFrameId()
        scanDiagnostics.logSharpness(
            frameId = frameId,
            sharpnessScore = frameSharpness,
            isBlurry = frameSharpness < SharpnessCalculator.DEFAULT_MIN_SHARPNESS,
            threshold = SharpnessCalculator.DEFAULT_MIN_SHARPNESS,
        )

        val currentRoi = scanGuidanceManager.getCurrentRoi()

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

        val guidanceState =
            scanGuidanceManager.processFrame(
                candidate = bestCandidateInfo,
                motionScore = lastMotionScore.toFloat(),
                sharpnessScore = frameSharpness,
                currentTimeMs = System.currentTimeMillis(),
            )
        updateScanGuidanceState(guidanceState)

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

        val stats = objectTracker.getStats()
        Log.i(
            TAG,
            ">>> Tracker stats: active=${stats.activeCandidates}, confirmed=${stats.confirmedCandidates}, frame=${stats.currentFrame}",
        )

        val canAddItems = guidanceState.canAddItem
        val isLocked = guidanceState.state == GuidanceState.LOCKED

        val detectionsToAdd =
            if (canAddItems && isLocked) {
                val allConfirmedCandidates = objectTracker.getConfirmedCandidates()
                Log.i(
                    TAG,
                    ">>> LOCKED state: checking ${allConfirmedCandidates.size} total confirmed candidates (${confirmedCandidates.size} newly confirmed)",
                )

                // Generate unique capture ID for this lock event - all candidates share the same ID
                // This prevents aggregation from merging multiple objects from the same lock
                val captureId = java.util.UUID.randomUUID().toString()

                allConfirmedCandidates.mapNotNull { candidate ->
                    val bbox = candidate.boundingBoxNorm ?: return@mapNotNull null

                    // Strict ROI check: entire bounding box must be inside ROI
                    val isInsideRoi = currentRoi.containsBox(bbox.left, bbox.top, bbox.right, bbox.bottom)
                    if (!isInsideRoi) {
                        Log.e(
                            TAG,
                            "!!! ASSERTION FAILED: Confirmed candidate ${candidate.internalId} is OUTSIDE ROI (bbox=$bbox, roi=$currentRoi)",
                        )
                        if (BuildConfig.DEBUG && Log.isLoggable(TAG, Log.ASSERT)) {
                            throw IllegalStateException("Confirmed candidate outside ROI - this should never happen")
                        }
                        null
                    } else {
                        // CRITICAL: Each detection needs its OWN copy of the bitmap
                        // If multiple detections share the same bitmap reference, deleting one item
                        // will recycle the bitmap that other pending detections are still using,
                        // causing thumbnail corruption. Create a separate copy for each detection.
                        val bitmapCopy = fullFrameBitmap?.copy(
                            fullFrameBitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888,
                            false
                        )

                        // WYSIWYG FIX: Create thumbnail from exact bounding box (no tightening)
                        // This ensures thumbnail matches what user sees in camera overlay
                        val candidateBbox = candidate.boundingBox
                        val wysiwygThumbnail = if (fullFrameBitmap != null && candidateBbox != null) {
                            createWysiwygThumbnail(
                                sourceBitmap = fullFrameBitmap,
                                normalizedBbox = candidateBbox,
                                rotationDegrees = inputImage.rotationDegrees
                            )
                        } else null

                        // Create raw detection instead of ScannedItem
                        // Item will be created later after user confirms hypothesis
                        val rawDetection = RawDetection(
                            boundingBox = candidate.boundingBox,
                            confidence = candidate.maxConfidence,
                            onDeviceLabel = candidate.labelText.takeIf { it.isNotBlank() } ?: "Unknown",
                            onDeviceCategory = candidate.category,
                            trackingId = candidate.internalId,
                            frameSharpness = frameSharpness,
                            captureType = CaptureType.TRACKING,
                            thumbnailRef = wysiwygThumbnail, // WYSIWYG thumbnail from exact bbox
                            fullFrameBitmap = bitmapCopy
                        )
                        objectTracker.markCandidateConsumed(candidate.internalId)
                        Log.i(TAG, ">>> Created RawDetection from candidate ${candidate.internalId}, marked as consumed")
                        rawDetection
                    }
                }
            } else {
                val totalConfirmed = objectTracker.getStats().confirmedCandidates
                if (totalConfirmed > 0) {
                    Log.d(
                        TAG,
                        ">>> Waiting for LOCKED state: $totalConfirmed confirmed candidates pending (canAddItem=$canAddItems, isLocked=$isLocked)",
                    )
                }
                emptyList()
            }

        Log.i(TAG, ">>> processObjectDetectionWithTracking: Converted to ${detectionsToAdd.size} RawDetections (gated by LOCKED=$isLocked)")
        detectionsToAdd.forEachIndexed { index, detection ->
            Log.i(TAG, "    RawDetection $index: label=${detection.onDeviceLabel}, category=${detection.onDeviceCategory}, confidence=${detection.confidence}")
        }

        Log.i(
            TAG,
            ">>> processObjectDetectionWithTracking: RETURNING ${detectionsToAdd.size} detections and ${trackingResponse.detectionResults.size} detection results",
        )
        return Pair(detectionsToAdd, trackingResponse.detectionResults)
    }
}
