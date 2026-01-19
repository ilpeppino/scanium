package com.scanium.app.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit

internal class SettingsMigrations(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun runMigrationIfNeeded() {
        dataStore.edit { preferences ->
            val currentVersion = preferences[SettingsKeys.Unified.SETTINGS_SCHEMA_VERSION_KEY] ?: 0

            if (currentVersion < SettingsKeys.Unified.CURRENT_SCHEMA_VERSION) {
                Log.i(
                    SETTINGS_DATASTORE_TAG,
                    "Running settings migration from version $currentVersion to ${SettingsKeys.Unified.CURRENT_SCHEMA_VERSION}",
                )

                if (currentVersion < 1) {
                    migrateToUnifiedSettings(preferences)
                }

                preferences[SettingsKeys.Unified.SETTINGS_SCHEMA_VERSION_KEY] =
                    SettingsKeys.Unified.CURRENT_SCHEMA_VERSION
                Log.i(SETTINGS_DATASTORE_TAG, "Settings migration complete")
            }
        }
    }

    private fun migrateToUnifiedSettings(preferences: MutablePreferences) {
        val existingMarketplaceCountry = preferences[SettingsKeys.Assistant.ASSISTANT_REGION_KEY]
        val primaryCountry =
            SettingsLocaleHelpers.normalizeCountryCode(
                existingMarketplaceCountry ?: SettingsLocaleHelpers.detectCountryCodeFromLocale(),
            )
        preferences[SettingsKeys.Unified.PRIMARY_REGION_COUNTRY_KEY] = primaryCountry

        val existingAppLanguage = preferences[SettingsKeys.General.APP_LANGUAGE_KEY]
        val primaryLanguage =
            when {
                existingAppLanguage != null && existingAppLanguage != "system" -> {
                    existingAppLanguage
                }

                else -> {
                    val systemLang =
                        java.util.Locale
                            .getDefault()
                            .language
                    if (systemLang.isNotEmpty()) systemLang else "en"
                }
            }
        preferences[SettingsKeys.Unified.PRIMARY_LANGUAGE_KEY] = primaryLanguage

        if (existingAppLanguage != null && existingAppLanguage != "system") {
            preferences[SettingsKeys.Unified.APP_LANGUAGE_OVERRIDE_KEY] = "custom:$existingAppLanguage"
        } else {
            preferences[SettingsKeys.Unified.APP_LANGUAGE_OVERRIDE_KEY] = "follow"
        }

        val existingAiLanguage = preferences[SettingsKeys.Assistant.ASSISTANT_LANGUAGE_KEY]
        if (existingAiLanguage != null && existingAiLanguage != primaryLanguage.uppercase()) {
            preferences[SettingsKeys.Unified.AI_LANGUAGE_OVERRIDE_KEY] = "custom:$existingAiLanguage"
        } else {
            preferences[SettingsKeys.Unified.AI_LANGUAGE_OVERRIDE_KEY] = "follow"
        }

        if (existingMarketplaceCountry != null) {
            preferences[SettingsKeys.Unified.MARKETPLACE_COUNTRY_OVERRIDE_KEY] =
                "custom:$existingMarketplaceCountry"
        } else {
            preferences[SettingsKeys.Unified.MARKETPLACE_COUNTRY_OVERRIDE_KEY] = "follow"
        }

        val existingVoiceLanguage = preferences[SettingsKeys.Voice.VOICE_LANGUAGE_KEY]
        if (existingVoiceLanguage != null && existingVoiceLanguage.isNotEmpty()) {
            preferences[SettingsKeys.Unified.TTS_LANGUAGE_SETTING_KEY] =
                "custom:$existingVoiceLanguage"
        } else {
            preferences[SettingsKeys.Unified.TTS_LANGUAGE_SETTING_KEY] = "follow_ai"
        }

        Log.i(
            SETTINGS_DATASTORE_TAG,
            "Unified settings initialized: primary=$primaryCountry/$primaryLanguage",
        )
    }
}
