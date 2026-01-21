package com.scanium.app.classification.hypothesis

import android.net.Uri

/**
 * Represents a single classification hypothesis from the reasoning layer.
 */
data class ClassificationHypothesis(
    val categoryId: String,
    val categoryName: String,
    val explanation: String,
    val confidence: Float, // 0.0-1.0
    val attributes: Map<String, String> = emptyMap()
)

/**
 * Multi-hypothesis classification result from backend.
 */
data class MultiHypothesisResult(
    val hypotheses: List<ClassificationHypothesis>,
    val globalConfidence: Float, // 0.0-1.0
    val needsRefinement: Boolean,
    val requestId: String
)

/**
 * UI state for hypothesis selection.
 */
sealed class HypothesisSelectionState {
    object Hidden : HypothesisSelectionState()

    data class Showing(
        val result: MultiHypothesisResult,
        val itemId: String,
        val thumbnailUri: Uri?
    ) : HypothesisSelectionState()
}
