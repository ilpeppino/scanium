package com.scanium.app.model.user

interface EntitlementPolicy {
    val canUseCloudClassification: Boolean
    val canUseAssistant: Boolean
    val canBatchExport: Boolean
    val maxDailyCloudClassifications: Int
    val isPro: Boolean
}

object FreeEntitlements : EntitlementPolicy {
    override val canUseCloudClassification = true // Capped
    override val canUseAssistant = false
    override val canBatchExport = false
    override val maxDailyCloudClassifications = 10
    override val isPro = false
}

object ProEntitlements : EntitlementPolicy {
    override val canUseCloudClassification = true
    override val canUseAssistant = true
    override val canBatchExport = true
    override val maxDailyCloudClassifications = 1000
    override val isPro = true
}

object DeveloperEntitlements : EntitlementPolicy {
    override val canUseCloudClassification = true
    override val canUseAssistant = true
    override val canBatchExport = true
    override val maxDailyCloudClassifications = Int.MAX_VALUE
    override val isPro = true
}

fun UserEdition.toEntitlements(): EntitlementPolicy = when(this) {
    UserEdition.FREE -> FreeEntitlements
    UserEdition.PRO -> ProEntitlements
    UserEdition.DEVELOPER -> DeveloperEntitlements
}
