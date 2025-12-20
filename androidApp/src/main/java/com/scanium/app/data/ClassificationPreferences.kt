package com.scanium.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scanium.app.BuildConfig
import com.scanium.app.ml.classification.ClassificationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.classificationDataStore: DataStore<Preferences> by preferencesDataStore(name = "classification_preferences")

class ClassificationPreferences(private val context: Context) {
    companion object {
        private val CLASSIFICATION_MODE_KEY = stringPreferencesKey("classification_mode")
        private val SAVE_CLOUD_CROPS_KEY = booleanPreferencesKey("save_cloud_crops")
    }

    val mode: Flow<ClassificationMode> = context.classificationDataStore.data
        .map { preferences ->
            val raw = preferences[CLASSIFICATION_MODE_KEY]
            // Default to CLOUD mode for production cloud-first classification
            raw?.let { runCatching { ClassificationMode.valueOf(it) }.getOrNull() } ?: ClassificationMode.CLOUD
        }

    val saveCloudCrops: Flow<Boolean> = context.classificationDataStore.data
        .map { preferences ->
            preferences[SAVE_CLOUD_CROPS_KEY]
                ?: (BuildConfig.DEBUG && BuildConfig.CLASSIFIER_SAVE_CROPS)
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
}
