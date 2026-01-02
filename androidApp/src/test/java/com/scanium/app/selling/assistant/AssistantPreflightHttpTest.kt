package com.scanium.app.selling.assistant

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/**
 * Tests for preflight HTTP response mapping.
 * Uses MockWebServer to verify correct status mapping for different HTTP responses.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantPreflightHttpTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockWebServer = MockWebServer()
        mockWebServer.start()
        client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        mockWebServer.shutdown()
    }

    @Test
    fun `200 with assistant ready returns AVAILABLE`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "status": "ok",
                        "ts": "2025-01-02T12:00:00Z",
                        "version": "1.0.0",
                        "assistant": {
                            "providerConfigured": true,
                            "providerReachable": true,
                            "state": "ready"
                        }
                    }
                    """.trimIndent(),
                ),
        )

        val result = performPreflight()

        assertThat(result.status).isEqualTo(PreflightStatus.AVAILABLE)
        assertThat(result.isAvailable).isTrue()
    }

    @Test
    fun `200 with assistant not ready returns TEMPORARILY_UNAVAILABLE`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "status": "ok",
                        "ts": "2025-01-02T12:00:00Z",
                        "version": "1.0.0",
                        "assistant": {
                            "providerConfigured": true,
                            "providerReachable": false,
                            "state": "provider_unreachable"
                        }
                    }
                    """.trimIndent(),
                ),
        )

        val result = performPreflight()

        assertThat(result.status).isEqualTo(PreflightStatus.TEMPORARILY_UNAVAILABLE)
        assertThat(result.reasonCode).isEqualTo("provider_unreachable")
    }

    @Test
    fun `401 returns UNAUTHORIZED not 404`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error": "unauthorized"}"""),
        )

        val result = performPreflight()

        assertThat(result.status).isEqualTo(PreflightStatus.UNAUTHORIZED)
        assertThat(result.reasonCode).isEqualTo("http_401")
    }

    @Test
    fun `403 returns UNAUTHORIZED not 404`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"error": "forbidden"}"""),
        )

        val result = performPreflight()

        assertThat(result.status).isEqualTo(PreflightStatus.UNAUTHORIZED)
        assertThat(result.reasonCode).isEqualTo("http_403")
    }

    @Test
    fun `404 returns ENDPOINT_NOT_FOUND not TEMPORARILY_UNAVAILABLE`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error": "not found"}"""),
        )

        val result = performPreflight()

        assertThat(result.status).isEqualTo(PreflightStatus.ENDPOINT_NOT_FOUND)
        assertThat(result.reasonCode).isEqualTo("endpoint_not_found")
        // 404 should NOT be retryable - it's a configuration error
        assertThat(result.canRetry).isFalse()
    }

    @Test
    fun `429 returns RATE_LIMITED with retry after`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "60")
                .setBody("""{"error": "rate limited"}"""),
        )

        val result = performPreflight()

        assertThat(result.status).isEqualTo(PreflightStatus.RATE_LIMITED)
        assertThat(result.reasonCode).isEqualTo("http_429")
        assertThat(result.retryAfterSeconds).isEqualTo(60)
        assertThat(result.canRetry).isTrue()
    }

    @Test
    fun `500 returns TEMPORARILY_UNAVAILABLE`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error": "internal server error"}"""),
        )

        val result = performPreflight()

        assertThat(result.status).isEqualTo(PreflightStatus.TEMPORARILY_UNAVAILABLE)
        assertThat(result.reasonCode).isEqualTo("http_500")
        assertThat(result.canRetry).isTrue()
    }

    @Test
    fun `502 returns TEMPORARILY_UNAVAILABLE`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(502)
                .setBody("""{"error": "bad gateway"}"""),
        )

        val result = performPreflight()

        assertThat(result.status).isEqualTo(PreflightStatus.TEMPORARILY_UNAVAILABLE)
        assertThat(result.reasonCode).isEqualTo("http_502")
    }

    @Test
    fun `503 returns TEMPORARILY_UNAVAILABLE`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("""{"error": "service unavailable"}"""),
        )

        val result = performPreflight()

        assertThat(result.status).isEqualTo(PreflightStatus.TEMPORARILY_UNAVAILABLE)
        assertThat(result.reasonCode).isEqualTo("http_503")
    }

    @Test
    fun `504 returns TEMPORARILY_UNAVAILABLE`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(504)
                .setBody("""{"error": "gateway timeout"}"""),
        )

        val result = performPreflight()

        assertThat(result.status).isEqualTo(PreflightStatus.TEMPORARILY_UNAVAILABLE)
        assertThat(result.reasonCode).isEqualTo("http_504")
    }

    @Test
    fun `preflight uses correct path health not v1_health`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "status": "ok",
                        "assistant": {
                            "providerConfigured": true,
                            "providerReachable": true
                        }
                    }
                    """.trimIndent(),
                ),
        )

        performPreflight()

        val request = mockWebServer.takeRequest()
        assertThat(request.path).isEqualTo("/health")
        assertThat(request.path).doesNotContain("/v1/health")
    }

    /**
     * Simulates the preflight check logic.
     * This mirrors the actual implementation in AssistantPreflightManagerImpl.
     */
    private suspend fun performPreflight(): PreflightResult = withContext(Dispatchers.IO) {
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        val endpoint = "$baseUrl/health"
        val startTime = System.currentTimeMillis()

        val request = Request.Builder()
            .url(endpoint)
            .get()
            .header("X-API-Key", "test-key")
            .header("X-Client", "Scanium-Android")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val body = response.body?.string()

                when {
                    response.isSuccessful -> {
                        val healthResponse = body?.let {
                            runCatching {
                                json.decodeFromString<TestHealthResponse>(it)
                            }.getOrNull()
                        }

                        val assistantReady = healthResponse?.assistant?.let {
                            it.providerConfigured && it.providerReachable
                        } ?: false

                        if (assistantReady) {
                            PreflightResult(
                                status = PreflightStatus.AVAILABLE,
                                latencyMs = latency,
                            )
                        } else {
                            PreflightResult(
                                status = PreflightStatus.TEMPORARILY_UNAVAILABLE,
                                latencyMs = latency,
                                reasonCode = healthResponse?.assistant?.state ?: "assistant_not_ready",
                            )
                        }
                    }
                    response.code == 401 || response.code == 403 -> {
                        PreflightResult(
                            status = PreflightStatus.UNAUTHORIZED,
                            latencyMs = latency,
                            reasonCode = "http_${response.code}",
                        )
                    }
                    response.code == 404 -> {
                        PreflightResult(
                            status = PreflightStatus.ENDPOINT_NOT_FOUND,
                            latencyMs = latency,
                            reasonCode = "endpoint_not_found",
                        )
                    }
                    response.code == 429 -> {
                        val retryAfter = response.header("Retry-After")?.toIntOrNull()
                        PreflightResult(
                            status = PreflightStatus.RATE_LIMITED,
                            latencyMs = latency,
                            reasonCode = "http_429",
                            retryAfterSeconds = retryAfter,
                        )
                    }
                    response.code in 500..599 -> {
                        PreflightResult(
                            status = PreflightStatus.TEMPORARILY_UNAVAILABLE,
                            latencyMs = latency,
                            reasonCode = "http_${response.code}",
                        )
                    }
                    else -> {
                        PreflightResult(
                            status = PreflightStatus.TEMPORARILY_UNAVAILABLE,
                            latencyMs = latency,
                            reasonCode = "http_${response.code}",
                        )
                    }
                }
            }
        } catch (e: Exception) {
            PreflightResult(
                status = PreflightStatus.OFFLINE,
                latencyMs = System.currentTimeMillis() - startTime,
                reasonCode = "exception",
            )
        }
    }
}

/**
 * Test DTOs that match the backend response format.
 */
@kotlinx.serialization.Serializable
private data class TestHealthResponse(
    val status: String = "unknown",
    val ts: String? = null,
    val version: String? = null,
    val assistant: TestAssistantHealthStatus? = null,
)

@kotlinx.serialization.Serializable
private data class TestAssistantHealthStatus(
    val providerConfigured: Boolean = false,
    val providerReachable: Boolean = false,
    val state: String? = null,
)
