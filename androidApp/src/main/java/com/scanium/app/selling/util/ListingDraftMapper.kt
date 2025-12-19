package com.scanium.app.selling.util

import android.util.Log
import com.scanium.app.items.ScannedItem
import com.scanium.app.selling.domain.ListingCondition
import com.scanium.app.selling.domain.ListingDraft
import com.scanium.app.selling.domain.ListingImageSource

/**
 * Maps ScannedItems to ListingDrafts for eBay selling flow.
 *
 * Uses ListingTitleBuilder to generate accurate, item-specific listing titles.
 */
object ListingDraftMapper {
    private const val TAG = "ListingDraftMapper"

    fun fromScannedItem(item: ScannedItem): ListingDraft {
        val suggestedPrice = (item.priceRange.first + item.priceRange.second) / 2.0
        val title = ListingTitleBuilder.buildTitle(item)
        val description = "Listing created from Scanium scan in category ${item.category.displayName}."

        // Debug logging to verify title generation
        Log.d(TAG, "Creating listing draft for item ${item.id}:")
        Log.d(TAG, "  - labelText: ${item.labelText}")
        Log.d(TAG, "  - category: ${item.category.displayName}")
        Log.d(TAG, "  - generated title: $title")

        return ListingDraft(
            scannedItemId = item.id,
            originalItem = item,
            title = title,
            description = description,
            category = item.category,
            price = suggestedPrice,
            currency = "EUR",
            condition = ListingCondition.USED,
            imageSource = ListingImageSource.DETECTION_THUMBNAIL
        )
    }
}
