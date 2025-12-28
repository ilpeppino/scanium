package com.scanium.app.camera.detection

import android.os.SystemClock
import android.util.Log
import com.scanium.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Diagnostic instrumentation for the scan pipeline.
 *
 * Provides structured logging and metrics collection for debugging
 * live scanning vs picture capture discrepancies.
 *
 * All logging is debug-only and respects the scanning diagnostics toggle.
 */
object ScanPipelineDiagnostics {
    private const val TAG = "ScanPipeline"

    // Diagnostic toggle (controlled via developer settings)
    private val _enabled = AtomicBoolean(BuildConfig.DEBUG)
    var enabled: Boolean
        get() = _enabled.get()
        set(value) = _enabled.set(value)

    // Frame counters
    private val frameId = AtomicLong(0)
    private val framesReceived = AtomicLong(0)
    private val framesProcessed = AtomicLong(0)
    private val framesDropped = AtomicLong(0)
    private val framesThrottled = AtomicLong(0)

    // Detection counters
    private val detectionsRun = AtomicLong(0)
    private val detectionsWithResults = AtomicLong(0)
    private val itemsConfirmed = AtomicLong(0)

    // Timing
    private var sessionStartMs = 0L
    private val lastDetectionTimeMs = AtomicLong(0)
    private val lastFrameTimeMs = AtomicLong(0)

    // Why idle reason
    private val _whyIdleReason = MutableStateFlow("not_started")
    val whyIdleReason: StateFlow<String> = _whyIdleReason.asStateFlow()

    // Metrics state flow for optional overlay
    private val _metricsState = MutableStateFlow(ScanMetricsState())
    val metricsState: StateFlow<ScanMetricsState> = _metricsState.asStateFlow()

    /**
     * Start a new diagnostic session.
     */
    fun startSession() {
        if (!enabled) return

        sessionStartMs = SystemClock.elapsedRealtime()
        frameId.set(0)
        framesReceived.set(0)
        framesProcessed.set(0)
        framesDropped.set(0)
        framesThrottled.set(0)
        detectionsRun.set(0)
        detectionsWithResults.set(0)
        itemsConfirmed.set(0)
        lastDetectionTimeMs.set(0)
        lastFrameTimeMs.set(0)
        _whyIdleReason.value = "starting"

        Log.i(TAG, "=== SESSION STARTED ===")
    }

    /**
     * Stop the diagnostic session and log summary.
     */
    fun stopSession() {
        if (!enabled) return

        val duration = SystemClock.elapsedRealtime() - sessionStartMs
        val fps = if (duration > 0) framesProcessed.get() * 1000.0 / duration else 0.0
        val detectionRate = if (duration > 0) detectionsRun.get() * 1000.0 / duration else 0.0

        Log.i(TAG, buildString {
            append("=== SESSION ENDED ===\n")
            append("  Duration: ${duration}ms\n")
            append("  Frames received: ${framesReceived.get()}\n")
            append("  Frames processed: ${framesProcessed.get()}\n")
            append("  Frames dropped: ${framesDropped.get()}\n")
            append("  Frames throttled: ${framesThrottled.get()}\n")
            append("  Effective FPS: ${"%.2f".format(fps)}\n")
            append("  Detections run: ${detectionsRun.get()}\n")
            append("  Detections with results: ${detectionsWithResults.get()}\n")
            append("  Items confirmed: ${itemsConfirmed.get()}\n")
            append("  Detection rate: ${"%.2f".format(detectionRate)}/sec")
        })

        _whyIdleReason.value = "stopped"
    }

    /**
     * Log frame arrival with motion-based throttle decision.
     */
    fun logFrameArrival(
        motionScore: Double,
        analysisIntervalMs: Long,
        timeSinceLastAnalysis: Long,
        willProcess: Boolean,
        dropReason: String? = null
    ) {
        if (!enabled) return

        val fid = frameId.incrementAndGet()
        framesReceived.incrementAndGet()
        lastFrameTimeMs.set(SystemClock.elapsedRealtime())

        if (willProcess) {
            framesProcessed.incrementAndGet()
            _whyIdleReason.value = "processing"
        } else {
            if (dropReason?.contains("throttle") == true) {
                framesThrottled.incrementAndGet()
                _whyIdleReason.value = "throttled"
            } else {
                framesDropped.incrementAndGet()
                _whyIdleReason.value = dropReason ?: "dropped"
            }
        }

        // Log only occasionally to avoid spam (every 10th frame or when processing)
        if (willProcess || fid % 10 == 0L) {
            Log.d(TAG, buildString {
                append("[FRAME #$fid] ")
                append("motion=${"%.3f".format(motionScore)} ")
                append("interval=${analysisIntervalMs}ms ")
                append("sinceLast=${timeSinceLastAnalysis}ms ")
                append(if (willProcess) "→ PROCESS" else "→ DROP ($dropReason)")
            })
        }

        updateMetricsState()
    }

