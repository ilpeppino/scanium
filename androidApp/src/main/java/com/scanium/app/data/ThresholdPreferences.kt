package com.scanium.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scanium.app.aggregation.AggregationPresets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-based preferences for storing the similarity threshold.
 *
 * This allows the threshold to persist across app restarts, useful for:
 * - Developers tuning the pipeline over multiple sessions
 * - Advanced users who want a specific threshold setting
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "threshold_preferences")

class ThresholdPreferences(
    private val context: Context,
) {
    companion object {
        private val SIMILARITY_THRESHOLD_KEY = floatPreferencesKey("similarity_threshold")
        private val DEFAULT_THRESHOLD = AggregationPresets.REALTIME.similarityThreshold
    }

    /**
     * Flow of the current similarity threshold.
     *
     * Emits the stored threshold, or the default if not yet set.
     */
    val similarityThreshold: Flow<Float> =
        context.dataStore.data
            .map { preferences ->
                preferences[SIMILARITY_THRESHOLD_KEY] ?: DEFAULT_THRESHOLD
            }

    /**
     * Save the similarity threshold to DataStore.
     *
     * @param threshold The threshold value to persist (0.0 - 1.0)
     */
    suspend fun saveSimilarityThreshold(threshold: Float) {
        context.dataStore.edit { preferences ->
            preferences[SIMILARITY_THRESHOLD_KEY] = threshold.coerceIn(0f, 1f)
        }
    }

    /**
     * Reset to default threshold.
     */
    suspend fun resetToDefault() {
        saveSimilarityThreshold(DEFAULT_THRESHOLD)
    }
}
