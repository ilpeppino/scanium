package com.scanium.telemetry.ports

/**
 * Port interface for emitting metric telemetry.
 *
 * Implementations of this port are responsible for:
 * - Recording metrics (counters, timers, gauges) to backend systems
 * - Aggregating metrics as appropriate for the backend
 *
 * The Telemetry facade guarantees that:
 * - Attributes are already sanitized (PII removed)
 * - Required attributes are present
 * - Metric names follow naming conventions
 */
interface MetricPort {
    /**
     * Increments a counter metric.
     *
     * @param name Metric name (e.g., "scan.items_detected")
     * @param delta Amount to increment (typically 1)
     * @param attributes Sanitized attributes for this metric
     */
    fun counter(
        name: String,
        delta: Long = 1,
        attributes: Map<String, String> = emptyMap(),
    )

    /**
     * Records a timer/duration metric.
     *
     * @param name Metric name (e.g., "scan.duration_ms")
     * @param millis Duration in milliseconds
     * @param attributes Sanitized attributes for this metric
     */
    fun timer(
        name: String,
        millis: Long,
        attributes: Map<String, String> = emptyMap(),
    )

    /**
     * Records a gauge metric (current value snapshot).
     *
     * @param name Metric name (e.g., "storage.items_count")
     * @param value Current value
     * @param attributes Sanitized attributes for this metric
     */
    fun gauge(
        name: String,
        value: Double,
        attributes: Map<String, String> = emptyMap(),
    )

    /**
     * Records a histogram metric for measuring distributions.
     *
     * Histograms are ideal for latency measurements where you need
     * percentile calculations (p50, p95, p99). The value is recorded
     * into predefined buckets for aggregation.
     *
     * @param name Metric name (e.g., "ml_inference_latency_ms")
     * @param value Observed value (e.g., latency in milliseconds)
     * @param attributes Sanitized attributes for this metric
     */
    fun histogram(
        name: String,
        value: Double,
        attributes: Map<String, String> = emptyMap(),
    )
}
