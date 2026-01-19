package com.scanium.app.items.export

import com.scanium.app.items.ScannedItem
import com.scanium.core.export.ExportItem
import com.scanium.core.export.ExportPayload
import com.scanium.shared.core.models.items.ItemPhoto
import com.scanium.shared.core.models.model.ImageRef
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.File

/**
 * Convert a generic ScannedItem<Uri> to ExportItem.
 */
fun ScannedItem.toExportItem(): ExportItem {
    val resolvedTitle =
        labelText?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Item ${id.takeLast(6)}"

    val categoryLabel =
        domainCategoryId?.takeIf { it.isNotBlank() }
            ?: category.displayName

    val pricePair = estimatedPriceRange?.toPair() ?: priceRange
    val hasExplicitPrice = estimatedPriceRange != null || priceRange.first != 0.0 || priceRange.second != 0.0

    // Gather all image references in deterministic order
    val imageRefs =
        buildList {
            // 1. Primary thumbnail (if available)
            val primaryRef = thumbnailRef ?: thumbnail
            if (primaryRef != null) {
                add(primaryRef)
            }

            // 2. Additional photos
            additionalPhotos
                .sortedWith(compareBy<ItemPhoto> { it.capturedAt }.thenBy { it.id })
                .forEach { photo ->
                    resolvePhotoRef(photo)?.let { add(it) }
                }
        }

    return ExportItem(
        id = id,
        title = resolvedTitle,
        description = "Scanned item in $categoryLabel",
        category = categoryLabel,
        attributes = emptyMap(),
        priceMin = if (hasExplicitPrice) pricePair.first else null,
        priceMax = if (hasExplicitPrice) pricePair.second else null,
        imageRef = imageRefs.firstOrNull(),
        imageRefs = imageRefs,
    )
}

private fun resolvePhotoRef(photo: ItemPhoto): ImageRef? {
    photo.bytes?.let { bytes ->
        return ImageRef.Bytes(
            bytes = bytes,
            mimeType = photo.mimeType,
            width = photo.width,
            height = photo.height,
        )
    }

    val uri = photo.uri ?: return null
    val file = File(uri)
    if (!file.exists()) {
        return null
    }
    val mimeType =
        when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> photo.mimeType
        }
    return runCatching {
        ImageRef.Bytes(
            bytes = file.readBytes(),
            mimeType = mimeType,
            width = photo.width,
            height = photo.height,
        )
    }.getOrNull()
}

/**
 * Convert a list of generic ScannedItem<Uri> to ExportPayload.
 */
fun List<ScannedItem>.toExportPayload(
    createdAt: Instant = Clock.System.now(),
    appVersion: String? = null,
): ExportPayload =
    ExportPayload(
        items = map { it.toExportItem() },
        createdAt = createdAt,
        appVersion = appVersion,
    )
