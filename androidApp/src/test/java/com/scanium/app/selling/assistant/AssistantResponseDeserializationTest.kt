package com.scanium.app.selling.assistant

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for assistant response deserialization.
 *
 * Verifies that the Android client can correctly deserialize responses from the backend,
 * including:
 * - The `reply` field (backend sends `reply`, Android expects `content` via @SerialName)
 * - The `fromCache` boolean field
 * - Extra/unknown fields are ignored (safety, correlationId, etc.)
 * - Legacy responses with fromCache as string in citationsMetadata still work
 */
@RunWith(RobolectricTestRunner::class)
class AssistantResponseDeserializationTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Test
    fun `deserializes response with reply field mapped to content`() {
        val backendResponse =
            """
            {
                "reply": "Here's help with your listing.",
                "actions": [],
                "citationsMetadata": {},
                "fromCache": false,
                "confidenceTier": "HIGH",
                "evidence": [],
                "suggestedAttributes": [],
                "suggestedDraftUpdates": []
            }
            """.trimIndent()

        val parsed = json.decodeFromString<DeserTestChatResponse>(backendResponse)

        assertThat(parsed.content).isEqualTo("Here's help with your listing.")
        assertThat(parsed.fromCache).isFalse()
        assertThat(parsed.confidenceTier).isEqualTo("HIGH")
    }

    @Test
    fun `deserializes response with fromCache true`() {
        val backendResponse =
            """
            {
                "reply": "Cached response.",
                "actions": [],
                "citationsMetadata": {},
                "fromCache": true,
                "confidenceTier": "MEDIUM"
            }
            """.trimIndent()

        val parsed = json.decodeFromString<DeserTestChatResponse>(backendResponse)

        assertThat(parsed.content).isEqualTo("Cached response.")
        assertThat(parsed.fromCache).isTrue()
    }

    @Test
    fun `ignores unknown fields like safety and correlationId`() {
        val backendResponse =
            """
            {
                "reply": "Response with extra fields.",
                "actions": [],
                "citationsMetadata": {},
                "fromCache": false,
                "safety": {
                    "blocked": false,
                    "reasonCode": null,
                    "requestId": "abc-123"
                },
                "correlationId": "corr-456",
                "someNewField": "future field"
            }
            """.trimIndent()

        val parsed = json.decodeFromString<DeserTestChatResponse>(backendResponse)

        assertThat(parsed.content).isEqualTo("Response with extra fields.")
        assertThat(parsed.fromCache).isFalse()
    }

    @Test
    fun `handles missing optional fields with defaults`() {
        val minimalResponse =
            """
            {
                "reply": "Minimal response."
            }
            """.trimIndent()

        val parsed = json.decodeFromString<DeserTestChatResponse>(minimalResponse)

        assertThat(parsed.content).isEqualTo("Minimal response.")
        assertThat(parsed.actions).isEmpty()
        assertThat(parsed.citationsMetadata).isNull()
        assertThat(parsed.fromCache).isFalse()
        assertThat(parsed.confidenceTier).isNull()
    }

    @Test
    fun `handles assistantError in response`() {
        val errorResponse =
            """
            {
                "reply": "Error occurred.",
                "actions": [],
                "citationsMetadata": {},
                "fromCache": false,
                "assistantError": {
                    "type": "provider_unavailable",
                    "category": "temporary",
                    "retryable": true,
                    "message": "Provider is down"
                }
            }
            """.trimIndent()

        val parsed = json.decodeFromString<DeserTestChatResponse>(errorResponse)

        assertThat(parsed.content).isEqualTo("Error occurred.")
        assertThat(parsed.assistantError).isNotNull()
        assertThat(parsed.assistantError?.type).isEqualTo("provider_unavailable")
        assertThat(parsed.assistantError?.retryable).isTrue()
    }

    @Test
    fun `handles complex citationsMetadata including legacy fromCache string`() {
        // Legacy format: fromCache was a string in citationsMetadata
        val legacyResponse =
            """
            {
                "reply": "Legacy response.",
                "actions": [],
                "citationsMetadata": {
                    "source": "claude",
                    "model": "claude-3",
                    "fromCache": "true"
                },
                "fromCache": true
            }
            """.trimIndent()

        val parsed = json.decodeFromString<DeserTestChatResponse>(legacyResponse)

        assertThat(parsed.content).isEqualTo("Legacy response.")
        assertThat(parsed.fromCache).isTrue()
        assertThat(parsed.citationsMetadata).isNotNull()
        assertThat(parsed.citationsMetadata?.get("fromCache")).isEqualTo("true") // Legacy string
    }

    @Test
    fun `handles full backend response with all fields`() {
        val fullResponse =
            """
            {
                "reply": "I analyzed your vintage lamp. Based on the markings, this appears to be a mid-century piece.",
                "actions": [
                    {
                        "type": "COPY_TEXT",
                        "payload": {"text": "Mid-century vintage lamp"},
                        "label": "Copy title suggestion"
                    }
                ],
                "citationsMetadata": {
                    "model": "claude-3-sonnet",
                    "tokensUsed": "1234"
                },
                "fromCache": false,
                "confidenceTier": "HIGH",
                "evidence": [
                    {"type": "visual", "text": "Visible manufacturer mark on base"}
                ],
                "suggestedAttributes": [
                    {"key": "brand", "value": "Unknown", "confidence": "MEDIUM"}
                ],
                "suggestedDraftUpdates": [
                    {"field": "title", "value": "Vintage Mid-Century Table Lamp", "confidence": "HIGH"}
                ],
                "suggestedNextPhoto": "Take a close-up of the manufacturer mark",
                "safety": {"blocked": false, "reasonCode": null, "requestId": "req-123"},
                "correlationId": "corr-abc"
            }
            """.trimIndent()

        val parsed = json.decodeFromString<DeserTestChatResponse>(fullResponse)

        assertThat(parsed.content).contains("vintage lamp")
        assertThat(parsed.actions).hasSize(1)
        assertThat(parsed.actions.first().type).isEqualTo("COPY_TEXT")
        assertThat(parsed.fromCache).isFalse()
        assertThat(parsed.confidenceTier).isEqualTo("HIGH")
        assertThat(parsed.evidence).hasSize(1)
        assertThat(parsed.suggestedAttributes).hasSize(1)
        assertThat(parsed.suggestedDraftUpdates).hasSize(1)
        assertThat(parsed.suggestedNextPhoto).contains("manufacturer mark")
    }
}

