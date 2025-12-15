package com.scanium.app.selling.util

import com.scanium.app.items.ScannedItem
import com.scanium.app.selling.domain.ListingCondition
import com.scanium.app.selling.domain.ListingDraft
import com.scanium.app.selling.domain.ListingImageSource

object ListingDraftMapper {
    fun fromScannedItem(item: ScannedItem): ListingDraft {
        val suggestedPrice = (item.priceRange.first + item.priceRange.second) / 2.0
        val baseTitle = item.labelText?.replaceFirstChar { it.uppercase() }
            ?: item.category.displayName
        val title = "Used $baseTitle"
        val description = "Listing created from Scanium scan in category ${item.category.displayName}."

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
