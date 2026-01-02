package com.scanium.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.scanium.app.R
import com.scanium.app.model.AssistantRegion
import com.scanium.app.model.AssistantTone
import com.scanium.app.model.AssistantUnits
import com.scanium.app.model.AssistantVerbosity
import com.scanium.app.model.config.AssistantPrerequisiteState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAssistantScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val allowAssistant by viewModel.allowAssistant.collectAsState()
    val allowAssistantImages by viewModel.allowAssistantImages.collectAsState()
    val assistantLanguage by viewModel.assistantLanguage.collectAsState()
    val assistantTone by viewModel.assistantTone.collectAsState()
    val assistantRegion by viewModel.assistantRegion.collectAsState()
    val assistantUnits by viewModel.assistantUnits.collectAsState()
    val assistantVerbosity by viewModel.assistantVerbosity.collectAsState()
    val voiceModeEnabled by viewModel.voiceModeEnabled.collectAsState()
    val speakAnswersEnabled by viewModel.speakAnswersEnabled.collectAsState()
    val autoSendTranscript by viewModel.autoSendTranscript.collectAsState()
    val voiceLanguage by viewModel.voiceLanguage.collectAsState()
    val assistantPrerequisiteState by viewModel.assistantPrerequisiteState.collectAsState()
    val showPrerequisiteDialog by viewModel.showPrerequisiteDialog.collectAsState()
    val connectionTestState by viewModel.connectionTestState.collectAsState()

    val languageOptions =
        listOf(
            "EN" to stringResource(R.string.settings_language_en),
            "NL" to stringResource(R.string.settings_language_nl),
            "DE" to stringResource(R.string.settings_language_de),
            "FR" to stringResource(R.string.settings_language_fr),
            "ES" to stringResource(R.string.settings_language_es),
            "IT" to stringResource(R.string.settings_language_it),
        )
    val assistantLanguageLabel =
        languageOptions.firstOrNull { it.first == assistantLanguage }?.second
            ?: languageOptions.first().second

    val toneOptions =
        AssistantTone.values().map { tone ->
            val label =
                when (tone) {
                    AssistantTone.NEUTRAL -> stringResource(R.string.settings_tone_neutral)
                    AssistantTone.FRIENDLY -> stringResource(R.string.settings_tone_friendly)
                    AssistantTone.PROFESSIONAL -> stringResource(R.string.settings_tone_professional)
                }
            tone.name to label
        }
    val selectedToneLabel = toneOptions.firstOrNull { it.first == assistantTone.name }?.second ?: assistantTone.name

    val regionOptions =
        AssistantRegion.values().map { region ->
            val label =
                when (region) {
                    AssistantRegion.EU -> stringResource(R.string.settings_region_europe)
                    AssistantRegion.NL -> stringResource(R.string.settings_region_netherlands)
                    AssistantRegion.DE -> stringResource(R.string.settings_region_germany)
                    AssistantRegion.FR -> stringResource(R.string.settings_region_france)
                    AssistantRegion.BE -> stringResource(R.string.settings_region_belgium)
                    AssistantRegion.UK -> stringResource(R.string.settings_region_uk)
                    AssistantRegion.US -> stringResource(R.string.settings_region_us)
                }
            region.name to label
        }
    val selectedRegionLabel = regionOptions.firstOrNull { it.first == assistantRegion.name }?.second ?: assistantRegion.name

    val unitOptions =
        AssistantUnits.values().map { units ->
            val label =
                when (units) {
                    AssistantUnits.METRIC -> stringResource(R.string.settings_units_metric)
                    AssistantUnits.IMPERIAL -> stringResource(R.string.settings_units_imperial)
                }
            units.name to label
        }
    val selectedUnitsLabel = unitOptions.firstOrNull { it.first == assistantUnits.name }?.second ?: assistantUnits.name

    val verbosityOptions =
        AssistantVerbosity.values().map { verbosity ->
            val label =
                when (verbosity) {
                    AssistantVerbosity.CONCISE -> stringResource(R.string.settings_verbosity_concise)
                    AssistantVerbosity.NORMAL -> stringResource(R.string.settings_verbosity_normal)
                    AssistantVerbosity.DETAILED -> stringResource(R.string.settings_verbosity_detailed)
                }
            verbosity.name to label
        }
    val selectedVerbosityLabel = verbosityOptions.firstOrNull { it.first == assistantVerbosity.name }?.second ?: assistantVerbosity.name

    val voiceLanguageOptions =
        listOf(
            "" to stringResource(R.string.settings_voice_language_follow_assistant, assistantLanguageLabel),
            "EN" to stringResource(R.string.settings_language_en),
            "NL" to stringResource(R.string.settings_language_nl),
            "DE" to stringResource(R.string.settings_language_de),
            "FR" to stringResource(R.string.settings_language_fr),
            "ES" to stringResource(R.string.settings_language_es),
            "IT" to stringResource(R.string.settings_language_it),
        )
    val selectedVoiceLanguageLabel =
        voiceLanguageOptions.firstOrNull { it.first == voiceLanguage }?.second
            ?: voiceLanguageOptions.first().second

    val micPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                viewModel.setVoiceModeEnabled(true)
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.settings_voice_mode_permission_denied),
                    )
                }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_assistant_title)) },
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
                    .padding(padding),
        ) {
            SettingsSectionHeader(title = stringResource(R.string.settings_section_assistant_access))
            SettingSwitchRow(
                title = stringResource(R.string.settings_assistant_toggle_title),
                subtitle =
                    if (assistantPrerequisiteState.allSatisfied) {
                        stringResource(R.string.settings_assistant_toggle_subtitle_ready)
                    } else {
                        stringResource(
                            R.string.settings_assistant_toggle_subtitle_prereqs,
                            assistantPrerequisiteState.unsatisfiedCount,
                        )
                    },
                icon = Icons.Filled.Cloud,
                checked = allowAssistant,
                onCheckedChange = { enabled ->
                    viewModel.setAllowAssistant(enabled)
                },
            )

            SettingActionRow(
                title = stringResource(R.string.settings_assistant_test_connection),
                subtitle =
                    when (connectionTestState) {
                        is ConnectionTestState.Testing -> stringResource(R.string.settings_assistant_connection_testing)
                        is ConnectionTestState.Success -> stringResource(R.string.settings_assistant_connection_success)
                        is ConnectionTestState.Failed -> (connectionTestState as ConnectionTestState.Failed).message
                        else -> stringResource(R.string.settings_assistant_connection_idle)
                    },
                icon =
                    when (connectionTestState) {
                        is ConnectionTestState.Success -> Icons.Filled.CheckCircle
                        is ConnectionTestState.Failed -> Icons.Filled.Warning
                        is ConnectionTestState.Testing -> null
                        else -> Icons.Filled.SettingsVoice
                    },
                onClick = {
                    if (connectionTestState !is ConnectionTestState.Testing) {
                        viewModel.testAssistantConnection()
                    }
                },
            )

            SettingSwitchRow(
                title = stringResource(R.string.settings_assistant_images_title),
                subtitle = stringResource(R.string.settings_assistant_images_subtitle),
                icon = Icons.Filled.Cloud,
                checked = allowAssistantImages,
                enabled = allowAssistant,
                onCheckedChange = viewModel::setAllowAssistantImages,
            )

            if (allowAssistant) {
                SettingsSectionHeader(title = stringResource(R.string.settings_section_personalization))
                SettingDropdownRow(
                    title = stringResource(R.string.settings_assistant_language_title),
                    subtitle = stringResource(R.string.settings_assistant_language_subtitle),
                    icon = Icons.Filled.Language,
                    selectedLabel = assistantLanguageLabel,
                    options = languageOptions,
                    onOptionSelected = viewModel::setAssistantLanguage,
                )

                SettingDropdownRow(
                    title = stringResource(R.string.settings_assistant_tone_title),
                    subtitle = stringResource(R.string.settings_assistant_tone_subtitle),
                    icon = Icons.Filled.Tune,
                    selectedLabel = selectedToneLabel,
                    options = toneOptions,
                    onOptionSelected = { value ->
                        viewModel.setAssistantTone(AssistantTone.valueOf(value))
                    },
                )

                SettingDropdownRow(
                    title = stringResource(R.string.settings_assistant_region_title),
                    subtitle = stringResource(R.string.settings_assistant_region_subtitle),
                    icon = Icons.Filled.Language,
                    selectedLabel = selectedRegionLabel,
                    options = regionOptions,
                    onOptionSelected = { value ->
                        viewModel.setAssistantRegion(AssistantRegion.valueOf(value))
                    },
                )

                SettingDropdownRow(
                    title = stringResource(R.string.settings_assistant_units_title),
                    subtitle = stringResource(R.string.settings_assistant_units_subtitle),
                    icon = Icons.Filled.Tune,
                    selectedLabel = selectedUnitsLabel,
                    options = unitOptions,
                    onOptionSelected = { value ->
                        viewModel.setAssistantUnits(AssistantUnits.valueOf(value))
                    },
                )

                SettingDropdownRow(
                    title = stringResource(R.string.settings_assistant_verbosity_title),
                    subtitle = stringResource(R.string.settings_assistant_verbosity_subtitle),
                    icon = Icons.Filled.Tune,
                    selectedLabel = selectedVerbosityLabel,
                    options = verbosityOptions,
                    onOptionSelected = { value ->
                        viewModel.setAssistantVerbosity(AssistantVerbosity.valueOf(value))
                    },
                )

                SettingsSectionHeader(title = stringResource(R.string.settings_section_voice_mode))
                SettingSwitchRow(
                    title = stringResource(R.string.settings_voice_mode_title),
                    subtitle = stringResource(R.string.settings_voice_mode_subtitle),
                    icon = Icons.Filled.Mic,
                    checked = voiceModeEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            val permissionGranted =
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                            if (permissionGranted) {
                                viewModel.setVoiceModeEnabled(true)
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        } else {
                            viewModel.setVoiceModeEnabled(false)
                        }
                    },
                )

                SettingSwitchRow(
                    title = stringResource(R.string.settings_speak_answers_title),
                    subtitle = stringResource(R.string.settings_speak_answers_subtitle),
                    icon = Icons.Filled.SettingsVoice,
                    checked = speakAnswersEnabled,
                    onCheckedChange = viewModel::setSpeakAnswersEnabled,
                )

                SettingSwitchRow(
                    title = stringResource(R.string.settings_auto_send_voice_title),
                    subtitle = stringResource(R.string.settings_auto_send_voice_subtitle),
                    icon = Icons.AutoMirrored.Filled.Send,
                    checked = autoSendTranscript,
                    enabled = voiceModeEnabled,
                    onCheckedChange = viewModel::setAutoSendTranscript,
                )

                SettingDropdownRow(
                    title = stringResource(R.string.settings_voice_language_title),
                    subtitle = stringResource(R.string.settings_voice_language_subtitle),
                    icon = Icons.Filled.Language,
                    selectedLabel = selectedVoiceLanguageLabel,
                    options = voiceLanguageOptions,
                    onOptionSelected = viewModel::setVoiceLanguage,
                    enabled = voiceModeEnabled,
                )
            }
        }
    }

    if (showPrerequisiteDialog) {
        AssistantPrerequisiteDialog(
            prerequisiteState = assistantPrerequisiteState,
            connectionState = connectionTestState,
            onDismiss = viewModel::dismissPrerequisiteDialog,
            onTestConnection = viewModel::testAssistantConnection,
        )
    }
}

