package com.scanium.app.model.billing

import kotlinx.coroutines.flow.Flow

interface BillingProvider {
    val entitlementState: Flow<EntitlementState>

    suspend fun refreshEntitlements()

    suspend fun purchase(
        productId: String,
        activityContext: Any? = null,
    ): Result<Unit>

    suspend fun restorePurchases(): Result<Unit>

    suspend fun getProductDetails(productIds: List<String>): List<ProductDetails>
}

data class ProductDetails(
    val id: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val priceCurrencyCode: String,
    val priceAmountMicros: Long,
)
