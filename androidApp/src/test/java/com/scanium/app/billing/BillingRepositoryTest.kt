package com.scanium.app.billing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanium.app.model.billing.EntitlementSource
import com.scanium.app.model.user.UserEdition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class BillingRepositoryTest {
    private val testScope = TestScope()
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var billingRepository: BillingRepository

    @Before
    fun setup() {
        testDataStore = PreferenceDataStoreFactory.create(scope = testScope) {
            ApplicationProvider.getApplicationContext<Context>().preferencesDataStoreFile("billing_prefs_test")
        }
        billingRepository = BillingRepository(testDataStore)
    }

    @Test
    fun `updateEntitlement persists correct state`() = testScope.runTest {
        // Given
        val status = UserEdition.PRO
        val source = EntitlementSource.PLAY_BILLING
        val token = "test_token_123"

        // When
        billingRepository.updateEntitlement(status, source, token)

        // Then
        val entitlementState = billingRepository.entitlementState.first()
        assertThat(entitlementState.status).isEqualTo(status)
        assertThat(entitlementState.source).isEqualTo(source)
        assertThat(entitlementState.lastUpdatedAt).isGreaterThan(0L)
    }

    @Test
    fun `clearEntitlement resets to FREE tier`() = testScope.runTest {
        // Given - an existing entitlement
        billingRepository.updateEntitlement(
            UserEdition.PRO,
            EntitlementSource.PLAY_BILLING,
        )
        val proState = billingRepository.entitlementState.first()
        assertThat(proState.status).isEqualTo(UserEdition.PRO)

        // When
        billingRepository.clearEntitlement()

        // Then
        val clearedState = billingRepository.entitlementState.first()
        assertThat(clearedState.status).isEqualTo(UserEdition.FREE)
        assertThat(clearedState.source).isEqualTo(EntitlementSource.LOCAL_CACHE)
    }

    @Test
    fun `purchase tokens are deduplicated`() = testScope.runTest {
        val token1 = "token_one"
        val token2 = "token_two"

        // When
        billingRepository.updateEntitlement(
            UserEdition.PRO,
            EntitlementSource.PLAY_BILLING,
            token1,
        )
        billingRepository.updateEntitlement(
            UserEdition.PRO,
            EntitlementSource.PLAY_BILLING,
            token2,
        )
        billingRepository.updateEntitlement(
            UserEdition.PRO,
            EntitlementSource.PLAY_BILLING,
            token1,
        )

        // Then
        val prefs = testDataStore.data.first()
        val storedTokens = prefs[BillingRepository.KEY_PURCHASE_TOKENS]?.split(",")
        assertThat(storedTokens).containsExactly(token1, token2)
    }

    @Test
    fun `corrupted preferences fall back to FREE`() = testScope.runTest {
        // Given - manually writing a corrupted value to the datastore
        testDataStore.edit { prefs ->
            prefs[BillingRepository.KEY_STATUS] = "INVALID_STATUS"
            prefs[BillingRepository.KEY_SOURCE] = "INVALID_SOURCE"
        }

        // When
        val entitlementState = billingRepository.entitlementState.first()

        // Then
        assertThat(entitlementState.status).isEqualTo(UserEdition.FREE)
        assertThat(entitlementState.source).isEqualTo(EntitlementSource.LOCAL_CACHE)
    }

    @Test
    fun `initial state is FREE`() = testScope.runTest {
        // When
        val entitlementState = billingRepository.entitlementState.first()

        // Then
        assertThat(entitlementState.status).isEqualTo(UserEdition.FREE)
        assertThat(entitlementState.source).isEqualTo(EntitlementSource.LOCAL_CACHE)
        assertThat(entitlementState.lastUpdatedAt).isEqualTo(0L)
        assertThat(entitlementState.expiresAt).isNull()
    }
}