package com.scanium.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scanium.app.model.user.UserEdition
import com.scanium.app.model.AssistantPrefs
import com.scanium.app.model.AssistantRegion
import com.scanium.app.model.AssistantTone
import com.scanium.app.model.AssistantUnits
import com.scanium.app.model.AssistantVerbosity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings_preferences"
)

class SettingsRepository(private val context: Context) {
    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val ALLOW_CLOUD_CLASSIFICATION_KEY = booleanPreferencesKey("allow_cloud_classification")
        private val ALLOW_ASSISTANT_KEY = booleanPreferencesKey("allow_assistant")
        private val SHARE_DIAGNOSTICS_KEY = booleanPreferencesKey("share_diagnostics")
        private val USER_EDITION_KEY = stringPreferencesKey("user_edition")
        private val DEVELOPER_MODE_KEY = booleanPreferencesKey("developer_mode")
        private val AUTO_SAVE_ENABLED_KEY = booleanPreferencesKey("auto_save_enabled")
        private val SAVE_DIRECTORY_URI_KEY = stringPreferencesKey("save_directory_uri")
        private val ALLOW_ASSISTANT_IMAGES_KEY = booleanPreferencesKey("allow_assistant_images")
        private val SOUNDS_ENABLED_KEY = booleanPreferencesKey("sounds_enabled")

        // Assistant personalization preferences
        private val ASSISTANT_LANGUAGE_KEY = stringPreferencesKey("assistant_language")
        private val ASSISTANT_TONE_KEY = stringPreferencesKey("assistant_tone")
        private val ASSISTANT_REGION_KEY = stringPreferencesKey("assistant_region")
        private val ASSISTANT_UNITS_KEY = stringPreferencesKey("assistant_units")
        private val ASSISTANT_VERBOSITY_KEY = stringPreferencesKey("assistant_verbosity")

        // Voice mode preferences
        private val VOICE_MODE_ENABLED_KEY = booleanPreferencesKey("voice_mode_enabled")
        private val SPEAK_ANSWERS_KEY = booleanPreferencesKey("speak_answers_enabled")
        private val AUTO_SEND_TRANSCRIPT_KEY = booleanPreferencesKey("auto_send_transcript")
        private val VOICE_LANGUAGE_KEY = stringPreferencesKey("voice_language")
        private val ASSISTANT_HAPTICS_KEY = booleanPreferencesKey("assistant_haptics_enabled")

        // Developer preferences
        private val DEV_ALLOW_SCREENSHOTS_KEY = booleanPreferencesKey("dev_allow_screenshots")
        private val DEV_SHOW_FTUE_BOUNDS_KEY = booleanPreferencesKey("dev_show_ftue_bounds")

