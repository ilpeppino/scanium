package com.scanium.app.pricing

import android.util.Log
import com.scanium.app.items.ScannedItem
import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.pricing.PriceEstimationOrchestrator
import com.scanium.shared.core.models.pricing.PriceEstimationRequest
import com.scanium.shared.core.models.pricing.PriceEstimationStatus
import com.scanium.shared.core.models.pricing.PriceEstimatorProvider
import com.scanium.shared.core.models.pricing.providers.MockPriceEstimatorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class PriceEstimationRepository(
    provider: PriceEstimatorProvider = MockPriceEstimatorProvider(),
    scope: CoroutineScope,
    timeoutMs: Long = PriceEstimationOrchestrator.DEFAULT_TIMEOUT_MS
) {
    private val orchestrator = PriceEstimationOrchestrator(
        provider = provider,
        scope = scope,
        timeoutMs = timeoutMs,
        logger = { message -> Log.d(TAG, message) }
    )

    fun ensureEstimation(item: ScannedItem) {
        if (item.category == ItemCategory.UNKNOWN) return

        val request = PriceEstimationRequest(
            itemId = item.id,
            categoryId = item.category.name,
            domainCategoryId = item.domainCategoryId,
            currencyCode = DEFAULT_CURRENCY
        )
        orchestrator.startEstimation(request)
    }

    fun observeStatus(itemId: String): StateFlow<PriceEstimationStatus> = orchestrator.getStatusFlow(itemId)

    fun cancel(itemId: String) = orchestrator.cancel(itemId)

    companion object {
        private const val TAG = "PriceEstimationRepo"
        private const val DEFAULT_CURRENCY = "EUR"
    }
}
