package com.scanium.core.tracking

import com.scanium.core.models.scanning.GuidanceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PHASE 5: Lightweight, privacy-safe metrics for scan confidence tracking.
 *
 * Tracks aggregated metrics (no images, no content) for:
 * - Frame analysis distribution
 * - Lock behavior and timing
 * - User interaction patterns
 * - Unlock reasons for debugging
 *
 * Exposed in:
 * - Developer → System Health (live)
 * - Logs (debug only)
 */
class ScanConfidenceMetrics {

    // ==================== Frame Metrics ====================

    private var totalFramesAnalyzed: Long = 0
    private var framesWithPreviewBbox: Long = 0
    private var framesReachingLock: Long = 0

    /** Percentage of frames with at least one preview bbox (0-100) */
    val previewBboxPercentage: Float
        get() = if (totalFramesAnalyzed > 0) {
            (framesWithPreviewBbox.toFloat() / totalFramesAnalyzed) * 100f
        } else 0f

    /** Percentage of frames that reached LOCKED state (0-100) */
    val lockPercentage: Float
        get() = if (totalFramesAnalyzed > 0) {
            (framesReachingLock.toFloat() / totalFramesAnalyzed) * 100f
        } else 0f

    // ==================== Lock Timing Metrics ====================

    private var lockAttempts: Int = 0
    private var successfulLocks: Int = 0
    private var totalTimeToLockMs: Long = 0
    private var lockStartTimeMs: Long = 0

    /** Average time to achieve lock in milliseconds */
    val avgTimeToLockMs: Float
        get() = if (successfulLocks > 0) {
            totalTimeToLockMs.toFloat() / successfulLocks
        } else 0f

    /** Success rate of lock attempts (0-100) */
    val lockSuccessRate: Float
        get() = if (lockAttempts > 0) {
            (successfulLocks.toFloat() / lockAttempts) * 100f
        } else 0f

    // ==================== Shutter Metrics ====================

    private var shutterTapsTotal: Int = 0
    private var shutterTapsWithoutEligibleBbox: Int = 0

    /** Percentage of shutter taps without eligible bbox (0-100) */
    val shutterTapsWithoutBboxPercentage: Float
        get() = if (shutterTapsTotal > 0) {
            (shutterTapsWithoutEligibleBbox.toFloat() / shutterTapsTotal) * 100f
        } else 0f

    // ==================== Unlock Reason Tracking ====================

    private val _unlockReasons = mutableMapOf<UnlockReason, Int>()

    /** Count of unlock events by reason */
    val unlockReasons: Map<UnlockReason, Int>
        get() = _unlockReasons.toMap()

    /** Most common unlock reason */
    val mostCommonUnlockReason: UnlockReason?
        get() = _unlockReasons.maxByOrNull { it.value }?.key

    // ==================== Live State Flow ====================

    private val _liveMetrics = MutableStateFlow(ScanMetricsSnapshot())
    val liveMetrics: StateFlow<ScanMetricsSnapshot> = _liveMetrics.asStateFlow()

    // ==================== Recording Methods ====================

    /**
     * Record a frame analysis result.
     *
     * @param hasPreviewBbox True if at least one bbox was shown
     * @param isLocked True if guidance state is LOCKED
     */
    fun recordFrame(hasPreviewBbox: Boolean, isLocked: Boolean) {
        totalFramesAnalyzed++
        if (hasPreviewBbox) framesWithPreviewBbox++
        if (isLocked) framesReachingLock++

        // Update live metrics periodically (every 30 frames)
        if (totalFramesAnalyzed % 30 == 0L) {
            updateLiveMetrics()
        }
    }

    /**
     * Record the start of a potential lock attempt.
     * Called when guidance enters GOOD state.
     */
    fun recordLockAttemptStart(timestampMs: Long) {
        if (lockStartTimeMs == 0L) {
            lockStartTimeMs = timestampMs
            lockAttempts++
        }
    }

    /**
     * Record a successful lock.
     */
    fun recordLockAchieved(timestampMs: Long) {
        if (lockStartTimeMs > 0) {
            val timeToLock = timestampMs - lockStartTimeMs
            totalTimeToLockMs += timeToLock
            successfulLocks++
            lockStartTimeMs = 0L
            updateLiveMetrics()
        }
    }

