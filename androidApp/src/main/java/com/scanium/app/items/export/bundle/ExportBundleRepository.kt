package com.scanium.app.items.export.bundle

import android.content.Context
import android.util.Log
import com.scanium.app.ScannedItem
import com.scanium.app.items.photos.ItemPhotoManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for building export bundles from selected items.
 *
 * Responsibilities:
 * - Fetch selected items from ItemsStateManager
 * - Gather all photos linked to each item
 * - Choose best text source in priority order:
 *   1. exportTitle + exportDescription (AI-generated)
 *   2. attributesSummaryText (user-editable)
 *   3. Generated minimal text from attributes
 * - Build ExportItemBundle with appropriate flags
 */
@Singleton
class ExportBundleRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val itemPhotoManager: ItemPhotoManager,
    ) {
        companion object {
            private const val TAG = "ExportBundleRepo"
            private const val ITEM_PHOTOS_DIR = "item_photos"
        }

        private val photosBaseDir: File by lazy {
            File(context.filesDir, ITEM_PHOTOS_DIR)
        }

        /**
         * Build export bundles for the given item IDs.
         *
         * @param items List of items to export (from ItemsStateManager)
         * @param itemIds Set of item IDs to include (null = all items)
         * @param limits Export limits configuration
         * @return Result containing bundles and statistics
         * @throws ExportLimitExceededException if limits are exceeded
         */
        fun buildBundles(
            items: List<ScannedItem>,
            itemIds: Set<String>? = null,
            limits: ExportLimits = ExportLimits(),
        ): ExportBundleResult {
            val selectedItems =
                if (itemIds != null) {
                    items.filter { it.id in itemIds }
                } else {
                    items
                }

            // Check item limit
            if (selectedItems.size > limits.maxItems) {
                throw ExportLimitExceededException(
                    "Cannot export more than ${limits.maxItems} items at once. " +
                        "Selected: ${selectedItems.size}",
                )
            }

            var totalPhotos = 0
            val bundles = mutableListOf<ExportItemBundle>()
            var readyCount = 0
            var needsAiCount = 0
            var noPhotosCount = 0

            for (item in selectedItems) {
                // Gather photos for this item
                val photoUris = gatherPhotoUris(item, limits.maxPhotosPerItem)
                totalPhotos += photoUris.size

                // Check total photo limit
                if (totalPhotos > limits.maxTotalPhotos) {
                    throw ExportLimitExceededException(
                        "Cannot export more than ${limits.maxTotalPhotos} photos total. " +
                            "Would exceed limit with item ${item.id}",
                    )
                }

                // Build the bundle with appropriate text source
                val bundle = buildBundle(item, photoUris)
                bundles.add(bundle)

                // Track statistics
                when {
                    bundle.isReady -> readyCount++
                    bundle.needsAi -> needsAiCount++
                }
                if (bundle.hasNoPhotos) noPhotosCount++
            }

            Log.i(
                TAG,
                "Built ${bundles.size} bundles: $readyCount ready, " +
                    "$needsAiCount needs AI, $noPhotosCount without photos",
            )

            return ExportBundleResult(
                bundles = bundles,
                totalItems = bundles.size,
                readyCount = readyCount,
                needsAiCount = needsAiCount,
                noPhotosCount = noPhotosCount,
            )
        }

        /**
         * Gather all photo URIs for an item in deterministic order.
         *
         * Order priority:
         * 1. fullImagePath (primary/thumbnail) - always first if available
         * 2. Photos from item_photos directory (sorted by name)
         * 3. Additional photos from item model (in order)
         *
         * This ensures consistent ordering and that all photos are included exactly once.
         */
        private fun gatherPhotoUris(
            item: ScannedItem,
            maxPhotos: Int,
        ): List<String> {
            val uris = mutableListOf<String>()
            val seen = mutableSetOf<String>()

            // 1. Add fullImagePath FIRST (primary photo)
            item.fullImagePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    uris.add(path)
                    seen.add(path)
                }
            }

            // 2. Add photos from item_photos directory (sorted by name)
            val itemDir = File(photosBaseDir, item.id)
            if (itemDir.exists() && itemDir.isDirectory) {
                val photoFiles =
                    itemDir.listFiles { file ->
                        file.isFile && file.extension.lowercase() in listOf("jpg", "jpeg", "png")
                    }
                photoFiles?.sortedBy { it.name }?.forEach { file ->
                    val path = file.absolutePath
                    if (uris.size < maxPhotos && path !in seen) {
                        uris.add(path)
                        seen.add(path)
                    }
                }
            }

            // 3. Add additional photos from item model
            item.additionalPhotos.forEach { photo ->
                val photoUri = photo.uri
                if (uris.size < maxPhotos && photoUri != null) {
                    val file = File(photoUri)
                    if (file.exists() && photoUri !in seen) {
                        uris.add(photoUri)
                        seen.add(photoUri)
                    }
                }
            }

            return uris.take(maxPhotos)
        }

        /**
         * Build an ExportItemBundle from a ScannedItem.
         *
         * Text source priority:
         * 1. exportTitle + exportDescription (Phase 4 AI-generated)
         * 2. attributesSummaryText (user-edited or enrichment-generated)
         * 3. Generated minimal text from attributes/displayLabel
         */
        private fun buildBundle(
            item: ScannedItem,
            photoUris: List<String>,
        ): ExportItemBundle {
            val flags = mutableSetOf<ExportBundleFlag>()

            // Determine primary photo URI (from thumbnail if stored)
            val primaryPhotoUri = item.fullImagePath?.takeIf { File(it).exists() }

            // Track if no photos available
            if (photoUris.isEmpty() && primaryPhotoUri == null) {
                flags.add(ExportBundleFlag.NO_PHOTOS)
            }

            // Choose text source
            val (title, description, bullets) = chooseTextSource(item, flags)

            return ExportItemBundle(
                itemId = item.id,
                title = title,
                description = description,
                bullets = bullets,
                category = item.category,
                attributes = item.attributes,
                photoUris = photoUris,
                primaryPhotoUri = primaryPhotoUri,
                createdAt = item.timestamp,
                flags = flags,
                confidenceTier = item.exportConfidenceTier,
                exportModel = item.exportModel,
            )
        }

        /**
         * Choose the best text source for the export.
         *
         * @return Triple of (title, description, bullets)
         */
        private fun chooseTextSource(
            item: ScannedItem,
            flags: MutableSet<ExportBundleFlag>,
        ): Triple<String, String, List<String>> {
            // Priority 1: AI-generated export fields (Phase 4)
            val exportTitle = item.exportTitle
            val exportDescription = item.exportDescription
            if (!exportTitle.isNullOrBlank() && !exportDescription.isNullOrBlank()) {
                flags.add(ExportBundleFlag.READY)
                return Triple(
                    exportTitle,
                    exportDescription,
                    item.exportBullets,
                )
            }

            // Priority 2: User-edited summary text
            if (item.summaryTextUserEdited && item.attributesSummaryText.isNotBlank()) {
                flags.add(ExportBundleFlag.USER_EDITED)
                val (title, description) = parseSummaryText(item)
                return Triple(title, description, emptyList())
            }

            // Priority 3: Enrichment-generated summary text
            if (item.attributesSummaryText.isNotBlank()) {
                flags.add(ExportBundleFlag.NEEDS_AI)
                val (title, description) = parseSummaryText(item)
                return Triple(title, description, emptyList())
            }

            // Priority 4: Generate minimal text from attributes
            flags.add(ExportBundleFlag.NEEDS_AI)
            return generateMinimalText(item)
        }

        /**
         * Parse summary text into title and description.
         */
        private fun parseSummaryText(item: ScannedItem): Pair<String, String> {
            val summaryText = item.attributesSummaryText

            // Use displayLabel as title
            val title = item.displayLabel

            // Use summary text as description
            return Pair(title, summaryText)
        }

        /**
         * Generate minimal text from attributes when no other source is available.
         */
        private fun generateMinimalText(item: ScannedItem): Triple<String, String, List<String>> {
            val title = item.displayLabel

            // Build description from attributes
            val descriptionParts = mutableListOf<String>()

            // Add category
            descriptionParts.add("Category: ${item.category.displayName}")

            // Add key attributes
            val keyAttributes = listOf("brand", "color", "size", "condition", "material")
            for (key in keyAttributes) {
                item.attributes[key]?.let { attr ->
                    val displayKey = key.replaceFirstChar { it.uppercase() }
                    descriptionParts.add("$displayKey: ${attr.value}")
                }
            }

            // Add price if available
            if (item.formattedPriceRange.isNotBlank()) {
                descriptionParts.add("Price Range: ${item.formattedPriceRange}")
            }

            item.formattedUserPrice?.let { price ->
                descriptionParts.add("Asking Price: $price")
            }

            val description =
                if (descriptionParts.isNotEmpty()) {
                    descriptionParts.joinToString("\n")
                } else {
                    "No details available"
                }

            return Triple(title, description, emptyList())
        }
    }

/**
 * Exception thrown when export limits are exceeded.
 */
class ExportLimitExceededException(
    message: String,
) : Exception(message)
