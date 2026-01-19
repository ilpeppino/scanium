package com.scanium.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scanium.app.listing.ExportProfileId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.exportProfileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "export_profile_preferences",
)

class ExportProfilePreferences(
    private val context: Context,
) {
    companion object {
        private val LAST_EXPORT_PROFILE_ID = stringPreferencesKey("last_export_profile_id")
    }

    suspend fun getLastProfileId(defaultId: ExportProfileId): ExportProfileId {
        val raw =
            context.exportProfileDataStore.data
                .map { preferences -> preferences[LAST_EXPORT_PROFILE_ID] }
                .first()
        return raw?.takeIf { it.isNotBlank() }?.let { ExportProfileId(it) } ?: defaultId
    }

    suspend fun setLastProfileId(profileId: ExportProfileId) {
        context.exportProfileDataStore.edit { preferences ->
            preferences[LAST_EXPORT_PROFILE_ID] = profileId.value
        }
    }
}