/**
 * Test DTOs that mirror the actual DTOs in AssistantRepository.kt.
 * These are used to verify serialization behavior independently.
 * Prefix with "Deser" to avoid name collisions with other test files.
 */
@Serializable
private data class DeserTestChatResponse(
    @SerialName("reply")
    val content: String,
    val actions: List<DeserTestActionDto> = emptyList(),
    val citationsMetadata: Map<String, String>? = null,
    val fromCache: Boolean = false,
    val confidenceTier: String? = null,
    val evidence: List<DeserTestEvidenceDto> = emptyList(),
    val suggestedAttributes: List<DeserTestAttributeDto> = emptyList(),
    val suggestedDraftUpdates: List<DeserTestDraftUpdateDto> = emptyList(),
    val suggestedNextPhoto: String? = null,
    val assistantError: DeserTestErrorDto? = null,
)

@Serializable
private data class DeserTestActionDto(
    val type: String,
    val payload: Map<String, String> = emptyMap(),
    val label: String? = null,
)

@Serializable
private data class DeserTestEvidenceDto(
    val type: String,
    val text: String,
)

@Serializable
private data class DeserTestAttributeDto(
    val key: String,
    val value: String,
    val confidence: String,
)

@Serializable
private data class DeserTestDraftUpdateDto(
    val field: String,
    val value: String,
    val confidence: String,
)

@Serializable
private data class DeserTestErrorDto(
    val type: String,
    val category: String,
    val retryable: Boolean,
    val retryAfterSeconds: Int? = null,
    val message: String? = null,
)
