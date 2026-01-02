package com.scanium.domain.repository

import com.scanium.app.ml.ItemCategory
import com.scanium.shared.core.models.model.ImageRef

/**
 * Interface for item classification (on-device or cloud).
 *
 * Implementations:
 * - CloudClassifier: Uses backend API → Google Vision
 * - MlKitClassifier: Uses on-device ML Kit labels (fallback)
 * - NoopClassifier: Returns UNKNOWN (for testing/disabled state)
 *
 * Design:
 * - Platform-agnostic (no Android types in signature)
 * - Uses ImageRef (KMP-compatible) instead of Bitmap
 * - Returns Result<T> for explicit error handling
 *
 * Usage:
 * ```
 * val classifier: ItemClassifier = CloudClassifier(...)
 * val result = classifier.classifyItem(thumbnail, hint = "Home good")
 * when {
 *     result.isSuccess -> {
 *         val classification = result.getOrThrow()
 *         // Use classification.domainCategoryId, .attributes, .confidence
 *     }
 *     else -> {
 *         // Fallback to coarse label or UNKNOWN
 *     }
 * }
 * ```
 */
interface ItemClassifier {
    /**
     * Classify a single item from its cropped image.
     *
     * @param thumbnail Cropped item image (JPEG, max 200x200px recommended)
     * @param hint Optional coarse label from on-device detector (e.g., "Home good")
     * @param domainPackId Target domain pack for category mapping (e.g., "home_resale")
     * @return Result containing ClassificationResult or error
     *
     * Error cases:
     * - Network error (cloud classifier only)
     * - Timeout (cloud classifier only)
     * - Rate limit exceeded
     * - Invalid image format
     */
    suspend fun classifyItem(
        thumbnail: ImageRef,
        hint: String? = null,
        domainPackId: String = "home_resale",
    ): Result<ClassificationResult>

    /**
     * Check if classifier is currently available.
     * For cloud classifiers: checks network connectivity, backend health.
     * For on-device classifiers: always returns true.
     *
     * @return true if classifier can be used, false otherwise
     */
    suspend fun isAvailable(): Boolean = true
}

/**
 * Result of item classification.
 *
 * @property domainCategoryId Fine-grained category ID (e.g., "furniture_sofa")
 * @property attributes Extracted attributes (color, material, condition, etc.)
 * @property confidence Classification confidence (0.0 to 1.0)
 * @property source Where classification came from (CLOUD, ON_DEVICE, FALLBACK)
 * @property label Human-readable label (e.g., "Sofa")
 * @property itemCategory Coarse category enum (for backwards compatibility)
 * @property latencyMs Time taken to classify (milliseconds)
 * @property requestId Unique ID for debugging/auditing (cloud only)
 */
data class ClassificationResult(
    val domainCategoryId: String,
    val attributes: Map<String, String> = emptyMap(),
    val confidence: Float,
    val source: ClassificationSource,
    val label: String? = null,
    val itemCategory: ItemCategory = ItemCategory.UNKNOWN,
    val latencyMs: Long? = null,
    val requestId: String? = null,
) {
    init {
        require(confidence in 0f..1f) { "Confidence must be between 0 and 1" }
    }

    /**
     * Get specific attribute value.
     * Common attributes: brand, color, material, condition, size, model
     */
    fun getAttribute(key: String): String? = attributes[key]

    val isHighConfidence: Boolean get() = confidence >= 0.7f
    val isMediumConfidence: Boolean get() = confidence >= 0.4f
    val isLowConfidence: Boolean get() = confidence < 0.4f
}

/**
 * Source of classification result.
 */
enum class ClassificationSource {
    /**
     * Cloud-based classification (Google Vision via backend).
     * Highest accuracy, requires network.
     */
    CLOUD,

    /**
     * On-device classification (ML Kit labels).
     * Fallback when cloud unavailable, lower accuracy.
     */
    ON_DEVICE,

    /**
     * Fallback/default when no classification available.
     * Lowest accuracy, used when both cloud and on-device fail.
     */
    FALLBACK,
}
