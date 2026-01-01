package com.scanium.app.regression

import android.util.Log
import com.scanium.app.testing.TestConfigOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume
import java.util.concurrent.TimeUnit

/**
 * Backend health gate for regression tests.
 *
 * Validates that the cloud backend is reachable before running tests that
 * require cloud functionality. Tests are skipped (not failed) if the
 * backend is unreachable.
 *
 * ## Usage in Tests
 * ```kotlin
 * @Before
 * fun setUp() = runBlocking {
 *     BackendHealthGate.requireBackendReachable()
 * }
 * ```
 *
 * Or for individual tests:
 * ```kotlin
 * @Test
 * fun testCloudClassification() = runTest {
 *     BackendHealthGate.checkBackendOrSkip()
 *     // ... test code
 * }
 * ```
 */
object BackendHealthGate {
    private const val TAG = "BackendHealthGate"
    private const val HEALTH_TIMEOUT_MS = 5_000L
    private const val CONNECT_TIMEOUT_SECONDS = 3L
    private const val READ_TIMEOUT_SECONDS = 3L

    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    /**
     * Result of a health check.
     */
    sealed class HealthResult {
        data class Healthy(val latencyMs: Long) : HealthResult()

        data class Unhealthy(val reason: String, val statusCode: Int? = null) : HealthResult()

        data class Unreachable(val error: String) : HealthResult()

        object NotConfigured : HealthResult()
    }

    /**
     * Check backend health.
     *
     * @return HealthResult indicating backend status
     */
    suspend fun checkHealth(): HealthResult =
        withContext(Dispatchers.IO) {
            val baseUrl = TestConfigOverride.getEffectiveBaseUrl()

            if (baseUrl.isBlank()) {
                Log.w(TAG, "Backend URL not configured")
                return@withContext HealthResult.NotConfigured
            }

            val healthUrl = "${baseUrl.trimEnd('/')}/health"
            val startTime = System.currentTimeMillis()

            try {
                val result =
                    withTimeoutOrNull(HEALTH_TIMEOUT_MS) {
                        val request =
                            Request.Builder()
                                .url(healthUrl)
                                .get()
                                .build()

                        httpClient.newCall(request).execute().use { response ->
                            val latency = System.currentTimeMillis() - startTime

                            when {
                                response.isSuccessful -> {
                                    Log.i(TAG, "Backend healthy: $healthUrl (${latency}ms)")
                                    HealthResult.Healthy(latency)
                                }
                                response.code in 401..403 -> {
                                    Log.w(TAG, "Backend auth required: ${response.code}")
                                    HealthResult.Unhealthy("Auth required", response.code)
                                }
                                response.code in 500..599 -> {
                                    Log.e(TAG, "Backend server error: ${response.code}")
                                    HealthResult.Unhealthy("Server error", response.code)
                                }
                                else -> {
                                    Log.w(TAG, "Backend unexpected response: ${response.code}")
                                    HealthResult.Unhealthy("Unexpected response", response.code)
                                }
                            }
                        }
                    }

                result ?: HealthResult.Unreachable("Request timeout (${HEALTH_TIMEOUT_MS}ms)")
            } catch (e: Exception) {
                Log.e(TAG, "Backend unreachable: ${e.message}", e)
                HealthResult.Unreachable(e.message ?: "Connection failed")
            }
        }

    /**
     * Check backend health and skip test if unreachable.
     *
     * Uses Assume.assumeTrue to skip (not fail) the test if backend
     * is not available. This is the recommended approach for regression
     * tests that require cloud backend.
     *
     * @throws org.junit.AssumptionViolatedException if backend not reachable
     */
    suspend fun checkBackendOrSkip() {
        when (val result = checkHealth()) {
            is HealthResult.Healthy -> {
                Log.i(TAG, "Backend ready (${result.latencyMs}ms)")
            }
            is HealthResult.NotConfigured -> {
                Assume.assumeTrue(
                    "Backend not configured: Set SCANIUM_BASE_URL instrumentation arg or BuildConfig",
                    false,
                )
            }
            is HealthResult.Unhealthy -> {
                Assume.assumeTrue(
                    "Backend unhealthy: ${result.reason} (HTTP ${result.statusCode})",
                    false,
                )
            }
            is HealthResult.Unreachable -> {
                Assume.assumeTrue(
                    "Backend not reachable: ${result.error}. " +
                        "Regression suite requires cloud backend. " +
                        "Ensure backend is running and SCANIUM_BASE_URL is set correctly.",
                    false,
                )
            }
        }
    }

    /**
     * Require backend to be reachable or skip all tests.
     * Suitable for @Before or @BeforeClass methods.
     */
    suspend fun requireBackendReachable() {
        checkBackendOrSkip()
    }

    /**
     * Check if backend is configured (has a base URL).
     */
    fun isConfigured(): Boolean {
        return TestConfigOverride.getEffectiveBaseUrl().isNotBlank()
    }

    /**
     * Get effective backend URL for logging/debugging.
     */
    fun getBackendUrl(): String {
        return TestConfigOverride.getEffectiveBaseUrl()
    }
}
