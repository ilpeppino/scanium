package com.scanium.app.ml

/**
 * State machine for vision enrichment progress.
 *
 * This tracks the lifecycle of extracting vision insights from a photo:
 * IDLE → ENRICHING → READY/FAILED
 *
 * Used to show clear progress UI without blocking the user.
 */
sealed class VisionEnrichmentState {
    /**
     * Initial state - no enrichment in progress.
     */
    data object Idle : VisionEnrichmentState()

    /**
     * Currently extracting info from photo.
     * @param itemId Item being enriched
     * @param startedAt Timestamp when enrichment started (for timeout detection)
     */
    data class Enriching(
        val itemId: String,
        val startedAt: Long = System.currentTimeMillis(),
    ) : VisionEnrichmentState()

    /**
     * Enrichment completed successfully.
     * @param itemId Item that was enriched
     * @param completedAt Timestamp when enrichment completed
     */
    data class Ready(
        val itemId: String,
        val completedAt: Long = System.currentTimeMillis(),
    ) : VisionEnrichmentState()

    /**
     * Enrichment failed.
     * @param itemId Item that failed
     * @param error User-friendly error message
     * @param isRetryable Whether the user can retry
     * @param failedAt Timestamp when enrichment failed
     */
    data class Failed(
        val itemId: String,
        val error: String,
        val isRetryable: Boolean = true,
        val failedAt: Long = System.currentTimeMillis(),
    ) : VisionEnrichmentState()

    // Helper properties
    val isEnriching: Boolean get() = this is Enriching
    val isReady: Boolean get() = this is Ready
    val isFailed: Boolean get() = this is Failed
    val isIdle: Boolean get() = this is Idle

    /**
     * Returns the item ID associated with this state, or null if idle.
     */
    fun itemIdOrNull(): String? =
        when (this) {
            is Idle -> null
            is Enriching -> itemId
            is Ready -> itemId
            is Failed -> itemId
        }

    /**
     * Returns a user-friendly status message for this state.
     */
    fun getStatusMessage(): String? =
        when (this) {
            is Idle -> null

            is Enriching -> "Extracting info from photo…"

            is Ready -> null

            // Don't show a message when ready
            is Failed -> error
        }
}

/**
 * Helper function to transition from one state to another.
 * Validates that the transition is valid according to the state machine rules.
 */
fun VisionEnrichmentState.transitionTo(newState: VisionEnrichmentState): VisionEnrichmentState {
    // Validate state transitions
    val isValid =
        when (this) {
            is VisionEnrichmentState.Idle -> {
                newState is VisionEnrichmentState.Enriching
            }

            is VisionEnrichmentState.Enriching -> {
                newState is VisionEnrichmentState.Ready || newState is VisionEnrichmentState.Failed ||
                    newState is VisionEnrichmentState.Idle
            }

            is VisionEnrichmentState.Ready -> {
                newState is VisionEnrichmentState.Idle || newState is VisionEnrichmentState.Enriching
            }

            is VisionEnrichmentState.Failed -> {
                newState is VisionEnrichmentState.Idle || newState is VisionEnrichmentState.Enriching
            }
        }

    if (!isValid) {
        android.util.Log.w("VisionEnrichmentState", "Invalid transition from $this to $newState")
    }

    return newState
}
