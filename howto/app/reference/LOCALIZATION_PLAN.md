***REMOVED*** Localization Plan

***REMOVED******REMOVED*** Current State (Discovery)

- The in-app language row lives in
  `androidApp/src/main/java/com/scanium/app/ui/settings/SettingsGeneralScreen.kt` and uses
  `SettingDropdownRow` with `enabled = true`.
- App language is already persisted via DataStore in
  `androidApp/src/main/java/com/scanium/app/data/SettingsRepository.kt` (`app_language`) and exposed
  through `SettingsViewModel`.
- Locale changes are applied in `SettingsViewModel.setAppLanguage(...)`, but the startup locale
  application in `androidApp/src/main/java/com/scanium/app/ScaniumApplication.kt` is currently
  nested under the Sentry DSN branch; if Sentry is disabled, the stored locale will not be applied
  on launch.
- `androidApp/src/main/res/xml/locales_config.xml` only lists `en`, `it`, `fr`, `nl` and needs
  `de` + `pt-BR` added.
- Translations currently exist for `it`, `fr`, `nl` only and cover the existing
  `values/strings.xml`, but many UI strings remain hardcoded in Kotlin and are not localized.

***REMOVED******REMOVED*** Planned Changes

1. **Model & persistence**
    - Expand `AppLanguage` to the required values: `SYSTEM`, `EN`, `IT`, `FR`, `NL`, `DE`, `PT_BR`.
    - Persist using existing `app_language` DataStore key (default `SYSTEM`).

2. **Apply locale (AndroidX AppCompat)**
    - Apply stored locale on app start for all builds by moving the `appLanguageFlow` collection
      outside the Sentry-only block.
    - Apply immediately on selection in `SettingsViewModel` (keep current behavior).

3. **Settings UI**
    - Ensure the app language row is fully enabled and includes all supported languages.
    - Update the description to “Choose the language used in the app.”

4. **Locales & translations**
    - Update `locales_config.xml` to include `de` and `pt-BR`.
    - Add `values-de` and `values-pt-rBR` resources.
    - Replace hardcoded UI strings with string resources and translate them across `it`, `fr`, `nl`,
      `de`, `pt-BR`.

5. **Testing**
    - Add a unit test for `SettingsRepository` language persistence.
    - Run `./gradlew :androidApp:assembleDebug --no-daemon` and `./gradlew test --no-daemon`.
