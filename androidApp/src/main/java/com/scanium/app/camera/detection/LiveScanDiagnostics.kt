package com.scanium.app.camera.detection

import android.util.Log
import com.scanium.core.models.geometry.NormalizedRect
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

/**
 * Diagnostic instrumentation for live scan centering investigation.
 *
 * Provides structured logging for debugging why centered near objects
 * are missed while distant background items are incorrectly added.
 *
 * Enable via Developer Options > "Live Scan Diagnostics".
 * Logs use tag "LiveScan" for easy filtering.
 */
object LiveScanDiagnostics {
    private const val TAG = "LiveScan"

    // Diagnostic toggle (controlled via developer settings)
    private val _enabled = AtomicBoolean(false)
    var enabled: Boolean
        get() = _enabled.get()
        set(value) {
            _enabled.set(value)
            if (value) {
                Log.i(TAG, "=== LIVE SCAN DIAGNOSTICS ENABLED ===")
            }
        }

    // Frame tracking
    private val frameIdCounter = AtomicLong(0)

    /**
     * Data class for a single detection candidate with center scoring info.
     */
    data class CandidateInfo(
        val trackingId: String?,
        val boundingBox: NormalizedRect,
        val confidence: Float,
        val category: String,
        val normalizedArea: Float,
        val centerX: Float,
        val centerY: Float,
        val centerDistance: Float,
        val centerScore: Float,
        val selected: Boolean,
        val rejectionReason: String? = null,
    )

    /**
     * Data class for frame-level diagnostic info.
     */
    data class FrameDiagnostics(
        val frameId: Long,
        val timestamp: Long,
        val imageWidth: Int,
        val imageHeight: Int,
        val rotationDegrees: Int,
        val sharpnessScore: Float,
        val motionScore: Double,
        val candidateCount: Int,
        val selectedCandidate: CandidateInfo?,
        val rejectedCandidates: List<CandidateInfo>,
        val classificationTriggered: Boolean,
        val itemAdded: Boolean,
    )

    /**
     * Generate a new frame ID.
     */
    fun nextFrameId(): Long = frameIdCounter.incrementAndGet()

    /**
     * Log a complete frame analysis with all candidates.
     */
    fun logFrameAnalysis(diagnostics: FrameDiagnostics) {
        if (!enabled) return

        val sb = StringBuilder()
        sb.appendLine("=== FRAME ***REMOVED***${diagnostics.frameId} ===")
        sb.appendLine("  timestamp=${diagnostics.timestamp}")
        sb.appendLine("  image=${diagnostics.imageWidth}x${diagnostics.imageHeight} rot=${diagnostics.rotationDegrees}")
        sb.appendLine("  sharpness=${String.format("%.1f", diagnostics.sharpnessScore)}")
        sb.appendLine("  motion=${String.format("%.3f", diagnostics.motionScore)}")
        sb.appendLine("  candidates=${diagnostics.candidateCount}")

        if (diagnostics.selectedCandidate != null) {
            val sel = diagnostics.selectedCandidate
            sb.appendLine("  SELECTED:")
            sb.appendLine("    id=${sel.trackingId ?: "null"}")
            sb.appendLine("    category=${sel.category}")
            sb.appendLine("    conf=${String.format("%.2f", sel.confidence)}")
            sb.appendLine("    area=${String.format("%.4f", sel.normalizedArea)} (${String.format("%.1f", sel.normalizedArea * 100)}%)")
            sb.appendLine("    center=(${String.format("%.3f", sel.centerX)}, ${String.format("%.3f", sel.centerY)})")
            sb.appendLine("    centerDist=${String.format("%.3f", sel.centerDistance)}")
            sb.appendLine("    centerScore=${String.format("%.3f", sel.centerScore)}")
        } else {
            sb.appendLine("  SELECTED: none")
        }

        if (diagnostics.rejectedCandidates.isNotEmpty()) {
            sb.appendLine("  REJECTED (${diagnostics.rejectedCandidates.size}):")
            diagnostics.rejectedCandidates.take(3).forEach { rej ->
                sb.appendLine(
                    "    - ${rej.category}: area=${String.format("%.4f", rej.normalizedArea)}, " +
                        "centerDist=${String.format("%.3f", rej.centerDistance)}, " +
                        "reason=${rej.rejectionReason ?: "lower_score"}",
                )
            }
            if (diagnostics.rejectedCandidates.size > 3) {
                sb.appendLine("    ... and ${diagnostics.rejectedCandidates.size - 3} more")
            }
        }

        sb.appendLine("  classificationTriggered=${diagnostics.classificationTriggered}")
        sb.appendLine("  itemAdded=${diagnostics.itemAdded}")

        Log.i(TAG, sb.toString())
    }

