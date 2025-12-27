package com.scanium.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsFeedbackScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val soundsEnabled by viewModel.soundsEnabled.collectAsState()
    val assistantHapticsEnabled by viewModel.assistantHapticsEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_feedback_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SettingsSectionHeader(title = stringResource(R.string.settings_section_notifications))
            SettingSwitchRow(
                title = stringResource(R.string.settings_sounds_title),
                subtitle = stringResource(R.string.settings_sounds_subtitle),
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                checked = soundsEnabled,
                onCheckedChange = viewModel::setSoundsEnabled
            )

            SettingSwitchRow(
                title = stringResource(R.string.settings_assistant_haptics_title),
                subtitle = stringResource(R.string.settings_assistant_haptics_subtitle),
                icon = Icons.Filled.Vibration,
                checked = assistantHapticsEnabled,
                onCheckedChange = viewModel::setAssistantHapticsEnabled
            )
        }
    }
}
