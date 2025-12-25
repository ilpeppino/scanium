package com.scanium.telemetry

import kotlinx.serialization.Serializable

/**
 * Runtime configuration for telemetry system.
 *
 * This configuration controls telemetry behavior across all platforms without requiring
 * app redeployment. It provides operational toggles for:
 * - Enabling/disabling telemetry export
 * - Filtering events by severity
 * - Controlling trace sampling rates
 * - Managing queue sizes and flush behavior
 *
 * ## Usage Example
 * ```kotlin
 * val config = TelemetryConfig(
 *     enabled = true,
 *     minSeverity = TelemetrySeverity.INFO,
 *     traceSampleRate = 0.1,
 *     maxQueueSize = 1000,
 *     flushIntervalMs = 5000,
 *     dataRegion = "EU"
 * )
 * ```
 *
 * ## Recommended Defaults for Mobile
 * - **Development**: enabled=true, minSeverity=DEBUG, traceSampleRate=0.1, maxQueueSize=1000
 * - **Production**: enabled=true, minSeverity=INFO, traceSampleRate=0.01, maxQueueSize=500
 * - **Offline/Testing**: enabled=false
 *
 * @property enabled Master toggle for telemetry export. When false, all ports become no-ops.
 * @property minSeverity Minimum severity level to emit. Events below this level are filtered.
 * @property traceSampleRate Trace sampling rate (0.0 to 1.0). Logs/metrics always exported when enabled.
 * @property maxQueueSize Maximum events to buffer before dropping oldest. Prevents memory exhaustion.
 * @property flushIntervalMs Interval in milliseconds to flush buffered events (default: 5000ms).
 * @property maxBatchSize Maximum events per batch export (default: 100).
 * @property dropPolicy Policy when queue is full: DROP_OLDEST or DROP_NEWEST.
 * @property maxRetries Maximum retry attempts for failed exports (default: 3).
 * @property retryBackoffMs Base backoff in milliseconds for exponential retry (default: 1000ms).
 * @property dataRegion Region where telemetry data is stored/processed (e.g., "EU", "US").
 */
@Serializable
data class TelemetryConfig(
    val enabled: Boolean = true,
    val minSeverity: TelemetrySeverity = TelemetrySeverity.INFO,
    val traceSampleRate: Double = 0.1,
    val maxQueueSize: Int = 500,
    val flushIntervalMs: Long = 5000,
    val maxBatchSize: Int = 100,
    val dropPolicy: DropPolicy = DropPolicy.DROP_OLDEST,
    val maxRetries: Int = 3,
    val retryBackoffMs: Long = 1000,
    val dataRegion: String = "US"
) {
    init {
        require(traceSampleRate in 0.0..1.0) {
            "traceSampleRate must be between 0.0 and 1.0, got $traceSampleRate"
        }
        require(maxQueueSize > 0) {
            "maxQueueSize must be positive, got $maxQueueSize"
        }
        require(flushIntervalMs > 0) {
            "flushIntervalMs must be positive, got $flushIntervalMs"
        }
        require(maxBatchSize > 0) {
            "maxBatchSize must be positive, got $maxBatchSize"
        }
        require(maxRetries >= 0) {
            "maxRetries must be non-negative, got $maxRetries"
        }
        require(retryBackoffMs > 0) {
            "retryBackoffMs must be positive, got $retryBackoffMs"
        }
        require(dataRegion.isNotBlank()) {
            "dataRegion must not be blank"
        }
    }

    /**
     * Drop policy when queue is full.
     */
    @Serializable
    enum class DropPolicy {
        /** Drop oldest events when queue is full (recommended for most cases) */
        DROP_OLDEST,

        /** Drop newest events when queue is full (preserves historical context) */
        DROP_NEWEST
    }

    companion object {
        /**
         * Disabled configuration (no telemetry export).
         */
        val DISABLED = TelemetryConfig(enabled = false)

        /**
         * Default configuration for local development.
         * - Verbose logging (DEBUG level)
         * - 10% trace sampling
         * - Larger queue for debugging
         */
        fun development() = TelemetryConfig(
            enabled = true,
            minSeverity = TelemetrySeverity.DEBUG,
            traceSampleRate = 0.1,
            maxQueueSize = 1000,
            flushIntervalMs = 5000,
            maxBatchSize = 100,
            dataRegion = "US"
        )

        /**
         * Default configuration for production.
         * - Info-level logging only
         * - 1% trace sampling (reduce overhead)
         * - Smaller queue to conserve memory
         */
        fun production() = TelemetryConfig(
            enabled = true,
            minSeverity = TelemetrySeverity.INFO,
            traceSampleRate = 0.01,
            maxQueueSize = 500,
            flushIntervalMs = 10000,
            maxBatchSize = 100,
            dataRegion = "US"
        )

        /**
         * Default configuration for staging/testing.
         * - Info-level logging
         * - 5% trace sampling (balance between coverage and overhead)
         * - Medium queue size
         */
        fun staging() = TelemetryConfig(
            enabled = true,
            minSeverity = TelemetrySeverity.INFO,
            traceSampleRate = 0.05,
            maxQueueSize = 750,
            flushIntervalMs = 7500,
            maxBatchSize = 100,
            dataRegion = "US"
        )
    }
}
