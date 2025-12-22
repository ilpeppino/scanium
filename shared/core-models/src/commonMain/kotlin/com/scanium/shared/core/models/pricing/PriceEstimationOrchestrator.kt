package com.scanium.shared.core.models.pricing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PriceEstimationOrchestrator(
    private val provider: PriceEstimatorProvider,
    private val scope: CoroutineScope,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val logger: (String) -> Unit = {}
) {
    private val statusFlows = mutableMapOf<String, MutableStateFlow<PriceEstimationStatus>>()
    private val activeJobs = mutableMapOf<String, Job>()
    private val requestKeys = mutableMapOf<String, String>()

    fun getStatusFlow(itemId: String): StateFlow<PriceEstimationStatus> {
        return statusFlows.getOrPut(itemId) { MutableStateFlow(PriceEstimationStatus.Idle) }.asStateFlow()
    }

    fun startEstimation(request: PriceEstimationRequest) {
        val itemId = request.itemId
        val dedupKey = buildDedupKey(request)
        val statusFlow = statusFlows.getOrPut(itemId) { MutableStateFlow(PriceEstimationStatus.Idle) }

        if (requestKeys[itemId] == dedupKey) {
            val currentStatus = statusFlow.value
            if (currentStatus is PriceEstimationStatus.Estimating || currentStatus is PriceEstimationStatus.Ready) {
                return
            }
        }

        requestKeys[itemId] = dedupKey
        activeJobs.remove(itemId)?.cancel()

        statusFlow.value = PriceEstimationStatus.Estimating

        val workerJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            logger("price_estimation_start id=${request.itemId} provider=${provider.id} category=${request.categoryId}")
            delay(1)
            try {
                val priceRange = provider.estimate(request)
                statusFlow.emit(PriceEstimationStatus.Ready(priceRange))
                logger("price_estimation_ready id=${request.itemId} provider=${provider.id} range=${priceRange.low.amount}-${priceRange.high.amount}")
            } catch (cancel: CancellationException) {
                // Swallow cancellation if timeout already emitted FAILED
                if (statusFlow.value !is PriceEstimationStatus.Failed) {
                    logger("price_estimation_cancelled id=${request.itemId}")
                }
            } catch (t: Throwable) {
                statusFlow.emit(PriceEstimationStatus.Failed(t.message))
                logger("price_estimation_failed id=${request.itemId} error=${t.message}")
            }
        }

        activeJobs[itemId] = workerJob

        val timeoutJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            delay(timeoutMs)
            if (workerJob.isActive) {
                workerJob.cancel()
                statusFlow.emit(PriceEstimationStatus.Failed("timeout"))
                logger("price_estimation_timeout id=${request.itemId}")
            }
        }

        workerJob.invokeOnCompletion {
            timeoutJob.cancel()
        }
    }

    fun cancel(itemId: String) {
        activeJobs.remove(itemId)?.cancel()
        statusFlows[itemId]?.value = PriceEstimationStatus.Idle
        requestKeys.remove(itemId)
    }

    fun clear(itemId: String) {
        cancel(itemId)
        statusFlows.remove(itemId)
    }

    private fun buildDedupKey(request: PriceEstimationRequest): String {
        val attributesKey = request.attributes.toSortedMap().entries.joinToString(separator = "|") { "${it.key}:${it.value}" }
        return listOfNotNull(
            request.itemId,
            request.categoryId,
            request.domainCategoryId,
            attributesKey,
            request.region,
            request.currencyCode
        ).joinToString("#")
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 2_500
    }
}
