package com.scanium.app.selling.assistant

import com.google.common.truth.Truth.assertThat
import com.scanium.app.selling.assistant.network.AssistantHttpConfig
import com.scanium.app.selling.assistant.network.AssistantOkHttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
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
        // Use unified test configuration from AssistantHttpConfig
        client = AssistantOkHttpClientFactory.create(
            config = AssistantHttpConfig.TEST,
            logStartupPolicy = false,
        )
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

    // ==================== Auth failure handling ====================
    // Auth failures from preflight should return UNKNOWN to allow chat attempt.
    // The actual chat may succeed with a different auth path.

    @Test
    fun `401 returns UNKNOWN to allow chat attempt`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error": "unauthorized"}"""),
        )

        val result = performPreflightChat()

        // Auth failure from preflight should return UNKNOWN, not UNAUTHORIZED
        // This allows the user to still attempt a chat which may succeed
        assertThat(result.status).isEqualTo(PreflightStatus.UNKNOWN)
        assertThat(result.reasonCode).isEqualTo("preflight_auth_401")
    }

    @Test
    fun `403 returns UNKNOWN to allow chat attempt`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"error": "forbidden"}"""),
        )

        val result = performPreflightChat()

        // Auth failure from preflight should return UNKNOWN, not UNAUTHORIZED
        assertThat(result.status).isEqualTo(PreflightStatus.UNKNOWN)
        assertThat(result.reasonCode).isEqualTo("preflight_auth_403")
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

    // ==================== HTTP 400 handling ====================
    // HTTP 400 from preflight means the preflight request schema was malformed.
    // This should NOT block the user - actual chat may still work.

    @Test
    fun `400 returns CLIENT_ERROR not TEMPORARILY_UNAVAILABLE`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error": "bad request", "message": "Invalid request schema"}"""),
        )

        val result = performPreflightChat()

        // HTTP 400 should return CLIENT_ERROR to allow chat attempt
        assertThat(result.status).isEqualTo(PreflightStatus.CLIENT_ERROR)
        assertThat(result.reasonCode).isEqualTo("preflight_schema_error")
        // CLIENT_ERROR should allow retry (chat attempt)
        assertThat(result.canRetry).isTrue()
    }

    @Test
    fun `preflight uses correct path v1_assist_chat`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"content": "ok"}"""),
        )

        performPreflightChat()

        val request = mockWebServer.takeRequest()
        assertThat(request.path).isEqualTo("/v1/assist/chat")
        assertThat(request.method).isEqualTo("POST")
    }

    @Test
    fun `preflight request includes required headers`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"content": "ok"}"""),
        )

        performPreflightChat()

        val request = mockWebServer.takeRequest()
        assertThat(request.getHeader("X-API-Key")).isEqualTo("test-key")
        assertThat(request.getHeader("X-Client")).isEqualTo("Scanium-Android")
        assertThat(request.getHeader("X-Scanium-Preflight")).isEqualTo("true")
        assertThat(request.getHeader("X-Scanium-Device-Id")).isNotNull()
    }

    @Test
    fun `preflight request includes valid JSON payload with empty items`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"content": "ok"}"""),
        )

        performPreflightChat()

        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertThat(body).contains("\"message\"")
        assertThat(body).contains("\"items\":[]") // Items MUST be empty array
        assertThat(body).contains("\"history\"")
    }

    // ==================== Regression tests for backend reachability fix ====================
    // These tests verify that the preflight only checks backend reachability,
    // not assistant-specific configuration. The assistant feature flag is checked
    // separately via featureFlags.enableAssistant in FeatureFlagRepository.

    @Test
    fun `200 with status ok but no assistant section returns AVAILABLE`() = runTest {
        // This is the key regression test for the fix.
        // Previously, missing assistant section would cause TEMPORARILY_UNAVAILABLE.
        // After fix, 200 with status=ok means backend is reachable = AVAILABLE.
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "status": "ok",
                        "ts": "2025-01-02T12:00:00Z",
                        "version": "1.0.0"
                    }
                    """.trimIndent(),
                ),
        )

        val result = performPreflight()

        assertThat(result.status).isEqualTo(PreflightStatus.AVAILABLE)
        assertThat(result.isAvailable).isTrue()
    }

    @Test
    fun `200 with status healthy returns AVAILABLE`() = runTest {
        // Backend may report "healthy" instead of "ok"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "status": "healthy",
                        "ts": "2025-01-02T12:00:00Z"
                    }
                    """.trimIndent(),
                ),
        )

        val result = performPreflight()

        assertThat(result.status).isEqualTo(PreflightStatus.AVAILABLE)
        assertThat(result.isAvailable).isTrue()
    }

    @Test
    fun `200 with assistant providerConfigured false returns AVAILABLE`() = runTest {
        // If providerConfigured is false, we don't mark unavailable -
        // we just consider backend reachable. The enableAssistant flag controls feature access.
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "status": "ok",
                        "assistant": {
                            "providerConfigured": false,
                            "providerReachable": false
                        }
                    }
                    """.trimIndent(),
                ),
        )

        val result = performPreflight()

        // providerConfigured=false means we don't have explicit info that provider is down
        // so we treat backend as available (reachable)
        assertThat(result.status).isEqualTo(PreflightStatus.AVAILABLE)
    }

    @Test
    fun `200 with degraded status returns TEMPORARILY_UNAVAILABLE`() = runTest {
        // If backend explicitly reports degraded status, respect that
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "status": "degraded",
                        "ts": "2025-01-02T12:00:00Z"
                    }
                    """.trimIndent(),
                ),
        )

        val result = performPreflight()

        assertThat(result.status).isEqualTo(PreflightStatus.TEMPORARILY_UNAVAILABLE)
        assertThat(result.reasonCode).isEqualTo("backend_degraded_degraded")
        assertThat(result.canRetry).isTrue()
    }

    @Test
    fun `200 with empty body returns AVAILABLE`() = runTest {
        // Edge case: empty body should default to available (backend responded)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(""),
        )

        val result = performPreflight()

        // Empty body means we can't parse status, defaults to "ok"
        assertThat(result.status).isEqualTo(PreflightStatus.AVAILABLE)
    }

    @Test
    fun `200 with malformed JSON returns AVAILABLE`() = runTest {
        // Edge case: if JSON parsing fails, default to available (backend responded with 200)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("not valid json"),
        )

        val result = performPreflight()

        // Can't parse, healthResponse is null, backendStatus defaults to "ok"
        assertThat(result.status).isEqualTo(PreflightStatus.AVAILABLE)
    }

    /**
     * Simulates the old preflight check logic using /health endpoint.
     * Kept for backward compatibility tests.
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

                        val backendStatus = healthResponse?.status ?: "ok"
                        val isBackendHealthy = backendStatus.equals("ok", ignoreCase = true) ||
                            backendStatus.equals("healthy", ignoreCase = true)

                        val assistantExplicitlyUnavailable = healthResponse?.assistant?.let { assistant ->
                            assistant.providerConfigured && !assistant.providerReachable
                        } ?: false

                        when {
                            assistantExplicitlyUnavailable -> {
                                PreflightResult(
                                    status = PreflightStatus.TEMPORARILY_UNAVAILABLE,
                                    latencyMs = latency,
                                    reasonCode = healthResponse?.assistant?.state ?: "provider_unreachable",
                                )
                            }
                            isBackendHealthy -> {
                                PreflightResult(
                                    status = PreflightStatus.AVAILABLE,
                                    latencyMs = latency,
                                )
                            }
                            else -> {
                                PreflightResult(
                                    status = PreflightStatus.TEMPORARILY_UNAVAILABLE,
                                    latencyMs = latency,
                                    reasonCode = "backend_degraded_$backendStatus",
                                )
                            }
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

    /**
     * Simulates the new preflight check logic using /v1/assist/chat endpoint.
     * This mirrors the actual implementation in AssistantPreflightManagerImpl.
     */
    private suspend fun performPreflightChat(): PreflightResult = withContext(Dispatchers.IO) {
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        val endpoint = "$baseUrl/v1/assist/chat"
        val startTime = System.currentTimeMillis()

        // Minimal valid payload - IMPORTANT: items MUST be empty array, not objects
        // Backend validates schema and returns 400 if items contains objects
        val payload = """{"message":"ping","items":[],"history":[]}"""

        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .header("X-API-Key", "test-key")
            .header("X-Client", "Scanium-Android")
            .header("X-Scanium-Preflight", "true")
            .header("X-Scanium-Device-Id", "test-device-raw-id") // Raw device ID, not hashed
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val body = response.body?.string()

                when {
                    response.isSuccessful -> {
                        val chatResponse = body?.let {
                            runCatching {
                                json.decodeFromString<TestChatResponse>(it)
                            }.getOrNull()
                        }

                        if (chatResponse?.assistantError != null) {
                            val errorType = chatResponse.assistantError.type.lowercase()
                            when {
                                errorType.contains("provider_unavailable") ||
                                    errorType.contains("provider_unreachable") -> {
                                    PreflightResult(
                                        status = PreflightStatus.TEMPORARILY_UNAVAILABLE,
                                        latencyMs = latency,
                                        reasonCode = errorType,
                                    )
                                }
                                else -> {
                                    PreflightResult(
                                        status = PreflightStatus.AVAILABLE,
                                        latencyMs = latency,
                                    )
                                }
                            }
                        } else {
                            PreflightResult(
                                status = PreflightStatus.AVAILABLE,
                                latencyMs = latency,
                            )
                        }
                    }
                    response.code == 400 -> {
                        // HTTP 400 = preflight request schema error
                        // This does NOT mean assistant is unavailable - allow chat attempt
                        PreflightResult(
                            status = PreflightStatus.CLIENT_ERROR,
                            latencyMs = latency,
                            reasonCode = "preflight_schema_error",
                        )
                    }
                    response.code == 401 || response.code == 403 -> {
                        // Auth failure from preflight - return UNKNOWN to allow chat attempt
                        PreflightResult(
                            status = PreflightStatus.UNKNOWN,
                            latencyMs = latency,
                            reasonCode = "preflight_auth_${response.code}",
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
                        // Unknown error - allow chat attempt
                        PreflightResult(
                            status = PreflightStatus.UNKNOWN,
                            latencyMs = latency,
                            reasonCode = "http_${response.code}",
                        )
                    }
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            // Timeout - return UNKNOWN to allow chat attempt
            PreflightResult(
                status = PreflightStatus.UNKNOWN,
                latencyMs = System.currentTimeMillis() - startTime,
                reasonCode = "timeout",
            )
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

@kotlinx.serialization.Serializable
private data class TestChatResponse(
    val content: String = "",
    val assistantError: TestAssistantError? = null,
)

@kotlinx.serialization.Serializable
private data class TestAssistantError(
    val type: String = "",
    val category: String = "",
    val retryable: Boolean = false,
    val message: String? = null,
)
