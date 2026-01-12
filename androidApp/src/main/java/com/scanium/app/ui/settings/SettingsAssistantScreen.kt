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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.scanium.app.data.MarketplaceRepository
import com.scanium.app.model.AiLanguageChoice
import com.scanium.app.model.AssistantRegion
import com.scanium.app.model.AssistantTone
import com.scanium.app.model.AssistantUnits
import com.scanium.app.model.AssistantVerbosity
import com.scanium.app.model.Country
import com.scanium.app.model.FollowOrCustom
import com.scanium.app.model.TtsLanguageChoice
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
    val assistantCountryCode by viewModel.assistantCountryCode.collectAsState()
    val assistantUnits by viewModel.assistantUnits.collectAsState()
    val assistantVerbosity by viewModel.assistantVerbosity.collectAsState()
    val voiceModeEnabled by viewModel.voiceModeEnabled.collectAsState()
    val speakAnswersEnabled by viewModel.speakAnswersEnabled.collectAsState()
    val autoSendTranscript by viewModel.autoSendTranscript.collectAsState()
    val voiceLanguage by viewModel.voiceLanguage.collectAsState()
    val assistantPrerequisiteState by viewModel.assistantPrerequisiteState.collectAsState()
    val showPrerequisiteDialog by viewModel.showPrerequisiteDialog.collectAsState()
    val connectionTestState by viewModel.connectionTestState.collectAsState()

    // Unified settings state
    val primaryLanguage by viewModel.primaryLanguage.collectAsState()
    val primaryRegionCountry by viewModel.primaryRegionCountry.collectAsState()
    val aiLanguageSetting by viewModel.aiLanguageSetting.collectAsState()
    val marketplaceCountrySetting by viewModel.marketplaceCountrySetting.collectAsState()
    val ttsLanguageSetting by viewModel.ttsLanguageSetting.collectAsState()
    val effectiveAiOutputLanguage by viewModel.effectiveAiOutputLanguage.collectAsState()
    val effectiveMarketplaceCountry by viewModel.effectiveMarketplaceCountry.collectAsState()
    val effectiveTtsLanguage by viewModel.effectiveTtsLanguage.collectAsState()

    // Load countries from marketplace JSON
    val marketplaceRepository = remember { MarketplaceRepository(context) }
    val countries = remember { marketplaceRepository.loadCountries() }

    // Helper to get language display name
    @Composable
    fun getLanguageDisplayName(languageTag: String): String {
        return when (languageTag.uppercase()) {
            "EN" -> stringResource(R.string.settings_language_en)
            "NL" -> stringResource(R.string.settings_language_nl)
            "DE" -> stringResource(R.string.settings_language_de)
            "FR" -> stringResource(R.string.settings_language_fr)
            "ES" -> stringResource(R.string.settings_language_es)
            "IT" -> stringResource(R.string.settings_language_it)
            "PT_BR", "PT-BR" -> stringResource(R.string.settings_language_pt_br)
            else -> languageTag
        }
    }

    // AI Language options with "Follow primary" and "Auto-detect"
    val aiLanguageOptions = buildList {
        // "Follow primary" option
        add(
            SettingOption(
                value = "follow",
                label = stringResource(R.string.settings_assistant_language_follow_primary, getLanguageDisplayName(primaryLanguage)),
                isRecommended = true,
            )
        )
        // "Auto-detect" option
        add(
            SettingOption(
                value = "auto_detect",
                label = stringResource(R.string.settings_assistant_language_auto_detect),
            )
        )
        // Individual language options
        add(SettingOption(value = "EN", label = stringResource(R.string.settings_language_en)))
        add(SettingOption(value = "NL", label = stringResource(R.string.settings_language_nl)))
        add(SettingOption(value = "DE", label = stringResource(R.string.settings_language_de)))
        add(SettingOption(value = "FR", label = stringResource(R.string.settings_language_fr)))
        add(SettingOption(value = "ES", label = stringResource(R.string.settings_language_es)))
        add(SettingOption(value = "IT", label = stringResource(R.string.settings_language_it)))
        add(SettingOption(value = "PT_BR", label = stringResource(R.string.settings_language_pt_br)))
    }

    // Legacy language options (for backward compatibility if needed)
    val languageOptions =
        listOf(
            SettingOption(value = "EN", label = stringResource(R.string.settings_language_en)),
            SettingOption(value = "NL", label = stringResource(R.string.settings_language_nl)),
            SettingOption(value = "DE", label = stringResource(R.string.settings_language_de)),
            SettingOption(value = "FR", label = stringResource(R.string.settings_language_fr)),
            SettingOption(value = "ES", label = stringResource(R.string.settings_language_es)),
            SettingOption(value = "IT", label = stringResource(R.string.settings_language_it)),
            SettingOption(value = "PT_BR", label = stringResource(R.string.settings_language_pt_br)),
        )

    // Tone options using SettingOption
    val toneOptions =
        AssistantTone.values().map { tone ->
            SettingOption(
                value = tone,
                label =
                    when (tone) {
                        AssistantTone.NEUTRAL -> stringResource(R.string.settings_tone_neutral)
                        AssistantTone.FRIENDLY -> stringResource(R.string.settings_tone_friendly)
                        AssistantTone.PROFESSIONAL -> stringResource(R.string.settings_tone_professional)
                        AssistantTone.MARKETPLACE -> stringResource(R.string.settings_tone_marketplace)
                    },
                isRecommended = tone == AssistantTone.FRIENDLY,
            )
        }

    // Marketplace country options with "Follow primary"
    val primaryCountry = remember(primaryRegionCountry, countries) {
        countries.find { it.code == primaryRegionCountry }
    }
    val primaryCountryLabel = primaryCountry?.let {
        "${it.getFlagEmoji()} ${it.getDisplayName(primaryLanguage.lowercase())}"
    } ?: primaryRegionCountry

    val marketplaceCountryOptions = buildList {
        // "Follow primary" option
        add(
            SettingOption(
                value = "follow",
                label = stringResource(R.string.settings_assistant_country_follow_primary, primaryCountryLabel),
                isRecommended = true,
            )
        )
        // Individual country options
        addAll(countries.map { country ->
            SettingOption(
                value = country.code,
                label = "${country.getFlagEmoji()} ${country.getDisplayName(primaryLanguage.lowercase())}",
            )
        })
    }

    // Legacy country options (for backward compatibility if needed)
    val countryOptions =
        remember(countries, assistantLanguage) {
            countries.map { country ->
                SettingOption(
                    value = country.code,
                    label = "${country.getFlagEmoji()} ${country.getDisplayName(assistantLanguage.lowercase())}",
                )
            }
        }

    // Units options using SettingOption
    val unitOptions =
        AssistantUnits.values().map { units ->
            SettingOption(
                value = units,
                label =
                    when (units) {
                        AssistantUnits.METRIC -> stringResource(R.string.settings_units_metric)
                        AssistantUnits.IMPERIAL -> stringResource(R.string.settings_units_imperial)
                    },
            )
        }

    // Verbosity options using SettingOption
    val verbosityOptions =
        AssistantVerbosity.values().map { verbosity ->
            SettingOption(
                value = verbosity,
                label =
                    when (verbosity) {
                        AssistantVerbosity.CONCISE -> stringResource(R.string.settings_verbosity_concise)
                        AssistantVerbosity.NORMAL -> stringResource(R.string.settings_verbosity_normal)
                        AssistantVerbosity.DETAILED -> stringResource(R.string.settings_verbosity_detailed)
                    },
                isRecommended = verbosity == AssistantVerbosity.NORMAL,
            )
        }

    // TTS (Voice output) language options with unified settings
    val ttsLanguageOptions = buildList {
        // "Follow AI language" option (default)
        add(
            SettingOption(
                value = "follow_ai",
                label = stringResource(R.string.settings_tts_language_follow_ai, getLanguageDisplayName(effectiveAiOutputLanguage)),
                isRecommended = true,
            )
        )
        // "Follow primary" option
        add(
            SettingOption(
                value = "follow_primary",
                label = stringResource(R.string.settings_tts_language_follow_primary, getLanguageDisplayName(primaryLanguage)),
            )
        )
        // Individual language options
        add(SettingOption(value = "EN", label = stringResource(R.string.settings_language_en)))
        add(SettingOption(value = "NL", label = stringResource(R.string.settings_language_nl)))
        add(SettingOption(value = "DE", label = stringResource(R.string.settings_language_de)))
        add(SettingOption(value = "FR", label = stringResource(R.string.settings_language_fr)))
        add(SettingOption(value = "ES", label = stringResource(R.string.settings_language_es)))
        add(SettingOption(value = "IT", label = stringResource(R.string.settings_language_it)))
        add(SettingOption(value = "PT_BR", label = stringResource(R.string.settings_language_pt_br)))
    }

    // Legacy voice language options (for backward compatibility)
    val currentLanguageLabel = languageOptions.find { it.value == assistantLanguage }?.label ?: ""
    val voiceLanguageOptions =
        listOf(
            SettingOption(
                value = "",
                label = stringResource(R.string.settings_voice_language_follow_assistant, currentLanguageLabel),
                isRecommended = true,
            ),
            SettingOption(value = "EN", label = stringResource(R.string.settings_language_en)),
            SettingOption(value = "NL", label = stringResource(R.string.settings_language_nl)),
            SettingOption(value = "DE", label = stringResource(R.string.settings_language_de)),
            SettingOption(value = "FR", label = stringResource(R.string.settings_language_fr)),
            SettingOption(value = "ES", label = stringResource(R.string.settings_language_es)),
            SettingOption(value = "IT", label = stringResource(R.string.settings_language_it)),
        )

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
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
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

                // AI Language picker (Unified Settings)
                ValuePickerSettingRow(
                    title = stringResource(R.string.settings_assistant_language_title),
                    subtitle = when (aiLanguageSetting) {
                        is FollowOrCustom.FollowPrimary -> stringResource(R.string.settings_assistant_language_follow_primary, getLanguageDisplayName(effectiveAiOutputLanguage))
                        is FollowOrCustom.Custom -> {
                            when (val choice = (aiLanguageSetting as FollowOrCustom.Custom).value) {
                                is AiLanguageChoice.AutoDetect -> stringResource(R.string.settings_assistant_language_auto_detect)
                                is AiLanguageChoice.LanguageTag -> "${stringResource(R.string.settings_assistant_language_custom)}: ${getLanguageDisplayName(choice.tag)}"
                            }
                        }
                    },
                    icon = Icons.Filled.Language,
                    currentValue = when (aiLanguageSetting) {
                        is FollowOrCustom.FollowPrimary -> "follow"
                        is FollowOrCustom.Custom -> {
                            when (val choice = (aiLanguageSetting as FollowOrCustom.Custom).value) {
                                is AiLanguageChoice.AutoDetect -> "auto_detect"
                                is AiLanguageChoice.LanguageTag -> choice.tag
                            }
                        }
                    },
                    options = aiLanguageOptions,
                    onValueSelected = { selectedValue ->
                        when (selectedValue) {
                            "follow" -> viewModel.setAiLanguageSetting(FollowOrCustom.followPrimary())
                            "auto_detect" -> viewModel.setAiLanguageSetting(FollowOrCustom.custom(AiLanguageChoice.AutoDetect))
                            else -> viewModel.setAiLanguageSetting(FollowOrCustom.custom(AiLanguageChoice.LanguageTag(selectedValue)))
                        }
                    },
                )

                // Tone picker with bottom sheet
                ValuePickerSettingRow(
                    title = stringResource(R.string.settings_assistant_tone_title),
                    subtitle = stringResource(R.string.settings_assistant_tone_subtitle),
                    icon = Icons.Filled.Tune,
                    currentValue = assistantTone,
                    options = toneOptions,
                    onValueSelected = viewModel::setAssistantTone,
                )

                // Marketplace Country picker (Unified Settings)
                ValuePickerSettingRow(
                    title = stringResource(R.string.settings_assistant_country_title),
                    subtitle = when (marketplaceCountrySetting) {
                        is FollowOrCustom.FollowPrimary -> {
                            val country = countries.find { it.code == effectiveMarketplaceCountry }
                            val label = country?.let {
                                "${it.getFlagEmoji()} ${it.getDisplayName(primaryLanguage.lowercase())}"
                            } ?: effectiveMarketplaceCountry
                            stringResource(R.string.settings_assistant_country_follow_primary, label)
                        }
                        is FollowOrCustom.Custom -> {
                            val country = countries.find { it.code == (marketplaceCountrySetting as FollowOrCustom.Custom).value }
                            val label = country?.let {
                                "${it.getFlagEmoji()} ${it.getDisplayName(primaryLanguage.lowercase())}"
                            } ?: (marketplaceCountrySetting as FollowOrCustom.Custom).value
                            "${stringResource(R.string.settings_assistant_country_custom)}: $label"
                        }
                    },
                    icon = Icons.Filled.Language,
                    currentValue = when (marketplaceCountrySetting) {
                        is FollowOrCustom.FollowPrimary -> "follow"
                        is FollowOrCustom.Custom -> (marketplaceCountrySetting as FollowOrCustom.Custom).value
                    },
                    options = marketplaceCountryOptions,
                    onValueSelected = { selectedValue ->
                        if (selectedValue == "follow") {
                            viewModel.setMarketplaceCountrySetting(FollowOrCustom.followPrimary())
                        } else {
                            viewModel.setMarketplaceCountrySetting(FollowOrCustom.custom(selectedValue))
                        }
                    },
                )

                // Units picker with bottom sheet
                ValuePickerSettingRow(
                    title = stringResource(R.string.settings_assistant_units_title),
                    subtitle = stringResource(R.string.settings_assistant_units_subtitle),
                    icon = Icons.Filled.Tune,
                    currentValue = assistantUnits,
                    options = unitOptions,
                    onValueSelected = viewModel::setAssistantUnits,
                )

                // Verbosity picker with bottom sheet
                ValuePickerSettingRow(
                    title = stringResource(R.string.settings_assistant_verbosity_title),
                    subtitle = stringResource(R.string.settings_assistant_verbosity_subtitle),
                    icon = Icons.Filled.Tune,
                    currentValue = assistantVerbosity,
                    options = verbosityOptions,
                    onValueSelected = viewModel::setAssistantVerbosity,
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

                // TTS (Voice output) language picker (Unified Settings)
                if (speakAnswersEnabled) {
                    ValuePickerSettingRow(
                        title = stringResource(R.string.settings_tts_language_title),
                        subtitle = when (ttsLanguageSetting) {
                            is TtsLanguageChoice.FollowAiLanguage -> stringResource(R.string.settings_tts_language_follow_ai, getLanguageDisplayName(effectiveTtsLanguage))
                            is TtsLanguageChoice.FollowPrimary -> stringResource(R.string.settings_tts_language_follow_primary, getLanguageDisplayName(effectiveTtsLanguage))
                            is TtsLanguageChoice.Custom -> "${stringResource(R.string.settings_tts_language_custom)}: ${getLanguageDisplayName((ttsLanguageSetting as TtsLanguageChoice.Custom).languageTag)}"
                        },
                        icon = Icons.Filled.SettingsVoice,
                        currentValue = when (ttsLanguageSetting) {
                            is TtsLanguageChoice.FollowAiLanguage -> "follow_ai"
                            is TtsLanguageChoice.FollowPrimary -> "follow_primary"
                            is TtsLanguageChoice.Custom -> (ttsLanguageSetting as TtsLanguageChoice.Custom).languageTag
                        },
                        options = ttsLanguageOptions,
                        onValueSelected = { selectedValue ->
                            when (selectedValue) {
                                "follow_ai" -> viewModel.setTtsLanguageSetting(TtsLanguageChoice.FollowAiLanguage)
                                "follow_primary" -> viewModel.setTtsLanguageSetting(TtsLanguageChoice.FollowPrimary)
                                else -> viewModel.setTtsLanguageSetting(TtsLanguageChoice.Custom(selectedValue))
                            }
                        },
                    )
                }

                SettingSwitchRow(
                    title = stringResource(R.string.settings_auto_send_voice_title),
                    subtitle = stringResource(R.string.settings_auto_send_voice_subtitle),
                    icon = Icons.AutoMirrored.Filled.Send,
                    checked = autoSendTranscript,
                    enabled = voiceModeEnabled,
                    onCheckedChange = viewModel::setAutoSendTranscript,
                )

                // Voice language picker with bottom sheet
                ValuePickerSettingRow(
                    title = stringResource(R.string.settings_voice_language_title),
                    subtitle = stringResource(R.string.settings_voice_language_subtitle),
                    icon = Icons.Filled.Language,
                    currentValue = voiceLanguage,
                    options = voiceLanguageOptions,
                    onValueSelected = viewModel::setVoiceLanguage,
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
