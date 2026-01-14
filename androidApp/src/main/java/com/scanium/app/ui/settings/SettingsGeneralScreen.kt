package com.scanium.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.scanium.app.R
import com.scanium.app.data.MarketplaceRepository
import com.scanium.app.data.ThemeMode
import com.scanium.app.model.user.UserEdition
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGeneralScreen(
    viewModel: SettingsViewModel,
    marketplaceRepository: MarketplaceRepository,
    onNavigateBack: () -> Unit,
    onUpgradeClick: () -> Unit,
) {
    // Legacy state (still used for backward compatibility during transition)
    val themeMode by viewModel.themeMode.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val currentEdition by viewModel.currentEdition.collectAsState()
    val entitlementState by viewModel.entitlementState.collectAsState()
    val soundsEnabled by viewModel.soundsEnabled.collectAsState()

    // Unified settings state
    val primaryRegionCountry by viewModel.primaryRegionCountry.collectAsState()
    val primaryLanguage by viewModel.primaryLanguage.collectAsState()

    // Load countries for primary region picker
    val countries = remember { marketplaceRepository.loadCountries() }

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

    // Helper to get language display name
    @Composable
    fun getLanguageDisplayName(languageTag: String): String {
        return when (languageTag.lowercase()) {
            "en" -> stringResource(R.string.settings_language_en)
            "nl" -> stringResource(R.string.settings_language_nl)
            "de" -> stringResource(R.string.settings_language_de)
            "fr" -> stringResource(R.string.settings_language_fr)
            "es" -> stringResource(R.string.settings_language_es)
            "it" -> stringResource(R.string.settings_language_it)
            "pt-br" -> stringResource(R.string.settings_language_pt_br)
            else -> languageTag
        }
    }

    // Primary region/country options
    val primaryCountryOptions = countries.map { country ->
        SettingOption(
            value = country.code,
            label = "${country.getFlagEmoji()} ${country.getDisplayName(primaryLanguage)}",
        )
    }

    // Language options
    val languageOptions = listOf(
        SettingOption(value = "en", label = stringResource(R.string.settings_language_en)),
        SettingOption(value = "nl", label = stringResource(R.string.settings_language_nl)),
        SettingOption(value = "de", label = stringResource(R.string.settings_language_de)),
        SettingOption(value = "fr", label = stringResource(R.string.settings_language_fr)),
        SettingOption(value = "es", label = stringResource(R.string.settings_language_es)),
        SettingOption(value = "it", label = stringResource(R.string.settings_language_it)),
        SettingOption(value = "pt-BR", label = stringResource(R.string.settings_language_pt_br)),
    )

    // Phase D: Account deletion dialog state
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

            // Phase C: Enhanced Auth section with session expiry and refresh
            val userInfo = remember { viewModel.getUserInfo() }
            val isSignedIn = userInfo != null

            if (isSignedIn && userInfo != null) {
                // Calculate session expiry display
                val expiresAt = remember { viewModel.getAccessTokenExpiresAt() }
                val expiryText = expiresAt?.let { timestamp ->
                    val now = System.currentTimeMillis()
                    val daysRemaining = ((timestamp - now) / (1000 * 60 * 60 * 24)).toInt()
                    when {
                        daysRemaining > 1 -> stringResource(R.string.settings_session_expires_in_days, daysRemaining)
                        daysRemaining == 1 -> stringResource(R.string.settings_session_expires_in_day)
                        else -> stringResource(R.string.settings_session_expires_soon)
                    }
                }

                ListItem(
                    headlineContent = {
                        Text(userInfo.displayName ?: userInfo.email ?: "Signed In")
                    },
                    supportingContent = {
                        Column {
                            if (userInfo.email != null) {
                                Text(userInfo.email)
                            }
                            expiryText?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    leadingContent = {
                        Icon(Icons.Filled.AccountCircle, contentDescription = null)
                    },
                )

                // Sign out and refresh session buttons
                ListItem(
                    headlineContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.signOut() }) {
                                Text(stringResource(R.string.settings_sign_out))
                            }
                            TextButton(onClick = { viewModel.refreshSession() }) {
                                Text(stringResource(R.string.settings_refresh_session))
                            }
                        }
                    }
                )

                // Phase D: Delete account button
                ListItem(
                    headlineContent = {
                        TextButton(
                            onClick = { showDeleteConfirmDialog = true },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Filled.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(stringResource(R.string.settings_delete_account))
                        }
                    }
                )
            } else {
                // Sign-in state management
                var isSigningIn by remember { mutableStateOf(false) }
                val activity = LocalContext.current as? android.app.Activity

                // Extract strings for use in coroutine
                val errorUnknown = stringResource(R.string.settings_sign_in_error_unknown)
                val errorTemplate = stringResource(R.string.settings_sign_in_error_template)

                ListItem(
                    headlineContent = {
                        Text(
                            if (isSigningIn) {
                                stringResource(R.string.settings_signing_in)
                            } else {
                                stringResource(R.string.settings_sign_in_google)
                            }
                        )
                    },
                    supportingContent = {
                        Text(
                            if (isSigningIn) {
                                stringResource(R.string.settings_signing_in_desc)
                            } else {
                                stringResource(R.string.settings_sign_in_google_desc)
                            }
                        )
                    },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                    },
                    modifier = Modifier.clickable(enabled = !isSigningIn && activity != null) {
                        activity?.let { act ->
                            isSigningIn = true
                            coroutineScope.launch {
                                val result = viewModel.signInWithGoogle(act)
                                isSigningIn = false
                                if (result.isFailure) {
                                    val errorMessage = result.exceptionOrNull()?.message ?: errorUnknown
                                    snackbarHostState.showSnackbar(errorTemplate.format(errorMessage))
                                }
                            }
                        }
                    }
                )
            }

            // Language & Region Section
            SettingsSectionHeader(title = stringResource(R.string.settings_section_language))

            // 1) Language - The single source of truth for app UI, AI assistant, and TTS
            ValuePickerSettingRow(
                title = stringResource(R.string.settings_primary_language_label),
                subtitle = getLanguageDisplayName(primaryLanguage),
                icon = Icons.Filled.Language,
                currentValue = primaryLanguage,
                options = languageOptions,
                onValueSelected = viewModel::setPrimaryLanguage,
            )

            // 2) Marketplace country - Drives marketplace selection only
            ValuePickerSettingRow(
                title = stringResource(R.string.settings_primary_country_label),
                subtitle = countries.find { it.code == primaryRegionCountry }?.let {
                    "${it.getFlagEmoji()} ${it.getDisplayName(primaryLanguage)}"
                } ?: primaryRegionCountry,
                icon = Icons.Filled.Language,
                currentValue = primaryRegionCountry,
                options = primaryCountryOptions,
                onValueSelected = viewModel::setPrimaryRegionCountry,
            )

            SettingsSectionHeader(title = stringResource(R.string.settings_section_appearance))

            // Theme picker with bottom sheet
            ValuePickerSettingRow(
                title = stringResource(R.string.settings_theme_label),
                subtitle = stringResource(R.string.settings_theme_subtitle),
                icon = Icons.Filled.DarkMode,
                currentValue = themeMode,
                options = themeOptions,
                onValueSelected = viewModel::setThemeMode,
            )

            SettingsSectionHeader(title = stringResource(R.string.settings_section_preferences))

            // Sounds toggle
            SettingSwitchRow(
                title = stringResource(R.string.settings_sounds_title),
                subtitle = stringResource(R.string.settings_sounds_subtitle),
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                checked = soundsEnabled,
                onCheckedChange = viewModel::setSoundsEnabled,
            )

            // First-time guide replay
            SettingActionRow(
                title = stringResource(R.string.settings_replay_guide_title),
                subtitle = stringResource(R.string.settings_replay_guide_subtitle),
                icon = Icons.Filled.Info,
                onClick = viewModel::resetFtueTour,
            )
        }

        // Phase D: Account deletion confirmation dialog
        if (showDeleteConfirmDialog) {
            val successMessage = stringResource(R.string.settings_delete_account_success)
            val errorMessageTemplate = stringResource(R.string.settings_delete_account_error)

            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                icon = {
                    Icon(
                        Icons.Filled.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(stringResource(R.string.settings_delete_account_title))
                },
                text = {
                    Text(stringResource(R.string.settings_delete_account_message))
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            coroutineScope.launch {
                                val result = viewModel.deleteAccount()
                                if (result.isSuccess) {
                                    snackbarHostState.showSnackbar(successMessage)
                                    // User will be automatically signed out and UI will update
                                } else {
                                    val errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
                                    snackbarHostState.showSnackbar(
                                        errorMessageTemplate.format(errorMessage)
                                    )
                                }
                            }
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.settings_delete_account_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text(stringResource(R.string.settings_delete_account_cancel))
                    }
                }
            )
        }
    }
}
