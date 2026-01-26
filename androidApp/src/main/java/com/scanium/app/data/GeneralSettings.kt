package com.scanium.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.scanium.app.model.AppLanguage
import com.scanium.app.model.user.UserEdition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class GeneralSettings(
    private val dataStore: DataStore<Preferences>,
) {
    val themeModeFlow: Flow<ThemeMode> =
        dataStore.data.safeMap(ThemeMode.SYSTEM) { preferences ->
            val raw = preferences[SettingsKeys.General.THEME_MODE_KEY]
            raw?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.General.THEME_MODE_KEY] = mode.name
        }
    }

    val appLanguageFlow: Flow<AppLanguage> =
        dataStore.data.safeMap(AppLanguage.SYSTEM) { preferences ->
            val raw = preferences[SettingsKeys.General.APP_LANGUAGE_KEY]
            raw?.let { AppLanguage.fromCode(it) } ?: AppLanguage.SYSTEM
        }

    suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.General.APP_LANGUAGE_KEY] = language.code
        }
    }

    val allowCloudClassificationFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.General.ALLOW_CLOUD_CLASSIFICATION_KEY] ?: true
        }

    suspend fun setAllowCloudClassification(allow: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.General.ALLOW_CLOUD_CLASSIFICATION_KEY] = allow
        }
    }

    val shareDiagnosticsFlow: Flow<Boolean> =
        dataStore.data.safeMap(false) { preferences ->
            preferences[SettingsKeys.General.SHARE_DIAGNOSTICS_KEY] ?: false
        }

    suspend fun setShareDiagnostics(share: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.General.SHARE_DIAGNOSTICS_KEY] = share
        }
    }

    val userEditionFlow: Flow<UserEdition> =
        dataStore.data.map { preferences ->
            val raw = preferences[SettingsKeys.General.USER_EDITION_KEY]
            raw?.let { runCatching { UserEdition.valueOf(it) }.getOrNull() } ?: UserEdition.FREE
        }

    suspend fun setUserEdition(edition: UserEdition) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.General.USER_EDITION_KEY] = edition.name
        }
    }

    val autoSaveEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.General.AUTO_SAVE_ENABLED_KEY] ?: false
        }

    suspend fun setAutoSaveEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.General.AUTO_SAVE_ENABLED_KEY] = enabled
        }
    }

    val saveDirectoryUriFlow: Flow<String?> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.General.SAVE_DIRECTORY_URI_KEY]
        }

    suspend fun setSaveDirectoryUri(uri: String?) {
        dataStore.edit { preferences ->
            if (uri != null) {
                preferences[SettingsKeys.General.SAVE_DIRECTORY_URI_KEY] = uri
            } else {
                preferences.remove(SettingsKeys.General.SAVE_DIRECTORY_URI_KEY)
            }
        }
    }

    val exportFormatFlow: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.General.EXPORT_FORMAT_KEY] ?: "ZIP"
        }

    suspend fun setExportFormat(format: String) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.General.EXPORT_FORMAT_KEY] = format
        }
    }

    val soundsEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.General.SOUNDS_ENABLED_KEY] ?: true
        }

    suspend fun setSoundsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.General.SOUNDS_ENABLED_KEY] = enabled
        }
    }

    val showItemInfoChipsFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.General.SHOW_ITEM_INFO_CHIPS_KEY] ?: true
        }

    suspend fun setShowItemInfoChips(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.General.SHOW_ITEM_INFO_CHIPS_KEY] = enabled
        }
    }
}
