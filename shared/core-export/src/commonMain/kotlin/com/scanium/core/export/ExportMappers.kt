package com.scanium.core.export

import com.scanium.core.models.items.ScannedItem
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

fun ScannedItem.toExportItem(): ExportItem {
    val resolvedTitle =
        enhancedLabelText?.takeIf { it.isNotBlank() }
            ?: labelText.takeIf { it.isNotBlank() }
            ?: "Item ${id.takeLast(6)}"

    val categoryLabel =
        domainCategoryId?.takeIf { it.isNotBlank() }
            ?: enhancedCategory?.displayName
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
        imageRef = thumbnail,
    )
}

fun List<ScannedItem>.toExportPayload(
    createdAt: Instant = Clock.System.now(),
    appVersion: String? = null,
): ExportPayload =
    ExportPayload(
        items = map { it.toExportItem() },
        createdAt = createdAt,
        appVersion = appVersion,
    )
