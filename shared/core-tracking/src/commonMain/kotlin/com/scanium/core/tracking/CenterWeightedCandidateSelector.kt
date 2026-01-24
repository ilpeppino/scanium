package com.scanium.core.tracking

import com.scanium.core.models.geometry.NormalizedRect
import com.scanium.core.models.scanning.ScanRoi
import kotlin.math.sqrt

/**
 * Center-weighted candidate selection with gating rules.
 *
 * This selector prioritizes detection candidates that are:
 * 1. Centered in the viewfinder (user intent)
 * 2. Large enough to be near objects (not distant background)
 * 3. High confidence
 * 4. Stable across frames (not flickering)
 *
 * The goal is to fix the issue where distant background objects are added
 * while centered near objects are missed during scanning.
 */
class CenterWeightedCandidateSelector(
    private val config: CenterWeightedConfig = CenterWeightedConfig(),
) {
    /**
     * Stability tracking for candidates.
     * Key: candidate tracking ID or generated ID
     * Value: StabilityState
     */
    private val stabilityTracker = mutableMapOf<String, StabilityState>()

    /**
     * Track the last selected candidate for stability checking.
     */
    private var lastSelectedId: String? = null
    private var lastSelectionTimeMs: Long = 0L

    /**
     * Select the best candidate from a list of detections.
     *
     * @param detections List of raw detections from ML Kit
     * @param frameSharpness Current frame sharpness score
     * @param currentTimeMs Current timestamp for stability tracking
     * @return SelectionResult with best candidate and rejection reasons
     */
    fun selectBestCandidate(
        detections: List<DetectionInfo>,
        frameSharpness: Float,
        currentTimeMs: Long,
    ): SelectionResult {
        if (detections.isEmpty()) {
            return SelectionResult(
                selectedCandidate = null,
                rejectedCandidates = emptyList(),
                selectionReason = "no_detections",
            )
        }

        // Score and filter candidates
        val scoredCandidates =
            detections.map { detection ->
                val centerDistance = calculateCenterDistance(detection.boundingBox)
                val area = detection.normalizedBoxArea
                val confidence = detection.confidence

                // Calculate composite score
                val score = calculateScore(confidence, area, centerDistance)

                // Apply gating rules
                val gatingResult =
                    applyGatingRules(
                        detection = detection,
                        centerDistance = centerDistance,
                        area = area,
                        frameSharpness = frameSharpness,
                    )

                ScoredCandidate(
                    detection = detection,
                    score = score,
                    centerDistance = centerDistance,
                    gatingResult = gatingResult,
                )
            }

        // Filter out gated candidates
        val passedGating = scoredCandidates.filter { it.gatingResult.passed }
        val rejected = scoredCandidates.filter { !it.gatingResult.passed }

        if (passedGating.isEmpty()) {
            // All candidates rejected by gating
            return SelectionResult(
                selectedCandidate = null,
                rejectedCandidates =
                    rejected.map {
                        RejectedCandidate(
                            detection = it.detection,
                            centerDistance = it.centerDistance,
                            score = it.score,
                            reason = it.gatingResult.reason ?: "gating_failed",
                        )
                    },
                selectionReason = "all_gated",
            )
        }

        // Select highest scoring candidate
        val bestCandidate = passedGating.maxByOrNull { it.score }!!

        // Check stability requirement
        val candidateId = bestCandidate.detection.trackingId ?: generateCandidateId(bestCandidate.detection)
        val stabilityResult = checkStability(candidateId, currentTimeMs)

        // Update stability state
        updateStabilityState(candidateId, currentTimeMs)

        // Build rejection list
        val rejectedList =
            scoredCandidates
                .filter { it != bestCandidate }
                .map {
                    RejectedCandidate(
                        detection = it.detection,
                        centerDistance = it.centerDistance,
                        score = it.score,
                        reason = if (it.gatingResult.passed) "lower_score" else (it.gatingResult.reason ?: "gating_failed"),
                    )
                }

        return SelectionResult(
            selectedCandidate =
                SelectedCandidate(
                    detection = bestCandidate.detection,
                    score = bestCandidate.score,
                    centerDistance = bestCandidate.centerDistance,
                    isStable = stabilityResult.isStable,
                    consecutiveFrames = stabilityResult.consecutiveFrames,
                    stableTimeMs = stabilityResult.stableTimeMs,
                ),
            rejectedCandidates = rejectedList,
            selectionReason = if (stabilityResult.isStable) "stable_selection" else "pending_stability",
        )
    }

    /**
     * Select the best candidate using a ScanRoi for filtering.
     *
     * This version uses the ROI boundaries instead of fixed center distance,
     * ensuring alignment between what the user sees and what gets detected.
     *
     * @param detections List of raw detections from ML Kit
     * @param scanRoi The current scan ROI for filtering
     * @param frameSharpness Current frame sharpness score
     * @param currentTimeMs Current timestamp for stability tracking
     * @return SelectionResult with best candidate and rejection reasons
     */
    fun selectBestCandidateWithRoi(
        detections: List<DetectionInfo>,
        scanRoi: ScanRoi,
        frameSharpness: Float,
        currentTimeMs: Long,
    ): SelectionResult {
        if (detections.isEmpty()) {
            return SelectionResult(
                selectedCandidate = null,
                rejectedCandidates = emptyList(),
                selectionReason = "no_detections",
            )
        }

        // Score and filter candidates using ROI
        val scoredCandidates =
            detections.map { detection ->
                val boxCenterX = (detection.boundingBox.left + detection.boundingBox.right) / 2f
                val boxCenterY = (detection.boundingBox.top + detection.boundingBox.bottom) / 2f
                val centerDistance = calculateCenterDistance(detection.boundingBox)
                val area = detection.normalizedBoxArea
                val confidence = detection.confidence

                // Calculate center score using ROI
                val roiCenterScore = scanRoi.centerScore(boxCenterX, boxCenterY)

                // Calculate score with ROI-aware center weighting
                val score = calculateScoreWithRoi(confidence, area, roiCenterScore)

                // Apply ROI-aware gating rules
                val gatingResult =
                    applyGatingRulesWithRoi(
                        detection = detection,
                        boundingBox = detection.boundingBox,
                        scanRoi = scanRoi,
                        area = area,
                        frameSharpness = frameSharpness,
                    )

                ScoredCandidate(
                    detection = detection,
                    score = score,
                    centerDistance = centerDistance,
                    gatingResult = gatingResult,
                )
            }

        // Filter out gated candidates
        val passedGating = scoredCandidates.filter { it.gatingResult.passed }
        val rejected = scoredCandidates.filter { !it.gatingResult.passed }

        if (passedGating.isEmpty()) {
            return SelectionResult(
                selectedCandidate = null,
                rejectedCandidates =
                    rejected.map {
                        RejectedCandidate(
                            detection = it.detection,
                            centerDistance = it.centerDistance,
                            score = it.score,
                            reason = it.gatingResult.reason ?: "gating_failed",
                        )
                    },
                selectionReason = "all_gated",
            )
        }

        // Select highest scoring candidate
        val bestCandidate = passedGating.maxByOrNull { it.score }!!

        // Check stability requirement
        val candidateId = bestCandidate.detection.trackingId ?: generateCandidateId(bestCandidate.detection)
        val stabilityResult = checkStability(candidateId, currentTimeMs)

        // Update stability state
        updateStabilityState(candidateId, currentTimeMs)

        // Build rejection list
        val rejectedList =
            scoredCandidates
                .filter { it != bestCandidate }
                .map {
                    RejectedCandidate(
                        detection = it.detection,
                        centerDistance = it.centerDistance,
                        score = it.score,
                        reason = if (it.gatingResult.passed) "lower_score" else (it.gatingResult.reason ?: "gating_failed"),
                    )
                }

        return SelectionResult(
            selectedCandidate =
                SelectedCandidate(
                    detection = bestCandidate.detection,
                    score = bestCandidate.score,
                    centerDistance = bestCandidate.centerDistance,
                    isStable = stabilityResult.isStable,
                    consecutiveFrames = stabilityResult.consecutiveFrames,
                    stableTimeMs = stabilityResult.stableTimeMs,
                ),
            rejectedCandidates = rejectedList,
            selectionReason = if (stabilityResult.isStable) "stable_selection" else "pending_stability",
        )
    }

    /**
     * Calculate score with ROI-aware center weighting.
     *
     * Updated formula per Phase 5:
     *   score = confidence * 0.5 + areaScore * 0.2 + centerScore * 0.3
     */
    private fun calculateScoreWithRoi(
        confidence: Float,
        area: Float,
        roiCenterScore: Float,
    ): Float {
        // Normalize area to 0-1 range (cap at 0.5 to avoid huge objects dominating)
        val normalizedArea = (area / 0.5f).coerceIn(0f, 1f)

        // Use Phase 5 scoring formula
        return (confidence * 0.5f) +
            (normalizedArea * 0.2f) +
            (roiCenterScore * 0.3f)
    }

    /**
     * Apply ROI-aware gating rules with strict containment check.
     */
    private fun applyGatingRulesWithRoi(
        detection: DetectionInfo,
        boundingBox: NormalizedRect,
        scanRoi: ScanRoi,
        area: Float,
        frameSharpness: Float,
    ): GatingResult {
        // Rule 1: ROI containment gate
        // Reject if ENTIRE bounding box is not fully inside ROI (unless very high confidence)
        // This ensures background objects outside guideline overlay are excluded
        val isInsideRoi =
            scanRoi.containsBox(
                boundingBox.left,
                boundingBox.top,
                boundingBox.right,
                boundingBox.bottom,
            )
        if (!isInsideRoi && detection.confidence < config.highConfidenceOverride) {
            return GatingResult(
                passed = false,
                reason = "outside_roi",
                value = 0f,
                threshold = 0f,
            )
        }

        // Rule 2: Minimum area gate
        // Reject tiny distant objects
        if (area < config.minArea) {
            return GatingResult(
                passed = false,
                reason = "min_area",
                value = area,
                threshold = config.minArea,
            )
        }

        // Rule 3: Sharpness gate for small objects
        // Small objects in blurry frames are likely background noise
        if (frameSharpness < config.minSharpness && area < config.sharpnessAreaThreshold) {
            return GatingResult(
                passed = false,
                reason = "sharpness_small_object",
                value = frameSharpness,
                threshold = config.minSharpness,
            )
        }

        return GatingResult(passed = true)
    }

    /**
     * Calculate center distance from frame center (0.5, 0.5).
     * Returns value in range [0, ~0.707] where 0 = centered, ~0.707 = corner.
     */
    private fun calculateCenterDistance(box: NormalizedRect): Float {
        val boxCenterX = (box.left + box.right) / 2f
        val boxCenterY = (box.top + box.bottom) / 2f

        val dx = boxCenterX - 0.5f
        val dy = boxCenterY - 0.5f

        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Calculate composite score for a candidate.
     *
     * Score formula:
     *   score = (confidence * 0.4) + (area * 0.3) + ((1 - centerDistance) * 0.3)
     *
     * This prioritizes:
     * - 40% confidence (ML Kit's assessment)
     * - 30% area (near objects are larger)
     * - 30% center proximity (user intent)
     */
    private fun calculateScore(
        confidence: Float,
        area: Float,
        centerDistance: Float,
    ): Float {
        // Normalize area to 0-1 range (cap at 0.5 to avoid huge objects dominating)
        val normalizedArea = (area / 0.5f).coerceIn(0f, 1f)

        // Normalize center distance (max theoretical is ~0.707 for corner)
        val normalizedCenterScore = 1f - (centerDistance / 0.707f).coerceIn(0f, 1f)

        return (confidence * config.confidenceWeight) +
            (normalizedArea * config.areaWeight) +
            (normalizedCenterScore * config.centerWeight)
    }

    /**
     * Apply gating rules to reject unsuitable candidates.
     */
    private fun applyGatingRules(
        detection: DetectionInfo,
        centerDistance: Float,
        area: Float,
        frameSharpness: Float,
    ): GatingResult {
        // Rule 1: Center distance gate
        // Reject if too far from center (unless very high confidence)
        if (centerDistance > config.maxCenterDistance && detection.confidence < config.highConfidenceOverride) {
            return GatingResult(
                passed = false,
                reason = "center_distance",
                value = centerDistance,
                threshold = config.maxCenterDistance,
            )
        }

        // Rule 2: Minimum area gate
        // Reject tiny distant objects
        if (area < config.minArea) {
            return GatingResult(
                passed = false,
                reason = "min_area",
                value = area,
                threshold = config.minArea,
            )
        }

        // Rule 3: Sharpness gate for small objects
        // Small objects in blurry frames are likely background noise
        if (frameSharpness < config.minSharpness && area < config.sharpnessAreaThreshold) {
            return GatingResult(
                passed = false,
                reason = "sharpness_small_object",
                value = frameSharpness,
                threshold = config.minSharpness,
            )
        }

        return GatingResult(passed = true)
    }

    /**
     * Check stability requirement for a candidate.
     */
    private fun checkStability(
        candidateId: String,
        currentTimeMs: Long,
    ): StabilityCheckResult {
        val state = stabilityTracker[candidateId]
        val isConsecutiveSelection = candidateId == lastSelectedId
        val consecutiveFrames =
            when {
                state == null -> 1
                isConsecutiveSelection -> state.consecutiveFrames + 1
                else -> 1
            }
        val firstSeenTime = state?.firstSeenTimeMs ?: currentTimeMs
        val timeSinceFirstSeen = currentTimeMs - firstSeenTime
        val meetsFrameRequirement = consecutiveFrames >= config.minStabilityFrames
        val meetsTimeRequirement = timeSinceFirstSeen >= config.minStabilityTimeMs

        return StabilityCheckResult(
            isStable = meetsFrameRequirement || meetsTimeRequirement,
            consecutiveFrames = consecutiveFrames,
            stableTimeMs = timeSinceFirstSeen,
        )
    }

    /**
     * Update stability tracking state.
     */
    private fun updateStabilityState(
        candidateId: String,
        currentTimeMs: Long,
    ) {
        val existing = stabilityTracker[candidateId]

        if (existing != null && candidateId == lastSelectedId) {
            // Same candidate selected again - increment count
            stabilityTracker[candidateId] =
                existing.copy(
                    consecutiveFrames = existing.consecutiveFrames + 1,
                    lastSeenTimeMs = currentTimeMs,
                )
        } else {
            // New candidate or different from last selection
            // Reset stability for all other candidates
            if (candidateId != lastSelectedId) {
                // Keep this candidate's state, remove others that are stale
                stabilityTracker.entries.removeAll { (id, state) ->
                    id != candidateId && currentTimeMs - state.lastSeenTimeMs > config.stabilityExpiryMs
                }
            }

            // Create or reset state for this candidate
            stabilityTracker[candidateId] =
                StabilityState(
                    firstSeenTimeMs = existing?.firstSeenTimeMs ?: currentTimeMs,
                    lastSeenTimeMs = currentTimeMs,
                    consecutiveFrames = if (candidateId == lastSelectedId) (existing?.consecutiveFrames ?: 0) + 1 else 1,
                )
        }

        lastSelectedId = candidateId
        lastSelectionTimeMs = currentTimeMs
    }

    /**
     * Generate a stable ID for candidates without tracking ID.
     */
    private fun generateCandidateId(detection: DetectionInfo): String {
        val box = detection.boundingBox
        val centerX = ((box.left + box.right) / 2f * 100).toInt()
        val centerY = ((box.top + box.bottom) / 2f * 100).toInt()
        return "gen_${detection.category}_${centerX}_$centerY"
    }

    /**
     * Reset all stability tracking state.
     * Call when starting a new scan session.
     */
    fun reset() {
        stabilityTracker.clear()
        lastSelectedId = null
        lastSelectionTimeMs = 0L
    }

    /**
     * Get current stability state for debugging.
     */
    fun getStabilityStats(): Map<String, StabilityState> = stabilityTracker.toMap()
}

/**
 * Configuration for center-weighted candidate selection.
 */
data class CenterWeightedConfig(
    // Scoring weights (should sum to 1.0)
    val confidenceWeight: Float = 0.4f,
    val areaWeight: Float = 0.3f,
    val centerWeight: Float = 0.3f,
    // Gating thresholds
    val maxCenterDistance: Float = 0.35f,
// Max distance from center (0.5, 0.5)
    val minArea: Float = 0.03f,
// Minimum box area (3% of frame)
    val minSharpness: Float = 100f,
// Minimum sharpness score
    val sharpnessAreaThreshold: Float = 0.10f,
// Area below which sharpness is checked
    val highConfidenceOverride: Float = 0.8f,
// Confidence that bypasses center gate
    // Stability requirements
    val minStabilityFrames: Int = 3,
// Min consecutive frames
    val minStabilityTimeMs: Long = 400,
// Min time in milliseconds
    val stabilityExpiryMs: Long = 1000,
// Time after which stale stability is cleared
)

/**
 * Internal stability tracking state.
 */
data class StabilityState(
    val firstSeenTimeMs: Long,
    val lastSeenTimeMs: Long,
    val consecutiveFrames: Int,
)

/**
 * Internal stability check result.
 */
private data class StabilityCheckResult(
    val isStable: Boolean,
    val consecutiveFrames: Int,
    val stableTimeMs: Long,
)

/**
 * Internal gating result.
 */
private data class GatingResult(
    val passed: Boolean,
    val reason: String? = null,
    val value: Float = 0f,
    val threshold: Float = 0f,
)

/**
 * Internal scored candidate.
 */
private data class ScoredCandidate(
    val detection: DetectionInfo,
    val score: Float,
    val centerDistance: Float,
    val gatingResult: GatingResult,
)

/**
 * Result of candidate selection.
 */
data class SelectionResult(
    val selectedCandidate: SelectedCandidate?,
    val rejectedCandidates: List<RejectedCandidate>,
    val selectionReason: String,
)

/**
 * A selected candidate with scoring details.
 */
data class SelectedCandidate(
    val detection: DetectionInfo,
    val score: Float,
    val centerDistance: Float,
    val isStable: Boolean,
    val consecutiveFrames: Int,
    val stableTimeMs: Long,
)

/**
 * A rejected candidate with reason.
 */
data class RejectedCandidate(
    val detection: DetectionInfo,
    val centerDistance: Float,
    val score: Float,
    val reason: String,
)
