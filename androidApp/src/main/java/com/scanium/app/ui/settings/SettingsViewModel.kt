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

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val entitlementManager: EntitlementManager
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val allowCloud: StateFlow<Boolean> = settingsRepository.allowCloudClassificationFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val allowAssistant: StateFlow<Boolean> = settingsRepository.allowAssistantFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val entitlementManager: EntitlementManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsRepository, entitlementManager) as T
        }
    }
}
