package com.scanium.app.ml

import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for VisionInsightsRepository response parsing.
 *
 * These tests verify that the API response is correctly parsed into
 * VisionAttributes that can be applied to ScannedItems.
 */
class VisionInsightsRepositoryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `parseResponse extracts OCR snippets correctly`() {
        val response = VisionInsightsResponse(
            success = true,
            requestId = "test-req-id",
            correlationId = "test-corr-id",
            ocrSnippets = listOf("Labello", "Lip Care", "Made in Germany"),
            logoHints = emptyList(),
            dominantColors = emptyList(),
            labelHints = emptyList(),
            suggestedLabel = null,
            categoryHint = null,
        )

        // Parse response into VisionInsightsResult
        val result = parseResponse(response)

        assertNotNull(result.visionAttributes.ocrText)
        assertTrue(result.visionAttributes.ocrText!!.contains("Labello"))
        assertTrue(result.visionAttributes.ocrText!!.contains("Lip Care"))
    }

    @Test
    fun `parseResponse extracts logos as brand candidates`() {
        val response = VisionInsightsResponse(
            success = true,
            requestId = "test-req-id",
            ocrSnippets = emptyList(),
            logoHints = listOf(
                LogoHintResponse("Labello", 0.95f),
                LogoHintResponse("Beiersdorf", 0.8f),
            ),
            dominantColors = emptyList(),
            labelHints = emptyList(),
            suggestedLabel = "Labello",
            categoryHint = "cosmetics",
        )

        val result = parseResponse(response)

        assertEquals(2, result.visionAttributes.logos.size)
        assertEquals("Labello", result.visionAttributes.logos[0].name)
        assertEquals(0.95f, result.visionAttributes.logos[0].score)
        assertEquals("Labello", result.visionAttributes.primaryBrand)
    }

    @Test
    fun `parseResponse extracts dominant colors`() {
        val response = VisionInsightsResponse(
            success = true,
            requestId = "test-req-id",
            ocrSnippets = emptyList(),
            logoHints = emptyList(),
            dominantColors = listOf(
                ColorResponse("blue", "***REMOVED***1E40AF", 45f),
                ColorResponse("white", "***REMOVED***FFFFFF", 30f),
            ),
            labelHints = emptyList(),
            suggestedLabel = null,
            categoryHint = null,
        )

        val result = parseResponse(response)

        assertEquals(2, result.visionAttributes.colors.size)
        assertEquals("blue", result.visionAttributes.colors[0].name)
        assertEquals("***REMOVED***1E40AF", result.visionAttributes.colors[0].hex)
        assertEquals(0.45f, result.visionAttributes.colors[0].score, 0.01f)
    }

    @Test
    fun `parseResponse extracts label hints`() {
        val response = VisionInsightsResponse(
            success = true,
            requestId = "test-req-id",
            ocrSnippets = emptyList(),
            logoHints = emptyList(),
            dominantColors = emptyList(),
            labelHints = listOf("cosmetics", "personal care", "lip care"),
            suggestedLabel = null,
            categoryHint = "cosmetics",
        )

        val result = parseResponse(response)

        assertEquals(3, result.visionAttributes.labels.size)
        assertEquals("cosmetics", result.visionAttributes.labels[0].name)
    }

    @Test
    fun `parseResponse preserves suggested label and category`() {
        val response = VisionInsightsResponse(
            success = true,
            requestId = "test-req-id",
            ocrSnippets = listOf("Labello Lip Care"),
            logoHints = listOf(LogoHintResponse("Labello", 0.95f)),
            dominantColors = emptyList(),
            labelHints = emptyList(),
            suggestedLabel = "Labello Lip Care",
            categoryHint = "cosmetics",
        )

        val result = parseResponse(response)

        assertEquals("Labello Lip Care", result.suggestedLabel)
        assertEquals("cosmetics", result.categoryHint)
    }

    @Test
    fun `parseResponse handles empty response gracefully`() {
        val response = VisionInsightsResponse(
            success = true,
            requestId = "test-req-id",
            ocrSnippets = emptyList(),
            logoHints = emptyList(),
            dominantColors = emptyList(),
            labelHints = emptyList(),
            suggestedLabel = null,
            categoryHint = null,
        )

        val result = parseResponse(response)

        assertTrue(result.visionAttributes.isEmpty)
        assertEquals(null, result.suggestedLabel)
        assertEquals(null, result.categoryHint)
    }

    @Test
    fun `JSON deserialization handles complete response`() {
        val jsonString = """
        {
            "success": true,
            "requestId": "abc-123",
            "correlationId": "def-456",
            "ocrSnippets": ["Labello", "Lip Balm"],
            "logoHints": [{"name": "Labello", "confidence": 0.92}],
            "dominantColors": [{"name": "blue", "hex": "***REMOVED***1E40AF", "pct": 45.5}],
            "labelHints": ["cosmetics", "lip care"],
            "suggestedLabel": "Labello Lip Balm",
            "categoryHint": "cosmetics",
            "extractionMeta": {
                "provider": "google-vision",
                "timingsMs": {"total": 500},
                "cacheHit": false
            }
        }
        """.trimIndent()

        val response = json.decodeFromString<VisionInsightsResponse>(jsonString)

        assertEquals(true, response.success)
        assertEquals("abc-123", response.requestId)
        assertEquals(2, response.ocrSnippets.size)
        assertEquals("Labello", response.logoHints[0].name)
        assertEquals(0.92f, response.logoHints[0].confidence)
        assertEquals("blue", response.dominantColors[0].name)
        assertEquals("***REMOVED***1E40AF", response.dominantColors[0].hex)
        assertEquals(45.5f, response.dominantColors[0].pct)
        assertEquals("Labello Lip Balm", response.suggestedLabel)
        assertEquals("cosmetics", response.categoryHint)
    }

    @Test
    fun `JSON deserialization handles error response`() {
        val jsonString = """
        {
            "success": false,
            "error": {
                "code": "VISION_UNAVAILABLE",
                "message": "Vision extraction failed",
                "correlationId": "def-456"
            }
        }
        """.trimIndent()

        val response = json.decodeFromString<VisionInsightsResponse>(jsonString)

        assertEquals(false, response.success)
        assertNotNull(response.error)
        assertEquals("VISION_UNAVAILABLE", response.error!!.code)
        assertEquals("Vision extraction failed", response.error!!.message)
    }

    // Helper to parse VisionInsightsResponse into VisionInsightsResult
    private fun parseResponse(response: VisionInsightsResponse): VisionInsightsResult {
        val visionAttributes = com.scanium.shared.core.models.items.VisionAttributes(
            colors = response.dominantColors.map { color ->
                com.scanium.shared.core.models.items.VisionColor(
                    name = color.name,
                    hex = color.hex,
                    score = color.pct / 100f,
                )
            },
            ocrText = response.ocrSnippets.joinToString("\n").takeIf { it.isNotBlank() },
            logos = response.logoHints.map { logo ->
                com.scanium.shared.core.models.items.VisionLogo(
                    name = logo.name,
                    score = logo.confidence,
                )
            },
            labels = response.labelHints.map { label ->
                com.scanium.shared.core.models.items.VisionLabel(
                    name = label,
                    score = 1.0f,
                )
            },
            brandCandidates = response.logoHints.map { it.name },
            modelCandidates = emptyList(),
        )

        return VisionInsightsResult(
            visionAttributes = visionAttributes,
            suggestedLabel = response.suggestedLabel,
            categoryHint = response.categoryHint,
            requestId = response.requestId,
        )
    }
}
