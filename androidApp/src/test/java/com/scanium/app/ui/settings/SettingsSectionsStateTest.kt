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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [buildSettingsSectionsState] pure mapper function.
 * These tests verify the deterministic state mapping logic.
 */
class SettingsSectionsStateTest {
    /**
     * Creates a default test input with all values set to sensible defaults.
     * Test methods can override specific values as needed.
     */
    private fun createTestInputs(
        // General
        themeMode: ThemeMode = ThemeMode.SYSTEM,
        primaryLanguage: String = "en",
        primaryRegionCountry: String = "NL",
        currentEdition: UserEdition = UserEdition.FREE,
        entitlementState: EntitlementState = EntitlementState.DEFAULT,
        soundsEnabled: Boolean = true,
        // Assistant
        allowAssistant: Boolean = false,
        allowAssistantImages: Boolean = false,
        assistantLanguage: String = "EN",
        assistantTone: AssistantTone = AssistantTone.NEUTRAL,
        assistantCountryCode: String = "NL",
        assistantUnits: AssistantUnits = AssistantUnits.METRIC,
        assistantVerbosity: AssistantVerbosity = AssistantVerbosity.NORMAL,
        voiceModeEnabled: Boolean = false,
        speakAnswersEnabled: Boolean = false,
        autoSendTranscript: Boolean = false,
        voiceLanguage: String = "",
        assistantHapticsEnabled: Boolean = false,
        assistantPrerequisiteState: AssistantPrerequisiteState = AssistantPrerequisiteState.LOADING,
        aiLanguageSetting: FollowOrCustom<AiLanguageChoice> = FollowOrCustom.followPrimary(),
        marketplaceCountrySetting: FollowOrCustom<String> = FollowOrCustom.followPrimary(),
        ttsLanguageSetting: TtsLanguageChoice = TtsLanguageChoice.FollowAiLanguage,
        effectiveAiOutputLanguage: String = "en",
        effectiveMarketplaceCountry: String = "NL",
        effectiveTtsLanguage: String = "en",
        // Camera
        showDetectionBoxes: Boolean = true,
        // Storage
        autoSaveEnabled: Boolean = false,
        saveDirectoryUri: String? = null,
        exportFormat: ExportFormat = ExportFormat.ZIP,
        // Privacy
        allowCloud: Boolean = true,
        shareDiagnostics: Boolean = false,
        privacySafeModeActive: Boolean = false,
        // Developer / Build flags
        isDeveloperModeEnabled: Boolean = false,
        allowDeveloperMode: Boolean = true,
        isDebugBuild: Boolean = false,
    ) = SettingsInputs(
        themeMode = themeMode,
        primaryLanguage = primaryLanguage,
        primaryRegionCountry = primaryRegionCountry,
        currentEdition = currentEdition,
        entitlementState = entitlementState,
        soundsEnabled = soundsEnabled,
        allowAssistant = allowAssistant,
        allowAssistantImages = allowAssistantImages,
        assistantLanguage = assistantLanguage,
        assistantTone = assistantTone,
        assistantCountryCode = assistantCountryCode,
        assistantUnits = assistantUnits,
        assistantVerbosity = assistantVerbosity,
        voiceModeEnabled = voiceModeEnabled,
        speakAnswersEnabled = speakAnswersEnabled,
        autoSendTranscript = autoSendTranscript,
        voiceLanguage = voiceLanguage,
        assistantHapticsEnabled = assistantHapticsEnabled,
        assistantPrerequisiteState = assistantPrerequisiteState,
        aiLanguageSetting = aiLanguageSetting,
        marketplaceCountrySetting = marketplaceCountrySetting,
        ttsLanguageSetting = ttsLanguageSetting,
        effectiveAiOutputLanguage = effectiveAiOutputLanguage,
        effectiveMarketplaceCountry = effectiveMarketplaceCountry,
        effectiveTtsLanguage = effectiveTtsLanguage,
        showDetectionBoxes = showDetectionBoxes,
        autoSaveEnabled = autoSaveEnabled,
        saveDirectoryUri = saveDirectoryUri,
        exportFormat = exportFormat,
        allowCloud = allowCloud,
        shareDiagnostics = shareDiagnostics,
        privacySafeModeActive = privacySafeModeActive,
        isDeveloperModeEnabled = isDeveloperModeEnabled,
        allowDeveloperMode = allowDeveloperMode,
        isDebugBuild = isDebugBuild,
    )

    // =========================================================================
    // Developer Section Visibility Tests
    // =========================================================================

