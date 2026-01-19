package com.scanium.app.ui.settings.developer

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.scanium.app.diagnostics.AppConfigSnapshot
import com.scanium.app.diagnostics.CapabilityStatus
import com.scanium.app.diagnostics.HealthStatus
import com.scanium.app.diagnostics.NetworkStatus
import com.scanium.app.diagnostics.NetworkTransport
import com.scanium.app.diagnostics.PermissionStatus

@Composable
fun SystemHealthSection(
    state: SystemHealthSectionState,
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
                text = stringResource(R.string.settings_dev_system_health_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.weight(1f))

            // Refresh button
            FilledTonalIconButton(
                onClick = onRefresh,
                enabled = !state.diagnosticsState.isRefreshing,
            ) {
                if (state.diagnosticsState.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.settings_dev_system_health_refresh_cd),
                    )
                }
            }

            // Copy button
            FilledTonalIconButton(onClick = onCopyDiagnostics) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.settings_dev_system_health_copy_diagnostics_cd),
                )
            }

            // Auto-refresh toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_dev_system_health_auto_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Switch(
                    checked = state.autoRefreshEnabled,
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
                    name = stringResource(R.string.settings_dev_system_health_backend_label),
                    status = state.diagnosticsState.backendHealth.status,
                    detail = state.diagnosticsState.backendHealth.detail,
                    latencyMs = state.diagnosticsState.backendHealth.latencyMs,
                    lastChecked = state.diagnosticsState.backendHealth.lastCheckedFormatted,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Network Status
                NetworkStatusRow(networkStatus = state.diagnosticsState.networkStatus)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Permissions
                Text(
                    text = stringResource(R.string.settings_dev_system_health_permissions_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                state.diagnosticsState.permissions.forEach { perm ->
                    PermissionRow(permission = perm)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Capabilities
                Text(
                    text = stringResource(R.string.settings_dev_system_health_capabilities_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                state.diagnosticsState.capabilities.forEach { cap ->
                    CapabilityRow(capability = cap)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // App Config
                state.diagnosticsState.appConfig?.let { config ->
                    AppConfigSection(
                        config = config,
                        onResetBaseUrl =
                            if (config.isBaseUrlOverridden) {
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
                        text = stringResource(R.string.settings_dev_system_health_network_label),
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
                            if (networkStatus.isMetered) {
                                append(stringResource(R.string.settings_dev_system_health_metered_suffix))
                            }
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text =
                if (networkStatus.isConnected) {
                    stringResource(R.string.settings_dev_system_health_connected)
                } else {
                    stringResource(R.string.settings_dev_system_health_not_connected)
                },
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
                text =
                    if (permission.isGranted) {
                        stringResource(R.string.settings_dev_system_health_permission_granted)
                    } else {
                        stringResource(R.string.settings_dev_system_health_permission_not_granted)
                    },
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
                        "Text-to-Speech" -> Icons.Filled.VolumeUp
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
            text = stringResource(R.string.settings_dev_system_health_app_config_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        ConfigRow(
            stringResource(R.string.settings_dev_system_health_config_version_label),
            "${config.versionName} (${config.versionCode})",
        )
        ConfigRow(stringResource(R.string.settings_dev_system_health_config_build_label), config.buildType)
        ConfigRow(stringResource(R.string.settings_dev_system_health_config_device_label), config.deviceModel)
        ConfigRow(
            stringResource(R.string.settings_dev_system_health_config_android_label),
            "${config.androidVersion} (SDK ${config.sdkInt})",
        )

        // Base URL with override indicator
        val baseUrlLabel =
            if (config.isBaseUrlOverridden) {
                stringResource(R.string.settings_dev_system_health_config_base_url_override_label)
            } else {
                stringResource(R.string.settings_dev_system_health_config_base_url_label)
            }
        ConfigRow(
            label = baseUrlLabel,
            value = config.baseUrl,
            isWarning = config.hasBaseUrlWarning,
        )

        // Show BuildConfig URL if overridden
        if (config.isBaseUrlOverridden) {
            ConfigRow(
                label = stringResource(R.string.settings_dev_system_health_config_buildconfig_url_label),
                value = config.buildConfigBaseUrl,
                isSecondary = true,
            )

            // Reset button
            if (onResetBaseUrl != null) {
                TextButton(
                    onClick = onResetBaseUrl,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(stringResource(R.string.settings_dev_system_health_config_reset_buildconfig))
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
            color =
                when {
                    isWarning -> MaterialTheme.colorScheme.error
                    isSecondary -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color =
                when {
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
                HealthStatus.HEALTHY -> Color(0xFF4CAF50)

                // Green
                HealthStatus.DEGRADED -> Color(0xFFFF9800)

                // Orange
                HealthStatus.DOWN -> Color(0xFFF44336)

                // Red
                HealthStatus.UNKNOWN -> Color(0xFF9E9E9E) // Gray
            },
        label = "status_color",
    )

    val text =
        when (status) {
            HealthStatus.HEALTHY -> stringResource(R.string.settings_dev_system_health_status_healthy)
            HealthStatus.DEGRADED -> stringResource(R.string.settings_dev_system_health_status_degraded)
            HealthStatus.DOWN -> stringResource(R.string.settings_dev_system_health_status_down)
            HealthStatus.UNKNOWN -> stringResource(R.string.settings_dev_system_health_status_unknown)
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