@Composable
private fun AssistantPrerequisiteDialog(
    prerequisiteState: AssistantPrerequisiteState,
    connectionState: ConnectionTestState,
    onDismiss: () -> Unit,
    onTestConnection: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_assistant_prereq_title)) },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                prerequisiteState.prerequisites.forEach { prerequisite ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = if (prerequisite.satisfied) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription =
                                if (prerequisite.satisfied) {
                                    stringResource(
                                        R.string.cd_prerequisite_satisfied,
                                    )
                                } else {
                                    stringResource(R.string.cd_prerequisite_not_satisfied)
                                },
                            tint = if (prerequisite.satisfied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        Column {
                            Text(text = prerequisite.displayName, style = MaterialTheme.typography.bodyMedium)
                            if (!prerequisite.satisfied) {
                                Text(
                                    text = prerequisite.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                when (connectionState) {
                    is ConnectionTestState.Testing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                            Text(text = stringResource(R.string.settings_assistant_connection_testing))
                        }
                    }
                    is ConnectionTestState.Success -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = stringResource(R.string.cd_connection_success),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.settings_assistant_connection_success),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    is ConnectionTestState.Failed -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = stringResource(R.string.cd_connection_failed),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = connectionState.message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    else -> { }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onTestConnection) {
                Text(stringResource(R.string.settings_assistant_connection_test))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_dialog_close))
            }
        },
    )
}
