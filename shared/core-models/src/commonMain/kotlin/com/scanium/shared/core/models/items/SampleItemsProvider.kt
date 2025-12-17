package com.scanium.shared.core.models.items

import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.model.NormalizedRect

/**
 * Lightweight, deterministic sample data for iOS to validate the Shared.xcframework wiring.
 */
class SampleItemsProvider {
    fun sampleItems(): List<ScannedItem<String>> = listOf(
        ScannedItem(
            id = "shared-fashion-1",
            category = ItemCategory.FASHION,
            priceRange = 40.0 to 85.0,
            confidence = 0.86f,
            boundingBox = NormalizedRect(
                left = 0.1f,
                top = 0.2f,
                right = 0.4f,
                bottom = 0.6f,
            ),
            labelText = "Sneaker",
        ),
        ScannedItem(
            id = "shared-electronics-2",
            category = ItemCategory.ELECTRONICS,
            priceRange = 120.0 to 250.0,
            confidence = 0.73f,
            barcodeValue = "01234567890",
            boundingBox = NormalizedRect(
                left = 0.35f,
                top = 0.3f,
                right = 0.6f,
                bottom = 0.55f,
            ),
            labelText = "Vintage camera",
            fullImageUri = "https://example.com/camera.jpg",
            listingId = "EB123",
            listingUrl = "https://ebay.example.com/EB123",
            listingStatus = ItemListingStatus.LISTED_ACTIVE,
        ),
    )
}
