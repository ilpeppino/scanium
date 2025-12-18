package com.scanium.app.selling.data

import com.google.common.truth.Truth.assertThat
import com.scanium.app.items.ScannedItem
import com.scanium.app.selling.domain.ListingCondition
import com.scanium.app.selling.domain.ListingDraft
import com.scanium.app.selling.domain.ListingId
import com.scanium.app.selling.domain.ListingImage
import com.scanium.app.selling.domain.ListingImageSource
import com.scanium.core.models.ml.ItemCategory
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MockEbayApiTest {

    private val api = MockEbayApi()

    @Test
    fun `create listing returns active listing`() = runBlocking {
        val draft = ListingDraft(
            scannedItemId = "1",
            originalItem = ScannedItem(
                id = "1",
                category = ItemCategory.FASHION,
                priceRange = 10.0 to 20.0
            ),
            title = "Used item",
            description = "desc",
            category = ItemCategory.FASHION,
            price = 15.0,
            currency = "EUR",
            condition = ListingCondition.USED
        )
        val listing = api.createListing(draft, ListingImage(ListingImageSource.DETECTION_THUMBNAIL, "uri"))

        assertThat(listing.status).isEqualTo(com.scanium.app.selling.domain.ListingStatus.ACTIVE)
        assertThat(listing.externalUrl).contains(listing.listingId.value)
    }

    @Test
    fun `end listing transitions to ended`() = runBlocking {
        val draft = ListingDraft(
            scannedItemId = "1",
            originalItem = ScannedItem(
                id = "1",
                category = ItemCategory.FASHION,
                priceRange = 10.0 to 20.0
            ),
            title = "Used item",
            description = "desc",
            category = ItemCategory.FASHION,
            price = 15.0,
            currency = "EUR",
            condition = ListingCondition.USED
        )
        val listing = api.createListing(draft, ListingImage(ListingImageSource.DETECTION_THUMBNAIL, "uri"))

        val status = api.endListing(listing.listingId)

        assertThat(status).isEqualTo(com.scanium.app.selling.domain.ListingStatus.ENDED)
        assertThat(api.getListingStatus(ListingId(listing.listingId.value))).isEqualTo(com.scanium.app.selling.domain.ListingStatus.ENDED)
    }
}
