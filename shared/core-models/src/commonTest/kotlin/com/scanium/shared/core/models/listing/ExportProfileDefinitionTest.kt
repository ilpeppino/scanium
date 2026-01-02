package com.scanium.shared.core.models.listing

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportProfileDefinitionTest {
    @Test
    fun parsesProfileDefinitionFromJson() {
        val json = Json { ignoreUnknownKeys = true }
        val profileJson =
            """
            {
              "id": "GENERIC",
              "displayName": "Generic",
              "localeHint": "en_US",
              "currencyHint": "EUR",
              "titleRules": {
                "maxLen": 70,
                "includeBrandInTitle": true,
                "includeModelInTitle": false,
                "capitalization": "SENTENCE_CASE"
              },
              "descriptionRules": {
                "format": "BULLETS",
                "includeMeasurements": false,
                "includeConditionLine": true,
                "includeDisclaimerLine": true
              },
              "fieldOrdering": ["price", "condition", "category", "photos"],
              "requiredFields": ["title", "price", "photos"],
              "optionalFieldLabels": {
                "price": "Price",
                "condition": "Condition",
                "category": "Category",
                "photos": "Photos"
              },
              "missingFieldPolicy": "SHOW_UNKNOWN"
            }
            """.trimIndent()

        val parsed = json.decodeFromString<ExportProfileDefinition>(profileJson)

        assertEquals(ExportProfileId.GENERIC, parsed.id)
        assertEquals("Generic", parsed.displayName)
        assertEquals(70, parsed.titleRules.maxLen)
        assertEquals(DescriptionFormat.BULLETS, parsed.descriptionRules.format)
        assertTrue(parsed.fieldOrdering.contains(ExportFieldKey.PRICE))
        assertTrue(parsed.requiredFields.contains(ExportFieldKey.PHOTOS))
        assertEquals("Photos", parsed.optionalFieldLabels[ExportFieldKey.PHOTOS])
        assertEquals(MissingFieldPolicy.SHOW_UNKNOWN, parsed.missingFieldPolicy)
    }
}
