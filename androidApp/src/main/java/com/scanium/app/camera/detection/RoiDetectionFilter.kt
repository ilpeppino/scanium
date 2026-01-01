package com.scanium.app.camera.detection

import com.scanium.app.ml.DetectionResult
import com.scanium.core.models.scanning.ScanRoi

/**
 * Result of filtering detections by ROI.
 *
 * @param roiEligible Detections with center inside ROI (preview-ready)
 * @param outsideRoi Detections with center outside ROI (filtered out)
 * @param totalDetections Original count before filtering
 */
data class RoiFilterResult(
    val roiEligible: List<DetectionResult>,
    val outsideRoi: List<DetectionResult>,
    val totalDetections: Int,
) {
    /** True if detections exist but none are inside ROI */
    val hasDetectionsOutsideRoiOnly: Boolean
        get() = roiEligible.isEmpty() && outsideRoi.isNotEmpty()

    /** Number of eligible detections */
    val eligibleCount: Int
        get() = roiEligible.size

    /** Number of detections outside ROI */
    val outsideCount: Int
        get() = outsideRoi.size
}

/**
 * Filters detections based on ROI eligibility.
 *
 * A detection is ROI-eligible iff its CENTER point lies within the ROI.
 * This is a strict rule that ensures:
 * - Only detections inside the scan zone are shown
 * - Users learn to center objects in the scan zone
 * - Consistent behavior between visualization and scanning
 */
object RoiDetectionFilter {
    /**
     * Filter detections by ROI eligibility.
     *
     * @param detections All detections from ML Kit
     * @param scanRoi Current scan ROI
     * @param maxPreviewBoxes Maximum number of preview boxes to show (default 2)
     * @return Filtered result with ROI-eligible and outside-ROI detections
     */
    fun filterByRoi(
        detections: List<DetectionResult>,
        scanRoi: ScanRoi,
        maxPreviewBoxes: Int = MAX_PREVIEW_BOXES,
    ): RoiFilterResult {
        if (detections.isEmpty()) {
            return RoiFilterResult(
                roiEligible = emptyList(),
                outsideRoi = emptyList(),
                totalDetections = 0,
            )
        }

        val eligible = mutableListOf<DetectionResult>()
        val outside = mutableListOf<DetectionResult>()

        for (detection in detections) {
            val bbox = detection.bboxNorm
            val centerX = (bbox.left + bbox.right) / 2f
            val centerY = (bbox.top + bbox.bottom) / 2f

            if (scanRoi.containsBoxCenter(centerX, centerY)) {
                eligible.add(detection)
            } else {
                outside.add(detection)
            }
        }

        // Limit to max preview boxes, preferring centered/higher confidence ones
        val limitedEligible =
            if (eligible.size > maxPreviewBoxes) {
                selectBestCandidates(eligible, scanRoi, maxPreviewBoxes)
            } else {
                eligible
            }

        return RoiFilterResult(
            roiEligible = limitedEligible,
            outsideRoi = outside,
            totalDetections = detections.size,
        )
    }

    /**
     * Check if a single detection is inside the ROI.
     *
     * @param detection Detection to check
     * @param scanRoi Current scan ROI
     * @return True if detection center is inside ROI
     */
    fun isInsideRoi(
        detection: DetectionResult,
        scanRoi: ScanRoi,
    ): Boolean {
        val bbox = detection.bboxNorm
        val centerX = (bbox.left + bbox.right) / 2f
        val centerY = (bbox.top + bbox.bottom) / 2f
        return scanRoi.containsBoxCenter(centerX, centerY)
    }

    /**
     * Calculate center score for a detection relative to ROI.
     *
     * @param detection Detection to score
     * @param scanRoi Current scan ROI
     * @return Score from 0 (corner) to 1 (perfectly centered)
     */
    fun calculateCenterScore(
        detection: DetectionResult,
        scanRoi: ScanRoi,
    ): Float {
        val bbox = detection.bboxNorm
        val centerX = (bbox.left + bbox.right) / 2f
        val centerY = (bbox.top + bbox.bottom) / 2f
        return scanRoi.centerScore(centerX, centerY)
    }

    /**
     * Select the best candidates from a list based on center score and confidence.
     */
    private fun selectBestCandidates(
        detections: List<DetectionResult>,
        scanRoi: ScanRoi,
        maxCount: Int,
    ): List<DetectionResult> {
        return detections
            .map { detection ->
                val centerScore = calculateCenterScore(detection, scanRoi)
                // Combined score: 60% center, 40% confidence
                val combinedScore = centerScore * 0.6f + detection.confidence * 0.4f
                detection to combinedScore
            }
            .sortedByDescending { it.second }
            .take(maxCount)
            .map { it.first }
    }

    // Maximum number of preview boxes to show at once
    private const val MAX_PREVIEW_BOXES = 2
}
