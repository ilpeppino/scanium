package com.scanium.app.items.export

import com.scanium.app.items.ScannedItem
import com.scanium.core.export.ExportItem
import com.scanium.core.export.ExportPayload
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

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

    return ExportItem(
        id = id,
        title = resolvedTitle,
        description = "Scanned item in $categoryLabel",
        category = categoryLabel,
        attributes = emptyMap(),
        priceMin = if (hasExplicitPrice) pricePair.first else null,
        priceMax = if (hasExplicitPrice) pricePair.second else null,
        imageRef = thumbnailRef ?: thumbnail,
    )
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
