package com.scanium.app.selling.assistant

import com.scanium.app.copy.CustomerSafeCopyPolicy
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class AssistantDisplayModelMapperTest {

    @Test
    fun parseHeuristic_withTitleAndPrice_returnsModel() {
        val text = """
            Title: Designer Handbag
            Price: €150
            Condition: Like New
        """.trimIndent()

        val result = AssistantDisplayModelMapper.parse(text)

        assertNotNull(result)
        result!!.apply {
            assertEquals("Designer Handbag", title)
            assertEquals("€150", priceSuggestion)
            assertEquals("Like New", condition)
        }
    }

    @Test
    fun parseHeuristic_withBulletHighlights_extractsBullets() {
        val text = """
            Title: Running Shoes
            Price: €75

            - Excellent grip
            - Lightweight
            • Breathable mesh
            * Cushioned sole
        """.trimIndent()

        val result = AssistantDisplayModelMapper.parse(text)

        assertNotNull(result)
        result!!.apply {
            assertEquals("Running Shoes", title)
            assertEquals(
                listOf("Excellent grip", "Lightweight", "Breathable mesh", "Cushioned sole"),
                highlights
            )
        }
    }

    @Test
    fun parseHeuristic_withTags_parsesTags() {
        val text = """
            Title: Vintage Watch
            Tags: Luxury, Swiss Made, Collectible
        """.trimIndent()

        val result = AssistantDisplayModelMapper.parse(text)

        assertNotNull(result)
        result!!.apply {
            assertEquals("Vintage Watch", title)
            assertEquals(listOf("Luxury", "Swiss Made", "Collectible"), tags)
        }
    }

    @Test
    fun parseHeuristic_withBoldTitle_recognizesBoldFormat() {
        val text = """
            **Silk Scarf**
            Price: €40
        """.trimIndent()

        val result = AssistantDisplayModelMapper.parse(text)

        assertNotNull(result)
        result!!.apply {
            assertEquals("Silk Scarf", title)
            assertEquals("€40", priceSuggestion)
        }
    }

    @Test
    fun parseHeuristic_withDescription_extractsDescription() {
        val text = """
            Title: Ceramic Vase
            Description: Beautiful hand-painted ceramic vase from Portugal
        """.trimIndent()

        val result = AssistantDisplayModelMapper.parse(text)

        assertNotNull(result)
        result!!.apply {
            assertEquals("Ceramic Vase", title)
            assertEquals("Beautiful hand-painted ceramic vase from Portugal", description)
        }
    }

    @Test
    fun parseHeuristic_withoutStructure_returnsNull() {
        val plainText = "This is just a plain description of an item without any structure."
        val result = AssistantDisplayModelMapper.parse(plainText)
        assertNull(result)
    }

    @Test
    fun parseHeuristic_withOnlyTitle_returnsNull() {
        val text = "Title: Some Item"
        val result = AssistantDisplayModelMapper.parse(text)
        assertNull(result)
    }

    @Test
    fun sanitize_removesBannedTokens() {
        val model = AssistantDisplayModel(
            title = "Item unknown in condition",
            priceSuggestion = "Confidence: 95% - €50",
            condition = "might be generic",
            highlights = listOf("High quality item", "unknown material"),
            tags = listOf("collectible", "possibly vintage")
        )

        val sanitized = AssistantDisplayModelMapper.sanitize(model)

        // Verify banned tokens are removed
        assertTrue(!sanitized.title.lowercase().contains("unknown"))
        assertTrue(!sanitized.priceSuggestion!!.contains("%"))
        assertTrue(!sanitized.condition!!.lowercase().contains("generic"))
        assertTrue(!sanitized.highlights[0].lowercase().contains("unknown"))
    }

    @Test
    fun sanitize_removesEmptyHighlightsAndTags() {
        val model = AssistantDisplayModel(
            title = "Test Item",
            highlights = listOf("   ", "   unknown   ", "Valid highlight"),
            tags = listOf("generic", "   ", "valid-tag")
        )

        val sanitized = AssistantDisplayModelMapper.sanitize(model)

        // After sanitization, empty and banned-only entries should be filtered
        assertTrue(sanitized.highlights.isNotEmpty())
        assertTrue(sanitized.highlights.all { it.isNotBlank() })
        assertTrue(sanitized.tags.isNotEmpty())
        assertTrue(sanitized.tags.all { it.isNotBlank() })
    }

    @Test
    fun parse_then_sanitize_producesCleanModel() {
        val text = """
            Title: Vintage Item unknown
            Price: confidence 95% - €50
            - Generic design
            - Possibly authentic
        """.trimIndent()

        val parsed = AssistantDisplayModelMapper.parse(text)
        assertNotNull(parsed)

        val sanitized = AssistantDisplayModelMapper.sanitize(parsed!!)

        // Verify final model is clean
        assertTrue(!sanitized.title.lowercase().contains("unknown"))
        assertTrue(!sanitized.priceSuggestion!!.lowercase().contains("confidence"))
        assertTrue(!sanitized.highlights[0].lowercase().contains("generic"))
    }

    @Test
    fun parse_emptyResponse_returnsNull() {
        val result = AssistantDisplayModelMapper.parse("")
        assertNull(result)
    }

    @Test
    fun parse_onlyWhitespace_returnsNull() {
        val result = AssistantDisplayModelMapper.parse("   \n\n   \t  ")
        assertNull(result)
    }
}
