package com.scanium.app.monitoring

/**
 * Status of a backend health check.
 * DEV-only: Used by the background health monitor.
 */
enum class MonitorHealthStatus {
    OK,
    FAIL,
}

/**
 * Result of checking a single endpoint.
 *
 * @property endpoint The endpoint that was checked (e.g., "/health", "/v1/config")
 * @property passed Whether the check passed according to the rules
 * @property httpCode The HTTP status code received (null if connection failed)
 * @property failureReason Human-readable failure reason (no secrets), null if passed
 */
data class EndpointCheckResult(
    val endpoint: String,
    val passed: Boolean,
    val httpCode: Int?,
    val failureReason: String?,
)

/**
 * Result of the complete health check run.
 *
 * @property status Overall status (OK if all endpoints pass, FAIL otherwise)
 * @property checkedAt Timestamp when the check was performed
 * @property detailsSummary Human-readable summary (e.g., "All 4 endpoints healthy")
 * @property failures List of failed endpoint checks
 * @property failureSignature A short signature of failures for deduplication (e.g., "401_config,timeout_health")
 */
data class HealthCheckResult(
    val status: MonitorHealthStatus,
    val checkedAt: Long,
    val detailsSummary: String,
    val failures: List<EndpointCheckResult>,
) {
    /**
     * Generate a failure signature for rate limiting.
     * Format: "<code>_<endpoint>,<code>_<endpoint>,..."
     * Empty string if no failures.
     */
    val failureSignature: String
        get() =
            failures
                .filter { !it.passed }
                .sortedBy { it.endpoint }
                .joinToString(",") { result ->
                    val code = result.httpCode?.toString() ?: "timeout"
                    "${code}_${result.endpoint.removePrefix("/").replace("/", "_")}"
                }
}

/**
 * Configuration for health monitoring.
 *
 * @property baseUrl The backend base URL to check
 * @property apiKey Optional API key for authenticated endpoints
 * @property authToken Optional auth token for authenticated endpoints (from sign-in)
 * @property notifyOnRecovery Whether to send notifications on recovery
 */
data class HealthMonitorConfig(
    val baseUrl: String,
    val apiKey: String?,
    val authToken: String? = null,
    val notifyOnRecovery: Boolean = true,
)
