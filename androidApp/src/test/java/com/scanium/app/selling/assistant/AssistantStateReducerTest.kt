package com.scanium.app.selling.assistant

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for AssistantStateReducer.
 *
 * Part of ARCH-001: Tests for extracted pure state reduction functions.
 */
class AssistantStateReducerTest {

    // ==================== resolveMode tests ====================

    @Test
    fun `resolveMode returns OFFLINE when not online`() {
        val result = AssistantStateReducer.resolveMode(isOnline = false, failure = null)
        assertThat(result).isEqualTo(AssistantMode.OFFLINE)
    }

    @Test
    fun `resolveMode returns OFFLINE when offline even with failure`() {
        val failure = AssistantBackendFailure(
            type = AssistantBackendErrorType.NETWORK_TIMEOUT,
            category = AssistantBackendErrorCategory.TEMPORARY,
            retryable = true,
            message = "Timeout",
        )
        val result = AssistantStateReducer.resolveMode(isOnline = false, failure = failure)
        assertThat(result).isEqualTo(AssistantMode.OFFLINE)
    }

    @Test
    fun `resolveMode returns LIMITED when online with failure`() {
        val failure = AssistantBackendFailure(
            type = AssistantBackendErrorType.PROVIDER_UNAVAILABLE,
            category = AssistantBackendErrorCategory.TEMPORARY,
            retryable = true,
            message = "Service unavailable",
        )
        val result = AssistantStateReducer.resolveMode(isOnline = true, failure = failure)
        assertThat(result).isEqualTo(AssistantMode.LIMITED)
    }

    @Test
    fun `resolveMode returns ONLINE when online without failure`() {
        val result = AssistantStateReducer.resolveMode(isOnline = true, failure = null)
        assertThat(result).isEqualTo(AssistantMode.ONLINE)
    }

    // ==================== computeAvailability tests ====================

    @Test
    fun `computeAvailability returns Unavailable LOADING when isLoading true`() {
        val result = AssistantStateReducer.computeAvailability(
            isOnline = true,
            isLoading = true,
            failure = null,
        )
        assertThat(result).isInstanceOf(AssistantAvailability.Unavailable::class.java)
        assertThat((result as AssistantAvailability.Unavailable).reason).isEqualTo(UnavailableReason.LOADING)
        assertThat(result.canRetry).isFalse()
    }

    @Test
    fun `computeAvailability returns Unavailable OFFLINE when not online`() {
        val result = AssistantStateReducer.computeAvailability(
            isOnline = false,
            isLoading = false,
            failure = null,
        )
        assertThat(result).isInstanceOf(AssistantAvailability.Unavailable::class.java)
        assertThat((result as AssistantAvailability.Unavailable).reason).isEqualTo(UnavailableReason.OFFLINE)
        assertThat(result.canRetry).isTrue()
    }

    @Test
    fun `computeAvailability returns Unavailable UNAUTHORIZED for auth failures`() {
        val failure = AssistantBackendFailure(
            type = AssistantBackendErrorType.UNAUTHORIZED,
            category = AssistantBackendErrorCategory.POLICY,
            retryable = false,
            message = "Not authorized",
        )
        val result = AssistantStateReducer.computeAvailability(
            isOnline = true,
            isLoading = false,
            failure = failure,
        )
        assertThat(result).isInstanceOf(AssistantAvailability.Unavailable::class.java)
        assertThat((result as AssistantAvailability.Unavailable).reason).isEqualTo(UnavailableReason.UNAUTHORIZED)
        assertThat(result.canRetry).isFalse()
    }

    @Test
    fun `computeAvailability returns Unavailable RATE_LIMITED with retryAfter`() {
        val failure = AssistantBackendFailure(
            type = AssistantBackendErrorType.RATE_LIMITED,
            category = AssistantBackendErrorCategory.TEMPORARY,
            retryable = true,
            message = "Rate limited",
            retryAfterSeconds = 30,
        )
        val result = AssistantStateReducer.computeAvailability(
            isOnline = true,
            isLoading = false,
            failure = failure,
        )
        assertThat(result).isInstanceOf(AssistantAvailability.Unavailable::class.java)
        assertThat((result as AssistantAvailability.Unavailable).reason).isEqualTo(UnavailableReason.RATE_LIMITED)
        assertThat(result.canRetry).isTrue()
        assertThat(result.retryAfterSeconds).isEqualTo(30)
    }

