package com.scanium.app.camera

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stall reason for NO_FRAMES watchdog.
 */
enum class StallReason {
    NONE,
    NO_FRAMES,
    RECOVERING,
    FAILED,
}

/**
 * Diagnostics state for camera pipeline debugging.
 * Exposed via StateFlow for debug overlay rendering.
 */
data class CameraPipelineDiagnostics(
    val sessionId: Int = 0,
    val isCameraBound: Boolean = false,
    /** True if we called setAnalyzer() on ImageAnalysis */
    val isAnalysisAttached: Boolean = false,
    /** True if we have received at least 1 frame in current session */
    val isAnalysisFlowing: Boolean = false,
    /** Legacy - now derived from isAnalysisAttached */
    val isAnalysisRunning: Boolean = false,
    val isPreviewDetectionActive: Boolean = false,
    val isScanningActive: Boolean = false,
    val lastFrameTimestampMs: Long = 0L,
    val lastBboxTimestampMs: Long = 0L,
    val analysisFramesPerSecond: Double = 0.0,
    val lifecycleState: String = "UNKNOWN",
    val navDestination: String = "",
    val bboxCount: Int = 0,
    /** Current stall reason from NO_FRAMES watchdog */
    val stallReason: StallReason = StallReason.NONE,
    /** Number of recovery attempts made by watchdog */
    val recoveryAttempts: Int = 0,
) {
    companion object {
        fun initial() = CameraPipelineDiagnostics()
    }
}

/**
 * Manages camera session lifecycle with session-based invalidation.
 *
 * Key guarantees:
 * 1. Each session has a unique ID - old session callbacks are ignored
 * 2. Session start/stop are idempotent (safe to call multiple times)
 * 3. Diagnostics state is always up-to-date for debug overlay
 * 4. Logging with CAM_LIFE tag for lifecycle debugging
 *
 * Usage:
 * - Call startSession() when camera should be active
 * - Call stopSession() when camera should be inactive
 * - Check isSessionActive() or session ID for callback validation
 */
class CameraSessionController {
    companion object {
        private const val TAG = "CAM_LIFE"

        // Rate limiting for repeating log values (1 per second)
        private const val LOG_RATE_LIMIT_MS = 1000L
    }

    private val sessionIdCounter = AtomicInteger(0)

    @Volatile
    private var currentSessionId: Int = 0

    @Volatile
    private var isActive: Boolean = false

    private val _diagnostics = MutableStateFlow(CameraPipelineDiagnostics.initial())
    val diagnostics: StateFlow<CameraPipelineDiagnostics> = _diagnostics.asStateFlow()

    // Rate limiting state
    private var lastFrameLogTime = 0L
    private var lastBboxLogTime = 0L
    private var lastLifecycleLogTime = 0L
    private var lastLoggedFrameTs = 0L
    private var lastLoggedBboxTs = 0L
    private var lastLoggedLifecycle = ""

    /**
     * Starts a new camera session.
     *
     * @return The new session ID. Use this to validate callbacks belong to current session.
     */
    fun startSession(): Int {
        val newSessionId = sessionIdCounter.incrementAndGet()
        val wasActive = isActive

        currentSessionId = newSessionId
        isActive = true

        Log.i(TAG, "SESSION_START: id=$newSessionId (was active=$wasActive)")

        updateDiagnostics { it.copy(sessionId = newSessionId) }

        return newSessionId
    }

    /**
     * Stops the current session.
     * All callbacks with the old session ID will be ignored.
     */
    fun stopSession() {
        val oldSessionId = currentSessionId
        val wasActive = isActive

        isActive = false

        if (wasActive) {
            Log.i(TAG, "SESSION_STOP: id=$oldSessionId")
        }

        // Clear diagnostics to show session is stopped
        updateDiagnostics {
            it.copy(
                isAnalysisRunning = false,
                isAnalysisAttached = false,
                isAnalysisFlowing = false,
                isPreviewDetectionActive = false,
                isScanningActive = false,
                bboxCount = 0,
                stallReason = StallReason.NONE,
                recoveryAttempts = 0,
            )
        }
    }

    /**
     * Checks if the given session ID is still valid.
     * Use this in callbacks to ensure they belong to the current session.
     */
    fun isSessionValid(sessionId: Int): Boolean = sessionId == currentSessionId && isActive

    /**
     * Checks if any session is currently active.
     */
    fun isSessionActive(): Boolean = isActive

