package com.scanium.app.ui.settings.developer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.scanium.app.diagnostics.AssistantDiagnosticsState
import com.scanium.app.diagnostics.AssistantReadiness
import com.scanium.app.diagnostics.BackendReachabilityStatus
import com.scanium.app.diagnostics.BackendStatusClassifier
import com.scanium.app.model.config.ConnectionTestResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Assistant / AI Diagnostics section showing assistant prerequisites and capabilities.
 * Developer-only, read-only diagnostics panel.
 */
@Composable
fun AssistantDiagnosticsSection(
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
                    text = stringResource(R.string.settings_dev_assistant_diagnostics_title),
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
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.settings_dev_assistant_recheck_cd),
                    )
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
                    label = stringResource(R.string.settings_dev_assistant_backend_reachability_label),
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
                            is ConnectionTestResult.Success ->
                                stringResource(R.string.settings_dev_assistant_backend_connected, result.httpStatus)
                            is ConnectionTestResult.Failure -> result.message
                            null ->
                                if (state.isChecking) {
                                    stringResource(R.string.settings_dev_assistant_checking)
                                } else {
                                    stringResource(R.string.settings_dev_assistant_not_checked)
                                }
                        },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Assistant Readiness (Prerequisites)
                AssistantDiagnosticRow(
                    icon = Icons.Default.CheckCircle,
                    label = stringResource(R.string.settings_dev_assistant_readiness_label),
                    status =
                        when {
                            state.prerequisiteState.allSatisfied -> DiagnosticStatus.OK
                            state.prerequisiteState.unsatisfiedCount > 0 -> DiagnosticStatus.WARNING
                            else -> DiagnosticStatus.UNKNOWN
                        },
                    detail =
                        if (state.prerequisiteState.allSatisfied) {
                            stringResource(
                                R.string.settings_dev_assistant_prerequisites_met,
                                state.prerequisiteState.prerequisites.size,
                            )
                        } else {
                            stringResource(
                                R.string.settings_dev_assistant_prerequisites_not_met,
                                state.prerequisiteState.unsatisfiedCount,
                            )
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
                    label = stringResource(R.string.settings_dev_assistant_network_state_label),
                    status = if (state.isNetworkConnected) DiagnosticStatus.OK else DiagnosticStatus.ERROR,
                    detail =
                        if (state.isNetworkConnected) {
                            stringResource(R.string.settings_dev_assistant_network_connected, state.networkType)
                        } else {
                            stringResource(R.string.settings_dev_assistant_network_not_connected)
                        },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Microphone Permission
                AssistantDiagnosticRow(
                    icon = Icons.Default.Mic,
                    label = stringResource(R.string.settings_dev_assistant_microphone_permission_label),
                    status = if (state.hasMicrophonePermission) DiagnosticStatus.OK else DiagnosticStatus.WARNING,
                    detail =
                        if (state.hasMicrophonePermission) {
                            stringResource(R.string.settings_dev_assistant_permission_granted)
                        } else {
                            stringResource(R.string.settings_dev_assistant_permission_not_granted)
                        },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Speech Recognition Availability
                AssistantDiagnosticRow(
                    icon = Icons.Default.RecordVoiceOver,
                    label = stringResource(R.string.settings_dev_assistant_speech_recognition_label),
                    status = if (state.isSpeechRecognitionAvailable) DiagnosticStatus.OK else DiagnosticStatus.ERROR,
                    detail =
                        if (state.isSpeechRecognitionAvailable) {
                            stringResource(R.string.settings_dev_assistant_speech_recognition_available)
                        } else {
                            stringResource(R.string.settings_dev_assistant_speech_recognition_unavailable)
                        },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Text-to-Speech Availability
                AssistantDiagnosticRow(
                    icon = Icons.Filled.VolumeUp,
                    label = stringResource(R.string.settings_dev_assistant_tts_label),
                    status = if (state.isTextToSpeechAvailable) DiagnosticStatus.OK else DiagnosticStatus.ERROR,
                    detail =
                        if (state.isTextToSpeechAvailable) {
                            if (state.isTtsReady) {
                                stringResource(R.string.settings_dev_assistant_tts_available_ready)
                            } else {
                                stringResource(R.string.settings_dev_assistant_tts_available)
                            }
                        } else {
                            stringResource(R.string.settings_dev_assistant_tts_not_available)
                        },
                )

                // Last checked timestamp
                if (state.lastChecked > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = stringResource(
                            R.string.settings_dev_assistant_last_checked,
                            formatTimestamp(state.lastChecked),
                        ),
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
                    stringResource(R.string.settings_dev_assistant_status_ready),
                    Icons.Default.CheckCircle,
                )
            AssistantReadiness.CHECKING ->
                Triple(
                    Color(0xFF2196F3),
                    stringResource(R.string.settings_dev_assistant_status_checking),
                    Icons.Default.Sync,
                )
            AssistantReadiness.NO_NETWORK ->
                Triple(
                    Color(0xFFF44336),
                    stringResource(R.string.settings_dev_assistant_status_no_network),
                    Icons.Default.WifiOff,
                )
            AssistantReadiness.BACKEND_UNREACHABLE ->
                Triple(
                    Color(0xFFF44336),
                    stringResource(R.string.settings_dev_assistant_status_backend_unreachable),
                    Icons.Default.CloudOff,
                )
            AssistantReadiness.BACKEND_UNAUTHORIZED ->
                Triple(
                    Color(0xFFFF9800),
                    stringResource(R.string.settings_dev_assistant_status_backend_unauthorized),
                    Icons.Default.Lock,
                )
            AssistantReadiness.BACKEND_SERVER_ERROR ->
                Triple(
                    Color(0xFFFF9800),
                    stringResource(R.string.settings_dev_assistant_status_backend_server_error),
                    Icons.Default.Error,
                )
            AssistantReadiness.BACKEND_NOT_FOUND ->
                Triple(
                    Color(0xFFFF9800),
                    stringResource(R.string.settings_dev_assistant_status_backend_not_found),
                    Icons.Default.SearchOff,
                )
            AssistantReadiness.BACKEND_NOT_CONFIGURED ->
                Triple(
                    Color(0xFF9E9E9E),
                    stringResource(R.string.settings_dev_assistant_status_backend_not_configured),
                    Icons.Default.Settings,
                )
            AssistantReadiness.PREREQUISITES_NOT_MET ->
                Triple(
                    Color(0xFFFF9800),
                    stringResource(R.string.settings_dev_assistant_status_prerequisites_not_met),
                    Icons.Default.Warning,
                )
            AssistantReadiness.UNKNOWN ->
                Triple(
                    Color(0xFF9E9E9E),
                    stringResource(R.string.settings_dev_assistant_status_unknown),
                    Icons.Filled.Help,
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
