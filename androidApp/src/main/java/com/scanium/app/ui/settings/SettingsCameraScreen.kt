package com.scanium.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.scanium.app.R
import com.scanium.app.camera.CameraViewModel
import com.scanium.app.config.FeatureFlags
import com.scanium.app.camera.CaptureResolution
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.media.StorageHelper
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
    val context = LocalContext.current

    val autoSaveEnabled by settingsViewModel.autoSaveEnabled.collectAsState()
    val saveDirectoryUri by settingsViewModel.saveDirectoryUri.collectAsState()
    val captureResolution by cameraViewModel.captureResolution.collectAsState()
    val classificationMode by classificationViewModel.classificationMode.collectAsState()
    val lowDataMode by classificationViewModel.lowDataMode.collectAsState()
    val similarityThreshold by itemsViewModel.similarityThreshold.collectAsState()
    val showDetectionBoxes by settingsViewModel.showDetectionBoxes.collectAsState()
    val openItemListAfterScan by settingsViewModel.openItemListAfterScan.collectAsState()

    val dirPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            uri?.let {
                StorageHelper.takePersistablePermissions(context, it)
                settingsViewModel.setSaveDirectoryUri(it.toString())
            }
        }

    // Filter resolution options: HIGH only available in dev builds
    val availableResolutions = CaptureResolution.values().filter { resolution ->
        resolution != CaptureResolution.HIGH || FeatureFlags.allowHighResolution
    }
    val captureOptions =
        availableResolutions.map { resolution ->
            SegmentOption(
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
            )
        }

    val classificationOptions =
        listOf(
            SegmentOption(
                value = ClassificationMode.CLOUD,
                label = stringResource(R.string.settings_classification_cloud),
                description = stringResource(R.string.settings_classification_cloud_desc),
            ),
            SegmentOption(
                value = ClassificationMode.ON_DEVICE,
                label = stringResource(R.string.settings_classification_on_device),
                description = stringResource(R.string.settings_classification_on_device_desc),
            ),
        )

    val accuracyOptions =
        listOf(
            SegmentOption(
                value = AccuracyLevel.LOW,
                label = stringResource(R.string.settings_accuracy_low),
                description = stringResource(R.string.settings_accuracy_low_desc),
            ),
            SegmentOption(
                value = AccuracyLevel.MEDIUM,
                label = stringResource(R.string.settings_accuracy_medium),
                description = stringResource(R.string.settings_accuracy_medium_desc),
            ),
            SegmentOption(
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
            SettingsSectionHeader(title = stringResource(R.string.settings_section_storage))
            SettingSwitchRow(
                title = stringResource(R.string.settings_auto_save_title),
                subtitle = stringResource(R.string.settings_auto_save_subtitle),
                icon = Icons.Filled.Storage,
                checked = autoSaveEnabled,
                onCheckedChange = settingsViewModel::setAutoSaveEnabled,
            )

            val folderName =
                saveDirectoryUri?.let { StorageHelper.getFolderDisplayName(context, Uri.parse(it)) }
                    ?: stringResource(R.string.settings_save_location_default)

            SettingActionRow(
                title = stringResource(R.string.settings_save_location_title),
                subtitle = folderName,
                icon = Icons.Filled.Folder,
                onClick = {
                    if (autoSaveEnabled) {
                        dirPickerLauncher.launch(saveDirectoryUri?.let { Uri.parse(it) })
                    }
                },
                enabled = autoSaveEnabled,
            )

            SettingsSectionHeader(title = stringResource(R.string.settings_section_capture))
            SettingIconSegmentedRow(
                title = stringResource(R.string.settings_resolution_title),
                subtitle = stringResource(R.string.settings_resolution_subtitle),
                icon = Icons.Filled.HighQuality,
                options = captureOptions,
                selected = captureResolution,
                onSelect = cameraViewModel::updateCaptureResolution,
            )

            SettingsSectionHeader(title = stringResource(R.string.settings_section_scanning))
            SettingIconSegmentedRow(
                title = stringResource(R.string.settings_classification_title),
                subtitle = stringResource(R.string.settings_classification_subtitle),
                icon = Icons.Filled.Cloud,
                options = classificationOptions,
                selected = classificationMode,
                onSelect = classificationViewModel::updateMode,
            )

            SettingSwitchRow(
                title = stringResource(R.string.settings_low_data_mode_title),
                subtitle = stringResource(R.string.settings_low_data_mode_subtitle),
                checked = lowDataMode,
                onCheckedChange = classificationViewModel::updateLowDataMode,
            )

            val currentAccuracy = AccuracyLevel.fromThreshold(similarityThreshold)
            SettingIconSegmentedRow(
                title = stringResource(R.string.settings_accuracy_title),
                subtitle = stringResource(R.string.settings_accuracy_subtitle),
                icon = Icons.Filled.Tune,
                options = accuracyOptions,
                selected = currentAccuracy,
                onSelect = { level ->
                    itemsViewModel.updateSimilarityThreshold(level.threshold)
                },
            )

            SettingSwitchRow(
                title = stringResource(R.string.settings_open_item_list_after_scan_title),
                subtitle = stringResource(R.string.settings_open_item_list_after_scan_subtitle),
                checked = openItemListAfterScan,
                onCheckedChange = settingsViewModel::setOpenItemListAfterScan,
            )

            SettingsSectionHeader(title = stringResource(R.string.settings_section_overlay))
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

private enum class AccuracyLevel(val threshold: Float) {
    LOW(0.45f),
    MEDIUM(0.55f),
    HIGH(0.7f),
    ;

    companion object {
        fun fromThreshold(value: Float): AccuracyLevel {
            return values().minBy { kotlin.math.abs(it.threshold - value) }
        }
    }
}
