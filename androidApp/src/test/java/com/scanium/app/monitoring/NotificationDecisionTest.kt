package com.scanium.app.monitoring

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for NotificationDecision logic.
 * Tests all state transitions and rate limiting behavior.
 */
class NotificationDecisionTest {
    private val currentTimeMs = 1_000_000_000L
    private val sixHoursMs = 6L * 60 * 60 * 1000

    // =========================================================================
    // OK -> FAIL Transitions
    // =========================================================================

    @Test
    fun `OK to FAIL - should notify immediately`() {
        val result = makeFailResult("health timeout")

        val decision =
            NotificationDecision.shouldNotify(
                previousStatus = MonitorHealthStatus.OK,
                currentResult = result,
                previousFailureSignature = null,
                lastNotifiedAt = null,
                notifyOnRecovery = true,
                currentTimeMs = currentTimeMs,
            )

        assertThat(decision).isInstanceOf(NotificationDecision.Decision.NotifyFailure::class.java)
        assertThat((decision as NotificationDecision.Decision.NotifyFailure).reason)
            .isEqualTo("health timeout")
    }

    @Test
    fun `first run with FAIL - should notify`() {
        val result = makeFailResult("config unauthorized (401)")

        val decision =
            NotificationDecision.shouldNotify(
                // First run
                previousStatus = null,
                currentResult = result,
                previousFailureSignature = null,
                lastNotifiedAt = null,
                notifyOnRecovery = true,
                currentTimeMs = currentTimeMs,
            )

        assertThat(decision).isInstanceOf(NotificationDecision.Decision.NotifyFailure::class.java)
    }

    @Test
    fun `first run with OK - should not notify`() {
        val result = makeOkResult()

        val decision =
            NotificationDecision.shouldNotify(
                previousStatus = null,
                currentResult = result,
                previousFailureSignature = null,
                lastNotifiedAt = null,
                notifyOnRecovery = true,
                currentTimeMs = currentTimeMs,
            )

        assertThat(decision).isEqualTo(NotificationDecision.Decision.NoNotification)
    }

    // =========================================================================
    // FAIL -> FAIL Transitions (Rate Limiting)
    // =========================================================================

    @Test
    fun `FAIL to FAIL with same signature within 6h - should not notify`() {
        val result = makeFailResult("health timeout", signature = "timeout_health")
        val recentNotifyTime = currentTimeMs - (sixHoursMs / 2) // 3 hours ago

        val decision =
            NotificationDecision.shouldNotify(
                previousStatus = MonitorHealthStatus.FAIL,
                currentResult = result,
                previousFailureSignature = "timeout_health",
                lastNotifiedAt = recentNotifyTime,
                notifyOnRecovery = true,
                currentTimeMs = currentTimeMs,
            )

        assertThat(decision).isEqualTo(NotificationDecision.Decision.NoNotification)
    }

    @Test
    fun `FAIL to FAIL with same signature after 6h - should notify as reminder`() {
        val result = makeFailResult("health timeout", signature = "timeout_health")
        val oldNotifyTime = currentTimeMs - (sixHoursMs + 1000) // Just over 6 hours ago

        val decision =
            NotificationDecision.shouldNotify(
                previousStatus = MonitorHealthStatus.FAIL,
                currentResult = result,
                previousFailureSignature = "timeout_health",
                lastNotifiedAt = oldNotifyTime,
                notifyOnRecovery = true,
                currentTimeMs = currentTimeMs,
            )

        assertThat(decision).isInstanceOf(NotificationDecision.Decision.NotifyFailure::class.java)
    }

    @Test
    fun `FAIL to FAIL with different signature - should notify immediately`() {
        val result = makeFailResult("config unauthorized (401)", signature = "401_v1_config")

        val decision =
            NotificationDecision.shouldNotify(
                previousStatus = MonitorHealthStatus.FAIL,
                currentResult = result,
                // Different signature
                previousFailureSignature = "timeout_health",
                // Very recent
                lastNotifiedAt = currentTimeMs - 1000,
                notifyOnRecovery = true,
                currentTimeMs = currentTimeMs,
            )

        assertThat(decision).isInstanceOf(NotificationDecision.Decision.NotifyFailure::class.java)
    }

