package com.scanium.app.enrichment

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for parsing enrichment API response models.
 * These tests verify that JSON responses from the backend are correctly parsed.
 */
@RunWith(RobolectricTestRunner::class)
class EnrichmentModelsParsingTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    @Test
    fun `parse EnrichSubmitResponse with success`() {
        val responseJson =
            """
            {
                "success": true,
                "requestId": "550e8400-e29b-41d4-a716-446655440000",
                "correlationId": "corr-123"
            }
            """.trimIndent()

        val response = json.decodeFromString<EnrichSubmitResponse>(responseJson)

        assertThat(response.success).isTrue()
        assertThat(response.requestId).isEqualTo("550e8400-e29b-41d4-a716-446655440000")
        assertThat(response.correlationId).isEqualTo("corr-123")
        assertThat(response.error).isNull()
    }

    @Test
    fun `parse EnrichSubmitResponse with error`() {
        val responseJson =
            """
            {
                "success": false,
                "error": {
                    "code": "UNAUTHORIZED",
                    "message": "Invalid API key",
                    "correlationId": "corr-456"
                }
            }
            """.trimIndent()

        val response = json.decodeFromString<EnrichSubmitResponse>(responseJson)

        assertThat(response.success).isFalse()
        assertThat(response.requestId).isNull()
        assertThat(response.error).isNotNull()
        assertThat(response.error!!.code).isEqualTo("UNAUTHORIZED")
        assertThat(response.error!!.message).isEqualTo("Invalid API key")
    }

    @Test
    fun `parse EnrichStatusResponse with vision facts`() {
        val responseJson =
            """
            {
                "success": true,
                "status": {
                    "requestId": "550e8400-e29b-41d4-a716-446655440000",
                    "correlationId": "corr-123",
                    "itemId": "item-001",
                    "stage": "VISION_DONE",
                    "visionFacts": {
                        "ocrSnippets": ["KLEENEX", "THE ORIGINAL"],
                        "logoHints": [
                            {"name": "Kleenex", "confidence": 0.95}
                        ],
                        "dominantColors": [
                            {"name": "blue", "hex": "***REMOVED***0066CC", "pct": 45.5},
                            {"name": "white", "hex": "***REMOVED***FFFFFF", "pct": 30.2}
                        ],
                        "labelHints": ["tissue box", "health product"]
                    },
                    "createdAt": 1704067200000,
                    "updatedAt": 1704067205000
                }
            }
            """.trimIndent()

        val response = json.decodeFromString<EnrichStatusResponse>(responseJson)

        assertThat(response.success).isTrue()
        assertThat(response.status).isNotNull()

        val status = response.status!!
        assertThat(status.stage).isEqualTo("VISION_DONE")
        assertThat(status.stageEnum).isEqualTo(EnrichmentStage.VISION_DONE)
        assertThat(status.isComplete).isFalse()

        val visionFacts = status.visionFacts!!
        assertThat(visionFacts.ocrSnippets).containsExactly("KLEENEX", "THE ORIGINAL")
        assertThat(visionFacts.logoHints).hasSize(1)
        assertThat(visionFacts.logoHints[0].name).isEqualTo("Kleenex")
        assertThat(visionFacts.dominantColors).hasSize(2)
        assertThat(visionFacts.dominantColors[0].name).isEqualTo("blue")
        assertThat(visionFacts.dominantColors[0].pct).isWithin(0.1f).of(45.5f)
    }

    @Test
    fun `parse EnrichStatusResponse with normalized attributes`() {
        val responseJson =
            """
            {
                "success": true,
                "status": {
                    "requestId": "550e8400-e29b-41d4-a716-446655440000",
                    "correlationId": "corr-123",
                    "itemId": "item-001",
                    "stage": "ATTRIBUTES_DONE",
                    "normalizedAttributes": [
                        {
                            "key": "brand",
                            "value": "Kleenex",
                            "confidence": "HIGH",
                            "source": "VISION_LOGO",
                            "evidence": "Logo detected with 95% confidence"
                        },
                        {
                            "key": "color",
                            "value": "blue",
                            "confidence": "MED",
                            "source": "VISION_COLOR"
                        },
                        {
                            "key": "product_type",
                            "value": "tissue box",
                            "confidence": "LOW",
                            "source": "VISION_LABEL"
                        }
                    ],
                    "createdAt": 1704067200000,
                    "updatedAt": 1704067210000
                }
            }
            """.trimIndent()

        val response = json.decodeFromString<EnrichStatusResponse>(responseJson)

        assertThat(response.success).isTrue()
        val status = response.status!!

        assertThat(status.stage).isEqualTo("ATTRIBUTES_DONE")
        assertThat(status.stageEnum).isEqualTo(EnrichmentStage.ATTRIBUTES_DONE)
        assertThat(status.isComplete).isFalse()

        val attrs = status.normalizedAttributes!!
        assertThat(attrs).hasSize(3)

        val brandAttr = attrs.find { it.key == "brand" }!!
        assertThat(brandAttr.value).isEqualTo("Kleenex")
        assertThat(brandAttr.confidence).isEqualTo("HIGH")
        assertThat(brandAttr.source).isEqualTo("VISION_LOGO")
        assertThat(brandAttr.evidence).isEqualTo("Logo detected with 95% confidence")

        val colorAttr = attrs.find { it.key == "color" }!!
        assertThat(colorAttr.value).isEqualTo("blue")
        assertThat(colorAttr.confidence).isEqualTo("MED")
        assertThat(colorAttr.evidence).isNull()
    }

    @Test
    fun `parse EnrichStatusResponse with draft`() {
        val responseJson =
            """
            {
                "success": true,
                "status": {
                    "requestId": "550e8400-e29b-41d4-a716-446655440000",
                    "correlationId": "corr-123",
                    "itemId": "item-001",
                    "stage": "DRAFT_DONE",
                    "draft": {
                        "title": "Kleenex Original Tissue Box - Blue",
                        "description": "Brand new Kleenex tissue box. The original soft tissues in a stylish blue box design.",
                        "missingFields": ["condition", "quantity"],
                        "confidence": "HIGH"
                    },
                    "timings": {
                        "vision": 2500,
                        "attributes": 150,
                        "draft": 3200,
                        "total": 5850
                    },
                    "createdAt": 1704067200000,
                    "updatedAt": 1704067215000
                }
            }
            """.trimIndent()

        val response = json.decodeFromString<EnrichStatusResponse>(responseJson)

        assertThat(response.success).isTrue()
        val status = response.status!!

        assertThat(status.stage).isEqualTo("DRAFT_DONE")
        assertThat(status.stageEnum).isEqualTo(EnrichmentStage.DRAFT_DONE)
        assertThat(status.isComplete).isTrue()
        assertThat(status.isSuccess).isTrue()
        assertThat(status.isFailed).isFalse()

        val draft = status.draft!!
        assertThat(draft.title).isEqualTo("Kleenex Original Tissue Box - Blue")
        assertThat(draft.description).contains("Brand new Kleenex")
        assertThat(draft.missingFields).containsExactly("condition", "quantity")
        assertThat(draft.confidence).isEqualTo("HIGH")

        val timings = status.timings!!
        assertThat(timings.vision).isEqualTo(2500L)
        assertThat(timings.attributes).isEqualTo(150L)
        assertThat(timings.draft).isEqualTo(3200L)
        assertThat(timings.total).isEqualTo(5850L)
    }

    @Test
    fun `parse EnrichStatusResponse with error`() {
        val responseJson =
            """
            {
                "success": true,
                "status": {
                    "requestId": "550e8400-e29b-41d4-a716-446655440000",
                    "correlationId": "corr-123",
                    "itemId": "item-001",
                    "stage": "FAILED",
                    "error": {
                        "code": "VISION_EXTRACTION_FAILED",
                        "message": "Google Vision API returned an error",
                        "stage": "VISION_STARTED",
                        "retryable": true
                    },
                    "createdAt": 1704067200000,
                    "updatedAt": 1704067205000
                }
            }
            """.trimIndent()

        val response = json.decodeFromString<EnrichStatusResponse>(responseJson)

        assertThat(response.success).isTrue()
        val status = response.status!!

        assertThat(status.stage).isEqualTo("FAILED")
        assertThat(status.stageEnum).isEqualTo(EnrichmentStage.FAILED)
        assertThat(status.isComplete).isTrue()
        assertThat(status.isSuccess).isFalse()
        assertThat(status.isFailed).isTrue()

        val error = status.error!!
        assertThat(error.code).isEqualTo("VISION_EXTRACTION_FAILED")
        assertThat(error.message).contains("Google Vision API")
        assertThat(error.stage).isEqualTo("VISION_STARTED")
        assertThat(error.retryable).isTrue()
    }

    @Test
    fun `EnrichmentStage enum properties`() {
        // QUEUED
        assertThat(EnrichmentStage.QUEUED.isComplete).isFalse()
        assertThat(EnrichmentStage.QUEUED.isInProgress).isTrue()
        assertThat(EnrichmentStage.QUEUED.hasVisionResults).isFalse()
        assertThat(EnrichmentStage.QUEUED.hasAttributeResults).isFalse()
        assertThat(EnrichmentStage.QUEUED.hasDraftResults).isFalse()

        // VISION_DONE
        assertThat(EnrichmentStage.VISION_DONE.isComplete).isFalse()
        assertThat(EnrichmentStage.VISION_DONE.isInProgress).isTrue()
        assertThat(EnrichmentStage.VISION_DONE.hasVisionResults).isTrue()
        assertThat(EnrichmentStage.VISION_DONE.hasAttributeResults).isFalse()

        // ATTRIBUTES_DONE
        assertThat(EnrichmentStage.ATTRIBUTES_DONE.hasVisionResults).isTrue()
        assertThat(EnrichmentStage.ATTRIBUTES_DONE.hasAttributeResults).isTrue()
        assertThat(EnrichmentStage.ATTRIBUTES_DONE.hasDraftResults).isFalse()

        // DRAFT_DONE
        assertThat(EnrichmentStage.DRAFT_DONE.isComplete).isTrue()
        assertThat(EnrichmentStage.DRAFT_DONE.isInProgress).isFalse()
        assertThat(EnrichmentStage.DRAFT_DONE.hasVisionResults).isTrue()
        assertThat(EnrichmentStage.DRAFT_DONE.hasAttributeResults).isTrue()
        assertThat(EnrichmentStage.DRAFT_DONE.hasDraftResults).isTrue()

        // FAILED
        assertThat(EnrichmentStage.FAILED.isComplete).isTrue()
        assertThat(EnrichmentStage.FAILED.isInProgress).isFalse()
    }

    @Test
    fun `parse status with unknown stage falls back to FAILED`() {
        val responseJson =
            """
            {
                "success": true,
                "status": {
                    "requestId": "550e8400-e29b-41d4-a716-446655440000",
                    "correlationId": "corr-123",
                    "itemId": "item-001",
                    "stage": "UNKNOWN_STAGE",
                    "createdAt": 1704067200000,
                    "updatedAt": 1704067205000
                }
            }
            """.trimIndent()

        val response = json.decodeFromString<EnrichStatusResponse>(responseJson)
        val status = response.status!!

        assertThat(status.stage).isEqualTo("UNKNOWN_STAGE")
        assertThat(status.stageEnum).isEqualTo(EnrichmentStage.FAILED)
    }

    @Test
    fun `parse empty vision facts and attributes`() {
        val responseJson =
            """
            {
                "success": true,
                "status": {
                    "requestId": "550e8400-e29b-41d4-a716-446655440000",
                    "correlationId": "corr-123",
                    "itemId": "item-001",
                    "stage": "VISION_DONE",
                    "visionFacts": {
                        "ocrSnippets": [],
                        "logoHints": [],
                        "dominantColors": [],
                        "labelHints": []
                    },
                    "createdAt": 1704067200000,
                    "updatedAt": 1704067205000
                }
            }
            """.trimIndent()

        val response = json.decodeFromString<EnrichStatusResponse>(responseJson)
        val visionFacts = response.status!!.visionFacts!!

        assertThat(visionFacts.ocrSnippets).isEmpty()
        assertThat(visionFacts.logoHints).isEmpty()
        assertThat(visionFacts.dominantColors).isEmpty()
        assertThat(visionFacts.labelHints).isEmpty()
    }

    // ==================== Phase 2: Structured Attributes Tests ====================

    @Test
    fun `parse EnrichStatusResponse with structured attributes and summary text`() {
        val responseJson =
            """
            {
                "success": true,
                "status": {
                    "requestId": "550e8400-e29b-41d4-a716-446655440000",
                    "correlationId": "corr-123",
                    "itemId": "item-001",
                    "stage": "ATTRIBUTES_DONE",
                    "normalizedAttributes": [
                        {"key": "brand", "value": "Kleenex", "confidence": "HIGH", "source": "VISION_LOGO"}
                    ],
                    "attributesStructured": [
                        {
                            "key": "brand",
                            "value": "Kleenex",
                            "source": "DETECTED",
                            "confidence": "HIGH",
                            "evidence": [
                                {"type": "LOGO", "rawValue": "Kleenex", "score": 0.95}
                            ],
                            "updatedAt": 1704067210000
                        },
                        {
                            "key": "color",
                            "value": "blue",
                            "source": "DETECTED",
                            "confidence": "MED",
                            "evidence": [
                                {"type": "COLOR", "rawValue": "blue (45%)", "score": 0.45}
                            ],
                            "updatedAt": 1704067210000
                        },
                        {
                            "key": "product_type",
                            "value": "tissue box",
                            "source": "DETECTED",
                            "confidence": "LOW",
                            "updatedAt": 1704067210000
                        }
                    ],
                    "summaryText": "Brand: Kleenex\nColor: blue\nProduct Type: tissue box",
                    "suggestedAdditions": [],
                    "createdAt": 1704067200000,
                    "updatedAt": 1704067210000
                }
            }
            """.trimIndent()

        val response = json.decodeFromString<EnrichStatusResponse>(responseJson)
        assertThat(response.success).isTrue()

        val status = response.status!!
        assertThat(status.stage).isEqualTo("ATTRIBUTES_DONE")

        // Verify structured attributes
        val structuredAttrs = status.attributesStructured!!
        assertThat(structuredAttrs).hasSize(3)

        val brandAttr = structuredAttrs.find { it.key == "brand" }!!
        assertThat(brandAttr.value).isEqualTo("Kleenex")
        assertThat(brandAttr.source).isEqualTo("DETECTED")
        assertThat(brandAttr.confidence).isEqualTo("HIGH")
        assertThat(brandAttr.isDetected).isTrue()
        assertThat(brandAttr.isUserProvided).isFalse()
        assertThat(brandAttr.confidenceLevel).isEqualTo(EnrichmentConfidence.HIGH)

        // Verify evidence
        val evidence = brandAttr.evidence!!
        assertThat(evidence).hasSize(1)
        assertThat(evidence[0].type).isEqualTo("LOGO")
        assertThat(evidence[0].rawValue).isEqualTo("Kleenex")
        assertThat(evidence[0].score).isWithin(0.01f).of(0.95f)

        val colorAttr = structuredAttrs.find { it.key == "color" }!!
        assertThat(colorAttr.confidenceLevel).isEqualTo(EnrichmentConfidence.MED)

        val productAttr = structuredAttrs.find { it.key == "product_type" }!!
        assertThat(productAttr.confidence).isEqualTo("LOW")
        assertThat(productAttr.confidenceLevel).isEqualTo(EnrichmentConfidence.LOW)
        assertThat(productAttr.evidence).isNull()

        // Verify summary text
        assertThat(status.summaryText).isEqualTo("Brand: Kleenex\nColor: blue\nProduct Type: tissue box")

        // Verify suggested additions (empty for fresh enrichment)
        assertThat(status.suggestedAdditions).isEmpty()
    }

    @Test
    fun `parse EnrichStatusResponse with USER source attribute`() {
        val responseJson =
            """
            {
                "success": true,
                "status": {
                    "requestId": "550e8400-e29b-41d4-a716-446655440000",
                    "correlationId": "corr-123",
                    "itemId": "item-001",
                    "stage": "ATTRIBUTES_DONE",
                    "attributesStructured": [
                        {
                            "key": "brand",
                            "value": "Nike",
                            "source": "USER",
                            "confidence": "HIGH",
                            "updatedAt": 1704067210000
                        }
                    ],
                    "summaryText": "Brand: Nike",
                    "createdAt": 1704067200000,
                    "updatedAt": 1704067210000
                }
            }
            """.trimIndent()

        val response = json.decodeFromString<EnrichStatusResponse>(responseJson)
        val status = response.status!!

        val brandAttr = status.attributesStructured!![0]
        assertThat(brandAttr.source).isEqualTo("USER")
        assertThat(brandAttr.isUserProvided).isTrue()
        assertThat(brandAttr.isDetected).isFalse()
    }

    @Test
    fun `parse EnrichStatusResponse with suggested additions`() {
        val responseJson =
            """
            {
                "success": true,
                "status": {
                    "requestId": "550e8400-e29b-41d4-a716-446655440000",
                    "correlationId": "corr-123",
                    "itemId": "item-001",
                    "stage": "ATTRIBUTES_DONE",
                    "attributesStructured": [
                        {
                            "key": "brand",
                            "value": "Nike",
                            "source": "USER",
                            "confidence": "HIGH",
                            "updatedAt": 1704067200000
                        }
                    ],
                    "summaryText": "Brand: Nike",
                    "suggestedAdditions": [
                        {
                            "attribute": {
                                "key": "color",
                                "value": "Red",
                                "source": "DETECTED",
                                "confidence": "MED",
                                "updatedAt": 1704067210000
                            },
                            "reason": "Detected color: \"Red\" (MED confidence)",
                            "action": "add"
                        },
                        {
                            "attribute": {
                                "key": "size",
                                "value": "L",
                                "source": "DETECTED",
                                "confidence": "LOW",
                                "updatedAt": 1704067210000
                            },
                            "reason": "Detected size: \"L\" (LOW confidence)",
                            "action": "add"
                        }
                    ],
                    "createdAt": 1704067200000,
                    "updatedAt": 1704067210000
                }
            }
            """.trimIndent()

        val response = json.decodeFromString<EnrichStatusResponse>(responseJson)
        val status = response.status!!

        val suggestions = status.suggestedAdditions!!
        assertThat(suggestions).hasSize(2)

        val colorSuggestion = suggestions[0]
        assertThat(colorSuggestion.attribute.key).isEqualTo("color")
        assertThat(colorSuggestion.attribute.value).isEqualTo("Red")
        assertThat(colorSuggestion.action).isEqualTo("add")
        assertThat(colorSuggestion.isAdd).isTrue()
        assertThat(colorSuggestion.isReplace).isFalse()
        assertThat(colorSuggestion.existingValue).isNull()

        val sizeSuggestion = suggestions[1]
        assertThat(sizeSuggestion.attribute.key).isEqualTo("size")
        assertThat(sizeSuggestion.isAdd).isTrue()
    }

    @Test
    fun `parse EnrichStatusResponse with replace suggestion`() {
        val responseJson =
            """
            {
                "success": true,
                "status": {
                    "requestId": "550e8400-e29b-41d4-a716-446655440000",
                    "correlationId": "corr-123",
                    "itemId": "item-001",
                    "stage": "ATTRIBUTES_DONE",
                    "attributesStructured": [
                        {
                            "key": "brand",
                            "value": "Sonny",
                            "source": "DETECTED",
                            "confidence": "LOW",
                            "updatedAt": 1704067200000
                        }
                    ],
                    "summaryText": "Brand: Sonny",
                    "suggestedAdditions": [
                        {
                            "attribute": {
                                "key": "brand",
                                "value": "Sony",
                                "source": "DETECTED",
                                "confidence": "HIGH",
                                "evidence": [{"type": "LOGO", "rawValue": "Sony", "score": 0.98}],
                                "updatedAt": 1704067210000
                            },
                            "reason": "Updated brand from \"Sonny\" to \"Sony\" (HIGH confidence)",
                            "action": "replace",
                            "existingValue": "Sonny"
                        }
                    ],
                    "createdAt": 1704067200000,
                    "updatedAt": 1704067210000
                }
            }
            """.trimIndent()

        val response = json.decodeFromString<EnrichStatusResponse>(responseJson)
        val status = response.status!!

        val suggestions = status.suggestedAdditions!!
        assertThat(suggestions).hasSize(1)

        val brandSuggestion = suggestions[0]
        assertThat(brandSuggestion.attribute.key).isEqualTo("brand")
        assertThat(brandSuggestion.attribute.value).isEqualTo("Sony")
        assertThat(brandSuggestion.action).isEqualTo("replace")
        assertThat(brandSuggestion.isReplace).isTrue()
        assertThat(brandSuggestion.isAdd).isFalse()
        assertThat(brandSuggestion.existingValue).isEqualTo("Sonny")
    }

    @Test
    fun `parse status without Phase 2 fields for backwards compatibility`() {
        // Old response format without attributesStructured, summaryText, suggestedAdditions
        val responseJson =
            """
            {
                "success": true,
                "status": {
                    "requestId": "550e8400-e29b-41d4-a716-446655440000",
                    "correlationId": "corr-123",
                    "itemId": "item-001",
                    "stage": "ATTRIBUTES_DONE",
                    "normalizedAttributes": [
                        {"key": "brand", "value": "Nike", "confidence": "HIGH", "source": "VISION_LOGO"}
                    ],
                    "createdAt": 1704067200000,
                    "updatedAt": 1704067210000
                }
            }
            """.trimIndent()

        val response = json.decodeFromString<EnrichStatusResponse>(responseJson)
        val status = response.status!!

        // Phase 2 fields should be null/absent
        assertThat(status.attributesStructured).isNull()
        assertThat(status.summaryText).isNull()
        assertThat(status.suggestedAdditions).isNull()

        // Legacy fields should still work
        assertThat(status.normalizedAttributes).isNotNull()
        assertThat(status.normalizedAttributes).hasSize(1)
    }

    @Test
    fun `StructuredAttributeDto confidence level mapping`() {
        // Test all confidence mappings
        val highJson = """{"key": "a", "value": "b", "source": "DETECTED", "confidence": "HIGH"}"""
        val medJson = """{"key": "a", "value": "b", "source": "DETECTED", "confidence": "MED"}"""
        val lowJson = """{"key": "a", "value": "b", "source": "DETECTED", "confidence": "LOW"}"""
        val mediumJson = """{"key": "a", "value": "b", "source": "DETECTED", "confidence": "MEDIUM"}"""

        val highAttr = json.decodeFromString<StructuredAttributeDto>(highJson)
        val medAttr = json.decodeFromString<StructuredAttributeDto>(medJson)
        val lowAttr = json.decodeFromString<StructuredAttributeDto>(lowJson)
        val mediumAttr = json.decodeFromString<StructuredAttributeDto>(mediumJson)

        assertThat(highAttr.confidenceLevel).isEqualTo(EnrichmentConfidence.HIGH)
        assertThat(medAttr.confidenceLevel).isEqualTo(EnrichmentConfidence.MED)
        assertThat(lowAttr.confidenceLevel).isEqualTo(EnrichmentConfidence.LOW)
        // "MEDIUM" should map to MED for backwards compatibility
        assertThat(mediumAttr.confidenceLevel).isEqualTo(EnrichmentConfidence.MED)
    }
}
