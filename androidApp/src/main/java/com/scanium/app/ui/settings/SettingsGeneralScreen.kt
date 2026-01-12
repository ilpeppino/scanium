package com.scanium.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.scanium.app.R
import com.scanium.app.data.ThemeMode
import com.scanium.app.model.AppLanguage
import com.scanium.app.model.user.UserEdition
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGeneralScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onUpgradeClick: () -> Unit,
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val currentEdition by viewModel.currentEdition.collectAsState()
    val entitlementState by viewModel.entitlementState.collectAsState()
    val soundsEnabled by viewModel.soundsEnabled.collectAsState()

    val editionLabel =
        when (currentEdition) {
            UserEdition.FREE -> stringResource(R.string.settings_edition_free)
            UserEdition.PRO -> stringResource(R.string.settings_edition_pro)
            UserEdition.DEVELOPER -> stringResource(R.string.settings_edition_developer)
        }

    val expirationText =
        entitlementState.expiresAt?.let { expires ->
            val formatted = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expires))
            stringResource(R.string.settings_edition_expires_on, formatted)
        }

    val trailingEditionAction: (@Composable () -> Unit)? =
        when (currentEdition) {
            UserEdition.FREE -> {
                { Button(onClick = onUpgradeClick) { Text(stringResource(R.string.settings_upgrade_cta)) } }
            }
            UserEdition.PRO -> {
                {
                    TextButton(onClick = {}, enabled = false) {
                        Text(stringResource(R.string.settings_manage_subscription))
                    }
                }
            }
            UserEdition.DEVELOPER -> null
        }

    // Theme options using SettingOption
    val themeOptions =
        listOf(
            SettingOption(
                value = ThemeMode.SYSTEM,
                label = stringResource(R.string.settings_theme_system),
                description = stringResource(R.string.settings_theme_system_desc),
                isRecommended = true,
            ),
            SettingOption(
                value = ThemeMode.LIGHT,
                label = stringResource(R.string.settings_theme_light),
                description = stringResource(R.string.settings_theme_light_desc),
            ),
            SettingOption(
                value = ThemeMode.DARK,
                label = stringResource(R.string.settings_theme_dark),
                description = stringResource(R.string.settings_theme_dark_desc),
            ),
        )

    // Language options using SettingOption
    val languageOptions =
        listOf(
            SettingOption(
                value = AppLanguage.SYSTEM,
                label = stringResource(R.string.settings_language_system_default),
                isRecommended = true,
            ),
            SettingOption(
                value = AppLanguage.EN,
                label = stringResource(R.string.settings_language_en),
            ),
            SettingOption(
                value = AppLanguage.ES,
                label = stringResource(R.string.settings_language_es),
            ),
            SettingOption(
                value = AppLanguage.IT,
                label = stringResource(R.string.settings_language_it),
            ),
            SettingOption(
                value = AppLanguage.FR,
                label = stringResource(R.string.settings_language_fr),
            ),
            SettingOption(
                value = AppLanguage.NL,
                label = stringResource(R.string.settings_language_nl),
            ),
            SettingOption(
                value = AppLanguage.DE,
                label = stringResource(R.string.settings_language_de),
            ),
            SettingOption(
                value = AppLanguage.PT_BR,
                label = stringResource(R.string.settings_language_pt_br),
            ),
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_general_title)) },
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
            SettingsSectionHeader(title = stringResource(R.string.settings_section_account))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_current_edition_title)) },
                supportingContent = {
                    Column {
                        Text(editionLabel, style = MaterialTheme.typography.bodyLarge)
                        expirationText?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                leadingContent = { Icon(Icons.Filled.VerifiedUser, contentDescription = stringResource(R.string.cd_edition_status)) },
                trailingContent = trailingEditionAction,
            )

            SettingsSectionHeader(title = stringResource(R.string.settings_section_appearance))

            // 1) Theme picker with bottom sheet
            ValuePickerSettingRow(
                title = stringResource(R.string.settings_theme_label),
                subtitle = stringResource(R.string.settings_theme_subtitle),
                icon = Icons.Filled.DarkMode,
                currentValue = themeMode,
                options = themeOptions,
                onValueSelected = viewModel::setThemeMode,
            )

            // 2) Language picker with bottom sheet
            ValuePickerSettingRow(
                title = stringResource(R.string.settings_language_system_title),
                subtitle = stringResource(R.string.settings_language_system_desc),
                icon = Icons.Filled.Language,
                currentValue = appLanguage,
                options = languageOptions,
                onValueSelected = viewModel::setAppLanguage,
            )

            SettingsSectionHeader(title = stringResource(R.string.settings_section_preferences))

            // 3) Sounds toggle
            SettingSwitchRow(
                title = stringResource(R.string.settings_sounds_title),
                subtitle = stringResource(R.string.settings_sounds_subtitle),
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                checked = soundsEnabled,
                onCheckedChange = viewModel::setSoundsEnabled,
            )

            // 4) First-time guide replay
            SettingActionRow(
                title = stringResource(R.string.settings_replay_guide_title),
                subtitle = stringResource(R.string.settings_replay_guide_subtitle),
                icon = Icons.Filled.Info,
                onClick = viewModel::resetFtueTour,
            )
        }
    }
}
