package com.scanium.app.items.export.bundle

import com.scanium.app.ItemCategory
import com.scanium.shared.core.models.items.ItemAttribute

/**
 * Flags indicating export bundle status.
 */
enum class ExportBundleFlag {
    /** Export fields are present and valid */
    READY,

    /** Missing AI-generated export fields - using fallback text */
    NEEDS_AI,

    /** No photos available for this item */
    NO_PHOTOS,

    /** Item has user-edited summary text (not AI-generated) */
    USER_EDITED,
}

/**
 * Represents a single item bundle ready for export.
 *
 * Contains all information needed to generate export files:
 * - Text content (title, description, bullets)
 * - Photo URIs for the item
 * - Metadata for structured export
 *
 * @param itemId Unique identifier for the item
 * @param title Marketplace-ready title (from export fields or fallback)
 * @param description Marketplace-ready description (from export fields or fallback)
 * @param bullets Optional bullet highlights for the listing
 * @param category Item category
 * @param attributes Map of attribute key to value for structured export
 * @param photoUris List of file paths to photos for this item
 * @param primaryPhotoUri The main/primary photo (thumbnail), if available
 * @param createdAt Timestamp when the item was first created
 * @param flags Set of flags indicating export status
 * @param confidenceTier AI confidence tier (HIGH/MED/LOW) if available
 * @param exportModel Model used for AI generation if available
 */
data class ExportItemBundle(
    val itemId: String,
    val title: String,
    val description: String,
    val bullets: List<String> = emptyList(),
    val category: ItemCategory,
    val attributes: Map<String, ItemAttribute> = emptyMap(),
    val photoUris: List<String> = emptyList(),
    val primaryPhotoUri: String? = null,
    val createdAt: Long,
    val flags: Set<ExportBundleFlag> = emptySet(),
    val confidenceTier: String? = null,
    val exportModel: String? = null,
) {
    /** True if this item needs AI processing to generate export fields */
    val needsAi: Boolean
        get() = ExportBundleFlag.NEEDS_AI in flags

    /** True if export fields are ready (AI-generated or user-edited) */
    val isReady: Boolean
        get() = ExportBundleFlag.READY in flags || ExportBundleFlag.USER_EDITED in flags

    /** True if this item has no photos */
    val hasNoPhotos: Boolean
        get() = ExportBundleFlag.NO_PHOTOS in flags

    /**
     * Number of photos available.
     * Note: primaryPhotoUri (if present) should be included as the first item in photoUris,
     * so we only count photoUris to avoid double-counting.
     */
    val photoCount: Int
        get() = photoUris.size
}

/**
 * Result of building export bundles from selected items.
 *
 * @param bundles List of export item bundles
 * @param totalItems Total number of items requested
 * @param readyCount Number of items with complete export fields
 * @param needsAiCount Number of items that need AI processing
 * @param noPhotosCount Number of items with no photos
 */
data class ExportBundleResult(
    val bundles: List<ExportItemBundle>,
    val totalItems: Int,
    val readyCount: Int,
    val needsAiCount: Int,
    val noPhotosCount: Int,
) {
    /** True if all items are ready for export */
    val allReady: Boolean
        get() = needsAiCount == 0

    /** True if any items need AI processing */
    val hasItemsNeedingAi: Boolean
        get() = needsAiCount > 0

    /** True if any items have no photos */
    val hasItemsWithoutPhotos: Boolean
        get() = noPhotosCount > 0
}

/**
 * Export configuration limits.
 */
data class ExportLimits(
    /** Maximum number of items per export session */
    val maxItems: Int = 50,
    /** Maximum number of photos per item */
    val maxPhotosPerItem: Int = 10,
    /** Maximum total photos across all items */
    val maxTotalPhotos: Int = 200,
)
