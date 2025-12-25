package com.scanium.core.export

import com.scanium.core.models.image.ImageRef

/**
 * Portable export representation for a scanned item.
 */
data class ExportItem(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val attributes: Map<String, String> = emptyMap(),
    val priceMin: Double? = null,
    val priceMax: Double? = null,
    val imageRef: ImageRef? = null
)
