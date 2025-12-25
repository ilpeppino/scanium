package com.scanium.app.ftue

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for managing First-Time User Experience (FTUE) state persistence.
 * Uses DataStore Preferences to track tour completion and debug settings.
 */
class FtueRepository(private val context: Context) {

    companion object {
        private val FTUE_COMPLETED_KEY = booleanPreferencesKey("ftue_completed")
        private val FTUE_FORCE_ENABLED_KEY = booleanPreferencesKey("ftue_force_enabled")
        private val PERMISSION_EDUCATION_SHOWN_KEY = booleanPreferencesKey("permission_education_shown")
    }

    /**
     * Flow indicating whether the FTUE tour has been completed.
     * Defaults to false (tour not completed).
     */
    val completedFlow: Flow<Boolean> = context.ftueDataStore.data.map { preferences ->
        preferences[FTUE_COMPLETED_KEY] ?: false
    }

    /**
     * Flow indicating whether the FTUE tour is force-enabled for debugging.
     * When true, tour will always show regardless of completion status.
     * Defaults to false.
     */
    val forceEnabledFlow: Flow<Boolean> = context.ftueDataStore.data.map { preferences ->
        preferences[FTUE_FORCE_ENABLED_KEY] ?: false
    }

    /**
     * Flow indicating whether the permission education dialog has been shown.
     * This dialog explains why camera access is needed before requesting permission.
     * Defaults to false (not shown).
     */
    val permissionEducationShownFlow: Flow<Boolean> = context.ftueDataStore.data.map { preferences ->
        preferences[PERMISSION_EDUCATION_SHOWN_KEY] ?: false
    }

    /**
     * Sets the tour completion status.
     * @param completed True if tour has been completed, false otherwise
     */
    suspend fun setCompleted(completed: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[FTUE_COMPLETED_KEY] = completed
        }
    }

    /**
     * Sets the force-enabled debug flag.
     * @param enabled True to force tour to always show, false for normal behavior
     */
    suspend fun setForceEnabled(enabled: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[FTUE_FORCE_ENABLED_KEY] = enabled
        }
    }

    /**
     * Sets the permission education shown status.
     * @param shown True if the education dialog has been shown, false otherwise
     */
    suspend fun setPermissionEducationShown(shown: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[PERMISSION_EDUCATION_SHOWN_KEY] = shown
        }
    }

    /**
     * Resets the tour completion status, allowing the tour to be shown again.
     */
    suspend fun reset() {
        setCompleted(false)
    }

    /**
     * Resets all FTUE state including permission education.
     * Useful for debugging or when user wants to see onboarding again.
     */
    suspend fun resetAll() {
        context.ftueDataStore.edit { preferences ->
            preferences[FTUE_COMPLETED_KEY] = false
            preferences[PERMISSION_EDUCATION_SHOWN_KEY] = false
        }
    }
}

// DataStore extension property for FTUE preferences
private val Context.ftueDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ftue_preferences"
)
