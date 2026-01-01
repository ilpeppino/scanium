package com.scanium.app.camera.detection

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Adaptive throttle policy that automatically adjusts detector intervals
 * based on rolling average frame processing time.
 *
 * When the device is slow (avg processing time exceeds threshold), intervals
 * are automatically increased to reduce CPU/battery load. When performance
 * improves, intervals are decreased back toward baseline.
 *
 * Thread-safe: Uses atomic operations and synchronized access.
 */
class AdaptiveThrottlePolicy(
    private val config: AdaptiveThrottleConfig = AdaptiveThrottleConfig(),
) {
    companion object {
        private const val TAG = "AdaptiveThrottlePolicy"
    }

    // Rolling window of processing times (circular buffer)
    private val processingTimes = LongArray(config.rollingWindowSize)
    private var windowIndex = 0
    private var windowCount = 0
    private val windowLock = Any()

    // Current adaptive multiplier (1.0 = no adjustment)
    private val _adaptiveMultiplier = MutableStateFlow(1.0f)
    val adaptiveMultiplier: StateFlow<Float> = _adaptiveMultiplier.asStateFlow()

    // Throttle state
    private val _isThrottling = MutableStateFlow(false)
    val isThrottling: StateFlow<Boolean> = _isThrottling.asStateFlow()

    // Policy enabled state (can be toggled via developer settings)
    private val _isEnabled = AtomicBoolean(config.enabledByDefault)
    val isEnabled: Boolean get() = _isEnabled.get()

    // Metrics
    private val totalFramesProcessed = AtomicLong(0)
    private val throttledFrames = AtomicLong(0)
    private var lastAdjustmentTimeMs = 0L
    private var lastLogTimeMs = 0L

    /**
     * Records a frame processing time and potentially adjusts throttle intervals.
     *
     * @param processingTimeMs Time taken to process the frame in milliseconds
     * @return Updated adaptive multiplier (1.0 = no adjustment, >1.0 = throttling)
     */
    fun recordProcessingTime(processingTimeMs: Long): Float {
        if (!_isEnabled.get()) {
            return 1.0f
        }

        totalFramesProcessed.incrementAndGet()

        synchronized(windowLock) {
            // Add to rolling window
            processingTimes[windowIndex] = processingTimeMs
            windowIndex = (windowIndex + 1) % config.rollingWindowSize
            if (windowCount < config.rollingWindowSize) {
                windowCount++
            }

            // Calculate rolling average
            val rollingAvg = calculateRollingAverage()

            // Only adjust if we have enough samples
            if (windowCount >= config.minSamplesForAdjustment) {
                adjustThrottleMultiplier(rollingAvg)
            }

            // Log metrics periodically
            maybeLogMetrics(rollingAvg, processingTimeMs)

            return _adaptiveMultiplier.value
        }
    }

    /**
     * Calculates the rolling average processing time.
     */
    private fun calculateRollingAverage(): Long {
        if (windowCount == 0) return 0L
        var sum = 0L
        for (i in 0 until windowCount) {
            sum += processingTimes[i]
        }
        return sum / windowCount
    }

    /**
     * Adjusts the throttle multiplier based on the rolling average.
     */
    private fun adjustThrottleMultiplier(rollingAvgMs: Long) {
        val now = SystemClock.elapsedRealtime()

        // Rate-limit adjustments to avoid oscillation
        if (now - lastAdjustmentTimeMs < config.adjustmentCooldownMs) {
            return
        }

        val currentMultiplier = _adaptiveMultiplier.value
        val newMultiplier: Float

        when {
            // High load: increase throttling
            rollingAvgMs > config.highLoadThresholdMs -> {
                newMultiplier =
                    (currentMultiplier * config.throttleIncreaseRate)
                        .coerceAtMost(config.maxMultiplier)
                if (newMultiplier > currentMultiplier) {
                    _isThrottling.value = true
                    throttledFrames.incrementAndGet()
                    Log.i(
                        TAG,
                        "[LOW_POWER] Increasing throttle: avg=${rollingAvgMs}ms > threshold=${config.highLoadThresholdMs}ms, multiplier=${"%.2f".format(
                            currentMultiplier,
                        )} -> ${"%.2f".format(newMultiplier)}",
                    )
                }
            }

            // Low load: decrease throttling
            rollingAvgMs < config.lowLoadThresholdMs && currentMultiplier > 1.0f -> {
                newMultiplier =
                    (currentMultiplier * config.throttleDecreaseRate)
                        .coerceAtLeast(1.0f)
                if (newMultiplier < currentMultiplier) {
                    Log.i(
                        TAG,
                        "[LOW_POWER] Decreasing throttle: avg=${rollingAvgMs}ms < threshold=${config.lowLoadThresholdMs}ms, multiplier=${"%.2f".format(
                            currentMultiplier,
                        )} -> ${"%.2f".format(newMultiplier)}",
                    )
                    if (newMultiplier <= 1.05f) {
                        _isThrottling.value = false
                        newMultiplier.let { _adaptiveMultiplier.value = 1.0f }
                        return
                    }
                }
            }

            else -> return // No adjustment needed
        }

        _adaptiveMultiplier.value = newMultiplier
        lastAdjustmentTimeMs = now
    }

    /**
     * Applies the adaptive multiplier to a base interval.
     *
     * @param baseIntervalMs Base throttle interval in milliseconds
     * @return Adjusted interval (baseInterval * multiplier), clamped to bounds
     */
    fun applyToInterval(baseIntervalMs: Long): Long {
        if (!_isEnabled.get()) {
            return baseIntervalMs
        }

        val adjustedInterval = (baseIntervalMs * _adaptiveMultiplier.value).toLong()
        return adjustedInterval.coerceIn(config.minIntervalMs, config.maxIntervalMs)
    }

    /**
     * Enables or disables adaptive throttling.
     */
    fun setEnabled(enabled: Boolean) {
        val wasEnabled = _isEnabled.getAndSet(enabled)
        if (wasEnabled != enabled) {
            Log.i(TAG, "Adaptive throttling ${if (enabled) "enabled" else "disabled"}")
            if (!enabled) {
                // Reset state when disabled
                reset()
            }
        }
    }

    /**
     * Resets all adaptive state.
     */
    fun reset() {
        synchronized(windowLock) {
            windowIndex = 0
            windowCount = 0
            processingTimes.fill(0L)
        }
        _adaptiveMultiplier.value = 1.0f
        _isThrottling.value = false
        lastAdjustmentTimeMs = 0L
        Log.d(TAG, "Adaptive throttle policy reset")
    }

    /**
     * Gets current statistics for debugging.
     */
    fun getStats(): AdaptiveThrottleStats {
        val rollingAvg = synchronized(windowLock) { calculateRollingAverage() }
        return AdaptiveThrottleStats(
            isEnabled = _isEnabled.get(),
            isThrottling = _isThrottling.value,
            adaptiveMultiplier = _adaptiveMultiplier.value,
            rollingAverageMs = rollingAvg,
            windowSampleCount = windowCount,
            totalFramesProcessed = totalFramesProcessed.get(),
            throttledFrameCount = throttledFrames.get(),
        )
    }

    private fun maybeLogMetrics(
        rollingAvgMs: Long,
        lastProcessingTimeMs: Long,
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLogTimeMs < config.metricsLogIntervalMs) return

        lastLogTimeMs = now
        val stats = getStats()
        Log.i(
            TAG,
            buildString {
                append("[METRICS] ")
                append("enabled=${stats.isEnabled}, ")
                append("throttling=${stats.isThrottling}, ")
                append("multiplier=${"%.2f".format(stats.adaptiveMultiplier)}, ")
                append("rollingAvg=${stats.rollingAverageMs}ms, ")
                append("lastFrame=${lastProcessingTimeMs}ms, ")
                append("samples=${stats.windowSampleCount}/${config.rollingWindowSize}, ")
                append("totalFrames=${stats.totalFramesProcessed}")
            },
        )
    }
}

