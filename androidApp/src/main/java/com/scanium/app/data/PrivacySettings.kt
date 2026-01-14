package com.scanium.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.scanium.app.config.FeatureFlags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class PrivacySettings(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun enablePrivacySafeMode() {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.General.ALLOW_CLOUD_CLASSIFICATION_KEY] = false
            preferences[SettingsKeys.Assistant.ALLOW_ASSISTANT_IMAGES_KEY] = false
            preferences[SettingsKeys.General.SHARE_DIAGNOSTICS_KEY] = false
        }
    }

    val isPrivacySafeModeActiveFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            val cloudOff = !(preferences[SettingsKeys.General.ALLOW_CLOUD_CLASSIFICATION_KEY] ?: true)
            val imagesDefaultValue = if (FeatureFlags.isDevBuild) true else false
            val imagesOff = !(preferences[SettingsKeys.Assistant.ALLOW_ASSISTANT_IMAGES_KEY] ?: imagesDefaultValue)
            val diagnosticsOff = !(preferences[SettingsKeys.General.SHARE_DIAGNOSTICS_KEY] ?: false)
            cloudOff && imagesOff && diagnosticsOff
        }

    suspend fun resetPrivacySettings() {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.General.ALLOW_CLOUD_CLASSIFICATION_KEY] = true
            preferences[SettingsKeys.Assistant.ALLOW_ASSISTANT_KEY] = false
            preferences[SettingsKeys.Assistant.ALLOW_ASSISTANT_IMAGES_KEY] = false
            preferences[SettingsKeys.Voice.VOICE_MODE_ENABLED_KEY] = false
            preferences[SettingsKeys.Voice.SPEAK_ANSWERS_KEY] = false
            preferences[SettingsKeys.Voice.AUTO_SEND_TRANSCRIPT_KEY] = false
            preferences[SettingsKeys.General.SHARE_DIAGNOSTICS_KEY] = false
        }
    }
}
