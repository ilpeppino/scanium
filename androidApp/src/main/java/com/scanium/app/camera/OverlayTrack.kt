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
 * - EYE: Object detected anywhere in frame (global vision, not selected)
 * - SELECTED: Object's center is inside ROI (user intent to act)
 * - READY: Selected + stability conditions met, holding for lock
 * - LOCKED: Stable lock achieved, scan-ready
 *
 * Visual progression: EYE → SELECTED → READY → LOCKED
 *
 * Eye Mode vs Focus Mode:
 * - Eye Mode: All detections shown as EYE (global awareness)
 * - Focus Mode: One detection promoted to SELECTED/READY/LOCKED (user intent)
 */
enum class OverlayBoxStyle {
    /** Eye style: thin stroke, very subtle - detected anywhere in frame (global vision) */
    EYE,

    /** Selected style: medium stroke, accent color - object center inside ROI (user intent) */
    SELECTED,

    /** Ready style: medium-thick stroke, green - selected + conditions met, holding steady */
    READY,

    /** Locked style: thick stroke, bright green with pulse - stable lock achieved, scan-ready */
    LOCKED,
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
    /** Box style for visual distinction: EYE (global) → SELECTED → READY → LOCKED */
    val boxStyle: OverlayBoxStyle = OverlayBoxStyle.EYE,
)

/**
 * Maps detection results and aggregated items to overlay tracks for rendering.
 *
 * Eye Mode vs Focus Mode:
 * - All detections are rendered (Eye mode = global vision)
 * - Only the selected detection (center inside ROI) is highlighted (Focus mode)
 *
 * @param detections Raw detection results from ML pipeline (ALL detections, not filtered)
 * @param aggregatedItems Aggregated items with enhanced classification
 * @param readyConfidenceThreshold Confidence threshold for isReady flag
 * @param pendingLabel Label to show while classification is pending
 * @param selectedTrackingId Tracking ID of the ROI-selected detection (SELECTED/READY style)
 * @param lockedTrackingId Tracking ID of the locked candidate (LOCKED style)
 * @param isGoodState True if guidance state is GOOD (shows READY style for selected detection)
 */
@Suppress("LongParameterList")
fun mapOverlayTracks(
    detections: List<DetectionResult>,
    aggregatedItems: List<AggregatedItem>,
    readyConfidenceThreshold: Float = DEFAULT_READY_THRESHOLD,
    pendingLabel: String = "Scanning…",
    /** Tracking ID of the ROI-selected detection (center inside ROI) */
    selectedTrackingId: String? = null,
    /** Tracking ID of the locked candidate (if any) - used to set LOCKED box style */
    lockedTrackingId: String? = null,
    /** True if guidance state is GOOD (conditions met, waiting for lock) */
    isGoodState: Boolean = false,
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
        val baseLabel =
            matched?.enhancedLabelText?.takeUnless { it.isNullOrBlank() }
                ?: matched?.labelText?.takeUnless { it.isBlank() }
        val label =
            when {
                !baseLabel.isNullOrBlank() -> baseLabel
                category != ItemCategory.UNKNOWN -> category.displayName
                else -> pendingLabel
            }

        val classificationConfidence = matched?.classificationConfidence
        val confidence = classificationConfidence ?: matched?.maxConfidence ?: detection.confidence
        val isReady = category != ItemCategory.UNKNOWN && confidence >= readyConfidenceThreshold

        val priceText =
            matched?.estimatedPriceRange?.formatted()
                ?: matched?.enhancedPriceRange?.let { PriceRange(Money(it.first), Money(it.second)).formatted() }
                ?: matched?.priceRange?.let { PriceRange(Money(it.first), Money(it.second)).formatted() }
                ?: detection.formattedPriceRange

        // Determine box style based on selection and lock status
        // Visual progression: EYE → SELECTED → READY → LOCKED
        //
        // Eye Mode: Detection exists anywhere in frame (global vision)
        // Focus Mode: Detection is selected (center inside ROI) and may lock
        val isSelected = selectedTrackingId != null && detectionId == selectedTrackingId
        val isLocked = lockedTrackingId != null && detectionId == lockedTrackingId

        val boxStyle =
            when {
                // LOCKED: This detection is the locked candidate (scan-ready)
                isLocked -> OverlayBoxStyle.LOCKED
                // READY: Selected + guidance is GOOD + conditions met
                isSelected && isGoodState && isReady -> OverlayBoxStyle.READY
                // SELECTED: Object center is inside ROI (user intent)
                isSelected -> OverlayBoxStyle.SELECTED
                // EYE: Object detected but not inside ROI (global vision)
                else -> OverlayBoxStyle.EYE
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
            boxStyle = boxStyle,
        )
    }
}
