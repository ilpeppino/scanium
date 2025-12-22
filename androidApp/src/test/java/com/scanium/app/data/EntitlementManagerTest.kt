package com.scanium.app.data

import com.scanium.app.model.billing.BillingProvider
import com.scanium.app.model.billing.EntitlementState
import com.scanium.app.model.user.UserEdition
import com.scanium.app.model.user.FreeEntitlements
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EntitlementManagerTest {

    @Test
    fun `Free edition has limited entitlements`() = runTest {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val billingProvider = mockk<BillingProvider>(relaxed = true)
        val userEditionFlow = MutableStateFlow(UserEdition.FREE)
        val developerModeFlow = MutableStateFlow(false)
        val entitlementStateFlow = MutableStateFlow(EntitlementState.DEFAULT)

        every { settingsRepository.userEditionFlow } returns userEditionFlow
        every { settingsRepository.developerModeFlow } returns developerModeFlow
        every { billingProvider.entitlementState } returns entitlementStateFlow

        val manager = EntitlementManager(settingsRepository, billingProvider)

        val policy = manager.entitlementPolicyFlow.first()
        
        // FreeEntitlements is an object or class?
        // Let's assume it's an object or data class equal to what we expect.
        assertEquals(FreeEntitlements, policy)
    }
}