    @Test
    fun `developer section visible when allowDeveloperMode true and isDebugBuild true`() {
        val inputs =
            createTestInputs(
                allowDeveloperMode = true,
                isDebugBuild = true,
                isDeveloperModeEnabled = false,
            )
        val result = buildSettingsSectionsState(inputs)

        assertEquals(true, result.home.showDeveloperCategory)
        assertNotNull(result.developer)
    }

    @Test
    fun `developer section visible when allowDeveloperMode true and isDeveloperModeEnabled true`() {
        val inputs =
            createTestInputs(
                allowDeveloperMode = true,
                isDebugBuild = false,
                isDeveloperModeEnabled = true,
            )
        val result = buildSettingsSectionsState(inputs)

        assertEquals(true, result.home.showDeveloperCategory)
        assertNotNull(result.developer)
    }

    @Test
    fun `developer section hidden when allowDeveloperMode false even in debug build`() {
        val inputs =
            createTestInputs(
                allowDeveloperMode = false,
                isDebugBuild = true,
                isDeveloperModeEnabled = true,
            )
        val result = buildSettingsSectionsState(inputs)

        assertEquals(false, result.home.showDeveloperCategory)
        assertNull(result.developer)
    }

    @Test
    fun `developer section hidden when allowDeveloperMode true but neither debug nor devMode enabled`() {
        val inputs =
            createTestInputs(
                allowDeveloperMode = true,
                isDebugBuild = false,
                isDeveloperModeEnabled = false,
            )
        val result = buildSettingsSectionsState(inputs)

        assertEquals(false, result.home.showDeveloperCategory)
        assertNull(result.developer)
    }

    // =========================================================================
    // Assistant Section State Tests
    // =========================================================================

    @Test
    fun `assistant section enabled state reflects input`() {
        val inputsEnabled = createTestInputs(allowAssistant = true)
        val resultEnabled = buildSettingsSectionsState(inputsEnabled)
        assertEquals(true, resultEnabled.assistant.enabled)

        val inputsDisabled = createTestInputs(allowAssistant = false)
        val resultDisabled = buildSettingsSectionsState(inputsDisabled)
        assertEquals(false, resultDisabled.assistant.enabled)
    }

    @Test
    fun `assistant section allowImages state reflects input`() {
        val inputsEnabled = createTestInputs(allowAssistantImages = true)
        val resultEnabled = buildSettingsSectionsState(inputsEnabled)
        assertEquals(true, resultEnabled.assistant.allowImages)

        val inputsDisabled = createTestInputs(allowAssistantImages = false)
        val resultDisabled = buildSettingsSectionsState(inputsDisabled)
        assertEquals(false, resultDisabled.assistant.allowImages)
    }

    @Test
    fun `assistant section personalization values pass through correctly`() {
        val inputs =
            createTestInputs(
                assistantTone = AssistantTone.FRIENDLY,
                assistantUnits = AssistantUnits.IMPERIAL,
                assistantVerbosity = AssistantVerbosity.DETAILED,
            )
        val result = buildSettingsSectionsState(inputs)

        assertEquals(AssistantTone.FRIENDLY, result.assistant.tone)
        assertEquals(AssistantUnits.IMPERIAL, result.assistant.units)
        assertEquals(AssistantVerbosity.DETAILED, result.assistant.verbosity)
    }

    // =========================================================================
    // General Section State Tests
    // =========================================================================

    @Test
    fun `general section theme mode reflects input`() {
        val inputsLight = createTestInputs(themeMode = ThemeMode.LIGHT)
        val resultLight = buildSettingsSectionsState(inputsLight)
        assertEquals(ThemeMode.LIGHT, resultLight.general.themeMode)

        val inputsDark = createTestInputs(themeMode = ThemeMode.DARK)
        val resultDark = buildSettingsSectionsState(inputsDark)
        assertEquals(ThemeMode.DARK, resultDark.general.themeMode)

        val inputsSystem = createTestInputs(themeMode = ThemeMode.SYSTEM)
        val resultSystem = buildSettingsSectionsState(inputsSystem)
        assertEquals(ThemeMode.SYSTEM, resultSystem.general.themeMode)
    }

    @Test
    fun `general section language and region pass through correctly`() {
        val inputs =
            createTestInputs(
                primaryLanguage = "de",
                primaryRegionCountry = "DE",
            )
        val result = buildSettingsSectionsState(inputs)

        assertEquals("de", result.general.primaryLanguage)
        assertEquals("DE", result.general.primaryRegionCountry)
    }

