package com.scanium.app.aggregation

/**
 * Predefined aggregation configuration presets for different use cases.
 *
 * These presets provide starting points for common scenarios:
 * - BALANCED: Good default for most use cases
 * - STRICT: Fewer merges, higher precision
 * - LOOSE: More merges, higher recall
 * - REALTIME: Optimized for real-time scanning with camera movement
 */
object AggregationPresets {
    /**
     * Balanced configuration (recommended default).
     *
     * Provides good balance between merging similar items and keeping distinct items separate.
     * Works well for most scanning scenarios.
     */
    val BALANCED =
        AggregationConfig(
            similarityThreshold = 0.6f,
// 60% similarity required
            maxCenterDistanceRatio = 0.25f,
// Max 25% of frame diagonal
            maxSizeDifferenceRatio = 0.5f,
// Max 50% size difference
            categoryMatchRequired = true,
// Category must match
            labelMatchRequired = false,
// Labels optional
            weights =
                SimilarityWeights(
                    categoryWeight = 0.3f,
// 30% category
                    labelWeight = 0.25f,
// 25% label
                    sizeWeight = 0.20f,
// 20% size
                    distanceWeight = 0.25f,
// 25% distance
                ),
        )

    /**
     * Strict configuration for high precision.
     *
     * Use when you need to minimize false positives and prefer
     * having multiple entries for the same object over merging different objects.
     *
     * Good for:
     * - Dense scenes with many similar objects
     * - High-value items where accuracy is critical
     * - Quality control scenarios
     */
    val STRICT =
        AggregationConfig(
            similarityThreshold = 0.75f,
// 75% similarity required
            maxCenterDistanceRatio = 0.15f,
// Max 15% of frame diagonal
            maxSizeDifferenceRatio = 0.3f,
// Max 30% size difference
            categoryMatchRequired = true,
            labelMatchRequired = true,
// Labels required and must match
            weights =
                SimilarityWeights(
                    categoryWeight = 0.35f,
// Higher category weight
                    labelWeight = 0.35f,
// Higher label weight
                    sizeWeight = 0.15f,
                    distanceWeight = 0.15f,
                ),
        )

    /**
     * Loose configuration for high recall.
     *
     * Use when you want to aggressively merge similar items and
     * are willing to accept occasional false merges.
     *
     * Good for:
     * - Casual scanning scenarios
     * - Quick inventory counts
     * - When camera movement is significant
     */
    val LOOSE =
        AggregationConfig(
            similarityThreshold = 0.5f,
// 50% similarity required
            maxCenterDistanceRatio = 0.35f,
// Max 35% of frame diagonal
            maxSizeDifferenceRatio = 0.7f,
// Max 70% size difference
            categoryMatchRequired = true,
            labelMatchRequired = false,
            weights =
                SimilarityWeights(
                    categoryWeight = 0.35f,
// Higher category weight
                    labelWeight = 0.2f,
// Lower label weight
                    sizeWeight = 0.15f,
// Lower size weight
                    distanceWeight = 0.3f,
// Higher distance weight
                ),
        )

    /**
     * Real-time optimized configuration.
     *
     * Tuned for continuous scanning with camera movement.
     * Tolerant of bounding box jitter and trackingId changes.
     *
     * Good for:
     * - Long-press continuous scanning mode
     * - Handheld device scanning
     * - Dynamic scenes with camera movement
     */
    val REALTIME =
        AggregationConfig(
            similarityThreshold = 0.55f,
// 55% similarity (slightly loose)
            maxCenterDistanceRatio = 0.30f,
// Max 30% of frame diagonal (tolerant of movement)
            maxSizeDifferenceRatio = 0.6f,
// Max 60% size difference (tolerant of zoom)
            categoryMatchRequired = true,
            labelMatchRequired = false,
// Don't require labels (they may be inconsistent)
            weights =
                SimilarityWeights(
                    categoryWeight = 0.4f,
// Higher category weight (most stable)
                    labelWeight = 0.15f,
// Lower label weight (can change)
                    sizeWeight = 0.20f,
// Medium size weight
                    distanceWeight = 0.25f,
// Medium distance weight
                ),
        )

