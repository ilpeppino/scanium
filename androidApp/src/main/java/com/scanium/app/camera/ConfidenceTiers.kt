package com.scanium.app.camera

/**
 * Defines confidence tiers for bounding box visualization and filtering.
 *
 * This is used by:
 * 1. The overlay rendering pipeline to determine stroke widths (visual feedback)
 * 2. The developer "Aggregation accuracy" filter to show/hide detections by confidence
 *
 * The stroke width is interpolated linearly based on confidence:
 * - minBoxStrokeWidth (2dp) at 0% confidence
 * - maxBoxStrokeWidth (4dp) at 100% confidence
 *
 * The tiers here define discrete filter steps that align with the visual feedback:
 * - "All" shows all detections (no filtering)
 * - Higher tiers progressively filter out lower-confidence detections
 *
 * @see DetectionOverlay for stroke width calculation
 */
object ConfidenceTiers {
    /**
     * A single confidence tier definition.
     *
     * @param name Display name for the tier (e.g., "All", "Low+", "Medium+", "High only")
     * @param minConfidence Minimum confidence threshold for this tier (0.0f - 1.0f)
     * @param description Brief description for UI/docs
     */
    data class Tier(
        val name: String,
        val minConfidence: Float,
        val description: String,
    )

    /**
     * Ordered list of confidence tiers, from lowest to highest threshold.
     *
     * These tiers define the steps for the developer overlay filter slider:
     * - Step 0 (leftmost): Show all detections
     * - Higher steps: Progressively filter to higher confidence only
     * - Last step (rightmost): Show only highest confidence detections
     */
    val tiers: List<Tier> =
        listOf(
            Tier(
                name = "All",
                minConfidence = 0.0f,
                description = "Show all detected bboxes",
            ),
            Tier(
                name = "Low+",
                minConfidence = 0.25f,
                description = "Show low confidence and above (>=25%)",
            ),
            Tier(
                name = "Medium+",
                minConfidence = 0.50f,
                description = "Show medium confidence and above (>=50%)",
            ),
            Tier(
                name = "High only",
                minConfidence = 0.75f,
                description = "Show only high confidence detections (>=75%)",
            ),
        )

    /**
     * Number of discrete steps for the slider.
     */
    val stepCount: Int = tiers.size

    /**
     * Get the tier at a given step index.
     *
     * @param stepIndex Index of the tier (0 to stepCount-1)
     * @return The tier at that index, or the first tier if out of bounds
     */
    fun getTier(stepIndex: Int): Tier = tiers.getOrElse(stepIndex.coerceIn(0, tiers.lastIndex)) { tiers.first() }

    /**
     * Get the minimum confidence threshold for a given step index.
     *
     * @param stepIndex Index of the tier (0 to stepCount-1)
     * @return Minimum confidence threshold (0.0f - 1.0f)
     */
    fun getMinConfidence(stepIndex: Int): Float = getTier(stepIndex).minConfidence

    /**
     * Get the display label for a given step index.
     *
     * @param stepIndex Index of the tier (0 to stepCount-1)
     * @return Display name (e.g., "All", "Medium+")
     */
    fun getLabel(stepIndex: Int): String = getTier(stepIndex).name

    /**
     * Get a formatted display string showing the current filter state.
     *
     * @param stepIndex Index of the tier (0 to stepCount-1)
     * @return Formatted string like "Showing: Medium+ (>=50%)"
     */
    fun getDisplayText(stepIndex: Int): String {
        val tier = getTier(stepIndex)
        return if (tier.minConfidence > 0f) {
            "Showing: ${tier.name} (>=${(tier.minConfidence * 100).toInt()}%)"
        } else {
            "Showing: ${tier.name}"
        }
    }

    /**
     * Filter a list of detections based on the selected tier.
     *
     * @param detections List of overlay tracks to filter
     * @param stepIndex Index of the tier to use for filtering
     * @return Filtered list containing only detections at or above the tier's confidence threshold
     */
    fun filterDetections(
        detections: List<OverlayTrack>,
        stepIndex: Int,
    ): List<OverlayTrack> {
        val minConfidence = getMinConfidence(stepIndex)
        return if (minConfidence <= 0f) {
            detections // No filtering for "All" tier
        } else {
            detections.filter { it.confidence >= minConfidence }
        }
    }
}
