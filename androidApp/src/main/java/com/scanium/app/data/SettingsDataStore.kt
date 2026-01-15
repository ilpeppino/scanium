package com.scanium.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore

internal const val SETTINGS_DATASTORE_TAG = "SettingsDataStore"

/**
 * DataStore with corruption handler that resets to defaults on file corruption.
 * This prevents startup crashes if the preferences file becomes corrupted.
 */
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { exception ->
        Log.e(SETTINGS_DATASTORE_TAG, "DataStore corrupted, resetting to defaults", exception)
        emptyPreferences()
    },
)
