package com.scanium.shared.core.models.items

import kotlinx.datetime.Clock

/**
 * Tracks the enrichment status across all three enrichment layers.
 *
 * The enrichment pipeline has three layers:
 * - Layer A (Local): Fast on-device extraction via ML Kit OCR + Palette colors (~100-200ms)
 * - Layer B (Cloud Vision): Cloud-based vision insights with logo/brand detection (~1-5s)
 * - Layer C (Full Enrichment): Complete backend enrichment with draft generation (~5-15s)
 *
 * @param layerA Status of Layer A (local) enrichment
 * @param layerB Status of Layer B (cloud vision) enrichment
 * @param layerC Status of Layer C (full enrichment) enrichment
 * @param lastUpdated Timestamp of last status change
 */
data class EnrichmentLayerStatus(
    val layerA: LayerState = LayerState.PENDING,
    val layerB: LayerState = LayerState.PENDING,
    val layerC: LayerState = LayerState.PENDING,
    val lastUpdated: Long = Clock.System.now().toEpochMilliseconds(),
) {
    /**
     * Whether any layer is currently in progress.
     */
    val isEnriching: Boolean
        get() = layerA == LayerState.IN_PROGRESS ||
            layerB == LayerState.IN_PROGRESS ||
            layerC == LayerState.IN_PROGRESS

    /**
     * Whether all layers have completed (successfully or with failure/skip).
     */
    val isComplete: Boolean
        get() = layerA.isTerminal && layerB.isTerminal && layerC.isTerminal

    /**
     * Whether at least one layer has completed successfully.
     */
    val hasAnyResults: Boolean
        get() = layerA == LayerState.COMPLETED ||
            layerB == LayerState.COMPLETED ||
            layerC == LayerState.COMPLETED

    /**
     * Human-readable summary of the enrichment status.
     */
    val statusSummary: String
        get() = when {
            isComplete && hasAnyResults -> "Enriched"
            isEnriching -> "Enriching..."
            layerA == LayerState.FAILED && layerB == LayerState.FAILED -> "Enrichment failed"
            else -> "Pending"
        }

    companion object {
        /**
         * Status representing no enrichment started yet.
         */
        val INITIAL = EnrichmentLayerStatus()

        /**
         * Status representing all layers completed successfully.
         */
        val ALL_COMPLETE = EnrichmentLayerStatus(
            layerA = LayerState.COMPLETED,
            layerB = LayerState.COMPLETED,
            layerC = LayerState.COMPLETED,
        )
    }
}

/**
 * State of a single enrichment layer.
 */
enum class LayerState {
    /**
     * Layer has not started yet.
     */
    PENDING,

    /**
     * Layer is currently processing.
     */
    IN_PROGRESS,

    /**
     * Layer completed successfully.
     */
    COMPLETED,

    /**
     * Layer failed to complete.
     */
    FAILED,

    /**
     * Layer was skipped (e.g., cloud layers when offline).
     */
    SKIPPED;

    /**
     * Whether this state is terminal (no further transitions expected).
     */
    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == SKIPPED
}
