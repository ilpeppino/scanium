package com.scanium.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.scanium.app.config.FeatureFlags
import com.scanium.app.model.AssistantPrefs
import com.scanium.app.model.AssistantRegion
import com.scanium.app.model.AssistantTone
import com.scanium.app.model.AssistantUnits
import com.scanium.app.model.AssistantVerbosity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal class AssistantSettings(
    private val dataStore: DataStore<Preferences>,
) {
    val allowAssistantFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            val storedValue = preferences[SettingsKeys.Assistant.ALLOW_ASSISTANT_KEY]
            when {
                storedValue != null -> storedValue
                FeatureFlags.isDevBuild -> true
                else -> false
            }
        }

    suspend fun setAllowAssistant(allow: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Assistant.ALLOW_ASSISTANT_KEY] = allow
        }
    }

    val allowAssistantImagesFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            val storedValue = preferences[SettingsKeys.Assistant.ALLOW_ASSISTANT_IMAGES_KEY]
            when {
                storedValue != null -> storedValue
                FeatureFlags.isDevBuild -> true
                else -> false
            }
        }

    suspend fun setAllowAssistantImages(allow: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Assistant.ALLOW_ASSISTANT_IMAGES_KEY] = allow
        }
    }

    val assistantLanguageFlow: Flow<String> =
        dataStore.data.map { preferences ->
            preferences[SettingsKeys.Assistant.ASSISTANT_LANGUAGE_KEY] ?: "EN"
        }

    suspend fun setAssistantLanguage(language: String) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Assistant.ASSISTANT_LANGUAGE_KEY] = language
        }
    }

    val assistantToneFlow: Flow<AssistantTone> =
        dataStore.data.map { preferences ->
            val raw = preferences[SettingsKeys.Assistant.ASSISTANT_TONE_KEY]
            raw?.let { runCatching { AssistantTone.valueOf(it) }.getOrNull() } ?: AssistantTone.NEUTRAL
        }

    suspend fun setAssistantTone(tone: AssistantTone) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Assistant.ASSISTANT_TONE_KEY] = tone.name
        }
    }

    val assistantCountryCodeFlow: Flow<String> =
        dataStore.data.map { preferences ->
            val raw = preferences[SettingsKeys.Assistant.ASSISTANT_REGION_KEY]
                ?: SettingsLocaleHelpers.detectCountryCodeFromLocale()
            SettingsLocaleHelpers.normalizeCountryCode(raw)
        }

    @Deprecated("Use assistantCountryCodeFlow for full country support")
    val assistantRegionFlow: Flow<AssistantRegion> =
        assistantCountryCodeFlow.map { countryCode ->
            SettingsLocaleHelpers.mapCountryCodeToRegion(countryCode)
        }

    suspend fun setAssistantCountryCode(countryCode: String) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Assistant.ASSISTANT_REGION_KEY] = countryCode.uppercase()
        }
    }

    @Deprecated("Use setAssistantCountryCode for full country support")
    suspend fun setAssistantRegion(region: AssistantRegion) {
        setAssistantCountryCode(region.name)
    }

    val assistantUnitsFlow: Flow<AssistantUnits> =
        dataStore.data.map { preferences ->
            val raw = preferences[SettingsKeys.Assistant.ASSISTANT_UNITS_KEY]
            raw?.let { runCatching { AssistantUnits.valueOf(it) }.getOrNull() } ?: AssistantUnits.METRIC
        }

    suspend fun setAssistantUnits(units: AssistantUnits) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Assistant.ASSISTANT_UNITS_KEY] = units.name
        }
    }

    val assistantVerbosityFlow: Flow<AssistantVerbosity> =
        dataStore.data.map { preferences ->
            val raw = preferences[SettingsKeys.Assistant.ASSISTANT_VERBOSITY_KEY]
            raw?.let { runCatching { AssistantVerbosity.valueOf(it) }.getOrNull() } ?: AssistantVerbosity.NORMAL
        }

    suspend fun setAssistantVerbosity(verbosity: AssistantVerbosity) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Assistant.ASSISTANT_VERBOSITY_KEY] = verbosity.name
        }
    }

    val assistantPrefsFlow: Flow<AssistantPrefs> =
        combine(
            assistantLanguageFlow,
            assistantToneFlow,
            assistantRegionFlow,
            assistantUnitsFlow,
            assistantVerbosityFlow,
        ) { language, tone, region, units, verbosity ->
            AssistantPrefs(
                language = language,
                tone = tone,
                region = region,
                units = units,
                verbosity = verbosity,
            )
        }
}
