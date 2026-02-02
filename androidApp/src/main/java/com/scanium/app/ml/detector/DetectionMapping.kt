package com.scanium.app.ml.detector

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.objects.DetectedObject
import com.scanium.android.platform.adapters.toImageRefJpeg
import com.scanium.android.platform.adapters.toNormalizedRect
import com.scanium.app.ItemCategory
import com.scanium.app.ScannedItem
import com.scanium.app.debug.ImageClassifierDebugger
import com.scanium.app.ml.DetectionResult
import com.scanium.app.perf.PerformanceMonitor
import com.scanium.app.tracking.DetectionInfo
import com.scanium.core.models.ml.LabelWithConfidence
import com.scanium.core.models.ml.RawDetection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Pure mapping/conversion layer for ML Kit detections.
 *
 * This class contains deterministic, side-effect-free (where possible) functions
 * for converting raw ML Kit DetectedObjects into application domain models.
 *
 * Responsibilities:
 * - Coordinate transforms (upright <-> sensor space)
 * - Bounding box normalization
 * - Category extraction from ML Kit labels
 * - Thumbnail cropping with rotation
 * - Edge zone filtering (pure geometry)
 *
 * Threading: All functions are thread-safe and can be called from any dispatcher.
 */
object DetectionMapping {
    private const val TAG = "DetectionMapping"
    const val CONFIDENCE_THRESHOLD = 0.3f // Category assignment threshold
    const val MAX_THUMBNAIL_DIMENSION_PX = 512
    const val BOUNDING_BOX_TIGHTEN_RATIO = 0.04f

    // Optional debugger for dev-only instrumentation
    var debugger: ImageClassifierDebugger? = null

    // Bounding box validation thresholds
    private const val MAX_BBOX_AREA_RATIO = 0.70f // Reject if bbox > 70% of frame
    private const val MAX_ASPECT_RATIO = 5.0f // Reject if width/height > 5 or < 0.2
    private const val MIN_ASPECT_RATIO = 0.2f

    // Rate-limited logging for dropped detections
    @Volatile
    private var lastEdgeDropLogTime = 0L

    @Volatile
    private var lastOversizedDropLogTime = 0L

    @Volatile
    private var lastAspectRatioDropLogTime = 0L
    private const val EDGE_DROP_LOG_INTERVAL_MS = 5000L
    private const val BBOX_VALIDATION_LOG_INTERVAL_MS = 5000L

    /**
     * PHASE 3: Geometry-based filtering (ZERO image cropping)
     * Checks if a detection's bounding box is fully contained within the safe zone with inset margin.
     * This eliminates detections at screen edges that are likely partial/cut-off objects.
     *
     * Updated to check all four corners instead of just the center to prevent large boxes
     * from passing validation when they extend far beyond the safe zone.
     *
     * @param bbox Detection bounding box in absolute pixel coordinates
     * @param cropRect Visible viewport rect (from ImageProxy.cropRect)
     * @param edgeInsetRatio Margin ratio for edge gating (0.0 to 0.5)
     * @return true if detection should be kept, false if it should be dropped
     */
    fun isDetectionInsideSafeZone(
        bbox: Rect,
        cropRect: Rect?,
        edgeInsetRatio: Float,
    ): Boolean {
        // If no cropRect provided, accept all detections (no filtering)
        if (cropRect == null || edgeInsetRatio <= 0f) return true

        // Calculate inset safe zone (inset from each edge)
        val insetX = (cropRect.width() * edgeInsetRatio).toInt()
        val insetY = (cropRect.height() * edgeInsetRatio).toInt()

        val safeLeft = cropRect.left + insetX
        val safeRight = cropRect.right - insetX
        val safeTop = cropRect.top + insetY
        val safeBottom = cropRect.bottom - insetY

        // Calculate bbox area for adaptive checking
        val bboxArea = bbox.width() * bbox.height()
        val frameArea = cropRect.width() * cropRect.height()
        val areaRatio = if (frameArea > 0) bboxArea.toFloat() / frameArea.toFloat() else 0f

        // For large detections (>40% of frame), require all corners inside safe zone
        // For smaller detections, only require center inside (less strict)
        val requireAllCorners = areaRatio > 0.40f

        val isInside =
            if (requireAllCorners) {
                // All four corners must be inside safe zone
                bbox.left >= safeLeft && bbox.right <= safeRight &&
                    bbox.top >= safeTop && bbox.bottom <= safeBottom
            } else {
                // Only center needs to be inside (original behavior for small objects)
                val centerX = (bbox.left + bbox.right) / 2
                val centerY = (bbox.top + bbox.bottom) / 2
                centerX >= safeLeft && centerX <= safeRight &&
                    centerY >= safeTop && centerY <= safeBottom
            }

        // Rate-limited logging for edge drops
        if (!isInside) {
            val now = System.currentTimeMillis()
            if (now - lastEdgeDropLogTime >= EDGE_DROP_LOG_INTERVAL_MS) {
                Log.d(
                    TAG,
                    "[EDGE_FILTER] Dropped detection at edge: bbox=($bbox), " +
                        "safeZone=($safeLeft,$safeTop)-($safeRight,$safeBottom), " +
                        "areaRatio=${(areaRatio * 100).toInt()}%, strictMode=$requireAllCorners",
                )
                lastEdgeDropLogTime = now
            }
        }

        return isInside
    }

