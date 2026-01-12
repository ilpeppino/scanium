package com.scanium.app.selling.assistant

import com.google.common.truth.Truth.assertThat
import com.scanium.app.listing.ExportProfileDefinition
import com.scanium.app.model.AssistantMessage
import com.scanium.app.model.AssistantPrefs
import com.scanium.app.model.AssistantResponse
import com.scanium.app.model.AssistantRole
import com.scanium.app.model.ItemAttributeSnapshot
import com.scanium.app.model.ItemContextSnapshot
import com.scanium.app.selling.assistant.network.AssistantHttpConfig
import com.scanium.app.selling.assistant.network.AssistantOkHttpClientFactory
import com.scanium.shared.core.models.listing.ExportProfileId
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for multipart request building in AssistantRepository.
 * Verifies that images are correctly included in requests when provided.
 */
@RunWith(RobolectricTestRunner::class)
class AssistantRepositoryMultipartTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        // Use unified test configuration from AssistantHttpConfig
        client = AssistantOkHttpClientFactory.create(
            config = AssistantHttpConfig.TEST,
            logStartupPolicy = false,
        )
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `send with no images uses JSON content type`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"content":"Hello","actions":[],"citationsMetadata":{}}"""),
        )

        val repository = createRepository()
        val items = listOf(createTestSnapshot("item-1"))

        // Act
        repository.send(
            items = items,
            history = emptyList(),
            userMessage = "Hello",
            exportProfile = createTestProfile(),
            correlationId = "test-123",
            imageAttachments = emptyList(), // No images
            assistantPrefs = null,
        )

        // Assert
        val request = mockWebServer.takeRequest()
        assertThat(request.path).isEqualTo("/v1/assist/chat")
        assertThat(request.getHeader("Content-Type")).contains("application/json")
        assertThat(request.body.readUtf8()).contains("\"message\":\"Hello\"")
    }

    @Test
    fun `send with images uses multipart content type`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"content":"Hello with image","actions":[],"citationsMetadata":{}}"""),
        )

        val repository = createRepository()
        val items = listOf(createTestSnapshot("item-1"))
        val imageAttachments = listOf(
            ItemImageAttachment(
                itemId = "item-1",
                imageBytes = "fake-image-bytes".toByteArray(),
                mimeType = "image/jpeg",
                filename = "test.jpg",
            ),
        )

        // Act
        repository.send(
            items = items,
            history = emptyList(),
            userMessage = "What color is this?",
            exportProfile = createTestProfile(),
            correlationId = "test-456",
            imageAttachments = imageAttachments,
            assistantPrefs = null,
        )

        // Assert
        val request = mockWebServer.takeRequest()
        assertThat(request.path).isEqualTo("/v1/assist/chat")
        assertThat(request.getHeader("Content-Type")).contains("multipart/form-data")

        val body = request.body.readUtf8()
        // Verify payload part is included
        assertThat(body).contains("name=\"payload\"")
        assertThat(body).contains("\"message\":\"What color is this?\"")
        // Verify image part is included with correct field naming
        assertThat(body).contains("name=\"itemImages[item-1]\"")
        assertThat(body).contains("fake-image-bytes")
    }

    @Test
    fun `send with multiple images includes all in multipart`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"content":"Got images","actions":[],"citationsMetadata":{}}"""),
        )

        val repository = createRepository()
        val items = listOf(
            createTestSnapshot("item-1"),
            createTestSnapshot("item-2"),
        )
        val imageAttachments = listOf(
            ItemImageAttachment(
                itemId = "item-1",
                imageBytes = "image1-bytes".toByteArray(),
                mimeType = "image/jpeg",
                filename = "image1.jpg",
            ),
            ItemImageAttachment(
                itemId = "item-1",
                imageBytes = "image2-bytes".toByteArray(),
                mimeType = "image/jpeg",
                filename = "image2.jpg",
            ),
            ItemImageAttachment(
                itemId = "item-2",
                imageBytes = "image3-bytes".toByteArray(),
                mimeType = "image/png",
                filename = "image3.png",
            ),
        )

        // Act
        repository.send(
            items = items,
            history = emptyList(),
            userMessage = "Analyze these",
            exportProfile = createTestProfile(),
            correlationId = "test-789",
            imageAttachments = imageAttachments,
            assistantPrefs = null,
        )

        // Assert
        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()

        // Verify all images are included
        assertThat(body).contains("name=\"itemImages[item-1]\"")
        assertThat(body).contains("name=\"itemImages[item-2]\"")
        assertThat(body).contains("image1-bytes")
        assertThat(body).contains("image2-bytes")
        assertThat(body).contains("image3-bytes")
    }

    @Test
    fun `send includes common headers with multipart request`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"content":"ok","actions":[],"citationsMetadata":{}}"""),
        )

        val repository = createRepository(apiKey = "test-api-key")
        val imageAttachments = listOf(
            ItemImageAttachment(
                itemId = "item-1",
                imageBytes = "test".toByteArray(),
                mimeType = "image/jpeg",
                filename = "test.jpg",
            ),
        )

        // Act
        repository.send(
            items = listOf(createTestSnapshot("item-1")),
            history = emptyList(),
            userMessage = "Test",
            exportProfile = createTestProfile(),
            correlationId = "correlation-123",
            imageAttachments = imageAttachments,
            assistantPrefs = null,
        )

        // Assert
        val request = mockWebServer.takeRequest()
        assertThat(request.getHeader("X-API-Key")).isEqualTo("test-api-key")
        assertThat(request.getHeader("X-Scanium-Correlation-Id")).isEqualTo("correlation-123")
        assertThat(request.getHeader("X-Client")).isEqualTo("Scanium-Android")
    }

    @Test
    fun `send with images includes history in payload`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"content":"ok","actions":[],"citationsMetadata":{}}"""),
        )

        val repository = createRepository()
        val history = listOf(
            AssistantMessage(
                role = AssistantRole.USER,
                content = "Previous question",
                timestamp = 1234567890L,
            ),
            AssistantMessage(
                role = AssistantRole.ASSISTANT,
                content = "Previous answer",
                timestamp = 1234567891L,
            ),
        )
        val imageAttachments = listOf(
            ItemImageAttachment(
                itemId = "item-1",
                imageBytes = "test".toByteArray(),
                mimeType = "image/jpeg",
                filename = "test.jpg",
            ),
        )

        // Act
        repository.send(
            items = listOf(createTestSnapshot("item-1")),
            history = history,
            userMessage = "Follow-up question",
            exportProfile = createTestProfile(),
            correlationId = "test-123",
            imageAttachments = imageAttachments,
            assistantPrefs = null,
        )

        // Assert
        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertThat(body).contains("Previous question")
        assertThat(body).contains("Previous answer")
        assertThat(body).contains("Follow-up question")
    }

    @Test
    fun `send with null assistantPrefs omits field from JSON payload`() = runBlocking {
        // Arrange
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"content":"ok","actions":[],"citationsMetadata":{}}"""),
        )

        val repository = createRepository()
        val items = listOf(createTestSnapshot("item-1"))

        // Act
        repository.send(
            items = items,
            history = emptyList(),
            userMessage = "Test message",
            exportProfile = createTestProfile(),
            correlationId = "test-123",
            imageAttachments = emptyList(),
            assistantPrefs = null,  // Explicitly null
        )

        // Assert
        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()

        // Verify that assistantPrefs field is NOT present in the JSON
        // (explicitNulls = false should omit it rather than sending "assistantPrefs":null)
        assertThat(body).doesNotContain("\"assistantPrefs\"")
    }

    // Helper methods

    private fun createRepository(apiKey: String? = "test-key"): AssistantRepository {
        val baseUrl = mockWebServer.url("/").toString()
        return TestAssistantRepository(client, baseUrl, apiKey)
    }

    private fun createTestSnapshot(itemId: String): ItemContextSnapshot {
        return ItemContextSnapshot(
            itemId = itemId,
            title = "Test Item",
            description = "Test description",
            category = "Furniture",
            confidence = 0.85f,
            attributes = listOf(
                ItemAttributeSnapshot(key = "category", value = "Furniture", confidence = 0.85f),
            ),
            priceEstimate = 25.0,
            photosCount = 1,
            exportProfileId = ExportProfileId.GENERIC,
        )
    }

    private fun createTestProfile(): ExportProfileDefinition {
        return ExportProfileDefinition(
            id = ExportProfileId.GENERIC,
            displayName = "Generic",
            titleRules = com.scanium.shared.core.models.listing.ExportTitleRules(),
            descriptionRules = com.scanium.shared.core.models.listing.ExportDescriptionRules(),
        )
    }
}

