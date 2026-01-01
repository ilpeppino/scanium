package com.scanium.app.regression

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.scanium.app.diagnostics.DiagnosticsRepository
import com.scanium.app.diagnostics.HealthStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TEST 1: Backend reachable + Cloud mode enabled
 *
 * Validates:
 * - Backend health endpoint responds with HTTP 200
 * - Cloud mode is properly configured
 * - App can communicate with backend API
 *
 * Skips if backend is not reachable (regression tests require cloud backend).
 */
@RunWith(AndroidJUnit4::class)
class BackendHealthRegressionTest {
    private lateinit var context: Context
    private lateinit var diagnosticsRepository: DiagnosticsRepository

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            RegressionTestConfig.initialize()
        }
    }

    @Before
    fun setUp() =
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            diagnosticsRepository = DiagnosticsRepository(context)

            // Check backend configured - skip if not
            Assume.assumeTrue(
                "Backend URL not configured: Set SCANIUM_BASE_URL instrumentation arg",
                RegressionTestConfig.isCloudModeAvailable(),
            )

            // Check backend reachable - skip if not
            BackendHealthGate.checkBackendOrSkip()
        }

    @Test
    fun testBackendHealthEndpoint_ReturnsHealthy() =
        runTest {
            // Act
            val healthResult = diagnosticsRepository.checkBackendHealth()

            // Assert
            assertThat(healthResult.status).isEqualTo(HealthStatus.HEALTHY)
            assertThat(healthResult.latencyMs).isNotNull()
            assertThat(healthResult.latencyMs!!).isLessThan(3000L)
        }

    @Test
    fun testCloudModeConfiguration_IsActive() {
        // Act
        val baseUrl = RegressionTestConfig.getBaseUrl()
        val isCloudAvailable = RegressionTestConfig.isCloudModeAvailable()

        // Assert
        assertThat(isCloudAvailable).isTrue()
        assertThat(baseUrl).isNotEmpty()
        assertThat(baseUrl).startsWith("http")
    }

    @Test
    fun testDiagnosticsState_ContainsValidConfig() =
        runTest {
            // Act
            diagnosticsRepository.refreshAll()
            val state = diagnosticsRepository.state.value

            // Assert - backend health
            assertThat(state.backendHealth.status).isEqualTo(HealthStatus.HEALTHY)

            // Assert - app config
            assertThat(state.appConfig).isNotNull()
            assertThat(state.appConfig!!.baseUrl).isNotEqualTo("(not configured)")

            // Assert - network status
            assertThat(state.networkStatus.isConnected).isTrue()
        }

    @Test
    fun testBackendHealthGate_HealthyResult() =
        runTest {
            // Act
            val result = BackendHealthGate.checkHealth()

            // Assert
            assertThat(result).isInstanceOf(BackendHealthGate.HealthResult.Healthy::class.java)

            val healthy = result as BackendHealthGate.HealthResult.Healthy
            assertThat(healthy.latencyMs).isGreaterThan(0L)
            assertThat(healthy.latencyMs).isLessThan(5000L)
        }

    @Test
    fun testBackendUrl_IsConfiguredCorrectly() {
        // Act
        val url = BackendHealthGate.getBackendUrl()

        // Assert
        assertThat(url).isNotEmpty()
        // URL should be a valid HTTP/HTTPS URL
        assertThat(url).containsMatch("^https?://")
    }
}
