package com.scanium.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    
    // For manual edition switching (Dev only ideally, but we put it here for now)
    fun setUserEdition(edition: UserEdition) {
        viewModelScope.launch { settingsRepository.setUserEdition(edition) }
    }

    fun refreshRemoteConfig() {
        viewModelScope.launch { configProvider.refresh(force = true) }
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val entitlementManager: EntitlementManager,
        private val configProvider: ConfigProvider
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsRepository, entitlementManager, configProvider) as T
        }
    }
}
