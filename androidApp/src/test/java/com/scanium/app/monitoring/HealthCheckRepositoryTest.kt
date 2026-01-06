package com.scanium.app.monitoring

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for HealthCheckRepository.
 * Tests endpoint validation rules using MockWebServer.
 */
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

    // =========================================================================
    // /health endpoint tests
    // =========================================================================

    @Test
    fun `health 200 passes`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // config
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // preflight
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // assist/status

        val config = HealthMonitorConfig(baseUrl(), null, true)
        val result = repository.performHealthCheck(config)

        assertThat(result.status).isEqualTo(MonitorHealthStatus.OK)
        assertThat(result.failures).isEmpty()
    }

    @Test
    fun `health 500 fails`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500)) // health fails
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // config
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // preflight
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // assist/status

        val config = HealthMonitorConfig(baseUrl(), null, true)
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
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // health
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // config - OK with key
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // preflight
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // assist/status

        val config = HealthMonitorConfig(baseUrl(), "test-api-key", true)
        val result = repository.performHealthCheck(config)

        assertThat(result.status).isEqualTo(MonitorHealthStatus.OK)
    }

    @Test
    fun `config 401 with API key fails`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // health
        mockWebServer.enqueue(MockResponse().setResponseCode(401)) // config - 401 fails with key
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // preflight
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // assist/status

        val config = HealthMonitorConfig(baseUrl(), "test-api-key", true)
        val result = repository.performHealthCheck(config)

        assertThat(result.status).isEqualTo(MonitorHealthStatus.FAIL)
        assertThat(result.failures.any { it.endpoint == "/v1/config" }).isTrue()
    }

    // =========================================================================
    // /v1/config endpoint tests - WITHOUT API key
    // =========================================================================

    @Test
    fun `config 401 without API key passes - endpoint is reachable`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // health
        mockWebServer.enqueue(MockResponse().setResponseCode(401)) // config - 401 OK without key
        mockWebServer.enqueue(MockResponse().setResponseCode(401)) // preflight - 401 OK without key
        mockWebServer.enqueue(MockResponse().setResponseCode(403)) // assist/status - 403 OK

        val config = HealthMonitorConfig(baseUrl(), null, true)
        val result = repository.performHealthCheck(config)

        assertThat(result.status).isEqualTo(MonitorHealthStatus.OK)
        assertThat(result.failures).isEmpty()
    }

    @Test
    fun `config 200 without API key passes`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // health
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // config
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // preflight
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // assist/status

        val config = HealthMonitorConfig(baseUrl(), null, true)
        val result = repository.performHealthCheck(config)

        assertThat(result.status).isEqualTo(MonitorHealthStatus.OK)
    }

    // =========================================================================
    // /v1/preflight endpoint tests
    // =========================================================================

    @Test
    fun `preflight 401 without API key passes`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // health
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // config
        mockWebServer.enqueue(MockResponse().setResponseCode(401)) // preflight - 401 OK without key
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // assist/status

        val config = HealthMonitorConfig(baseUrl(), null, true)
        val result = repository.performHealthCheck(config)

        assertThat(result.status).isEqualTo(MonitorHealthStatus.OK)
    }

    @Test
    fun `preflight 500 fails`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // health
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // config
        mockWebServer.enqueue(MockResponse().setResponseCode(500)) // preflight - server error
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // assist/status

        val config = HealthMonitorConfig(baseUrl(), null, true)
        val result = repository.performHealthCheck(config)

        assertThat(result.status).isEqualTo(MonitorHealthStatus.FAIL)
        assertThat(result.failures.any { it.endpoint == "/v1/preflight" }).isTrue()
    }

    // =========================================================================
    // /v1/assist/status endpoint tests
    // =========================================================================

    @Test
    fun `assist-status 403 accepted - protected but reachable`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // health
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // config
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // preflight
        mockWebServer.enqueue(MockResponse().setResponseCode(403)) // assist/status - 403 OK

        val config = HealthMonitorConfig(baseUrl(), null, true)
        val result = repository.performHealthCheck(config)

        assertThat(result.status).isEqualTo(MonitorHealthStatus.OK)
    }

    @Test
    fun `assist-status 200 passes`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // health
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // config
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // preflight
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // assist/status

        val config = HealthMonitorConfig(baseUrl(), "test-key", true)
        val result = repository.performHealthCheck(config)

        assertThat(result.status).isEqualTo(MonitorHealthStatus.OK)
    }

    @Test
    fun `assist-status 500 fails`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // health
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // config
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // preflight
        mockWebServer.enqueue(MockResponse().setResponseCode(500)) // assist/status

        val config = HealthMonitorConfig(baseUrl(), null, true)
        val result = repository.performHealthCheck(config)

        assertThat(result.status).isEqualTo(MonitorHealthStatus.FAIL)
        assertThat(result.failures.any { it.endpoint == "/v1/assist/status" }).isTrue()
    }

    // =========================================================================
    // Failure signature tests
    // =========================================================================

    @Test
    fun `failure signature format is correct`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500)) // health
        mockWebServer.enqueue(MockResponse().setResponseCode(401)) // config
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // preflight
        mockWebServer.enqueue(MockResponse().setResponseCode(200)) // assist/status

        val config = HealthMonitorConfig(baseUrl(), "test-key", true)
        val result = repository.performHealthCheck(config)

        // Signature should be sorted and formatted correctly
        assertThat(result.failureSignature).contains("health")
        assertThat(result.failureSignature).contains("config")
    }

    @Test
    fun `OK result has empty failure signature`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val config = HealthMonitorConfig(baseUrl(), null, true)
        val result = repository.performHealthCheck(config)

        assertThat(result.failureSignature).isEmpty()
    }
}
