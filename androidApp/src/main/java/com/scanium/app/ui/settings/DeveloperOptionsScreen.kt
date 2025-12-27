package com.scanium.app.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanium.app.diagnostics.*
import com.scanium.app.model.config.ConnectionTestResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Developer Options screen with System Health diagnostics.
 * Only visible in debug builds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperOptionsScreen(
    viewModel: DeveloperOptionsViewModel,
    classificationViewModel: com.scanium.app.settings.ClassificationModeViewModel,
    onNavigateBack: () -> Unit
) {
    val diagnosticsState by viewModel.diagnosticsState.collectAsState()
    val assistantDiagnosticsState by viewModel.assistantDiagnosticsState.collectAsState()
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()
    val forceFtueTour by viewModel.forceFtueTour.collectAsState()
    val showFtueDebugBounds by viewModel.showFtueDebugBounds.collectAsState()
    val autoRefreshEnabled by viewModel.autoRefreshEnabled.collectAsState()
    val copyResult by viewModel.copyResult.collectAsState()
    val allowScreenshots by viewModel.allowScreenshots.collectAsState()
    val barcodeDetectionEnabled by viewModel.barcodeDetectionEnabled.collectAsState()
    val documentDetectionEnabled by viewModel.documentDetectionEnabled.collectAsState()
    val adaptiveThrottlingEnabled by viewModel.adaptiveThrottlingEnabled.collectAsState()
    val scrollState = rememberScrollState()
    val saveCloudCrops by classificationViewModel.saveCloudCrops.collectAsState()
    val verboseLogging by classificationViewModel.verboseLogging.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar for copy result
    LaunchedEffect(copyResult) {
        copyResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearCopyResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Options") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            // System Health Section
            SystemHealthSection(
                diagnosticsState = diagnosticsState,
                autoRefreshEnabled = autoRefreshEnabled,
                onRefresh = { viewModel.refreshDiagnostics() },
                onCopyDiagnostics = { viewModel.copyDiagnosticsToClipboard() },
                onAutoRefreshChange = { viewModel.setAutoRefreshEnabled(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Assistant / AI Diagnostics Section
            AssistantDiagnosticsSection(
                state = assistantDiagnosticsState,
                onRecheck = { viewModel.refreshAssistantDiagnostics() }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader("Diagnostics & Security")

            SettingSwitchRow(
                title = "Allow screenshots",
                subtitle = if (allowScreenshots) "Screenshots allowed" else "Screenshots blocked (FLAG_SECURE)",
                icon = Icons.Default.ScreenLockPortrait,
                checked = allowScreenshots,
                onCheckedChange = { viewModel.setAllowScreenshots(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Developer Settings Section
            SettingsSectionHeader("Developer Settings")

            SettingSwitchRow(
                title = "Developer Mode",
                subtitle = "Unlock all features for testing",
                icon = Icons.Default.BugReport,
                checked = isDeveloperMode,
                onCheckedChange = { viewModel.setDeveloperMode(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Detection Settings Section (Performance & Low-Power Mode)
            SettingsSectionHeader("Detection & Performance")

            SettingSwitchRow(
                title = "Barcode/QR Detection",
                subtitle = if (barcodeDetectionEnabled) "Scanning for barcodes and QR codes" else "Barcode detection disabled",
                icon = Icons.Default.CropFree,
                checked = barcodeDetectionEnabled,
                onCheckedChange = { viewModel.setBarcodeDetectionEnabled(it) }
            )

            SettingSwitchRow(
                title = "Document Detection",
                subtitle = if (documentDetectionEnabled) "Detecting document candidates" else "Document detection disabled",
                icon = Icons.Default.Article,
                checked = documentDetectionEnabled,
                onCheckedChange = { viewModel.setDocumentDetectionEnabled(it) }
            )

            SettingSwitchRow(
                title = "Adaptive Throttling",
                subtitle = if (adaptiveThrottlingEnabled) "Low-power mode: auto-adjusts scan rate" else "Fixed scan rate (may drain battery)",
                icon = Icons.Default.Speed,
                checked = adaptiveThrottlingEnabled,
                onCheckedChange = { viewModel.setAdaptiveThrottlingEnabled(it) }
            )

            SettingsSectionHeader("Classifier Diagnostics")
            SettingSwitchRow(
                title = "Save cloud crops",
                subtitle = "Writes outgoing classifier crops to cache (cleared on uninstall)",
                icon = Icons.Default.Cloud,
                checked = saveCloudCrops,
                onCheckedChange = classificationViewModel::updateSaveCloudCrops
            )

            SettingSwitchRow(
                title = "Verbose classifier logging",
                subtitle = "Adds extra classifier details to Logcat (debug builds only)",
                icon = Icons.Default.Tune,
                checked = verboseLogging,
                onCheckedChange = classificationViewModel::updateVerboseLogging
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Testing Section
            SettingsSectionHeader("Testing & Debug")

            SettingActionRow(
                title = "Test Crash Reporting",
                subtitle = "Send test event to Sentry (handled exception)",
                icon = Icons.Default.BugReport,
                onClick = { viewModel.triggerCrashTest(throwCrash = false) }
            )

            SettingActionRow(
                title = "Test Diagnostics Bundle",
                subtitle = "Capture exception with diagnostics.json attachment",
                icon = Icons.Default.BugReport,
                onClick = { viewModel.triggerDiagnosticsTest() }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // First-Time Experience Section
            SettingsSectionHeader("First-Time Experience")

            SettingSwitchRow(
                title = "Force First-Time Tour",
                subtitle = "Always show tour on app launch (debug only)",
                icon = Icons.Default.Info,
                checked = forceFtueTour,
                onCheckedChange = { viewModel.setForceFtueTour(it) }
            )

            SettingSwitchRow(
                title = "Show FTUE debug bounds",
                subtitle = "Draw spotlight outlines and center line",
                icon = Icons.Default.CenterFocusStrong,
                checked = showFtueDebugBounds,
                onCheckedChange = { viewModel.setShowFtueDebugBounds(it) }
            )

            SettingActionRow(
                title = "Reset Tour Progress",
                subtitle = "Clear tour completion flag",
                icon = Icons.Default.Refresh,
                onClick = { viewModel.resetFtueTour() }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SystemHealthSection(
    diagnosticsState: DiagnosticsState,
    autoRefreshEnabled: Boolean,
    onRefresh: () -> Unit,
    onCopyDiagnostics: () -> Unit,
    onAutoRefreshChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header with actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "System Health",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Auto-refresh toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Auto",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = autoRefreshEnabled,
                        onCheckedChange = onAutoRefreshChange,
                        modifier = Modifier.height(24.dp)
                    )
                }

                // Refresh button
                FilledTonalIconButton(
                    onClick = onRefresh,
                    enabled = !diagnosticsState.isRefreshing
                ) {
                    if (diagnosticsState.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }

                // Copy button
                FilledTonalIconButton(onClick = onCopyDiagnostics) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy diagnostics")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Health check cards
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Backend Health
                HealthCheckRow(
                    icon = Icons.Default.Cloud,
                    name = "Backend",
                    status = diagnosticsState.backendHealth.status,
                    detail = diagnosticsState.backendHealth.detail,
                    latencyMs = diagnosticsState.backendHealth.latencyMs,
                    lastChecked = diagnosticsState.backendHealth.lastCheckedFormatted
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Network Status
                NetworkStatusRow(networkStatus = diagnosticsState.networkStatus)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Permissions
                Text(
                    text = "Permissions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                diagnosticsState.permissions.forEach { perm ->
                    PermissionRow(permission = perm)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Capabilities
                Text(
                    text = "Capabilities",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                diagnosticsState.capabilities.forEach { cap ->
                    CapabilityRow(capability = cap)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // App Config
                diagnosticsState.appConfig?.let { config ->
                    AppConfigSection(config = config)
                }
            }
        }
    }
}

@Composable
private fun HealthCheckRow(
    icon: ImageVector,
    name: String,
    status: HealthStatus,
    detail: String?,
    latencyMs: Long?,
    lastChecked: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    StatusIndicator(status = status)
                }
                detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            latencyMs?.let {
                Text(
                    text = "${it}ms",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = lastChecked,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NetworkStatusRow(networkStatus: NetworkStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                when (networkStatus.transport) {
                    NetworkTransport.WIFI -> Icons.Default.Wifi
                    NetworkTransport.CELLULAR -> Icons.Default.SignalCellularAlt
                    NetworkTransport.ETHERNET -> Icons.Default.SettingsEthernet
                    NetworkTransport.VPN -> Icons.Default.VpnKey
                    else -> Icons.Default.SignalWifiOff
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Network",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    StatusIndicator(
                        status = if (networkStatus.isConnected) HealthStatus.HEALTHY else HealthStatus.DOWN
                    )
                }
                Text(
                    text = buildString {
                        append(networkStatus.transport.name)
                        if (networkStatus.isMetered) append(" (Metered)")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = if (networkStatus.isConnected) "Connected" else "Not connected",
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun PermissionRow(permission: PermissionStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (permission.name == "Camera") Icons.Default.CameraAlt else Icons.Default.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = permission.name,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (permission.isGranted) Color(0xFF4CAF50)
                        else Color(0xFFF44336)
                    )
            )
            Text(
                text = if (permission.isGranted) "Granted" else "Not granted",
                style = MaterialTheme.typography.labelSmall,
                color = if (permission.isGranted)
                    Color(0xFF4CAF50)
                else
                    Color(0xFFF44336)
            )
        }
    }
}

@Composable
private fun CapabilityRow(capability: CapabilityStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                when (capability.name) {
                    "Speech Recognition" -> Icons.Default.Mic
                    "Text-to-Speech" -> Icons.Default.VolumeUp
                    "Camera Lenses" -> Icons.Default.CameraAlt
                    else -> Icons.Default.Settings
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = capability.name,
                    style = MaterialTheme.typography.bodyMedium
                )
                capability.detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (capability.isAvailable) Color(0xFF4CAF50)
                    else Color(0xFFF44336)
                )
        )
    }
}

@Composable
private fun AppConfigSection(config: AppConfigSnapshot) {
    Column {
        Text(
            text = "App Configuration",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        ConfigRow("Version", "${config.versionName} (${config.versionCode})")
        ConfigRow("Build", config.buildType)
        ConfigRow("Device", config.deviceModel)
        ConfigRow("Android", "${config.androidVersion} (SDK ${config.sdkInt})")
        ConfigRow("Base URL", config.baseUrl)
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun StatusIndicator(status: HealthStatus) {
    val color by animateColorAsState(
        targetValue = when (status) {
            HealthStatus.HEALTHY -> Color(0xFF4CAF50) // Green
            HealthStatus.DEGRADED -> Color(0xFFFF9800) // Orange
            HealthStatus.DOWN -> Color(0xFFF44336) // Red
            HealthStatus.UNKNOWN -> Color(0xFF9E9E9E) // Gray
        },
        label = "status_color"
    )

    val text = when (status) {
        HealthStatus.HEALTHY -> "Healthy"
        HealthStatus.DEGRADED -> "Degraded"
        HealthStatus.DOWN -> "Down"
        HealthStatus.UNKNOWN -> "Unknown"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

// ==================== Assistant / AI Diagnostics Section ====================

/**
 * Assistant / AI Diagnostics section showing assistant prerequisites and capabilities.
 * Developer-only, read-only diagnostics panel.
 */
@Composable
private fun AssistantDiagnosticsSection(
    state: AssistantDiagnosticsState,
    onRecheck: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header with recheck button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Assistant / AI Diagnostics",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Recheck button
            FilledTonalIconButton(
                onClick = onRecheck,
                enabled = !state.isChecking
            ) {
                if (state.isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Recheck")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Overall status badge
        AssistantOverallStatusBadge(readiness = state.overallReadiness)

        Spacer(modifier = Modifier.height(16.dp))

        // Diagnostics card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Backend Reachability
                AssistantDiagnosticRow(
                    icon = Icons.Default.Cloud,
                    label = "Backend Reachability",
                    status = when (state.backendReachable) {
                        BackendReachabilityStatus.REACHABLE -> DiagnosticStatus.OK
                        BackendReachabilityStatus.UNREACHABLE -> DiagnosticStatus.ERROR
                        BackendReachabilityStatus.DEGRADED -> DiagnosticStatus.WARNING
                        BackendReachabilityStatus.CHECKING -> DiagnosticStatus.CHECKING
                        BackendReachabilityStatus.UNKNOWN -> DiagnosticStatus.UNKNOWN
                    },
                    detail = when (val result = state.connectionTestResult) {
                        is ConnectionTestResult.Success -> "Connected"
                        is ConnectionTestResult.Failure -> result.message
                        null -> if (state.isChecking) "Checking..." else "Not checked"
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Assistant Readiness (Prerequisites)
                AssistantDiagnosticRow(
                    icon = Icons.Default.CheckCircle,
                    label = "Assistant Readiness",
                    status = when {
                        state.prerequisiteState.allSatisfied -> DiagnosticStatus.OK
                        state.prerequisiteState.unsatisfiedCount > 0 -> DiagnosticStatus.WARNING
                        else -> DiagnosticStatus.UNKNOWN
                    },
                    detail = if (state.prerequisiteState.allSatisfied) {
                        "All ${state.prerequisiteState.prerequisites.size} prerequisites met"
                    } else {
                        "${state.prerequisiteState.unsatisfiedCount} prerequisites not met"
                    }
                )

                // Show prerequisite details
                if (state.prerequisiteState.prerequisites.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    state.prerequisiteState.prerequisites.forEach { prereq ->
                        PrerequisiteDetailRow(prereq.displayName, prereq.satisfied)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Network State
                AssistantDiagnosticRow(
                    icon = if (state.isNetworkConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                    label = "Network State",
                    status = if (state.isNetworkConnected) DiagnosticStatus.OK else DiagnosticStatus.ERROR,
                    detail = if (state.isNetworkConnected) {
                        "Connected (${state.networkType})"
                    } else {
                        "Not connected"
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Microphone Permission
                AssistantDiagnosticRow(
                    icon = Icons.Default.Mic,
                    label = "Microphone Permission",
                    status = if (state.hasMicrophonePermission) DiagnosticStatus.OK else DiagnosticStatus.WARNING,
                    detail = if (state.hasMicrophonePermission) "Granted" else "Not granted"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Speech Recognition Availability
                AssistantDiagnosticRow(
                    icon = Icons.Default.RecordVoiceOver,
                    label = "Speech Recognition",
                    status = if (state.isSpeechRecognitionAvailable) DiagnosticStatus.OK else DiagnosticStatus.ERROR,
                    detail = if (state.isSpeechRecognitionAvailable) "Available" else "Not available on this device"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Text-to-Speech Availability
                AssistantDiagnosticRow(
                    icon = Icons.Default.VolumeUp,
                    label = "Text-to-Speech",
                    status = if (state.isTextToSpeechAvailable) DiagnosticStatus.OK else DiagnosticStatus.ERROR,
                    detail = if (state.isTextToSpeechAvailable) {
                        if (state.isTtsReady) "Available and ready" else "Available"
                    } else {
                        "Not available"
                    }
                )

                // Last checked timestamp
                if (state.lastChecked > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Last checked: ${formatTimestamp(state.lastChecked)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Overall status badge for assistant readiness.
 */
@Composable
private fun AssistantOverallStatusBadge(readiness: AssistantReadiness) {
    val (color, text, icon) = when (readiness) {
        AssistantReadiness.READY -> Triple(
            Color(0xFF4CAF50),
            "Assistant Ready",
            Icons.Default.CheckCircle
        )
        AssistantReadiness.CHECKING -> Triple(
            Color(0xFF2196F3),
            "Checking...",
            Icons.Default.Sync
        )
        AssistantReadiness.NO_NETWORK -> Triple(
            Color(0xFFF44336),
            "No Network Connection",
            Icons.Default.WifiOff
        )
        AssistantReadiness.BACKEND_UNREACHABLE -> Triple(
            Color(0xFFF44336),
            "Backend Unreachable",
            Icons.Default.CloudOff
        )
        AssistantReadiness.PREREQUISITES_NOT_MET -> Triple(
            Color(0xFFFF9800),
            "Prerequisites Not Met",
            Icons.Default.Warning
        )
        AssistantReadiness.UNKNOWN -> Triple(
            Color(0xFF9E9E9E),
            "Unknown Status",
            Icons.Default.Help
        )
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

/**
 * Status enum for diagnostic rows.
 */
private enum class DiagnosticStatus {
    OK, WARNING, ERROR, CHECKING, UNKNOWN
}

/**
 * Single diagnostic row with icon, label, status indicator, and detail.
 */
@Composable
private fun AssistantDiagnosticRow(
    icon: ImageVector,
    label: String,
    status: DiagnosticStatus,
    detail: String
) {
    val statusColor = when (status) {
        DiagnosticStatus.OK -> Color(0xFF4CAF50)
        DiagnosticStatus.WARNING -> Color(0xFFFF9800)
        DiagnosticStatus.ERROR -> Color(0xFFF44336)
        DiagnosticStatus.CHECKING -> Color(0xFF2196F3)
        DiagnosticStatus.UNKNOWN -> Color(0xFF9E9E9E)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                }
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Small row showing prerequisite detail.
 */
@Composable
private fun PrerequisiteDetailRow(name: String, satisfied: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 36.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            if (satisfied) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (satisfied) Color(0xFF4CAF50) else Color(0xFFF44336),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = if (satisfied) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                Color(0xFFF44336)
            }
        )
    }
}

/**
 * Format timestamp to readable time.
 */
private fun formatTimestamp(timestamp: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
