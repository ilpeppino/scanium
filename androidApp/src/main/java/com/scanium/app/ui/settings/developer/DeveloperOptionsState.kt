package com.scanium.app.ui.settings.developer

import com.scanium.app.diagnostics.AssistantDiagnosticsState
import com.scanium.app.diagnostics.DiagnosticsState
import com.scanium.app.monitoring.DevHealthMonitorScheduler
import com.scanium.app.monitoring.DevHealthMonitorStateStore
import com.scanium.app.selling.assistant.PreflightResult

/**
 * State holder for System Health section.
 */
data class SystemHealthSectionState(
    val diagnosticsState: DiagnosticsState,
    val autoRefreshEnabled: Boolean,
)

/**
 * State holder for Health Monitor section.
 */
data class HealthMonitorSectionState(
    val monitorState: DevHealthMonitorStateStore.MonitorState,
    val monitorConfig: DevHealthMonitorStateStore.MonitorConfig,
    val workState: DevHealthMonitorScheduler.WorkState,
    val effectiveBaseUrl: String,
)

/**
 * State holder for main screen debug settings and flags.
 */
data class DebugSettingsState(
    val isDeveloperMode: Boolean,
    val allowScreenshots: Boolean,
    val barcodeDetectionEnabled: Boolean,
    val documentDetectionEnabled: Boolean,
    val adaptiveThrottlingEnabled: Boolean,
    val liveScanDiagnosticsEnabled: Boolean,
    val bboxMappingDebugEnabled: Boolean,
    val correlationDebugEnabled: Boolean,
    val cameraPipelineDebugEnabled: Boolean,
    val overlayAccuracyStep: Int,
    val forceFtueTour: Boolean,
    val showFtueDebugBounds: Boolean,
)

/**
 * State holder for classifier diagnostics.
 */
data class ClassifierDiagnosticsState(
    val saveCloudCrops: Boolean,
    val verboseLogging: Boolean,
)

/**
 * Main state holder grouping all developer options state.
 * Note: AssistantDiagnosticsState and PreflightResult are passed separately
 * as they are already well-structured state objects.
 */
data class DeveloperOptionsState(
    val systemHealth: SystemHealthSectionState,
    val assistantDiagnostics: AssistantDiagnosticsState,
    val preflight: PreflightResult,
    val healthMonitor: HealthMonitorSectionState,
    val debugSettings: DebugSettingsState,
    val classifierDiagnostics: ClassifierDiagnosticsState,
    val copyResult: String?,
)