    /**
     * Record lock attempt failure (never reached LOCKED).
     */
    fun recordLockAttemptFailed() {
        lockStartTimeMs = 0L
    }

    /**
     * Record an unlock event with reason.
     */
    fun recordUnlock(reason: UnlockReason) {
        _unlockReasons[reason] = (_unlockReasons[reason] ?: 0) + 1
        updateLiveMetrics()
    }

    /**
     * Record a shutter tap event.
     *
     * @param hadEligibleBbox True if there was an eligible bbox when shutter was tapped
     */
    fun recordShutterTap(hadEligibleBbox: Boolean) {
        shutterTapsTotal++
        if (!hadEligibleBbox) {
            shutterTapsWithoutEligibleBbox++
        }
        updateLiveMetrics()
    }

    /**
     * Reset all metrics (call when starting a new session).
     */
    fun reset() {
        totalFramesAnalyzed = 0
        framesWithPreviewBbox = 0
        framesReachingLock = 0
        lockAttempts = 0
        successfulLocks = 0
        totalTimeToLockMs = 0
        lockStartTimeMs = 0
        shutterTapsTotal = 0
        shutterTapsWithoutEligibleBbox = 0
        _unlockReasons.clear()
        updateLiveMetrics()
    }

    /**
     * Get a snapshot of current metrics for logging/display.
     */
    fun getSnapshot(): ScanMetricsSnapshot {
        return ScanMetricsSnapshot(
            totalFrames = totalFramesAnalyzed,
            previewBboxPct = previewBboxPercentage,
            lockPct = lockPercentage,
            avgTimeToLockMs = avgTimeToLockMs,
            lockSuccessRate = lockSuccessRate,
            shutterWithoutBboxPct = shutterTapsWithoutBboxPercentage,
            unlockReasons = unlockReasons.toMap()
        )
    }

    private fun updateLiveMetrics() {
        _liveMetrics.value = getSnapshot()
    }

    /**
     * Format metrics as a debug string for logging.
     */
    fun toDebugString(): String {
        return buildString {
            appendLine("=== Scan Confidence Metrics ===")
            appendLine("Frames: $totalFramesAnalyzed")
            appendLine("Preview bbox: ${String.format("%.1f", previewBboxPercentage)}%")
            appendLine("Locked: ${String.format("%.1f", lockPercentage)}%")
            appendLine("Avg time-to-lock: ${String.format("%.0f", avgTimeToLockMs)}ms")
            appendLine("Lock success: ${String.format("%.1f", lockSuccessRate)}%")
            appendLine("Shutter w/o bbox: ${String.format("%.1f", shutterTapsWithoutBboxPercentage)}%")
            if (_unlockReasons.isNotEmpty()) {
                appendLine("Unlock reasons:")
                _unlockReasons.entries.sortedByDescending { it.value }.forEach { (reason, count) ->
                    appendLine("  - $reason: $count")
                }
            }
        }
    }
}

/**
 * Reasons for unlock events (for debugging).
 */
enum class UnlockReason {
    /** High motion detected (camera moved) */
    MOTION,
    /** Focus lost or blur detected */
    FOCUS,
    /** Object moved off-center */
    OFF_CENTER,
    /** Object left the ROI */
    LEFT_ROI,
    /** Lock timeout exceeded */
    TIMEOUT,
    /** Candidate lost (no detection) */
    CANDIDATE_LOST,
    /** User action (e.g., stopped scanning) */
    USER_ACTION
}

/**
 * Snapshot of scan metrics for display/logging.
 */
data class ScanMetricsSnapshot(
    val totalFrames: Long = 0,
    val previewBboxPct: Float = 0f,
    val lockPct: Float = 0f,
    val avgTimeToLockMs: Float = 0f,
    val lockSuccessRate: Float = 0f,
    val shutterWithoutBboxPct: Float = 0f,
    val unlockReasons: Map<UnlockReason, Int> = emptyMap()
) {
    fun toDebugString(): String = buildString {
        append("Frames:$totalFrames ")
        append("Bbox:${String.format("%.0f", previewBboxPct)}% ")
        append("Lock:${String.format("%.0f", lockPct)}% ")
        append("TTL:${String.format("%.0f", avgTimeToLockMs)}ms ")
    }
}