    /**
     * Log a candidate selection decision in compact form.
     */
    fun logCandidateSelection(
        frameId: Long,
        selectedId: String?,
        selectedScore: Float,
        selectedCenterDist: Float,
        selectedArea: Float,
        totalCandidates: Int,
        rejectionReasons: Map<String, String>,
    ) {
        if (!enabled) return

        val reasons =
            if (rejectionReasons.isNotEmpty()) {
                rejectionReasons.entries.take(3).joinToString("; ") { "${it.key}:${it.value}" }
            } else {
                "none"
            }

        Log.d(
            TAG,
            "[FRAME ***REMOVED***$frameId] SELECT: id=$selectedId, score=${String.format("%.3f", selectedScore)}, " +
                "centerDist=${String.format("%.3f", selectedCenterDist)}, area=${String.format("%.4f", selectedArea)}, " +
                "total=$totalCandidates, rejected=$reasons",
        )
    }

    /**
     * Log when an item is being added with frame correlation.
     */
    fun logItemAdded(
        frameId: Long,
        candidateId: String,
        category: String,
        confidence: Float,
        centerDistance: Float,
        area: Float,
        sharpness: Float,
    ) {
        if (!enabled) return

        Log.i(
            TAG,
            "[FRAME ***REMOVED***$frameId] ITEM_ADDED: id=$candidateId, cat=$category, " +
                "conf=${String.format("%.2f", confidence)}, " +
                "centerDist=${String.format("%.3f", centerDistance)}, " +
                "area=${String.format("%.4f", area)}, " +
                "sharpness=${String.format("%.1f", sharpness)}",
        )
    }

    /**
     * Log when a candidate is rejected by gating rules.
     */
    fun logGatingRejection(
        frameId: Long,
        candidateId: String?,
        reason: String,
        value: Float,
        threshold: Float,
    ) {
        if (!enabled) return

        Log.d(
            TAG,
            "[FRAME ***REMOVED***$frameId] GATING_REJECT: id=$candidateId, reason=$reason, " +
                "value=${String.format("%.3f", value)}, threshold=${String.format("%.3f", threshold)}",
        )
    }

    /**
     * Log stability tracking state.
     */
    fun logStabilityState(
        frameId: Long,
        candidateId: String?,
        consecutiveFrames: Int,
        requiredFrames: Int,
        stableTimeMs: Long,
        requiredTimeMs: Long,
        isStable: Boolean,
    ) {
        if (!enabled) return

        Log.d(
            TAG,
            "[FRAME ***REMOVED***$frameId] STABILITY: id=$candidateId, " +
                "frames=$consecutiveFrames/$requiredFrames, " +
                "time=${stableTimeMs}ms/${requiredTimeMs}ms, " +
                "stable=$isStable",
        )
    }

    /**
     * Log sharpness computation result.
     */
    fun logSharpness(
        frameId: Long,
        sharpnessScore: Float,
        isBlurry: Boolean,
        threshold: Float,
    ) {
        if (!enabled) return

        Log.d(
            TAG,
            "[FRAME ***REMOVED***$frameId] SHARPNESS: score=${String.format("%.1f", sharpnessScore)}, " +
                "blurry=$isBlurry, threshold=${String.format("%.1f", threshold)}",
        )
    }

    /**
     * Helper to calculate center distance from frame center (0.5, 0.5).
     * Returns value in range [0, ~0.707] where 0 = centered, ~0.707 = corner.
     */
    fun calculateCenterDistance(box: NormalizedRect): Float {
        val boxCenterX = (box.left + box.right) / 2f
        val boxCenterY = (box.top + box.bottom) / 2f

        // Frame center is (0.5, 0.5) in normalized coordinates
        val dx = boxCenterX - 0.5f
        val dy = boxCenterY - 0.5f

        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Helper to get box center coordinates.
     */
    fun getBoxCenter(box: NormalizedRect): Pair<Float, Float> {
        val centerX = (box.left + box.right) / 2f
        val centerY = (box.top + box.bottom) / 2f
        return centerX to centerY
    }
}
