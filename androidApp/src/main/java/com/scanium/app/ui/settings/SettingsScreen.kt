package com.scanium.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.scanium.app.media.StorageHelper
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.scanium.app.BuildConfig
import com.scanium.app.model.user.UserEdition
import com.scanium.app.model.AssistantRegion
import com.scanium.app.model.AssistantTone
import com.scanium.app.model.AssistantUnits
import com.scanium.app.model.AssistantVerbosity

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
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val allowCloud by viewModel.allowCloud.collectAsState()
    val allowAssistant by viewModel.allowAssistant.collectAsState()
    val shareDiagnostics by viewModel.shareDiagnostics.collectAsState()
    val currentEdition by viewModel.currentEdition.collectAsState()
    val entitlementState by viewModel.entitlementState.collectAsState()
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()
    val autoSaveEnabled by viewModel.autoSaveEnabled.collectAsState()
    val saveDirectoryUri by viewModel.saveDirectoryUri.collectAsState()
    val allowAssistantImages by viewModel.allowAssistantImages.collectAsState()
    val forceFtueTour by viewModel.forceFtueTour.collectAsState()

    // Assistant Personalization
    val assistantLanguage by viewModel.assistantLanguage.collectAsState()
    val assistantTone by viewModel.assistantTone.collectAsState()
    val assistantRegion by viewModel.assistantRegion.collectAsState()
    val assistantUnits by viewModel.assistantUnits.collectAsState()
    val assistantVerbosity by viewModel.assistantVerbosity.collectAsState()

    // Voice Mode
    val voiceModeEnabled by viewModel.voiceModeEnabled.collectAsState()
    val speakAnswersEnabled by viewModel.speakAnswersEnabled.collectAsState()
    val autoSendTranscript by viewModel.autoSendTranscript.collectAsState()
    val voiceLanguage by viewModel.voiceLanguage.collectAsState()
    val assistantHapticsEnabled by viewModel.assistantHapticsEnabled.collectAsState()

    // Privacy Safe Mode
    val isPrivacySafeModeActive by viewModel.isPrivacySafeModeActive.collectAsState()

    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            StorageHelper.takePersistablePermissions(context, it)
            viewModel.setSaveDirectoryUri(it.toString())
        }
    }

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
            
            SettingsSectionTitle("Storage")

            SettingsSwitchItem(
                title = "Automatically save photos",
                subtitle = "Save captured items to device",
                icon = Icons.Default.Save,
                checked = autoSaveEnabled,
                onCheckedChange = { viewModel.setAutoSaveEnabled(it) }
            )

            val folderName = saveDirectoryUri?.let { 
                StorageHelper.getFolderDisplayName(context, Uri.parse(it)) 
            } ?: "Tap to select folder"

            ListItem(
                headlineContent = { Text("Save location") },
                supportingContent = { Text(folderName) },
                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                modifier = Modifier.clickable(enabled = autoSaveEnabled) {
                    dirPickerLauncher.launch(
                        saveDirectoryUri?.let { Uri.parse(it) }
                    )
                },
                colors = if (!autoSaveEnabled) {
                     ListItemDefaults.colors(
                        headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        supportingColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        leadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                     )
                } else ListItemDefaults.colors()
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

            SettingsSwitchItem(
                title = "Privacy Safe Mode",
                subtitle = if (isPrivacySafeModeActive) "Active - no data leaves device" else "Disable all cloud sharing",
                icon = Icons.Default.Shield,
                checked = isPrivacySafeModeActive,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        viewModel.enablePrivacySafeMode()
                    } else {
                        // Re-enable cloud classification when turning off safe mode
                        viewModel.setAllowCloud(true)
                    }
                }
            )

            SettingsItem(
                title = "Reset Privacy Settings",
                subtitle = "Restore defaults for all privacy options",
                icon = Icons.Default.RestartAlt,
                onClick = { viewModel.resetPrivacySettings() }
            )

            HorizontalDivider()

            SettingsSectionTitle("Features")

            SettingsSwitchItem(
                title = "Assistant Features",
                subtitle = "Enable AI assistant (Experimental)",
                icon = Icons.Default.AutoAwesome,
                checked = allowAssistant,
                enabled = currentEdition != UserEdition.FREE, // Only for Pro/Dev
                onCheckedChange = { viewModel.setAllowAssistant(it) }
            )

            SettingsSwitchItem(
                title = "Send Images to Assistant",
                subtitle = "Allow item thumbnails to be sent for visual context",
                icon = Icons.Default.Cloud,
                checked = allowAssistantImages,
                enabled = allowAssistant, // Only available when assistant is enabled
                onCheckedChange = { viewModel.setAllowAssistantImages(it) }
            )

            // Assistant Personalization Section (only show when assistant is enabled)
            if (allowAssistant) {
                HorizontalDivider()
                SettingsSectionTitle("Assistant Personalization")

                SettingsDropdownItem(
                    title = "Language",
                    subtitle = "Assistant response language",
                    icon = Icons.Default.Language,
                    selectedValue = assistantLanguage,
                    options = listOf("EN" to "English", "NL" to "Dutch", "DE" to "German", "FR" to "French"),
                    onValueChange = { viewModel.setAssistantLanguage(it) }
                )

                SettingsDropdownItem(
                    title = "Tone",
                    subtitle = "Response style",
                    icon = Icons.Default.Tune,
                    selectedValue = assistantTone.name,
                    options = listOf(
                        AssistantTone.NEUTRAL.name to "Neutral",
                        AssistantTone.FRIENDLY.name to "Friendly",
                        AssistantTone.PROFESSIONAL.name to "Professional"
                    ),
                    onValueChange = { viewModel.setAssistantTone(AssistantTone.valueOf(it)) }
                )

                SettingsDropdownItem(
                    title = "Region",
                    subtitle = "Affects currency and marketplace suggestions",
                    icon = Icons.Default.Language,
                    selectedValue = assistantRegion.name,
                    options = listOf(
                        AssistantRegion.NL.name to "Netherlands",
                        AssistantRegion.DE.name to "Germany",
                        AssistantRegion.BE.name to "Belgium",
                        AssistantRegion.FR.name to "France",
                        AssistantRegion.UK.name to "United Kingdom",
                        AssistantRegion.US.name to "United States",
                        AssistantRegion.EU.name to "Europe (General)"
                    ),
                    onValueChange = { viewModel.setAssistantRegion(AssistantRegion.valueOf(it)) }
                )

                SettingsDropdownItem(
                    title = "Units",
                    subtitle = "Measurement system",
                    icon = Icons.Default.Tune,
                    selectedValue = assistantUnits.name,
                    options = listOf(
                        AssistantUnits.METRIC.name to "Metric (cm, kg)",
                        AssistantUnits.IMPERIAL.name to "Imperial (in, lb)"
                    ),
                    onValueChange = { viewModel.setAssistantUnits(AssistantUnits.valueOf(it)) }
                )

                SettingsDropdownItem(
                    title = "Verbosity",
                    subtitle = "Response detail level",
                    icon = Icons.Default.Tune,
                    selectedValue = assistantVerbosity.name,
                    options = listOf(
                        AssistantVerbosity.CONCISE.name to "Concise",
                        AssistantVerbosity.NORMAL.name to "Normal",
                        AssistantVerbosity.DETAILED.name to "Detailed"
                    ),
                    onValueChange = { viewModel.setAssistantVerbosity(AssistantVerbosity.valueOf(it)) }
                )

                // Voice Mode Section
                HorizontalDivider()
                SettingsSectionTitle("Voice Mode")

                SettingsSwitchItem(
                    title = "Enable Voice Mode",
                    subtitle = "Use microphone for hands-free input",
                    icon = Icons.Default.Mic,
                    checked = voiceModeEnabled,
                    onCheckedChange = { viewModel.setVoiceModeEnabled(it) }
                )

                SettingsSwitchItem(
                    title = "Speak Answers Aloud",
                    subtitle = "Read assistant responses using text-to-speech",
                    icon = Icons.Default.VolumeUp,
                    checked = speakAnswersEnabled,
                    enabled = voiceModeEnabled,
                    onCheckedChange = { viewModel.setSpeakAnswersEnabled(it) }
                )

                SettingsSwitchItem(
                    title = "Auto-send After Transcription",
                    subtitle = "Automatically send message after voice input",
                    icon = Icons.Default.Send,
                    checked = autoSendTranscript,
                    enabled = voiceModeEnabled,
                    onCheckedChange = { viewModel.setAutoSendTranscript(it) }
                )

                SettingsSwitchItem(
                    title = "Assistant Haptics",
                    subtitle = "Vibrate on send/apply/copy actions",
                    icon = Icons.Default.Vibration,
                    checked = assistantHapticsEnabled,
                    onCheckedChange = { viewModel.setAssistantHapticsEnabled(it) }
                )

                SettingsDropdownItem(
                    title = "Voice Language",
                    subtitle = "Language for speech recognition and TTS",
                    icon = Icons.Default.Language,
                    selectedValue = voiceLanguage.ifEmpty { "Follow assistant ($assistantLanguage)" },
                    options = listOf(
                        "" to "Follow assistant language",
                        "EN" to "English",
                        "NL" to "Dutch",
                        "DE" to "German",
                        "FR" to "French",
                        "ES" to "Spanish",
                        "IT" to "Italian"
                    ),
                    onValueChange = { viewModel.setVoiceLanguage(it) }
                )
            }

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

                SettingsItem(
                    title = "Test Crash Reporting",
                    subtitle = "Send test event to Sentry (handled exception)",
                    icon = Icons.Default.BugReport,
                    onClick = { viewModel.triggerCrashTest(throwCrash = false) }
                )

                SettingsItem(
                    title = "Test Diagnostics Bundle",
                    subtitle = "Capture exception with diagnostics.json attachment",
                    icon = Icons.Default.BugReport,
                    onClick = { viewModel.triggerDiagnosticsTest() }
                )

                HorizontalDivider()
                SettingsSectionTitle("First-Time Experience")

                SettingsSwitchItem(
                    title = "Force First-Time Tour",
                    subtitle = "Always show tour on app launch (debug only)",
                    icon = Icons.Default.Info,
                    checked = forceFtueTour,
                    onCheckedChange = { viewModel.setForceFtueTour(it) }
                )

                SettingsItem(
                    title = "Reset Tour Progress",
                    subtitle = "Clear tour completion flag",
                    icon = Icons.Default.Refresh,
                    onClick = { viewModel.resetFtueTour() }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdownItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    selectedValue: String,
    options: List<Pair<String, String>>, // value to displayLabel
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selectedValue }?.second ?: selectedValue

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = {
                Column {
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
            trailingContent = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .clickable { expanded = true }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onValueChange(value)
                        expanded = false
                    },
                    leadingIcon = if (value == selectedValue) {
                        { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else null
                )
            }
        }
    }
}
