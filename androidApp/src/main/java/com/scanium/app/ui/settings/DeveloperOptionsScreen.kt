package com.scanium.app.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanium.app.R
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
    onNavigateBack: () -> Unit,
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
    val liveScanDiagnosticsEnabled by viewModel.scanningDiagnosticsEnabled.collectAsState()
    val bboxMappingDebugEnabled by viewModel.bboxMappingDebugEnabled.collectAsState()
    val correlationDebugEnabled by viewModel.correlationDebugEnabled.collectAsState()
    val cameraPipelineDebugEnabled by viewModel.cameraPipelineDebugEnabled.collectAsState()
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
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState),
        ) {
            // System Health Section
            SystemHealthSection(
                diagnosticsState = diagnosticsState,
                autoRefreshEnabled = autoRefreshEnabled,
                onRefresh = { viewModel.refreshDiagnostics() },
                onCopyDiagnostics = { viewModel.copyDiagnosticsToClipboard() },
                onAutoRefreshChange = { viewModel.setAutoRefreshEnabled(it) },
                onResetBaseUrl = { viewModel.resetBaseUrlOverride() },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Assistant / AI Diagnostics Section
            AssistantDiagnosticsSection(
                state = assistantDiagnosticsState,
                onRecheck = { viewModel.refreshAssistantDiagnostics() },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader("Diagnostics & Security")

            SettingSwitchRow(
                title = "Allow screenshots",
                subtitle = if (allowScreenshots) "Screenshots allowed" else "Screenshots blocked (FLAG_SECURE)",
                icon = Icons.Default.ScreenLockPortrait,
                checked = allowScreenshots,
                onCheckedChange = { viewModel.setAllowScreenshots(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Developer Settings Section
            SettingsSectionHeader("Developer Settings")

            SettingSwitchRow(
                title = "Developer Mode",
                subtitle = "Unlock all features for testing",
                icon = Icons.Default.BugReport,
                checked = isDeveloperMode,
                onCheckedChange = { viewModel.setDeveloperMode(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Detection Settings Section (Performance & Low-Power Mode)
            SettingsSectionHeader("Detection & Performance")

            SettingSwitchRow(
                title = "Barcode/QR Detection",
                subtitle = if (barcodeDetectionEnabled) "Scanning for barcodes and QR codes" else "Barcode detection disabled",
                icon = Icons.Default.CropFree,
                checked = barcodeDetectionEnabled,
                onCheckedChange = { viewModel.setBarcodeDetectionEnabled(it) },
            )

            SettingSwitchRow(
                title = "Document Detection",
                subtitle = if (documentDetectionEnabled) "Detecting document candidates" else "Document detection disabled",
                icon = Icons.Default.Article,
                checked = documentDetectionEnabled,
                onCheckedChange = { viewModel.setDocumentDetectionEnabled(it) },
            )

            SettingSwitchRow(
                title = "Adaptive Throttling",
                subtitle = if (adaptiveThrottlingEnabled) "Low-power mode: auto-adjusts scan rate" else "Fixed scan rate (may drain battery)",
                icon = Icons.Default.Speed,
                checked = adaptiveThrottlingEnabled,
                onCheckedChange = { viewModel.setAdaptiveThrottlingEnabled(it) },
            )

            SettingSwitchRow(
                title = "Live Scan Diagnostics",
                subtitle = if (liveScanDiagnosticsEnabled) "Detailed LiveScan logs enabled (Logcat tag: LiveScan)" else "Diagnostic logging disabled",
                icon = Icons.Default.Analytics,
                checked = liveScanDiagnosticsEnabled,
                onCheckedChange = { viewModel.setScanningDiagnosticsEnabled(it) },
            )

            SettingSwitchRow(
                title = "Geometry Debug Overlay",
                subtitle = if (bboxMappingDebugEnabled) "Shows bbox mapping info: rotation, scale, dimensions (Logcat tag: GeomMap)" else "Geometry debug disabled",
                icon = Icons.Default.GridOn,
                checked = bboxMappingDebugEnabled,
                onCheckedChange = { viewModel.setBboxMappingDebugEnabled(it) },
            )

            SettingSwitchRow(
                title = "Bbox↔Snapshot Correlation",
                subtitle = if (correlationDebugEnabled) "Validates bbox AR matches crop AR (Logcat tag: CORR)" else "Correlation validation disabled",
                icon = Icons.Default.Compare,
                checked = correlationDebugEnabled,
                onCheckedChange = { viewModel.setCorrelationDebugEnabled(it) },
            )

            SettingSwitchRow(
                title = "Camera Pipeline Debug",
                subtitle = if (cameraPipelineDebugEnabled) "Shows lifecycle/session overlay (Logcat tag: CAM_LIFE)" else "Pipeline debug disabled",
                icon = Icons.Default.Videocam,
                checked = cameraPipelineDebugEnabled,
                onCheckedChange = { viewModel.setCameraPipelineDebugEnabled(it) },
            )

            SettingsSectionHeader("Classifier Diagnostics")
            SettingSwitchRow(
                title = "Save cloud crops",
                subtitle = "Writes outgoing classifier crops to cache (cleared on uninstall)",
                icon = Icons.Default.Cloud,
                checked = saveCloudCrops,
                onCheckedChange = classificationViewModel::updateSaveCloudCrops,
            )

            SettingSwitchRow(
                title = "Verbose classifier logging",
                subtitle = "Adds extra classifier details to Logcat (debug builds only)",
                icon = Icons.Default.Tune,
                checked = verboseLogging,
                onCheckedChange = classificationViewModel::updateVerboseLogging,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Testing Section
            SettingsSectionHeader("Testing & Debug")

            SettingActionRow(
                title = "Test Crash Reporting",
                subtitle = "Send test event to Sentry (handled exception)",
                icon = Icons.Default.BugReport,
                onClick = { viewModel.triggerCrashTest(throwCrash = false) },
            )

            SettingActionRow(
                title = "Test Diagnostics Bundle",
                subtitle = "Capture exception with diagnostics.json attachment",
                icon = Icons.Default.BugReport,
                onClick = { viewModel.triggerDiagnosticsTest() },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // First-Time Experience Section
            SettingsSectionHeader("First-Time Experience")

            SettingSwitchRow(
                title = "Force First-Time Tour",
                subtitle = "Always show tour on app launch (debug only)",
                icon = Icons.Default.Info,
                checked = forceFtueTour,
                onCheckedChange = { viewModel.setForceFtueTour(it) },
            )

            SettingSwitchRow(
                title = "Show FTUE debug bounds",
                subtitle = "Draw spotlight outlines and center line",
                icon = Icons.Default.CenterFocusStrong,
                checked = showFtueDebugBounds,
                onCheckedChange = { viewModel.setShowFtueDebugBounds(it) },
            )

            SettingActionRow(
                title = "Reset Tour Progress",
                subtitle = "Clear tour completion flag",
                icon = Icons.Default.Refresh,
                onClick = { viewModel.resetFtueTour() },
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
    onAutoRefreshChange: (Boolean) -> Unit,
    onResetBaseUrl: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        // Header with actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "System Health",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Auto-refresh toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Auto",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = autoRefreshEnabled,
                        onCheckedChange = onAutoRefreshChange,
                        modifier = Modifier.height(24.dp),
                    )
                }

                // Refresh button
                FilledTonalIconButton(
                    onClick = onRefresh,
                    enabled = !diagnosticsState.isRefreshing,
                ) {
                    if (diagnosticsState.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
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
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Backend Health
                HealthCheckRow(
                    icon = Icons.Default.Cloud,
                    name = "Backend",
                    status = diagnosticsState.backendHealth.status,
                    detail = diagnosticsState.backendHealth.detail,
                    latencyMs = diagnosticsState.backendHealth.latencyMs,
                    lastChecked = diagnosticsState.backendHealth.lastCheckedFormatted,
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
                    modifier = Modifier.padding(bottom = 4.dp),
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
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                diagnosticsState.capabilities.forEach { cap ->
                    CapabilityRow(capability = cap)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // App Config
                diagnosticsState.appConfig?.let { config ->
                    AppConfigSection(
                        config = config,
                        onResetBaseUrl = if (config.isBaseUrlOverridden) {
                            onResetBaseUrl
                        } else {
                            null
                        },
                    )
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
    lastChecked: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                icon,
                contentDescription = stringResource(R.string.cd_service_status),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    StatusIndicator(status = status)
                }
                detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            latencyMs?.let {
                Text(
                    text = "${it}ms",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                text = lastChecked,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NetworkStatusRow(networkStatus: NetworkStatus) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                when (networkStatus.transport) {
                    NetworkTransport.WIFI -> Icons.Default.Wifi
                    NetworkTransport.CELLULAR -> Icons.Default.SignalCellularAlt
                    NetworkTransport.ETHERNET -> Icons.Default.SettingsEthernet
                    NetworkTransport.VPN -> Icons.Default.VpnKey
                    else -> Icons.Default.SignalWifiOff
                },
                contentDescription = stringResource(R.string.cd_network_status),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Network",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    StatusIndicator(
                        status = if (networkStatus.isConnected) HealthStatus.HEALTHY else HealthStatus.DOWN,
                    )
                }
                Text(
                    text =
                        buildString {
                            append(networkStatus.transport.name)
                            if (networkStatus.isMetered) append(" (Metered)")
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = if (networkStatus.isConnected) "Connected" else "Not connected",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun PermissionRow(permission: PermissionStatus) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (permission.name == "Camera") Icons.Default.CameraAlt else Icons.Default.Mic,
                    contentDescription = stringResource(R.string.cd_permission_status),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = permission.name,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (permission.isGranted) {
                                Color(0xFF4CAF50)
                            } else {
                                Color(0xFFF44336)
                            },
                        ),
            )
            Text(
                text = if (permission.isGranted) "Granted" else "Not granted",
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (permission.isGranted) {
                        Color(0xFF4CAF50)
                    } else {
                        Color(0xFFF44336)
                    },
            )
        }
    }
}

@Composable
private fun CapabilityRow(capability: CapabilityStatus) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when (capability.name) {
                        "Speech Recognition" -> Icons.Default.Mic
                        "Text-to-Speech" -> Icons.Default.VolumeUp
                        "Camera Lenses" -> Icons.Default.CameraAlt
                        else -> Icons.Default.Settings
                    },
                    contentDescription = stringResource(R.string.cd_capability_status),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column {
                Text(
                    text = capability.name,
                    style = MaterialTheme.typography.bodyMedium,
                )
                capability.detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (capability.isAvailable) {
                            Color(0xFF4CAF50)
                        } else {
                            Color(0xFFF44336)
                        },
                    ),
        )
    }
}

@Composable
private fun AppConfigSection(
    config: AppConfigSnapshot,
    onResetBaseUrl: (() -> Unit)? = null,
) {
    Column {
        Text(
            text = "App Configuration",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        ConfigRow("Version", "${config.versionName} (${config.versionCode})")
        ConfigRow("Build", config.buildType)
        ConfigRow("Device", config.deviceModel)
        ConfigRow("Android", "${config.androidVersion} (SDK ${config.sdkInt})")

        // Base URL with override indicator
        val baseUrlLabel = when {
            config.isBaseUrlOverridden -> "Base URL (OVERRIDE)"
            else -> "Base URL"
        }
        ConfigRow(
            label = baseUrlLabel,
            value = config.baseUrl,
            isWarning = config.hasBaseUrlWarning,
        )

        // Show BuildConfig URL if overridden
        if (config.isBaseUrlOverridden) {
            ConfigRow(
                label = "BuildConfig URL",
                value = config.buildConfigBaseUrl,
                isSecondary = true,
            )

            // Reset button
            if (onResetBaseUrl != null) {
                TextButton(
                    onClick = onResetBaseUrl,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("Reset to BuildConfig default")
                }
            }
        }
    }
}

@Composable
private fun ConfigRow(
    label: String,
    value: String,
    isWarning: Boolean = false,
    isSecondary: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = when {
                isWarning -> MaterialTheme.colorScheme.error
                isSecondary -> MaterialTheme.colorScheme.outline
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = when {
                isWarning -> MaterialTheme.colorScheme.error
                isSecondary -> MaterialTheme.colorScheme.outline
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun StatusIndicator(status: HealthStatus) {
    val color by animateColorAsState(
        targetValue =
            when (status) {
                HealthStatus.HEALTHY -> Color(0xFF4CAF50) // Green
                HealthStatus.DEGRADED -> Color(0xFFFF9800) // Orange
                HealthStatus.DOWN -> Color(0xFFF44336) // Red
                HealthStatus.UNKNOWN -> Color(0xFF9E9E9E) // Gray
            },
        label = "status_color",
    )

    val text =
        when (status) {
            HealthStatus.HEALTHY -> "Healthy"
            HealthStatus.DEGRADED -> "Degraded"
            HealthStatus.DOWN -> "Down"
            HealthStatus.UNKNOWN -> "Unknown"
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
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
    onRecheck: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        // Header with recheck button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = stringResource(R.string.cd_ai_model),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Assistant / AI Diagnostics",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Recheck button
            FilledTonalIconButton(
                onClick = onRecheck,
                enabled = !state.isChecking,
            ) {
                if (state.isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Recheck")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Overall status badge with debug detail and status message
        val connectionResult = state.connectionTestResult
        val debugDetail = when (connectionResult) {
            is ConnectionTestResult.Success -> "GET ${connectionResult.endpoint} -> ${connectionResult.httpStatus}"
            is ConnectionTestResult.Failure -> connectionResult.debugDetail
            null -> null
        }
        // Use BackendStatusClassifier for accurate status messages with HTTP codes
        val statusMessage = connectionResult?.let { BackendStatusClassifier.getStatusMessage(it) }
        AssistantOverallStatusBadge(
            readiness = state.overallReadiness,
            debugDetail = debugDetail,
            statusMessage = statusMessage,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Diagnostics card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Backend Reachability
                AssistantDiagnosticRow(
                    icon = Icons.Default.Cloud,
                    label = "Backend Reachability",
                    status =
                        when (state.backendReachable) {
                            BackendReachabilityStatus.REACHABLE -> DiagnosticStatus.OK
                            BackendReachabilityStatus.UNREACHABLE -> DiagnosticStatus.ERROR
                            BackendReachabilityStatus.UNAUTHORIZED -> DiagnosticStatus.WARNING
                            BackendReachabilityStatus.SERVER_ERROR -> DiagnosticStatus.WARNING
                            BackendReachabilityStatus.NOT_FOUND -> DiagnosticStatus.WARNING
                            BackendReachabilityStatus.NOT_CONFIGURED -> DiagnosticStatus.WARNING
                            BackendReachabilityStatus.DEGRADED -> DiagnosticStatus.WARNING
                            BackendReachabilityStatus.CHECKING -> DiagnosticStatus.CHECKING
                            BackendReachabilityStatus.UNKNOWN -> DiagnosticStatus.UNKNOWN
                        },
                    detail =
                        when (val result = state.connectionTestResult) {
                            is ConnectionTestResult.Success -> "Connected (${result.httpStatus})"
                            is ConnectionTestResult.Failure -> result.message
                            null -> if (state.isChecking) "Checking..." else "Not checked"
                        },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Assistant Readiness (Prerequisites)
                AssistantDiagnosticRow(
                    icon = Icons.Default.CheckCircle,
                    label = "Assistant Readiness",
                    status =
                        when {
                            state.prerequisiteState.allSatisfied -> DiagnosticStatus.OK
                            state.prerequisiteState.unsatisfiedCount > 0 -> DiagnosticStatus.WARNING
                            else -> DiagnosticStatus.UNKNOWN
                        },
                    detail =
                        if (state.prerequisiteState.allSatisfied) {
                            "All ${state.prerequisiteState.prerequisites.size} prerequisites met"
                        } else {
                            "${state.prerequisiteState.unsatisfiedCount} prerequisites not met"
                        },
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
                    detail =
                        if (state.isNetworkConnected) {
                            "Connected (${state.networkType})"
                        } else {
                            "Not connected"
                        },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Microphone Permission
                AssistantDiagnosticRow(
                    icon = Icons.Default.Mic,
                    label = "Microphone Permission",
                    status = if (state.hasMicrophonePermission) DiagnosticStatus.OK else DiagnosticStatus.WARNING,
                    detail = if (state.hasMicrophonePermission) "Granted" else "Not granted",
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Speech Recognition Availability
                AssistantDiagnosticRow(
                    icon = Icons.Default.RecordVoiceOver,
                    label = "Speech Recognition",
                    status = if (state.isSpeechRecognitionAvailable) DiagnosticStatus.OK else DiagnosticStatus.ERROR,
                    detail = if (state.isSpeechRecognitionAvailable) "Available" else "Not available on this device",
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Text-to-Speech Availability
                AssistantDiagnosticRow(
                    icon = Icons.Default.VolumeUp,
                    label = "Text-to-Speech",
                    status = if (state.isTextToSpeechAvailable) DiagnosticStatus.OK else DiagnosticStatus.ERROR,
                    detail =
                        if (state.isTextToSpeechAvailable) {
                            if (state.isTtsReady) "Available and ready" else "Available"
                        } else {
                            "Not available"
                        },
                )

                // Last checked timestamp
                if (state.lastChecked > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Last checked: ${formatTimestamp(state.lastChecked)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Overall status badge for assistant readiness.
 * Shows accurate status messages distinguishing unreachable vs unauthorized vs server errors.
 * When [statusMessage] is provided, uses it for backend-related error states to include HTTP codes.
 */
@Composable
private fun AssistantOverallStatusBadge(
    readiness: AssistantReadiness,
    debugDetail: String? = null,
    statusMessage: String? = null,
) {
    val (color, defaultText, icon) =
        when (readiness) {
            AssistantReadiness.READY ->
                Triple(
                    Color(0xFF4CAF50),
                    "Assistant Ready",
                    Icons.Default.CheckCircle,
                )
            AssistantReadiness.CHECKING ->
                Triple(
                    Color(0xFF2196F3),
                    "Checking...",
                    Icons.Default.Sync,
                )
            AssistantReadiness.NO_NETWORK ->
                Triple(
                    Color(0xFFF44336),
                    "No Network Connection",
                    Icons.Default.WifiOff,
                )
            AssistantReadiness.BACKEND_UNREACHABLE ->
                Triple(
                    Color(0xFFF44336),
                    "Backend Unreachable",
                    Icons.Default.CloudOff,
                )
            AssistantReadiness.BACKEND_UNAUTHORIZED ->
                Triple(
                    Color(0xFFFF9800),
                    "Backend Reachable — Invalid API Key",
                    Icons.Default.Lock,
                )
            AssistantReadiness.BACKEND_SERVER_ERROR ->
                Triple(
                    Color(0xFFFF9800),
                    "Backend Reachable — Server Error",
                    Icons.Default.Error,
                )
            AssistantReadiness.BACKEND_NOT_FOUND ->
                Triple(
                    Color(0xFFFF9800),
                    "Backend Reachable — Endpoint Not Found",
                    Icons.Default.SearchOff,
                )
            AssistantReadiness.BACKEND_NOT_CONFIGURED ->
                Triple(
                    Color(0xFF9E9E9E),
                    "Backend Not Configured",
                    Icons.Default.Settings,
                )
            AssistantReadiness.PREREQUISITES_NOT_MET ->
                Triple(
                    Color(0xFFFF9800),
                    "Prerequisites Not Met",
                    Icons.Default.Warning,
                )
            AssistantReadiness.UNKNOWN ->
                Triple(
                    Color(0xFF9E9E9E),
                    "Unknown Status",
                    Icons.Default.Help,
                )
        }

    // Use statusMessage from BackendStatusClassifier for backend-related states (includes HTTP codes)
    val text = when (readiness) {
        AssistantReadiness.BACKEND_UNREACHABLE,
        AssistantReadiness.BACKEND_UNAUTHORIZED,
        AssistantReadiness.BACKEND_SERVER_ERROR,
        AssistantReadiness.BACKEND_NOT_FOUND,
        AssistantReadiness.BACKEND_NOT_CONFIGURED -> statusMessage ?: defaultText
        else -> defaultText
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = text,
                    tint = color,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
            }
            // Show debug detail (endpoint + status) for error states
            if (debugDetail != null && readiness != AssistantReadiness.READY && readiness != AssistantReadiness.CHECKING) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = debugDetail,
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.8f),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/**
 * Status enum for diagnostic rows.
 */
private enum class DiagnosticStatus {
    OK,
    WARNING,
    ERROR,
    CHECKING,
    UNKNOWN,
}

/**
 * Single diagnostic row with icon, label, status indicator, and detail.
 */
@Composable
private fun AssistantDiagnosticRow(
    icon: ImageVector,
    label: String,
    status: DiagnosticStatus,
    detail: String,
) {
    val statusColor =
        when (status) {
            DiagnosticStatus.OK -> Color(0xFF4CAF50)
            DiagnosticStatus.WARNING -> Color(0xFFFF9800)
            DiagnosticStatus.ERROR -> Color(0xFFF44336)
            DiagnosticStatus.CHECKING -> Color(0xFF2196F3)
            DiagnosticStatus.UNKNOWN -> Color(0xFF9E9E9E)
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor),
                    )
                }
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Small row showing prerequisite detail.
 */
@Composable
private fun PrerequisiteDetailRow(
    name: String,
    satisfied: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 36.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (satisfied) Icons.Default.Check else Icons.Default.Close,
            contentDescription =
                if (satisfied) {
                    stringResource(
                        R.string.cd_prerequisite_satisfied,
                    )
                } else {
                    stringResource(R.string.cd_prerequisite_not_satisfied)
                },
            tint = if (satisfied) Color(0xFF4CAF50) else Color(0xFFF44336),
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color =
                if (satisfied) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    Color(0xFFF44336)
                },
        )
    }
}

/**
 * Format timestamp to readable time.
 */
private fun formatTimestamp(timestamp: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
