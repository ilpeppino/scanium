package com.scanium.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scanium.app.ml.classification.ClassificationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.classificationDataStore: DataStore<Preferences> by preferencesDataStore(name = "classification_preferences")

class ClassificationPreferences(private val context: Context) {
    companion object {
        private val CLASSIFICATION_MODE_KEY = stringPreferencesKey("classification_mode")
    }

    val mode: Flow<ClassificationMode> = context.classificationDataStore.data
        .map { preferences ->
            val raw = preferences[CLASSIFICATION_MODE_KEY]
            raw?.let { runCatching { ClassificationMode.valueOf(it) }.getOrNull() } ?: ClassificationMode.ON_DEVICE
        }

    suspend fun setMode(mode: ClassificationMode) {
        context.classificationDataStore.edit { preferences ->
            preferences[CLASSIFICATION_MODE_KEY] = mode.name
        }
    }
}
