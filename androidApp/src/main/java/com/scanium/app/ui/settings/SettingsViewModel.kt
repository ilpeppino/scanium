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
import com.scanium.app.model.config.RemoteConfig
import kotlinx.coroutines.flow.combine

class SettingsViewModel(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val entitlementManager: EntitlementManager,
    private val configProvider: ConfigProvider
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val allowCloud: StateFlow<Boolean> = combine(
        settingsRepository.allowCloudClassificationFlow,
        configProvider.config
    ) { userPref, remote ->
        userPref && remote.featureFlags.enableCloud
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val allowAssistant: StateFlow<Boolean> = combine(
        settingsRepository.allowAssistantFlow,
        configProvider.config
    ) { userPref, remote ->
        userPref && remote.featureFlags.enableAssistant
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    val entitlements = entitlementManager.entitlementPolicyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.scanium.app.model.user.FreeEntitlements)

    val remoteConfig: StateFlow<RemoteConfig> = configProvider.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RemoteConfig())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setAllowCloud(allow: Boolean) {
        viewModelScope.launch { settingsRepository.setAllowCloudClassification(allow) }
    }

    fun setAllowAssistant(allow: Boolean) {
        viewModelScope.launch { settingsRepository.setAllowAssistant(allow) }
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

    class Factory(
        private val application: Application,
        private val settingsRepository: SettingsRepository,
        private val entitlementManager: EntitlementManager,
        private val configProvider: ConfigProvider
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(application, settingsRepository, entitlementManager, configProvider) as T
        }
    }
}