    @Test
    fun `FAIL to FAIL with no previous notification - should notify`() {
        val result = makeFailResult("health timeout", signature = "timeout_health")

        val decision =
            NotificationDecision.shouldNotify(
                previousStatus = MonitorHealthStatus.FAIL,
                currentResult = result,
                previousFailureSignature = "timeout_health",
                // Never notified
                lastNotifiedAt = null,
                notifyOnRecovery = true,
                currentTimeMs = currentTimeMs,
            )

        assertThat(decision).isInstanceOf(NotificationDecision.Decision.NotifyFailure::class.java)
    }

    // =========================================================================
    // FAIL -> OK Transitions (Recovery)
    // =========================================================================

    @Test
    fun `FAIL to OK with notifyOnRecovery true - should notify recovery`() {
        val result = makeOkResult()

        val decision =
            NotificationDecision.shouldNotify(
                previousStatus = MonitorHealthStatus.FAIL,
                currentResult = result,
                previousFailureSignature = "timeout_health",
                lastNotifiedAt = null,
                notifyOnRecovery = true,
                currentTimeMs = currentTimeMs,
            )

        assertThat(decision).isEqualTo(NotificationDecision.Decision.NotifyRecovery)
    }

    @Test
    fun `FAIL to OK with notifyOnRecovery false - should not notify`() {
        val result = makeOkResult()

        val decision =
            NotificationDecision.shouldNotify(
                previousStatus = MonitorHealthStatus.FAIL,
                currentResult = result,
                previousFailureSignature = "timeout_health",
                lastNotifiedAt = null,
                notifyOnRecovery = false,
                currentTimeMs = currentTimeMs,
            )

        assertThat(decision).isEqualTo(NotificationDecision.Decision.NoNotification)
    }

    // =========================================================================
    // OK -> OK Transitions
    // =========================================================================

    @Test
    fun `OK to OK - should not notify`() {
        val result = makeOkResult()

        val decision =
            NotificationDecision.shouldNotify(
                previousStatus = MonitorHealthStatus.OK,
                currentResult = result,
                previousFailureSignature = null,
                lastNotifiedAt = null,
                notifyOnRecovery = true,
                currentTimeMs = currentTimeMs,
            )

        assertThat(decision).isEqualTo(NotificationDecision.Decision.NoNotification)
    }

    // =========================================================================
    // Helper Functions
    // =========================================================================

    private fun makeOkResult(): HealthCheckResult {
        return HealthCheckResult(
            status = MonitorHealthStatus.OK,
            checkedAt = currentTimeMs,
            detailsSummary = "All 4 endpoints healthy",
            failures = emptyList(),
        )
    }

    private fun makeFailResult(
        failureReason: String,
        signature: String? = null,
    ): HealthCheckResult {
        val failures =
            listOf(
                EndpointCheckResult(
                    endpoint = "/health",
                    passed = false,
                    httpCode = null,
                    failureReason = failureReason,
                ),
            )

        // Create a result with a predictable signature if specified
        val result =
            HealthCheckResult(
                status = MonitorHealthStatus.FAIL,
                checkedAt = currentTimeMs,
                detailsSummary = "1/4 endpoints failed",
                failures = failures,
            )

        // Verify signature if expected
        if (signature != null) {
            // The signature is computed from failures, so we need to construct failures
            // that produce the expected signature
            return when (signature) {
                "timeout_health" ->
                    HealthCheckResult(
                        status = MonitorHealthStatus.FAIL,
                        checkedAt = currentTimeMs,
                        detailsSummary = "1/4 endpoints failed",
                        failures =
                            listOf(
                                EndpointCheckResult(
                                    endpoint = "/health",
                                    passed = false,
                                    httpCode = null,
                                    failureReason = failureReason,
                                ),
                            ),
                    )

                "401_v1_config" ->
                    HealthCheckResult(
                        status = MonitorHealthStatus.FAIL,
                        checkedAt = currentTimeMs,
                        detailsSummary = "1/4 endpoints failed",
                        failures =
                            listOf(
                                EndpointCheckResult(
                                    endpoint = "/v1/config",
                                    passed = false,
                                    httpCode = 401,
                                    failureReason = failureReason,
                                ),
                            ),
                    )

                else -> result
            }
        }

        return result
    }
}
