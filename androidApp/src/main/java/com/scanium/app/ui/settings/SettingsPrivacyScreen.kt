package com.scanium.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
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
fun SettingsPrivacyScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDataUsage: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val allowCloud by viewModel.allowCloud.collectAsState()
    val shareDiagnostics by viewModel.shareDiagnostics.collectAsState()
    val privacySafeModeActive by viewModel.isPrivacySafeModeActive.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_privacy_title)) },
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
            SettingsSectionHeader(title = stringResource(R.string.settings_section_data_controls))
            SettingSwitchRow(
                title = stringResource(R.string.settings_cloud_classification_title),
                subtitle = stringResource(R.string.settings_cloud_classification_subtitle),
                icon = Icons.Filled.Cloud,
                checked = allowCloud,
                onCheckedChange = viewModel::setAllowCloud
            )

            SettingNavigationRow(
                title = stringResource(R.string.settings_data_usage_title),
                subtitle = stringResource(R.string.settings_data_usage_subtitle),
                icon = Icons.Filled.Info,
                onClick = onNavigateToDataUsage
            )

            SettingSwitchRow(
                title = stringResource(R.string.settings_share_diagnostics_title),
                subtitle = stringResource(R.string.settings_share_diagnostics_subtitle),
                icon = Icons.Filled.Info,
                checked = shareDiagnostics,
                onCheckedChange = viewModel::setShareDiagnostics
            )

            SettingSwitchRow(
                title = stringResource(R.string.settings_privacy_safe_mode_title),
                subtitle = if (privacySafeModeActive) {
                    stringResource(R.string.settings_privacy_safe_mode_active)
                } else {
                    stringResource(R.string.settings_privacy_safe_mode_inactive)
                },
                icon = Icons.Filled.Shield,
                checked = privacySafeModeActive,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        viewModel.enablePrivacySafeMode()
                    } else {
                        viewModel.setAllowCloud(true)
                    }
                }
            )

            SettingActionRow(
                title = stringResource(R.string.settings_reset_privacy_title),
                subtitle = stringResource(R.string.settings_reset_privacy_subtitle),
                icon = Icons.Filled.RestartAlt,
                onClick = viewModel::resetPrivacySettings
            )

            SettingsSectionHeader(title = stringResource(R.string.settings_section_legal))
            SettingNavigationRow(
                title = stringResource(R.string.settings_privacy_policy_title),
                subtitle = stringResource(R.string.settings_privacy_policy_subtitle),
                icon = Icons.Filled.Info,
                onClick = onNavigateToPrivacyPolicy
            )
            SettingNavigationRow(
                title = stringResource(R.string.settings_terms_title),
                subtitle = stringResource(R.string.settings_terms_subtitle),
                icon = Icons.Filled.Info,
                onClick = onNavigateToTerms
            )
            SettingNavigationRow(
                title = stringResource(R.string.settings_about_title),
                subtitle = stringResource(R.string.settings_about_subtitle),
                icon = Icons.Filled.Info,
                onClick = onNavigateToAbout
            )
        }
    }
}
