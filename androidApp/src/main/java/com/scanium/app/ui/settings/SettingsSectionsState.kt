package com.scanium.app.ui.settings

import com.scanium.app.data.ThemeMode
import com.scanium.app.model.AiLanguageChoice
import com.scanium.app.model.AssistantTone
import com.scanium.app.model.AssistantUnits
import com.scanium.app.model.AssistantVerbosity
import com.scanium.app.model.FollowOrCustom
import com.scanium.app.model.TtsLanguageChoice
import com.scanium.app.model.billing.EntitlementState
import com.scanium.app.model.config.AssistantPrerequisiteState
import com.scanium.app.model.user.UserEdition

/**
 * Top-level UI state for all settings sections.
 * This is a pure data model representing the rendered settings UI.
 * It is built by [buildSettingsSectionsState] from [SettingsInputs].
 */
data class SettingsSectionsState(
    val home: HomeSectionState,
    val general: GeneralSectionState,
    val assistant: AssistantSectionState,
    val camera: CameraSectionState,
    val storage: StorageSectionState,
    val privacy: PrivacySectionState,
    val developer: DeveloperSectionState?,
)

/**
 * State for the Settings Home screen (category list).
 */
data class HomeSectionState(
    val showDeveloperCategory: Boolean,
)

/**
 * State for the General settings section.
 */
data class GeneralSectionState(
    val themeMode: ThemeMode,
    val primaryLanguage: String,
    val primaryRegionCountry: String,
    val currentEdition: UserEdition,
    val entitlementState: EntitlementState,
    val soundsEnabled: Boolean,
)

/**
 * State for the Assistant settings section.
 */
data class AssistantSectionState(
    val enabled: Boolean,
    val allowImages: Boolean,
    val language: String,
    val tone: AssistantTone,
    val countryCode: String,
    val units: AssistantUnits,
    val verbosity: AssistantVerbosity,
    val voiceModeEnabled: Boolean,
    val speakAnswersEnabled: Boolean,
    val autoSendTranscript: Boolean,
    val voiceLanguage: String,
    val hapticsEnabled: Boolean,
    val prerequisiteState: AssistantPrerequisiteState,
    val primaryLanguage: String,
    val primaryRegionCountry: String,
    val aiLanguageSetting: FollowOrCustom<AiLanguageChoice>,
    val marketplaceCountrySetting: FollowOrCustom<String>,
    val ttsLanguageSetting: TtsLanguageChoice,
    val effectiveAiOutputLanguage: String,
    val effectiveMarketplaceCountry: String,
    val effectiveTtsLanguage: String,
)

/**
 * State for the Camera settings section.
 */
data class CameraSectionState(
    val showDetectionBoxes: Boolean,
)

/**
 * State for the Storage settings section.
 */
data class StorageSectionState(
    val autoSaveEnabled: Boolean,
    val saveDirectoryUri: String?,
    val exportFormat: ExportFormat,
)

/**
 * State for the Privacy settings section.
 */
data class PrivacySectionState(
    val allowCloud: Boolean,
    val shareDiagnostics: Boolean,
    val privacySafeModeActive: Boolean,
)

/**
 * State for the Developer settings section.
 * Null if developer section should not be shown.
 */
data class DeveloperSectionState(
    val isDeveloperModeEnabled: Boolean,
)

/**
 * Aggregated inputs for building [SettingsSectionsState].
 * These represent all the data sources that feed into the settings UI state.
 */