    /**
     * Gets the current session ID.
     */
    fun getCurrentSessionId(): Int = currentSessionId

    // =========================================================================
    // Diagnostics Updates (called by CameraXManager/CameraScreen)
    // =========================================================================

    fun updateCameraBound(bound: Boolean) {
        logIfChanged("CAMERA_BOUND", bound.toString())
        updateDiagnostics { it.copy(isCameraBound = bound) }
    }

    fun updateAnalysisRunning(running: Boolean) {
        logIfChanged("ANALYSIS_RUNNING", running.toString())
        updateDiagnostics { it.copy(isAnalysisRunning = running) }
    }

    fun updateAnalysisAttached(attached: Boolean) {
        logIfChanged("ANALYSIS_ATTACHED", attached.toString())
        updateDiagnostics { it.copy(isAnalysisAttached = attached) }
    }

    fun updateAnalysisFlowing(flowing: Boolean) {
        logIfChanged("ANALYSIS_FLOWING", flowing.toString())
        updateDiagnostics { it.copy(isAnalysisFlowing = flowing) }
    }

    fun updateStallReason(reason: StallReason) {
        logIfChanged("STALL_REASON", reason.name)
        updateDiagnostics { it.copy(stallReason = reason) }
    }

    fun updateRecoveryAttempts(attempts: Int) {
        updateDiagnostics { it.copy(recoveryAttempts = attempts) }
    }

    fun updatePreviewDetectionActive(active: Boolean) {
        logIfChanged("PREVIEW_DETECTION", active.toString())
        updateDiagnostics { it.copy(isPreviewDetectionActive = active) }
    }

    fun updateScanningActive(active: Boolean) {
        logIfChanged("SCANNING", active.toString())
        updateDiagnostics { it.copy(isScanningActive = active) }
    }

    fun updateLastFrameTimestamp(timestampMs: Long) {
        val now = System.currentTimeMillis()
        // Rate limit frame timestamp logging to 1 per second
        if (now - lastFrameLogTime >= LOG_RATE_LIMIT_MS && timestampMs != lastLoggedFrameTs) {
            Log.d(TAG, "FRAME: ts=$timestampMs")
            lastFrameLogTime = now
            lastLoggedFrameTs = timestampMs
        }
        updateDiagnostics { it.copy(lastFrameTimestampMs = timestampMs) }
    }

    fun updateLastBboxTimestamp(
        timestampMs: Long,
        count: Int,
    ) {
        val now = System.currentTimeMillis()
        // Rate limit bbox timestamp logging to 1 per second
        if (now - lastBboxLogTime >= LOG_RATE_LIMIT_MS && timestampMs != lastLoggedBboxTs) {
            Log.d(TAG, "BBOX: ts=$timestampMs, count=$count")
            lastBboxLogTime = now
            lastLoggedBboxTs = timestampMs
        }
        updateDiagnostics { it.copy(lastBboxTimestampMs = timestampMs, bboxCount = count) }
    }

    fun updateAnalysisFps(fps: Double) {
        updateDiagnostics { it.copy(analysisFramesPerSecond = fps) }
    }

    fun updateLifecycleState(state: String) {
        val now = System.currentTimeMillis()
        // Rate limit lifecycle logging unless state changes
        if (state != lastLoggedLifecycle || now - lastLifecycleLogTime >= LOG_RATE_LIMIT_MS) {
            Log.i(TAG, "LIFECYCLE: $state")
            lastLifecycleLogTime = now
            lastLoggedLifecycle = state
        }
        updateDiagnostics { it.copy(lifecycleState = state) }
    }

    fun updateNavDestination(destination: String) {
        Log.i(TAG, "NAV_DESTINATION: $destination")
        updateDiagnostics { it.copy(navDestination = destination) }
    }

    /**
     * Logs a lifecycle event (not rate-limited).
     * Use for important one-time events like bind/unbind, start/stop.
     */
    fun logEvent(
        event: String,
        details: String = "",
    ) {
        if (details.isNotEmpty()) {
            Log.i(TAG, "$event: $details")
        } else {
            Log.i(TAG, event)
        }
    }

    private fun logIfChanged(
        event: String,
        value: String,
    ) {
        // Always log state changes (not rate-limited)
        Log.i(TAG, "$event: $value")
    }

    private fun updateDiagnostics(update: (CameraPipelineDiagnostics) -> CameraPipelineDiagnostics) {
        _diagnostics.value = update(_diagnostics.value)
    }
}
