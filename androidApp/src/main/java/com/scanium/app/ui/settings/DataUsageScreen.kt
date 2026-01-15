package com.scanium.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scanium.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataUsageScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_data_usage_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.settings_data_usage_header),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            DataUsageSectionWithIcon(
                icon = Icons.Default.PhoneAndroid,
                title = stringResource(R.string.settings_data_usage_section_on_device_title),
                body = stringResource(R.string.settings_data_usage_section_on_device_body),
            )

            DataUsageSectionWithIcon(
                icon = Icons.Default.Cloud,
                title = stringResource(R.string.settings_data_usage_section_cloud_title),
                body = stringResource(R.string.settings_data_usage_section_cloud_body),
            )

            DataUsageSectionWithIcon(
                icon = Icons.Default.AutoAwesome,
                title = stringResource(R.string.settings_data_usage_section_assistant_title),
                body = stringResource(R.string.settings_data_usage_section_assistant_body),
            )

            DataUsageSectionWithIcon(
                icon = Icons.Default.Mic,
                title = stringResource(R.string.settings_data_usage_section_voice_title),
                body = stringResource(R.string.settings_data_usage_section_voice_body),
            )

            DataUsageSectionWithIcon(
                icon = Icons.Default.BugReport,
                title = stringResource(R.string.settings_data_usage_section_crash_title),
                body = stringResource(R.string.settings_data_usage_section_crash_body),
            )

            DataUsageSection(
                title = stringResource(R.string.settings_data_usage_section_not_collect_title),
                body = stringResource(R.string.settings_data_usage_section_not_collect_body),
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_data_usage_control_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_data_usage_control_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DataUsageSectionWithIcon(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun DataUsageSection(
    title: String,
    body: String,
) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
