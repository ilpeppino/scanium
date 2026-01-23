# Scanium Settings Information Architecture

This document inventories every persistent setting in the Android app, notes the original UI
surface, and maps each item to the proposed category in the redesigned Settings experience.

## Inventory

| Setting / Feature                        | Storage (DataStore key or source)                                         | Screen Path (New IA)                                                                | Notes                                                                                                            |
|------------------------------------------|---------------------------------------------------------------------------|-------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| Theme (System / Light / Dark)            | `settings_preferences.theme_mode`                                         | Settings › General › Appearance › Theme                                             | Segmented chips calling `SettingsViewModel.setThemeMode`.                                                        |
| App sounds                               | `settings_preferences.sounds_enabled`                                     | Settings › Notifications & Feedback › Notifications & feedback › Enable sounds      | Switch controls `LocalSoundManager` enablement.                                                                  |
| Auto-save captured photos                | `settings_preferences.auto_save_enabled`                                  | Settings › Camera & Scanning › Storage › Automatically save photos                  | Switch gates directory picker + writes to DataStore.                                                             |
| Save directory (persisted URI)           | `settings_preferences.save_directory_uri`                                 | Settings › Camera & Scanning › Storage › Save location                              | Action row launches SAF and persists URI permissions via `StorageHelper`.                                        |
| Cloud classification toggle              | `settings_preferences.allow_cloud_classification` (FeatureFlagRepository) | Settings › Data & Privacy › Data controls › Cloud classification                    | Primary gateway for backend processing.                                                                          |
| Privacy Safe Mode (composite)            | Derived – flips cloud classification, assistant images, diagnostics       | Settings › Data & Privacy › Data controls › Privacy Safe Mode                       | Quick action that disables all network sharing and re-enables cloud classification when turned off.              |
| Share diagnostics                        | `settings_preferences.share_diagnostics`                                  | Settings › Data & Privacy › Data controls › Share diagnostics                       | Controls telemetry sharing + diagnostics uploads.                                                                |
| Assistant availability                   | `settings_preferences.allow_assistant` (FeatureFlagRepository)            | Settings › AI Assistant › Assistant access › Enable assistant features              | Switch with prerequisite status messaging and connection test action.                                            |
| Allow assistant to send images           | `settings_preferences.allow_assistant_images`                             | Settings › AI Assistant › Assistant access › Send images to assistant               | Only enabled when assistant master switch is on.                                                                 |
| Account &amp; Edition summary            | `billingProvider.entitlementState`, `settings_preferences.developer_mode` | Settings › General › Account &amp; edition › Current edition                        | Displays current edition, expiry date (if available), and upgrade/manage CTAs (routes to paywall).               |
| Assistant personalization – language     | `settings_preferences.assistant_language`                                 | Settings › AI Assistant › Personalization › Language                                | Dropdown of supported ISO codes surfaced as localized labels.                                                    |
| Assistant personalization – tone         | `settings_preferences.assistant_tone`                                     | Settings › AI Assistant › Personalization › Tone                                    | Dropdown mapping enum values (Neutral/Friendly/Professional).                                                    |
| Assistant personalization – region       | `settings_preferences.assistant_region`                                   | Settings › AI Assistant › Personalization › Region                                  | Dropdown (EU, NL, DE, FR, BE, UK, US).                                                                           |
| Assistant personalization – units        | `settings_preferences.assistant_units`                                    | Settings › AI Assistant › Personalization › Units                                   | Dropdown for metric/imperial preference.                                                                         |
| Assistant personalization – verbosity    | `settings_preferences.assistant_verbosity`                                | Settings › AI Assistant › Personalization › Verbosity                               | Dropdown (Concise/Normal/Detailed).                                                                              |
| Voice mode enabled                       | `settings_preferences.voice_mode_enabled`                                 | Settings › AI Assistant › Voice & input › Voice input (microphone)                  | Switch requests RECORD_AUDIO permission when enabling.                                                           |
| Speak answers aloud                      | `settings_preferences.speak_answers_enabled`                              | Settings › AI Assistant › Voice & input › Read assistant replies aloud              | Switch triggers on-device TTS playback.                                                                          |
| Auto-send transcript                     | `settings_preferences.auto_send_transcript`                               | Settings › AI Assistant › Voice & input › Auto-send after dictation                 | Switch only enabled when voice mode is active.                                                                   |
| Voice language override                  | `settings_preferences.voice_language`                                     | Settings › AI Assistant › Voice & input › Voice language                            | Dropdown with “Follow assistant language” option; disabled when voice mode off.                                  |
| Assistant haptics                        | `settings_preferences.assistant_haptics_enabled`                          | Settings › Notifications & Feedback › Notifications & feedback › Assistant haptics  | Switch controlling vibration cues for assistant UI.                                                              |
| Developer mode toggle                    | `settings_preferences.developer_mode`                                     | Settings › Developer Options › Developer Settings › Developer Mode                  | Switch remains gated to debug builds; also surfaces toggle state to show the menu in release if already enabled. |
| Allow screenshots (FLAG_SECURE)          | `settings_preferences.dev_allow_screenshots`                              | Settings › Developer Options › Diagnostics & Security › Allow screenshots           | Switch toggles FLAG_SECURE at runtime.                                                                           |
| Show FTUE debug bounds                   | `settings_preferences.dev_show_ftue_bounds`                               | Settings › Developer Options › First-Time Experience › Show FTUE debug bounds       | Switch overlays tour bounds + centerline.                                                                        |
| Force FTUE tour                          | `ftue_preferences.ftue_force_enabled`                                     | Settings › Developer Options › First-Time Experience › Force First-Time Tour        | Switch stored in FTUE repository.                                                                                |
| Reset FTUE progress                      | `FtueRepository.reset()`                                                  | Settings › Developer Options › First-Time Experience › Reset Tour Progress          | Action row clearing FTUE completion flags.                                                                       |
| Barcode detection toggle                 | `settings_preferences.dev_barcode_detection_enabled`                      | Settings › Developer Options › Detection & Performance › Barcode/QR Detection       | Switch toggles secondary detector.                                                                               |
| Document detection toggle                | `settings_preferences.dev_document_detection_enabled`                     | Settings › Developer Options › Detection & Performance › Document Detection         | Switch for doc candidate extraction.                                                                             |
| Adaptive throttling toggle               | `settings_preferences.dev_adaptive_throttling_enabled`                    | Settings › Developer Options › Detection & Performance › Adaptive Throttling        | Controls throttling heuristics.                                                                                  |
| Classification mode (Cloud vs On-device) | `classification_preferences.classification_mode`                          | Settings › Camera & Scanning › Scanning behavior › Classification mode              | Segmented chips for cloud/on-device inference.                                                                   |
| Low data mode                            | `classification_preferences.low_data_mode`                                | Settings › Camera & Scanning › Scanning behavior › Low data mode                    | Switch spacing cloud requests.                                                                                   |
| Save cloud crops (debug)                 | `classification_preferences.save_cloud_crops`                             | Settings › Developer Options › Classifier Diagnostics › Save cloud crops            | Debug-only switch writing master crops to cache.                                                                 |
| Verbose classifier logging               | `classification_preferences.verbose_logging`                              | Settings › Developer Options › Classifier Diagnostics › Verbose classifier logging  | Switch increases Logcat detail.                                                                                  |
| Capture resolution (Low / Normal / High) | `CameraViewModel.captureResolution` (session memory)                      | Settings › Camera & Scanning › Capture quality › Image resolution                   | Segmented chips updating the shared `CameraViewModel`.                                                           |
| Aggregation similarity threshold         | `ItemsStateManager.similarityThreshold`                                   | Settings › Camera & Scanning › Scanning behavior › Aggregation accuracy             | Segmented chips (Low/Medium/High) feeding `ItemsViewModel`.                                                      |
| Privacy diagnostics / Data usage info    | Static content (`DataUsageScreen`, `PrivacyPolicy`, `Terms`, `About`)     | Settings › Data & Privacy › Data controls › Data usage & transparency / Legal links | Navigation rows lead to dedicated informational screens.                                                         |
| System Health / Diagnostics              | `DiagnosticsRepository`, `AssistantDiagnosticsState`                      | Settings › Developer Options › System Health & Assistant Diagnostics                | Cards show backend/network/permissions plus assistant readiness actions (refresh, copy).                         |
| Keep screen on while scanning            | Derived flag inside `CameraScreen`                                        | Not user-facing                                                                     | Behavior left on camera surface; no toggle in Settings.                                                          |

