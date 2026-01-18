package com.scanium.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.scanium.app.config.FeatureFlags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DeveloperSettings(
    private val dataStore: DataStore<Preferences>,
) {
    val developerModeFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            when {
                FeatureFlags.isDevBuild -> true
                !FeatureFlags.allowDeveloperMode -> false
                else -> preferences[SettingsKeys.Developer.DEVELOPER_MODE_KEY] ?: false
            }
        }

    suspend fun setDeveloperMode(enabled: Boolean) {
        if (FeatureFlags.isDevBuild) return
        if (!FeatureFlags.allowDeveloperMode) return
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Developer.DEVELOPER_MODE_KEY] = enabled
        }
    }

    val devAllowScreenshotsFlow: Flow<Boolean> =
        dataStore.data.safeMap(FeatureFlags.allowScreenshots) { preferences ->
            if (!FeatureFlags.allowScreenshots) {
                false
            } else {
                preferences[SettingsKeys.Developer.DEV_ALLOW_SCREENSHOTS_KEY] ?: true
            }
        }

    suspend fun setDevAllowScreenshots(allowed: Boolean) {
        if (!FeatureFlags.allowScreenshots) return
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Developer.DEV_ALLOW_SCREENSHOTS_KEY] = allowed
        }
    }

    val devShowFtueBoundsFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Developer.DEV_SHOW_FTUE_BOUNDS_KEY] ?: false
        }

    suspend fun setDevShowFtueBounds(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Developer.DEV_SHOW_FTUE_BOUNDS_KEY] = enabled
        }
    }

    val devBarcodeDetectionEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Developer.DEV_BARCODE_DETECTION_ENABLED_KEY] ?: true
        }

    suspend fun setDevBarcodeDetectionEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Developer.DEV_BARCODE_DETECTION_ENABLED_KEY] = enabled
        }
    }

    val devDocumentDetectionEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Developer.DEV_DOCUMENT_DETECTION_ENABLED_KEY] ?: true
        }

    suspend fun setDevDocumentDetectionEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Developer.DEV_DOCUMENT_DETECTION_ENABLED_KEY] = enabled
        }
    }

    val devAdaptiveThrottlingEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Developer.DEV_ADAPTIVE_THROTTLING_ENABLED_KEY] ?: true
        }

    suspend fun setDevAdaptiveThrottlingEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Developer.DEV_ADAPTIVE_THROTTLING_ENABLED_KEY] = enabled
        }
    }

    val devScanningDiagnosticsEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Developer.DEV_SCANNING_DIAGNOSTICS_ENABLED_KEY] ?: false
        }

    suspend fun setDevScanningDiagnosticsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Developer.DEV_SCANNING_DIAGNOSTICS_ENABLED_KEY] = enabled
        }
    }

    val devRoiDiagnosticsEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Developer.DEV_ROI_DIAGNOSTICS_ENABLED_KEY] ?: false
        }

    suspend fun setDevRoiDiagnosticsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Developer.DEV_ROI_DIAGNOSTICS_ENABLED_KEY] = enabled
        }
    }

    val devBboxMappingDebugEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Developer.DEV_BBOX_MAPPING_DEBUG_KEY] ?: false
        }

    suspend fun setDevBboxMappingDebugEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Developer.DEV_BBOX_MAPPING_DEBUG_KEY] = enabled
        }
    }

    val devCorrelationDebugEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Developer.DEV_CORRELATION_DEBUG_KEY] ?: false
        }

    suspend fun setDevCorrelationDebugEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Developer.DEV_CORRELATION_DEBUG_KEY] = enabled
        }
    }

    val devCameraPipelineDebugEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Developer.DEV_CAMERA_PIPELINE_DEBUG_KEY] ?: false
        }

    suspend fun setDevCameraPipelineDebugEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Developer.DEV_CAMERA_PIPELINE_DEBUG_KEY] = enabled
        }
    }

    val devMotionOverlaysEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Developer.DEV_MOTION_OVERLAYS_ENABLED_KEY] ?: true
        }

    suspend fun setDevMotionOverlaysEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Developer.DEV_MOTION_OVERLAYS_ENABLED_KEY] = enabled
        }
    }

    val devOverlayAccuracyStepFlow: Flow<Int> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Developer.DEV_OVERLAY_ACCURACY_STEP_KEY] ?: 0
        }

    suspend fun setDevOverlayAccuracyStep(stepIndex: Int) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Developer.DEV_OVERLAY_ACCURACY_STEP_KEY] = stepIndex
        }
    }

    val devShowCameraUiFtueBoundsFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Developer.DEV_SHOW_CAMERA_UI_FTUE_BOUNDS_KEY] ?: false
        }

    suspend fun setDevShowCameraUiFtueBounds(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Developer.DEV_SHOW_CAMERA_UI_FTUE_BOUNDS_KEY] = enabled
        }
    }

    val devShowBuildWatermarkFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Developer.DEV_SHOW_BUILD_WATERMARK_KEY] ?: false
        }

    suspend fun setDevShowBuildWatermark(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Developer.DEV_SHOW_BUILD_WATERMARK_KEY] = enabled
        }
    }
}
