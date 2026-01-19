package com.scanium.app.ui.settings.developer

import android.os.Build
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.scanium.app.R
import com.scanium.app.monitoring.DevHealthMonitorScheduler
import com.scanium.app.monitoring.MonitorHealthStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Background health monitoring section for DEV builds.
 * Configures periodic backend health checks with local notifications.
 */
@Composable
fun HealthMonitorSection(
    state: HealthMonitorSectionState,
    onEnabledChange: (Boolean) -> Unit,
    onNotifyRecoveryChange: (Boolean) -> Unit,
    onBaseUrlChange: (String?) -> Unit,
    onRunNow: () -> Unit,
) {
    var baseUrlInput by remember(state.monitorConfig.baseUrlOverride) {
        mutableStateOf(state.monitorConfig.baseUrlOverride ?: "")
    }

    Column(
        modifier =
            Modifier
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
                    text = stringResource(R.string.settings_dev_health_monitor_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Status badge
        HealthMonitorStatusBadge(
            isEnabled = state.monitorConfig.enabled,
            workState = state.workState,
            lastStatus = state.monitorState.lastStatus,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Settings card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
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
                            text = stringResource(R.string.settings_dev_health_monitor_enable_title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.settings_dev_health_monitor_enable_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.monitorConfig.enabled,
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
                            text = stringResource(R.string.settings_dev_health_monitor_notify_recovery_title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.settings_dev_health_monitor_notify_recovery_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.monitorConfig.notifyOnRecovery,
                        onCheckedChange = onNotifyRecoveryChange,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Base URL input
                Text(
                    text = stringResource(R.string.settings_dev_health_monitor_base_url_override_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text =
                        stringResource(
                            R.string.settings_dev_health_monitor_base_url_override_subtitle,
                            state.effectiveBaseUrl,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.settings_dev_health_monitor_base_url_placeholder)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                if (baseUrlInput != (state.monitorConfig.baseUrlOverride ?: "")) {
                    TextButton(
                        onClick = {
                            onBaseUrlChange(baseUrlInput.takeIf { it.isNotBlank() })
                        },
                    ) {
                        Text(stringResource(R.string.settings_dev_health_monitor_save_url))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Last check result
                Text(
                    text = stringResource(R.string.settings_dev_health_monitor_last_check_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (state.monitorState.hasEverRun) {
                    val statusColor =
                        when (state.monitorState.lastStatus) {
                            MonitorHealthStatus.OK -> Color(0xFF4CAF50)
                            MonitorHealthStatus.FAIL -> Color(0xFFF44336)
                            null -> Color(0xFF9E9E9E)
                        }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(statusColor),
                        )
                        Text(
                            text =
                                when (state.monitorState.lastStatus) {
                                    MonitorHealthStatus.OK -> {
                                        stringResource(R.string.settings_dev_health_monitor_status_ok)
                                    }

                                    MonitorHealthStatus.FAIL -> {
                                        stringResource(R.string.settings_dev_health_monitor_status_fail)
                                    }

                                    null -> {
                                        stringResource(R.string.settings_dev_health_monitor_status_unknown)
                                    }
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = statusColor,
                        )
                        state.monitorState.lastCheckedAt?.let { ts ->
                            Text(
                                text =
                                    stringResource(
                                        R.string.settings_dev_health_monitor_last_check_at,
                                        formatTimestamp(ts),
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    state.monitorState.lastFailureSummary?.let { summary ->
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF44336),
                            modifier = Modifier.padding(start = 18.dp, top = 4.dp),
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.settings_dev_health_monitor_last_check_never),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Notification diagnostics (Android 13+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    val context = LocalContext.current
                    val notificationManager = NotificationManagerCompat.from(context)
                    val notificationsEnabled = notificationManager.areNotificationsEnabled()

                    Text(
                        text = stringResource(R.string.settings_dev_health_monitor_notification_status_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            if (notificationsEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = if (notificationsEnabled) Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text =
                                if (notificationsEnabled) {
                                    stringResource(R.string.settings_dev_health_monitor_notifications_enabled)
                                } else {
                                    stringResource(R.string.settings_dev_health_monitor_notifications_disabled)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (notificationsEnabled) Color(0xFF4CAF50) else Color(0xFFF44336),
                        )
                    }
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
                    Text(stringResource(R.string.settings_dev_health_monitor_run_now))
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
    val (color, text, icon) =
        when {
            !isEnabled -> {
                Triple(
                    Color(0xFF9E9E9E),
                    stringResource(R.string.settings_dev_health_monitor_status_disabled),
                    Icons.Default.PauseCircle,
                )
            }

            workState == DevHealthMonitorScheduler.WorkState.Running -> {
                Triple(
                    Color(0xFF2196F3),
                    stringResource(R.string.settings_dev_health_monitor_status_running),
                    Icons.Default.Sync,
                )
            }

            lastStatus == MonitorHealthStatus.OK -> {
                Triple(
                    Color(0xFF4CAF50),
                    stringResource(R.string.settings_dev_health_monitor_status_last_ok),
                    Icons.Default.CheckCircle,
                )
            }

            lastStatus == MonitorHealthStatus.FAIL -> {
                Triple(
                    Color(0xFFF44336),
                    stringResource(R.string.settings_dev_health_monitor_status_last_fail),
                    Icons.Default.Error,
                )
            }

            else -> {
                Triple(
                    Color(0xFF2196F3),
                    stringResource(R.string.settings_dev_health_monitor_status_waiting),
                    Icons.Default.Schedule,
                )
            }
        }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
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

/**
 * Format timestamp to readable time.
 */
private fun formatTimestamp(timestamp: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
