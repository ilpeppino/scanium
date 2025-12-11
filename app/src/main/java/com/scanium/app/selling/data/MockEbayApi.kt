package com.scanium.app.selling.data

import com.scanium.app.selling.domain.Listing
import com.scanium.app.selling.domain.ListingDraft
import com.scanium.app.selling.domain.ListingId
import com.scanium.app.selling.domain.ListingImage
import com.scanium.app.selling.domain.ListingStatus
import kotlin.random.Random

class MockEbayApi(private val failureRate: Double = 0.0) : EbayApi {
    private val listings = mutableMapOf<String, Listing>()

    override suspend fun createListing(draft: ListingDraft, image: ListingImage?): Listing {
        if (failureRate > 0 && Random.nextDouble() < failureRate) {
            throw IllegalStateException("Random mock failure")
        }

        val id = ListingId("EBAY-MOCK-${System.currentTimeMillis()}")
        val listing = Listing(
            listingId = id,
            scannedItemId = draft.scannedItemId,
            title = draft.title,
            description = draft.description,
            category = draft.category,
            price = draft.price,
            currency = draft.currency,
            condition = draft.condition,
            image = image,
            status = ListingStatus.ACTIVE,
            externalUrl = "https://mock.ebay.local/listing/${id.value}"
        )
        listings[id.value] = listing
        return listing
    }

    override suspend fun getListingStatus(id: ListingId): ListingStatus {
        return listings[id.value]?.status ?: ListingStatus.UNKNOWN
    }

    override suspend fun endListing(id: ListingId): ListingStatus {
        val existing = listings[id.value]
        val updated = existing?.copy(status = ListingStatus.ENDED)
        if (updated != null) {
            listings[id.value] = updated
        }
        return updated?.status ?: ListingStatus.UNKNOWN
    }
}
