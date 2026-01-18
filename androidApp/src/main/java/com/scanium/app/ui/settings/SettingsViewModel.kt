package com.scanium.app.ui.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanium.app.auth.AuthRepository
import com.scanium.app.data.EntitlementManager
import com.scanium.app.data.MarketplaceRepository
import com.scanium.app.data.SettingsRepository
import com.scanium.app.data.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import com.scanium.app.ftue.FtueRepository
import com.scanium.app.model.AiLanguageChoice
import com.scanium.app.model.AppLanguage
import com.scanium.app.model.AssistantPrefs
import com.scanium.app.model.AssistantRegion
import com.scanium.app.model.AssistantTone
import com.scanium.app.model.AssistantUnits
import com.scanium.app.model.AssistantVerbosity
import com.scanium.app.model.FollowOrCustom
import com.scanium.app.model.TtsLanguageChoice
import com.scanium.app.model.billing.EntitlementState
import com.scanium.app.model.config.AssistantPrerequisiteState
import com.scanium.app.model.config.ConfigProvider
import com.scanium.app.model.config.ConnectionTestResult
import com.scanium.app.model.config.FeatureFlagRepository
import com.scanium.app.model.config.RemoteConfig
import com.scanium.app.model.user.UserEdition
import com.scanium.diagnostics.DiagnosticsPort
import com.scanium.telemetry.TelemetrySeverity
import com.scanium.telemetry.facade.Telemetry
import com.scanium.telemetry.ports.CrashPort
import dagger.hilt.android.lifecycle.HiltViewModel
import com.scanium.app.BuildConfig
import com.scanium.app.config.FeatureFlags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for the main settings screen.
 * Part of ARCH-001: Migrated to Hilt dependency injection.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settingsRepository: SettingsRepository,
        private val marketplaceRepository: MarketplaceRepository,
        private val entitlementManager: EntitlementManager,
        private val configProvider: ConfigProvider,
        private val featureFlagRepository: FeatureFlagRepository,
        private val ftueRepository: FtueRepository,
        private val crashPort: CrashPort,
        private val telemetry: Telemetry?,
        private val diagnosticsPort: DiagnosticsPort,
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        val themeMode: StateFlow<ThemeMode> =
            settingsRepository.themeModeFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

        val appLanguage: StateFlow<AppLanguage> =
            settingsRepository.appLanguageFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLanguage.SYSTEM)

        // Use centralized FeatureFlagRepository for cloud classification state
        val allowCloud: StateFlow<Boolean> =
            featureFlagRepository.isCloudClassificationEnabled
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

        // Use centralized FeatureFlagRepository for assistant state
        val allowAssistant: StateFlow<Boolean> =
            featureFlagRepository.isAssistantEnabled
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        val shareDiagnostics: StateFlow<Boolean> =
            settingsRepository.shareDiagnosticsFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        val currentEdition: StateFlow<UserEdition> =
            entitlementManager.currentEditionFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserEdition.FREE)

        val entitlementState: StateFlow<EntitlementState> =
            entitlementManager.entitlementStateFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EntitlementState.DEFAULT)

        val isDeveloperMode: StateFlow<Boolean> =
            settingsRepository.developerModeFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        val autoSaveEnabled: StateFlow<Boolean> =
            settingsRepository.autoSaveEnabledFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        val saveDirectoryUri: StateFlow<String?> =
            settingsRepository.saveDirectoryUriFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        val exportFormat: StateFlow<ExportFormat> =
            settingsRepository.exportFormatFlow
                .map { formatString ->
                    when (formatString) {
                        "CSV" -> ExportFormat.CSV
                        "JSON" -> ExportFormat.JSON
                        else -> ExportFormat.ZIP
                    }
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExportFormat.ZIP)

        val allowAssistantImages: StateFlow<Boolean> =
            settingsRepository.allowAssistantImagesFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        val soundsEnabled: StateFlow<Boolean> =
            settingsRepository.soundsEnabledFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

        val showDetectionBoxes: StateFlow<Boolean> =
            settingsRepository.showDetectionBoxesFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

        val openItemListAfterScan: StateFlow<Boolean> =
            settingsRepository.openItemListAfterScanFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        val entitlements =
            entitlementManager.entitlementPolicyFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.scanium.app.model.user.FreeEntitlements)

        val remoteConfig: StateFlow<RemoteConfig> =
            configProvider.config
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RemoteConfig())

        val forceFtueTour: StateFlow<Boolean> =
            ftueRepository.forceEnabledFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        // Assistant Personalization
        val assistantLanguage: StateFlow<String> =
            settingsRepository.assistantLanguageFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "EN")

        val assistantTone: StateFlow<AssistantTone> =
            settingsRepository.assistantToneFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssistantTone.NEUTRAL)

        /**
         * Country code for assistant (e.g., "NL", "DE", "PL").
         * Supports all countries from marketplaces.json.
         */
        val assistantCountryCode: StateFlow<String> =
            settingsRepository.assistantCountryCodeFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "NL")

        /**
         * Legacy region enum for backward compatibility.
         * @deprecated Use assistantCountryCode instead
         */
        @Deprecated("Use assistantCountryCode for full country support")
        val assistantRegion: StateFlow<AssistantRegion> =
            settingsRepository.assistantRegionFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssistantRegion.EU)

        val assistantUnits: StateFlow<AssistantUnits> =
            settingsRepository.assistantUnitsFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssistantUnits.METRIC)

        val assistantVerbosity: StateFlow<AssistantVerbosity> =
            settingsRepository.assistantVerbosityFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssistantVerbosity.NORMAL)

        val assistantPrefs: StateFlow<AssistantPrefs> =
            settingsRepository.assistantPrefsFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssistantPrefs())

        // Voice Mode settings
        val voiceModeEnabled: StateFlow<Boolean> =
            settingsRepository.voiceModeEnabledFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        val speakAnswersEnabled: StateFlow<Boolean> =
            settingsRepository.speakAnswersEnabledFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        val autoSendTranscript: StateFlow<Boolean> =
            settingsRepository.autoSendTranscriptFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        val voiceLanguage: StateFlow<String> =
            settingsRepository.voiceLanguageFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

        val assistantHapticsEnabled: StateFlow<Boolean> =
            settingsRepository.assistantHapticsEnabledFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        // Privacy Safe Mode
        val isPrivacySafeModeActive: StateFlow<Boolean> =
            settingsRepository.isPrivacySafeModeActiveFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        // Assistant Prerequisites
        val assistantPrerequisiteState: StateFlow<AssistantPrerequisiteState> =
            featureFlagRepository.assistantPrerequisiteState
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssistantPrerequisiteState.LOADING)

        // Dialog state for showing prerequisites
        private val _showPrerequisiteDialog = MutableStateFlow(false)
        val showPrerequisiteDialog: StateFlow<Boolean> = _showPrerequisiteDialog.asStateFlow()

        // Connection test state
        private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
        val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch { settingsRepository.setThemeMode(mode) }
        }

        fun setAppLanguage(language: AppLanguage) {
            viewModelScope.launch {
                settingsRepository.setAppLanguage(language)
                val localeList =
                    when (language) {
                        AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
                        else -> LocaleListCompat.forLanguageTags(language.code)
                    }
                AppCompatDelegate.setApplicationLocales(localeList)
            }
        }

        fun setAllowCloud(allow: Boolean) {
            viewModelScope.launch { featureFlagRepository.setCloudClassificationEnabled(allow) }
        }

        /**
         * Attempts to enable/disable the assistant.
         * If enabling and prerequisites aren't met, shows the prerequisite dialog.
         */
        fun setAllowAssistant(allow: Boolean) {
            viewModelScope.launch {
                val success = featureFlagRepository.setAssistantEnabled(allow)
                if (!success && allow) {
                    // Prerequisites not met, show the dialog
                    _showPrerequisiteDialog.value = true
                }
            }
        }

        fun dismissPrerequisiteDialog() {
            _showPrerequisiteDialog.value = false
        }

        /**
         * Tests the backend connection for the assistant.
         * Updates connectionTestState with the result.
         */
        fun testAssistantConnection() {
            viewModelScope.launch {
                _connectionTestState.value = ConnectionTestState.Testing
                val result = featureFlagRepository.testAssistantConnection()
                _connectionTestState.value =
                    when (result) {
                        is ConnectionTestResult.Success -> ConnectionTestState.Success
                        is ConnectionTestResult.Failure -> ConnectionTestState.Failed(result.message)
                    }
            }
        }

        fun resetConnectionTestState() {
            _connectionTestState.value = ConnectionTestState.Idle
        }

        fun setShareDiagnostics(share: Boolean) {
            viewModelScope.launch { settingsRepository.setShareDiagnostics(share) }
        }

        fun setDeveloperMode(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setDeveloperMode(enabled) }
        }

        fun setAutoSaveEnabled(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setAutoSaveEnabled(enabled) }
        }

        fun setSaveDirectoryUri(uri: String?) {
            viewModelScope.launch { settingsRepository.setSaveDirectoryUri(uri) }
        }

        fun setExportFormat(format: ExportFormat) {
            viewModelScope.launch {
                val formatString =
                    when (format) {
                        ExportFormat.ZIP -> "ZIP"
                        ExportFormat.CSV -> "CSV"
                        ExportFormat.JSON -> "JSON"
                    }
                settingsRepository.setExportFormat(formatString)
            }
        }

        /**
         * Clears cached data including temporary files and thumbnails.
         * This clears the app's cache directory.
         */
        fun clearCache(context: Context) {
            viewModelScope.launch {
                try {
                    context.cacheDir.deleteRecursively()
                } catch (e: Exception) {
                    android.util.Log.e("SettingsViewModel", "Failed to clear cache", e)
                }
            }
        }

        fun setAllowAssistantImages(allow: Boolean) {
            viewModelScope.launch { settingsRepository.setAllowAssistantImages(allow) }
        }

        fun setSoundsEnabled(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setSoundsEnabled(enabled) }
        }

        fun setShowDetectionBoxes(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setShowDetectionBoxes(enabled) }
        }

        fun setOpenItemListAfterScan(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setOpenItemListAfterScan(enabled) }
        }

        fun setForceFtueTour(enabled: Boolean) {
            viewModelScope.launch {
                ftueRepository.setForceEnabled(enabled)
            }
        }

        fun resetFtueTour() {
            viewModelScope.launch {
                if (com.scanium.app.BuildConfig.FLAVOR == "dev") {
                    android.util.Log.d("FTUE", "Replay: Resetting all FTUE flags via resetAll()")
                }
                ftueRepository.resetAll()
            }
        }

        // Assistant Personalization setters
        fun setAssistantLanguage(language: String) {
            viewModelScope.launch { settingsRepository.setAssistantLanguage(language) }
        }

        fun setAssistantTone(tone: AssistantTone) {
            viewModelScope.launch { settingsRepository.setAssistantTone(tone) }
        }

        /**
         * Set the assistant country code.
         * Accepts any valid ISO 2-letter country code from marketplaces.json.
         */
        fun setAssistantCountryCode(countryCode: String) {
            viewModelScope.launch { settingsRepository.setAssistantCountryCode(countryCode) }
        }

        /**
         * Legacy setter for AssistantRegion enum.
         * @deprecated Use setAssistantCountryCode instead
         */
        @Deprecated("Use setAssistantCountryCode for full country support")
        fun setAssistantRegion(region: AssistantRegion) {
            viewModelScope.launch { settingsRepository.setAssistantRegion(region) }
        }

        fun setAssistantUnits(units: AssistantUnits) {
            viewModelScope.launch { settingsRepository.setAssistantUnits(units) }
        }

        fun setAssistantVerbosity(verbosity: AssistantVerbosity) {
            viewModelScope.launch { settingsRepository.setAssistantVerbosity(verbosity) }
        }

        // Voice Mode setters
        fun setVoiceModeEnabled(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setVoiceModeEnabled(enabled) }
        }

        fun setSpeakAnswersEnabled(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setSpeakAnswersEnabled(enabled) }
        }

        fun setAutoSendTranscript(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setAutoSendTranscript(enabled) }
        }

        fun setVoiceLanguage(language: String) {
            viewModelScope.launch { settingsRepository.setVoiceLanguage(language) }
        }

        fun setAssistantHapticsEnabled(enabled: Boolean) {
            viewModelScope.launch { settingsRepository.setAssistantHapticsEnabled(enabled) }
        }

        // =========================================================================
        // Unified Settings (Primary Region & Language)
        // =========================================================================

        // Primary Settings (Language & Marketplace Country)
        val primaryRegionCountry: StateFlow<String> =
            settingsRepository.primaryRegionCountryFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "NL")

        val primaryLanguage: StateFlow<String> =
            settingsRepository.primaryLanguageFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

        // Override Settings

        val aiLanguageSetting: StateFlow<FollowOrCustom<AiLanguageChoice>> =
            settingsRepository.aiLanguageSettingFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FollowOrCustom.followPrimary())

        val marketplaceCountrySetting: StateFlow<FollowOrCustom<String>> =
            settingsRepository.marketplaceCountrySettingFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FollowOrCustom.followPrimary())

        val ttsLanguageSetting: StateFlow<TtsLanguageChoice> =
            settingsRepository.ttsLanguageSettingFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TtsLanguageChoice.FollowAiLanguage)

        val lastDetectedSpokenLanguage: StateFlow<String?> =
            settingsRepository.lastDetectedSpokenLanguageFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

        // Effective Values (resolved from primary + overrides)
        // Note: effectiveAppLanguage removed - Language is now the direct source of truth
        val effectiveAiOutputLanguage: StateFlow<String> =
            settingsRepository.effectiveAiOutputLanguageFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

        val effectiveMarketplaceCountry: StateFlow<String> =
            settingsRepository.effectiveMarketplaceCountryFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "NL")

        val effectiveTtsLanguage: StateFlow<String> =
            settingsRepository.effectiveTtsLanguageFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

        // =========================================================================
        // Combined Settings Sections State
        // =========================================================================

        /**
         * Combined UI state for all settings sections.
         * Built from individual flows using [buildSettingsSectionsState].
         * Preserves existing individual flows for backward compatibility.
         */
        val sectionsState: StateFlow<SettingsSectionsState> = combine(
            // Group 1: General section inputs
            combine(
                themeMode,
                primaryLanguage,
                primaryRegionCountry,
                currentEdition,
                entitlementState,
            ) { theme, lang, region, edition, entitlement ->
                GeneralInputs(theme, lang, region, edition, entitlement)
            },
            // Group 2: Assistant section inputs (part 1)
            combine(
                allowAssistant,
                allowAssistantImages,
                assistantLanguage,
                assistantTone,
                assistantCountryCode,
            ) { allow, images, lang, tone, country ->
                AssistantInputs1(allow, images, lang, tone, country)
            },
            // Group 3: Assistant section inputs (part 2)
            combine(
                assistantUnits,
                assistantVerbosity,
                voiceModeEnabled,
                speakAnswersEnabled,
                autoSendTranscript,
            ) { units, verbosity, voice, speak, autoSend ->
                AssistantInputs2(units, verbosity, voice, speak, autoSend)
            },
            // Group 4: Assistant section inputs (part 3) + Camera/Storage/Privacy
            combine(
                voiceLanguage,
                assistantHapticsEnabled,
                assistantPrerequisiteState,
                showDetectionBoxes,
                autoSaveEnabled,
            ) { voiceLang, haptics, prereq, boxes, autoSave ->
                MixedInputs1(voiceLang, haptics, prereq, boxes, autoSave)
            },
            // Group 5: Storage/Privacy/Developer
            combine(
                saveDirectoryUri,
                exportFormat,
                allowCloud,
                shareDiagnostics,
                isPrivacySafeModeActive,
            ) { saveDir, format, cloud, diagnostics, privacySafe ->
                MixedInputs2(saveDir, format, cloud, diagnostics, privacySafe)
            },
        ) { general, assistant1, assistant2, mixed1, mixed2 ->
            // Additional inputs needed: sounds, developer mode, unified settings
            val soundsVal = soundsEnabled.value
            val devMode = isDeveloperMode.value
            val aiLangSetting = aiLanguageSetting.value
            val marketplaceSetting = marketplaceCountrySetting.value
            val ttsSetting = ttsLanguageSetting.value
            val effectiveAi = effectiveAiOutputLanguage.value
            val effectiveMarket = effectiveMarketplaceCountry.value
            val effectiveTts = effectiveTtsLanguage.value

            val inputs = SettingsInputs(
                // General
                themeMode = general.themeMode,
                primaryLanguage = general.primaryLanguage,
                primaryRegionCountry = general.primaryRegionCountry,
                currentEdition = general.currentEdition,
                entitlementState = general.entitlementState,
                soundsEnabled = soundsVal,
                // Assistant
                allowAssistant = assistant1.allowAssistant,
                allowAssistantImages = assistant1.allowAssistantImages,
                assistantLanguage = assistant1.assistantLanguage,
                assistantTone = assistant1.assistantTone,
                assistantCountryCode = assistant1.assistantCountryCode,
                assistantUnits = assistant2.assistantUnits,
                assistantVerbosity = assistant2.assistantVerbosity,
                voiceModeEnabled = assistant2.voiceModeEnabled,
                speakAnswersEnabled = assistant2.speakAnswersEnabled,
                autoSendTranscript = assistant2.autoSendTranscript,
                voiceLanguage = mixed1.voiceLanguage,
                assistantHapticsEnabled = mixed1.assistantHapticsEnabled,
                assistantPrerequisiteState = mixed1.assistantPrerequisiteState,
                aiLanguageSetting = aiLangSetting,
                marketplaceCountrySetting = marketplaceSetting,
                ttsLanguageSetting = ttsSetting,
                effectiveAiOutputLanguage = effectiveAi,
                effectiveMarketplaceCountry = effectiveMarket,
                effectiveTtsLanguage = effectiveTts,
                // Camera
                showDetectionBoxes = mixed1.showDetectionBoxes,
                // Storage
                autoSaveEnabled = mixed1.autoSaveEnabled,
                saveDirectoryUri = mixed2.saveDirectoryUri,
                exportFormat = mixed2.exportFormat,
                // Privacy
                allowCloud = mixed2.allowCloud,
                shareDiagnostics = mixed2.shareDiagnostics,
                privacySafeModeActive = mixed2.privacySafeModeActive,
                // Developer / Build flags
                isDeveloperModeEnabled = devMode,
                allowDeveloperMode = FeatureFlags.allowDeveloperMode,
                isDebugBuild = BuildConfig.DEBUG,
            )
            buildSettingsSectionsState(inputs)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            buildSettingsSectionsState(defaultSettingsInputs()),
        )

        // Setters for Primary Settings
        fun setPrimaryRegionCountry(countryCode: String) {
            viewModelScope.launch { settingsRepository.setPrimaryRegionCountry(countryCode) }
        }

        /**
         * Set the primary language.
         * This is the single source of truth for app UI, AI assistant, and TTS.
         * Also auto-sets marketplace country based on language mapping.
         */
        fun setPrimaryLanguage(languageTag: String) {
            viewModelScope.launch {
                settingsRepository.setPrimaryLanguage(languageTag)
                // Apply language to app UI via AppCompatDelegate
                val localeList = LocaleListCompat.forLanguageTags(languageTag)
                AppCompatDelegate.setApplicationLocales(localeList)
            }
        }

        // Setters for Override Settings

        fun setAiLanguageSetting(setting: FollowOrCustom<AiLanguageChoice>) {
            viewModelScope.launch { settingsRepository.setAiLanguageSetting(setting) }
        }

        fun setMarketplaceCountrySetting(setting: FollowOrCustom<String>) {
            viewModelScope.launch { settingsRepository.setMarketplaceCountrySetting(setting) }
        }

        fun setTtsLanguageSetting(setting: TtsLanguageChoice) {
            viewModelScope.launch { settingsRepository.setTtsLanguageSetting(setting) }
        }

        fun setLastDetectedSpokenLanguage(languageTag: String?) {
            viewModelScope.launch { settingsRepository.setLastDetectedSpokenLanguage(languageTag) }
        }

        /**
         * Helper to set both language and marketplace country at once (e.g., during onboarding).
         * Note: Setting language automatically sets marketplace country via mapping,
         * so this is mainly used when user explicitly selects both.
         */
        fun setPrimaryRegionAndLanguage(
            countryCode: String,
            languageTag: String,
        ) {
            viewModelScope.launch {
                // Set language first (auto-sets marketplace via mapping)
                settingsRepository.setPrimaryLanguage(languageTag)
                // Then set marketplace country (which marks it as manually selected)
                settingsRepository.setPrimaryRegionCountry(countryCode)
                // Apply language to app UI
                val localeList = LocaleListCompat.forLanguageTags(languageTag)
                AppCompatDelegate.setApplicationLocales(localeList)
            }
        }

        // Privacy Safe Mode
        fun enablePrivacySafeMode() {
            viewModelScope.launch { settingsRepository.enablePrivacySafeMode() }
        }

        fun resetPrivacySettings() {
            viewModelScope.launch { settingsRepository.resetPrivacySettings() }
        }

        // For manual edition switching (Dev only ideally, but we put it here for now)
        fun setUserEdition(edition: UserEdition) {
            viewModelScope.launch { settingsRepository.setUserEdition(edition) }
        }

        fun refreshRemoteConfig() {
            viewModelScope.launch { configProvider.refresh(force = true) }
        }

        /**
         * Triggers a test crash to verify Sentry integration.
         * Only available in DEBUG builds via Developer settings.
         *
         * This will:
         * 1. Add a breadcrumb to the crash report
         * 2. Capture a test exception via CrashPort
         * 3. Throw a RuntimeException (if throwCrash is true)
         */
        fun triggerCrashTest(throwCrash: Boolean = false) {
            // Add a breadcrumb before the crash
            crashPort.addBreadcrumb(
                message = "User triggered crash test",
                attributes =
                    mapOf(
                        "test_type" to "manual_crash_test",
                        "developer_mode" to "true",
                    ),
            )

            // Capture a handled exception first
            val testException = RuntimeException("🧪 Test crash from developer settings - this is intentional!")
            crashPort.captureException(
                throwable = testException,
                attributes =
                    mapOf(
                        "crash_test" to "true",
                        "trigger_source" to "settings_developer_menu",
                    ),
            )

            // Optionally throw a crash (for testing unhandled exception flow)
            if (throwCrash) {
                throw testException
            }
        }

        /**
         * Triggers a diagnostics bundle test to verify Sentry attachment.
         * Only available in DEBUG builds via Developer settings.
         *
         * This will:
         * 1. Emit several test telemetry events to populate DiagnosticsBuffer
         * 2. Capture a test exception with diagnostics bundle attached
         * 3. Log diagnostic info about the bundle size and event count
         *
         * The diagnostics bundle should appear as an attachment ("diagnostics.json")
         * in the Sentry event in the Sentry dashboard.
         */
        fun triggerDiagnosticsTest() {
            // Emit some test telemetry events to populate the diagnostics buffer
            telemetry?.info(
                "diagnostics_test.started",
                mapOf(
                    "test_id" to "diag_${System.currentTimeMillis()}",
                    "test_type" to "manual_diagnostics_test",
                ),
            )

            telemetry?.event(
                "diagnostics_test.event_1",
                TelemetrySeverity.DEBUG,
                mapOf(
                    "step" to "1",
                    "data" to "test_data_123",
                ),
            )

            telemetry?.event(
                "diagnostics_test.event_2",
                TelemetrySeverity.INFO,
                mapOf(
                    "step" to "2",
                    "data" to "test_data_456",
                ),
            )

            telemetry?.warn(
                "diagnostics_test.warning",
                mapOf(
                    "step" to "3",
                    "warning_type" to "test_warning",
                ),
            )

            // Check diagnostics buffer status
            val breadcrumbCount = diagnosticsPort.breadcrumbCount()
            android.util.Log.i("DiagnosticsTest", "DiagnosticsBuffer has $breadcrumbCount events before capture")

            // Capture a test exception (will include diagnostics bundle as attachment)
            val testException = RuntimeException("🔬 Diagnostics bundle test - check for diagnostics.json attachment")
            crashPort.captureException(
                throwable = testException,
                attributes =
                    mapOf(
                        "diagnostics_test" to "true",
                        "breadcrumb_count" to breadcrumbCount.toString(),
                        "trigger_source" to "settings_developer_menu",
                    ),
            )

            android.util.Log.i(
                "DiagnosticsTest",
                "Captured exception with diagnostics bundle ($breadcrumbCount events). Check Sentry for attachment.",
            )
        }

        // =========================================================================
        // Authentication
        // =========================================================================

        /**
         * Reactive flow of current user info. Updates immediately on sign-in/sign-out.
         */
        val userInfoFlow: StateFlow<com.scanium.app.config.SecureApiKeyStore.UserInfo?> =
            authRepository.userInfoFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), authRepository.getUserInfo())

        /**
         * Initiates Google Sign-In flow via Credential Manager.
         * Exchanges Google ID token with backend and stores session token.
         *
         * @param activity The Activity context required for showing the Google Sign-In UI
         * @return Result indicating success or failure
         */
        suspend fun signInWithGoogle(activity: android.app.Activity): Result<Unit> {
            return authRepository.signInWithGoogle(activity)
        }

        /**
         * Phase C: Signs out the current user by calling backend logout and clearing local state.
         */
        fun signOut() {
            viewModelScope.launch {
                val result = authRepository.signOut()
                if (result.isFailure) {
                    android.util.Log.e("SettingsViewModel", "Sign out failed", result.exceptionOrNull())
                }
            }
        }

        /**
         * Phase C: Refresh the session using the refresh token.
         */
        fun refreshSession() {
            viewModelScope.launch {
                val result = authRepository.refreshSession()
                if (result.isFailure) {
                    android.util.Log.e("SettingsViewModel", "Session refresh failed", result.exceptionOrNull())
                } else {
                    android.util.Log.i("SettingsViewModel", "Session refreshed successfully")
                }
            }
        }

        /**
         * Phase D: Delete the user's account (permanently deletes all data).
         * Returns Result indicating success or failure.
         */
        suspend fun deleteAccount(): Result<Unit> {
            return authRepository.deleteAccount()
        }

        /**
         * Returns the currently signed-in user's info, or null if not signed in.
         */
        fun getUserInfo() = authRepository.getUserInfo()

        /**
         * Phase C: Get access token expiry timestamp (milliseconds since epoch)
         */
        fun getAccessTokenExpiresAt() = authRepository.getAccessTokenExpiresAt()
    }