    @Test
    fun `general section edition reflects input`() {
        val inputsFree = createTestInputs(currentEdition = UserEdition.FREE)
        val resultFree = buildSettingsSectionsState(inputsFree)
        assertEquals(UserEdition.FREE, resultFree.general.currentEdition)

        val inputsPro = createTestInputs(currentEdition = UserEdition.PRO)
        val resultPro = buildSettingsSectionsState(inputsPro)
        assertEquals(UserEdition.PRO, resultPro.general.currentEdition)

        val inputsDev = createTestInputs(currentEdition = UserEdition.DEVELOPER)
        val resultDev = buildSettingsSectionsState(inputsDev)
        assertEquals(UserEdition.DEVELOPER, resultDev.general.currentEdition)
    }

    // =========================================================================
    // Privacy Section State Tests
    // =========================================================================

    @Test
    fun `privacy section allowCloud state reflects input`() {
        val inputsEnabled = createTestInputs(allowCloud = true)
        val resultEnabled = buildSettingsSectionsState(inputsEnabled)
        assertEquals(true, resultEnabled.privacy.allowCloud)

        val inputsDisabled = createTestInputs(allowCloud = false)
        val resultDisabled = buildSettingsSectionsState(inputsDisabled)
        assertEquals(false, resultDisabled.privacy.allowCloud)
    }

    @Test
    fun `privacy section privacySafeMode state reflects input`() {
        val inputsActive = createTestInputs(privacySafeModeActive = true)
        val resultActive = buildSettingsSectionsState(inputsActive)
        assertEquals(true, resultActive.privacy.privacySafeModeActive)

        val inputsInactive = createTestInputs(privacySafeModeActive = false)
        val resultInactive = buildSettingsSectionsState(inputsInactive)
        assertEquals(false, resultInactive.privacy.privacySafeModeActive)
    }

    // =========================================================================
    // Storage Section State Tests
    // =========================================================================

    @Test
    fun `storage section export format reflects input`() {
        val inputsZip = createTestInputs(exportFormat = ExportFormat.ZIP)
        val resultZip = buildSettingsSectionsState(inputsZip)
        assertEquals(ExportFormat.ZIP, resultZip.storage.exportFormat)

        val inputsCsv = createTestInputs(exportFormat = ExportFormat.CSV)
        val resultCsv = buildSettingsSectionsState(inputsCsv)
        assertEquals(ExportFormat.CSV, resultCsv.storage.exportFormat)

        val inputsJson = createTestInputs(exportFormat = ExportFormat.JSON)
        val resultJson = buildSettingsSectionsState(inputsJson)
        assertEquals(ExportFormat.JSON, resultJson.storage.exportFormat)
    }

    @Test
    fun `storage section autoSave and directory state reflects input`() {
        val inputs =
            createTestInputs(
                autoSaveEnabled = true,
                saveDirectoryUri = "content://com.android.externalstorage/tree/primary",
            )
        val result = buildSettingsSectionsState(inputs)

        assertEquals(true, result.storage.autoSaveEnabled)
        assertEquals("content://com.android.externalstorage/tree/primary", result.storage.saveDirectoryUri)
    }

    // =========================================================================
    // Camera Section State Tests
    // =========================================================================

    @Test
    fun `camera section showDetectionBoxes state reflects input`() {
        val inputsEnabled = createTestInputs(showDetectionBoxes = true)
        val resultEnabled = buildSettingsSectionsState(inputsEnabled)
        assertEquals(true, resultEnabled.camera.showDetectionBoxes)

        val inputsDisabled = createTestInputs(showDetectionBoxes = false)
        val resultDisabled = buildSettingsSectionsState(inputsDisabled)
        assertEquals(false, resultDisabled.camera.showDetectionBoxes)
    }

    // =========================================================================
    // Pure Function Determinism Test
    // =========================================================================

    @Test
    fun `buildSettingsSectionsState is deterministic with same inputs`() {
        val inputs =
            createTestInputs(
                themeMode = ThemeMode.DARK,
                primaryLanguage = "nl",
                primaryRegionCountry = "NL",
                allowAssistant = true,
                assistantTone = AssistantTone.FRIENDLY,
                allowDeveloperMode = true,
                isDebugBuild = true,
            )

        val result1 = buildSettingsSectionsState(inputs)
        val result2 = buildSettingsSectionsState(inputs)

        assertEquals(result1, result2)
    }
}
