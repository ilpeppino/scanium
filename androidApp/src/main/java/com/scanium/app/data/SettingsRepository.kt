package com.scanium.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scanium.app.model.user.UserEdition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings_preferences"
)

class SettingsRepository(private val context: Context) {
    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val ALLOW_CLOUD_CLASSIFICATION_KEY = booleanPreferencesKey("allow_cloud_classification")
        private val ALLOW_ASSISTANT_KEY = booleanPreferencesKey("allow_assistant")
        private val SHARE_DIAGNOSTICS_KEY = booleanPreferencesKey("share_diagnostics")
        private val USER_EDITION_KEY = stringPreferencesKey("user_edition")
        private val DEVELOPER_MODE_KEY = booleanPreferencesKey("developer_mode")
    }

    val themeModeFlow: Flow<ThemeMode> = context.settingsDataStore.data.map { preferences ->
        val raw = preferences[THEME_MODE_KEY]
        raw?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    val allowCloudClassificationFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[ALLOW_CLOUD_CLASSIFICATION_KEY] ?: true
    }

    suspend fun setAllowCloudClassification(allow: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[ALLOW_CLOUD_CLASSIFICATION_KEY] = allow
        }
    }

    val allowAssistantFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[ALLOW_ASSISTANT_KEY] ?: false
    }

    suspend fun setAllowAssistant(allow: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[ALLOW_ASSISTANT_KEY] = allow
        }
    }

    val shareDiagnosticsFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[SHARE_DIAGNOSTICS_KEY] ?: false
    }

    suspend fun setShareDiagnostics(share: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[SHARE_DIAGNOSTICS_KEY] = share
        }
    }

    val userEditionFlow: Flow<UserEdition> = context.settingsDataStore.data.map { preferences ->
        val raw = preferences[USER_EDITION_KEY]
        raw?.let { runCatching { UserEdition.valueOf(it) }.getOrNull() } ?: UserEdition.FREE
    }

    suspend fun setUserEdition(edition: UserEdition) {
        context.settingsDataStore.edit { preferences ->
            preferences[USER_EDITION_KEY] = edition.name
        }
    }

    val developerModeFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[DEVELOPER_MODE_KEY] ?: false
    }

    suspend fun setDeveloperMode(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[DEVELOPER_MODE_KEY] = enabled
        }
    }
}
