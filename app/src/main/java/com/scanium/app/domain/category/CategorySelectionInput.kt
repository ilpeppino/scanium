package com.scanium.app.domain.category

/**
 * Input data for category selection by the CategoryEngine.
 *
 * This data class encapsulates all information available for determining
 * the appropriate domain category for a detected item:
 * - ML Kit label (coarse category)
 * - CLIP candidate label (fine-grained, future)
 * - CLIP similarity score (confidence, future)
 * - Additional metadata (bounding box size, etc., future)
 *
 * Design principles:
 * - Extensible: Can add new fields without breaking existing consumers
 * - Nullable: Fields are optional; engine degrades gracefully
 * - Immutable: Thread-safe and easy to test
 *
 * Example usage:
 * ```
 * val input = CategorySelectionInput(
 *   mlKitLabel = "Home good",
 *   mlKitConfidence = 0.85f
 * )
 * val category = categoryEngine.selectCategory(input)
 * ```
 *
 * Future enhancements:
 * - Add `clipCandidateLabel` and `clipSimilarity` for on-device CLIP
 * - Add `cloudLabel` and `cloudConfidence` for cloud classification
 * - Add `boundingBoxArea` for size-based heuristics
 *
 * @property mlKitLabel Coarse category label from ML Kit (e.g., "Home good", "Electronics")
 * @property mlKitConfidence Confidence score from ML Kit (0.0 - 1.0)
 * @property clipCandidateLabel Fine-grained label from CLIP model (future, placeholder)
 * @property clipSimilarity Similarity score from CLIP (0.0 - 1.0, future, placeholder)
 */
data class CategorySelectionInput(
    val mlKitLabel: String? = null,
    val mlKitConfidence: Float? = null,
    val clipCandidateLabel: String? = null,
    val clipSimilarity: Float? = null
)