    /**
     * Validates bounding box size and aspect ratio to filter out problematic detections.
     * Rejects detections that are:
     * - Too large (>70% of frame area) - likely background/floor included
     * - Extreme aspect ratios (>5:1 or <1:5) - likely erroneous detections
     *
     * @param bbox Detection bounding box in absolute pixel coordinates
     * @param frameWidth Frame width in pixels
     * @param frameHeight Frame height in pixels
     * @return true if bbox passes validation, false if it should be rejected
     */
    fun isBoundingBoxValid(
        bbox: Rect,
        frameWidth: Int,
        frameHeight: Int,
    ): Boolean {
        if (frameWidth <= 0 || frameHeight <= 0) return true

        // Calculate bbox dimensions
        val bboxWidth = bbox.width().coerceAtLeast(1)
        val bboxHeight = bbox.height().coerceAtLeast(1)

        // Check area ratio
        val frameArea = frameWidth * frameHeight
        val bboxArea = bboxWidth * bboxHeight
        val areaRatio = bboxArea.toFloat() / frameArea.toFloat()

        if (areaRatio > MAX_BBOX_AREA_RATIO) {
            val now = System.currentTimeMillis()
            if (now - lastOversizedDropLogTime >= BBOX_VALIDATION_LOG_INTERVAL_MS) {
                Log.w(
                    TAG,
                    "[BBOX_VALIDATION] Rejected oversized detection: area=${(areaRatio * 100).toInt()}% " +
                        "(${bboxWidth}x$bboxHeight in ${frameWidth}x$frameHeight frame), " +
                        "threshold=${(MAX_BBOX_AREA_RATIO * 100).toInt()}%",
                )
                lastOversizedDropLogTime = now
            }
            return false
        }

        // Check aspect ratio (width/height)
        val aspectRatio = bboxWidth.toFloat() / bboxHeight.toFloat()

        if (aspectRatio > MAX_ASPECT_RATIO || aspectRatio < MIN_ASPECT_RATIO) {
            val now = System.currentTimeMillis()
            if (now - lastAspectRatioDropLogTime >= BBOX_VALIDATION_LOG_INTERVAL_MS) {
                Log.w(
                    TAG,
                    "[BBOX_VALIDATION] Rejected detection with extreme aspect ratio: " +
                        "ratio=${"%.2f".format(aspectRatio)} (${bboxWidth}x$bboxHeight), " +
                        "valid range: $MIN_ASPECT_RATIO-$MAX_ASPECT_RATIO",
                )
                lastAspectRatioDropLogTime = now
            }
            return false
        }

        return true
    }

