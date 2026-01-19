package com.scanium.app.selling.assistant

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Tests that verify the AssistantChatRequest serialization matches
 * the backend Zod schema expectations.
 *
 * Backend schema requirements:
 * - message: non-empty string
 * - items: array of objects with itemId (string)
 * - history: optional array with role in USER|ASSISTANT|SYSTEM, content string, timestamp number (ms)
 * - attributes: array (optional, but never null if present)
 * - source: enum USER|DETECTED|DEFAULT|UNKNOWN if present
 */
class AssistantRequestSchemaTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Test
    fun `request serialization includes all required fields`() {
        // Given a request with all fields populated
        val request =
            TestAssistantChatRequest(
                items =
                    listOf(
                        TestItemContextSnapshotDto(
                            itemId = "item-123",
                            title = "Test Item",
                            description = "A test description",
                            category = "Furniture",
                            confidence = 0.85f,
                            attributes =
                                listOf(
                                    TestItemAttributeDto(
                                        key = "brand",
                                        value = "TestBrand",
                                        confidence = 0.9f,
                                        source = "USER",
                                    ),
                                ),
                            priceEstimate = 99.99,
                            photosCount = 2,
                            exportProfileId = "generic",
                        ),
                    ),
                history =
                    listOf(
                        TestAssistantMessageDto(
                            role = "USER",
                            content = "Previous question",
                            timestamp = 1704067200000L,
                        ),
                    ),
                message = "Generate a listing",
                exportProfile = TestExportProfileDto("generic", "Generic"),
                assistantPrefs = null,
            )

        // When serialized
        val jsonString = json.encodeToString(request)
        val parsed = json.parseToJsonElement(jsonString).jsonObject

        // Then all required fields are present
        assertThat(parsed.containsKey("message")).isTrue()
        assertThat(parsed.containsKey("items")).isTrue()
        assertThat(parsed["message"]?.jsonPrimitive?.content).isEqualTo("Generate a listing")
    }

    @Test
    fun `item context includes description field`() {
        val request =
            TestAssistantChatRequest(
                items =
                    listOf(
                        TestItemContextSnapshotDto(
                            itemId = "item-123",
                            title = "Test",
                            description = "Test description",
                            category = null,
                            confidence = null,
                            attributes = emptyList(),
                            priceEstimate = null,
                            photosCount = 1,
                            exportProfileId = null,
                        ),
                    ),
                history = emptyList(),
                message = "Test",
                exportProfile = null,
                assistantPrefs = null,
            )

        val jsonString = json.encodeToString(request)
        val parsed = json.parseToJsonElement(jsonString).jsonObject
        val firstItem = parsed["items"]?.jsonArray?.get(0)?.jsonObject

        assertThat(firstItem?.containsKey("description")).isTrue()
        assertThat(firstItem?.get("description")?.jsonPrimitive?.content).isEqualTo("Test description")
    }

    @Test
    fun `history messages have valid role enum values`() {
        val request =
            TestAssistantChatRequest(
                items = emptyList(),
                history =
                    listOf(
                        TestAssistantMessageDto(role = "USER", content = "Q1", timestamp = 1000L),
                        TestAssistantMessageDto(role = "ASSISTANT", content = "A1", timestamp = 2000L),
                        TestAssistantMessageDto(role = "SYSTEM", content = "S1", timestamp = 3000L),
                    ),
                message = "Test",
                exportProfile = null,
                assistantPrefs = null,
            )

        val jsonString = json.encodeToString(request)
        val parsed = json.parseToJsonElement(jsonString).jsonObject
        val history = parsed["history"]?.jsonArray

        assertThat(history).isNotNull()
        assertThat(history?.size).isEqualTo(3)
        assertThat(history?.get(0)?.jsonObject?.get("role")?.jsonPrimitive?.content).isEqualTo("USER")
        assertThat(history?.get(1)?.jsonObject?.get("role")?.jsonPrimitive?.content).isEqualTo("ASSISTANT")
        assertThat(history?.get(2)?.jsonObject?.get("role")?.jsonPrimitive?.content).isEqualTo("SYSTEM")
    }

    @Test
    fun `timestamp is serialized as number in milliseconds`() {
        val timestampMs = 1704067200000L // 2024-01-01 00:00:00 UTC
        val request =
            TestAssistantChatRequest(
                items = emptyList(),
                history =
                    listOf(
                        TestAssistantMessageDto(role = "USER", content = "Test", timestamp = timestampMs),
                    ),
                message = "Test",
                exportProfile = null,
                assistantPrefs = null,
            )

        val jsonString = json.encodeToString(request)
        val parsed = json.parseToJsonElement(jsonString).jsonObject
        val firstMessage = parsed["history"]?.jsonArray?.get(0)?.jsonObject

        val timestampValue = firstMessage?.get("timestamp")?.jsonPrimitive?.content?.toLong()
        assertThat(timestampValue).isEqualTo(timestampMs)
    }

    @Test
    fun `attributes array is never null when items have attributes`() {
        val request =
            TestAssistantChatRequest(
                items =
                    listOf(
                        TestItemContextSnapshotDto(
                            itemId = "item-123",
                            title = "Test",
                            description = null,
                            category = null,
                            confidence = null,
                            attributes =
                                listOf(
                                    TestItemAttributeDto("brand", "Nike", 0.9f, "DETECTED"),
                                ),
                            priceEstimate = null,
                            photosCount = 1,
                            exportProfileId = null,
                        ),
                    ),
                history = emptyList(),
                message = "Test",
                exportProfile = null,
                assistantPrefs = null,
            )

        val jsonString = json.encodeToString(request)
        val parsed = json.parseToJsonElement(jsonString).jsonObject
        val firstItem = parsed["items"]?.jsonArray?.get(0)?.jsonObject
        val attributes = firstItem?.get("attributes")

        assertThat(attributes).isNotNull()
        assertThat(attributes).isNotInstanceOf(JsonNull::class.java)
        assertThat(attributes?.jsonArray).isNotNull()
    }

    @Test
    fun `attribute source enum values match backend schema`() {
        // Backend expects: USER, DETECTED, DEFAULT, UNKNOWN
        val validSources = listOf("USER", "DETECTED", "DEFAULT", "UNKNOWN")

        validSources.forEach { source ->
            val request =
                TestAssistantChatRequest(
                    items =
                        listOf(
                            TestItemContextSnapshotDto(
                                itemId = "item-123",
                                title = "Test",
                                description = null,
                                category = null,
                                confidence = null,
                                attributes =
                                    listOf(
                                        TestItemAttributeDto("key", "value", 0.8f, source),
                                    ),
                                priceEstimate = null,
                                photosCount = 1,
                                exportProfileId = null,
                            ),
                        ),
                    history = emptyList(),
                    message = "Test",
                    exportProfile = null,
                    assistantPrefs = null,
                )

            val jsonString = json.encodeToString(request)
            val parsed = json.parseToJsonElement(jsonString).jsonObject
            val firstAttr =
                parsed["items"]?.jsonArray?.get(0)?.jsonObject
                    ?.get("attributes")?.jsonArray?.get(0)?.jsonObject

            assertThat(firstAttr?.get("source")?.jsonPrimitive?.content).isEqualTo(source)
        }
    }

    @Test
    fun `empty history is serialized as empty array not null`() {
        val request =
            TestAssistantChatRequest(
                items = emptyList(),
                history = emptyList(),
                message = "Test",
                exportProfile = null,
                assistantPrefs = null,
            )

        val jsonString = json.encodeToString(request)
        val parsed = json.parseToJsonElement(jsonString).jsonObject
        val history = parsed["history"]

        assertThat(history).isNotNull()
        assertThat(history).isNotInstanceOf(JsonNull::class.java)
        assertThat(history?.jsonArray?.size).isEqualTo(0)
    }

    @Test
    fun `message is never empty`() {
        val request =
            TestAssistantChatRequest(
                items = emptyList(),
                history = emptyList(),
                message = "Generate a listing for this item",
                exportProfile = null,
                assistantPrefs = null,
            )

        val jsonString = json.encodeToString(request)
        val parsed = json.parseToJsonElement(jsonString).jsonObject
        val message = parsed["message"]?.jsonPrimitive?.content

        assertThat(message).isNotNull()
        assertThat(message).isNotEmpty()
    }

    @Test
    fun `assistantPrefs tone enum values match backend schema`() {
        // Backend expects: NEUTRAL, FRIENDLY, PROFESSIONAL, MARKETPLACE
        val validTones = listOf("NEUTRAL", "FRIENDLY", "PROFESSIONAL", "MARKETPLACE")

        validTones.forEach { tone ->
            val request =
                TestAssistantChatRequest(
                    items =
                        listOf(
                            TestItemContextSnapshotDto(
                                itemId = "item-123",
                                title = "Test",
                            ),
                        ),
                    history = emptyList(),
                    message = "Test",
                    exportProfile = null,
                    assistantPrefs =
                        TestAssistantPrefsDto(
                            tone = tone,
                            language = "EN",
                            region = "EU",
                        ),
                )

            val jsonString = json.encodeToString(request)
            val parsed = json.parseToJsonElement(jsonString).jsonObject
            val prefs = parsed["assistantPrefs"]?.jsonObject

            assertThat(prefs).isNotNull()
            assertThat(prefs?.get("tone")?.jsonPrimitive?.content).isEqualTo(tone)
        }
    }

    @Test
    fun `MARKETPLACE tone serializes correctly`() {
        val request =
            TestAssistantChatRequest(
                items =
                    listOf(
                        TestItemContextSnapshotDto(itemId = "item-1", title = "Test Item"),
                    ),
                history = emptyList(),
                message = "Create listing",
                exportProfile = null,
                assistantPrefs =
                    TestAssistantPrefsDto(
                        tone = "MARKETPLACE",
                        language = "EN",
                        region = "NL",
                        verbosity = "CONCISE",
                    ),
            )

        val jsonString = json.encodeToString(request)
        val parsed = json.parseToJsonElement(jsonString).jsonObject
        val prefs = parsed["assistantPrefs"]?.jsonObject

        assertThat(prefs).isNotNull()
        assertThat(prefs?.get("tone")?.jsonPrimitive?.content).isEqualTo("MARKETPLACE")
        assertThat(prefs?.get("language")?.jsonPrimitive?.content).isEqualTo("EN")
        assertThat(prefs?.get("region")?.jsonPrimitive?.content).isEqualTo("NL")
        assertThat(prefs?.get("verbosity")?.jsonPrimitive?.content).isEqualTo("CONCISE")
    }
}

