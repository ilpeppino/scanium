package com.scanium.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.scanium.app.R
import com.scanium.app.camera.CameraViewModel
import com.scanium.app.camera.CaptureResolution
import com.scanium.app.config.FeatureFlags
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.settings.ClassificationModeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCameraScreen(
    settingsViewModel: SettingsViewModel,
    classificationViewModel: ClassificationModeViewModel,
    itemsViewModel: ItemsViewModel,
    cameraViewModel: CameraViewModel,
    onNavigateBack: () -> Unit,
) {
    val captureResolution by cameraViewModel.captureResolution.collectAsState()
    val classificationMode by classificationViewModel.classificationMode.collectAsState()
    val similarityThreshold by itemsViewModel.similarityThreshold.collectAsState()
    val showDetectionBoxes by settingsViewModel.showDetectionBoxes.collectAsState()

    // Filter resolution options: HIGH only available in dev builds
    val availableResolutions =
        CaptureResolution.values().filter { resolution ->
            resolution != CaptureResolution.HIGH || FeatureFlags.allowHighResolution
        }
    // Resolution options using SettingOption
    val captureOptions =
        availableResolutions.map { resolution ->
            SettingOption(
                value = resolution,
                label =
                    when (resolution) {
                        CaptureResolution.LOW -> stringResource(R.string.settings_resolution_low)
                        CaptureResolution.NORMAL -> stringResource(R.string.settings_resolution_normal)
                        CaptureResolution.HIGH -> stringResource(R.string.settings_resolution_high)
                    },
                description =
                    when (resolution) {
                        CaptureResolution.LOW -> stringResource(R.string.settings_resolution_low_desc)
                        CaptureResolution.NORMAL -> stringResource(R.string.settings_resolution_normal_desc)
                        CaptureResolution.HIGH -> stringResource(R.string.settings_resolution_high_desc)
                    },
                isRecommended = resolution == CaptureResolution.NORMAL,
            )
        }

    // Classification mode options using SettingOption
    val classificationOptions =
        listOf(
            SettingOption(
                value = ClassificationMode.CLOUD,
                label = stringResource(R.string.settings_classification_cloud),
                description = stringResource(R.string.settings_classification_cloud_desc),
                isRecommended = true,
            ),
            SettingOption(
                value = ClassificationMode.ON_DEVICE,
                label = stringResource(R.string.settings_classification_on_device),
                description = stringResource(R.string.settings_classification_on_device_desc),
            ),
        )

    // Accuracy options using SettingOption
    val accuracyOptions =
        listOf(
            SettingOption(
                value = AccuracyLevel.LOW,
                label = stringResource(R.string.settings_accuracy_low),
                description = stringResource(R.string.settings_accuracy_low_desc),
            ),
            SettingOption(
                value = AccuracyLevel.MEDIUM,
                label = stringResource(R.string.settings_accuracy_medium),
                description = stringResource(R.string.settings_accuracy_medium_desc),
                isRecommended = true,
            ),
            SettingOption(
                value = AccuracyLevel.HIGH,
                label = stringResource(R.string.settings_accuracy_high),
                description = stringResource(R.string.settings_accuracy_high_desc),
            ),
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_camera_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
        ) {
            // Section: Capture behavior
            SettingsSectionHeader(title = stringResource(R.string.settings_section_capture))

            // 1) Image resolution picker with bottom sheet
            ValuePickerSettingRow(
                title = stringResource(R.string.settings_resolution_title),
                subtitle = stringResource(R.string.settings_resolution_subtitle),
                icon = Icons.Filled.HighQuality,
                currentValue = captureResolution,
                options = captureOptions,
                onValueSelected = cameraViewModel::updateCaptureResolution,
            )

            // Section: Detection & accuracy
            SettingsSectionHeader(title = stringResource(R.string.settings_section_scanning))

            // 2) Classification mode picker with bottom sheet
            ValuePickerSettingRow(
                title = stringResource(R.string.settings_classification_title),
                subtitle = stringResource(R.string.settings_classification_subtitle),
                icon = Icons.Filled.Cloud,
                currentValue = classificationMode,
                options = classificationOptions,
                onValueSelected = classificationViewModel::updateMode,
            )

            // 3) Aggregation accuracy picker with bottom sheet
            val currentAccuracy = AccuracyLevel.fromThreshold(similarityThreshold)
            ValuePickerSettingRow(
                title = stringResource(R.string.settings_accuracy_title),
                subtitle = stringResource(R.string.settings_accuracy_subtitle),
                icon = Icons.Filled.Tune,
                currentValue = currentAccuracy,
                options = accuracyOptions,
                onValueSelected = { level ->
                    itemsViewModel.updateSimilarityThreshold(level.threshold)
                },
            )

            // 4) Live bounding boxes toggle
            SettingSwitchRow(
                title = stringResource(R.string.settings_detection_boxes_title),
                subtitle = stringResource(R.string.settings_detection_boxes_subtitle),
                icon = Icons.Filled.CropFree,
                checked = showDetectionBoxes,
                onCheckedChange = settingsViewModel::setShowDetectionBoxes,
            )
        }
    }
}

private enum class AccuracyLevel(
    val threshold: Float,
) {
    LOW(0.45f),
    MEDIUM(0.55f),
    HIGH(0.7f),
    ;

    companion object {
        fun fromThreshold(value: Float): AccuracyLevel = values().minBy { kotlin.math.abs(it.threshold - value) }
    }
}