    /**
     * Converts sensor (bitmap) coordinates to InputImage (upright) coordinates.
     *
     * ML Kit returns bounding boxes in InputImage coordinate space (upright, post-rotation).
     * InputImage.width/height are the dimensions AFTER rotation metadata is applied.
     *
     * When the bitmap is in sensor orientation (unrotated), we need to transform
     * bbox coordinates from InputImage space to sensor space for cropping.
     *
     * @param uprightBbox Bounding box in InputImage/upright space
     * @param inputImageWidth InputImage.width (upright dimensions)
     * @param inputImageHeight InputImage.height (upright dimensions)
     * @param rotationDegrees Rotation passed to InputImage.fromMediaImage()
     * @return Bounding box in sensor (unrotated bitmap) coordinates
     */
    fun uprightBboxToSensorBbox(
        uprightBbox: Rect,
        inputImageWidth: Int,
        inputImageHeight: Int,
        rotationDegrees: Int,
    ): Rect {
        // Normalize in upright space first
        val normLeft = uprightBbox.left.toFloat() / inputImageWidth
        val normTop = uprightBbox.top.toFloat() / inputImageHeight
        val normRight = uprightBbox.right.toFloat() / inputImageWidth
        val normBottom = uprightBbox.bottom.toFloat() / inputImageHeight

        // Calculate sensor dimensions
        val (sensorW, sensorH) =
            when (rotationDegrees) {
                90, 270 -> Pair(inputImageHeight, inputImageWidth)
                else -> Pair(inputImageWidth, inputImageHeight)
            }

        // Apply inverse rotation to get sensor-space normalized coordinates
        val (sensorNormLeft, sensorNormTop, sensorNormRight, sensorNormBottom) =
            when (rotationDegrees) {
                0 -> {
                    listOf(normLeft, normTop, normRight, normBottom)
                }

                90 -> {
                    // Inverse of 90° clockwise rotation
                    // Upright (x, y) -> Sensor (y, 1-x)
                    listOf(normTop, 1f - normRight, normBottom, 1f - normLeft)
                }

                180 -> {
                    // Inverse of 180° is 180°
                    listOf(1f - normRight, 1f - normBottom, 1f - normLeft, 1f - normTop)
                }

                270 -> {
                    // Inverse of 270° clockwise rotation
                    // Upright (x, y) -> Sensor (1-y, x)
                    listOf(1f - normBottom, normLeft, 1f - normTop, normRight)
                }

                else -> {
                    listOf(normLeft, normTop, normRight, normBottom)
                }
            }

        // Convert back to pixel coordinates in sensor space
        return Rect(
            (sensorNormLeft * sensorW).toInt(),
            (sensorNormTop * sensorH).toInt(),
            (sensorNormRight * sensorW).toInt(),
            (sensorNormBottom * sensorH).toInt(),
        )
    }

    /**
     * Extracts the best category from ML Kit labels using multi-label consensus.
     * ML Kit provides a list of labels with confidence scores.
     *
     * Previously used only the highest-confidence label, which could be incorrect
     * (e.g., "T-shirt" at 0.45 instead of "Laptop" at 0.42).
     * Now uses CategoryResolver to consider top N labels for better accuracy.
     *
     * See: howto/app/debugging/RCA_MACBOOK_TSHIRT_MISCLASSIFICATION.md
     */
    fun extractCategory(detectedObject: DetectedObject): ItemCategory {
        return CategoryResolver.resolveCategoryFromLabels(
            detectedObject = detectedObject,
            confidenceThreshold = CONFIDENCE_THRESHOLD,
        )
    }

