package com.scanium.core.export

import kotlinx.datetime.Instant

/**
 * Export bundle for selected items.
 */
data class ExportPayload(
    val items: List<ExportItem>,
    val createdAt: Instant,
    val appVersion: String? = null
)
