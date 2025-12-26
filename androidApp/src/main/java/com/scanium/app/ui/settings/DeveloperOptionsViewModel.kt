package com.scanium.app.ui.settings

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scanium.app.data.SettingsRepository
import com.scanium.app.diagnostics.DiagnosticsRepository
import com.scanium.app.diagnostics.DiagnosticsState
import com.scanium.app.ftue.FtueRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for Developer Options screen.
 * Manages diagnostics state and developer settings.
 */
class DeveloperOptionsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val ftueRepository: FtueRepository,
    private val diagnosticsRepository: DiagnosticsRepository
) : AndroidViewModel(application) {

    // Diagnostics state
    val diagnosticsState: StateFlow<DiagnosticsState> = diagnosticsRepository.state

    // Developer settings
    val isDeveloperMode: StateFlow<Boolean> = settingsRepository.developerModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val forceFtueTour: StateFlow<Boolean> = ftueRepository.forceEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Auto-refresh state
    private val _autoRefreshEnabled = MutableStateFlow(false)
    val autoRefreshEnabled: StateFlow<Boolean> = _autoRefreshEnabled.asStateFlow()

    private var autoRefreshJob: Job? = null

    // Copy result feedback
    private val _copyResult = MutableStateFlow<String?>(null)
    val copyResult: StateFlow<String?> = _copyResult.asStateFlow()

    init {
        // Initial refresh on creation
        refreshDiagnostics()
    }

    /**
     * Refresh all diagnostics.
     */
    fun refreshDiagnostics() {
        viewModelScope.launch {
            diagnosticsRepository.refreshAll()
        }
    }

    /**
     * Toggle auto-refresh.
     */
    fun setAutoRefreshEnabled(enabled: Boolean) {
        _autoRefreshEnabled.value = enabled
        if (enabled) {
            startAutoRefresh()
        } else {
            stopAutoRefresh()
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive && _autoRefreshEnabled.value) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                if (_autoRefreshEnabled.value) {
                    diagnosticsRepository.refreshAll()
                }
            }
        }
    }

    private fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    /**
     * Copy diagnostics summary to clipboard.
     */
    fun copyDiagnosticsToClipboard() {
        val summary = diagnosticsRepository.generateDiagnosticsSummary()
        val clipboardManager = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Scanium Diagnostics", summary)
        clipboardManager.setPrimaryClip(clip)
        _copyResult.value = "Diagnostics copied to clipboard"

        // Clear message after delay
        viewModelScope.launch {
            delay(2000)
            _copyResult.value = null
        }
    }

    /**
     * Clear copy result message.
     */
    fun clearCopyResult() {
        _copyResult.value = null
    }

    // Developer settings
    fun setDeveloperMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDeveloperMode(enabled)
        }
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

    fun triggerCrashTest(throwCrash: Boolean = false) {
        if (throwCrash) {
            throw RuntimeException("Test crash from Developer Options")
        } else {
            try {
                throw RuntimeException("Test handled exception from Developer Options")
            } catch (e: Exception) {
                // Log to Sentry/crash reporting as handled exception
                io.sentry.Sentry.captureException(e)
            }
        }
    }

    fun triggerDiagnosticsTest() {
        viewModelScope.launch {
            val summary = diagnosticsRepository.generateDiagnosticsSummary()
            try {
                throw RuntimeException("Test diagnostics bundle")
            } catch (e: Exception) {
                io.sentry.Sentry.captureException(e) { scope ->
                    scope.setExtra("diagnostics", summary)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
    }

    class Factory(
        private val application: Application,
        private val settingsRepository: SettingsRepository,
        private val ftueRepository: FtueRepository,
        private val diagnosticsRepository: DiagnosticsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DeveloperOptionsViewModel::class.java)) {
                return DeveloperOptionsViewModel(
                    application,
                    settingsRepository,
                    ftueRepository,
                    diagnosticsRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        private const val AUTO_REFRESH_INTERVAL_MS = 15_000L // 15 seconds
    }
}
