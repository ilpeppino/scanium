package com.scanium.test

import com.scanium.core.models.geometry.NormalizedRect
import com.scanium.core.models.image.ImageRef
import com.scanium.core.models.image.ImageRefBytes
import com.scanium.core.models.items.ScannedItem
import com.scanium.core.models.ml.ItemCategory
import com.scanium.core.models.ml.RawDetection
import com.scanium.core.models.ml.LabelWithConfidence
import com.scanium.core.models.pricing.Money
import com.scanium.core.models.pricing.PriceRange
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import com.scanium.core.tracking.DetectionInfo

/**
 * Test builders for creating test data in a portable, KMP-compatible way.
 *
 * These builders replace Android-specific helpers (RectF, Bitmap) with pure Kotlin types.
 */

/**
 * Creates a NormalizedRect from normalized coordinates (0.0 to 1.0).
 *
 * Example:
 * ```
 * val rect = testNormalizedRect(
 *     left = 0.1f,
 *     top = 0.1f,
 *     right = 0.5f,
 *     bottom = 0.5f
 * )
 * ```
 */
fun testNormalizedRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float
): NormalizedRect {
    return NormalizedRect(left, top, right, bottom)
}

/**
 * Creates a NormalizedRect with a centered square of given size.
 *
 * Example:
 * ```
 * val rect = testCenteredRect(size = 0.2f) // 20% of frame, centered
 * ```
 */
fun testCenteredRect(size: Float = 0.2f): NormalizedRect {
    val halfSize = size / 2f
    val center = 0.5f
    return NormalizedRect(
        left = center - halfSize,
        top = center - halfSize,
        right = center + halfSize,
        bottom = center + halfSize
    )
}

/**
 * Creates a DetectionInfo for testing.
 *
 * Example:
 * ```
 * val detection = testDetectionInfo(
 *     trackingId = "track_1",
 *     boundingBox = testNormalizedRect(0.1f, 0.1f, 0.5f, 0.5f),
 *     confidence = 0.85f,
 *     category = ItemCategory.FASHION
 * )
 * ```
 */
fun testDetectionInfo(
    trackingId: String? = "test_track_${randomId()}",
    boundingBox: NormalizedRect = testCenteredRect(),
    confidence: Float = 0.7f,
    category: ItemCategory = ItemCategory.FASHION,
    labelText: String = "Test Item",
    thumbnail: ImageRef? = null,
    normalizedBoxArea: Float? = null
): DetectionInfo {
    return DetectionInfo(
        trackingId = trackingId,
        boundingBox = boundingBox,
        confidence = confidence,
        category = category,
        labelText = labelText,
        thumbnail = thumbnail,
        normalizedBoxArea = normalizedBoxArea ?: boundingBox.area,
        boundingBoxNorm = boundingBox
    )
}

/**
 * Creates a ScannedItem for testing.
 *
 * Example:
 * ```
 * val item = testScannedItem(
 *     id = "item_1",
 *     category = ItemCategory.ELECTRONICS,
 *     labelText = "Laptop"
 * )
 * ```
 */
fun testScannedItem(
    id: String = "test_item_${randomId()}",
    category: ItemCategory = ItemCategory.FASHION,
    labelText: String = "Test Item",
    confidence: Float = 0.8f,
    boundingBox: NormalizedRect = testCenteredRect(),
    priceRange: Pair<Double, Double> = 10.0 to 50.0,
    thumbnail: ImageRef? = null,
    timestampMs: Long = System.currentTimeMillis(),
    mergeCount: Int = 1,
    sourceDetectionIds: Set<String> = setOf(id),
    estimatedPriceRange: PriceRange? = null,
    priceEstimationStatus: PriceEstimationStatus? = null
): ScannedItem {
    val resolvedRange = estimatedPriceRange ?: PriceRange(
        low = Money(priceRange.first),
        high = Money(priceRange.second)
    )
    val resolvedStatus = priceEstimationStatus ?: PriceEstimationStatus.Ready(resolvedRange)

    return ScannedItem(
        aggregatedId = id,
        category = category,
        labelText = labelText,
        boundingBox = boundingBox,
        priceRange = priceRange,
        estimatedPriceRange = resolvedRange,
        priceEstimationStatus = resolvedStatus,
        confidence = confidence,
        thumbnail = thumbnail,
        timestampMs = timestampMs,
        mergeCount = mergeCount,
        averageConfidence = confidence,
        sourceDetectionIds = sourceDetectionIds.toMutableSet()
    )
}

/**
 * Creates a RawDetection for testing.
 *
 * Example:
 * ```
 * val detection = testRawDetection(
 *     boundingBox = testNormalizedRect(0.1f, 0.1f, 0.5f, 0.5f),
 *     labels = listOf(ItemCategory.FASHION to 0.85f)
 * )
 * ```
 */
fun testRawDetection(
    trackingId: String = "raw_track_${randomId()}",
    boundingBox: NormalizedRect = testCenteredRect(),
    labels: List<Pair<ItemCategory, Float>> = listOf(ItemCategory.FASHION to 0.8f)
): RawDetection {
    return RawDetection(
        trackingId = trackingId,
        bboxNorm = boundingBox,
        labels = labels.map { (category, confidence) ->
            LabelWithConfidence(category.name, confidence)
        }
    )
}

/**
 * Creates a test ImageRef.Bytes with dummy data.
 *
 * Example:
 * ```
 * val image = testImageRef(width = 1920, height = 1080)
 * ```
 */
fun testImageRef(
    width: Int = 800,
    height: Int = 600,
    mimeType: String = "image/jpeg"
): ImageRefBytes {
    // Create minimal valid JPEG header for testing
    val dummyBytes = ByteArray(100) { it.toByte() }
    return ImageRefBytes(
        bytes = dummyBytes,
        width = width,
        height = height,
        mimeType = mimeType
    )
}

/**
 * Extension function to create multiple test detections at different positions.
 *
 * Example:
 * ```
 * val detections = testDetectionGrid(count = 4) // Creates 2x2 grid
 * ```
 */
fun testDetectionGrid(
    count: Int,
    startCategory: ItemCategory = ItemCategory.FASHION
): List<DetectionInfo> {
    val detections = mutableListOf<DetectionInfo>()
    val gridSize = kotlin.math.sqrt(count.toDouble()).toInt()

    for (i in 0 until count) {
        val row = i / gridSize
        val col = i % gridSize
        val spacing = 1f / (gridSize + 1)

        val left = spacing * (col + 1) - 0.1f
        val top = spacing * (row + 1) - 0.1f
        val right = spacing * (col + 1) + 0.1f
        val bottom = spacing * (row + 1) + 0.1f

        detections.add(
            testDetectionInfo(
                trackingId = "grid_${i}",
                boundingBox = testNormalizedRect(left, top, right, bottom),
                category = startCategory,
                labelText = "Item $i"
            )
        )
    }

    return detections
}

// Internal helper for generating unique IDs
private var idCounter = 0
private fun randomId(): String = "${System.currentTimeMillis()}_${idCounter++}"
