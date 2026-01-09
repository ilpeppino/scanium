package com.scanium.app.items.edit

import com.google.common.truth.Truth.assertThat
import com.scanium.shared.core.models.assistant.ConfidenceTier
import org.junit.Test

/**
 * Tests for ExportAssistantState helper methods and properties.
 */
class ExportAssistantStateTest {

    @Test
    fun `Idle state has correct properties`() {
        val state: ExportAssistantState = ExportAssistantState.Idle

        assertThat(state.isLoading).isFalse()
        assertThat(state.isError).isFalse()
        assertThat(state.isSuccess).isFalse()
        assertThat(state.getStatusMessage()).isNull()
        assertThat(state.isLongRunning()).isFalse()
    }

    @Test
    fun `Generating state shows drafting message`() {
        val state = ExportAssistantState.Generating(correlationId = "test-123")

        assertThat(state.isLoading).isTrue()
        assertThat(state.isError).isFalse()
        assertThat(state.getStatusMessage()).isEqualTo("Drafting description…")
    }

    @Test
    fun `Generating state is not long-running initially`() {
        val state = ExportAssistantState.Generating()

        assertThat(state.isLongRunning()).isFalse()
    }

    @Test
    fun `Generating state becomes long-running after 10 seconds`() {
        val elevenSecondsAgo = System.currentTimeMillis() - 11_000
        val state = ExportAssistantState.Generating(startedAt = elevenSecondsAgo)

        assertThat(state.isLongRunning()).isTrue()
    }

    @Test
    fun `Success state does not show message`() {
        val state = ExportAssistantState.Success(
            title = "Test Title",
            description = "Test Description",
            bullets = listOf("Point 1", "Point 2"),
            confidenceTier = ConfidenceTier.HIGH,
            fromCache = false,
            model = "gpt-4o-mini"
        )

        assertThat(state.isSuccess).isTrue()
        assertThat(state.isLoading).isFalse()
        assertThat(state.isError).isFalse()
        assertThat(state.getStatusMessage()).isNull()
        assertThat(state.isLongRunning()).isFalse()
    }

    @Test
    fun `Error state shows error message`() {
        val state = ExportAssistantState.Error(
            message = "Network error",
            isRetryable = true
        )

        assertThat(state.isError).isTrue()
        assertThat(state.isLoading).isFalse()
        assertThat(state.getStatusMessage()).isEqualTo("Network error")
        assertThat(state.isLongRunning()).isFalse()
    }

    @Test
    fun `Error state preserves retryable flag`() {
        val retryable = ExportAssistantState.Error("Timeout", isRetryable = true)
        val nonRetryable = ExportAssistantState.Error("Auth failed", isRetryable = false)

        assertThat(retryable.isRetryable).isTrue()
        assertThat(nonRetryable.isRetryable).isFalse()
    }

    @Test
    fun `Generating state records timestamp`() {
        val before = System.currentTimeMillis()
        val state = ExportAssistantState.Generating()
        val after = System.currentTimeMillis()

        assertThat(state.startedAt).isAtLeast(before)
        assertThat(state.startedAt).isAtMost(after)
    }

    @Test
    fun `Success state records timestamp`() {
        val before = System.currentTimeMillis()
        val state = ExportAssistantState.Success(
            title = "Test",
            description = "Test",
            bullets = emptyList(),
            confidenceTier = null,
            fromCache = false,
            model = null
        )
        val after = System.currentTimeMillis()

        assertThat(state.generatedAt).isAtLeast(before)
        assertThat(state.generatedAt).isAtMost(after)
    }

    @Test
    fun `Error state records timestamp`() {
        val before = System.currentTimeMillis()
        val state = ExportAssistantState.Error("Error", isRetryable = true)
        val after = System.currentTimeMillis()

        assertThat(state.occurredAt).isAtLeast(before)
        assertThat(state.occurredAt).isAtMost(after)
    }

    @Test
    fun `Success state stores all export content`() {
        val state = ExportAssistantState.Success(
            title = "Nike Air Max Sneakers - Size 10",
            description = "Comfortable and stylish sneakers...",
            bullets = listOf("Brand: Nike", "Size: 10", "Color: Black"),
            confidenceTier = ConfidenceTier.HIGH,
            fromCache = false,
            model = "gpt-4o-mini"
        )

        assertThat(state.title).isEqualTo("Nike Air Max Sneakers - Size 10")
        assertThat(state.description).isEqualTo("Comfortable and stylish sneakers...")
        assertThat(state.bullets).hasSize(3)
        assertThat(state.bullets).contains("Brand: Nike")
        assertThat(state.confidenceTier).isEqualTo(ConfidenceTier.HIGH)
        assertThat(state.fromCache).isFalse()
        assertThat(state.model).isEqualTo("gpt-4o-mini")
    }

    @Test
    fun `Success state can have null title and description`() {
        val state = ExportAssistantState.Success(
            title = null,
            description = null,
            bullets = emptyList(),
            confidenceTier = null,
            fromCache = false,
            model = null
        )

        assertThat(state.title).isNull()
        assertThat(state.description).isNull()
        assertThat(state.bullets).isEmpty()
    }
}
