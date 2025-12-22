package com.scanium.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.scanium.app.BuildConfig
import com.scanium.app.model.user.UserEdition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDataUsage: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToUpgrade: () -> Unit
) {
    val scrollState = rememberScrollState()
    val allowCloud by viewModel.allowCloud.collectAsState()
    val allowAssistant by viewModel.allowAssistant.collectAsState()
    val shareDiagnostics by viewModel.shareDiagnostics.collectAsState()
    val currentEdition by viewModel.currentEdition.collectAsState()
    val entitlementState by viewModel.entitlementState.collectAsState()
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scrollState)
                .fillMaxSize()
        ) {
            SettingsSectionTitle("Account & Edition")
            
            ListItem(
                headlineContent = { Text("Current Edition") },
                supportingContent = { 
                    Column {
                        Text(currentEdition.name)
                        entitlementState.expiresAt?.let { expires ->
                            val date = java.util.Date(expires)
                            val format = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                            Text("Expires: ${format.format(date)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                leadingContent = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                trailingContent = {
                    if (currentEdition == UserEdition.FREE) {
                        Button(onClick = onNavigateToUpgrade) {
                            Text("Upgrade")
                        }
                    } else if (currentEdition == UserEdition.PRO) {
                         TextButton(onClick = { /* TODO: Manage subscription */ }) {
                            Text("Manage")
                        }
                    }
                }
            )

            HorizontalDivider()

            SettingsSectionTitle("Privacy & Data")

            SettingsSwitchItem(
                title = "Cloud Classification",
                subtitle = "Send images to cloud for better accuracy",
                icon = Icons.Default.Cloud,
                checked = allowCloud,
                onCheckedChange = { viewModel.setAllowCloud(it) }
            )

            SettingsItem(
                title = "Data Usage & Transparency",
                subtitle = "Learn how we handle your data",
                icon = Icons.Default.Info,
                onClick = onNavigateToDataUsage
            )

            SettingsSwitchItem(
                title = "Share Diagnostics",
                subtitle = "Help us improve Scanium",
                icon = Icons.Default.BugReport,
                checked = shareDiagnostics,
                onCheckedChange = { viewModel.setShareDiagnostics(it) }
            )

            HorizontalDivider()

            SettingsSectionTitle("Features")

            SettingsSwitchItem(
                title = "Assistant Features",
                subtitle = "Enable AI assistant (Experimental)",
                icon = Icons.Default.SmartToy,
                checked = allowAssistant,
                enabled = currentEdition != UserEdition.FREE, // Only for Pro/Dev
                onCheckedChange = { viewModel.setAllowAssistant(it) }
            )

            HorizontalDivider()
            
            SettingsSectionTitle("Legal")
            
            SettingsItem(
                title = "Privacy Policy",
                icon = Icons.Default.Security,
                onClick = onNavigateToPrivacy
            )
            
            SettingsItem(
                title = "Terms of Service",
                icon = Icons.Default.Info,
                onClick = onNavigateToTerms
            )
            
            SettingsItem(
                title = "About Scanium",
                icon = Icons.Default.Info,
                onClick = onNavigateToAbout
            )

            if (BuildConfig.DEBUG) {
                HorizontalDivider()
                SettingsSectionTitle("Developer")
                
                SettingsSwitchItem(
                    title = "Developer Mode",
                    subtitle = "Unlock all features for testing",
                    icon = Icons.Default.BugReport,
                    checked = isDeveloperMode,
                    onCheckedChange = { viewModel.setDeveloperMode(it) }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        },
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) }
    )
}
