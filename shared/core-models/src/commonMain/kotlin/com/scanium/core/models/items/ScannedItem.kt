package com.scanium.core.models.items

import com.scanium.core.models.geometry.NormalizedRect
import com.scanium.core.models.image.ImageRef
import com.scanium.core.models.ml.ItemCategory
import com.scanium.core.models.pricing.PriceRange
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Platform-agnostic representation of a detected or aggregated item.
 *
 * This model is used by the shared tracking/aggregation pipeline and keeps
 * only portable fields (no android.* dependencies).
 */
data class ScannedItem(
    val aggregatedId: String = generateAggregatedId(),
    var category: ItemCategory,
    var labelText: String = "",
    var boundingBox: NormalizedRect,
    var priceRange: Pair<Double, Double> = 0.0 to 0.0,
    var estimatedPriceRange: PriceRange? = null,
    var priceEstimationStatus: PriceEstimationStatus = PriceEstimationStatus.Idle,
    var confidence: Float = 0f,
    var thumbnail: ImageRef? = null,
    var timestampMs: Long = Clock.System.now().toEpochMilliseconds(),
    var mergeCount: Int = 1,
    var averageConfidence: Float = confidence,
    val sourceDetectionIds: MutableSet<String> = mutableSetOf(),
    var enhancedCategory: ItemCategory? = null,
    var enhancedLabelText: String? = null,
    var enhancedPriceRange: Pair<Double, Double>? = null,
    var classificationStatus: String = "NOT_STARTED",
    var domainCategoryId: String? = null,
    var classificationErrorMessage: String? = null,
    var classificationRequestId: String? = null,
) {
    val id: String
        get() = aggregatedId

    init {
        if (sourceDetectionIds.isEmpty()) {
            sourceDetectionIds.add(aggregatedId)
        }
    }
}

private fun generateAggregatedId(): String {
    val bytes = Random.nextBytes(6)
    return buildString(bytes.size * 2 + 4) {
        append("agg_")
        bytes.forEach { byte ->
            append(byte.toHex())
        }
    }
}

private fun Byte.toHex(): String {
    val intVal = toInt() and 0xFF
    val hexChars = "0123456789abcdef"
    return buildString(2) {
        append(hexChars[intVal shr 4])
        append(hexChars[intVal and 0x0F])
    }
}