    /**
     * Label-focused configuration.
     *
     * Emphasizes label text similarity over spatial features.
     * Use when ML Kit provides reliable, consistent labels.
     *
     * Good for:
     * - Branded products with clear text
     * - Barcode/text scanning scenarios
     * - When position is less reliable
     */
    val LABEL_FOCUSED =
        AggregationConfig(
            similarityThreshold = 0.65f,
            maxCenterDistanceRatio = 0.4f,
// More tolerant of position
            maxSizeDifferenceRatio = 0.6f,
// More tolerant of size
            categoryMatchRequired = true,
            labelMatchRequired = true,
// Labels required
            weights =
                SimilarityWeights(
                    categoryWeight = 0.25f,
                    labelWeight = 0.45f,
// Much higher label weight
                    sizeWeight = 0.15f,
                    distanceWeight = 0.15f,
                ),
        )

    /**
     * Spatial-focused configuration.
     *
     * Emphasizes spatial proximity and size over labels.
     * Use when labels are unreliable or inconsistent.
     *
     * Good for:
     * - Generic objects without clear labels
     * - Items with similar appearance
     * - When spatial stability is high
     */
    val SPATIAL_FOCUSED =
        AggregationConfig(
            similarityThreshold = 0.6f,
            maxCenterDistanceRatio = 0.20f,
// Stricter distance requirement
            maxSizeDifferenceRatio = 0.4f,
// Stricter size requirement
            categoryMatchRequired = true,
            labelMatchRequired = false,
            weights =
                SimilarityWeights(
                    categoryWeight = 0.3f,
                    labelWeight = 0.1f,
// Much lower label weight
                    sizeWeight = 0.3f,
// Higher size weight
                    distanceWeight = 0.3f,
// Higher distance weight
                ),
        )

    /**
     * No aggregation configuration.
     *
     * DISABLES all merging - every capture creates a separate item.
     * Use when you want WYSIWYG behavior: each photo = unique item,
     * even if it's the same physical object from different angles.
     *
     * Good for:
     * - User wants explicit control over each capture
     * - Each photo should be a distinct item regardless of similarity
     * - No automatic merging or deduplication desired
     * - Multi-angle captures of same object should be separate items
     */
    val NO_AGGREGATION =
        AggregationConfig(
            similarityThreshold = 2.0f,
// Impossible threshold - nothing can reach 200% similarity
            maxCenterDistanceRatio = 0.0f,
// No distance tolerance
            maxSizeDifferenceRatio = 0.0f,
// No size tolerance
            categoryMatchRequired = true,
            labelMatchRequired = true,
            weights =
                SimilarityWeights(
                    categoryWeight = 0.25f,
                    labelWeight = 0.25f,
                    sizeWeight = 0.25f,
                    distanceWeight = 0.25f,
                ),
        )

    /**
     * Get configuration by name.
     *
     * Useful for runtime configuration selection.
     */
    fun getPreset(name: String): AggregationConfig {
        return when (name.uppercase()) {
            "BALANCED" -> BALANCED
            "STRICT" -> STRICT
            "LOOSE" -> LOOSE
            "REALTIME" -> REALTIME
            "LABEL_FOCUSED" -> LABEL_FOCUSED
            "SPATIAL_FOCUSED" -> SPATIAL_FOCUSED
            "NO_AGGREGATION" -> NO_AGGREGATION
            else -> BALANCED
        }
    }

    /**
     * Get all available preset names.
     */
    fun getPresetNames(): List<String> {
        return listOf(
            "BALANCED",
            "STRICT",
            "LOOSE",
            "REALTIME",
            "LABEL_FOCUSED",
            "SPATIAL_FOCUSED",
            "NO_AGGREGATION",
        )
    }
}
