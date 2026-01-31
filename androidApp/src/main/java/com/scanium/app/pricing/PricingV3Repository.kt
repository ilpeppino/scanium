package com.scanium.app.pricing

import com.scanium.app.BuildConfig
import com.scanium.app.logging.CorrelationIds
import com.scanium.shared.core.models.assistant.MarketplaceUsed
import com.scanium.shared.core.models.assistant.PriceRange
import com.scanium.shared.core.models.assistant.PricingConfidence
import com.scanium.shared.core.models.assistant.PricingInsights

class PricingV3Exception(
    val errorCode: String,
    val userMessage: String,
    val retryable: Boolean,
    val retryAfterSeconds: Int? = null,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : Exception(userMessage, cause)

class PricingV3Repository(
    private val api: PricingV3Api,
    private val apiKeyProvider: () -> String? = { null },
    private val authTokenProvider: () -> String? = { null },
    private val getDeviceId: () -> String = { "" },
    private val baseUrlProvider: () -> String = { BuildConfig.SCANIUM_API_BASE_URL },
) {
    suspend fun estimatePrice(request: PricingV3Request): Result<PricingInsights> {
        val baseUrl =
            baseUrlProvider().takeIf { it.isNotBlank() }
                ?: return Result.failure(
                    PricingV3Exception(
                        errorCode = "CONFIG_ERROR",
                        userMessage = "Pricing service not configured",
                        retryable = false,
                    ),
                )

        val apiKey =
            apiKeyProvider()?.takeIf { it.isNotBlank() }
                ?: return Result.failure(
                    PricingV3Exception(
                        errorCode = "CONFIG_ERROR",
                        userMessage = "API key is missing",
                        retryable = false,
                    ),
                )

        val endpoint = "${baseUrl.trimEnd('/')}/v1/pricing/v3"
        val correlationId = CorrelationIds.currentClassificationSessionId()

        return runCatching {
            val response =
                api.estimatePrice(
                    endpoint = endpoint,
                    requestPayload = request,
                    apiKey = apiKey,
                    correlationId = correlationId,
                    deviceId = getDeviceId().takeIf { it.isNotBlank() },
                    authToken = authTokenProvider()?.takeIf { it.isNotBlank() },
                )

            if (!response.success || response.pricing == null) {
                throw PricingV3Exception(
                    errorCode = response.error?.code ?: "UNKNOWN_ERROR",
                    userMessage = response.error?.message ?: "Pricing request failed",
                    retryable = false,
                )
            }

            response.toPricingInsights()
        }
    }
}

internal fun PricingV3Response.toPricingInsights(): PricingInsights {
    val payload = pricing ?: throw IllegalStateException("Pricing payload missing")
    return PricingInsights(
        status = payload.status,
        countryCode = payload.countryCode,
        marketplacesUsed = payload.marketplacesUsed.map { it.toModel() },
        querySummary = payload.reason,
        results = emptyList(),
        range = payload.range?.toModel(),
        confidence = payload.confidence.toConfidenceOrNull(),
        errorCode = payload.errorCode,
    )
}

private fun PricingV3MarketplaceDto.toModel(): MarketplaceUsed =
    MarketplaceUsed(
        id = id,
        name = name,
        baseUrl = baseUrl ?: "",
    )

private fun PricingV3RangeDto.toModel(): PriceRange =
    PriceRange(
        low = low,
        high = high,
        currency = currency,
    )

private fun String?.toConfidenceOrNull(): PricingConfidence? =
    this?.let {
        runCatching { PricingConfidence.valueOf(it.uppercase()) }.getOrNull()
    }
