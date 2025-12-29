package com.scanium.core.tracking

import com.scanium.core.models.scanning.GuidanceState
import com.scanium.core.models.scanning.ScanDiagnostics
import com.scanium.core.models.scanning.ScanGuidanceState
import com.scanium.core.models.scanning.ScanRoi
import com.scanium.core.models.scanning.ScanRoiConfig
import kotlinx.datetime.Clock
import kotlin.math.abs

/**
 * Central coordinator for scan guidance state.
 *
 * Manages:
 * - Dynamic ROI sizing based on detected object area
 * - Focus/stability state inference
 * - Center-lock mechanism for preventing background detections
 * - Guidance state transitions
 *
 * This is the single source of truth for scan guidance, used by both UI and analyzer.
 */
class ScanGuidanceManager(
    private val config: ScanGuidanceConfig = ScanGuidanceConfig()
) {
    // Current ROI state
    private var currentRoiSize: Float = config.roiConfig.initialSize
    private var targetRoiSize: Float = config.roiConfig.initialSize
    private var roiAnimationProgress: Float = 1f

    // State tracking
    private var currentState: GuidanceState = GuidanceState.SEARCHING
    private var stateEnteredTimeMs: Long = 0L
    private var lastUpdateTimeMs: Long = 0L

    // Lock tracking
    private var lockedCandidateId: String? = null
    private var lockStartTimeMs: Long = 0L

    // Stability tracking
    private var stableFrameCount: Int = 0
    private var stableStartTimeMs: Long = 0L
    private var lastCandidateId: String? = null
    private var lastCandidateCenterX: Float = 0f
    private var lastCandidateCenterY: Float = 0f

    // Motion tracking
    private var recentMotionScores = mutableListOf<Float>()
    private var averageMotionScore: Float = 0f

    private var lastMotionScore: Float = 0f

    private var lastInstantMotionScore: Float = 0f


    private var lastInstantMotionScore: Float = 0f


    // Sharpness tracking
    private var recentSharpnessScores = mutableListOf<Float>()
    private var averageSharpness: Float = config.minSharpnessForGood

    // Hint rate limiting
    private var lastHintChangeTimeMs: Long = 0L
    private var currentHintText: String? = null

    /**
     * Process a frame and update guidance state.
     *
     * @param candidate Best candidate from center-weighted selection (null if none)
     * @param motionScore Frame motion score (0 = still, 1 = high motion)
     * @param sharpnessScore Frame sharpness score
     * @param currentTimeMs Current timestamp
     * @return Updated guidance state
     */
    fun processFrame(
        candidate: CandidateInfo?,
        motionScore: Float,
        sharpnessScore: Float,
        currentTimeMs: Long = Clock.System.now().toEpochMilliseconds()
    ): ScanGuidanceState {
        lastUpdateTimeMs = currentTimeMs

        // Update running averages
        updateMotionAverage(motionScore)
        updateSharpnessAverage(sharpnessScore)

        // Determine new state based on conditions
        val newState = evaluateState(candidate, currentTimeMs)

        // Handle state transition
        if (newState != currentState) {
            onStateTransition(currentState, newState, currentTimeMs)
            currentState = newState
            stateEnteredTimeMs = currentTimeMs
        }

        // Update ROI size based on candidate
        updateRoiSize(candidate, currentTimeMs)

        // Update stability tracking
        updateStabilityTracking(candidate, currentTimeMs)

        // Check for lock eligibility
        checkLockEligibility(candidate, currentTimeMs)

        // Build output state
        return buildGuidanceState(candidate, currentTimeMs)
    }

    /**
     * Evaluate what guidance state should be active based on current conditions.
     */
    private fun evaluateState(candidate: CandidateInfo?, currentTimeMs: Long): GuidanceState {
        // If locked, stay locked unless conditions break
        if (currentState == GuidanceState.LOCKED && lockedCandidateId != null) {
            if (shouldBreakLock(candidate, currentTimeMs)) {
                breakLock()
            } else {
                return GuidanceState.LOCKED
            }
        }

        // No candidate detected
        if (candidate == null) {
            return GuidanceState.SEARCHING
        }

        // Check motion/stability first
        if (averageMotionScore > config.maxMotionForStable) {
            return GuidanceState.UNSTABLE
        }

        // Check sharpness
        if (averageSharpness < config.minSharpnessForFocus) {
            return GuidanceState.FOCUSING
        }

        // Check distance (based on box area)
        if (candidate.boxArea > config.roiConfig.tooCloseAreaThreshold) {
            return GuidanceState.TOO_CLOSE
        }

        if (candidate.boxArea < config.roiConfig.tooFarAreaThreshold) {
            return GuidanceState.TOO_FAR
        }

        // Check centering
        val centerDistance = calculateCenterDistance(candidate.boxCenterX, candidate.boxCenterY)
        if (centerDistance > config.maxCenterDistanceForGood) {
            return GuidanceState.OFF_CENTER
        }

        // All conditions good
        if (averageSharpness >= config.minSharpnessForGood && averageMotionScore <= config.maxMotionForGood) {
            // Check if we should transition to LOCKED
            if (isEligibleForLock(candidate, currentTimeMs)) {
                return GuidanceState.LOCKED
            }
            return GuidanceState.GOOD
        }

        // Transitional state - focusing
        return GuidanceState.FOCUSING
    }

    /**
     * Handle state transition side effects.
     */
    private fun onStateTransition(from: GuidanceState, to: GuidanceState, currentTimeMs: Long) {
        when (to) {
            GuidanceState.LOCKED -> {
                // Start lock
                lockedCandidateId = lastCandidateId
                lockStartTimeMs = currentTimeMs
            }
            GuidanceState.SEARCHING -> {
                // Reset all tracking
                resetStabilityTracking()
                breakLock()
            }
            else -> {
                // If leaving LOCKED, break the lock
                if (from == GuidanceState.LOCKED) {
                    breakLock()
                }
            }
        }
    }

    /**
     * Update the dynamic ROI size based on detected object.
     */
    private fun updateRoiSize(candidate: CandidateInfo?, currentTimeMs: Long) {
        // Don't resize while locked
        if (currentState == GuidanceState.LOCKED) {
            return
        }

        if (candidate == null) {
            // Gradually return to default size
            targetRoiSize = config.roiConfig.initialSize
        } else {
            // Adjust based on object area
            targetRoiSize = when {
                candidate.boxArea > config.roiConfig.tooCloseAreaThreshold -> {
                    // Too close - shrink zone
                    (currentRoiSize - config.roiConfig.sizeAdjustmentStep)
                        .coerceAtLeast(config.roiConfig.minSize)
                }
                candidate.boxArea < config.roiConfig.tooFarAreaThreshold -> {
                    // Too far - expand zone
                    (currentRoiSize + config.roiConfig.sizeAdjustmentStep)
                        .coerceAtMost(config.roiConfig.maxSize)
                }
                else -> {
                    // Good distance - maintain or return to default
                    currentRoiSize
                }
            }
        }

        // Animate towards target
        val sizeDelta = targetRoiSize - currentRoiSize
        if (abs(sizeDelta) > 0.001f) {
            // Simple lerp animation (would be smoother with actual animation framework)
            currentRoiSize += sizeDelta * 0.15f
        }
    }

    /**
     * Update stability tracking for lock eligibility.
     */
    private fun updateStabilityTracking(candidate: CandidateInfo?, currentTimeMs: Long) {
        if (candidate == null) {
            resetStabilityTracking()
            return
        }

        // Check if this is the same candidate as before (by proximity)
        val isSameCandidate = lastCandidateId != null && (
            candidate.trackingId == lastCandidateId ||
            isSamePositionCandidate(candidate.boxCenterX, candidate.boxCenterY)
        )

        if (isSameCandidate) {
            stableFrameCount++
            if (stableStartTimeMs == 0L) {
                stableStartTimeMs = currentTimeMs
            }
        } else {
            // New candidate - reset stability
            stableFrameCount = 1
            stableStartTimeMs = currentTimeMs
        }

        // Update last candidate tracking
        lastCandidateId = candidate.trackingId ?: generatePositionId(candidate.boxCenterX, candidate.boxCenterY)
        lastCandidateCenterX = candidate.boxCenterX
        lastCandidateCenterY = candidate.boxCenterY
    }

    /**
     * Check if candidate is same as previous based on position.
     */
    private fun isSamePositionCandidate(centerX: Float, centerY: Float): Boolean {
        val dx = abs(centerX - lastCandidateCenterX)
        val dy = abs(centerY - lastCandidateCenterY)
        return dx < config.positionMatchThreshold && dy < config.positionMatchThreshold
    }

    /**
     * Generate a position-based ID for candidates without tracking ID.
     */
    private fun generatePositionId(centerX: Float, centerY: Float): String {
        return "pos_${(centerX * 100).toInt()}_${(centerY * 100).toInt()}"
    }

    /**
     * Reset stability tracking.
     */
    private fun resetStabilityTracking() {
        stableFrameCount = 0
        stableStartTimeMs = 0L
        lastCandidateId = null
    }

    /**
     * Check if conditions are met to enter LOCKED state.
     */
    private fun isEligibleForLock(candidate: CandidateInfo?, currentTimeMs: Long): Boolean {
        if (candidate == null) return false

        // Check all lock criteria
        val centerDistance = calculateCenterDistance(candidate.boxCenterX, candidate.boxCenterY)
        val isInsideRoi = centerDistance <= config.maxCenterDistanceForLock
        val isGoodArea = candidate.boxArea in config.roiConfig.tooFarAreaThreshold..config.roiConfig.tooCloseAreaThreshold
        val isSharp = averageSharpness >= config.minSharpnessForLock
        val isStill = averageMotionScore <= config.maxMotionForLock
        val stableTimeMs = if (stableStartTimeMs > 0) currentTimeMs - stableStartTimeMs else 0L
        val isStableEnough = stableFrameCount >= config.minStableFramesForLock &&
                stableTimeMs >= config.minStableTimeForLockMs

        return isInsideRoi && isGoodArea && isSharp && isStill && isStableEnough
    }

    /**
     * Check lock eligibility and transition if ready.
     */
    private fun checkLockEligibility(candidate: CandidateInfo?, currentTimeMs: Long) {
        // Handled in evaluateState
    }

    /**
     * Check if lock should be broken.
     */
    private fun shouldBreakLock(candidate: CandidateInfo?, currentTimeMs: Long): Boolean {
        // Break lock if:
<<<<<<< HEAD
<<<<<<< HEAD
        // 1. High motion detected (panning)
        if (lastMotionScore > config.lockBreakMotionThreshold ||
            averageMotionScore > config.lockBreakMotionThreshold
        ) {
=======
=======
>>>>>>> 71971f1 (scan guidance)
        // 1. High motion detected (panning) - check both instant and average
        //    Instant motion catches sudden movements immediately
        if (lastInstantMotionScore > config.lockBreakMotionThreshold ||
            averageMotionScore > config.lockBreakMotionThreshold) {
<<<<<<< HEAD
>>>>>>> 71971f1 (scan guidance)
=======
>>>>>>> 71971f1 (scan guidance)
            return true
        }

        // 2. Candidate lost
        if (candidate == null) {
            return true
        }

        // 3. Candidate moved significantly (different position)
        if (!isSamePositionCandidate(candidate.boxCenterX, candidate.boxCenterY)) {
            return true
        }

        // 4. Lock timeout (optional, for cases where add doesn't happen)
        val lockDuration = currentTimeMs - lockStartTimeMs
        if (lockDuration > config.maxLockDurationMs) {
            return true
        }

        return false
    }

    /**
     * Break the current lock.
     */
    private fun breakLock() {
        lockedCandidateId = null
        lockStartTimeMs = 0L
    }

    /**
     * Update motion score running average.
     */
    private fun updateMotionAverage(motionScore: Float) {
<<<<<<< HEAD
<<<<<<< HEAD
        lastMotionScore = motionScore
=======
        lastInstantMotionScore = motionScore
>>>>>>> 71971f1 (scan guidance)
=======
        lastInstantMotionScore = motionScore
>>>>>>> 71971f1 (scan guidance)
        recentMotionScores.add(motionScore)
        if (recentMotionScores.size > config.motionAverageWindow) {
            recentMotionScores.removeAt(0)
        }
        averageMotionScore = recentMotionScores.average().toFloat()
    }

    /**
     * Update sharpness score running average.
     */
    private fun updateSharpnessAverage(sharpnessScore: Float) {
        recentSharpnessScores.add(sharpnessScore)
        if (recentSharpnessScores.size > config.sharpnessAverageWindow) {
            recentSharpnessScores.removeAt(0)
        }
        averageSharpness = recentSharpnessScores.average().toFloat()
    }

    /**
     * Calculate distance from center of ROI.
     */
    private fun calculateCenterDistance(boxCenterX: Float, boxCenterY: Float): Float {
        val dx = boxCenterX - 0.5f
        val dy = boxCenterY - 0.5f
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * Build the full guidance state for UI consumption.
     */
    private fun buildGuidanceState(candidate: CandidateInfo?, currentTimeMs: Long): ScanGuidanceState {
        val roi = ScanRoi.centered(currentRoiSize)

        // Determine hint text with rate limiting
        val hintText = getHintForState(currentState, candidate)
        val shouldShowHint = shouldShowHint(currentState, currentTimeMs)

        // Update hint if changed and rate limit passed
        if (hintText != currentHintText && currentTimeMs - lastHintChangeTimeMs > config.hintRateLimitMs) {
            currentHintText = hintText
            lastHintChangeTimeMs = currentTimeMs
        }

        return ScanGuidanceState(
            state = currentState,
            scanRoi = roi,
            hintText = currentHintText,
            showHint = shouldShowHint,
            detectedBoxArea = candidate?.boxArea,
            centerDistance = candidate?.let { calculateCenterDistance(it.boxCenterX, it.boxCenterY) },
            sharpnessScore = averageSharpness,
            motionScore = averageMotionScore,
            stateTimeMs = currentTimeMs - stateEnteredTimeMs,
            canAddItem = currentState == GuidanceState.LOCKED,
            lockedCandidateId = lockedCandidateId
        )
    }

    /**
     * Get appropriate hint text for the current state.
     */
    private fun getHintForState(state: GuidanceState, candidate: CandidateInfo?): String? {
        return when (state) {
            GuidanceState.SEARCHING -> null  // No hint needed
            GuidanceState.TOO_CLOSE -> "Move phone away"
            GuidanceState.TOO_FAR -> "Move closer"
            GuidanceState.OFF_CENTER -> "Center the object"
            GuidanceState.UNSTABLE -> "Hold steady"
            GuidanceState.FOCUSING -> "Focusing..."
            GuidanceState.GOOD -> "Hold still to scan"
            GuidanceState.LOCKED -> null  // No hint - implicit success
        }
    }

    /**
     * Determine if hint should be shown (rate limiting and state-based logic).
     */
    private fun shouldShowHint(state: GuidanceState, currentTimeMs: Long): Boolean {
        // Don't show hints for SEARCHING, LOCKED, or GOOD (after initial display)
        if (state == GuidanceState.SEARCHING || state == GuidanceState.LOCKED) {
            return false
        }

        // Brief display for FOCUSING
        if (state == GuidanceState.FOCUSING) {
            return currentTimeMs - stateEnteredTimeMs < 1000
        }

        // Brief display for GOOD
        if (state == GuidanceState.GOOD) {
            return currentTimeMs - stateEnteredTimeMs < 1500
        }

        // Show for problem states
        return true
    }

    /**
     * Check if a candidate should be allowed to trigger item add.
     *
     * This enforces that only the locked candidate can be added.
     */
    fun shouldAllowAdd(candidateId: String?): Boolean {
        if (currentState != GuidanceState.LOCKED) {
            return false
        }
        // If no candidate ID provided, allow any add in locked state
        if (candidateId == null) {
            return true
        }
        // Otherwise, must match locked candidate
        return candidateId == lockedCandidateId
    }

    /**
     * Notify that an item was added (for lock release).
     */
    fun onItemAdded() {
        breakLock()
        currentState = GuidanceState.SEARCHING
        stateEnteredTimeMs = lastUpdateTimeMs
        resetStabilityTracking()
    }

    /**
     * Get current diagnostics for debug overlay.
     */
    fun getDiagnostics(): ScanDiagnostics {
        return ScanDiagnostics(
            roiSizePercent = (currentRoiSize * 100).toInt(),
            boxAreaPercent = null, // Set by caller with current candidate
            sharpness = averageSharpness,
            centerDistance = null, // Set by caller
            lockState = if (lockedCandidateId != null) "LOCKED" else "UNLOCKED",
            stableFrames = stableFrameCount,
            stableTimeMs = if (stableStartTimeMs > 0) lastUpdateTimeMs - stableStartTimeMs else 0L,
            motionScore = averageMotionScore,
            guidanceState = currentState.name
        )
    }

    /**
     * Get the current scan ROI.
     */
    fun getCurrentRoi(): ScanRoi {
        return ScanRoi.centered(currentRoiSize)
    }

    /**
     * Check if currently in locked state.
     */
    fun isLocked(): Boolean = currentState == GuidanceState.LOCKED

    /**
     * Reset all state (call when starting new scan session).
     */
    fun reset() {
        currentRoiSize = config.roiConfig.initialSize
        targetRoiSize = config.roiConfig.initialSize
        roiAnimationProgress = 1f
        currentState = GuidanceState.SEARCHING
        stateEnteredTimeMs = 0L
        lastUpdateTimeMs = 0L
        lockedCandidateId = null
        lockStartTimeMs = 0L
        resetStabilityTracking()
        recentMotionScores.clear()
        recentSharpnessScores.clear()
        averageMotionScore = 0f
        lastInstantMotionScore = 0f
        averageSharpness = config.minSharpnessForGood
        lastHintChangeTimeMs = 0L
        currentHintText = null
    }
}

/**
 * Minimal candidate info needed for guidance evaluation.
 */
data class CandidateInfo(
    val trackingId: String?,
    val boxCenterX: Float,
    val boxCenterY: Float,
    val boxArea: Float,
    val confidence: Float
)

/**
 * Configuration for scan guidance behavior.
 */
data class ScanGuidanceConfig(
    /** ROI sizing configuration */
    val roiConfig: ScanRoiConfig = ScanRoiConfig(),

    /** Maximum motion score to consider scene stable */
    val maxMotionForStable: Float = 0.3f,

    /** Maximum motion score for "good" state */
    val maxMotionForGood: Float = 0.15f,

    /** Maximum motion score for lock eligibility */
    val maxMotionForLock: Float = 0.1f,

    /** Motion threshold that breaks an existing lock */
    val lockBreakMotionThreshold: Float = 0.25f,

    /** Minimum sharpness to consider focus acceptable */
    val minSharpnessForFocus: Float = 50f,

    /** Minimum sharpness for "good" state */
    val minSharpnessForGood: Float = 100f,

    /** Minimum sharpness for lock eligibility */
    val minSharpnessForLock: Float = 120f,

    /** Maximum center distance for "good" state (normalized, 0-0.707) */
    val maxCenterDistanceForGood: Float = 0.2f,

    /** Maximum center distance for lock eligibility */
    val maxCenterDistanceForLock: Float = 0.15f,

    /** Minimum consecutive stable frames for lock */
    val minStableFramesForLock: Int = 3,

    /** Minimum stable time for lock (ms) */
    val minStableTimeForLockMs: Long = 400L,

    /** Maximum lock duration before auto-release (ms) */
    val maxLockDurationMs: Long = 5000L,

    /** Position match threshold for stability tracking */
    val positionMatchThreshold: Float = 0.05f,

    /** Number of frames for motion averaging */
    val motionAverageWindow: Int = 5,

    /** Number of frames for sharpness averaging */
    val sharpnessAverageWindow: Int = 3,

    /** Minimum time between hint changes (ms) */
    val hintRateLimitMs: Long = 500L
)