    /**
     * Log detection invocation.
     */
    fun logDetectionInvoked(
        mode: String,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int
    ) {
        if (!enabled) return

        detectionsRun.incrementAndGet()

        Log.d(TAG, buildString {
            append("[DETECT] ")
            append("mode=$mode ")
            append("size=${imageWidth}x${imageHeight} ")
            append("rotation=$rotationDegrees°")
        })
    }

    /**
     * Log detection result.
     */
    fun logDetectionResult(
        detectionCount: Int,
        topConfidence: Float,
        inferenceTimeMs: Long,
        itemsAdded: Int
    ) {
        if (!enabled) return

        if (detectionCount > 0) {
            detectionsWithResults.incrementAndGet()
        }
        itemsConfirmed.addAndGet(itemsAdded.toLong())
        lastDetectionTimeMs.set(SystemClock.elapsedRealtime())

        Log.d(TAG, buildString {
            append("[RESULT] ")
            append("detections=$detectionCount ")
            append("topConf=${"%.2f".format(topConfidence)} ")
            append("inference=${inferenceTimeMs}ms ")
            append("itemsAdded=$itemsAdded")
        })

        updateMetricsState()
    }

    /**
     * Log why detection was skipped.
     */
    fun logDetectionSkipped(reason: String) {
        if (!enabled) return

        Log.d(TAG, "[SKIP] Detection skipped: $reason")
        _whyIdleReason.value = reason
    }

    /**
     * Get current metrics for overlay display.
     */
    fun getMetrics(): ScanMetrics {
        val now = SystemClock.elapsedRealtime()
        val duration = now - sessionStartMs
        val fps = if (duration > 0) framesProcessed.get() * 1000.0 / duration else 0.0
        val infRate = if (duration > 0) detectionsRun.get() * 1000.0 / duration else 0.0
        val lastDet = lastDetectionTimeMs.get()
        val msSinceLastDet = if (lastDet > 0) now - lastDet else -1

        return ScanMetrics(
            effectiveFps = fps,
            inferenceRate = infRate,
            droppedFrames = framesDropped.get(),
            throttledFrames = framesThrottled.get(),
            msSinceLastDetection = msSinceLastDet,
            detectionCount = detectionsWithResults.get(),
            itemsConfirmed = itemsConfirmed.get(),
            whyIdle = _whyIdleReason.value
        )
    }

    private fun updateMetricsState() {
        _metricsState.value = ScanMetricsState(
            metrics = getMetrics(),
            timestamp = SystemClock.elapsedRealtime()
        )
    }
}

/**
 * Snapshot of scan pipeline metrics.
 */
data class ScanMetrics(
    val effectiveFps: Double = 0.0,
    val inferenceRate: Double = 0.0,
    val droppedFrames: Long = 0,
    val throttledFrames: Long = 0,
    val msSinceLastDetection: Long = -1,
    val detectionCount: Long = 0,
    val itemsConfirmed: Long = 0,
    val whyIdle: String = "unknown"
) {
    /**
     * Format for overlay display.
     */
    fun toOverlayString(): String = buildString {
        append("Live: fps=${"%.1f".format(effectiveFps)} ")
        append("inf/s=${"%.1f".format(inferenceRate)} ")
        if (msSinceLastDetection >= 0) {
            append("lastDet=${msSinceLastDetection}ms ")
        }
        append("det=$detectionCount ")
        if (throttledFrames > 0) {
            append("throttled=$throttledFrames")
        }
    }
}

/**
 * State wrapper for metrics flow.
 */
data class ScanMetricsState(
    val metrics: ScanMetrics = ScanMetrics(),
    val timestamp: Long = 0
)