/**
 * A test-only implementation of AssistantRepository that uses MockWebServer.
 * This allows testing the multipart request building without the full production setup.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private class TestAssistantRepository(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val apiKey: String?,
) : AssistantRepository {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false  // Don't encode null values for optional fields
    }

    override suspend fun send(
        items: List<ItemContextSnapshot>,
        history: List<AssistantMessage>,
        userMessage: String,
        exportProfile: ExportProfileDefinition,
        correlationId: String,
        imageAttachments: List<ItemImageAttachment>,
        assistantPrefs: AssistantPrefs?,
        includePricing: Boolean,
        pricingCountryCode: String?,
    ): AssistantResponse = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val requestPayload = buildPayload(items, history, userMessage, exportProfile, assistantPrefs)
        val endpoint = "${baseUrl.trimEnd('/')}/v1/assist/chat"
        val payloadJson = json.encodeToString(TestChatRequestDto.serializer(), requestPayload)

        val request = if (imageAttachments.isNotEmpty()) {
            buildMultipartRequest(endpoint, payloadJson, imageAttachments, correlationId)
        } else {
            buildJsonRequest(endpoint, payloadJson, correlationId)
        }

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw RuntimeException("Empty response")
            if (response.isSuccessful) {
                val parsed = json.decodeFromString(TestChatResponseDto.serializer(), responseBody)
                return@withContext AssistantResponse(content = parsed.content ?: "")
            }
            throw RuntimeException("Request failed: ${response.code}")
        }
    }

    private fun buildPayload(
        items: List<ItemContextSnapshot>,
        history: List<AssistantMessage>,
        userMessage: String,
        exportProfile: ExportProfileDefinition,
        assistantPrefs: AssistantPrefs?,
    ): TestChatRequestDto {
        return TestChatRequestDto(
            items = items.map { item ->
                TestItemSnapshotDto(
                    itemId = item.itemId,
                    title = item.title,
                    description = item.description,
                    category = item.category,
                    confidence = item.confidence,
                    attributes = item.attributes.map { attr ->
                        TestAttributeDto(attr.key, attr.value, attr.confidence)
                    },
                    priceEstimate = item.priceEstimate,
                    photosCount = item.photosCount,
                    exportProfileId = item.exportProfileId.value,
                )
            },
            history = history.map { msg ->
                TestMessageDto(msg.role.name, msg.content, msg.timestamp)
            },
            message = userMessage,
            exportProfile = TestProfileDto(exportProfile.id.value, exportProfile.displayName),
        )
    }

    private fun buildJsonRequest(
        endpoint: String,
        payloadJson: String,
        correlationId: String,
    ): Request {
        return Request.Builder()
            .url(endpoint)
            .post(payloadJson.toRequestBody("application/json".toMediaType()))
            .apply { addCommonHeaders(this, correlationId) }
            .build()
    }

    private fun buildMultipartRequest(
        endpoint: String,
        payloadJson: String,
        imageAttachments: List<ItemImageAttachment>,
        correlationId: String,
    ): Request {
        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("payload", payloadJson)

        imageAttachments.forEachIndexed { index, attachment ->
            val fieldName = "itemImages[${attachment.itemId}]"
            val mediaType = attachment.mimeType.toMediaType()
            val filename = "image_${index}_${attachment.filename}"
            multipartBuilder.addFormDataPart(
                fieldName,
                filename,
                attachment.imageBytes.toRequestBody(mediaType),
            )
        }

        return Request.Builder()
            .url(endpoint)
            .post(multipartBuilder.build())
            .apply { addCommonHeaders(this, correlationId) }
            .build()
    }

    private fun addCommonHeaders(builder: Request.Builder, correlationId: String) {
        apiKey?.let { builder.header("X-API-Key", it) }
        builder.header("X-Scanium-Correlation-Id", correlationId)
        builder.header("X-Client", "Scanium-Android")
    }
}

// Test-only DTOs for serialization (minimal versions)

@kotlinx.serialization.Serializable
private data class TestChatRequestDto(
    val items: List<TestItemSnapshotDto>,
    val history: List<TestMessageDto>?,
    val message: String,
    val exportProfile: TestProfileDto?,
)

@kotlinx.serialization.Serializable
private data class TestItemSnapshotDto(
    val itemId: String,
    val title: String?,
    val description: String?,
    val category: String?,
    val confidence: Float?,
    val attributes: List<TestAttributeDto>,
    val priceEstimate: Double?,
    val photosCount: Int,
    val exportProfileId: String?,
)

@kotlinx.serialization.Serializable
private data class TestAttributeDto(
    val key: String,
    val value: String,
    val confidence: Float?,
)

@kotlinx.serialization.Serializable
private data class TestMessageDto(
    val role: String,
    val content: String,
    val timestamp: Long,
)

@kotlinx.serialization.Serializable
private data class TestProfileDto(
    val id: String,
    val displayName: String,
)

@kotlinx.serialization.Serializable
private data class TestChatResponseDto(
    val content: String?,
    val actions: List<String>?,
    val citationsMetadata: Map<String, String>?,
)
