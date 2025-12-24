package com.scanium.app.telemetry

/**
 * Configuration for OTLP (OpenTelemetry Protocol) export.
 *
 * Controls where and how telemetry data (logs, metrics, traces) is exported
 * to an OTLP-compatible backend like Grafana Alloy.
 *
 * ***REMOVED******REMOVED*** Endpoints
 * - HTTP: http://localhost:4318 (default for Alloy)
 * - gRPC: http://localhost:4317 (not currently supported)
 *
 * Paths are automatically appended:
 * - Logs: {baseUrl}/v1/logs
 * - Metrics: {baseUrl}/v1/metrics
 * - Traces: {baseUrl}/v1/traces
 *
 * ***REMOVED******REMOVED*** Example Configuration
 * ```kotlin
 * val config = OtlpConfiguration(
 *     enabled = true,
 *     endpoint = "http://localhost:4318",
 *     environment = "dev",
 *     traceSamplingRate = 0.1
 * )
 * ```
 */
data class OtlpConfiguration(
    /**
     * Enable/disable OTLP export globally.
     * When false, all port implementations become no-ops.
     */
    val enabled: Boolean = false,

    /**
     * OTLP HTTP endpoint base URL (without path).
     * Example: "http://localhost:4318" or "https://otlp.example.com:4318"
     *
     * Paths (/v1/logs, /v1/metrics, /v1/traces) are appended automatically.
     */
    val endpoint: String = "http://localhost:4318",

    /**
     * Deployment environment tag.
     * Included as resource attribute: deployment.environment
     * Common values: "dev", "staging", "prod"
     */
    val environment: String = "dev",

    /**
     * Trace sampling rate (0.0 to 1.0).
     * - 0.0 = no traces exported
     * - 0.1 = 10% of traces exported (recommended for dev)
     * - 1.0 = all traces exported (only for testing/debugging)
     *
     * Logs and metrics are always exported when enabled.
     */
    val traceSamplingRate: Double = 0.1,

    /**
     * Service name for resource attributes.
     * Included as resource attribute: service.name
     */
    val serviceName: String = "scanium-mobile",

    /**
     * Service version (typically app version).
     * Included as resource attribute: service.version
     */
    val serviceVersion: String = "unknown",

    /**
     * Maximum batch size before forcing export.
     * Higher values reduce network overhead but increase memory usage.
     */
    val maxBatchSize: Int = 100,

    /**
     * Batch timeout in milliseconds.
     * Export batch even if not full after this duration.
     */
    val batchTimeoutMs: Long = 5000,

    /**
     * HTTP request timeout in milliseconds.
     */
    val httpTimeoutMs: Long = 10000,

    /**
     * Enable debug logging for OTLP export.
     * Logs export attempts and failures (no sensitive data).
     */
    val debugLogging: Boolean = false
) {
    companion object {
        /**
         * Disabled configuration (no-op).
         */
        val DISABLED = OtlpConfiguration(enabled = false)

        /**
         * Default local development configuration.
         * Points to localhost:4318 (Grafana Alloy default).
         */
        fun localDev(
            serviceVersion: String = "dev",
            traceSamplingRate: Double = 0.1
        ) = OtlpConfiguration(
            enabled = true,
            endpoint = "http://10.0.2.2:4318", // Android emulator host
            environment = "dev",
            serviceVersion = serviceVersion,
            traceSamplingRate = traceSamplingRate,
            debugLogging = true
        )
    }

    /**
     * Validates configuration.
     * @throws IllegalArgumentException if invalid
     */
    fun validate() {
        require(traceSamplingRate in 0.0..1.0) {
            "traceSamplingRate must be between 0.0 and 1.0, got $traceSamplingRate"
        }
        require(maxBatchSize > 0) {
            "maxBatchSize must be positive, got $maxBatchSize"
        }
        require(batchTimeoutMs > 0) {
            "batchTimeoutMs must be positive, got $batchTimeoutMs"
        }
        require(httpTimeoutMs > 0) {
            "httpTimeoutMs must be positive, got $httpTimeoutMs"
        }
        if (enabled) {
            require(endpoint.isNotBlank()) {
                "endpoint must not be blank when enabled"
            }
            require(endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
                "endpoint must start with http:// or https://, got $endpoint"
            }
        }
    }
}
