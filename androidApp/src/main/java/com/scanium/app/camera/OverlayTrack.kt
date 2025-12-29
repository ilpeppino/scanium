package com.scanium.app.camera

import com.scanium.app.aggregation.AggregatedItem
import com.scanium.app.ml.DetectionResult
import com.scanium.app.ml.ItemCategory
import com.scanium.shared.core.models.model.NormalizedRect
import com.scanium.shared.core.models.pricing.Money
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import com.scanium.shared.core.models.pricing.PriceRange

private const val DEFAULT_READY_THRESHOLD = 0.55f

/**
 * Visual style for overlay bounding boxes.
 *
 * PREVIEW: Immediate detection feedback - thin, neutral style
 * LOCKED: Scan-ready detection - thicker, highlighted style
 */
enum class OverlayBoxStyle {
    /** Preview style: thin stroke, neutral color - immediate feedback */
    PREVIEW,
    /** Locked style: thicker stroke, highlighted - scan ready */
    LOCKED
}

data class OverlayTrack(
    val bboxNorm: NormalizedRect,
    val label: String,
    val priceText: String,
    val confidence: Float,
    val isReady: Boolean,
    val priceEstimationStatus: PriceEstimationStatus,
    val aggregatedId: String? = null,
    val trackingId: String? = null,
    /** Box style for visual distinction: PREVIEW (immediate) vs LOCKED (scan-ready) */
    val boxStyle: OverlayBoxStyle = OverlayBoxStyle.PREVIEW
)

@Suppress("LongParameterList")
fun mapOverlayTracks(
    detections: List<DetectionResult>,
    aggregatedItems: List<AggregatedItem>,
    readyConfidenceThreshold: Float = DEFAULT_READY_THRESHOLD,
    pendingLabel: String = "Scanning…",
    /** Tracking ID of the locked candidate (if any) - used to set LOCKED box style */
    lockedTrackingId: String? = null
): List<OverlayTrack> {
    val aggregatedBySource = mutableMapOf<String, AggregatedItem>()
    aggregatedItems.forEach { item ->
        aggregatedBySource[item.aggregatedId] = item
        item.sourceDetectionIds.forEach { id -> aggregatedBySource[id] = item }
    }

    return detections.map { detection ->
        val detectionId = detection.trackingId?.toString()
        val matched = detectionId?.let { aggregatedBySource[it] }
        val category = matched?.enhancedCategory ?: matched?.category ?: detection.category
        val baseLabel = matched?.enhancedLabelText?.takeUnless { it.isNullOrBlank() }
            ?: matched?.labelText?.takeUnless { it.isBlank() }
        val label = when {
            !baseLabel.isNullOrBlank() -> baseLabel
            category != ItemCategory.UNKNOWN -> category.displayName
            else -> pendingLabel
        }

        val classificationConfidence = matched?.classificationConfidence
        val confidence = classificationConfidence ?: matched?.maxConfidence ?: detection.confidence
        val isReady = category != ItemCategory.UNKNOWN && confidence >= readyConfidenceThreshold

        val priceText = matched?.estimatedPriceRange?.formatted()
            ?: matched?.enhancedPriceRange?.let { PriceRange(Money(it.first), Money(it.second)).formatted() }
            ?: matched?.priceRange?.let { PriceRange(Money(it.first), Money(it.second)).formatted() }
            ?: detection.formattedPriceRange

        // Determine box style: LOCKED if this detection matches the locked candidate
        val boxStyle = if (lockedTrackingId != null && detectionId == lockedTrackingId) {
            OverlayBoxStyle.LOCKED
        } else {
            OverlayBoxStyle.PREVIEW
        }

        OverlayTrack(
            bboxNorm = detection.bboxNorm,
            label = label,
            priceText = priceText,
            confidence = confidence,
            isReady = isReady,
            priceEstimationStatus = matched?.priceEstimationStatus ?: detection.priceEstimationStatus,
            aggregatedId = matched?.aggregatedId,
            trackingId = detectionId,
            boxStyle = boxStyle
        )
    }
}
