package com.scanium.app.data

import com.scanium.app.BuildConfig
import com.scanium.app.model.billing.BillingProvider
import com.scanium.app.model.user.EntitlementPolicy
import com.scanium.app.model.user.FreeEntitlements
import com.scanium.app.model.user.ProEntitlements
import com.scanium.app.model.user.DeveloperEntitlements
import com.scanium.app.model.user.UserEdition
import com.scanium.app.model.user.toEntitlements
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class EntitlementManager(
    private val settingsRepository: SettingsRepository,
    private val billingProvider: BillingProvider
) {

    val entitlementPolicyFlow: Flow<EntitlementPolicy> = combine(
        billingProvider.entitlementState.map { it.status },
        settingsRepository.developerModeFlow
    ) { edition, devMode ->
        if (BuildConfig.DEBUG && devMode) {
            DeveloperEntitlements
        } else {
            edition.toEntitlements()
        }
    }
    
    val currentEditionFlow: Flow<UserEdition> = combine(
        billingProvider.entitlementState.map { it.status },
        settingsRepository.developerModeFlow
    ) { edition, devMode ->
        if (BuildConfig.DEBUG && devMode) {
            UserEdition.DEVELOPER
        } else {
            edition
        }
    }
    
    val entitlementStateFlow = billingProvider.entitlementState
}
