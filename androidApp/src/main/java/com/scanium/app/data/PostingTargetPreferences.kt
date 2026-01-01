package com.scanium.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.postingTargetDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "posting_target_preferences",
)

class PostingTargetPreferences(private val context: Context) {
    companion object {
        private val LAST_TARGET_ID = stringPreferencesKey("last_posting_target_id")
        private val CUSTOM_TARGET_URL = stringPreferencesKey("custom_posting_target_url")
    }

    suspend fun getLastTargetId(defaultId: String): String {
        val raw =
            context.postingTargetDataStore.data
                .map { preferences -> preferences[LAST_TARGET_ID] }
                .first()
        return raw?.takeIf { it.isNotBlank() } ?: defaultId
    }

    suspend fun setLastTargetId(targetId: String) {
        context.postingTargetDataStore.edit { preferences ->
            preferences[LAST_TARGET_ID] = targetId
        }
    }

    suspend fun getCustomUrl(): String? {
        return context.postingTargetDataStore.data
            .map { preferences -> preferences[CUSTOM_TARGET_URL] }
            .first()
            ?.takeIf { it.isNotBlank() }
    }

    suspend fun setCustomUrl(url: String) {
        context.postingTargetDataStore.edit { preferences ->
            preferences[CUSTOM_TARGET_URL] = url
        }
    }
}
