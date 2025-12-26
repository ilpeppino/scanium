package com.scanium.app.camera.detection

import android.os.SystemClock

/**
 * Time-based throttling helper for detector invocations.
 *
 * Tracks the last invocation time per detector type and determines
 * whether enough time has passed to allow a new invocation.
 *
 * Thread-safe: Uses synchronized access to internal state.
 */
class ThrottleHelper {

    companion object {
        private const val TAG = "ThrottleHelper"

        /** Default throttle intervals per detector type (milliseconds) */
        val DEFAULT_INTERVALS = mapOf(
            DetectorType.OBJECT to 400L,    // Object detection is heavy, run ~2.5x per second max
            DetectorType.BARCODE to 100L,   // Barcode is lighter, can run more frequently
            DetectorType.DOCUMENT to 500L   // Document detection is heavy
        )
    }

    /** Configuration: minimum interval between invocations per detector */
    private val minIntervals = mutableMapOf<DetectorType, Long>()

    /** Last invocation timestamps per detector (using elapsedRealtime for monotonic time) */
    private val lastInvocationTime = mutableMapOf<DetectorType, Long>()

    /** Lock for thread-safe access */
    private val lock = Any()

    init {
        // Initialize with defaults
        DEFAULT_INTERVALS.forEach { (type, interval) ->
            minIntervals[type] = interval
        }
    }

    /**
     * Configure the minimum interval for a detector type.
     *
     * @param detectorType The detector to configure
     * @param intervalMs Minimum milliseconds between invocations
     */
    fun setMinInterval(detectorType: DetectorType, intervalMs: Long) {
        require(intervalMs >= 0) { "Interval must be non-negative" }
        synchronized(lock) {
            minIntervals[detectorType] = intervalMs
        }
    }

    /**
     * Get the current minimum interval for a detector type.
     */
    fun getMinInterval(detectorType: DetectorType): Long {
        synchronized(lock) {
            return minIntervals[detectorType] ?: DEFAULT_INTERVALS[detectorType] ?: 0L
        }
    }

    /**
     * Check if enough time has passed since the last invocation.
     *
     * @param detectorType The detector to check
     * @param currentTimeMs Current timestamp (use SystemClock.elapsedRealtime() for consistency)
     * @return true if the detector can be invoked, false if throttled
     */
    fun canInvoke(detectorType: DetectorType, currentTimeMs: Long = SystemClock.elapsedRealtime()): Boolean {
        synchronized(lock) {
            val lastTime = lastInvocationTime[detectorType] ?: 0L
            val minInterval = minIntervals[detectorType] ?: 0L
            return (currentTimeMs - lastTime) >= minInterval
        }
    }

    /**
     * Check if invocation is allowed and record the timestamp if so.
     *
     * Atomic operation combining canInvoke() and recordInvocation().
     *
     * @param detectorType The detector to check and record
     * @param currentTimeMs Current timestamp
     * @return true if invocation was allowed and recorded, false if throttled
     */
    fun tryInvoke(detectorType: DetectorType, currentTimeMs: Long = SystemClock.elapsedRealtime()): Boolean {
        synchronized(lock) {
            val lastTime = lastInvocationTime[detectorType] ?: 0L
            val minInterval = minIntervals[detectorType] ?: 0L

            return if ((currentTimeMs - lastTime) >= minInterval) {
                lastInvocationTime[detectorType] = currentTimeMs
                true
            } else {
                false
            }
        }
    }

    /**
     * Record an invocation timestamp without checking throttle.
     * Use when you've already checked canInvoke() separately.
     */
    fun recordInvocation(detectorType: DetectorType, currentTimeMs: Long = SystemClock.elapsedRealtime()) {
        synchronized(lock) {
            lastInvocationTime[detectorType] = currentTimeMs
        }
    }

    /**
     * Get time remaining until next allowed invocation.
     *
     * @return Milliseconds until allowed, or 0 if already allowed
     */
    fun timeUntilAllowed(detectorType: DetectorType, currentTimeMs: Long = SystemClock.elapsedRealtime()): Long {
        synchronized(lock) {
            val lastTime = lastInvocationTime[detectorType] ?: 0L
            val minInterval = minIntervals[detectorType] ?: 0L
            val elapsed = currentTimeMs - lastTime
            return (minInterval - elapsed).coerceAtLeast(0L)
        }
    }

    /**
     * Reset throttle state for a specific detector.
     */
    fun reset(detectorType: DetectorType) {
        synchronized(lock) {
            lastInvocationTime.remove(detectorType)
        }
    }

    /**
     * Reset all throttle state.
     */
    fun resetAll() {
        synchronized(lock) {
            lastInvocationTime.clear()
        }
    }

    /**
     * Get debug statistics.
     */
    fun getStats(currentTimeMs: Long = SystemClock.elapsedRealtime()): Map<DetectorType, ThrottleStats> {
        synchronized(lock) {
            return DetectorType.entries.associateWith { type ->
                ThrottleStats(
                    minIntervalMs = minIntervals[type] ?: 0L,
                    lastInvocationMs = lastInvocationTime[type] ?: 0L,
                    timeSinceLastMs = currentTimeMs - (lastInvocationTime[type] ?: currentTimeMs)
                )
            }
        }
    }
}

/**
 * Statistics for a single detector's throttle state.
 */
data class ThrottleStats(
    val minIntervalMs: Long,
    val lastInvocationMs: Long,
    val timeSinceLastMs: Long
)