    /**
     * Extracts DetectionInfo from a DetectedObject for tracking purposes.
     *
     * @param detectedObject ML Kit detected object with bbox in InputImage (upright) coordinates
     * @param sourceBitmap Source bitmap in sensor orientation (unrotated)
     * @param imageRotationDegrees Rotation from sensor to upright orientation
     * @param uprightWidth InputImage width (upright dimensions)
     * @param uprightHeight InputImage height (upright dimensions)
     * @param onRawDetection Optional callback to receive raw detection data
     */
    fun extractDetectionInfo(
        detectedObject: DetectedObject,
        sourceBitmap: Bitmap?,
        imageRotationDegrees: Int,
        uprightWidth: Int,
        uprightHeight: Int,
        onRawDetection: (RawDetection) -> Unit = {},
    ): DetectionInfo? =
        try {
            // Extract tracking ID (may be null)
            val trackingId = detectedObject.trackingId?.toString()

            // Get bounding box (in InputImage/upright coordinate space)
            val uprightBbox = detectedObject.boundingBox

            val labels =
                detectedObject.labels.mapIndexed { index, label ->
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
            val confidence =
                labelConfidence.takeIf { it > 0f } ?: run {
                    // Objects detected without classification get a reasonable confidence
                    if (detectedObject.trackingId != null) {
                        0.6f // Good confidence for tracked but unlabeled objects
                    } else {
                        0.4f // Moderate confidence for objects without tracking
                    }
                }

            val labelText = bestLabel?.text ?: "Object"

            // Determine category
            val category =
                if (labelConfidence >= CONFIDENCE_THRESHOLD) {
                    ItemCategory.fromMlKitLabel(bestLabel?.text)
                } else {
                    ItemCategory.UNKNOWN
                }

            // For thumbnail cropping: convert upright bbox to sensor space
            val sensorBbox =
                uprightBboxToSensorBbox(
                    uprightBbox = uprightBbox,
                    inputImageWidth = uprightWidth,
                    inputImageHeight = uprightHeight,
                    rotationDegrees = imageRotationDegrees,
                )

            // Crop thumbnail with rotation for correct display orientation
            val thumbnail = sourceBitmap?.let { cropThumbnail(it, sensorBbox, imageRotationDegrees) }
            val thumbnailQuality =
                if (thumbnail != null) {
                    com.scanium.app.camera.ImageUtils
                        .calculateSharpness(thumbnail)
                        .toFloat()
                } else {
                    0f
                }
            val thumbnailRef = thumbnail?.toImageRefJpeg(quality = 85)

            // Normalize using upright dimensions (keeps bbox in upright coordinate space)
            val bboxNorm = uprightBbox.toNormalizedRect(uprightWidth, uprightHeight)

            // Calculate normalized area
            val normalizedBoxArea = bboxNorm.area

            Log.d(
                TAG,
                "extractDetectionInfo: trackingId=$trackingId, confidence=$confidence (label=$labelConfidence), category=$category, area=$normalizedBoxArea, quality=$thumbnailQuality",
            )

            onRawDetection(
                RawDetection(
                    trackingId = trackingId ?: "gen_${UUID.randomUUID()}",
                    bboxNorm = bboxNorm,
                    labels = labels,
                    thumbnailRef = thumbnailRef,
                ),
            )

            DetectionInfo(
                trackingId = trackingId,
                boundingBox = bboxNorm,
                confidence = confidence,
                category = category,
                labelText = labelText,
                thumbnail = thumbnailRef,
                normalizedBoxArea = normalizedBoxArea,
                qualityScore = thumbnailQuality,
                labels = labels, // Preserve all labels for enrichment-based category refinement
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting detection info", e)
            null
        }

    /**
     * Converts a DetectedObject from ML Kit to a ScannedItem.
     *
     * @param detectedObject ML Kit detected object with bbox in InputImage (upright) coordinates
     * @param sourceBitmap Source bitmap in sensor orientation (unrotated)
     * @param imageRotationDegrees Rotation from sensor to upright orientation
     * @param uprightWidth InputImage width (upright dimensions)
     * @param uprightHeight InputImage height (upright dimensions)
     */
    fun convertToScannedItem(
        detectedObject: DetectedObject,
        sourceBitmap: Bitmap?,
        imageRotationDegrees: Int,
        uprightWidth: Int,
        uprightHeight: Int,
    ): ScannedItem? =
        try {
            // Extract tracking ID (null if not available)
            val trackingId =
                detectedObject.trackingId?.toString()
                    ?: java.util.UUID
                        .randomUUID()
                        .toString()

            // Get bounding box (in InputImage/upright coordinate space)
            val uprightBbox = detectedObject.boundingBox

            // For thumbnail cropping: convert upright bbox to sensor space
            // because sourceBitmap is in sensor orientation (unrotated)
            val sensorBbox =
                uprightBboxToSensorBbox(
                    uprightBbox = uprightBbox,
                    inputImageWidth = uprightWidth,
                    inputImageHeight = uprightHeight,
                    rotationDegrees = imageRotationDegrees,
                )

            // Crop thumbnail from source bitmap using sensor-space bbox, then rotate
            val thumbnail = sourceBitmap?.let { cropThumbnail(it, sensorBbox, imageRotationDegrees) }
            val thumbnailQuality =
                if (thumbnail != null) {
                    com.scanium.app.camera.ImageUtils
                        .calculateSharpness(thumbnail)
                        .toFloat()
                } else {
                    0f
                }
            val thumbnailRef = thumbnail?.toImageRefJpeg(quality = 85)

            // Determine category from labels and get confidence
            val bestLabel = detectedObject.labels.maxByOrNull { it.confidence }
            val labelConfidence = bestLabel?.confidence ?: 0f
            val category = extractCategory(detectedObject)

            // Preserve all labels for category refinement during enrichment
            val labels =
                detectedObject.labels.mapIndexed { index, label ->
                    LabelWithConfidence(
                        text = label.text,
                        confidence = label.confidence,
                        index = index,
                    )
                }

            // Use effective confidence (fallback for objects without classification)
            val confidence =
                labelConfidence.takeIf { it > 0f } ?: run {
                    // Objects detected without classification get a higher confidence
                    // ML Kit's object detection is reliable even without classification
                    if (detectedObject.trackingId != null) {
                        0.6f // Good confidence for tracked but unlabeled objects
                    } else {
                        0.4f // Moderate confidence for objects without tracking
                    }
                }

            // Normalize bounding box using upright dimensions
            // This keeps bbox in upright coordinate space for overlay drawing
            val normalizedBox = uprightBbox.toNormalizedRect(uprightWidth, uprightHeight)

            ScannedItem(
                id = trackingId,
                thumbnail = thumbnailRef,
                thumbnailRef = thumbnailRef,
                category = category,
                priceRange = 0.0 to 0.0,
                confidence = confidence,
                boundingBox = normalizedBox,
                labelText = bestLabel?.text,
                qualityScore = thumbnailQuality,
                mlKitLabels = labels, // Preserve all labels for category refinement
            )
        } catch (e: Exception) {
            // If cropping or processing fails, skip this object
            null
        }

    /**
     * Converts a DetectedObject from ML Kit to a DetectionResult for overlay rendering.
     */
    fun convertToDetectionResult(
        detectedObject: DetectedObject,
        imageWidth: Int,
        imageHeight: Int,
    ): DetectionResult? =
        try {
            val boundingBox = detectedObject.boundingBox
            val category = extractCategory(detectedObject)

            // Get best confidence score
            val confidence = detectedObject.labels.maxByOrNull { it.confidence }?.confidence ?: 0f

            DetectionResult(
                bboxNorm = boundingBox.toNormalizedRect(imageWidth, imageHeight),
                category = category,
                priceRange = 0.0 to 0.0,
                confidence = confidence,
                trackingId = detectedObject.trackingId,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error converting to DetectionResult", e)
            null
        }

    /**
     * Crops a thumbnail from the source bitmap using the bounding box.
     *
     * @param source Source bitmap (in original sensor orientation)
     * @param boundingBox Bounding box in source bitmap coordinates
     * @param rotationDegrees Rotation to apply after cropping for correct display orientation
     */
    fun cropThumbnail(
        source: Bitmap,
        boundingBox: Rect,
        rotationDegrees: Int = 0,
    ): Bitmap? =
        PerformanceMonitor.measure(
            metricName = PerformanceMonitor.Metrics.THUMBNAIL_CROP_LATENCY_MS,
            spanName = PerformanceMonitor.Spans.THUMBNAIL_CROP,
            attributes = mapOf("source_size" to "${source.width}x${source.height}"),
        ) {
            try {
                val adjustedBox =
                    boundingBox.tighten(
                        insetRatio = BOUNDING_BOX_TIGHTEN_RATIO,
                        frameWidth = source.width,
                        frameHeight = source.height,
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
                val rotatedBitmap =
                    if (rotationDegrees != 0) {
                        val matrix = android.graphics.Matrix()
                        matrix.postRotate(rotationDegrees.toFloat())
                        val rotated =
                            Bitmap.createBitmap(
                                croppedBitmap,
                                0,
                                0,
                                croppedBitmap.width,
                                croppedBitmap.height,
                                matrix,
                                true,
                            )
                        croppedBitmap.recycle() // Free the unrotated bitmap
                        rotated
                    } else {
                        croppedBitmap
                    }

                Log.d(
                    TAG,
                    "Created thumbnail: ${rotatedBitmap.width}x${rotatedBitmap.height} (cropped: ${thumbnailWidth}x$thumbnailHeight, rotation: $rotationDegrees°)",
                )

                // DEV-ONLY: Log the generated thumbnail for debugging
                debugger?.let { dbg ->
                    CoroutineScope(Dispatchers.IO).launch {
                        dbg.logClassifierInput(
                            bitmap = rotatedBitmap,
                            source = "Generated thumbnail (cropped from full frame)",
                            itemId = null,
                            originPath = "Cropped from bbox $boundingBox, rotated $rotationDegrees°",
                        )
                    }
                }

                rotatedBitmap
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create thumbnail", e)
                null
            }
        }

    /**
     * Converts a confirmed ObjectCandidate to a ScannedItem.
     *
     * This is used by the tracking pipeline to create final items from
     * candidates that have met the confirmation threshold.
     */
    fun candidateToScannedItem(candidate: com.scanium.app.tracking.ObjectCandidate): ScannedItem =
        ScannedItem(
            id = candidate.internalId,
            thumbnail = candidate.thumbnail,
            thumbnailRef = candidate.thumbnail,
            category = candidate.category,
            priceRange = 0.0 to 0.0,
            confidence = candidate.maxConfidence,
            timestamp = System.currentTimeMillis(),
            recognizedText = null,
            barcodeValue = null,
            boundingBox = candidate.boundingBox,
            labelText = candidate.labelText.takeIf { it.isNotBlank() },
            mlKitLabels = candidate.labels, // Preserve labels from candidate
        )
}

/**
 * Tightens a bounding box by applying an inset ratio.
 * This reduces the box size to focus on the center of the detected object.
 */
fun Rect.tighten(
    insetRatio: Float,
    frameWidth: Int,
    frameHeight: Int,
): Rect {
    if (insetRatio <= 0f) return Rect(this)
    if (frameWidth <= 0 || frameHeight <= 0) return Rect(this)
    val currentWidth = width().coerceAtLeast(1)
    val currentHeight = height().coerceAtLeast(1)

    if (currentWidth < 4 || currentHeight < 4) {
        return Rect(
            left.coerceIn(0, frameWidth - 1),
            top.coerceIn(0, frameHeight - 1),
            right.coerceIn(1, frameWidth),
            bottom.coerceIn(1, frameHeight),
        )
    }

    val widthRatio = currentWidth.toFloat() / frameWidth.toFloat()
    val heightRatio = currentHeight.toFloat() / frameHeight.toFloat()
    val dominantRatio = maxOf(widthRatio, heightRatio)
    // Reduced adaptive boost values - previous values were too aggressive
    val adaptiveBoost =
        when {
            dominantRatio > 0.65f -> 0.04f

            // Large objects: was 0.08f
            dominantRatio > 0.45f -> 0.02f

            // Medium objects: was 0.05f
            dominantRatio > 0.30f -> 0.01f

            // Small objects: was 0.03f
            dominantRatio < 0.12f -> -0.02f

            // Very small: was -0.04f
            else -> 0f
        }
    // Max effective ratio reduced from 0.35 to 0.15 to prevent over-cropping
    val effectiveRatio = (insetRatio + adaptiveBoost).coerceIn(0f, 0.15f)

    val insetX = (currentWidth * effectiveRatio / 2f).roundToInt().coerceAtMost(currentWidth / 2 - 1)
    val insetY = (currentHeight * effectiveRatio / 2f).roundToInt().coerceAtMost(currentHeight / 2 - 1)

    val newLeft = (left + insetX).coerceIn(0, frameWidth - 1)
    val newTop = (top + insetY).coerceIn(0, frameHeight - 1)
    val newRight = (right - insetX).coerceIn(newLeft + 1, frameWidth)
    val newBottom = (bottom - insetY).coerceIn(newTop + 1, frameHeight)

    return Rect(newLeft, newTop, newRight, newBottom)
}
