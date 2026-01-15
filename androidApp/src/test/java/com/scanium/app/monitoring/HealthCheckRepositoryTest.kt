package com.scanium.app.monitoring

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for HealthCheckRepository.
 * Tests endpoint validation rules using MockWebServer.
 *
 * Note: The repository checks all endpoints in parallel, so we use a Dispatcher
 * to return appropriate responses based on the request path rather than relying
 * on enqueue order.
 */
@RunWith(RobolectricTestRunner::class)
class HealthCheckRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: HealthCheckRepository

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        repository = HealthCheckRepository()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    private fun baseUrl(): String = mockWebServer.url("/").toString().trimEnd('/')

    /**
     * Helper to set up a dispatcher that returns specific responses per endpoint.
     */
    private fun setDispatcher(
        healthCode: Int = 200,
        configCode: Int = 200,
        warmupCode: Int = 200,
    ) {
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/health" -> MockResponse().setResponseCode(healthCode)
                    "/v1/config" -> MockResponse().setResponseCode(configCode)
                    "/v1/assist/warmup" -> MockResponse().setResponseCode(warmupCode)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    // =========================================================================
    // /health endpoint tests
    // =========================================================================

    @Test
    fun `health 200 passes`() = runTest {
        setDispatcher(healthCode = 200, configCode = 200, warmupCode = 200)

        val config = HealthMonitorConfig(baseUrl = baseUrl(), apiKey = null, notifyOnRecovery = true)
        val result = repository.performHealthCheck(config)

        assertThat(result.status).isEqualTo(MonitorHealthStatus.OK)
        assertThat(result.failures).isEmpty()
    }

    @Test
    fun `health 500 fails`() = runTest {
        setDispatcher(healthCode = 500, configCode = 200, warmupCode = 200)

        val config = HealthMonitorConfig(baseUrl = baseUrl(), apiKey = null, notifyOnRecovery = true)
        val result = repository.performHealthCheck(config)

        assertThat(result.status).isEqualTo(MonitorHealthStatus.FAIL)
        assertThat(result.failures).hasSize(1)
        assertThat(result.failures.first().endpoint).isEqualTo("/health")
    }

    // =========================================================================
    // /v1/config endpoint tests - WITH API key
    // =========================================================================

    @Test
    fun `config 200 with API key passes`() = runTest {
        setDispatcher(healthCode = 200, configCode = 200, warmupCode = 200)

        val config = HealthMonitorConfig(baseUrl = baseUrl(), apiKey = "test-api-key", notifyOnRecovery = true)
        val result = repository.performHealthCheck(config)

        assertThat(result.status).isEqualTo(MonitorHealthStatus.OK)
    }

    @Test
    fun `config 401 with API key fails`() = runTest {
        setDispatcher(healthCode = 200, configCode = 401, warmupCode = 200)

        val config = HealthMonitorConfig(baseUrl = baseUrl(), apiKey = "test-api-key", notifyOnRecovery = true)
        val result = repository.performHealthCheck(config)

        assertThat(result.status).isEqualTo(MonitorHealthStatus.FAIL)
        assertThat(result.failures.any { it.endpoint == "/v1/config" }).isTrue()
    }

    // =========================================================================
    // /v1/config endpoint tests - WITHOUT API key
    // =========================================================================

    @Test
    fun `config 401 without API key passes - endpoint is reachable`() = runTest {
        setDispatcher(healthCode = 200, configCode = 401, warmupCode = 401)

        val config = HealthMonitorConfig(baseUrl = baseUrl(), apiKey = null, notifyOnRecovery = true)
        val result = repository.performHealthCheck(config)

        assertThat(result.status).isEqualTo(MonitorHealthStatus.OK)
        assertThat(result.failures).isEmpty()
    }

    @Test
    fun `config 200 without API key passes`() = runTest {
        setDispatcher(healthCode = 200, configCode = 200, warmupCode = 200)

        val config = HealthMonitorConfig(baseUrl = baseUrl(), apiKey = null, notifyOnRecovery = true)
        val result = repository.performHealthCheck(config)

        assertThat(result.status).isEqualTo(MonitorHealthStatus.OK)
    }

    // =========================================================================
    // /v1/assist/warmup endpoint tests
    // =========================================================================

    @Test
    fun `warmup 401 without API key passes`() = runTest {
        setDispatcher(healthCode = 200, configCode = 200, warmupCode = 401)

        val config = HealthMonitorConfig(baseUrl = baseUrl(), apiKey = null, notifyOnRecovery = true)
        val result = repository.performHealthCheck(config)

        assertThat(result.status).isEqualTo(MonitorHealthStatus.OK)
    }

    @Test
    fun `warmup 403 without API key passes`() = runTest {
        setDispatcher(healthCode = 200, configCode = 200, warmupCode = 403)

        val config = HealthMonitorConfig(baseUrl = baseUrl(), apiKey = null, notifyOnRecovery = true)
        val result = repository.performHealthCheck(config)

        assertThat(result.status).isEqualTo(MonitorHealthStatus.OK)
    }

    @Test
    fun `warmup 500 fails`() = runTest {
        setDispatcher(healthCode = 200, configCode = 200, warmupCode = 500)

        val config = HealthMonitorConfig(baseUrl = baseUrl(), apiKey = null, notifyOnRecovery = true)
        val result = repository.performHealthCheck(config)

        assertThat(result.status).isEqualTo(MonitorHealthStatus.FAIL)
        assertThat(result.failures.any { it.endpoint == "/v1/assist/warmup" }).isTrue()
    }

    // =========================================================================
    // /v1/assist/warmup method validation
    // =========================================================================

    @Test
    fun `warmup uses POST`() = runTest {
        setDispatcher(healthCode = 200, configCode = 200, warmupCode = 200)

        val config = HealthMonitorConfig(baseUrl = baseUrl(), apiKey = "test-key", notifyOnRecovery = true)
        repository.performHealthCheck(config)

        val requests = (0 until mockWebServer.requestCount).map { mockWebServer.takeRequest() }
        val warmupRequest = requests.first { it.path == "/v1/assist/warmup" }
        assertThat(warmupRequest.method).isEqualTo("POST")
    }

    // =========================================================================
    // Failure signature tests
    // =========================================================================

    @Test
    fun `failure signature format is correct`() = runTest {
        setDispatcher(healthCode = 500, configCode = 401, warmupCode = 200)

        val config = HealthMonitorConfig(baseUrl = baseUrl(), apiKey = "test-key", notifyOnRecovery = true)
        val result = repository.performHealthCheck(config)

        // Signature should be sorted and formatted correctly
        assertThat(result.failureSignature).contains("health")
        assertThat(result.failureSignature).contains("config")
    }

    @Test
    fun `OK result has empty failure signature`() = runTest {
        setDispatcher(healthCode = 200, configCode = 200, warmupCode = 200)

        val config = HealthMonitorConfig(baseUrl = baseUrl(), apiKey = null, notifyOnRecovery = true)
        val result = repository.performHealthCheck(config)

        assertThat(result.failureSignature).isEmpty()
    }
}
