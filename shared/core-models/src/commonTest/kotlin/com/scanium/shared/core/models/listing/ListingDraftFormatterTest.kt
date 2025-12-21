package com.scanium.shared.core.models.listing

import com.scanium.shared.core.models.items.ScannedItem
import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.model.ImageRef
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class ListingDraftFormatterTest {

    @Test
    fun formatterIsDeterministic() {
        val item = sampleItem()
        val draft = ListingDraftBuilder.build(item)

        val first = ListingDraftFormatter.format(draft)
        val second = ListingDraftFormatter.format(draft)

        assertEquals(first.clipboardText, second.clipboardText)
        assertEquals(first.shareText, second.shareText)
    }

    @Test
    fun formatterOrdersFieldsDeterministically() {
        val item = sampleItem()
        val draft = ListingDraftBuilder.build(item)
        val formatted = ListingDraftFormatter.format(draft).clipboardText

        val titleIndex = formatted.indexOf("Title:")
        val priceIndex = formatted.indexOf("Price:")
        val conditionIndex = formatted.indexOf("Condition:")
        val categoryIndex = formatted.indexOf("Category:")
        val descriptionIndex = formatted.indexOf("Description:")

        assertTrue(titleIndex >= 0)
        assertTrue(priceIndex > titleIndex)
        assertTrue(conditionIndex > priceIndex)
        assertTrue(categoryIndex > conditionIndex)
        assertTrue(descriptionIndex > categoryIndex)
    }

    @Test
    fun formatterHandlesMissingOptionalFields() {
        val item = sampleItem().copy(labelText = null)
        val draft = ListingDraftBuilder.build(item).copy(
            fields = mapOf(
                DraftFieldKey.CATEGORY to DraftField(
                    value = item.category.displayName,
                    confidence = item.confidence,
                    source = DraftProvenance.DETECTED
                ),
                DraftFieldKey.CONDITION to DraftField(
                    value = "Used",
                    confidence = 1f,
                    source = DraftProvenance.DEFAULT
                )
            )
        )

        val formatted = ListingDraftFormatter.format(draft).clipboardText
        assertTrue(formatted.contains("Title:"))
        assertTrue(formatted.contains("Price:"))
        assertTrue(formatted.contains("Condition:"))
        assertTrue(formatted.contains("Category:"))
        assertTrue(formatted.contains("Description:"))
    }

    private fun sampleItem(): ScannedItem<Nothing> {
        return ScannedItem(
            id = "item-1",
            thumbnail = ImageRef.Bytes(
                bytes = ByteArray(8) { it.toByte() },
                mimeType = "image/jpeg",
                width = 4,
                height = 2
            ),
            category = ItemCategory.HOME_GOOD,
            priceRange = 10.0 to 20.0,
            confidence = 0.8f,
            timestamp = 1000L,
            labelText = "Mug"
        )
    }
}
