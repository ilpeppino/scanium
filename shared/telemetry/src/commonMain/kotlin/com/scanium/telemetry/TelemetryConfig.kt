package com.scanium.telemetry

import com.scanium.telemetry.TelemetrySeverity

/**
 * Shared configuration model for telemetry behavior.
 *
 * Defines operational toggles and limits for telemetry collection and export.
 * This configuration is platform-agnostic and should be mapped to platform-specific
 * implementations (e.g., OTLP exporter config).
 */
data class TelemetryConfig(
    /** Master toggle to enable/disable all telemetry */
    val enabled: Boolean = true,

    /** Minimum severity to log. Events below this severity are dropped. */
    val minSeverity: TelemetrySeverity = TelemetrySeverity.DEBUG,

    /** Probability of sampling a trace (0.0 - 1.0) */
    val traceSampleRate: Double = 0.1,

    /** Maximum number of events to buffer in memory before dropping new ones */
    val maxQueueSize: Int = 1000,

    /** Interval between automatic flushes in milliseconds */
    val flushIntervalMs: Long = 5000L,

    /** Maximum number of events to send in a single batch */
    val maxBatchSize: Int = 100,
    
    /** Base backoff delay in milliseconds when export fails */
    val backoffBaseDelayMs: Long = 1000L,
    
    /** Maximum backoff delay in milliseconds */
    val backoffMaxDelayMs: Long = 60000L
)
