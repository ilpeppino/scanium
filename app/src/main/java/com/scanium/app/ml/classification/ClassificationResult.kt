package com.scanium.app.ml.classification

import com.scanium.app.ml.ItemCategory

/**
 * Result returned by an [ItemClassifier].
 */
data class ClassificationResult(
    val label: String?,
    val confidence: Float,
    val category: ItemCategory,
    val mode: ClassificationMode
)
