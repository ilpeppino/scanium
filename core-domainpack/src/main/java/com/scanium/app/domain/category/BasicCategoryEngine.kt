package com.scanium.app.domain.category

import android.util.Log
import com.scanium.app.domain.config.DomainCategory
import com.scanium.app.domain.repository.DomainPackRepository

/**
 * Basic implementation of CategoryEngine using simple ML Kit label matching.
 *
 * **Current functionality (Track A):**
 * - Matches ML Kit labels against DomainCategory display names
 * - Simple string matching (case-insensitive, whitespace-tolerant)
 * - Priority-based tie-breaking when multiple categories match
 * - Filters out disabled categories
 *
 * **NOT implemented yet (future tracks):**
 * - CLIP prompt matching (requires on-device CLIP model)
 * - Cloud classifier integration (requires backend API)
 * - Hierarchical parent-child category matching
 * - Confidence-based multi-source fusion
 *
 * **Matching strategy:**
 * 1. Normalize ML Kit label (lowercase, trim)
 * 2. Check for substring match in category display name
 * 3. If multiple matches, prefer higher priority
 * 4. Return first match, or null if no matches
 *
 * Example:
 * ```
 * ML Kit label: "Home good"
 * Matches: furniture_sofa, furniture_chair, furniture_table, ...
 * Selected: furniture_sofa (highest priority among matches)
 * ```
 *
 * @param repository Domain Pack repository for accessing category taxonomy
 */
class BasicCategoryEngine(
    private val repository: DomainPackRepository,
) : CategoryEngine {
    override suspend fun selectCategory(input: CategorySelectionInput): DomainCategory? {
        val pack = repository.getActiveDomainPack()

        // Extract ML Kit label
        val mlKitLabel = input.mlKitLabel?.trim()?.lowercase()
        if (mlKitLabel.isNullOrEmpty()) {
            Log.d(TAG, "No ML Kit label provided, cannot select category")
            return null
        }

        Log.d(TAG, "Selecting category for ML Kit label: '$mlKitLabel'")

        // Get all enabled categories sorted by priority
        val candidates = pack.getCategoriesByPriority()

        // Find matching categories
        val matches =
            candidates.filter { category ->
                matchesCategory(mlKitLabel, category)
            }

        if (matches.isEmpty()) {
            Log.d(TAG, "No matching categories found for label '$mlKitLabel'")
            return null
        }

        // Return highest priority match (list is already sorted)
        val selected = matches.first()
        Log.d(
            TAG,
            "Selected category: ${selected.id} (${selected.displayName}) " +
                "with priority ${selected.priority}",
        )

        return selected
    }

    override suspend fun getCandidateCategories(input: CategorySelectionInput): List<Pair<DomainCategory, Float>> {
        val pack = repository.getActiveDomainPack()

        val mlKitLabel = input.mlKitLabel?.trim()?.lowercase()
        if (mlKitLabel.isNullOrEmpty()) {
            return emptyList()
        }

        // Get all enabled categories
        val candidates = pack.getCategoriesByPriority()

        // Find all matches with scores
        val matches =
            candidates.mapNotNull { category ->
                val score = calculateMatchScore(mlKitLabel, category)
                if (score > 0.0f) {
                    category to score
                } else {
                    null
                }
            }

        // Sort by score descending
        return matches.sortedByDescending { it.second }
    }

    /**
     * Check if an ML Kit label matches a domain category.
     *
     * Current strategy: Simple substring matching on display name and parent ID.
     * Future: Match against CLIP prompts, aliases.
     *
     * @param mlKitLabel Normalized ML Kit label (lowercase, trimmed)
     * @param category Domain category to check
     * @return true if the label matches this category
     */
    private fun matchesCategory(
        mlKitLabel: String,
        category: DomainCategory,
    ): Boolean {
        val displayName = category.displayName.lowercase()

        // Check for substring match in display name
        if (mlKitLabel.contains(displayName) || displayName.contains(mlKitLabel)) {
            return true
        }

        // Check for match in parent ID (e.g., "home" in "furniture")
        category.parentId?.let { parentId ->
            val parentLower = parentId.lowercase()
            if (mlKitLabel.contains(parentLower) || parentLower.contains(mlKitLabel)) {
                return true
            }
        }

        return false
    }

    /**
     * Calculate a match score between an ML Kit label and a domain category.
     *
     * Current strategy: Binary match (1.0 if matches, 0.0 otherwise).
     * Future: Semantic similarity using embeddings, fuzzy matching, etc.
     *
     * @param mlKitLabel Normalized ML Kit label (lowercase, trimmed)
     * @param category Domain category to score
     * @return Match score (0.0 - 1.0)
     */
    private fun calculateMatchScore(
        mlKitLabel: String,
        category: DomainCategory,
    ): Float {
        val displayName = category.displayName.lowercase()

        // Exact match
        if (mlKitLabel == displayName) {
            return 1.0f
        }

        // Substring match
        if (mlKitLabel.contains(displayName) || displayName.contains(mlKitLabel)) {
            return 0.7f
        }

        // Check against parent ID (for hierarchical matching)
        category.parentId?.let { parentId ->
            if (mlKitLabel.contains(parentId.lowercase())) {
                return 0.5f
            }
        }

        // No match
        return 0.0f
    }

    companion object {
        private const val TAG = "BasicCategoryEngine"
    }
}
