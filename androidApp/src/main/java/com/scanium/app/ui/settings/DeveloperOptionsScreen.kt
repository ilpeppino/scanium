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
import com.scanium.app.camera.ConfidenceTiers
import com.scanium.app.config.FeatureFlags
import com.scanium.app.diagnostics.*
import com.scanium.app.model.config.ConnectionTestResult
import com.scanium.app.monitoring.DevHealthMonitorScheduler
import com.scanium.app.monitoring.DevHealthMonitorStateStore
import com.scanium.app.monitoring.MonitorHealthStatus
import com.scanium.app.selling.assistant.PreflightResult
import com.scanium.app.selling.assistant.PreflightStatus
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
    val preflightState by viewModel.preflightState.collectAsState()
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
    val overlayAccuracyStep by viewModel.overlayAccuracyStep.collectAsState()
    val scrollState = rememberScrollState()
    val saveCloudCrops by classificationViewModel.saveCloudCrops.collectAsState()
    val verboseLogging by classificationViewModel.verboseLogging.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Health Monitor state
    val healthMonitorState by viewModel.healthMonitorState.collectAsState()
    val healthMonitorConfig by viewModel.healthMonitorConfig.collectAsState()
    val healthMonitorWorkState by viewModel.healthMonitorWorkState.collectAsState()

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
            // Diagnostics description (DEV-only)
            if (FeatureFlags.isDevBuild) {
                DiagnosticsDescriptionCard()
            }

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

            // Preflight Diagnostics Section
            PreflightDiagnosticsSection(
                state = preflightState,
                onRefresh = { viewModel.refreshPreflight() },
                onClearCache = { viewModel.clearPreflightCache() },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Background Health Monitor Section
            HealthMonitorSection(
                monitorState = healthMonitorState,
                monitorConfig = healthMonitorConfig,
                workState = healthMonitorWorkState,
                effectiveBaseUrl = viewModel.getHealthMonitorEffectiveBaseUrl(),
                onEnabledChange = { viewModel.setHealthMonitorEnabled(it) },
                onNotifyRecoveryChange = { viewModel.setHealthMonitorNotifyOnRecovery(it) },
                onBaseUrlChange = { viewModel.setHealthMonitorBaseUrl(it) },
                onRunNow = { viewModel.runHealthCheckNow() },
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

            // In DEV builds, Developer Mode is always ON and cannot be toggled
            // Only show the toggle in non-DEV builds (shouldn't normally be reachable)
            if (!FeatureFlags.isDevBuild) {
                SettingSwitchRow(
                    title = "Developer Mode",
                    subtitle = "Unlock all features for testing",
                    icon = Icons.Default.BugReport,
                    checked = isDeveloperMode,
                    onCheckedChange = { viewModel.setDeveloperMode(it) },
                )
            } else {
                // DEV flavor: Show static indicator that dev mode is always on
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Column {
                        Text(
                            text = "Developer Mode",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Always enabled in DEV builds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

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

            // Overlay Accuracy Filter (stepped slider)
            OverlayAccuracySliderRow(
                currentStep = overlayAccuracyStep,
                onStepChange = { viewModel.setOverlayAccuracyStep(it) },
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "System Health",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.weight(1f))

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

// ==================== Preflight Diagnostics Section ====================

/**
 * Preflight diagnostics section showing current preflight status, latency, and cache info.
 */
@Composable
private fun PreflightDiagnosticsSection(
    state: PreflightResult,
    onRefresh: () -> Unit,
    onClearCache: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        // Header with actions
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
                    Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Preflight Health Check",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Clear cache button
                FilledTonalIconButton(
                    onClick = onClearCache,
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear cache")
                }

                // Refresh button
                FilledTonalIconButton(
                    onClick = onRefresh,
                    enabled = state.status != PreflightStatus.CHECKING,
                ) {
                    if (state.status == PreflightStatus.CHECKING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh preflight")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Status badge
        PreflightStatusBadge(status = state.status)

        Spacer(modifier = Modifier.height(16.dp))

        // Details card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Status
                PreflightDetailRow(
                    label = "Status",
                    value = state.status.name,
                    valueColor = getPreflightStatusColor(state.status),
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Latency
                PreflightDetailRow(
                    label = "Latency",
                    value = if (state.latencyMs > 0) "${state.latencyMs}ms" else "N/A",
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Checked at
                PreflightDetailRow(
                    label = "Last Checked",
                    value = if (state.checkedAt > 0) formatTimestamp(state.checkedAt) else "Never",
                )

                // Cache age
                if (state.checkedAt > 0) {
                    val cacheAge = (System.currentTimeMillis() - state.checkedAt) / 1000
                    PreflightDetailRow(
                        label = "Cache Age",
                        value = "${cacheAge}s ago",
                    )
                }

                // Reason code (if any)
                state.reasonCode?.let { reason ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    PreflightDetailRow(
                        label = "Reason",
                        value = reason,
                        valueColor = MaterialTheme.colorScheme.error,
                    )
                }

                // Retry after (if rate limited)
                state.retryAfterSeconds?.let { retryAfter ->
                    PreflightDetailRow(
                        label = "Retry After",
                        value = "${retryAfter}s",
                        valueColor = MaterialTheme.colorScheme.tertiary,
                    )
                }

                // Correlation ID (if any)
                state.correlationId?.let { correlationId ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    PreflightDetailRow(
                        label = "Correlation ID",
                        value = correlationId,
                        isMono = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreflightStatusBadge(status: PreflightStatus) {
    val (color, text, icon) = when (status) {
        PreflightStatus.AVAILABLE -> Triple(
            Color(0xFF4CAF50),
            "Available",
            Icons.Default.CheckCircle,
        )
        PreflightStatus.CHECKING -> Triple(
            Color(0xFF2196F3),
            "Checking...",
            Icons.Default.Sync,
        )
        PreflightStatus.TEMPORARILY_UNAVAILABLE -> Triple(
            Color(0xFFFF9800),
            "Temporarily Unavailable",
            Icons.Default.Warning,
        )
        PreflightStatus.OFFLINE -> Triple(
            Color(0xFFF44336),
            "Offline",
            Icons.Default.CloudOff,
        )
        PreflightStatus.RATE_LIMITED -> Triple(
            Color(0xFFFF9800),
            "Rate Limited",
            Icons.Default.Timer,
        )
        PreflightStatus.UNAUTHORIZED -> Triple(
            Color(0xFFF44336),
            "Unauthorized",
            Icons.Default.Lock,
        )
        PreflightStatus.NOT_CONFIGURED -> Triple(
            Color(0xFF9E9E9E),
            "Not Configured",
            Icons.Default.Settings,
        )
        PreflightStatus.ENDPOINT_NOT_FOUND -> Triple(
            Color(0xFFF44336),
            "Endpoint Not Found (check base URL / tunnel route)",
            Icons.Default.LinkOff,
        )
        PreflightStatus.UNKNOWN -> Triple(
            Color(0xFF9E9E9E),
            "Unknown",
            Icons.Default.Help,
        )
        PreflightStatus.CLIENT_ERROR -> Triple(
            Color(0xFFFF9800),
            "Client Error (preflight schema mismatch)",
            Icons.Default.Info,
        )
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
    }
}

@Composable
private fun PreflightDetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    isMono: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (isMono) FontFamily.Monospace else null,
            color = valueColor,
        )
    }
}

@Composable
private fun getPreflightStatusColor(status: PreflightStatus): Color {
    return when (status) {
        PreflightStatus.AVAILABLE -> Color(0xFF4CAF50)
        PreflightStatus.CHECKING -> Color(0xFF2196F3)
        PreflightStatus.TEMPORARILY_UNAVAILABLE -> Color(0xFFFF9800)
        PreflightStatus.OFFLINE -> Color(0xFFF44336)
        PreflightStatus.RATE_LIMITED -> Color(0xFFFF9800)
        PreflightStatus.UNAUTHORIZED -> Color(0xFFF44336)
        PreflightStatus.NOT_CONFIGURED -> Color(0xFF9E9E9E)
        PreflightStatus.ENDPOINT_NOT_FOUND -> Color(0xFFF44336)
        PreflightStatus.CLIENT_ERROR -> Color(0xFFFF9800) // Orange - warning but allows chat
        PreflightStatus.UNKNOWN -> Color(0xFF9E9E9E)
    }
}

// ==================== Overlay Accuracy Filter Slider ====================

/**
 * Stepped slider row for filtering bboxes by confidence threshold.
 *
 * This is a debug-only control that filters which bounding boxes are
 * shown on the camera overlay based on confidence. Does NOT affect
 * detection, tracking, or aggregation logic - only visibility.
 *
 * @param currentStep Current step index (0 = All, higher = more filtering)
 * @param onStepChange Callback when user changes the step
 */
@Composable
private fun OverlayAccuracySliderRow(
    currentStep: Int,
    onStepChange: (Int) -> Unit,
) {
    val stepCount = ConfidenceTiers.stepCount
    val currentTier = ConfidenceTiers.getTier(currentStep)
    val displayText = ConfidenceTiers.getDisplayText(currentStep)

    // Slider value state (convert to/from Float for Slider component)
    var sliderValue by remember(currentStep) { mutableStateOf(currentStep.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Header row with icon and title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.FilterList,
                contentDescription = "Overlay accuracy filter",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column {
                Text(
                    text = "Aggregation Accuracy (Overlay Filter)",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Slider with stepped values
        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                sliderValue = newValue
            },
            onValueChangeFinished = {
                // Snap to integer and notify
                val snappedStep = sliderValue.toInt().coerceIn(0, stepCount - 1)
                sliderValue = snappedStep.toFloat()
                if (snappedStep != currentStep) {
                    onStepChange(snappedStep)
                }
            },
            valueRange = 0f..(stepCount - 1).toFloat(),
            steps = stepCount - 2, // steps parameter is intermediate steps, so (count - 2)
            modifier = Modifier.fillMaxWidth(),
        )

        // Step labels row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ConfidenceTiers.tiers.forEachIndexed { index, tier ->
                val isSelected = index == currentStep
                Text(
                    text = tier.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Helper text
        Text(
            text = "Filters which bboxes are drawn on camera overlay. Does not affect detection or aggregation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ==================== Health Monitor Section ====================

/**
 * Background health monitoring section for DEV builds.
 * Configures periodic backend health checks with local notifications.
 */
@Composable
private fun HealthMonitorSection(
    monitorState: DevHealthMonitorStateStore.MonitorState,
    monitorConfig: DevHealthMonitorStateStore.MonitorConfig,
    workState: DevHealthMonitorScheduler.WorkState,
    effectiveBaseUrl: String,
    onEnabledChange: (Boolean) -> Unit,
    onNotifyRecoveryChange: (Boolean) -> Unit,
    onBaseUrlChange: (String?) -> Unit,
    onRunNow: () -> Unit,
) {
    var baseUrlInput by remember(monitorConfig.baseUrlOverride) {
        mutableStateOf(monitorConfig.baseUrlOverride ?: "")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        // Header
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
                    Icons.Default.MonitorHeart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Background Health Monitor",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Status badge
        HealthMonitorStatusBadge(
            isEnabled = monitorConfig.enabled,
            workState = workState,
            lastStatus = monitorState.lastStatus,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Settings card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Enable toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable monitoring",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Check backend every 15 minutes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = monitorConfig.enabled,
                        onCheckedChange = onEnabledChange,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Notify on recovery toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notify on recovery",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Send notification when backend recovers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = monitorConfig.notifyOnRecovery,
                        onCheckedChange = onNotifyRecoveryChange,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Base URL input
                Text(
                    text = "Base URL Override",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Leave empty to use default: $effectiveBaseUrl",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://your-backend.example.com") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                if (baseUrlInput != (monitorConfig.baseUrlOverride ?: "")) {
                    TextButton(
                        onClick = {
                            onBaseUrlChange(baseUrlInput.takeIf { it.isNotBlank() })
                        },
                    ) {
                        Text("Save URL")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Last check result
                Text(
                    text = "Last Check",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (monitorState.hasEverRun) {
                    val statusColor = when (monitorState.lastStatus) {
                        MonitorHealthStatus.OK -> Color(0xFF4CAF50)
                        MonitorHealthStatus.FAIL -> Color(0xFFF44336)
                        null -> Color(0xFF9E9E9E)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor),
                        )
                        Text(
                            text = when (monitorState.lastStatus) {
                                MonitorHealthStatus.OK -> "OK"
                                MonitorHealthStatus.FAIL -> "FAIL"
                                null -> "Unknown"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = statusColor,
                        )
                        monitorState.lastCheckedAt?.let { ts ->
                            Text(
                                text = "at ${formatTimestamp(ts)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    monitorState.lastFailureSummary?.let { summary ->
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF44336),
                            modifier = Modifier.padding(start = 18.dp, top = 4.dp),
                        )
                    }
                } else {
                    Text(
                        text = "Never run",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Run now button
                Button(
                    onClick = onRunNow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Run Now")
                }
            }
        }
    }
}

/**
 * Status badge showing current health monitor state.
 */
@Composable
private fun HealthMonitorStatusBadge(
    isEnabled: Boolean,
    workState: DevHealthMonitorScheduler.WorkState,
    lastStatus: MonitorHealthStatus?,
) {
    val (color, text, icon) = when {
        !isEnabled -> Triple(
            Color(0xFF9E9E9E),
            "Disabled",
            Icons.Default.PauseCircle,
        )
        workState == DevHealthMonitorScheduler.WorkState.Running -> Triple(
            Color(0xFF2196F3),
            "Running...",
            Icons.Default.Sync,
        )
        lastStatus == MonitorHealthStatus.OK -> Triple(
            Color(0xFF4CAF50),
            "Enabled - Last check OK",
            Icons.Default.CheckCircle,
        )
        lastStatus == MonitorHealthStatus.FAIL -> Triple(
            Color(0xFFF44336),
            "Enabled - Last check FAILED",
            Icons.Default.Error,
        )
        else -> Triple(
            Color(0xFF2196F3),
            "Enabled - Waiting for first check",
            Icons.Default.Schedule,
        )
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
    }
}

// ==================== Diagnostics Description (DEV-only) ====================

/**
 * Informational card explaining what the diagnostics sections are for.
 * Only shown in DEV builds to help developers understand the diagnostic tools.
 */
@Composable
private fun DiagnosticsDescriptionCard() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column {
                Text(
                    text = "Diagnostics & Checks",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Diagnostics & checks help verify connectivity to your backend services " +
                        "(health, config, preflight, assistant) and alert you when something breaks. " +
                        "Use them while testing to quickly spot disruptions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