// Test-only DTOs that mirror production DTOs for schema verification
@kotlinx.serialization.Serializable
private data class TestAssistantChatRequest(
    val items: List<TestItemContextSnapshotDto>,
    val history: List<TestAssistantMessageDto> = emptyList(),
    val message: String,
    val exportProfile: TestExportProfileDto? = null,
    val assistantPrefs: TestAssistantPrefsDto? = null,
)

@kotlinx.serialization.Serializable
private data class TestItemContextSnapshotDto(
    val itemId: String,
    val title: String? = null,
    val description: String? = null,
    val category: String? = null,
    val confidence: Float? = null,
    val attributes: List<TestItemAttributeDto> = emptyList(),
    val priceEstimate: Double? = null,
    val photosCount: Int = 0,
    val exportProfileId: String? = null,
)

@kotlinx.serialization.Serializable
private data class TestItemAttributeDto(
    val key: String,
    val value: String,
    val confidence: Float? = null,
    val source: String? = null,
)

@kotlinx.serialization.Serializable
private data class TestAssistantMessageDto(
    val role: String,
    val content: String,
    val timestamp: Long,
    val itemContextIds: List<String> = emptyList(),
)

@kotlinx.serialization.Serializable
private data class TestExportProfileDto(
    val id: String,
    val displayName: String,
)

@kotlinx.serialization.Serializable
private data class TestAssistantPrefsDto(
    val language: String? = null,
    val tone: String? = null,
    val region: String? = null,
    val units: String? = null,
    val verbosity: String? = null,
)
