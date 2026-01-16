package com.scanium.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scanium.app.BuildConfig
import com.scanium.app.R
import com.scanium.app.config.FeatureFlags

import androidx.annotation.StringRes

data class SettingsCategory(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit,
    val visible: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onGeneralClick: () -> Unit,
    onCameraClick: () -> Unit,
    onAssistantClick: () -> Unit,
    onFeedbackClick: () -> Unit,
    onStorageClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onDeveloperClick: () -> Unit,
) {
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()
    // FeatureFlags.allowDeveloperMode is false in beta/prod builds, completely hiding Developer Options
    // In dev builds, it's shown if either DEBUG build type or user enabled developer mode
    val showDeveloper = FeatureFlags.allowDeveloperMode && (BuildConfig.DEBUG || isDeveloperMode)

    val categories =
        listOf(
            SettingsCategory(
                title = R.string.settings_category_general_title,
                description = R.string.settings_category_general_desc,
                icon = Icons.Filled.Settings,
                onClick = onGeneralClick,
            ),
            SettingsCategory(
                title = R.string.settings_category_camera_title,
                description = R.string.settings_category_camera_desc,
                icon = Icons.Filled.CameraAlt,
                onClick = onCameraClick,
            ),
            SettingsCategory(
                title = R.string.settings_category_assistant_title,
                description = R.string.settings_category_assistant_desc,
                icon = Icons.Rounded.AutoAwesome,
                onClick = onAssistantClick,
            ),
            SettingsCategory(
                title = R.string.settings_category_feedback_title,
                description = R.string.settings_category_feedback_desc,
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                onClick = onFeedbackClick,
            ),
            SettingsCategory(
                title = R.string.settings_category_storage_title,
                description = R.string.settings_category_storage_desc,
                icon = Icons.Filled.Storage,
                onClick = onStorageClick,
            ),
            SettingsCategory(
                title = R.string.settings_category_privacy_title,
                description = R.string.settings_category_privacy_desc,
                icon = Icons.Filled.PrivacyTip,
                onClick = onPrivacyClick,
            ),
            SettingsCategory(
                title = R.string.settings_category_developer_title,
                description = R.string.settings_category_developer_desc,
                icon = Icons.Filled.BugReport,
                onClick = onDeveloperClick,
                visible = showDeveloper,
            ),
        ).filter { it.visible }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_home_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // Donation banner - always first item after description
            item {
                SettingsDonationCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onDonationClicked = { amount ->
                        // Optional: emit analytics event
                        // TelemetryService.trackEvent("donation_clicked", mapOf("amount" to amount))
                    },
                )
            }

            items(categories) { category ->
                SettingNavigationRow(
                    title = stringResource(category.title),
                    subtitle = stringResource(category.description),
                    icon = category.icon,
                    onClick = category.onClick,
                )
            }

            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text =
                            stringResource(
                                R.string.settings_version,
                                BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
