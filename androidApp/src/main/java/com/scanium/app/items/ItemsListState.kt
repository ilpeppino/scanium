package com.scanium.app.items

/**
 * UI state holder for ItemsListContent.
 *
 * Data-only class with no logic, derived fields, or computed defaults.
 * All fields already exist in ItemsListScreen and are passed unchanged.
 */
data class ItemsListState(
    val selectedIds: Set<String>,
    val selectionMode: Boolean,
)
