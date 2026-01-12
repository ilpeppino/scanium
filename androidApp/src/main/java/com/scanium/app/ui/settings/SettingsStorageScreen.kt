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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.scanium.app.R
import com.scanium.app.media.StorageHelper
import kotlinx.coroutines.launch

/**
 * Export format options for item export.
 */
enum class ExportFormat {
    ZIP,
    CSV,
    JSON,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsStorageScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val autoSaveEnabled by viewModel.autoSaveEnabled.collectAsState()
    val saveDirectoryUri by viewModel.saveDirectoryUri.collectAsState()
    val exportFormat by viewModel.exportFormat.collectAsState()

    val dirPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            uri?.let {
                StorageHelper.takePersistablePermissions(context, it)
                viewModel.setSaveDirectoryUri(it.toString())
            }
        }

    // Export format options using SettingOption
    val exportFormatOptions =
        listOf(
            SettingOption(
                value = ExportFormat.ZIP,
                label = stringResource(R.string.settings_export_format_zip),
                description = stringResource(R.string.settings_export_format_zip_desc),
                isRecommended = true,
            ),
            SettingOption(
                value = ExportFormat.CSV,
                label = stringResource(R.string.settings_export_format_csv),
                description = stringResource(R.string.settings_export_format_csv_desc),
            ),
            SettingOption(
                value = ExportFormat.JSON,
                label = stringResource(R.string.settings_export_format_json),
                description = stringResource(R.string.settings_export_format_json_desc),
            ),
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_storage_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
        ) {
            // Section: Storage options
            SettingsSectionHeader(title = stringResource(R.string.settings_section_storage_options))

            // 1) Save images to device toggle
            SettingSwitchRow(
                title = stringResource(R.string.settings_auto_save_title),
                subtitle = stringResource(R.string.settings_auto_save_subtitle),
                icon = Icons.Filled.Storage,
                checked = autoSaveEnabled,
                onCheckedChange = viewModel::setAutoSaveEnabled,
            )

            // Save location picker
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

            // Section: Export
            SettingsSectionHeader(title = stringResource(R.string.settings_section_export))

            // 2) Default export format picker with bottom sheet
            ValuePickerSettingRow(
                title = stringResource(R.string.settings_export_format_title),
                subtitle = stringResource(R.string.settings_export_format_subtitle),
                icon = Icons.Filled.FileDownload,
                currentValue = exportFormat,
                options = exportFormatOptions,
                onValueSelected = viewModel::setExportFormat,
            )

            // Section: Cache & data
            SettingsSectionHeader(title = stringResource(R.string.settings_section_cache))

            // 3) Clear cached data button
            SettingActionRow(
                title = stringResource(R.string.settings_clear_cache_title),
                subtitle = stringResource(R.string.settings_clear_cache_subtitle),
                icon = Icons.Filled.Delete,
                onClick = {
                    viewModel.clearCache(context)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.settings_cache_cleared),
                        )
                    }
                },
            )
        }
    }
}
