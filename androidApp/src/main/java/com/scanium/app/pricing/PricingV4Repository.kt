package com.scanium.app.pricing

import com.scanium.app.BuildConfig
import com.scanium.app.logging.CorrelationIds
import com.scanium.shared.core.models.assistant.MarketplaceUsed
import com.scanium.shared.core.models.assistant.PriceInfo
import com.scanium.shared.core.models.assistant.PriceRange
import com.scanium.shared.core.models.assistant.PricingConfidence
import com.scanium.shared.core.models.assistant.PricingInsights
import com.scanium.shared.core.models.assistant.SampleListing

class PricingV4Exception(
    val errorCode: String,
    val userMessage: String,
    val retryable: Boolean,
    val retryAfterSeconds: Int? = null,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : Exception(userMessage, cause)

class PricingV4Repository(
    private val api: PricingV4Api,
    private val apiKeyProvider: () -> String? = { null },
    private val authTokenProvider: () -> String? = { null },
    private val getDeviceId: () -> String = { "" },
    private val baseUrlProvider: () -> String = { BuildConfig.SCANIUM_API_BASE_URL },
) {
    suspend fun estimatePrice(request: PricingV4Request): Result<PricingInsights> {
        val baseUrl = baseUrlProvider().takeIf { it.isNotBlank() }
            ?: return Result.failure(
                PricingV4Exception(
                    errorCode = "CONFIG_ERROR",
                    userMessage = "Pricing service not configured",
                    retryable = false,
                ),
            )

        val apiKey = apiKeyProvider()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(
                PricingV4Exception(
                    errorCode = "CONFIG_ERROR",
                    userMessage = "API key is missing",
                    retryable = false,
                ),
            )

        val endpoint = "${baseUrl.trimEnd('/')}/v1/pricing/v4"
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
                throw PricingV4Exception(
                    errorCode = response.error?.code ?: "UNKNOWN_ERROR",
                    userMessage = response.error?.message ?: "Pricing request failed",
                    retryable = false,
                )
            }

            response.toPricingInsights()
        }
    }
}

internal fun PricingV4Response.toPricingInsights(): PricingInsights {
    val payload = pricing ?: throw IllegalStateException("Pricing payload missing")
    return PricingInsights(
        status = payload.status,
        countryCode = payload.countryCode,
        marketplacesUsed = payload.sources.map { it.toModel() },
        results = emptyList(),
        range = payload.range?.toModel(),
        confidence = payload.confidence.toConfidenceOrNull(),
        totalListingsAnalyzed = payload.totalListingsAnalyzed,
        timeWindowDays = payload.timeWindowDays,
        sampleListings = payload.sampleListings.map { it.toModel() },
        fallbackReason = payload.fallbackReason,
    )
}

private fun PricingV4SourceDto.toModel(): MarketplaceUsed =
    MarketplaceUsed(
        id = id,
        name = name,
        baseUrl = baseUrl,
        listingCount = listingCount,
        searchUrl = searchUrl,
    )

private fun PricingV4RangeDto.toModel(): PriceRange =
    PriceRange(
        low = low,
        median = median,
        high = high,
        currency = currency,
    )

private fun PricingV4SampleListingDto.toModel(): SampleListing =
    SampleListing(
        title = title,
        price = PriceInfo(amount = price, currency = currency),
        condition = condition,
        url = url,
        marketplace = marketplace,
    )

private fun String?.toConfidenceOrNull(): PricingConfidence? =
    this?.let {
        runCatching { PricingConfidence.valueOf(it.uppercase()) }.getOrNull()
    }