        // Detection settings (developer toggles for beta)
        private val DEV_BARCODE_DETECTION_ENABLED_KEY = booleanPreferencesKey("dev_barcode_detection_enabled")
        private val DEV_DOCUMENT_DETECTION_ENABLED_KEY = booleanPreferencesKey("dev_document_detection_enabled")
        private val DEV_ADAPTIVE_THROTTLING_ENABLED_KEY = booleanPreferencesKey("dev_adaptive_throttling_enabled")
    }

    val themeModeFlow: Flow<ThemeMode> = context.settingsDataStore.data.map { preferences ->
        val raw = preferences[THEME_MODE_KEY]
        raw?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    val allowCloudClassificationFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[ALLOW_CLOUD_CLASSIFICATION_KEY] ?: true
    }

    suspend fun setAllowCloudClassification(allow: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[ALLOW_CLOUD_CLASSIFICATION_KEY] = allow
        }
    }

    val allowAssistantFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[ALLOW_ASSISTANT_KEY] ?: false
    }

    suspend fun setAllowAssistant(allow: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[ALLOW_ASSISTANT_KEY] = allow
        }
    }

    val shareDiagnosticsFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[SHARE_DIAGNOSTICS_KEY] ?: false
    }

    suspend fun setShareDiagnostics(share: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SHARE_DIAGNOSTICS_KEY] = share
        }
    }

    val userEditionFlow: Flow<UserEdition> = context.settingsDataStore.data.map { preferences ->
        val raw = preferences[USER_EDITION_KEY]
        raw?.let { runCatching { UserEdition.valueOf(it) }.getOrNull() } ?: UserEdition.FREE
    }

    suspend fun setUserEdition(edition: UserEdition) {
        context.settingsDataStore.edit { preferences ->
            preferences[USER_EDITION_KEY] = edition.name
        }
    }

    val developerModeFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[DEVELOPER_MODE_KEY] ?: false
    }

    suspend fun setDeveloperMode(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEVELOPER_MODE_KEY] = enabled
        }
    }

    val autoSaveEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[AUTO_SAVE_ENABLED_KEY] ?: false
    }

    suspend fun setAutoSaveEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_SAVE_ENABLED_KEY] = enabled
        }
    }

    val saveDirectoryUriFlow: Flow<String?> = context.settingsDataStore.data.map { preferences ->
        preferences[SAVE_DIRECTORY_URI_KEY]
    }

    suspend fun setSaveDirectoryUri(uri: String?) {
        context.settingsDataStore.edit { preferences ->
            if (uri != null) {
                preferences[SAVE_DIRECTORY_URI_KEY] = uri
            } else {
                preferences.remove(SAVE_DIRECTORY_URI_KEY)
            }
        }
    }

    /**
     * Whether to allow images to be sent to the AI assistant.
     * Default is OFF for privacy reasons.
     */
    val allowAssistantImagesFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[ALLOW_ASSISTANT_IMAGES_KEY] ?: false
    }

    suspend fun setAllowAssistantImages(allow: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[ALLOW_ASSISTANT_IMAGES_KEY] = allow
        }
    }

    /**
     * Whether app sounds are enabled.
     * Default is ON for subtle feedback.
     */
    val soundsEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[SOUNDS_ENABLED_KEY] ?: true
    }

    suspend fun setSoundsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SOUNDS_ENABLED_KEY] = enabled
        }
    }

    // =========================================================================
    // Assistant Personalization Preferences
    // =========================================================================

    val assistantLanguageFlow: Flow<String> = context.settingsDataStore.data.map { preferences ->
        preferences[ASSISTANT_LANGUAGE_KEY] ?: "EN"
    }

    suspend fun setAssistantLanguage(language: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[ASSISTANT_LANGUAGE_KEY] = language
        }
    }

    val assistantToneFlow: Flow<AssistantTone> = context.settingsDataStore.data.map { preferences ->
        val raw = preferences[ASSISTANT_TONE_KEY]
        raw?.let { runCatching { AssistantTone.valueOf(it) }.getOrNull() } ?: AssistantTone.NEUTRAL
    }

    suspend fun setAssistantTone(tone: AssistantTone) {
        context.settingsDataStore.edit { preferences ->
            preferences[ASSISTANT_TONE_KEY] = tone.name
        }
    }

    val assistantRegionFlow: Flow<AssistantRegion> = context.settingsDataStore.data.map { preferences ->
        val raw = preferences[ASSISTANT_REGION_KEY]
        raw?.let { runCatching { AssistantRegion.valueOf(it) }.getOrNull() } ?: AssistantRegion.EU
    }

    suspend fun setAssistantRegion(region: AssistantRegion) {
        context.settingsDataStore.edit { preferences ->
            preferences[ASSISTANT_REGION_KEY] = region.name
        }
    }

    val assistantUnitsFlow: Flow<AssistantUnits> = context.settingsDataStore.data.map { preferences ->
        val raw = preferences[ASSISTANT_UNITS_KEY]
        raw?.let { runCatching { AssistantUnits.valueOf(it) }.getOrNull() } ?: AssistantUnits.METRIC
    }

    suspend fun setAssistantUnits(units: AssistantUnits) {
        context.settingsDataStore.edit { preferences ->
            preferences[ASSISTANT_UNITS_KEY] = units.name
        }
    }

    val assistantVerbosityFlow: Flow<AssistantVerbosity> = context.settingsDataStore.data.map { preferences ->
        val raw = preferences[ASSISTANT_VERBOSITY_KEY]
        raw?.let { runCatching { AssistantVerbosity.valueOf(it) }.getOrNull() } ?: AssistantVerbosity.NORMAL
    }

    suspend fun setAssistantVerbosity(verbosity: AssistantVerbosity) {
        context.settingsDataStore.edit { preferences ->
            preferences[ASSISTANT_VERBOSITY_KEY] = verbosity.name
        }
    }

    /**
     * Combined flow that emits the current assistant preferences.
     * This can be collected to get all preferences as a single object.
     */
    val assistantPrefsFlow: Flow<AssistantPrefs> = combine(
        assistantLanguageFlow,
        assistantToneFlow,
        assistantRegionFlow,
        assistantUnitsFlow,
        assistantVerbosityFlow
    ) { language, tone, region, units, verbosity ->
        AssistantPrefs(
            language = language,
            tone = tone,
            region = region,
            units = units,
            verbosity = verbosity
        )
    }

    // =========================================================================
    // Voice Mode Preferences
    // =========================================================================

    /**
     * Whether voice mode is enabled (master toggle).
     * Default is OFF for privacy-first approach.
     */
    val voiceModeEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[VOICE_MODE_ENABLED_KEY] ?: false
    }

    suspend fun setVoiceModeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[VOICE_MODE_ENABLED_KEY] = enabled
        }
    }

    /**
     * Whether to speak assistant answers aloud (TTS).
     * Default is OFF for privacy-first approach.
     */
    val speakAnswersEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[SPEAK_ANSWERS_KEY] ?: false
    }

    suspend fun setSpeakAnswersEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SPEAK_ANSWERS_KEY] = enabled
        }
    }

    /**
     * Whether to automatically send message after transcription.
     * Default is OFF so users can edit transcripts for privacy.
     */
    val autoSendTranscriptFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[AUTO_SEND_TRANSCRIPT_KEY] ?: false
    }

    suspend fun setAutoSendTranscript(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_SEND_TRANSCRIPT_KEY] = enabled
        }
    }

    /**
     * Voice language for STT/TTS.
     * Empty string means follow assistant language setting.
     */
    val voiceLanguageFlow: Flow<String> = context.settingsDataStore.data.map { preferences ->
        preferences[VOICE_LANGUAGE_KEY] ?: ""
    }

    suspend fun setVoiceLanguage(language: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[VOICE_LANGUAGE_KEY] = language
        }
    }

    /**
     * Developer preference to allow screenshots.
     * Default true to avoid disrupting debugging workflows.
     */
    val devAllowScreenshotsFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[DEV_ALLOW_SCREENSHOTS_KEY] ?: true
    }

    suspend fun setDevAllowScreenshots(allowed: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEV_ALLOW_SCREENSHOTS_KEY] = allowed
        }
    }

    /**
     * Developer preference to show FTUE debug bounds.
     * Default false to avoid visual clutter.
     */
    val devShowFtueBoundsFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[DEV_SHOW_FTUE_BOUNDS_KEY] ?: false
    }

    suspend fun setDevShowFtueBounds(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEV_SHOW_FTUE_BOUNDS_KEY] = enabled
        }
    }

    /**
     * Whether assistant interactions should trigger haptic feedback.
     * Default is OFF.
     */
    val assistantHapticsEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[ASSISTANT_HAPTICS_KEY] ?: false
    }

    suspend fun setAssistantHapticsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[ASSISTANT_HAPTICS_KEY] = enabled
        }
    }

    // =========================================================================
    // Detection Settings (Developer Toggles for Beta)
    // =========================================================================

    /**
     * Whether barcode/QR detection is enabled.
     * Default is ON for beta.
     */
    val devBarcodeDetectionEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[DEV_BARCODE_DETECTION_ENABLED_KEY] ?: true
    }

    suspend fun setDevBarcodeDetectionEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEV_BARCODE_DETECTION_ENABLED_KEY] = enabled
        }
    }

    /**
     * Whether document candidate detection is enabled.
     * Default is ON for beta.
     */
    val devDocumentDetectionEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[DEV_DOCUMENT_DETECTION_ENABLED_KEY] ?: true
    }

    suspend fun setDevDocumentDetectionEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEV_DOCUMENT_DETECTION_ENABLED_KEY] = enabled
        }
    }

    /**
     * Whether adaptive throttling (low-power mode) is enabled.
     * Default is ON for beta.
     */
    val devAdaptiveThrottlingEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[DEV_ADAPTIVE_THROTTLING_ENABLED_KEY] ?: true
    }

    suspend fun setDevAdaptiveThrottlingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEV_ADAPTIVE_THROTTLING_ENABLED_KEY] = enabled
        }
    }

    // =========================================================================
    // Privacy Safe Mode
    // =========================================================================

    /**
     * Activates Privacy Safe Mode by disabling all cloud data sharing features.
     * This is a one-tap way to ensure no data leaves the device.
     *
     * Disables:
     * - Cloud classification
     * - Assistant images
     * - Share diagnostics
     *
     * Note: Does NOT disable voice mode (local STT) as it uses on-device processing.
     */
    suspend fun enablePrivacySafeMode() {
        context.settingsDataStore.edit { preferences ->
            preferences[ALLOW_CLOUD_CLASSIFICATION_KEY] = false
            preferences[ALLOW_ASSISTANT_IMAGES_KEY] = false
            preferences[SHARE_DIAGNOSTICS_KEY] = false
        }
    }

    /**
     * Checks if Privacy Safe Mode is effectively active.
     * Returns true if all cloud sharing features are disabled.
     */
    val isPrivacySafeModeActiveFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        val cloudOff = !(preferences[ALLOW_CLOUD_CLASSIFICATION_KEY] ?: true)
        val imagesOff = !(preferences[ALLOW_ASSISTANT_IMAGES_KEY] ?: false)
        val diagnosticsOff = !(preferences[SHARE_DIAGNOSTICS_KEY] ?: false)
        cloudOff && imagesOff && diagnosticsOff
    }

    /**
     * Resets all privacy-related settings to their default values.
     *
     * Defaults:
     * - Cloud classification: ON (default true)
     * - Assistant: OFF (default false)
     * - Assistant images: OFF (default false)
     * - Voice mode: OFF (default false)
     * - Speak answers: OFF (default false)
     * - Share diagnostics: OFF (default false)
     */
    suspend fun resetPrivacySettings() {
        context.settingsDataStore.edit { preferences ->
            preferences[ALLOW_CLOUD_CLASSIFICATION_KEY] = true
            preferences[ALLOW_ASSISTANT_KEY] = false
            preferences[ALLOW_ASSISTANT_IMAGES_KEY] = false
            preferences[VOICE_MODE_ENABLED_KEY] = false
            preferences[SPEAK_ANSWERS_KEY] = false
            preferences[AUTO_SEND_TRANSCRIPT_KEY] = false
            preferences[SHARE_DIAGNOSTICS_KEY] = false
        }
    }
}
