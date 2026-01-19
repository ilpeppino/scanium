package com.scanium.app.selling.domain

import com.scanium.app.ItemCategory
import com.scanium.app.ScannedItem

data class Listing(
    val listingId: ListingId,
    val scannedItemId: String,
    val title: String,
    val description: String,
    val category: ItemCategory,
    val price: Double,
    val currency: String,
    val condition: ListingCondition,
    val image: ListingImage?,
    val status: ListingStatus,
    val externalUrl: String?,
)

data class ListingDraft(
    val scannedItemId: String,
    val originalItem: ScannedItem,
    val title: String,
    val description: String,
    val category: ItemCategory,
    val price: Double,
    val currency: String,
    val condition: ListingCondition = ListingCondition.USED,
    val imageSource: ListingImageSource = ListingImageSource.DETECTION_THUMBNAIL,
)
