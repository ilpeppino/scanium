package com.scanium.app.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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

/**
 * Developer Options screen with System Health diagnostics.
 * Only visible in debug builds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperOptionsScreen(
    viewModel: DeveloperOptionsViewModel,
    onNavigateBack: () -> Unit
) {
    val diagnosticsState by viewModel.diagnosticsState.collectAsState()
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()
    val forceFtueTour by viewModel.forceFtueTour.collectAsState()
    val autoRefreshEnabled by viewModel.autoRefreshEnabled.collectAsState()
    val copyResult by viewModel.copyResult.collectAsState()
    val scrollState = rememberScrollState()
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

            // Developer Settings Section
            SettingsSectionTitle("Developer Settings")

            SettingsSwitchItem(
                title = "Developer Mode",
                subtitle = "Unlock all features for testing",
                icon = Icons.Default.BugReport,
                checked = isDeveloperMode,
                onCheckedChange = { viewModel.setDeveloperMode(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Testing Section
            SettingsSectionTitle("Testing & Debug")

            SettingsItem(
                title = "Test Crash Reporting",
                subtitle = "Send test event to Sentry (handled exception)",
                icon = Icons.Default.BugReport,
                onClick = { viewModel.triggerCrashTest(throwCrash = false) }
            )

            SettingsItem(
                title = "Test Diagnostics Bundle",
                subtitle = "Capture exception with diagnostics.json attachment",
                icon = Icons.Default.BugReport,
                onClick = { viewModel.triggerDiagnosticsTest() }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // First-Time Experience Section
            SettingsSectionTitle("First-Time Experience")

            SettingsSwitchItem(
                title = "Force First-Time Tour",
                subtitle = "Always show tour on app launch (debug only)",
                icon = Icons.Default.Info,
                checked = forceFtueTour,
                onCheckedChange = { viewModel.setForceFtueTour(it) }
            )

            SettingsItem(
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
