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
        private val SHUTTER_HINT_SHOWN_KEY = booleanPreferencesKey("shutter_hint_shown")
        private val LANGUAGE_SELECTION_SHOWN_KEY = booleanPreferencesKey("language_selection_shown")

        // Camera FTUE flags (screen-scoped, per-step)
        private val CAMERA_ROI_HINT_SEEN_KEY = booleanPreferencesKey("camera_roi_hint_seen")
        private val CAMERA_BBOX_HINT_SEEN_KEY = booleanPreferencesKey("camera_bbox_hint_seen")
        private val CAMERA_SHUTTER_HINT_SEEN_KEY = booleanPreferencesKey("camera_shutter_hint_seen")
        private val CAMERA_FTUE_COMPLETED_KEY = booleanPreferencesKey("camera_ftue_completed")
    }

    /**
     * Flow indicating whether the FTUE tour has been completed.
     * Defaults to false (tour not completed).
     */
    val completedFlow: Flow<Boolean> =
        context.ftueDataStore.data.map { preferences ->
            preferences[FTUE_COMPLETED_KEY] ?: false
        }

    /**
     * Flow indicating whether the FTUE tour is force-enabled for debugging.
     * When true, tour will always show regardless of completion status.
     * Defaults to false.
     */
    val forceEnabledFlow: Flow<Boolean> =
        context.ftueDataStore.data.map { preferences ->
            preferences[FTUE_FORCE_ENABLED_KEY] ?: false
        }

    /**
     * Flow indicating whether the permission education dialog has been shown.
     * This dialog explains why camera access is needed before requesting permission.
     * Defaults to false (not shown).
     */
    val permissionEducationShownFlow: Flow<Boolean> =
        context.ftueDataStore.data.map { preferences ->
            preferences[PERMISSION_EDUCATION_SHOWN_KEY] ?: false
        }

    /**
     * Flow indicating whether the shutter hint has been shown.
     * Defaults to false (not shown).
     */
    val shutterHintShownFlow: Flow<Boolean> =
        context.ftueDataStore.data.map { preferences ->
            preferences[SHUTTER_HINT_SHOWN_KEY] ?: false
        }

    /**
     * Flow indicating whether the language selection dialog has been shown.
     * This dialog allows users to choose their preferred app language after granting camera permission.
     * Defaults to false (not shown).
     */
    val languageSelectionShownFlow: Flow<Boolean> =
        context.ftueDataStore.data.map { preferences ->
            preferences[LANGUAGE_SELECTION_SHOWN_KEY] ?: false
        }

    /**
     * Flow indicating whether the camera ROI pulse hint has been shown.
     * Part of the camera screen FTUE sequence.
     * Defaults to false (not shown).
     */
    val cameraRoiHintSeenFlow: Flow<Boolean> =
        context.ftueDataStore.data.map { preferences ->
            preferences[CAMERA_ROI_HINT_SEEN_KEY] ?: false
        }

    /**
     * Flow indicating whether the camera bounding box hint has been shown.
     * Part of the camera screen FTUE sequence.
     * Defaults to false (not shown).
     */
    val cameraBboxHintSeenFlow: Flow<Boolean> =
        context.ftueDataStore.data.map { preferences ->
            preferences[CAMERA_BBOX_HINT_SEEN_KEY] ?: false
        }

    /**
     * Flow indicating whether the camera shutter hint has been shown.
     * Part of the camera screen FTUE sequence.
     * Defaults to false (not shown).
     */
    val cameraShutterHintSeenFlow: Flow<Boolean> =
        context.ftueDataStore.data.map { preferences ->
            preferences[CAMERA_SHUTTER_HINT_SEEN_KEY] ?: false
        }

    /**
     * Flow indicating whether the camera FTUE sequence has been completed.
     * Defaults to false (not completed).
     */
    val cameraFtueCompletedFlow: Flow<Boolean> =
        context.ftueDataStore.data.map { preferences ->
            preferences[CAMERA_FTUE_COMPLETED_KEY] ?: false
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
     * Sets the shutter hint shown status.
     * @param shown True if the shutter hint has been shown, false otherwise
     */
    suspend fun setShutterHintShown(shown: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[SHUTTER_HINT_SHOWN_KEY] = shown
        }
    }

    /**
     * Sets the language selection shown status.
     * @param shown True if the language selection dialog has been shown, false otherwise
     */
    suspend fun setLanguageSelectionShown(shown: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[LANGUAGE_SELECTION_SHOWN_KEY] = shown
        }
    }

    /**
     * Sets the camera ROI hint seen status.
     * @param seen True if the ROI pulse hint has been shown, false otherwise
     */
    suspend fun setCameraRoiHintSeen(seen: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[CAMERA_ROI_HINT_SEEN_KEY] = seen
        }
    }

    /**
     * Sets the camera bounding box hint seen status.
     * @param seen True if the BBox hint has been shown, false otherwise
     */
    suspend fun setCameraBboxHintSeen(seen: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[CAMERA_BBOX_HINT_SEEN_KEY] = seen
        }
    }

    /**
     * Sets the camera shutter hint seen status.
     * @param seen True if the shutter hint has been shown, false otherwise
     */
    suspend fun setCameraShutterHintSeen(seen: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[CAMERA_SHUTTER_HINT_SEEN_KEY] = seen
        }
    }

    /**
     * Sets the camera FTUE completion status.
     * @param completed True if the camera FTUE sequence has been completed, false otherwise
     */
    suspend fun setCameraFtueCompleted(completed: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[CAMERA_FTUE_COMPLETED_KEY] = completed
        }
    }

    /**
     * Resets the tour completion status, allowing the tour to be shown again.
     */
    suspend fun reset() {
        setCompleted(false)
    }

    /**
     * Resets all FTUE state including permission education and screen-scoped FTUE sequences.
     * Useful for debugging or when user wants to see onboarding again.
     */
    suspend fun resetAll() {
        context.ftueDataStore.edit { preferences ->
            preferences[FTUE_COMPLETED_KEY] = false
            preferences[PERMISSION_EDUCATION_SHOWN_KEY] = false
            preferences[SHUTTER_HINT_SHOWN_KEY] = false
            preferences[LANGUAGE_SELECTION_SHOWN_KEY] = false
            // Camera FTUE
            preferences[CAMERA_ROI_HINT_SEEN_KEY] = false
            preferences[CAMERA_BBOX_HINT_SEEN_KEY] = false
            preferences[CAMERA_SHUTTER_HINT_SEEN_KEY] = false
            preferences[CAMERA_FTUE_COMPLETED_KEY] = false
        }
    }
}

// DataStore extension property for FTUE preferences
private val Context.ftueDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ftue_preferences",
)
