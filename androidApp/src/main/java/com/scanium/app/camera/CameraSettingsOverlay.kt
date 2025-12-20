package com.scanium.app.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scanium.app.BuildConfig
import com.scanium.app.ml.classification.ClassificationMode
import kotlin.math.abs

@Composable
fun CameraSettingsOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    similarityThreshold: Float,
    onThresholdChange: (Float) -> Unit,
    classificationMode: ClassificationMode,
    onProcessingModeChange: (ClassificationMode) -> Unit,
    captureResolution: CaptureResolution,
    onResolutionChange: (CaptureResolution) -> Unit,
    saveCloudCropsEnabled: Boolean,
    onSaveCloudCropsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(onClick = onDismiss)
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        ) {
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp)
            ) {
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .padding(vertical = 24.dp, horizontal = 16.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    ResolutionSettingsCard(
                        captureResolution = captureResolution,
                        onResolutionChange = onResolutionChange
                    )
                    AccuracySettingsCard(
                        similarityThreshold = similarityThreshold,
                        onThresholdChange = onThresholdChange
                    )
                    ProcessingSettingsCard(
                        classificationMode = classificationMode,
                        onProcessingModeChange = onProcessingModeChange,
                        saveCloudCropsEnabled = saveCloudCropsEnabled,
                        onSaveCloudCropsChange = onSaveCloudCropsChange
                    )
                }
            }
        }
    }
}

@Composable
private fun ResolutionSettingsCard(
    captureResolution: CaptureResolution,
    onResolutionChange: (CaptureResolution) -> Unit
) {
    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Image Quality",
                style = MaterialTheme.typography.titleMedium
            )
            Divider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Resolution",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Choose image quality for saved items. Higher quality creates larger files.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaptureResolution.values().forEach { resolution ->
                        ListItem(
                            headlineContent = { Text(resolution.displayName) },
                            supportingContent = {
                                Text(resolution.description)
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = resolution == captureResolution,
                                    onClick = { onResolutionChange(resolution) }
                                )
                            },
                            modifier = Modifier.clickable { onResolutionChange(resolution) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessingSettingsCard(
    classificationMode: ClassificationMode,
    onProcessingModeChange: (ClassificationMode) -> Unit,
    saveCloudCropsEnabled: Boolean,
    onSaveCloudCropsChange: (Boolean) -> Unit
) {
    val isCloudConfigured = BuildConfig.SCANIUM_API_BASE_URL.isNotBlank()
    val showConfigWarning = classificationMode == ClassificationMode.CLOUD && !isCloudConfigured

    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Processing",
                style = MaterialTheme.typography.titleMedium
            )
            Divider()

            ListItem(
                headlineContent = { Text("Processing mode") },
                supportingContent = {
                    val (modeLabel, modeDescription) = if (classificationMode == ClassificationMode.ON_DEVICE) {
                        "On-device" to "Fastest and private. Works offline."
                    } else {
                        "Cloud" to "Requires internet. Higher accuracy but slower."
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(modeLabel)
                        Text(
                            text = modeDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                leadingContent = {
                    val (icon, tint) = if (classificationMode == ClassificationMode.ON_DEVICE) {
                        Icons.Filled.PhoneAndroid to MaterialTheme.colorScheme.secondary
                    } else {
                        Icons.Filled.Cloud to MaterialTheme.colorScheme.primary
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint
                    )
                },
                trailingContent = {
                    Switch(
                        checked = classificationMode == ClassificationMode.CLOUD,
                        onCheckedChange = { isChecked ->
                            val newMode = if (isChecked) {
                                ClassificationMode.CLOUD
                            } else {
                                ClassificationMode.ON_DEVICE
                            }
                            onProcessingModeChange(newMode)
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Processing mode"
                        }
                    )
                }
            )

            // Configuration warning when cloud mode is enabled but not configured
            if (showConfigWarning) {
                Surface(
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Cloud classifier not configured",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Set SCANIUM_API_BASE_URL in local.properties to enable cloud classification. See docs/DEV_GUIDE.md for setup instructions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Debug: Save cloud crops to cache
            if (BuildConfig.DEBUG) {
                Divider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f, fill = true),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("Save cloud crops", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Writes outgoing classifier crops to app cache for debugging.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = saveCloudCropsEnabled,
                            onCheckedChange = onSaveCloudCropsChange
                        )
                    }
                    Text(
                        text = "Files stored under cache/classifier_crops (cleared on uninstall).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AccuracySettingsCard(
    similarityThreshold: Float,
    onThresholdChange: (Float) -> Unit
) {
    val accuracyOptions = AccuracyLevel.values()
    val selectedLevel = accuracyOptions.minBy { option ->
        abs(option.targetThreshold - similarityThreshold)
    }

    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Accuracy",
                style = MaterialTheme.typography.titleMedium
            )
            Divider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Item accuracy",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Choose how strict matching should be when saving detections.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accuracyOptions.forEach { option ->
                        ListItem(
                            headlineContent = { Text(option.label) },
                            supportingContent = {
                                Text(option.supportingText)
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = option == selectedLevel,
                                    onClick = { onThresholdChange(option.targetThreshold) }
                                )
                            }
                        )
                    }
                }

                Text(
                    text = "Current target: ${(selectedLevel.targetThreshold * 100).toInt()}% similarity",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Slider previously used 0f..1f range with 0.55f default (ItemsViewModel / AggregationPresets.REALTIME);
// map Low/Medium/High to representative targets on that same scale.
private enum class AccuracyLevel(val label: String, val targetThreshold: Float, val supportingText: String) {
    LOW(label = "Low", targetThreshold = 0.45f, supportingText = "Looser matching; saves more items."),
    MEDIUM(label = "Medium", targetThreshold = 0.55f, supportingText = "Balanced (matches previous default)."),
    HIGH(label = "High", targetThreshold = 0.7f, supportingText = "Stricter matching; keeps high-confidence items.");
}
