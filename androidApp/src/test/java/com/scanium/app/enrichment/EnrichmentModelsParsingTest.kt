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

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `parse EnrichSubmitResponse with success`() {
        val responseJson = """
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
        val responseJson = """
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
        val responseJson = """
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
        val responseJson = """
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
        val responseJson = """
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
        val responseJson = """
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
        val responseJson = """
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
        val responseJson = """
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
}
