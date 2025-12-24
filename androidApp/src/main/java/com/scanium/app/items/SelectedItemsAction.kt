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
    REVIEW_DRAFT(
        displayName = "Review draft",
        description = "Open a listing draft for the selected item"
    )
}
