package com.scanium.app.pricing

import com.scanium.app.BuildConfig
import com.scanium.app.logging.CorrelationIds

class VariantSchemaRepository(
    private val api: VariantSchemaApi,
    private val apiKeyProvider: () -> String? = { null },
    private val authTokenProvider: () -> String? = { null },
    private val getDeviceId: () -> String = { "" },
    private val baseUrlProvider: () -> String = { BuildConfig.SCANIUM_API_BASE_URL },
) {
    suspend fun fetchSchema(productType: String): Result<VariantSchema> {
        val baseUrl =
            baseUrlProvider().takeIf { it.isNotBlank() }
                ?: return Result.failure(
                    PricingV4Exception(
                        errorCode = "CONFIG_ERROR",
                        userMessage = "Pricing service not configured",
                        retryable = false,
                    ),
                )

        val apiKey =
            apiKeyProvider()?.takeIf { it.isNotBlank() }
                ?: return Result.failure(
                    PricingV4Exception(
                        errorCode = "CONFIG_ERROR",
                        userMessage = "API key is missing",
                        retryable = false,
                    ),
                )

        val endpoint = "${baseUrl.trimEnd('/')}/v1/pricing/variant-schema"
        val correlationId = CorrelationIds.currentClassificationSessionId()

        return runCatching {
            api.getSchema(
                endpoint = endpoint,
                productType = productType,
                apiKey = apiKey,
                correlationId = correlationId,
                deviceId = getDeviceId().takeIf { it.isNotBlank() },
                authToken = authTokenProvider()?.takeIf { it.isNotBlank() },
            )
        }
    }
}
