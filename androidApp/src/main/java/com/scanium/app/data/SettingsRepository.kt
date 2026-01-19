package com.scanium.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.scanium.app.model.AiLanguageChoice
import com.scanium.app.model.AppLanguage
import com.scanium.app.model.AssistantPrefs
import com.scanium.app.model.AssistantRegion
import com.scanium.app.model.AssistantTone
import com.scanium.app.model.AssistantUnits
import com.scanium.app.model.AssistantVerbosity
import com.scanium.app.model.FollowOrCustom
import com.scanium.app.model.TtsLanguageChoice
import com.scanium.app.model.user.UserEdition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SettingsRepository(
    private val context: Context,
    private val dataStore: DataStore<Preferences> = context.settingsDataStore,
) {
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val migrations = SettingsMigrations(dataStore)
    private val generalSettings = GeneralSettings(dataStore)
    private val assistantSettings = AssistantSettings(dataStore)
    private val voiceSettings = VoiceSettings(dataStore)
    private val developerSettings = DeveloperSettings(dataStore)
    private val scanningSettings = ScanningSettings(dataStore)
    private val privacySettings = PrivacySettings(dataStore)
    private val unifiedSettings = UnifiedSettings(dataStore)

    init {
        initScope.launch {
            migrations.runMigrationIfNeeded()
        }
    }

    val themeModeFlow: Flow<ThemeMode> = generalSettings.themeModeFlow

    suspend fun setThemeMode(mode: ThemeMode) {
        generalSettings.setThemeMode(mode)
    }

    val appLanguageFlow: Flow<AppLanguage> = generalSettings.appLanguageFlow

    suspend fun setAppLanguage(language: AppLanguage) {
        generalSettings.setAppLanguage(language)
    }

    val allowCloudClassificationFlow: Flow<Boolean> = generalSettings.allowCloudClassificationFlow

    suspend fun setAllowCloudClassification(allow: Boolean) {
        generalSettings.setAllowCloudClassification(allow)
    }

    val allowAssistantFlow: Flow<Boolean> = assistantSettings.allowAssistantFlow

    suspend fun setAllowAssistant(allow: Boolean) {
        assistantSettings.setAllowAssistant(allow)
    }

    val shareDiagnosticsFlow: Flow<Boolean> = generalSettings.shareDiagnosticsFlow

    suspend fun setShareDiagnostics(share: Boolean) {
        generalSettings.setShareDiagnostics(share)
    }

    val userEditionFlow: Flow<UserEdition> = generalSettings.userEditionFlow

    suspend fun setUserEdition(edition: UserEdition) {
        generalSettings.setUserEdition(edition)
    }

    val developerModeFlow: Flow<Boolean> = developerSettings.developerModeFlow

    suspend fun setDeveloperMode(enabled: Boolean) {
        developerSettings.setDeveloperMode(enabled)
    }

    val autoSaveEnabledFlow: Flow<Boolean> = generalSettings.autoSaveEnabledFlow

    suspend fun setAutoSaveEnabled(enabled: Boolean) {
        generalSettings.setAutoSaveEnabled(enabled)
    }

    val saveDirectoryUriFlow: Flow<String?> = generalSettings.saveDirectoryUriFlow

    suspend fun setSaveDirectoryUri(uri: String?) {
        generalSettings.setSaveDirectoryUri(uri)
    }

    val exportFormatFlow: Flow<String> = generalSettings.exportFormatFlow

    suspend fun setExportFormat(format: String) {
        generalSettings.setExportFormat(format)
    }

    val allowAssistantImagesFlow: Flow<Boolean> = assistantSettings.allowAssistantImagesFlow

    suspend fun setAllowAssistantImages(allow: Boolean) {
        assistantSettings.setAllowAssistantImages(allow)
    }

    val soundsEnabledFlow: Flow<Boolean> = generalSettings.soundsEnabledFlow

    suspend fun setSoundsEnabled(enabled: Boolean) {
        generalSettings.setSoundsEnabled(enabled)
    }

    val assistantLanguageFlow: Flow<String> = assistantSettings.assistantLanguageFlow

    suspend fun setAssistantLanguage(language: String) {
        assistantSettings.setAssistantLanguage(language)
    }

    val assistantToneFlow: Flow<AssistantTone> = assistantSettings.assistantToneFlow

    suspend fun setAssistantTone(tone: AssistantTone) {
        assistantSettings.setAssistantTone(tone)
    }

    val assistantCountryCodeFlow: Flow<String> = assistantSettings.assistantCountryCodeFlow

    @Deprecated("Use assistantCountryCodeFlow for full country support")
    val assistantRegionFlow: Flow<AssistantRegion> = assistantSettings.assistantRegionFlow

    suspend fun setAssistantCountryCode(countryCode: String) {
        assistantSettings.setAssistantCountryCode(countryCode)
    }

    @Deprecated("Use setAssistantCountryCode for full country support")
    suspend fun setAssistantRegion(region: AssistantRegion) {
        assistantSettings.setAssistantRegion(region)
    }

    val assistantUnitsFlow: Flow<AssistantUnits> = assistantSettings.assistantUnitsFlow

    suspend fun setAssistantUnits(units: AssistantUnits) {
        assistantSettings.setAssistantUnits(units)
    }

    val assistantVerbosityFlow: Flow<AssistantVerbosity> = assistantSettings.assistantVerbosityFlow

    suspend fun setAssistantVerbosity(verbosity: AssistantVerbosity) {
        assistantSettings.setAssistantVerbosity(verbosity)
    }

    // ISSUE-3 FIX: Combine base assistant prefs with unified language setting
    // Users set language in General settings (primaryLanguageFlow), which should drive AI output
    val assistantPrefsFlow: Flow<AssistantPrefs> =
        combine(
            assistantSettings.assistantPrefsFlow,
            unifiedSettings.effectiveAiOutputLanguageFlow,
        ) { basePrefs, unifiedLanguage ->
            basePrefs.copy(language = unifiedLanguage)
        }

    val voiceModeEnabledFlow: Flow<Boolean> = voiceSettings.voiceModeEnabledFlow

    suspend fun setVoiceModeEnabled(enabled: Boolean) {
        voiceSettings.setVoiceModeEnabled(enabled)
    }

    val speakAnswersEnabledFlow: Flow<Boolean> = voiceSettings.speakAnswersEnabledFlow

    suspend fun setSpeakAnswersEnabled(enabled: Boolean) {
        voiceSettings.setSpeakAnswersEnabled(enabled)
    }

    val autoSendTranscriptFlow: Flow<Boolean> = voiceSettings.autoSendTranscriptFlow

    suspend fun setAutoSendTranscript(enabled: Boolean) {
        voiceSettings.setAutoSendTranscript(enabled)
    }

    val voiceLanguageFlow: Flow<String> = voiceSettings.voiceLanguageFlow

    suspend fun setVoiceLanguage(language: String) {
        voiceSettings.setVoiceLanguage(language)
    }

    val devAllowScreenshotsFlow: Flow<Boolean> = developerSettings.devAllowScreenshotsFlow

    suspend fun setDevAllowScreenshots(allowed: Boolean) {
        developerSettings.setDevAllowScreenshots(allowed)
    }

    val devShowFtueBoundsFlow: Flow<Boolean> = developerSettings.devShowFtueBoundsFlow

    suspend fun setDevShowFtueBounds(enabled: Boolean) {
        developerSettings.setDevShowFtueBounds(enabled)
    }

    val assistantHapticsEnabledFlow: Flow<Boolean> = voiceSettings.assistantHapticsEnabledFlow

    suspend fun setAssistantHapticsEnabled(enabled: Boolean) {
        voiceSettings.setAssistantHapticsEnabled(enabled)
    }

    val devBarcodeDetectionEnabledFlow: Flow<Boolean> = developerSettings.devBarcodeDetectionEnabledFlow

    suspend fun setDevBarcodeDetectionEnabled(enabled: Boolean) {
        developerSettings.setDevBarcodeDetectionEnabled(enabled)
    }

    val devDocumentDetectionEnabledFlow: Flow<Boolean> = developerSettings.devDocumentDetectionEnabledFlow

    suspend fun setDevDocumentDetectionEnabled(enabled: Boolean) {
        developerSettings.setDevDocumentDetectionEnabled(enabled)
    }

    val devAdaptiveThrottlingEnabledFlow: Flow<Boolean> = developerSettings.devAdaptiveThrottlingEnabledFlow

    suspend fun setDevAdaptiveThrottlingEnabled(enabled: Boolean) {
        developerSettings.setDevAdaptiveThrottlingEnabled(enabled)
    }

    val devScanningDiagnosticsEnabledFlow: Flow<Boolean> = developerSettings.devScanningDiagnosticsEnabledFlow

    suspend fun setDevScanningDiagnosticsEnabled(enabled: Boolean) {
        developerSettings.setDevScanningDiagnosticsEnabled(enabled)
    }

    val scanningGuidanceEnabledFlow: Flow<Boolean> = scanningSettings.scanningGuidanceEnabledFlow

    suspend fun setScanningGuidanceEnabled(enabled: Boolean) {
        scanningSettings.setScanningGuidanceEnabled(enabled)
    }

    val devRoiDiagnosticsEnabledFlow: Flow<Boolean> = developerSettings.devRoiDiagnosticsEnabledFlow

    suspend fun setDevRoiDiagnosticsEnabled(enabled: Boolean) {
        developerSettings.setDevRoiDiagnosticsEnabled(enabled)
    }

    val devBboxMappingDebugEnabledFlow: Flow<Boolean> = developerSettings.devBboxMappingDebugEnabledFlow

    suspend fun setDevBboxMappingDebugEnabled(enabled: Boolean) {
        developerSettings.setDevBboxMappingDebugEnabled(enabled)
    }

    val devCorrelationDebugEnabledFlow: Flow<Boolean> = developerSettings.devCorrelationDebugEnabledFlow

    suspend fun setDevCorrelationDebugEnabled(enabled: Boolean) {
        developerSettings.setDevCorrelationDebugEnabled(enabled)
    }

    val devCameraPipelineDebugEnabledFlow: Flow<Boolean> = developerSettings.devCameraPipelineDebugEnabledFlow

    suspend fun setDevCameraPipelineDebugEnabled(enabled: Boolean) {
        developerSettings.setDevCameraPipelineDebugEnabled(enabled)
    }

    suspend fun enablePrivacySafeMode() {
        privacySettings.enablePrivacySafeMode()
    }

    val isPrivacySafeModeActiveFlow: Flow<Boolean> = privacySettings.isPrivacySafeModeActiveFlow

    suspend fun resetPrivacySettings() {
        privacySettings.resetPrivacySettings()
    }

    val devMotionOverlaysEnabledFlow: Flow<Boolean> = developerSettings.devMotionOverlaysEnabledFlow

    suspend fun setDevMotionOverlaysEnabled(enabled: Boolean) {
        developerSettings.setDevMotionOverlaysEnabled(enabled)
    }

    val showDetectionBoxesFlow: Flow<Boolean> = scanningSettings.showDetectionBoxesFlow

    suspend fun setShowDetectionBoxes(enabled: Boolean) {
        scanningSettings.setShowDetectionBoxes(enabled)
    }

    val devOverlayAccuracyStepFlow: Flow<Int> = developerSettings.devOverlayAccuracyStepFlow

    suspend fun setDevOverlayAccuracyStep(stepIndex: Int) {
        developerSettings.setDevOverlayAccuracyStep(stepIndex)
    }

    val devShowCameraUiFtueBoundsFlow: Flow<Boolean> = developerSettings.devShowCameraUiFtueBoundsFlow

    suspend fun setDevShowCameraUiFtueBounds(enabled: Boolean) {
        developerSettings.setDevShowCameraUiFtueBounds(enabled)
    }

    val devShowBuildWatermarkFlow: Flow<Boolean> = developerSettings.devShowBuildWatermarkFlow

    suspend fun setDevShowBuildWatermark(enabled: Boolean) {
        developerSettings.setDevShowBuildWatermark(enabled)
    }

    val openItemListAfterScanFlow: Flow<Boolean> = scanningSettings.openItemListAfterScanFlow

    suspend fun setOpenItemListAfterScan(enabled: Boolean) {
        scanningSettings.setOpenItemListAfterScan(enabled)
    }

    val primaryRegionCountryFlow: Flow<String> = unifiedSettings.primaryRegionCountryFlow

    suspend fun setPrimaryRegionCountry(countryCode: String) {
        unifiedSettings.setPrimaryRegionCountry(countryCode)
    }

    val primaryLanguageFlow: Flow<String> = unifiedSettings.primaryLanguageFlow

    suspend fun setPrimaryLanguage(languageTag: String) {
        unifiedSettings.setPrimaryLanguage(languageTag)
    }

    val appLanguageSettingFlow: Flow<FollowOrCustom<String>> = unifiedSettings.appLanguageSettingFlow

    suspend fun setAppLanguageSetting(setting: FollowOrCustom<String>) {
        unifiedSettings.setAppLanguageSetting(setting)
    }

    val aiLanguageSettingFlow: Flow<FollowOrCustom<AiLanguageChoice>> = unifiedSettings.aiLanguageSettingFlow

    suspend fun setAiLanguageSetting(setting: FollowOrCustom<AiLanguageChoice>) {
        unifiedSettings.setAiLanguageSetting(setting)
    }

    val marketplaceCountrySettingFlow: Flow<FollowOrCustom<String>> = unifiedSettings.marketplaceCountrySettingFlow

    suspend fun setMarketplaceCountrySetting(setting: FollowOrCustom<String>) {
        unifiedSettings.setMarketplaceCountrySetting(setting)
    }

    val ttsLanguageSettingFlow: Flow<TtsLanguageChoice> = unifiedSettings.ttsLanguageSettingFlow

    suspend fun setTtsLanguageSetting(setting: TtsLanguageChoice) {
        unifiedSettings.setTtsLanguageSetting(setting)
    }

    val lastDetectedSpokenLanguageFlow: Flow<String?> = unifiedSettings.lastDetectedSpokenLanguageFlow

    suspend fun setLastDetectedSpokenLanguage(languageTag: String?) {
        unifiedSettings.setLastDetectedSpokenLanguage(languageTag)
    }

    val effectiveAppLanguageFlow: Flow<String> = unifiedSettings.effectiveAppLanguageFlow

    val effectiveAiOutputLanguageFlow: Flow<String> = unifiedSettings.effectiveAiOutputLanguageFlow

    val effectiveMarketplaceCountryFlow: Flow<String> = unifiedSettings.effectiveMarketplaceCountryFlow

    val effectiveTtsLanguageFlow: Flow<String> = unifiedSettings.effectiveTtsLanguageFlow

    fun mapLanguageToMarketplaceCountry(languageTag: String): String = unifiedSettings.mapLanguageToMarketplaceCountry(languageTag)
}