/**
 * State for the connection test operation.
 */
sealed class ConnectionTestState {
    object Idle : ConnectionTestState()

    object Testing : ConnectionTestState()

    object Success : ConnectionTestState()

    data class Failed(val message: String) : ConnectionTestState()
}

// =========================================================================
// Helper data classes for flow combining (internal use only)
// =========================================================================

internal data class GeneralInputs(
    val themeMode: ThemeMode,
    val primaryLanguage: String,
    val primaryRegionCountry: String,
    val currentEdition: UserEdition,
    val entitlementState: EntitlementState,
)

internal data class AssistantInputs1(
    val allowAssistant: Boolean,
    val allowAssistantImages: Boolean,
    val assistantLanguage: String,
    val assistantTone: AssistantTone,
    val assistantCountryCode: String,
)

internal data class AssistantInputs2(
    val assistantUnits: AssistantUnits,
    val assistantVerbosity: AssistantVerbosity,
    val voiceModeEnabled: Boolean,
    val speakAnswersEnabled: Boolean,
    val autoSendTranscript: Boolean,
)

internal data class MixedInputs1(
    val voiceLanguage: String,
    val assistantHapticsEnabled: Boolean,
    val assistantPrerequisiteState: AssistantPrerequisiteState,
    val showDetectionBoxes: Boolean,
    val autoSaveEnabled: Boolean,
)

