package com.scanium.shared.core.models.classification

import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.model.ImageRef

/**
 * Portable classifier contract. Implementations may be cloud-backed or on-device.
 */
interface Classifier {
    /**
     * Classify a cropped item thumbnail.
     *
     * @param thumbnail portable image reference (JPEG bytes recommended)
     * @param hint optional coarse label from on-device detector (e.g., "Home good")
     * @param domainPackId domain taxonomy to target (default: home_resale)
     */
    suspend fun classify(
        thumbnail: ImageRef,
        hint: String? = null,
        domainPackId: String = DEFAULT_DOMAIN_PACK_ID,
    ): ClassificationResult

    /**
     * Availability check (e.g., network, configuration). Cloud clients can
     * short-circuit when not configured; on-device classifiers can always return true.
     */
    suspend fun isAvailable(): Boolean = true
}

/**
 * Repository-style contract to allow swapping implementations or composing multiple classifiers.
 */
interface ClassificationRepository {
    suspend fun classify(
        thumbnail: ImageRef,
        hint: String? = null,
        mode: ClassificationMode = ClassificationMode.CLOUD,
        domainPackId: String = DEFAULT_DOMAIN_PACK_ID,
    ): ClassificationResult
}

enum class ClassificationMode {
    CLOUD,
    ON_DEVICE,
    DISABLED,
}

data class ClassificationResult(
    val domainCategoryId: String?,
    val confidence: Float,
    val source: ClassificationSource,
    val label: String? = null,
    val itemCategory: ItemCategory = ItemCategory.UNKNOWN,
    val attributes: Map<String, String> = emptyMap(),
    val requestId: String? = null,
    val latencyMs: Long? = null,
    val status: ClassificationStatus = ClassificationStatus.SUCCESS,
    val errorMessage: String? = null,
)

enum class ClassificationSource {
    CLOUD,
    ON_DEVICE,
    FALLBACK,
}

enum class ClassificationStatus {
    SUCCESS,
    FAILED,
    SKIPPED,
}

const val DEFAULT_DOMAIN_PACK_ID = "home_resale"
