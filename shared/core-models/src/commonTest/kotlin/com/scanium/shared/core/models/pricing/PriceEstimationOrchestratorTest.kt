package com.scanium.shared.core.models.pricing

import com.scanium.shared.core.models.pricing.providers.MockPriceEstimatorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class CountingProvider : PriceEstimatorProvider {
    override val id: String = "counting"
    override val capabilities: PriceEstimatorCapabilities = PriceEstimatorCapabilities()
    var callCount = 0
    var nextRange: PriceRange = PriceRange(Money(1.0), Money(2.0))

    override suspend fun estimate(request: PriceEstimationRequest): PriceRange {
        callCount++
        return nextRange
    }
}

private class SlowProvider : PriceEstimatorProvider {
    override val id: String = "slow"
    override val capabilities: PriceEstimatorCapabilities = PriceEstimatorCapabilities()
    override suspend fun estimate(request: PriceEstimationRequest): PriceRange {
        delay(5_000)
        return PriceRange(Money(1.0), Money(2.0))
    }
}

class PriceEstimationOrchestratorTest {
    private fun TestScope.newOrchestrator(provider: PriceEstimatorProvider): PriceEstimationOrchestrator {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        return PriceEstimationOrchestrator(provider, scope, timeoutMs = 2_000)
    }

    @Test
    fun `dedup prevents duplicate provider calls for same request`() = runTest {
        val provider = CountingProvider()
        val orchestrator = newOrchestrator(provider)
        val request = PriceEstimationRequest(itemId = "1", categoryId = "FASHION")

        orchestrator.startEstimation(request)
        orchestrator.startEstimation(request)

        testScheduler.advanceUntilIdle()

        assertEquals(1, provider.callCount)
        assertIs<PriceEstimationStatus.Ready>(orchestrator.getStatusFlow("1").value)
    }

    @Test
    fun `status transitions reach Ready`() = runTest {
        val provider = CountingProvider()
        provider.nextRange = PriceRange(Money(10.0), Money(20.0))
        val orchestrator = newOrchestrator(provider)
        val request = PriceEstimationRequest(itemId = "abc", categoryId = "HOME_GOOD")

        orchestrator.startEstimation(request)
        assertIs<PriceEstimationStatus.Estimating>(orchestrator.getStatusFlow("abc").value)

        testScheduler.advanceUntilIdle()
        val status = orchestrator.getStatusFlow("abc").value
        assertIs<PriceEstimationStatus.Ready>(status)
        assertEquals(10.0, status.priceRange.low.amount)
        assertEquals(20.0, status.priceRange.high.amount)
    }

    @Test
    fun `mock provider returns deterministic values per category`() = runTest {
        val provider = MockPriceEstimatorProvider(delayMs = 0L)
        val fashionRange = provider.estimate(PriceEstimationRequest("id1", "FASHION"))
        val electronicsRange = provider.estimate(PriceEstimationRequest("id2", "ELECTRONICS"))

        assertEquals(8.0, fashionRange.low.amount)
        assertEquals(28.0, fashionRange.high.amount)
        assertEquals(25.0, electronicsRange.low.amount)
        assertEquals(180.0, electronicsRange.high.amount)
    }

    @Test
    fun `timeout yields failed status`() = runTest {
        val orchestrator = newOrchestrator(SlowProvider())
        val request = PriceEstimationRequest(itemId = "slow-id", categoryId = "FASHION")

        orchestrator.startEstimation(request)
        // allow estimation to start and timeout
        testScheduler.advanceTimeBy(2_100)

        val status = orchestrator.getStatusFlow("slow-id").value
        assertIs<PriceEstimationStatus.Failed>(status)
    }
}
