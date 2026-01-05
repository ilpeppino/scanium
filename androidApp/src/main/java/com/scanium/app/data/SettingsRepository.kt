package com.scanium.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scanium.app.config.FeatureFlags
import com.scanium.app.model.AssistantPrefs
import com.scanium.app.model.AssistantRegion
import com.scanium.app.model.AssistantTone
import com.scanium.app.model.AssistantUnits
import com.scanium.app.model.AssistantVerbosity
import com.scanium.app.model.user.UserEdition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings_preferences",
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
        private val DEV_SCANNING_DIAGNOSTICS_ENABLED_KEY = booleanPreferencesKey("dev_scanning_diagnostics_enabled")

        // Scanning guidance settings
        private val SCANNING_GUIDANCE_ENABLED_KEY = booleanPreferencesKey("scanning_guidance_enabled")
        private val DEV_ROI_DIAGNOSTICS_ENABLED_KEY = booleanPreferencesKey("dev_roi_diagnostics_enabled")

        // Bbox mapping debug settings
        private val DEV_BBOX_MAPPING_DEBUG_KEY = booleanPreferencesKey("dev_bbox_mapping_debug_enabled")
        private val DEV_CORRELATION_DEBUG_KEY = booleanPreferencesKey("dev_correlation_debug_enabled")

        // Camera pipeline lifecycle debug
        private val DEV_CAMERA_PIPELINE_DEBUG_KEY = booleanPreferencesKey("dev_camera_pipeline_debug_enabled")

        // Motion overlays toggle
        private val DEV_MOTION_OVERLAYS_ENABLED_KEY = booleanPreferencesKey("dev_motion_overlays_enabled")

        // Overlay accuracy filter (developer debug feature)
        private val DEV_OVERLAY_ACCURACY_STEP_KEY = intPreferencesKey("dev_overlay_accuracy_step")
    }

    val themeModeFlow: Flow<ThemeMode> =
        context.settingsDataStore.data.map { preferences ->
            val raw = preferences[THEME_MODE_KEY]
            raw?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    val allowCloudClassificationFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[ALLOW_CLOUD_CLASSIFICATION_KEY] ?: true
        }

    suspend fun setAllowCloudClassification(allow: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[ALLOW_CLOUD_CLASSIFICATION_KEY] = allow
        }
    }

    val allowAssistantFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[ALLOW_ASSISTANT_KEY] ?: false
        }

    suspend fun setAllowAssistant(allow: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[ALLOW_ASSISTANT_KEY] = allow
        }
    }

    val shareDiagnosticsFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[SHARE_DIAGNOSTICS_KEY] ?: false
        }

    suspend fun setShareDiagnostics(share: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SHARE_DIAGNOSTICS_KEY] = share
        }
    }

    val userEditionFlow: Flow<UserEdition> =
        context.settingsDataStore.data.map { preferences ->
            val raw = preferences[USER_EDITION_KEY]
            raw?.let { runCatching { UserEdition.valueOf(it) }.getOrNull() } ?: UserEdition.FREE
        }

    suspend fun setUserEdition(edition: UserEdition) {
        context.settingsDataStore.edit { preferences ->
            preferences[USER_EDITION_KEY] = edition.name
        }
    }

    /**
     * Developer mode flow - clamped to false in beta/prod builds.
     * In beta/prod, this always returns false regardless of stored preference.
     */
    val developerModeFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            // Clamp to false if developer mode is not allowed by feature flags
            if (!FeatureFlags.allowDeveloperMode) {
                false
            } else {
                preferences[DEVELOPER_MODE_KEY] ?: false
            }
        }

    suspend fun setDeveloperMode(enabled: Boolean) {
        // Only persist if developer mode is allowed
        if (!FeatureFlags.allowDeveloperMode) return
        context.settingsDataStore.edit { preferences ->
            preferences[DEVELOPER_MODE_KEY] = enabled
        }
    }

    val autoSaveEnabledFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[AUTO_SAVE_ENABLED_KEY] ?: false
        }

    suspend fun setAutoSaveEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUTO_SAVE_ENABLED_KEY] = enabled
        }
    }

    val saveDirectoryUriFlow: Flow<String?> =
        context.settingsDataStore.data.map { preferences ->
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
    val allowAssistantImagesFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
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
    val soundsEnabledFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
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

    val assistantLanguageFlow: Flow<String> =
        context.settingsDataStore.data.map { preferences ->
            preferences[ASSISTANT_LANGUAGE_KEY] ?: "EN"
        }

    suspend fun setAssistantLanguage(language: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[ASSISTANT_LANGUAGE_KEY] = language
        }
    }

    val assistantToneFlow: Flow<AssistantTone> =
        context.settingsDataStore.data.map { preferences ->
            val raw = preferences[ASSISTANT_TONE_KEY]
            raw?.let { runCatching { AssistantTone.valueOf(it) }.getOrNull() } ?: AssistantTone.NEUTRAL
        }

    suspend fun setAssistantTone(tone: AssistantTone) {
        context.settingsDataStore.edit { preferences ->
            preferences[ASSISTANT_TONE_KEY] = tone.name
        }
    }

    val assistantRegionFlow: Flow<AssistantRegion> =
        context.settingsDataStore.data.map { preferences ->
            val raw = preferences[ASSISTANT_REGION_KEY]
            raw?.let { runCatching { AssistantRegion.valueOf(it) }.getOrNull() } ?: AssistantRegion.EU
        }

    suspend fun setAssistantRegion(region: AssistantRegion) {
        context.settingsDataStore.edit { preferences ->
            preferences[ASSISTANT_REGION_KEY] = region.name
        }
    }

    val assistantUnitsFlow: Flow<AssistantUnits> =
        context.settingsDataStore.data.map { preferences ->
            val raw = preferences[ASSISTANT_UNITS_KEY]
            raw?.let { runCatching { AssistantUnits.valueOf(it) }.getOrNull() } ?: AssistantUnits.METRIC
        }

    suspend fun setAssistantUnits(units: AssistantUnits) {
        context.settingsDataStore.edit { preferences ->
            preferences[ASSISTANT_UNITS_KEY] = units.name
        }
    }

    val assistantVerbosityFlow: Flow<AssistantVerbosity> =
        context.settingsDataStore.data.map { preferences ->
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
    val assistantPrefsFlow: Flow<AssistantPrefs> =
        combine(
            assistantLanguageFlow,
            assistantToneFlow,
            assistantRegionFlow,
            assistantUnitsFlow,
            assistantVerbosityFlow,
        ) { language, tone, region, units, verbosity ->
            AssistantPrefs(
                language = language,
                tone = tone,
                region = region,
                units = units,
                verbosity = verbosity,
            )
        }

    // =========================================================================
    // Voice Mode Preferences
    // =========================================================================

    /**
     * Whether voice mode is enabled (master toggle).
     * Default is OFF for privacy-first approach.
     */
    val voiceModeEnabledFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
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
    val speakAnswersEnabledFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
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
    val autoSendTranscriptFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
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
    val voiceLanguageFlow: Flow<String> =
        context.settingsDataStore.data.map { preferences ->
            preferences[VOICE_LANGUAGE_KEY] ?: ""
        }

    suspend fun setVoiceLanguage(language: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[VOICE_LANGUAGE_KEY] = language
        }
    }

    /**
     * Developer preference to allow screenshots.
     * Default true in dev builds to avoid disrupting debugging workflows.
     * In beta/prod builds, this is always false regardless of stored preference.
     */
    val devAllowScreenshotsFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            // Clamp to false if screenshots are not allowed by feature flags
            if (!FeatureFlags.allowScreenshots) {
                false
            } else {
                preferences[DEV_ALLOW_SCREENSHOTS_KEY] ?: true
            }
        }

    suspend fun setDevAllowScreenshots(allowed: Boolean) {
        // Only persist if screenshots are allowed
        if (!FeatureFlags.allowScreenshots) return
        context.settingsDataStore.edit { preferences ->
            preferences[DEV_ALLOW_SCREENSHOTS_KEY] = allowed
        }
    }

    /**
     * Developer preference to show FTUE debug bounds.
     * Default false to avoid visual clutter.
     */
    val devShowFtueBoundsFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
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
    val assistantHapticsEnabledFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
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
    val devBarcodeDetectionEnabledFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
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
    val devDocumentDetectionEnabledFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
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
    val devAdaptiveThrottlingEnabledFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[DEV_ADAPTIVE_THROTTLING_ENABLED_KEY] ?: true
        }

    suspend fun setDevAdaptiveThrottlingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEV_ADAPTIVE_THROTTLING_ENABLED_KEY] = enabled
        }
    }

    /**
     * Whether scanning diagnostics logging is enabled.
     * When enabled, detailed ScanPipeline logs are emitted for debugging.
     * Default is OFF to reduce log noise.
     */
    val devScanningDiagnosticsEnabledFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[DEV_SCANNING_DIAGNOSTICS_ENABLED_KEY] ?: false
        }

    suspend fun setDevScanningDiagnosticsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEV_SCANNING_DIAGNOSTICS_ENABLED_KEY] = enabled
        }
    }

    // =========================================================================
    // Scanning Guidance Settings
    // =========================================================================

    /**
     * Whether the scanning guidance overlay is enabled.
     * Shows the scan zone, hints, and visual feedback during live scanning.
     * Default is ON for improved UX.
     */
    val scanningGuidanceEnabledFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[SCANNING_GUIDANCE_ENABLED_KEY] ?: true
        }

    suspend fun setScanningGuidanceEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SCANNING_GUIDANCE_ENABLED_KEY] = enabled
        }
    }

    /**
     * Whether ROI diagnostics overlay is enabled (developer toggle).
     * Shows numeric values for ROI size, sharpness, center distance, lock state, etc.
     * Default is OFF to avoid visual clutter in production.
     */
    val devRoiDiagnosticsEnabledFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[DEV_ROI_DIAGNOSTICS_ENABLED_KEY] ?: false
        }

    suspend fun setDevRoiDiagnosticsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEV_ROI_DIAGNOSTICS_ENABLED_KEY] = enabled
        }
    }

    /**
     * Whether bbox mapping debug overlay is enabled (developer toggle).
     * Shows rotation, scale, offset, and effective dimensions for portrait/landscape debugging.
     * Default is OFF to avoid visual clutter in production.
     */
    val devBboxMappingDebugEnabledFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[DEV_BBOX_MAPPING_DEBUG_KEY] ?: false
        }

    suspend fun setDevBboxMappingDebugEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEV_BBOX_MAPPING_DEBUG_KEY] = enabled
        }
    }

    /**
     * Whether bbox↔snapshot correlation debug is enabled (developer toggle).
     * Shows aspect ratio validation, logs once per second with tag CORR.
     * Default is OFF to avoid performance impact.
     */
    val devCorrelationDebugEnabledFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[DEV_CORRELATION_DEBUG_KEY] ?: false
        }

    suspend fun setDevCorrelationDebugEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEV_CORRELATION_DEBUG_KEY] = enabled
        }
    }

    /**
     * Whether camera pipeline lifecycle debug overlay is enabled (developer toggle).
     * Shows isCameraBound, isAnalysisRunning, lastFrameTimestamp, lastBboxTimestamp,
     * framesPerSecond, currentLifecycleState, and navDestination.
     * Also enables CAM_LIFE log tag for lifecycle events.
     * Default is OFF to avoid visual clutter.
     */
    val devCameraPipelineDebugEnabledFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[DEV_CAMERA_PIPELINE_DEBUG_KEY] ?: false
        }

    suspend fun setDevCameraPipelineDebugEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEV_CAMERA_PIPELINE_DEBUG_KEY] = enabled
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
    val isPrivacySafeModeActiveFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
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

    // =========================================================================
    // Motion Overlays Settings
    // =========================================================================

    /**
     * Whether motion overlays (scan frame appear, lightning pulse) are enabled.
     * Part of Scanium motion language system.
     * Default is ON for enhanced visual feedback.
     */
    val devMotionOverlaysEnabledFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[DEV_MOTION_OVERLAYS_ENABLED_KEY] ?: true
        }

    suspend fun setDevMotionOverlaysEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEV_MOTION_OVERLAYS_ENABLED_KEY] = enabled
        }
    }

    // =========================================================================
    // Overlay Accuracy Filter (Developer Debug Feature)
    // =========================================================================

    /**
     * Current step index for overlay accuracy filter (debug feature).
     * Controls which bboxes are shown based on confidence threshold.
     *
     * Step 0 = "All" (show everything)
     * Higher steps = filter to higher confidence only
     *
     * Default is 0 (show all detections).
     *
     * @see com.scanium.app.camera.ConfidenceTiers for tier definitions
     */
    val devOverlayAccuracyStepFlow: Flow<Int> =
        context.settingsDataStore.data.map { preferences ->
            preferences[DEV_OVERLAY_ACCURACY_STEP_KEY] ?: 0
        }

    /**
     * Set the overlay accuracy filter step index.
     *
     * @param stepIndex Index of the tier (0 = show all, higher = more filtering)
     */
    suspend fun setDevOverlayAccuracyStep(stepIndex: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEV_OVERLAY_ACCURACY_STEP_KEY] = stepIndex
        }
    }
}
