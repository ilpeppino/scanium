package com.scanium.app.selling.assistant

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantPreflightManagerTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun preflightResult_initialState_isUnknown() = runTest {
        val manager = TestablePreflightManager()

        assertThat(manager.currentResult.value.status).isEqualTo(PreflightStatus.UNKNOWN)
    }

    @Test
    fun preflight_whenAvailable_returnsAvailable() = runTest {
        val manager = TestablePreflightManager()
        manager.setMockResult(PreflightResult(PreflightStatus.AVAILABLE, 50))

        val result = manager.preflight(forceRefresh = true)

        assertThat(result.status).isEqualTo(PreflightStatus.AVAILABLE)
        assertThat(result.isAvailable).isTrue()
    }

    @Test
    fun preflight_cachesTTL_returnsCachedWithin30s() = runTest {
        val manager = TestablePreflightManager()
        val initialResult = PreflightResult(
            status = PreflightStatus.AVAILABLE,
            latencyMs = 50,
            checkedAt = System.currentTimeMillis(),
        )
        manager.setMockResult(initialResult)

        // First call should make a request
        val first = manager.preflight(forceRefresh = true)
        assertThat(first.status).isEqualTo(PreflightStatus.AVAILABLE)
        assertThat(manager.requestCount).isEqualTo(1)

        // Second call within 30s should return cached
        val second = manager.preflight(forceRefresh = false)
        assertThat(second.status).isEqualTo(PreflightStatus.AVAILABLE)
        // Should still be 1 request since cache was used
        assertThat(manager.requestCount).isEqualTo(1)
    }

    @Test
    fun preflight_forceRefresh_ignoresCache() = runTest {
        val manager = TestablePreflightManager()
        val initialResult = PreflightResult(
            status = PreflightStatus.AVAILABLE,
            latencyMs = 50,
            checkedAt = System.currentTimeMillis(),
        )
        manager.setMockResult(initialResult)

        // First call
        manager.preflight(forceRefresh = true)
        assertThat(manager.requestCount).isEqualTo(1)

        // Force refresh should make another request even within TTL
        manager.preflight(forceRefresh = true)
        assertThat(manager.requestCount).isEqualTo(2)
    }

    @Test
    fun preflight_whenUnauthorized_returnsUnauthorized() = runTest {
        val manager = TestablePreflightManager()
        manager.setMockResult(PreflightResult(PreflightStatus.UNAUTHORIZED, 30, reasonCode = "http_401"))

        val result = manager.preflight(forceRefresh = true)

        assertThat(result.status).isEqualTo(PreflightStatus.UNAUTHORIZED)
        assertThat(result.isAvailable).isFalse()
        assertThat(result.canRetry).isFalse()
    }

    @Test
    fun preflight_whenRateLimited_returnsRateLimited() = runTest {
        val manager = TestablePreflightManager()
        manager.setMockResult(
            PreflightResult(
                status = PreflightStatus.RATE_LIMITED,
                latencyMs = 30,
                reasonCode = "http_429",
                retryAfterSeconds = 60,
            ),
        )

        val result = manager.preflight(forceRefresh = true)

        assertThat(result.status).isEqualTo(PreflightStatus.RATE_LIMITED)
        assertThat(result.canRetry).isTrue()
        assertThat(result.retryAfterSeconds).isEqualTo(60)
    }

    @Test
    fun preflight_whenOffline_returnsOffline() = runTest {
        val manager = TestablePreflightManager()
        manager.setMockResult(
            PreflightResult(
                status = PreflightStatus.OFFLINE,
                latencyMs = 0,
                reasonCode = "dns_failure",
            ),
        )

        val result = manager.preflight(forceRefresh = true)

        assertThat(result.status).isEqualTo(PreflightStatus.OFFLINE)
        assertThat(result.isAvailable).isFalse()
        assertThat(result.canRetry).isTrue()
    }

    @Test
    fun warmUp_whenAvailable_initiatesWarmup() = runTest {
        val manager = TestablePreflightManager()
        manager.setMockResult(PreflightResult(PreflightStatus.AVAILABLE, 50))

        // Must preflight first to set Available state
        manager.preflight(forceRefresh = true)

        val initiated = manager.warmUp()

        assertThat(initiated).isTrue()
        assertThat(manager.warmUpCount).isEqualTo(1)
    }

    @Test
    fun warmUp_whenUnavailable_skipsWarmup() = runTest {
        val manager = TestablePreflightManager()
        manager.setMockResult(PreflightResult(PreflightStatus.OFFLINE, 0))

        // Preflight returns offline
        manager.preflight(forceRefresh = true)

        val initiated = manager.warmUp()

        assertThat(initiated).isFalse()
        assertThat(manager.warmUpCount).isEqualTo(0)
    }

    @Test
    fun warmUp_rateLimited_skipsSecondWarmupWithin10min() = runTest {
        val manager = TestablePreflightManager()
        manager.setMockResult(PreflightResult(PreflightStatus.AVAILABLE, 50))
        manager.preflight(forceRefresh = true)

        // First warmup should succeed
        val first = manager.warmUp()
        assertThat(first).isTrue()
        assertThat(manager.warmUpCount).isEqualTo(1)

        // Second warmup within 10 minutes should be rate limited
        val second = manager.warmUp()
        assertThat(second).isFalse()
        assertThat(manager.warmUpCount).isEqualTo(1) // Still 1
    }

    @Test
    fun cancelWarmUp_stopsPendingWarmup() = runTest {
        val manager = TestablePreflightManager()
        manager.setMockResult(PreflightResult(PreflightStatus.AVAILABLE, 50))
        manager.preflight(forceRefresh = true)

        manager.warmUp()
        manager.cancelWarmUp()

        assertThat(manager.warmUpCancelled).isTrue()
    }

    @Test
    fun clearCache_resetsToUnknown() = runTest {
        val manager = TestablePreflightManager()
        manager.setMockResult(PreflightResult(PreflightStatus.AVAILABLE, 50))
        manager.preflight(forceRefresh = true)

        assertThat(manager.currentResult.value.status).isEqualTo(PreflightStatus.AVAILABLE)

        manager.clearCache()

        assertThat(manager.currentResult.value.status).isEqualTo(PreflightStatus.UNKNOWN)
    }

    @Test
    fun preflightResult_latencyMs_isTracked() = runTest {
        val manager = TestablePreflightManager()
        manager.setMockResult(PreflightResult(PreflightStatus.AVAILABLE, 150))

        val result = manager.preflight(forceRefresh = true)

        assertThat(result.latencyMs).isEqualTo(150)
    }

    @Test
    fun preflightResult_checkedAt_isPopulated() = runTest {
        val manager = TestablePreflightManager()
        val now = System.currentTimeMillis()
        manager.setMockResult(PreflightResult(PreflightStatus.AVAILABLE, 50, checkedAt = now))

        val result = manager.preflight(forceRefresh = true)

        assertThat(result.checkedAt).isAtLeast(now)
    }

    /**
     * Testable implementation of AssistantPreflightManager that doesn't make network calls.
     */
    private class TestablePreflightManager : AssistantPreflightManager {
        private val _currentResult = MutableStateFlow(
            PreflightResult(PreflightStatus.UNKNOWN, 0),
        )
        override val currentResult: StateFlow<PreflightResult> = _currentResult

        override val lastStatusFlow: Flow<PreflightResult?> = flowOf(null)

        private var mockResult = PreflightResult(PreflightStatus.AVAILABLE, 50)
        var requestCount = 0
            private set
        var warmUpCount = 0
            private set
        var warmUpCancelled = false
            private set
        private var lastWarmUpTime = 0L

        private val cacheTtlMs = 30_000L
        private val warmUpIntervalMs = 600_000L // 10 minutes

        fun setMockResult(result: PreflightResult) {
            mockResult = result
        }

        override suspend fun preflight(forceRefresh: Boolean): PreflightResult {
            val cached = _currentResult.value
            val now = System.currentTimeMillis()
            val cacheAge = now - cached.checkedAt

            if (!forceRefresh && cached.status != PreflightStatus.UNKNOWN && cacheAge < cacheTtlMs) {
                return cached
            }

            requestCount++
            val result = mockResult.copy(checkedAt = now)
            _currentResult.value = result
            return result
        }

        override suspend fun warmUp(): Boolean {
            if (!_currentResult.value.isAvailable) {
                return false
            }

            val now = System.currentTimeMillis()
            if (now - lastWarmUpTime < warmUpIntervalMs && lastWarmUpTime > 0) {
                return false
            }

            warmUpCount++
            lastWarmUpTime = now
            return true
        }

        override fun cancelWarmUp() {
            warmUpCancelled = true
        }

        override suspend fun clearCache() {
            _currentResult.value = PreflightResult(PreflightStatus.UNKNOWN, 0)
        }
    }
}
