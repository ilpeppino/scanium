package com.scanium.app.selling.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scanium.app.selling.data.MockEbayConfigManager
import com.scanium.app.selling.data.MockFailureMode

/**
 * Debug settings dialog for configuring mock eBay behavior.
 *
 * Allows testing different failure scenarios and network conditions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettingsDialog(onDismiss: () -> Unit) {
    val config by MockEbayConfigManager.config.collectAsState()

    var networkDelayEnabled by remember { mutableStateOf(config.simulateNetworkDelay) }
    var selectedFailureMode by remember { mutableStateOf(config.failureMode) }
    var failureRate by remember { mutableFloatStateOf(config.failureRate.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mock eBay Debug Settings") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Network delay toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Simulate Network Delay", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = networkDelayEnabled,
                        onCheckedChange = { networkDelayEnabled = it },
                    )
                }

                HorizontalDivider()

                // Failure mode selection
                Text("Failure Mode", style = MaterialTheme.typography.titleSmall)

                MockFailureMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        RadioButton(
                            selected = selectedFailureMode == mode,
                            onClick = { selectedFailureMode = mode },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = mode.name.replace('_', ' '),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = getFailureModeDescription(mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Failure rate slider (only shown if failure mode is not NONE)
                if (selectedFailureMode != MockFailureMode.NONE) {
                    HorizontalDivider()
                    Text("Failure Rate: ${(failureRate * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = failureRate,
                        onValueChange = { failureRate = it },
                        valueRange = 0f..1f,
                        steps = 9,
// 0%, 10%, 20%, ..., 100%
                    )
                    Text(
                        text =
                            when {
                                failureRate < 0.1f -> "Never fails"
                                failureRate < 0.3f -> "Rarely fails"
                                failureRate < 0.7f -> "Sometimes fails"
                                else -> "Often fails"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Apply settings
                MockEbayConfigManager.updateConfig(
                    config.copy(
                        simulateNetworkDelay = networkDelayEnabled,
                        failureMode = selectedFailureMode,
                        failureRate = if (selectedFailureMode == MockFailureMode.NONE) 0.0 else failureRate.toDouble(),
                    ),
                )
                onDismiss()
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                MockEbayConfigManager.resetToDefaults()
                onDismiss()
            }) {
                Text("Reset")
            }
        },
    )
}

private fun getFailureModeDescription(mode: MockFailureMode): String {
    return when (mode) {
        MockFailureMode.NONE -> "All requests succeed"
        MockFailureMode.NETWORK_TIMEOUT -> "Simulates network timeout errors"
        MockFailureMode.VALIDATION_ERROR -> "Simulates validation errors"
        MockFailureMode.IMAGE_TOO_SMALL -> "Simulates image quality errors"
        MockFailureMode.RANDOM -> "Random failures"
    }
}
