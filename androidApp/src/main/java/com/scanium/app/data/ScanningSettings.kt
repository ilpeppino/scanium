package com.scanium.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class ScanningSettings(
    private val dataStore: DataStore<Preferences>,
) {
    val scanningGuidanceEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Scanning.SCANNING_GUIDANCE_ENABLED_KEY] ?: true
        }

    suspend fun setScanningGuidanceEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Scanning.SCANNING_GUIDANCE_ENABLED_KEY] = enabled
        }
    }

    val showDetectionBoxesFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Scanning.SHOW_DETECTION_BOXES_KEY] ?: true
        }

    suspend fun setShowDetectionBoxes(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Scanning.SHOW_DETECTION_BOXES_KEY] = enabled
        }
    }

    val openItemListAfterScanFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Scanning.OPEN_ITEM_LIST_AFTER_SCAN_KEY] ?: false
        }

    suspend fun setOpenItemListAfterScan(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Scanning.OPEN_ITEM_LIST_AFTER_SCAN_KEY] = enabled
        }
    }

    val smartMergeSuggestionsEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Scanning.SMART_MERGE_SUGGESTIONS_ENABLED_KEY] ?: false
        }

    suspend fun setSmartMergeSuggestionsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Scanning.SMART_MERGE_SUGGESTIONS_ENABLED_KEY] = enabled
        }
    }
}
