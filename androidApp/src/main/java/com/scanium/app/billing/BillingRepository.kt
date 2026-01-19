package com.scanium.app.billing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scanium.app.model.billing.EntitlementSource
import com.scanium.app.model.billing.EntitlementState
import com.scanium.app.model.user.UserEdition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.billingDataStore: DataStore<Preferences> by preferencesDataStore(name = "billing_prefs")

class BillingRepository(
    private val context: Context,
) {
    companion object {
        private val KEY_STATUS = stringPreferencesKey("entitlement_status")
        private val KEY_SOURCE = stringPreferencesKey("entitlement_source")
        private val KEY_LAST_UPDATED = longPreferencesKey("last_updated")
        private val KEY_EXPIRES_AT = longPreferencesKey("expires_at")
        private val KEY_PURCHASE_TOKENS = stringPreferencesKey("purchase_tokens") // Basic storage for validation
    }

    val entitlementState: Flow<EntitlementState> =
        context.billingDataStore.data.map { prefs ->
            val status =
                try {
                    UserEdition.valueOf(prefs[KEY_STATUS] ?: UserEdition.FREE.name)
                } catch (e: Exception) {
                    UserEdition.FREE
                }

            val source =
                try {
                    EntitlementSource.valueOf(prefs[KEY_SOURCE] ?: EntitlementSource.LOCAL_CACHE.name)
                } catch (e: Exception) {
                    EntitlementSource.LOCAL_CACHE
                }

            EntitlementState(
                status = status,
                source = source,
                lastUpdatedAt = prefs[KEY_LAST_UPDATED] ?: 0L,
                expiresAt = prefs[KEY_EXPIRES_AT],
            )
        }

    suspend fun updateEntitlement(
        status: UserEdition,
        source: EntitlementSource,
        purchaseToken: String? = null,
    ) {
        context.billingDataStore.edit { prefs ->
            prefs[KEY_STATUS] = status.name
            prefs[KEY_SOURCE] = source.name
            prefs[KEY_LAST_UPDATED] = System.currentTimeMillis()
            if (purchaseToken != null) {
                // Append token if unique
                val current = prefs[KEY_PURCHASE_TOKENS]?.split(",")?.toMutableSet() ?: mutableSetOf()
                current.add(purchaseToken)
                prefs[KEY_PURCHASE_TOKENS] = current.joinToString(",")
            }
        }
    }

    suspend fun clearEntitlement() {
        context.billingDataStore.edit { prefs ->
            prefs[KEY_STATUS] = UserEdition.FREE.name
            prefs[KEY_SOURCE] = EntitlementSource.LOCAL_CACHE.name
            prefs[KEY_LAST_UPDATED] = System.currentTimeMillis()
        }
    }
}