/**
 * Configuration for adaptive throttle policy.
 */
data class AdaptiveThrottleConfig(
    /** Size of rolling window for averaging processing times */
    val rollingWindowSize: Int = 10,
    /** Minimum samples needed before making adjustments */
    val minSamplesForAdjustment: Int = 5,
    /** Processing time threshold (ms) above which throttling increases */
    val highLoadThresholdMs: Long = 150L,
    /** Processing time threshold (ms) below which throttling decreases */
    val lowLoadThresholdMs: Long = 80L,
    /** Multiplier increase rate when high load detected (e.g., 1.25 = 25% increase) */
    val throttleIncreaseRate: Float = 1.25f,
    /** Multiplier decrease rate when low load detected (e.g., 0.9 = 10% decrease) */
    val throttleDecreaseRate: Float = 0.9f,
    /** Maximum throttle multiplier (caps how much intervals can increase) */
    val maxMultiplier: Float = 3.0f,
    /** Minimum interval after adaptive adjustment (ms) */
    val minIntervalMs: Long = 200L,
    /** Maximum interval after adaptive adjustment (ms) */
    val maxIntervalMs: Long = 2000L,
    /** Cooldown between adjustments to avoid oscillation (ms) */
    val adjustmentCooldownMs: Long = 500L,
    /** Interval for logging metrics (ms) */
    val metricsLogIntervalMs: Long = 10_000L,
    /** Whether adaptive throttling is enabled by default */
    val enabledByDefault: Boolean = true,
)

/**
 * Statistics for adaptive throttle policy.
 */
data class AdaptiveThrottleStats(
    val isEnabled: Boolean,
    val isThrottling: Boolean,
    val adaptiveMultiplier: Float,
    val rollingAverageMs: Long,
    val windowSampleCount: Int,
    val totalFramesProcessed: Long,
    val throttledFrameCount: Long,
)
