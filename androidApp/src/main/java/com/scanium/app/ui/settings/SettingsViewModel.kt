package com.scanium.app.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scanium.app.ScaniumApplication
import com.scanium.app.data.EntitlementManager
import com.scanium.app.data.SettingsRepository
import com.scanium.app.data.ThemeMode
import com.scanium.app.model.user.UserEdition
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.scanium.app.model.billing.EntitlementState
import com.scanium.app.model.config.ConfigProvider
import com.scanium.app.model.config.FeatureFlagRepository
import com.scanium.app.model.config.RemoteConfig

class SettingsViewModel(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val entitlementManager: EntitlementManager,
    private val configProvider: ConfigProvider,
    private val featureFlagRepository: FeatureFlagRepository,
    private val ftueRepository: com.scanium.app.ftue.FtueRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    // Use centralized FeatureFlagRepository for cloud classification state
    val allowCloud: StateFlow<Boolean> = featureFlagRepository.isCloudClassificationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Use centralized FeatureFlagRepository for assistant state
    val allowAssistant: StateFlow<Boolean> = featureFlagRepository.isAssistantEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val shareDiagnostics: StateFlow<Boolean> = settingsRepository.shareDiagnosticsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentEdition: StateFlow<UserEdition> = entitlementManager.currentEditionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserEdition.FREE)
        
    val entitlementState: StateFlow<EntitlementState> = entitlementManager.entitlementStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EntitlementState.DEFAULT)

    val isDeveloperMode: StateFlow<Boolean> = settingsRepository.developerModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoSaveEnabled: StateFlow<Boolean> = settingsRepository.autoSaveEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val saveDirectoryUri: StateFlow<String?> = settingsRepository.saveDirectoryUriFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allowAssistantImages: StateFlow<Boolean> = settingsRepository.allowAssistantImagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val entitlements = entitlementManager.entitlementPolicyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.scanium.app.model.user.FreeEntitlements)

    val remoteConfig: StateFlow<RemoteConfig> = configProvider.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RemoteConfig())

    val forceFtueTour: StateFlow<Boolean> = ftueRepository.forceEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setAllowCloud(allow: Boolean) {
        viewModelScope.launch { featureFlagRepository.setCloudClassificationEnabled(allow) }
    }

    fun setAllowAssistant(allow: Boolean) {
        viewModelScope.launch { featureFlagRepository.setAssistantEnabled(allow) }
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

    fun setAllowAssistantImages(allow: Boolean) {
        viewModelScope.launch { settingsRepository.setAllowAssistantImages(allow) }
    }

    fun setForceFtueTour(enabled: Boolean) {
        viewModelScope.launch {
            ftueRepository.setForceEnabled(enabled)
        }
    }

    fun resetFtueTour() {
        viewModelScope.launch {
            ftueRepository.reset()
        }
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
        val scaniumApp = application as? ScaniumApplication
        if (scaniumApp == null) {
            throw IllegalStateException("Application is not ScaniumApplication")
        }

        // Add a breadcrumb before the crash
        scaniumApp.crashPort.addBreadcrumb(
            message = "User triggered crash test",
            attributes = mapOf(
                "test_type" to "manual_crash_test",
                "developer_mode" to "true"
            )
        )

        // Capture a handled exception first
        val testException = RuntimeException("🧪 Test crash from developer settings - this is intentional!")
        scaniumApp.crashPort.captureException(
            throwable = testException,
            attributes = mapOf(
                "crash_test" to "true",
                "trigger_source" to "settings_developer_menu"
            )
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
        val scaniumApp = application as? ScaniumApplication
        if (scaniumApp == null) {
            throw IllegalStateException("Application is not ScaniumApplication")
        }

        // Emit some test telemetry events to populate the diagnostics buffer
        scaniumApp.telemetry.info("diagnostics_test.started", mapOf(
            "test_id" to "diag_${System.currentTimeMillis()}",
            "test_type" to "manual_diagnostics_test"
        ))

        scaniumApp.telemetry.event("diagnostics_test.event_1", com.scanium.telemetry.TelemetrySeverity.DEBUG, mapOf(
            "step" to "1",
            "data" to "test_data_123"
        ))

        scaniumApp.telemetry.event("diagnostics_test.event_2", com.scanium.telemetry.TelemetrySeverity.INFO, mapOf(
            "step" to "2",
            "data" to "test_data_456"
        ))

        scaniumApp.telemetry.warn("diagnostics_test.warning", mapOf(
            "step" to "3",
            "warning_type" to "test_warning"
        ))

        // Check diagnostics buffer status
        val breadcrumbCount = scaniumApp.diagnosticsPort.breadcrumbCount()
        android.util.Log.i("DiagnosticsTest", "DiagnosticsBuffer has $breadcrumbCount events before capture")

        // Capture a test exception (will include diagnostics bundle as attachment)
        val testException = RuntimeException("🔬 Diagnostics bundle test - check for diagnostics.json attachment")
        scaniumApp.crashPort.captureException(
            throwable = testException,
            attributes = mapOf(
                "diagnostics_test" to "true",
                "breadcrumb_count" to breadcrumbCount.toString(),
                "trigger_source" to "settings_developer_menu"
            )
        )

        android.util.Log.i("DiagnosticsTest", "Captured exception with diagnostics bundle ($breadcrumbCount events). Check Sentry for attachment.")
    }

    class Factory(
        private val application: Application,
        private val settingsRepository: SettingsRepository,
        private val entitlementManager: EntitlementManager,
        private val configProvider: ConfigProvider,
        private val featureFlagRepository: FeatureFlagRepository,
        private val ftueRepository: com.scanium.app.ftue.FtueRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(application, settingsRepository, entitlementManager, configProvider, featureFlagRepository, ftueRepository) as T
        }
    }
}
