package com.scanium.app.ml

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for VisionEnrichmentState transitions and helper methods.
 */
class VisionEnrichmentStateTest {

    @Test
    fun `initial state is Idle`() {
        val state: VisionEnrichmentState = VisionEnrichmentState.Idle

        assertThat(state.isIdle).isTrue()
        assertThat(state.isEnriching).isFalse()
        assertThat(state.isReady).isFalse()
        assertThat(state.isFailed).isFalse()
        assertThat(state.itemIdOrNull()).isNull()
        assertThat(state.getStatusMessage()).isNull()
    }

    @Test
    fun `Enriching state has correct properties`() {
        val state = VisionEnrichmentState.Enriching(itemId = "item-123")

        assertThat(state.isEnriching).isTrue()
        assertThat(state.isIdle).isFalse()
        assertThat(state.itemIdOrNull()).isEqualTo("item-123")
        assertThat(state.getStatusMessage()).isEqualTo("Extracting info from photo…")
    }

    @Test
    fun `Ready state has correct properties`() {
        val state = VisionEnrichmentState.Ready(itemId = "item-456")

        assertThat(state.isReady).isTrue()
        assertThat(state.isEnriching).isFalse()
        assertThat(state.itemIdOrNull()).isEqualTo("item-456")
        assertThat(state.getStatusMessage()).isNull()
    }

    @Test
    fun `Failed state has correct properties`() {
        val state = VisionEnrichmentState.Failed(
            itemId = "item-789",
            error = "Network error",
            isRetryable = true
        )

        assertThat(state.isFailed).isTrue()
        assertThat(state.isEnriching).isFalse()
        assertThat(state.itemIdOrNull()).isEqualTo("item-789")
        assertThat(state.getStatusMessage()).isEqualTo("Network error")
    }

    @Test
    fun `valid transition from Idle to Enriching`() {
        val idle: VisionEnrichmentState = VisionEnrichmentState.Idle
        val enriching = VisionEnrichmentState.Enriching("item-1")

        val newState = idle.transitionTo(enriching)

        assertThat(newState).isEqualTo(enriching)
        assertThat(newState.isEnriching).isTrue()
    }

    @Test
    fun `valid transition from Enriching to Ready`() {
        val enriching = VisionEnrichmentState.Enriching("item-1")
        val ready = VisionEnrichmentState.Ready("item-1")

        val newState = enriching.transitionTo(ready)

        assertThat(newState).isEqualTo(ready)
        assertThat(newState.isReady).isTrue()
    }

    @Test
    fun `valid transition from Enriching to Failed`() {
        val enriching = VisionEnrichmentState.Enriching("item-1")
        val failed = VisionEnrichmentState.Failed(
            itemId = "item-1",
            error = "Timeout",
            isRetryable = true
        )

        val newState = enriching.transitionTo(failed)

        assertThat(newState).isEqualTo(failed)
        assertThat(newState.isFailed).isTrue()
    }

    @Test
    fun `valid transition from Ready to Idle`() {
        val ready = VisionEnrichmentState.Ready("item-1")
        val idle = VisionEnrichmentState.Idle

        val newState = ready.transitionTo(idle)

        assertThat(newState).isEqualTo(idle)
        assertThat(newState.isIdle).isTrue()
    }

    @Test
    fun `valid transition from Failed to Enriching for retry`() {
        val failed = VisionEnrichmentState.Failed(
            itemId = "item-1",
            error = "Timeout",
            isRetryable = true
        )
        val enriching = VisionEnrichmentState.Enriching("item-1")

        val newState = failed.transitionTo(enriching)

        assertThat(newState).isEqualTo(enriching)
        assertThat(newState.isEnriching).isTrue()
    }

    @Test
    fun `timestamp is recorded for Enriching state`() {
        val before = System.currentTimeMillis()
        val state = VisionEnrichmentState.Enriching("item-1")
        val after = System.currentTimeMillis()

        assertThat(state.startedAt).isAtLeast(before)
        assertThat(state.startedAt).isAtMost(after)
    }

    @Test
    fun `timestamp is recorded for Ready state`() {
        val before = System.currentTimeMillis()
        val state = VisionEnrichmentState.Ready("item-1")
        val after = System.currentTimeMillis()

        assertThat(state.completedAt).isAtLeast(before)
        assertThat(state.completedAt).isAtMost(after)
    }

    @Test
    fun `timestamp is recorded for Failed state`() {
        val before = System.currentTimeMillis()
        val state = VisionEnrichmentState.Failed("item-1", "Error", isRetryable = true)
        val after = System.currentTimeMillis()

        assertThat(state.failedAt).isAtLeast(before)
        assertThat(state.failedAt).isAtMost(after)
    }

    @Test
    fun `retryable flag is preserved in Failed state`() {
        val retryable = VisionEnrichmentState.Failed("item-1", "Timeout", isRetryable = true)
        val nonRetryable = VisionEnrichmentState.Failed("item-1", "Auth failed", isRetryable = false)

        assertThat(retryable.isRetryable).isTrue()
        assertThat(nonRetryable.isRetryable).isFalse()
    }
}
