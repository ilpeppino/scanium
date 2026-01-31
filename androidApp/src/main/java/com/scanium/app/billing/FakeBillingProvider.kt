package com.scanium.app.billing

import android.util.Log
import com.scanium.app.model.billing.BillingProvider
import com.scanium.app.model.billing.EntitlementSource
import com.scanium.app.model.billing.EntitlementState
import com.scanium.app.model.billing.ProductDetails
import com.scanium.app.model.user.UserEdition
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeBillingProvider : BillingProvider {
    private val mutableEntitlementState = MutableStateFlow(EntitlementState.DEFAULT)
    override val entitlementState: Flow<EntitlementState> = mutableEntitlementState.asStateFlow()

    override suspend fun refreshEntitlements() {
        Log.d(TAG, "Fake refreshing entitlements...")
        delay(500)
        // No-op: in fake mode, state is only changed by purchase/restore actions or dev toggles
    }

    override suspend fun purchase(
        productId: String,
        activityContext: Any?,
    ): Result<Unit> {
        Log.d(TAG, "Fake purchasing $productId...")
        delay(1000) // Simulate network

        mutableEntitlementState.value = EntitlementState(
            status = UserEdition.PRO,
            source = EntitlementSource.LOCAL_CACHE, // Fake source
            lastUpdatedAt = System.currentTimeMillis()
        )
        return Result.success(Unit)
    }

    override suspend fun restorePurchases(): Result<Unit> {
        Log.d(TAG, "Fake restoring purchases...")
        delay(1000)
        // Simulate finding a purchase
        mutableEntitlementState.value = EntitlementState(
            status = UserEdition.PRO,
            source = EntitlementSource.LOCAL_CACHE, // Fake source
            lastUpdatedAt = System.currentTimeMillis()
        )
        return Result.success(Unit)
    }

    override suspend fun getProductDetails(productIds: List<String>): List<ProductDetails> {
        delay(300)
        return productIds.map { id ->
            ProductDetails(
                id = id,
                title = if (id.contains("monthly")) "Pro Monthly" else "Pro Lifetime",
                description = "Unlock all features with Scanium Pro",
                formattedPrice = if (id.contains("monthly")) "€4.99" else "€49.99",
                priceCurrencyCode = "EUR",
                priceAmountMicros = if (id.contains("monthly")) 4990000 else 49990000,
            )
        }
    }

    companion object {
        private const val TAG = "FakeBillingProvider"
    }
}
