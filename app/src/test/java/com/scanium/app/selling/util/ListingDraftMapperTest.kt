package com.scanium.app.selling.util

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import com.scanium.app.items.ScannedItem
import com.scanium.app.ml.ItemCategory
import org.junit.Test

class ListingDraftMapperTest {

    @Test
    fun `fromScannedItem maps core fields`() {
        val item = ScannedItem(
            id = "item-1",
            thumbnail = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888),
            category = ItemCategory.ELECTRONICS,
            priceRange = 10.0 to 30.0,
            labelText = "Laptop"
        )

        val draft = ListingDraftMapper.fromScannedItem(item)

        assertThat(draft.scannedItemId).isEqualTo("item-1")
        assertThat(draft.title).isEqualTo("Used Laptop")
        assertThat(draft.category).isEqualTo(ItemCategory.ELECTRONICS)
        assertThat(draft.price).isWithin(0.1).of(20.0)
        assertThat(draft.currency).isEqualTo("EUR")
    }
}
