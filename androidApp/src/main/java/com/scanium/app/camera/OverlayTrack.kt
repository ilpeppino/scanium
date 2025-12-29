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
 * Each style represents a distinct scanning state with clear visual feedback:
 * - PREVIEW: Object detected but not yet ready for scanning
 * - READY: All scan conditions met, holding for lock
 * - LOCKED: Stable lock achieved, scan-ready
 *
 * Visual progression: PREVIEW → READY → LOCKED
 */
enum class OverlayBoxStyle {
    /** Preview style: thin stroke, neutral color - immediate feedback when object detected */
    PREVIEW,
    /** Ready style: medium stroke, accent color - conditions met, holding steady */
    READY,
    /** Locked style: thick stroke, highlighted with pulse - stable lock achieved, scan-ready */
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

/**
 * Maps detection results and aggregated items to overlay tracks for rendering.
 *
 * @param detections Raw detection results from ML pipeline
 * @param aggregatedItems Aggregated items with enhanced classification
 * @param readyConfidenceThreshold Confidence threshold for isReady flag
 * @param pendingLabel Label to show while classification is pending
 * @param lockedTrackingId Tracking ID of the locked candidate (LOCKED style)
 * @param isGoodState True if guidance state is GOOD (shows READY style for eligible detections)
 */
@Suppress("LongParameterList")
fun mapOverlayTracks(
    detections: List<DetectionResult>,
    aggregatedItems: List<AggregatedItem>,
    readyConfidenceThreshold: Float = DEFAULT_READY_THRESHOLD,
    pendingLabel: String = "Scanning…",
    /** Tracking ID of the locked candidate (if any) - used to set LOCKED box style */
    lockedTrackingId: String? = null,
    /** True if guidance state is GOOD (conditions met, waiting for lock) */
    isGoodState: Boolean = false
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

        // Determine box style based on guidance state and lock status
        // Visual progression: PREVIEW → READY → LOCKED
        val boxStyle = when {
            // LOCKED: This detection is the locked candidate
            lockedTrackingId != null && detectionId == lockedTrackingId -> OverlayBoxStyle.LOCKED
            // READY: Guidance is in GOOD state and this is a viable candidate
            isGoodState && isReady -> OverlayBoxStyle.READY
            // PREVIEW: Default - object detected but not ready for scan
            else -> OverlayBoxStyle.PREVIEW
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
