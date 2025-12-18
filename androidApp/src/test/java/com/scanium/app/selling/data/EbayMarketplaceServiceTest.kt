package com.scanium.app.selling.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanium.app.items.ScannedItem
import com.scanium.app.selling.util.ListingDraftMapper
import com.scanium.core.models.image.ImageRef
import com.scanium.core.models.ml.ItemCategory
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
            thumbnail = testImageRef(20, 20),
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

    private fun testImageRef(width: Int, height: Int): ImageRef.Bytes {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val bytes = ByteArray((safeWidth * safeHeight).coerceAtLeast(1)) { 1 }
        return ImageRef.Bytes(
            bytes = bytes,
            mimeType = "image/jpeg",
            width = safeWidth,
            height = safeHeight
        )
    }
}
