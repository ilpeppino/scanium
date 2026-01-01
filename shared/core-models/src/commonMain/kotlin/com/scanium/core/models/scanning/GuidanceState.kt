package com.scanium.core.models.scanning

/**
 * Distance confidence level for scan feedback.
 *
 * Indicates how well the object distance matches the optimal scanning range.
 * Used for subtle visual feedback on the scan zone border.
 */
enum class DistanceConfidence {
    /** Object is too close to the camera */
    TOO_CLOSE,

    /** Object is at optimal scanning distance */
    OPTIMAL,

    /** Object is too far from the camera */
    TOO_FAR,

    /** No object detected or unable to determine */
    UNKNOWN,
}

/**
 * Guidance states for the camera scanning overlay.
 *
 * These states inform the user what action to take for optimal scanning.
 * The UI displays appropriate hints and visual feedback based on these states.
 */
enum class GuidanceState {
    /**
     * Initial state - scanning is active but no candidate detected yet.
     * Hint: "Point at an item"
     */
    SEARCHING,

    /**
     * Object detected but too close to camera.
     * Large detected box area indicates proximity.
     * Hint: "Move phone away"
     */
    TOO_CLOSE,

    /**
     * Object detected but too far from camera.
     * Small detected box area indicates distance.
     * Hint: "Move closer"
     */
    TOO_FAR,

    /**
     * Object detected but not centered in the scan zone.
     * Hint: "Center the object"
     */
    OFF_CENTER,

    /**
     * Camera or scene is unstable (motion detected or blur).
     * Hint: "Hold steady"
     */
    UNSTABLE,

    /**
     * Camera is actively focusing.
     * Brief transitional state.
     * Hint: "Focusing..." (shown briefly)
     */
    FOCUSING,

    /**
     * All conditions are good - object is centered, focused, stable.
     * Ready to lock onto candidate.
     * Hint: "Hold still to scan" or subtle glow
     */
    GOOD,

    /**
     * Locked onto a candidate - stable detection achieved.
     * Only this candidate will be considered for adding.
     * Visual: Stronger outline on scan zone
     */
    LOCKED,
}

/**
 * Comprehensive scan guidance state combining all relevant information.
 *
 * This is the complete state exposed to the UI for rendering guidance.
 */
data class ScanGuidanceState(
    /** Current guidance state */
    val state: GuidanceState,
    /** Current scan ROI (for overlay rendering) */
    val scanRoi: ScanRoi,
    /** Hint text to display (localized) */
    val hintText: String?,
    /** Whether hint should be visible */
    val showHint: Boolean,
    /** Detected candidate box area (normalized, 0-1) */
    val detectedBoxArea: Float?,
    /** Distance from candidate center to ROI center (0 = centered) */
    val centerDistance: Float?,
    /** Current sharpness score */
    val sharpnessScore: Float?,
    /** Current motion score (0 = still, 1 = high motion) */
    val motionScore: Float?,
    /** Time in current state (ms) */
    val stateTimeMs: Long,
    /** Whether locked state allows item add */
    val canAddItem: Boolean,
    /** ID of locked candidate (if in LOCKED state) */
    val lockedCandidateId: String?,
    /** Distance confidence for visual feedback on scan zone */
    val distanceConfidence: DistanceConfidence = DistanceConfidence.UNKNOWN,
) {
    companion object {
        /** Initial state when scanning starts */
        fun initial(roi: ScanRoi = ScanRoi.DEFAULT): ScanGuidanceState {
            return ScanGuidanceState(
                state = GuidanceState.SEARCHING,
                scanRoi = roi,
                hintText = null,
                showHint = false,
                detectedBoxArea = null,
                centerDistance = null,
                sharpnessScore = null,
                motionScore = null,
                stateTimeMs = 0L,
                canAddItem = false,
                lockedCandidateId = null,
                distanceConfidence = DistanceConfidence.UNKNOWN,
            )
        }
    }
}

/**
 * Diagnostics data for debug overlay.
 */
data class ScanDiagnostics(
    /** Current ROI size as percentage */
    val roiSizePercent: Int,
    /** Detected box area as percentage */
    val boxAreaPercent: Int?,
    /** Sharpness score */
    val sharpness: Float?,
    /** Center distance (0-1) */
    val centerDistance: Float?,
    /** Current lock state */
    val lockState: String,
    /** Consecutive stable frames */
    val stableFrames: Int,
    /** Stable time in ms */
    val stableTimeMs: Long,
    /** Motion score */
    val motionScore: Float?,
    /** Current guidance state name */
    val guidanceState: String,
)
