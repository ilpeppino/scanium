package com.scanium.app.selling.assistant.network

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Unit tests for AssistantRetryInterceptor.
 * Verifies retry behavior for different error conditions.
 */
@RunWith(RobolectricTestRunner::class)
class AssistantRetryInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun createClient(maxRetries: Int = 1, retryDelayMs: Long = 10L): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(AssistantRetryInterceptor(maxRetries, retryDelayMs))
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .build()
    }

    private fun createRequest(): Request {
        return Request.Builder()
            .url(mockWebServer.url("/test"))
            .get()
            .build()
    }

    // ==================== Transient Error Retry Tests ====================

    @Test
    fun `retries on 502 Bad Gateway and succeeds`() {
        client = createClient(maxRetries = 1)

        // First request returns 502, second succeeds
        mockWebServer.enqueue(MockResponse().setResponseCode(502))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("success"))

        val response = client.newCall(createRequest()).execute()

        assertThat(response.code).isEqualTo(200)
        assertThat(response.body?.string()).isEqualTo("success")
        assertThat(mockWebServer.requestCount).isEqualTo(2)
    }

    @Test
    fun `retries on 503 Service Unavailable and succeeds`() {
        client = createClient(maxRetries = 1)

        mockWebServer.enqueue(MockResponse().setResponseCode(503))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("success"))

        val response = client.newCall(createRequest()).execute()

        assertThat(response.code).isEqualTo(200)
        assertThat(mockWebServer.requestCount).isEqualTo(2)
    }

    @Test
    fun `retries on 504 Gateway Timeout and succeeds`() {
        client = createClient(maxRetries = 1)

        mockWebServer.enqueue(MockResponse().setResponseCode(504))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("success"))

        val response = client.newCall(createRequest()).execute()

        assertThat(response.code).isEqualTo(200)
        assertThat(mockWebServer.requestCount).isEqualTo(2)
    }

    @Test
    fun `returns error response after max retries exhausted`() {
        client = createClient(maxRetries = 2)

        // All three attempts fail with 503
        mockWebServer.enqueue(MockResponse().setResponseCode(503))
        mockWebServer.enqueue(MockResponse().setResponseCode(503))
        mockWebServer.enqueue(MockResponse().setResponseCode(503))

        val response = client.newCall(createRequest()).execute()

        // Should return the last error after exhausting retries
        assertThat(response.code).isEqualTo(503)
        assertThat(mockWebServer.requestCount).isEqualTo(3) // 1 + 2 retries
    }

    // ==================== Non-Retryable Error Tests ====================

    @Test
    fun `does NOT retry on 400 Bad Request`() {
        client = createClient(maxRetries = 1)

        mockWebServer.enqueue(MockResponse().setResponseCode(400).setBody("bad request"))

        val response = client.newCall(createRequest()).execute()

        assertThat(response.code).isEqualTo(400)
        assertThat(mockWebServer.requestCount).isEqualTo(1) // No retry
    }

    @Test
    fun `does NOT retry on 401 Unauthorized`() {
        client = createClient(maxRetries = 1)

        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))

        val response = client.newCall(createRequest()).execute()

        assertThat(response.code).isEqualTo(401)
        assertThat(mockWebServer.requestCount).isEqualTo(1) // No retry
    }

    @Test
    fun `does NOT retry on 403 Forbidden`() {
        client = createClient(maxRetries = 1)

        mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("forbidden"))

        val response = client.newCall(createRequest()).execute()

        assertThat(response.code).isEqualTo(403)
        assertThat(mockWebServer.requestCount).isEqualTo(1) // No retry
    }

    @Test
    fun `does NOT retry on 404 Not Found`() {
        client = createClient(maxRetries = 1)

        mockWebServer.enqueue(MockResponse().setResponseCode(404).setBody("not found"))

        val response = client.newCall(createRequest()).execute()

        assertThat(response.code).isEqualTo(404)
        assertThat(mockWebServer.requestCount).isEqualTo(1) // No retry
    }

    @Test
    fun `does NOT retry on 429 Rate Limited`() {
        client = createClient(maxRetries = 1)

        // 429 should NOT be retried - respect rate limits
        mockWebServer.enqueue(MockResponse().setResponseCode(429).setBody("rate limited"))

        val response = client.newCall(createRequest()).execute()

        assertThat(response.code).isEqualTo(429)
        assertThat(mockWebServer.requestCount).isEqualTo(1) // No retry
    }

    // ==================== Success Cases ====================

    @Test
    fun `success on first try does not trigger retry logic`() {
        client = createClient(maxRetries = 1)

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("immediate success"))

        val response = client.newCall(createRequest()).execute()

        assertThat(response.code).isEqualTo(200)
        assertThat(response.body?.string()).isEqualTo("immediate success")
        assertThat(mockWebServer.requestCount).isEqualTo(1)
    }

    @Test
    fun `success with custom 2xx status codes is not retried`() {
        client = createClient(maxRetries = 1)

        mockWebServer.enqueue(MockResponse().setResponseCode(201).setBody("created"))

        val response = client.newCall(createRequest()).execute()

        assertThat(response.code).isEqualTo(201)
        assertThat(mockWebServer.requestCount).isEqualTo(1)
    }

    // ==================== Retry Count Configuration Tests ====================

    @Test
    fun `zero retries means no retry attempts`() {
        client = createClient(maxRetries = 0)

        mockWebServer.enqueue(MockResponse().setResponseCode(503))

        val response = client.newCall(createRequest()).execute()

        assertThat(response.code).isEqualTo(503)
        assertThat(mockWebServer.requestCount).isEqualTo(1)
    }

    @Test
    fun `multiple retries are attempted up to max`() {
        client = createClient(maxRetries = 3)

        // First three fail, fourth succeeds
        mockWebServer.enqueue(MockResponse().setResponseCode(502))
        mockWebServer.enqueue(MockResponse().setResponseCode(503))
        mockWebServer.enqueue(MockResponse().setResponseCode(504))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("success"))

        val response = client.newCall(createRequest()).execute()

        assertThat(response.code).isEqualTo(200)
        assertThat(mockWebServer.requestCount).isEqualTo(4) // 1 + 3 retries
    }

    // ==================== Timeout Retry Tests ====================

    @Test
    fun `retries on socket timeout and succeeds`() {
        client = createClient(maxRetries = 1, retryDelayMs = 10L)

        // First request times out, second succeeds
        mockWebServer.enqueue(
            MockResponse()
                .setSocketPolicy(SocketPolicy.NO_RESPONSE)
        )
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("success"))

        val response = client.newCall(createRequest()).execute()

        assertThat(response.code).isEqualTo(200)
        assertThat(mockWebServer.requestCount).isEqualTo(2)
    }

    @Test
    fun `throws IOException after timeout retries exhausted`() {
        client = createClient(maxRetries = 1, retryDelayMs = 10L)

        // Both requests timeout
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        try {
            client.newCall(createRequest()).execute()
            assertThat(false).isTrue() // Should not reach here
        } catch (e: IOException) {
            // Expected
            assertThat(mockWebServer.requestCount).isEqualTo(2)
        }
    }

    // ==================== Connection Error Tests ====================

    @Test
    fun `retries on connection disconnect and succeeds`() {
        client = createClient(maxRetries = 1)

        // First request disconnects, second succeeds
        mockWebServer.enqueue(
            MockResponse()
                .setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        )
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("success"))

        val response = client.newCall(createRequest()).execute()

        assertThat(response.code).isEqualTo(200)
        assertThat(mockWebServer.requestCount).isEqualTo(2)
    }

    // ==================== Mixed Scenario Tests ====================

    @Test
    fun `handles sequence of different transient errors`() {
        client = createClient(maxRetries = 3)

        // Different errors, then success
        mockWebServer.enqueue(MockResponse().setResponseCode(502))
        mockWebServer.enqueue(MockResponse().setResponseCode(503))
        mockWebServer.enqueue(MockResponse().setResponseCode(504))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("finally"))

        val response = client.newCall(createRequest()).execute()

        assertThat(response.code).isEqualTo(200)
        assertThat(response.body?.string()).isEqualTo("finally")
        assertThat(mockWebServer.requestCount).isEqualTo(4)
    }

    @Test
    fun `non-retryable error in retry sequence stops retrying`() {
        client = createClient(maxRetries = 3)

        // Transient error, then non-retryable error
        mockWebServer.enqueue(MockResponse().setResponseCode(502))
        mockWebServer.enqueue(MockResponse().setResponseCode(401)) // Non-retryable

        val response = client.newCall(createRequest()).execute()

        assertThat(response.code).isEqualTo(401)
        assertThat(mockWebServer.requestCount).isEqualTo(2) // Stopped at 401
    }
}
