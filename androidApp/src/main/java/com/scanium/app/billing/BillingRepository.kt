package com.scanium.app.billing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.scanium.app.model.billing.EntitlementSource
import com.scanium.app.model.billing.EntitlementState
import com.scanium.app.model.user.UserEdition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BillingRepository(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        val KEY_STATUS = stringPreferencesKey("entitlement_status")
        val KEY_SOURCE = stringPreferencesKey("entitlement_source")
        val KEY_LAST_UPDATED = longPreferencesKey("last_updated")
        val KEY_EXPIRES_AT = longPreferencesKey("expires_at")
        val KEY_PURCHASE_TOKENS = stringPreferencesKey("purchase_tokens") // Basic storage for validation
    }

    val entitlementState: Flow<EntitlementState> =
        dataStore.data.map { prefs ->
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
        dataStore.edit { prefs ->
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
        dataStore.edit { prefs ->
            prefs[KEY_STATUS] = UserEdition.FREE.name
            prefs[KEY_SOURCE] = EntitlementSource.LOCAL_CACHE.name
            prefs[KEY_LAST_UPDATED] = System.currentTimeMillis()
        }
    }
}
