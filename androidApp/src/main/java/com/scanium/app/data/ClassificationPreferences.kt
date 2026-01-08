package com.scanium.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scanium.app.BuildConfig
import com.scanium.app.ml.classification.ClassificationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val TAG = "ClassificationPrefs"

/**
 * DataStore with corruption handler that resets to defaults on file corruption.
 */
private val Context.classificationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "classification_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { exception ->
        Log.e(TAG, "DataStore corrupted, resetting to defaults", exception)
        emptyPreferences()
    },
)

class ClassificationPreferences(private val context: Context) {
    companion object {
        private val CLASSIFICATION_MODE_KEY = stringPreferencesKey("classification_mode")
        private val SAVE_CLOUD_CROPS_KEY = booleanPreferencesKey("save_cloud_crops")
        private val LOW_DATA_MODE_KEY = booleanPreferencesKey("low_data_mode")
        private val VERBOSE_LOGGING_KEY = booleanPreferencesKey("verbose_logging")
    }

    val mode: Flow<ClassificationMode> =
        context.classificationDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    Log.e(TAG, "DataStore IO error, using default mode", exception)
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val raw = preferences[CLASSIFICATION_MODE_KEY]
                // Default to CLOUD mode for production cloud-first classification
                raw?.let { runCatching { ClassificationMode.valueOf(it) }.getOrNull() } ?: ClassificationMode.CLOUD
            }

    val saveCloudCrops: Flow<Boolean> =
        context.classificationDataStore.data
            .map { preferences ->
                preferences[SAVE_CLOUD_CROPS_KEY]
                    ?: (BuildConfig.DEBUG && BuildConfig.CLASSIFIER_SAVE_CROPS)
            }

    val lowDataMode: Flow<Boolean> =
        context.classificationDataStore.data
            .map { preferences ->
                preferences[LOW_DATA_MODE_KEY] ?: false
            }

    val verboseLogging: Flow<Boolean> =
        context.classificationDataStore.data
            .map { preferences ->
                preferences[VERBOSE_LOGGING_KEY] ?: BuildConfig.DEBUG
            }

    suspend fun setMode(mode: ClassificationMode) {
        context.classificationDataStore.edit { preferences ->
            preferences[CLASSIFICATION_MODE_KEY] = mode.name
        }
    }

    suspend fun setSaveCloudCrops(enabled: Boolean) {
        if (!BuildConfig.DEBUG) {
            return // Never persist debug diagnostics in release builds
        }
        context.classificationDataStore.edit { preferences ->
            preferences[SAVE_CLOUD_CROPS_KEY] = enabled
        }
    }

    suspend fun setLowDataMode(enabled: Boolean) {
        context.classificationDataStore.edit { preferences ->
            preferences[LOW_DATA_MODE_KEY] = enabled
        }
    }

    suspend fun setVerboseLogging(enabled: Boolean) {
        if (!BuildConfig.DEBUG) {
            return
        }
        context.classificationDataStore.edit { preferences ->
            preferences[VERBOSE_LOGGING_KEY] = enabled
        }
    }
}
