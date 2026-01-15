package com.scanium.app.ui.settings.developer

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.scanium.app.monitoring.DevHealthMonitorScheduler
import com.scanium.app.monitoring.DevHealthMonitorStateStore
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
            isEnabled = state.monitorConfig.enabled,
            workState = state.workState,
            lastStatus = state.monitorState.lastStatus,
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
                        checked = state.monitorConfig.notifyOnRecovery,
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
                    text = "Leave empty to use default: ${state.effectiveBaseUrl}",
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
                if (baseUrlInput != (state.monitorConfig.baseUrlOverride ?: "")) {
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
                if (state.monitorState.hasEverRun) {
                    val statusColor = when (state.monitorState.lastStatus) {
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
                            text = when (state.monitorState.lastStatus) {
                                MonitorHealthStatus.OK -> "OK"
                                MonitorHealthStatus.FAIL -> "FAIL"
                                null -> "Unknown"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = statusColor,
                        )
                        state.monitorState.lastCheckedAt?.let { ts ->
                            Text(
                                text = "at ${formatTimestamp(ts)}",
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
                        text = "Never run",
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
                        text = "Notification Status",
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
                            text = if (notificationsEnabled) "Enabled" else "Disabled - check permission above",
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

/**
 * Format timestamp to readable time.
 */
private fun formatTimestamp(timestamp: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