data class SettingsInputs(
    // General
    val themeMode: ThemeMode,
    val primaryLanguage: String,
    val primaryRegionCountry: String,
    val currentEdition: UserEdition,
    val entitlementState: EntitlementState,
    val soundsEnabled: Boolean,

    // Assistant
    val allowAssistant: Boolean,
    val allowAssistantImages: Boolean,
    val assistantLanguage: String,
    val assistantTone: AssistantTone,
    val assistantCountryCode: String,
    val assistantUnits: AssistantUnits,
    val assistantVerbosity: AssistantVerbosity,
    val voiceModeEnabled: Boolean,
    val speakAnswersEnabled: Boolean,
    val autoSendTranscript: Boolean,
    val voiceLanguage: String,
    val assistantHapticsEnabled: Boolean,
    val assistantPrerequisiteState: AssistantPrerequisiteState,
    val aiLanguageSetting: FollowOrCustom<AiLanguageChoice>,
    val marketplaceCountrySetting: FollowOrCustom<String>,
    val ttsLanguageSetting: TtsLanguageChoice,
    val effectiveAiOutputLanguage: String,
    val effectiveMarketplaceCountry: String,
    val effectiveTtsLanguage: String,

    // Camera
    val showDetectionBoxes: Boolean,

    // Storage
    val autoSaveEnabled: Boolean,
    val saveDirectoryUri: String?,
    val exportFormat: ExportFormat,

    // Privacy
    val allowCloud: Boolean,
    val shareDiagnostics: Boolean,
    val privacySafeModeActive: Boolean,

    // Developer / Build flags
    val isDeveloperModeEnabled: Boolean,
    val allowDeveloperMode: Boolean,
    val isDebugBuild: Boolean,
)

/**
 * Pure function to build [SettingsSectionsState] from [SettingsInputs].
 * This is deterministic and side-effect free.
 *
 * Developer section visibility rules (must remain identical to existing logic):
 * - allowDeveloperMode must be true (false in beta/prod builds)
 * - AND (isDebugBuild OR isDeveloperModeEnabled)
 */
fun buildSettingsSectionsState(inputs: SettingsInputs): SettingsSectionsState {
    // Developer section visibility: allowDeveloperMode && (isDebug || isDeveloperMode)
    // This matches the existing logic in SettingsHomeScreen.kt:60
    val showDeveloper = inputs.allowDeveloperMode && (inputs.isDebugBuild || inputs.isDeveloperModeEnabled)

    return SettingsSectionsState(
        home = HomeSectionState(
            showDeveloperCategory = showDeveloper,
        ),
        general = GeneralSectionState(
            themeMode = inputs.themeMode,
            primaryLanguage = inputs.primaryLanguage,
            primaryRegionCountry = inputs.primaryRegionCountry,
            currentEdition = inputs.currentEdition,
            entitlementState = inputs.entitlementState,
            soundsEnabled = inputs.soundsEnabled,
        ),
        assistant = AssistantSectionState(
            enabled = inputs.allowAssistant,
            allowImages = inputs.allowAssistantImages,
            language = inputs.assistantLanguage,
            tone = inputs.assistantTone,
            countryCode = inputs.assistantCountryCode,
            units = inputs.assistantUnits,
            verbosity = inputs.assistantVerbosity,
            voiceModeEnabled = inputs.voiceModeEnabled,
            speakAnswersEnabled = inputs.speakAnswersEnabled,
            autoSendTranscript = inputs.autoSendTranscript,
            voiceLanguage = inputs.voiceLanguage,
            hapticsEnabled = inputs.assistantHapticsEnabled,
            prerequisiteState = inputs.assistantPrerequisiteState,
            primaryLanguage = inputs.primaryLanguage,
            primaryRegionCountry = inputs.primaryRegionCountry,
            aiLanguageSetting = inputs.aiLanguageSetting,
            marketplaceCountrySetting = inputs.marketplaceCountrySetting,
            ttsLanguageSetting = inputs.ttsLanguageSetting,
            effectiveAiOutputLanguage = inputs.effectiveAiOutputLanguage,
            effectiveMarketplaceCountry = inputs.effectiveMarketplaceCountry,
            effectiveTtsLanguage = inputs.effectiveTtsLanguage,
        ),
        camera = CameraSectionState(
            showDetectionBoxes = inputs.showDetectionBoxes,
        ),
        storage = StorageSectionState(
            autoSaveEnabled = inputs.autoSaveEnabled,
            saveDirectoryUri = inputs.saveDirectoryUri,
            exportFormat = inputs.exportFormat,
        ),
        privacy = PrivacySectionState(
            allowCloud = inputs.allowCloud,
            shareDiagnostics = inputs.shareDiagnostics,
            privacySafeModeActive = inputs.privacySafeModeActive,
        ),
        developer = if (showDeveloper) {
            DeveloperSectionState(
                isDeveloperModeEnabled = inputs.isDeveloperModeEnabled,
            )
        } else {
            null
        },
    )
}
