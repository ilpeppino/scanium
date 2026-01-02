package com.scanium.shared.core.models.listing

import com.scanium.shared.core.models.items.ScannedItem
import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.model.ImageRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListingDraftBuilderTest {
    @Test
    fun `builder is deterministic for same input`() {
        val item = sampleItem()
        val first = ListingDraftBuilder.build(item)
        val second = ListingDraftBuilder.build(item)

        assertEquals(first, second)
    }

    @Test
    fun `completeness marks missing fields`() {
        val item = sampleItem().copy(thumbnail = null, thumbnailRef = null)
        val draft = ListingDraftBuilder.build(item)
        val missing = draft.completeness.missing

        assertTrue(missing.contains(DraftRequiredField.PHOTO))
        assertTrue(draft.completeness.score < 100)
    }

    @Test
    fun `missing title and price reduce completeness`() {
        val item = sampleItem()
        val draft =
            ListingDraftBuilder.build(item).copy(
                title = DraftField(value = "", confidence = 0f),
                price = DraftField(value = 0.0, confidence = 0f),
            ).recomputeCompleteness()

        assertTrue(draft.completeness.missing.contains(DraftRequiredField.TITLE))
        assertTrue(draft.completeness.missing.contains(DraftRequiredField.PRICE))
    }

    @Test
    fun `field serialization is stable`() {
        val fields =
            mapOf(
                DraftFieldKey.CONDITION to DraftField(value = "Used", confidence = 1f, source = DraftProvenance.DEFAULT),
                DraftFieldKey.CATEGORY to DraftField(value = "Fashion", confidence = 0.8f, source = DraftProvenance.DETECTED),
            )

        val json = DraftFieldsSerializer.toJson(fields)
        val restored = DraftFieldsSerializer.fromJson(json)

        assertEquals(fields, restored)
        assertTrue(json.startsWith("{"))
    }

    private fun sampleItem(): ScannedItem<Any?> {
        val image =
            ImageRef.Bytes(
                bytes = ByteArray(8) { it.toByte() },
                mimeType = "image/jpeg",
                width = 2,
                height = 2,
            )

        return ScannedItem(
            id = "item-1",
            category = ItemCategory.FASHION,
            priceRange = 10.0 to 30.0,
            confidence = 0.7f,
            labelText = "sneaker",
            thumbnail = image,
            thumbnailRef = image,
            timestamp = 1234L,
        )
    }
}
