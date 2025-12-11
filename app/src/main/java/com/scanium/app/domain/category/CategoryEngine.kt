package com.scanium.app.domain.category

import com.scanium.app.domain.config.DomainCategory

/**
 * Engine for selecting the appropriate domain category for a detected item.
 *
 * The CategoryEngine is the core component of the config-driven classification system.
 * It takes multiple inputs (ML Kit, CLIP, cloud) and selects the best matching
 * DomainCategory from the active Domain Pack.
 *
 * **Design principles:**
 * - **Multi-source**: Combines ML Kit, CLIP, and cloud classifications
 * - **Configurable**: Uses Domain Pack taxonomy (no hardcoded categories)
 * - **Extensible**: Easy to add new classification sources
 * - **Fallback chain**: Degrades gracefully when inputs are missing
 *
 * **Classification pipeline (planned):**
 * ```
 * 1. ML Kit (coarse categories) → Baseline classification
 * 2. On-device CLIP (fine-grained) → Improved accuracy
 * 3. Cloud classifier (highest accuracy) → Optional, for premium features
 * ```
 *
 * **Current implementation (Track A):**
 * - Only ML Kit label matching (simple baseline)
 * - CLIP and cloud support added in future tracks
 *
 * **Future enhancements:**
 * - Confidence-based selection (prefer higher confidence sources)
 * - Multi-category probabilities (return top-N candidates)
 * - Hierarchical matching (parent category fallback)
 * - Learning from user corrections
 *
 * Example:
 * ```
 * val input = CategorySelectionInput(mlKitLabel = "Home good")
 * val category = categoryEngine.selectCategory(input)
 * // Returns DomainCategory(id = "furniture_chair", ...)
 * ```
 */
interface CategoryEngine {
    /**
     * Select the best matching domain category for the given input.
     *
     * This method analyzes the input data and returns the most appropriate
     * DomainCategory from the active Domain Pack. If no suitable category
     * is found, returns null.
     *
     * **Selection strategy:**
     * - Prefer CLIP/cloud labels over ML Kit (when available, future)
     * - Use priority field to break ties
     * - Filter out disabled categories
     * - Match against category display names, prompts, and parent IDs
     *
     * @param input Classification input data (ML Kit, CLIP, cloud)
     * @return The best matching DomainCategory, or null if no match found
     */
    suspend fun selectCategory(input: CategorySelectionInput): DomainCategory?

    /**
     * Get all candidate categories with confidence scores.
     *
     * Returns a ranked list of potential matches with scores, useful for:
     * - Displaying multiple suggestions to users
     * - Debugging classification decisions
     * - A/B testing different thresholds
     *
     * @param input Classification input data
     * @return List of (DomainCategory, confidence score) pairs, sorted by score descending
     */
    suspend fun getCandidateCategories(
        input: CategorySelectionInput
    ): List<Pair<DomainCategory, Float>> {
        // Default implementation: return top match with score 1.0, or empty list
        val topMatch = selectCategory(input)
        return if (topMatch != null) {
            listOf(topMatch to 1.0f)
        } else {
            emptyList()
        }
    }
}
