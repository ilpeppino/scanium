package com.scanium.app.selling.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanium.app.domain.DomainPackProvider
import com.scanium.app.ScannedItem
import com.scanium.core.models.image.Bytes
import com.scanium.core.models.ml.ItemCategory
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ListingDraftMapperTest {
    @After
    fun tearDown() {
        if (DomainPackProvider.isInitialized) {
            DomainPackProvider.reset()
        }
    }

    @Test
    fun `fromScannedItem maps core fields with labelText`() {
        val item =
            ScannedItem(
                id = "item-1",
                thumbnail = testImageRef(),
                category = ItemCategory.ELECTRONICS,
                priceRange = 10.0 to 30.0,
                labelText = "Laptop",
            )

        val draft = ListingDraftMapper.fromScannedItem(item)

        assertThat(draft.scannedItemId).isEqualTo("item-1")
        assertThat(draft.title).isEqualTo("Used Laptop")
        assertThat(draft.category).isEqualTo(ItemCategory.ELECTRONICS)
        assertThat(draft.price).isWithin(0.1).of(20.0)
        assertThat(draft.currency).isEqualTo("EUR")
    }

    @Test
    fun `fromScannedItem uses ListingTitleBuilder for title generation`() {
        val item =
            ScannedItem(
                id = "item-2",
                thumbnail = testImageRef(),
                category = ItemCategory.HOME_GOOD,
                priceRange = 15.0 to 45.0,
                labelText = "Decor / Wall Art",
            )

        val draft = ListingDraftMapper.fromScannedItem(item)

        // Verify title is generated correctly from domain pack label
        assertThat(draft.title).isEqualTo("Used Decor / Wall Art")
    }

    @Test
    fun `fromScannedItem falls back to category when labelText is null`() {
        val item =
            ScannedItem(
                id = "item-3",
                thumbnail = testImageRef(),
                category = ItemCategory.FASHION,
                priceRange = 20.0 to 60.0,
                labelText = null,
            )

        val draft = ListingDraftMapper.fromScannedItem(item)

        // Should use category display name as fallback
        assertThat(draft.title).isEqualTo("Used Fashion")
    }

    @Test
    fun `fromScannedItem calculates average price`() {
        val item =
            ScannedItem(
                id = "item-4",
                thumbnail = testImageRef(),
                category = ItemCategory.HOME_GOOD,
                priceRange = 100.0 to 200.0,
                labelText = "Chair",
            )

        val draft = ListingDraftMapper.fromScannedItem(item)

        assertThat(draft.price).isWithin(0.1).of(150.0)
    }

    @Test
    fun `fromScannedItem preserves original item reference`() {
        val item =
            ScannedItem(
                id = "item-5",
                thumbnail = testImageRef(),
                category = ItemCategory.ELECTRONICS,
                priceRange = 50.0 to 150.0,
                labelText = "Monitor",
            )

        val draft = ListingDraftMapper.fromScannedItem(item)

        assertThat(draft.originalItem).isSameInstanceAs(item)
    }

    @Test
    fun `listing title prefers domain category display name when available`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DomainPackProvider.reset()
        DomainPackProvider.initialize(context)

        val item =
            ScannedItem(
                id = "item-6",
                thumbnail = testImageRef(),
                category = ItemCategory.HOME_GOOD,
                priceRange = 40.0 to 60.0,
                labelText = "Generic Label",
                domainCategoryId = "furniture_sofa",
            )

        val draft = ListingDraftMapper.fromScannedItem(item)

        assertThat(draft.title).isEqualTo("Used Sofa")
    }

    private fun testImageRef(
        width: Int = 10,
        height: Int = 10,
    ): Bytes {
        val bytes = ByteArray((width * height).coerceAtLeast(1)) { 1 }
        return Bytes(
            bytes = bytes,
            mimeType = "image/jpeg",
            width = width,
            height = height,
        )
    }
}
