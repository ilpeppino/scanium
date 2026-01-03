package com.scanium.app.ui.settings

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scanium.app.data.SettingsRepository
import com.scanium.app.diagnostics.AssistantDiagnosticsState
import com.scanium.app.diagnostics.BackendReachabilityStatus
import com.scanium.app.diagnostics.BackendStatusClassifier
import com.scanium.app.diagnostics.DiagnosticsRepository
import com.scanium.app.diagnostics.DiagnosticsState
import com.scanium.app.ftue.FtueRepository
import com.scanium.app.model.config.AssistantPrerequisiteState
import com.scanium.app.model.config.ConnectionTestResult
import com.scanium.app.model.config.FeatureFlagRepository
import com.scanium.app.platform.ConnectivityStatus
import com.scanium.app.platform.ConnectivityStatusProvider
import com.scanium.app.selling.assistant.AssistantPreflightManager
import com.scanium.app.selling.assistant.PreflightResult
import com.scanium.app.selling.assistant.PreflightStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Developer Options screen.
 * Manages diagnostics state and developer settings.
 *
 * Part of ARCH-001/DX-003: Migrated to Hilt injection to reduce boilerplate.
 * All dependencies are now injected by Hilt, eliminating the need for a manual Factory class.
 */
@HiltViewModel
class DeveloperOptionsViewModel
    @Inject
    constructor(
        application: Application,
        private val settingsRepository: SettingsRepository,
        private val ftueRepository: FtueRepository,
        private val diagnosticsRepository: DiagnosticsRepository,
        private val featureFlagRepository: FeatureFlagRepository,
        private val connectivityStatusProvider: ConnectivityStatusProvider,
        private val preflightManager: AssistantPreflightManager,
    ) : AndroidViewModel(application) {
        // Diagnostics state
        val diagnosticsState: StateFlow<DiagnosticsState> = diagnosticsRepository.state

        // ==================== Assistant Diagnostics ====================

        private val _assistantDiagnosticsState = MutableStateFlow(AssistantDiagnosticsState())
        val assistantDiagnosticsState: StateFlow<AssistantDiagnosticsState> = _assistantDiagnosticsState.asStateFlow()

        private val _assistantDiagnosticsRefreshing = MutableStateFlow(false)

        // Preflight diagnostics
        val preflightState: StateFlow<PreflightResult> = preflightManager.currentResult
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreflightResult(PreflightStatus.UNKNOWN, 0))

        // Developer settings
        val isDeveloperMode: StateFlow<Boolean> =
            settingsRepository.developerModeFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        val forceFtueTour: StateFlow<Boolean> =
            ftueRepository.forceEnabledFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        val allowScreenshots: StateFlow<Boolean> =
            settingsRepository.devAllowScreenshotsFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

        val showFtueDebugBounds: StateFlow<Boolean> =
            settingsRepository.devShowFtueBoundsFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        // Detection settings (default ON for beta)
        val barcodeDetectionEnabled: StateFlow<Boolean> =
            settingsRepository.devBarcodeDetectionEnabledFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

        val documentDetectionEnabled: StateFlow<Boolean> =
            settingsRepository.devDocumentDetectionEnabledFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

        val adaptiveThrottlingEnabled: StateFlow<Boolean> =
            settingsRepository.devAdaptiveThrottlingEnabledFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

        val scanningDiagnosticsEnabled: StateFlow<Boolean> =
            settingsRepository.devScanningDiagnosticsEnabledFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        val bboxMappingDebugEnabled: StateFlow<Boolean> =
            settingsRepository.devBboxMappingDebugEnabledFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        val correlationDebugEnabled: StateFlow<Boolean> =
            settingsRepository.devCorrelationDebugEnabledFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        val cameraPipelineDebugEnabled: StateFlow<Boolean> =
            settingsRepository.devCameraPipelineDebugEnabledFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        /**
         * Current step index for overlay accuracy filter.
         * Step 0 = "All" (show all detections)
         * Higher steps = filter to higher confidence only
         *
         * @see com.scanium.app.camera.ConfidenceTiers for tier definitions
         */
        val overlayAccuracyStep: StateFlow<Int> =
            settingsRepository.devOverlayAccuracyStepFlow
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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

            // Observe assistant prerequisites and connectivity for live updates
            viewModelScope.launch {
                combine(
                    featureFlagRepository.assistantPrerequisiteState,
                    connectivityStatusProvider.statusFlow,
                ) { prerequisiteState, connectivity ->
                    Pair(prerequisiteState, connectivity)
                }.collect { (prerequisiteState, connectivity) ->
                    updateAssistantDiagnostics(prerequisiteState, connectivity)
                }
            }

            // Initial assistant diagnostics check
            refreshAssistantDiagnostics()
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
         * Refresh assistant-specific diagnostics.
         */
        fun refreshAssistantDiagnostics() {
            if (_assistantDiagnosticsRefreshing.value) return

            viewModelScope.launch {
                _assistantDiagnosticsRefreshing.value = true
                _assistantDiagnosticsState.value =
                    _assistantDiagnosticsState.value.copy(
                        isChecking = true,
                        backendReachable = BackendReachabilityStatus.CHECKING,
                    )

                // Test backend connection
                val connectionResult = featureFlagRepository.testAssistantConnection()
                val backendStatus = BackendStatusClassifier.classify(connectionResult)

                // Update state with connection test result
                _assistantDiagnosticsState.value =
                    _assistantDiagnosticsState.value.copy(
                        backendReachable = backendStatus,
                        connectionTestResult = connectionResult,
                        isChecking = false,
                        lastChecked = System.currentTimeMillis(),
                    )

                _assistantDiagnosticsRefreshing.value = false
            }
        }

        /**
         * Refresh preflight status.
         */
        fun refreshPreflight() {
            viewModelScope.launch {
                preflightManager.preflight(forceRefresh = true)
            }
        }

        /**
         * Clear preflight cache.
         */
        fun clearPreflightCache() {
            viewModelScope.launch {
                preflightManager.clearCache()
            }
        }

        /**
         * Update assistant diagnostics from live flows.
         */
        private fun updateAssistantDiagnostics(
            prerequisiteState: AssistantPrerequisiteState,
            connectivity: ConnectivityStatus,
        ) {
            val context = getApplication<Application>()
            val networkStatus = diagnosticsRepository.checkNetworkStatus()

            _assistantDiagnosticsState.value =
                _assistantDiagnosticsState.value.copy(
                    prerequisiteState = prerequisiteState,
                    isNetworkConnected = connectivity == ConnectivityStatus.ONLINE,
                    networkType = networkStatus.transport.name,
                    hasMicrophonePermission =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED,
                    isSpeechRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context),
                    isTextToSpeechAvailable = true,
// TTS is available on all Android devices
                    isTtsReady = true,
// Basic availability check; actual readiness depends on VoiceController
                )
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
            autoRefreshJob =
                viewModelScope.launch {
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
            val clipboardManager =
                getApplication<Application>()
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

        fun setAllowScreenshots(allowed: Boolean) {
            viewModelScope.launch {
                settingsRepository.setDevAllowScreenshots(allowed)
            }
        }

        /**
         * Reset base URL override to use BuildConfig default.
         */
        fun resetBaseUrlOverride() {
            viewModelScope.launch {
                val context = getApplication<Application>()
                val devOverride = com.scanium.app.config.DevConfigOverride(context)
                devOverride.clearBaseUrlOverride()
                // Refresh diagnostics to update the displayed URL
                diagnosticsRepository.refreshAll()
            }
        }

        fun setShowFtueDebugBounds(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.setDevShowFtueBounds(enabled)
            }
        }

        // Detection settings
        fun setBarcodeDetectionEnabled(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.setDevBarcodeDetectionEnabled(enabled)
            }
        }

        fun setDocumentDetectionEnabled(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.setDevDocumentDetectionEnabled(enabled)
            }
        }

        fun setAdaptiveThrottlingEnabled(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.setDevAdaptiveThrottlingEnabled(enabled)
            }
        }

        fun setScanningDiagnosticsEnabled(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.setDevScanningDiagnosticsEnabled(enabled)
            }
        }

        fun setBboxMappingDebugEnabled(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.setDevBboxMappingDebugEnabled(enabled)
            }
        }

        fun setCorrelationDebugEnabled(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.setDevCorrelationDebugEnabled(enabled)
                // Enable/disable the correlation debug system
                com.scanium.app.camera.geom.CorrelationDebug.setEnabled(enabled)
            }
        }

        fun setCameraPipelineDebugEnabled(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.setDevCameraPipelineDebugEnabled(enabled)
            }
        }

        /**
         * Set the overlay accuracy filter step index.
         *
         * @param stepIndex Index of the tier (0 = show all, higher = more filtering)
         * @see com.scanium.app.camera.ConfidenceTiers for tier definitions
         */
        fun setOverlayAccuracyStep(stepIndex: Int) {
            viewModelScope.launch {
                settingsRepository.setDevOverlayAccuracyStep(stepIndex)
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

        /**
         * @deprecated Use hiltViewModel() instead. This Factory is no longer needed.
         * Part of ARCH-001/DX-003: Manual Factory replaced by @HiltViewModel.
         */
        @Deprecated(
            message = "Use hiltViewModel() instead. This Factory is no longer needed with Hilt.",
            replaceWith = ReplaceWith("hiltViewModel()", "androidx.hilt.navigation.compose.hiltViewModel"),
        )
        class Factory(
            private val application: Application,
            private val settingsRepository: SettingsRepository,
            private val ftueRepository: FtueRepository,
            private val diagnosticsRepository: DiagnosticsRepository,
            private val featureFlagRepository: FeatureFlagRepository,
            private val connectivityStatusProvider: ConnectivityStatusProvider,
            private val preflightManager: AssistantPreflightManager,
        ) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(DeveloperOptionsViewModel::class.java)) {
                    return DeveloperOptionsViewModel(
                        application,
                        settingsRepository,
                        ftueRepository,
                        diagnosticsRepository,
                        featureFlagRepository,
                        connectivityStatusProvider,
                        preflightManager,
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }

        companion object {
            private const val AUTO_REFRESH_INTERVAL_MS = 15_000L // 15 seconds
        }
    }
