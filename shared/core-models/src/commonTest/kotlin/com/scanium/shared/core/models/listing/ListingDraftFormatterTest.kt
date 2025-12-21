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
        val profile = ExportProfiles.generic()

        val first = ListingDraftFormatter.format(draft, profile)
        val second = ListingDraftFormatter.format(draft, profile)

        assertEquals(first.clipboardText, second.clipboardText)
        assertEquals(first.shareText, second.shareText)
    }

    @Test
    fun formatterOrdersFieldsDeterministically() {
        val item = sampleItem()
        val draft = ListingDraftBuilder.build(item)
        val profile = ExportProfileDefinition(
            id = ExportProfileId("TEST"),
            displayName = "Test",
            fieldOrdering = listOf(
                ExportFieldKey.PRICE,
                ExportFieldKey.CONDITION,
                ExportFieldKey.CATEGORY
            ),
            missingFieldPolicy = MissingFieldPolicy.SHOW_UNKNOWN
        )
        val formatted = ListingDraftFormatter.format(draft, profile).clipboardText

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
        val profile = ExportProfiles.generic()

        val formatted = ListingDraftFormatter.format(draft, profile).clipboardText
        assertTrue(formatted.contains("Title:"))
        assertTrue(formatted.contains("Price:"))
        assertTrue(formatted.contains("Condition:"))
        assertTrue(formatted.contains("Category:"))
        assertTrue(formatted.contains("Description:"))
    }

    @Test
    fun formatterRespectsTitleMaxLength() {
        val item = sampleItem()
        val draft = ListingDraftBuilder.build(item).copy(
            title = DraftField(value = "Very Long Listing Title That Should Be Trimmed")
        )
        val profile = ExportProfileDefinition(
            id = ExportProfileId("SHORT"),
            displayName = "Short",
            titleRules = ExportTitleRules(maxLen = 12)
        )

        val formatted = ListingDraftFormatter.format(draft, profile).clipboardText
        val titleStart = formatted.indexOf("Title:") + "Title:".length
        val titleLine = formatted.substring(titleStart).trim().lineSequence().first()

        assertTrue(titleLine.length <= 12)
    }

    @Test
    fun formatterReportsMissingRequiredFields() {
        val item = sampleItem()
        val draft = ListingDraftBuilder.build(item).copy(
            photos = emptyList(),
            price = DraftField(value = null)
        )
        val profile = ExportProfileDefinition(
            id = ExportProfileId("REQ"),
            displayName = "Required",
            requiredFields = listOf(
                ExportFieldKey.TITLE,
                ExportFieldKey.PRICE,
                ExportFieldKey.PHOTOS
            )
        )

        val missing = ListingDraftFormatter.missingRequiredFields(draft, profile)

        assertTrue(missing.contains(ExportFieldKey.PRICE))
        assertTrue(missing.contains(ExportFieldKey.PHOTOS))
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
