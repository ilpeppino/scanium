package com.scanium.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.scanium.app.model.AiLanguageChoice
import com.scanium.app.model.FollowOrCustom
import com.scanium.app.model.TtsLanguageChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal class UnifiedSettings(
    private val dataStore: DataStore<Preferences>,
) {
    val primaryRegionCountryFlow: Flow<String> =
        dataStore.data.safeMap(SettingsLocaleHelpers.detectCountryCodeFromLocale()) { preferences ->
            val raw = preferences[SettingsKeys.Unified.PRIMARY_REGION_COUNTRY_KEY]
                ?: SettingsLocaleHelpers.detectCountryCodeFromLocale()
            SettingsLocaleHelpers.normalizeCountryCode(raw)
        }

    suspend fun setPrimaryRegionCountry(countryCode: String) {
        val normalized = SettingsLocaleHelpers.normalizeCountryCode(countryCode)
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Unified.PRIMARY_REGION_COUNTRY_KEY] = normalized
            preferences[SettingsKeys.Unified.MARKETPLACE_COUNTRY_OVERRIDE_KEY] = "custom:$normalized"
        }
    }

    val primaryLanguageFlow: Flow<String> =
        dataStore.data.safeMap("en") { preferences ->
            preferences[SettingsKeys.Unified.PRIMARY_LANGUAGE_KEY] ?: "en"
        }

    suspend fun setPrimaryLanguage(languageTag: String) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Unified.PRIMARY_LANGUAGE_KEY] = languageTag
            val mappedCountry = SettingsLocaleHelpers.mapLanguageToMarketplaceCountry(languageTag)
            preferences[SettingsKeys.Unified.PRIMARY_REGION_COUNTRY_KEY] = mappedCountry
            preferences[SettingsKeys.Unified.MARKETPLACE_COUNTRY_OVERRIDE_KEY] = "follow"
        }
    }

    val appLanguageSettingFlow: Flow<FollowOrCustom<String>> =
        dataStore.data.safeMap(FollowOrCustom.followPrimary<String>()) { preferences ->
            val raw = preferences[SettingsKeys.Unified.APP_LANGUAGE_OVERRIDE_KEY]
            parseFollowOrCustom(raw)
        }

    suspend fun setAppLanguageSetting(setting: FollowOrCustom<String>) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Unified.APP_LANGUAGE_OVERRIDE_KEY] = serializeFollowOrCustom(setting)
        }
    }

    val aiLanguageSettingFlow: Flow<FollowOrCustom<AiLanguageChoice>> =
        dataStore.data.safeMap(FollowOrCustom.followPrimary<AiLanguageChoice>()) { preferences ->
            val raw = preferences[SettingsKeys.Unified.AI_LANGUAGE_OVERRIDE_KEY]
            parseFollowOrCustomAiLanguage(raw)
        }

    suspend fun setAiLanguageSetting(setting: FollowOrCustom<AiLanguageChoice>) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Unified.AI_LANGUAGE_OVERRIDE_KEY] = serializeFollowOrCustomAiLanguage(setting)
        }
    }

    val marketplaceCountrySettingFlow: Flow<FollowOrCustom<String>> =
        dataStore.data.safeMap(FollowOrCustom.followPrimary<String>()) { preferences ->
            val raw = preferences[SettingsKeys.Unified.MARKETPLACE_COUNTRY_OVERRIDE_KEY]
            parseFollowOrCustom(raw)
        }

    suspend fun setMarketplaceCountrySetting(setting: FollowOrCustom<String>) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Unified.MARKETPLACE_COUNTRY_OVERRIDE_KEY] =
                serializeFollowOrCustom(setting)
        }
    }

    val ttsLanguageSettingFlow: Flow<TtsLanguageChoice> =
        dataStore.data.safeMap(TtsLanguageChoice.FollowAiLanguage) { preferences ->
            val raw = preferences[SettingsKeys.Unified.TTS_LANGUAGE_SETTING_KEY]
            parseTtsLanguageChoice(raw)
        }

    suspend fun setTtsLanguageSetting(setting: TtsLanguageChoice) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.Unified.TTS_LANGUAGE_SETTING_KEY] = serializeTtsLanguageChoice(setting)
        }
    }

    val lastDetectedSpokenLanguageFlow: Flow<String?> =
        dataStore.data.safeMap(null as String?) { preferences ->
            preferences[SettingsKeys.Unified.LAST_DETECTED_SPOKEN_LANGUAGE_KEY]
        }

    suspend fun setLastDetectedSpokenLanguage(languageTag: String?) {
        dataStore.edit { preferences ->
            if (languageTag != null) {
                preferences[SettingsKeys.Unified.LAST_DETECTED_SPOKEN_LANGUAGE_KEY] = languageTag
            } else {
                preferences.remove(SettingsKeys.Unified.LAST_DETECTED_SPOKEN_LANGUAGE_KEY)
            }
        }
    }

    val effectiveAppLanguageFlow: Flow<String> = primaryLanguageFlow

    val effectiveAiOutputLanguageFlow: Flow<String> = effectiveAppLanguageFlow

    val effectiveMarketplaceCountryFlow: Flow<String> =
        combine(primaryRegionCountryFlow, marketplaceCountrySettingFlow) { primary, setting ->
            setting.resolve(primary)
        }

    val effectiveTtsLanguageFlow: Flow<String> = effectiveAppLanguageFlow

    fun mapLanguageToMarketplaceCountry(languageTag: String): String {
        return SettingsLocaleHelpers.mapLanguageToMarketplaceCountry(languageTag)
    }

    private fun parseFollowOrCustom(raw: String?): FollowOrCustom<String> {
        return when {
            raw == null || raw == "follow" -> FollowOrCustom.followPrimary()
            raw.startsWith("custom:") -> FollowOrCustom.custom(raw.removePrefix("custom:"))
            else -> FollowOrCustom.followPrimary()
        }
    }

    private fun serializeFollowOrCustom(setting: FollowOrCustom<String>): String {
        return when (setting) {
            is FollowOrCustom.FollowPrimary -> "follow"
            is FollowOrCustom.Custom -> "custom:${setting.value}"
        }
    }

    private fun parseFollowOrCustomAiLanguage(raw: String?): FollowOrCustom<AiLanguageChoice> {
        return when {
            raw == null || raw == "follow" -> FollowOrCustom.followPrimary()
            raw == "custom:auto_detect" -> FollowOrCustom.custom(AiLanguageChoice.AutoDetect)
            raw.startsWith("custom:") -> {
                val tag = raw.removePrefix("custom:")
                FollowOrCustom.custom(AiLanguageChoice.LanguageTag(tag))
            }
            else -> FollowOrCustom.followPrimary()
        }
    }

    private fun serializeFollowOrCustomAiLanguage(setting: FollowOrCustom<AiLanguageChoice>): String {
        return when (setting) {
            is FollowOrCustom.FollowPrimary -> "follow"
            is FollowOrCustom.Custom -> {
                when (val choice = setting.value) {
                    is AiLanguageChoice.AutoDetect -> "custom:auto_detect"
                    is AiLanguageChoice.LanguageTag -> "custom:${choice.tag}"
                }
            }
        }
    }

    private fun parseTtsLanguageChoice(raw: String?): TtsLanguageChoice {
        return when {
            raw == null || raw == "follow_ai" -> TtsLanguageChoice.FollowAiLanguage
            raw == "follow_primary" -> TtsLanguageChoice.FollowPrimary
            raw.startsWith("custom:") -> TtsLanguageChoice.Custom(raw.removePrefix("custom:"))
            else -> TtsLanguageChoice.FollowAiLanguage
        }
    }

    private fun serializeTtsLanguageChoice(setting: TtsLanguageChoice): String {
        return when (setting) {
            is TtsLanguageChoice.FollowAiLanguage -> "follow_ai"
            is TtsLanguageChoice.FollowPrimary -> "follow_primary"
            is TtsLanguageChoice.Custom -> "custom:${setting.languageTag}"
        }
    }
}