    @Test
    fun `computeAvailability returns Unavailable BACKEND_ERROR for network failures`() {
        val failure = AssistantBackendFailure(
            type = AssistantBackendErrorType.NETWORK_TIMEOUT,
            category = AssistantBackendErrorCategory.TEMPORARY,
            retryable = true,
            message = "Network timeout",
        )
        val result = AssistantStateReducer.computeAvailability(
            isOnline = true,
            isLoading = false,
            failure = failure,
        )
        assertThat(result).isInstanceOf(AssistantAvailability.Unavailable::class.java)
        assertThat((result as AssistantAvailability.Unavailable).reason).isEqualTo(UnavailableReason.BACKEND_ERROR)
        assertThat(result.canRetry).isTrue()
    }

    @Test
    fun `computeAvailability returns Available when all conditions good`() {
        val result = AssistantStateReducer.computeAvailability(
            isOnline = true,
            isLoading = false,
            failure = null,
        )
        assertThat(result).isEqualTo(AssistantAvailability.Available)
    }

    // ==================== buildFallbackSnackbarMessage tests ====================

    @Test
    fun `buildFallbackSnackbarMessage handles auth required`() {
        val failure = AssistantBackendFailure(
            type = AssistantBackendErrorType.AUTH_REQUIRED,
            category = AssistantBackendErrorCategory.POLICY,
            retryable = false,
            message = "Auth required",
        )
        val message = AssistantStateReducer.buildFallbackSnackbarMessage(failure)
        assertThat(message).contains("Switched to Local Helper")
        assertThat(message).contains("Sign in required")
    }

    @Test
    fun `buildFallbackSnackbarMessage handles rate limited with retry hint`() {
        val failure = AssistantBackendFailure(
            type = AssistantBackendErrorType.RATE_LIMITED,
            category = AssistantBackendErrorCategory.TEMPORARY,
            retryable = true,
            message = "Rate limited",
            retryAfterSeconds = 60,
        )
        val message = AssistantStateReducer.buildFallbackSnackbarMessage(failure)
        assertThat(message).contains("Switched to Local Helper")
        assertThat(message).contains("wait 60s")
    }

    @Test
    fun `buildFallbackSnackbarMessage handles network timeout`() {
        val failure = AssistantBackendFailure(
            type = AssistantBackendErrorType.NETWORK_TIMEOUT,
            category = AssistantBackendErrorCategory.TEMPORARY,
            retryable = true,
            message = "Timeout",
        )
        val message = AssistantStateReducer.buildFallbackSnackbarMessage(failure)
        assertThat(message).contains("Switched to Local Helper")
        assertThat(message).contains("Check your connection")
    }

    // ==================== availabilityDebugString tests ====================

    @Test
    fun `availabilityDebugString formats Available`() {
        val result = AssistantStateReducer.availabilityDebugString(AssistantAvailability.Available)
        assertThat(result).isEqualTo("Available")
    }

    @Test
    fun `availabilityDebugString formats Checking`() {
        val result = AssistantStateReducer.availabilityDebugString(AssistantAvailability.Checking)
        assertThat(result).isEqualTo("Checking")
    }

    @Test
    fun `availabilityDebugString formats Unavailable with details`() {
        val unavailable = AssistantAvailability.Unavailable(
            reason = UnavailableReason.RATE_LIMITED,
            canRetry = true,
            retryAfterSeconds = 30,
        )
        val result = AssistantStateReducer.availabilityDebugString(unavailable)
        assertThat(result).contains("RATE_LIMITED")
        assertThat(result).contains("canRetry=true")
        assertThat(result).contains("retryAfter=30")
    }

    // ==================== getAlternativeKey tests ====================

    @Test
    fun `getAlternativeKey maps color to secondaryColor`() {
        assertThat(AssistantStateReducer.getAlternativeKey("color")).isEqualTo("secondaryColor")
        assertThat(AssistantStateReducer.getAlternativeKey("COLOR")).isEqualTo("secondaryColor")
        assertThat(AssistantStateReducer.getAlternativeKey("Color")).isEqualTo("secondaryColor")
    }

    @Test
    fun `getAlternativeKey maps brand to brand2`() {
        assertThat(AssistantStateReducer.getAlternativeKey("brand")).isEqualTo("brand2")
        assertThat(AssistantStateReducer.getAlternativeKey("BRAND")).isEqualTo("brand2")
    }

    @Test
    fun `getAlternativeKey maps model to model2`() {
        assertThat(AssistantStateReducer.getAlternativeKey("model")).isEqualTo("model2")
    }

    @Test
    fun `getAlternativeKey maps unknown keys to key2`() {
        assertThat(AssistantStateReducer.getAlternativeKey("size")).isEqualTo("size2")
        assertThat(AssistantStateReducer.getAlternativeKey("weight")).isEqualTo("weight2")
    }
}
