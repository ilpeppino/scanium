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

        // Items List FTUE flags (screen-scoped, per-step)
        private val LIST_TAP_EDIT_HINT_SEEN_KEY = booleanPreferencesKey("list_tap_edit_hint_seen")
        private val LIST_SWIPE_DELETE_HINT_SEEN_KEY = booleanPreferencesKey("list_swipe_delete_hint_seen")
        private val LIST_LONG_PRESS_HINT_SEEN_KEY = booleanPreferencesKey("list_long_press_hint_seen")
        private val LIST_SHARE_GOAL_HINT_SEEN_KEY = booleanPreferencesKey("list_share_goal_hint_seen")
        private val LIST_FTUE_COMPLETED_KEY = booleanPreferencesKey("list_ftue_completed")

        // Edit Item FTUE flags (screen-scoped, per-step)
        private val EDIT_IMPROVE_DETAILS_HINT_SEEN_KEY = booleanPreferencesKey("edit_improve_details_hint_seen")
        private val EDIT_CONDITION_PRICE_HINT_SEEN_KEY = booleanPreferencesKey("edit_condition_price_hint_seen")
        private val EDIT_USE_AI_HINT_SEEN_KEY = booleanPreferencesKey("edit_use_ai_hint_seen")
        private val EDIT_FTUE_COMPLETED_KEY = booleanPreferencesKey("edit_ftue_completed")

        // Settings FTUE flags (screen-scoped, per-step)
        private val SETTINGS_LANGUAGE_HINT_SEEN_KEY = booleanPreferencesKey("settings_language_hint_seen")
        private val SETTINGS_REPLAY_HINT_SEEN_KEY = booleanPreferencesKey("settings_replay_hint_seen")
        private val SETTINGS_FTUE_COMPLETED_KEY = booleanPreferencesKey("settings_ftue_completed")
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
     * Flow indicating whether the items list "tap to edit" hint has been shown.
     * Part of the items list FTUE sequence.
     * Defaults to false (not shown).
     */
    val listTapEditHintSeenFlow: Flow<Boolean> =
        context.ftueDataStore.data.map { preferences ->
            preferences[LIST_TAP_EDIT_HINT_SEEN_KEY] ?: false
        }

    /**
     * Flow indicating whether the items list "swipe to delete" hint has been shown.
     * Part of the items list FTUE sequence.
     * Defaults to false (not shown).
     */
    val listSwipeDeleteHintSeenFlow: Flow<Boolean> =
        context.ftueDataStore.data.map { preferences ->
            preferences[LIST_SWIPE_DELETE_HINT_SEEN_KEY] ?: false
        }

    /**
     * Flow indicating whether the items list "long-press to select" hint has been shown.
     * Part of the items list FTUE sequence.
     * Defaults to false (not shown).
     */
    val listLongPressHintSeenFlow: Flow<Boolean> =
        context.ftueDataStore.data.map { preferences ->
            preferences[LIST_LONG_PRESS_HINT_SEEN_KEY] ?: false
        }

    /**
     * Flow indicating whether the items list "share to sell" goal hint has been shown.
     * Part of the items list FTUE sequence.
     * Defaults to false (not shown).
     */
    val listShareGoalHintSeenFlow: Flow<Boolean> =
        context.ftueDataStore.data.map { preferences ->
            preferences[LIST_SHARE_GOAL_HINT_SEEN_KEY] ?: false
        }

    /**
     * Flow indicating whether the items list FTUE sequence has been completed.
     * Defaults to false (not completed).
     */
    val listFtueCompletedFlow: Flow<Boolean> =
        context.ftueDataStore.data.map { preferences ->
            preferences[LIST_FTUE_COMPLETED_KEY] ?: false
        }

    /**
     * Flow indicating whether the edit item FTUE sequence has been completed.
     * Defaults to false (not completed).
     */
    val editFtueCompletedFlow: Flow<Boolean> =
        context.ftueDataStore.data.map { preferences ->
            preferences[EDIT_FTUE_COMPLETED_KEY] ?: false
        }

    /**
     * Flow indicating whether the settings FTUE sequence has been completed.
     * Defaults to false (not completed).
     */
    val settingsFtueCompletedFlow: Flow<Boolean> =
        context.ftueDataStore.data.map { preferences ->
            preferences[SETTINGS_FTUE_COMPLETED_KEY] ?: false
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
     * Sets the items list "tap to edit" hint seen status.
     * @param seen True if the hint has been shown, false otherwise
     */
    suspend fun setListTapEditHintSeen(seen: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[LIST_TAP_EDIT_HINT_SEEN_KEY] = seen
        }
    }

    /**
     * Sets the items list "swipe to delete" hint seen status.
     * @param seen True if the hint has been shown, false otherwise
     */
    suspend fun setListSwipeDeleteHintSeen(seen: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[LIST_SWIPE_DELETE_HINT_SEEN_KEY] = seen
        }
    }

    /**
     * Sets the items list "long-press to select" hint seen status.
     * @param seen True if the hint has been shown, false otherwise
     */
    suspend fun setListLongPressHintSeen(seen: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[LIST_LONG_PRESS_HINT_SEEN_KEY] = seen
        }
    }

    /**
     * Sets the items list "share to sell" goal hint seen status.
     * @param seen True if the hint has been shown, false otherwise
     */
    suspend fun setListShareGoalHintSeen(seen: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[LIST_SHARE_GOAL_HINT_SEEN_KEY] = seen
        }
    }

    /**
     * Sets the items list FTUE completion status.
     * @param completed True if the items list FTUE sequence has been completed, false otherwise
     */
    suspend fun setListFtueCompleted(completed: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[LIST_FTUE_COMPLETED_KEY] = completed
        }
    }

    /**
     * Sets the edit item "improve details" hint seen status.
     * @param seen True if the hint has been shown, false otherwise
     */
    suspend fun setEditImproveDetailsHintSeen(seen: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[EDIT_IMPROVE_DETAILS_HINT_SEEN_KEY] = seen
        }
    }

    /**
     * Sets the edit item "condition/price" hint seen status.
     * @param seen True if the hint has been shown, false otherwise
     */
    suspend fun setEditConditionPriceHintSeen(seen: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[EDIT_CONDITION_PRICE_HINT_SEEN_KEY] = seen
        }
    }

    /**
     * Sets the edit item "use AI" hint seen status.
     * @param seen True if the hint has been shown, false otherwise
     */
    suspend fun setEditUseAiHintSeen(seen: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[EDIT_USE_AI_HINT_SEEN_KEY] = seen
        }
    }

    /**
     * Sets the edit item FTUE completion status.
     * @param completed True if the edit item FTUE sequence has been completed, false otherwise
     */
    suspend fun setEditFtueCompleted(completed: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[EDIT_FTUE_COMPLETED_KEY] = completed
        }
    }

    /**
     * Sets the settings "language" hint seen status.
     * @param seen True if the hint has been shown, false otherwise
     */
    suspend fun setSettingsLanguageHintSeen(seen: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[SETTINGS_LANGUAGE_HINT_SEEN_KEY] = seen
        }
    }

    /**
     * Sets the settings "replay guide" hint seen status.
     * @param seen True if the hint has been shown, false otherwise
     */
    suspend fun setSettingsReplayHintSeen(seen: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[SETTINGS_REPLAY_HINT_SEEN_KEY] = seen
        }
    }

    /**
     * Sets the settings FTUE completion status.
     * @param completed True if the settings FTUE sequence has been completed, false otherwise
     */
    suspend fun setSettingsFtueCompleted(completed: Boolean) {
        context.ftueDataStore.edit { preferences ->
            preferences[SETTINGS_FTUE_COMPLETED_KEY] = completed
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
            // Items List FTUE
            preferences[LIST_TAP_EDIT_HINT_SEEN_KEY] = false
            preferences[LIST_SWIPE_DELETE_HINT_SEEN_KEY] = false
            preferences[LIST_LONG_PRESS_HINT_SEEN_KEY] = false
            preferences[LIST_SHARE_GOAL_HINT_SEEN_KEY] = false
            preferences[LIST_FTUE_COMPLETED_KEY] = false
            // Edit Item FTUE
            preferences[EDIT_IMPROVE_DETAILS_HINT_SEEN_KEY] = false
            preferences[EDIT_CONDITION_PRICE_HINT_SEEN_KEY] = false
            preferences[EDIT_USE_AI_HINT_SEEN_KEY] = false
            preferences[EDIT_FTUE_COMPLETED_KEY] = false
            // Settings FTUE
            preferences[SETTINGS_LANGUAGE_HINT_SEEN_KEY] = false
            preferences[SETTINGS_REPLAY_HINT_SEEN_KEY] = false
            preferences[SETTINGS_FTUE_COMPLETED_KEY] = false
        }
    }
}

// DataStore extension property for FTUE preferences
private val Context.ftueDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ftue_preferences",
)
