package com.scanium.shared.core.models.assistant

import com.scanium.shared.core.models.listing.DraftField
import com.scanium.shared.core.models.listing.DraftFieldKey
import com.scanium.shared.core.models.listing.DraftPhotoRef
import com.scanium.shared.core.models.listing.DraftProvenance
import com.scanium.shared.core.models.listing.DraftStatus
import com.scanium.shared.core.models.listing.ExportProfileId
import com.scanium.shared.core.models.listing.ExportProfiles
import com.scanium.shared.core.models.listing.ListingDraft
import com.scanium.shared.core.models.model.ImageRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class AssistantPromptBuilderTest {
    @Test
    fun buildRequest_isDeterministic() {
        val draft = sampleDraft()
        val snapshot = ItemContextSnapshotBuilder.fromDraft(draft)
        val profile = ExportProfiles.generic()
        val first =
            AssistantPromptBuilder.buildRequest(
                items = listOf(snapshot),
                userMessage = "Suggest a better title",
                exportProfile = profile,
                conversation = emptyList(),
            )
        val second =
            AssistantPromptBuilder.buildRequest(
                items = listOf(snapshot),
                userMessage = "Suggest a better title",
                exportProfile = profile,
                conversation = emptyList(),
            )

        assertEquals(first, second)
    }

    @Test
    fun snapshotBuilder_usesDraftFields() {
        val draft = sampleDraft()
        val snapshot = ItemContextSnapshotBuilder.fromDraft(draft)

        assertEquals("item-1", snapshot.itemId)
        assertEquals("Vintage Lamp", snapshot.title)
        assertEquals("Lighting", snapshot.category)
        assertEquals(0.9f, snapshot.confidence)
        assertEquals(2, snapshot.photosCount)
        assertEquals(42.0, snapshot.priceEstimate)
        assertEquals(ExportProfileId.GENERIC, snapshot.exportProfileId)
        assertEquals(
            listOf(
                ItemAttributeSnapshot("brand", "Acme", 0.7f, AttributeSource.DETECTED),
                ItemAttributeSnapshot("category", "Lighting", 0.9f, AttributeSource.DETECTED),
            ),
            snapshot.attributes,
        )
    }

    private fun sampleDraft(): ListingDraft {
        val photo =
            DraftPhotoRef(
                image =
                    ImageRef.Bytes(
                        bytes = byteArrayOf(1, 2, 3),
                        mimeType = "image/jpeg",
                        width = 10,
                        height = 10,
                    ),
            )

        return ListingDraft(
            id = "draft-1",
            itemId = "item-1",
            profile = ExportProfileId.GENERIC,
            title = DraftField("Vintage Lamp", confidence = 0.8f, source = DraftProvenance.USER_EDITED),
            description = DraftField("Nice lamp", confidence = 0.6f, source = DraftProvenance.DEFAULT),
            fields =
                mapOf(
                    DraftFieldKey.CATEGORY to DraftField("Lighting", 0.9f, DraftProvenance.DETECTED),
                    DraftFieldKey.BRAND to DraftField("Acme", 0.7f, DraftProvenance.DETECTED),
                ),
            price = DraftField(42.0, confidence = 0.5f, source = DraftProvenance.USER_EDITED),
            photos = listOf(photo, photo),
            status = DraftStatus.DRAFT,
            createdAt = 1L,
            updatedAt = 2L,
        )
    }

    @Test
    fun requestSerialization_withPricing() {
        val draft = sampleDraft()
        val snapshot = ItemContextSnapshotBuilder.fromDraft(draft)
        val profile = ExportProfiles.generic()
        val request = AssistantPromptBuilder.buildRequest(
            items = listOf(snapshot),
            userMessage = "Suggest a price",
            exportProfile = profile,
            conversation = emptyList(),
            assistantPrefs = AssistantPrefs(region = AssistantRegion.NL)
        )

        // Add pricing fields manually (since builder doesn't support them yet)
        val requestWithPricing = request.copy(
            includePricing = true,
            pricingPrefs = PricingPrefs(countryCode = "NL")
        )

        val json = Json { ignoreUnknownKeys = true }
        val jsonString = json.encodeToString(requestWithPricing)

        // Verify JSON contains pricing fields
        assertEquals(true, jsonString.contains("\"includePricing\":true"))
        assertEquals(true, jsonString.contains("\"pricingPrefs\""))
        assertEquals(true, jsonString.contains("\"countryCode\":\"NL\""))
    }

    @Test
    fun responseSerialization_withPricingInsights() {
        val json = Json { ignoreUnknownKeys = true }
        val responseJson = """
            {
                "reply": "Here's a suggested price",
                "pricingInsights": {
                    "status": "OK",
                    "countryCode": "NL",
                    "range": {
                        "low": 15.0,
                        "high": 35.0,
                        "currency": "EUR"
                    },
                    "results": [
                        {
                            "title": "Similar vintage lamp",
                            "price": {
                                "amount": 25.0,
                                "currency": "EUR"
                            },
                            "sourceMarketplaceId": "marktplaats",
                            "url": "https://example.com/listing1"
                        },
                        {
                            "title": "Another lamp",
                            "price": {
                                "amount": 20.0,
                                "currency": "EUR"
                            },
                            "sourceMarketplaceId": "2dehands",
                            "url": "https://example.com/listing2"
                        }
                    ]
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<AssistantResponse>(responseJson)

        assertEquals("Here's a suggested price", response.text)
        assertNotNull(response.pricingInsights)
        assertEquals("OK", response.pricingInsights!!.status)
        assertNotNull(response.pricingInsights!!.range)
        assertEquals(15.0, response.pricingInsights!!.range!!.low)
        assertEquals(35.0, response.pricingInsights!!.range!!.high)
        assertEquals("EUR", response.pricingInsights!!.range!!.currency)
        assertEquals(2, response.pricingInsights!!.results.size)
        assertEquals("Similar vintage lamp", response.pricingInsights!!.results[0].title)
        assertEquals(25.0, response.pricingInsights!!.results[0].price.amount)
        assertEquals("marktplaats", response.pricingInsights!!.results[0].sourceMarketplaceId)
        assertEquals("https://example.com/listing1", response.pricingInsights!!.results[0].url)
    }

    @Test
    fun responseSerialization_withoutPricingInsights() {
        val json = Json { ignoreUnknownKeys = true }
        val responseJson = """
            {
                "reply": "Here's a suggested title"
            }
        """.trimIndent()

        val response = json.decodeFromString<AssistantResponse>(responseJson)

        assertEquals("Here's a suggested title", response.text)
        assertNull(response.pricingInsights)
    }

    @Test
    fun responseSerialization_pricingNotSupported() {
        val json = Json { ignoreUnknownKeys = true }
        val responseJson = """
            {
                "reply": "Here's a suggested price",
                "pricingInsights": {
                    "status": "NOT_SUPPORTED",
                    "countryCode": "US",
                    "errorCode": "COUNTRY_NOT_SUPPORTED"
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<AssistantResponse>(responseJson)

        assertEquals("Here's a suggested price", response.text)
        assertNotNull(response.pricingInsights)
        assertEquals("NOT_SUPPORTED", response.pricingInsights!!.status)
        assertNull(response.pricingInsights!!.range)
        assertEquals("COUNTRY_NOT_SUPPORTED", response.pricingInsights!!.errorCode)
    }
}