## Navigation routes

All settings destinations live on dedicated routes inside `NavGraph`:

- `settings/home` → `SettingsHomeScreen`
- `settings/general` → `SettingsGeneralScreen`
- `settings/camera` → `SettingsCameraScreen`
- `settings/assistant` → `SettingsAssistantScreen`
- `settings/feedback` → `SettingsFeedbackScreen`
- `settings/privacy` → `SettingsPrivacyScreen`
- `settings/developer` → `DeveloperOptionsScreen` (visible in debug builds or when Developer Mode is
  already enabled)

Legal/info sub-routes remain (`data_usage`, `privacy`, `terms`, `about`) plus the existing `paywall`
route for upgrades.

### Scattered entry points before redesign

- **SettingsScreen** (single scroll list) – mix of storage, privacy, assistant, legal, developer
  entry.
- **CameraSettingsOverlay** – theme, capture resolution, accuracy threshold, classification mode,
  and debug toggles are only accessible while on the camera surface.
- **DeveloperOptionsScreen** – developer-only toggles + diagnostics (separate route).
- Misc. informational screens: `DataUsageScreen`, `Legal` pages.

The new IA consolidates everything under a cohesive Settings home with category screens:

1. **General** – appearance (theme), sound & potential future language.
2. **Camera & Scanning** – capture preferences, storage, classification mode, aggregation accuracy,
   cloud usage notes.
3. **AI Assistant** – enabling assistant, personalization, voice and feedback controls.
4. **Notifications & Feedback** – sounds (moved from storage), haptics toggles, diagnostics sharing
   prompts.
5. **Data & Privacy** – cloud data controls, privacy safe mode, data usage, legal links.
6. **Developer Options** – gated screen for diagnostics, FTUE tools, debug flags.

Each category receives its own screen with grouped sections, while the Settings home lists
categories (icon + subtitle) as the single entry point from navigation and from the camera “More
Settings” button.
