package com.scanium.app.items

/**
 * Represents the action to perform on selected items.
 */
enum class SelectedItemsAction(
    val displayName: String,
    val description: String
) {
    SELL_ON_EBAY(
        displayName = "Sell on eBay",
        description = "List items on eBay marketplace"
    ),
    SAVE_TO_DEVICE(
        displayName = "Save to device",
        description = "Save item snapshots to Gallery"
    )
}
