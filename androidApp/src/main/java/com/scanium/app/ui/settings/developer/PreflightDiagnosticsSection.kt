package com.scanium.app.ui.settings.developer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanium.app.R
import com.scanium.app.selling.assistant.PreflightResult
import com.scanium.app.selling.assistant.PreflightStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Preflight diagnostics section showing current preflight status, latency, and cache info.
 */
@Composable
fun PreflightDiagnosticsSection(
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
                    text = stringResource(R.string.settings_dev_preflight_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Clear cache button
                FilledTonalIconButton(
                    onClick = onClearCache,
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = stringResource(R.string.settings_dev_preflight_clear_cache_cd),
                    )
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
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.settings_dev_preflight_refresh_cd),
                        )
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
                    label = stringResource(R.string.settings_dev_preflight_label_status),
                    value = state.status.name,
                    valueColor = getPreflightStatusColor(state.status),
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Latency
                PreflightDetailRow(
                    label = stringResource(R.string.settings_dev_preflight_label_latency),
                    value =
                        if (state.latencyMs > 0) {
                            stringResource(R.string.settings_dev_preflight_latency_value, state.latencyMs)
                        } else {
                            stringResource(R.string.settings_dev_preflight_value_na)
                        },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Checked at
                PreflightDetailRow(
                    label = stringResource(R.string.settings_dev_preflight_label_last_checked),
                    value =
                        if (state.checkedAt > 0) {
                            formatTimestamp(state.checkedAt)
                        } else {
                            stringResource(R.string.settings_dev_preflight_value_never)
                        },
                )

                // Cache age
                if (state.checkedAt > 0) {
                    val cacheAge = (System.currentTimeMillis() - state.checkedAt) / 1000
                    PreflightDetailRow(
                        label = stringResource(R.string.settings_dev_preflight_label_cache_age),
                        value = stringResource(R.string.settings_dev_preflight_cache_age_value, cacheAge),
                    )
                }

                // Reason code (if any)
                state.reasonCode?.let { reason ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    PreflightDetailRow(
                        label = stringResource(R.string.settings_dev_preflight_label_reason),
                        value = reason,
                        valueColor = MaterialTheme.colorScheme.error,
                    )
                }

                // Retry after (if rate limited)
                state.retryAfterSeconds?.let { retryAfter ->
                    PreflightDetailRow(
                        label = stringResource(R.string.settings_dev_preflight_label_retry_after),
                        value = stringResource(R.string.settings_dev_preflight_retry_after_value, retryAfter),
                        valueColor = MaterialTheme.colorScheme.tertiary,
                    )
                }

                // Correlation ID (if any)
                state.correlationId?.let { correlationId ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    PreflightDetailRow(
                        label = stringResource(R.string.settings_dev_preflight_label_correlation_id),
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
            stringResource(R.string.settings_dev_preflight_status_available),
            Icons.Default.CheckCircle,
        )
        PreflightStatus.CHECKING -> Triple(
            Color(0xFF2196F3),
            stringResource(R.string.settings_dev_preflight_status_checking),
            Icons.Default.Sync,
        )
        PreflightStatus.TEMPORARILY_UNAVAILABLE -> Triple(
            Color(0xFFFF9800),
            stringResource(R.string.settings_dev_preflight_status_temporarily_unavailable),
            Icons.Default.Warning,
        )
        PreflightStatus.OFFLINE -> Triple(
            Color(0xFFF44336),
            stringResource(R.string.settings_dev_preflight_status_offline),
            Icons.Default.CloudOff,
        )
        PreflightStatus.RATE_LIMITED -> Triple(
            Color(0xFFFF9800),
            stringResource(R.string.settings_dev_preflight_status_rate_limited),
            Icons.Default.Timer,
        )
        PreflightStatus.UNAUTHORIZED -> Triple(
            Color(0xFFF44336),
            stringResource(R.string.settings_dev_preflight_status_unauthorized),
            Icons.Default.Lock,
        )
        PreflightStatus.NOT_CONFIGURED -> Triple(
            Color(0xFF9E9E9E),
            stringResource(R.string.settings_dev_preflight_status_not_configured),
            Icons.Default.Settings,
        )
        PreflightStatus.ENDPOINT_NOT_FOUND -> Triple(
            Color(0xFFF44336),
            stringResource(R.string.settings_dev_preflight_status_endpoint_not_found),
            Icons.Default.LinkOff,
        )
        PreflightStatus.UNKNOWN -> Triple(
            Color(0xFF9E9E9E),
            stringResource(R.string.settings_dev_preflight_status_unknown),
            Icons.Filled.Help,
        )
        PreflightStatus.CLIENT_ERROR -> Triple(
            Color(0xFFFF9800),
            stringResource(R.string.settings_dev_preflight_status_client_error),
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

/**
 * Format timestamp to readable time.
 */
private fun formatTimestamp(timestamp: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