internal data class MixedInputs2(
    val saveDirectoryUri: String?,
    val exportFormat: ExportFormat,
    val allowCloud: Boolean,
    val shareDiagnostics: Boolean,
    val privacySafeModeActive: Boolean,
)

/**
 * Creates default [SettingsInputs] for initial state.
 * Uses the same defaults as the individual StateFlow properties.
 */
internal fun defaultSettingsInputs(): SettingsInputs = SettingsInputs(
    // General
    themeMode = ThemeMode.SYSTEM,
    primaryLanguage = "en",
    primaryRegionCountry = "NL",
    currentEdition = UserEdition.FREE,
    entitlementState = EntitlementState.DEFAULT,
    soundsEnabled = true,
    // Assistant
    allowAssistant = false,
    allowAssistantImages = false,
    assistantLanguage = "EN",
    assistantTone = AssistantTone.NEUTRAL,
    assistantCountryCode = "NL",
    assistantUnits = AssistantUnits.METRIC,
    assistantVerbosity = AssistantVerbosity.NORMAL,
    voiceModeEnabled = false,
    speakAnswersEnabled = false,
    autoSendTranscript = false,
    voiceLanguage = "",
    assistantHapticsEnabled = false,
    assistantPrerequisiteState = AssistantPrerequisiteState.LOADING,
    aiLanguageSetting = FollowOrCustom.followPrimary(),
    marketplaceCountrySetting = FollowOrCustom.followPrimary(),
    ttsLanguageSetting = TtsLanguageChoice.FollowAiLanguage,
    effectiveAiOutputLanguage = "en",
    effectiveMarketplaceCountry = "NL",
    effectiveTtsLanguage = "en",
    // Camera
    showDetectionBoxes = true,
    // Storage
    autoSaveEnabled = false,
    saveDirectoryUri = null,
    exportFormat = ExportFormat.ZIP,
    // Privacy
    allowCloud = true,
    shareDiagnostics = false,
    privacySafeModeActive = false,
    // Developer / Build flags
    isDeveloperModeEnabled = false,
    allowDeveloperMode = FeatureFlags.allowDeveloperMode,
    isDebugBuild = BuildConfig.DEBUG,
)
