package com.scanium.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class VoiceSettings(
    private val dataStore: DataStore<Preferences>,
) {
    val voiceModeEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Voice.VOICE_MODE_ENABLED_KEY] ?: false
        }

    suspend fun setVoiceModeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Voice.VOICE_MODE_ENABLED_KEY] = enabled
        }
    }

    val speakAnswersEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Voice.SPEAK_ANSWERS_KEY] ?: false
        }

    suspend fun setSpeakAnswersEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Voice.SPEAK_ANSWERS_KEY] = enabled
        }
    }

    val autoSendTranscriptFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Voice.AUTO_SEND_TRANSCRIPT_KEY] ?: false
        }

    suspend fun setAutoSendTranscript(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Voice.AUTO_SEND_TRANSCRIPT_KEY] = enabled
        }
    }

    val voiceLanguageFlow: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Voice.VOICE_LANGUAGE_KEY] ?: ""
        }

    suspend fun setVoiceLanguage(language: String) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Voice.VOICE_LANGUAGE_KEY] = language
        }
    }

    val assistantHapticsEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Voice.ASSISTANT_HAPTICS_KEY] ?: false
        }

    suspend fun setAssistantHapticsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Voice.ASSISTANT_HAPTICS_KEY] = enabled
        }
    }
}
