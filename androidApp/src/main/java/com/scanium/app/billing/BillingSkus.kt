package com.scanium.app.billing

object BillingSkus {
    const val PRO_SUBSCRIPTION_MONTHLY = "scanium_pro_monthly"
    const val PRO_LIFETIME = "scanium_pro_lifetime"
    
    val SUBSCRIPTIONS = listOf(PRO_SUBSCRIPTION_MONTHLY)
    val INAPP = listOf(PRO_LIFETIME)
}
