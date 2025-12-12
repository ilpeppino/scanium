package com.scanium.app.selling.data

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanium.app.items.ScannedItem
import com.scanium.app.ml.ItemCategory
import com.scanium.app.selling.util.ListingDraftMapper
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EbayMarketplaceServiceTest {

    @Test
    fun `createListingForDraft returns success with listing`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val api = MockEbayApi()
        val service = EbayMarketplaceService(context, api)
        val item = ScannedItem(
            id = "1",
            thumbnail = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888),
            category = ItemCategory.HOME_GOOD,
            priceRange = 20.0 to 40.0
        )

        val draft = ListingDraftMapper.fromScannedItem(item)

        val result = service.createListingForDraft(draft)

        assertThat(result).isInstanceOf(ListingCreationResult.Success::class.java)
        val listing = (result as ListingCreationResult.Success).listing
        assertThat(listing.listingId.value).startsWith("EBAY-MOCK")
        assertThat(listing.status).isEqualTo(com.scanium.app.selling.domain.ListingStatus.ACTIVE)
    }
}
