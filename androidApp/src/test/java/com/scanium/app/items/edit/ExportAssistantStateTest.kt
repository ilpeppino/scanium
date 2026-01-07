package com.scanium.app.items.edit

import com.google.common.truth.Truth.assertThat
import com.scanium.shared.core.models.assistant.ConfidenceTier
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for ExportAssistantState state machine.
 *
 * Tests verify:
 * - State transitions behave correctly
 * - State properties return expected values
 * - Success state contains all required fields
 */
@RunWith(RobolectricTestRunner::class)
class ExportAssistantStateTest {

    // ==================== Idle State ====================

    @Test
    fun `idle state isLoading returns false`() {
        val state = ExportAssistantState.Idle
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `idle state isError returns false`() {
        val state = ExportAssistantState.Idle
        assertThat(state.isError).isFalse()
    }

    @Test
    fun `idle state isSuccess returns false`() {
        val state = ExportAssistantState.Idle
        assertThat(state.isSuccess).isFalse()
    }

    // ==================== Generating State ====================

    @Test
    fun `generating state isLoading returns true`() {
        val state = ExportAssistantState.Generating()
        assertThat(state.isLoading).isTrue()
    }

    @Test
    fun `generating state has startedAt timestamp`() {
        val before = System.currentTimeMillis()
        val state = ExportAssistantState.Generating()
        val after = System.currentTimeMillis()

        assertThat(state.startedAt).isAtLeast(before)
        assertThat(state.startedAt).isAtMost(after)
    }

    @Test
    fun `generating state can have correlationId`() {
        val state = ExportAssistantState.Generating(correlationId = "test-123")
        assertThat(state.correlationId).isEqualTo("test-123")
    }

    // ==================== Success State ====================

    @Test
    fun `success state isSuccess returns true`() {
        val state = ExportAssistantState.Success(
            title = "Test Title",
            description = "Test Description",
            bullets = listOf("Bullet 1", "Bullet 2"),
            confidenceTier = ConfidenceTier.HIGH,
            fromCache = false,
            model = "gpt-4",
        )
        assertThat(state.isSuccess).isTrue()
    }

    @Test
    fun `success state isLoading returns false`() {
        val state = ExportAssistantState.Success(
            title = "Test",
            description = null,
            bullets = emptyList(),
            confidenceTier = null,
            fromCache = false,
            model = null,
        )
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `success state contains title and description`() {
        val state = ExportAssistantState.Success(
            title = "Vintage Nike Air Max 90",
            description = "Classic sneakers in excellent condition.",
            bullets = listOf("Original box included", "Size 10 US"),
            confidenceTier = ConfidenceTier.HIGH,
            fromCache = false,
            model = "gpt-4",
        )

        assertThat(state.title).isEqualTo("Vintage Nike Air Max 90")
        assertThat(state.description).isEqualTo("Classic sneakers in excellent condition.")
        assertThat(state.bullets).hasSize(2)
        assertThat(state.confidenceTier).isEqualTo(ConfidenceTier.HIGH)
    }

    @Test
    fun `success state can have null title`() {
        val state = ExportAssistantState.Success(
            title = null,
            description = "Description only",
            bullets = emptyList(),
            confidenceTier = null,
            fromCache = false,
            model = null,
        )
        assertThat(state.title).isNull()
        assertThat(state.description).isNotNull()
    }

    @Test
    fun `success state can have empty bullets`() {
        val state = ExportAssistantState.Success(
            title = "Title",
            description = "Description",
            bullets = emptyList(),
            confidenceTier = ConfidenceTier.MED,
            fromCache = false,
            model = null,
        )
        assertThat(state.bullets).isEmpty()
    }

    @Test
    fun `success state fromCache flag is preserved`() {
        val cachedState = ExportAssistantState.Success(
            title = "Cached Title",
            description = null,
            bullets = emptyList(),
            confidenceTier = null,
            fromCache = true,
            model = null,
        )
        assertThat(cachedState.fromCache).isTrue()

        val freshState = ExportAssistantState.Success(
            title = "Fresh Title",
            description = null,
            bullets = emptyList(),
            confidenceTier = null,
            fromCache = false,
            model = null,
        )
        assertThat(freshState.fromCache).isFalse()
    }

    // ==================== Error State ====================

    @Test
    fun `error state isError returns true`() {
        val state = ExportAssistantState.Error(
            message = "Network error",
            isRetryable = true,
        )
        assertThat(state.isError).isTrue()
    }

    @Test
    fun `error state isLoading returns false`() {
        val state = ExportAssistantState.Error(
            message = "Error message",
            isRetryable = false,
        )
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `error state contains message`() {
        val state = ExportAssistantState.Error(
            message = "Failed to generate: timeout",
            isRetryable = true,
        )
        assertThat(state.message).isEqualTo("Failed to generate: timeout")
    }

    @Test
    fun `error state retryable flag is preserved`() {
        val retryable = ExportAssistantState.Error(
            message = "Timeout",
            isRetryable = true,
        )
        assertThat(retryable.isRetryable).isTrue()

        val notRetryable = ExportAssistantState.Error(
            message = "Invalid item",
            isRetryable = false,
        )
        assertThat(notRetryable.isRetryable).isFalse()
    }

    @Test
    fun `error state has occurredAt timestamp`() {
        val before = System.currentTimeMillis()
        val state = ExportAssistantState.Error(message = "Error", isRetryable = true)
        val after = System.currentTimeMillis()

        assertThat(state.occurredAt).isAtLeast(before)
        assertThat(state.occurredAt).isAtMost(after)
    }

    // ==================== Confidence Tiers ====================

    @Test
    fun `all confidence tiers are handled`() {
        val highConfidence = ExportAssistantState.Success(
            title = "High",
            description = null,
            bullets = emptyList(),
            confidenceTier = ConfidenceTier.HIGH,
            fromCache = false,
            model = null,
        )
        assertThat(highConfidence.confidenceTier).isEqualTo(ConfidenceTier.HIGH)

        val medConfidence = ExportAssistantState.Success(
            title = "Med",
            description = null,
            bullets = emptyList(),
            confidenceTier = ConfidenceTier.MED,
            fromCache = false,
            model = null,
        )
        assertThat(medConfidence.confidenceTier).isEqualTo(ConfidenceTier.MED)

        val lowConfidence = ExportAssistantState.Success(
            title = "Low",
            description = null,
            bullets = emptyList(),
            confidenceTier = ConfidenceTier.LOW,
            fromCache = false,
            model = null,
        )
        assertThat(lowConfidence.confidenceTier).isEqualTo(ConfidenceTier.LOW)
    }
}
