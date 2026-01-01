package com.scanium.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    val currentEdition by viewModel.currentEdition.collectAsState()
    val entitlementState by viewModel.entitlementState.collectAsState()

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

    val themeOptions =
        listOf(
            SegmentOption(
                ThemeMode.SYSTEM,
                stringResource(R.string.settings_theme_system),
                stringResource(R.string.settings_theme_system_desc),
            ),
            SegmentOption(
                ThemeMode.LIGHT,
                stringResource(R.string.settings_theme_light),
                stringResource(R.string.settings_theme_light_desc),
            ),
            SegmentOption(ThemeMode.DARK, stringResource(R.string.settings_theme_dark), stringResource(R.string.settings_theme_dark_desc)),
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
                    .padding(padding),
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
            SettingSegmentedRow(
                title = stringResource(R.string.settings_theme_label),
                subtitle = stringResource(R.string.settings_theme_subtitle),
                options = themeOptions,
                selected = themeMode,
                onSelect = viewModel::setThemeMode,
            )

            SettingsSectionHeader(title = stringResource(R.string.settings_section_language))
            SettingNavigationRow(
                title = stringResource(R.string.settings_language_system_title),
                subtitle = stringResource(R.string.settings_language_system_desc),
                icon = null,
                onClick = {},
                showChevron = false,
                enabled = false,
            )
        }
    }
}
