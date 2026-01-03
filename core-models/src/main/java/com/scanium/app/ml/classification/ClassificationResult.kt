package com.scanium.app.ml.classification

import com.scanium.app.ml.ItemCategory
import com.scanium.shared.core.models.items.ItemAttribute

/**
 * Result returned by an [ItemClassifier].
 *
 * Enhanced to support domain pack categories and cloud classification.
 *
 * @property label Human-readable label (e.g., "Sofa")
 * @property confidence Classification confidence (0.0 - 1.0)
 * @property category Coarse ItemCategory for backward compatibility
 * @property mode Classification mode used (ON_DEVICE or CLOUD)
 * @property domainCategoryId Optional fine-grained domain category ID (e.g., "furniture_sofa")
 * @property attributes Optional key-value attributes (e.g., color, material, condition) - static from domain pack
 * @property enrichedAttributes Enriched attributes from VisionExtractor (brand, model, color, etc.) with confidence
 * @property status Classification status (PENDING, SUCCESS, FAILED)
 * @property errorMessage Error message if status is FAILED
 * @property requestId Optional backend request ID for debugging
 */
data class ClassificationResult(
    val label: String?,
    val confidence: Float,
    val category: ItemCategory,
    val mode: ClassificationMode,
    val domainCategoryId: String? = null,
    val attributes: Map<String, String>? = null,
    val enrichedAttributes: Map<String, ItemAttribute> = emptyMap(),
    val status: ClassificationStatus = ClassificationStatus.SUCCESS,
    val errorMessage: String? = null,
    val requestId: String? = null,
)

/**
 * Classification request status.
 */
enum class ClassificationStatus {
    /** Classification in progress */
    PENDING,

    /** Classification completed successfully */
    SUCCESS,

    /** Classification failed (retryable or non-retryable) */
    FAILED,
}
