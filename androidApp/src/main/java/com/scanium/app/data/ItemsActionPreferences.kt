package com.scanium.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scanium.app.items.SelectedItemsAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.itemsActionDataStore: DataStore<Preferences> by preferencesDataStore(name = "items_action_preferences")

class ItemsActionPreferences(private val context: Context) {
    companion object {
        private val LAST_PRIMARY_ACTION_KEY = stringPreferencesKey("last_primary_action")
    }

    val lastAction: Flow<SelectedItemsAction> = context.itemsActionDataStore.data
        .map { preferences ->
            val raw = preferences[LAST_PRIMARY_ACTION_KEY]
            raw?.let { runCatching { SelectedItemsAction.valueOf(it) }.getOrNull() }
                ?: SelectedItemsAction.SELL_ON_EBAY
        }

    suspend fun setLastAction(action: SelectedItemsAction) {
        context.itemsActionDataStore.edit { preferences ->
            preferences[LAST_PRIMARY_ACTION_KEY] = action.name
        }
    }
}
