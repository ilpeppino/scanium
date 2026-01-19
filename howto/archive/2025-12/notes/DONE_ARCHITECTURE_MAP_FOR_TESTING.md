***REMOVED*** Architecture Map for Testing

***REMOVED******REMOVED*** Summary

- **App shell**: `MainActivity` → `ScaniumApp` wires Compose, theming, ItemsViewModel,
  Settings/Developer stacks, OTLP telemetry (
  `androidApp/src/main/java/com/scanium/app/MainActivity.kt`, `.../ScaniumApp.kt`,
  `.../ScaniumApplication.kt`).
- **Navigation**: centralized in `androidApp/src/main/java/com/scanium/app/navigation/NavGraph.kt`
  with routes defined in `Routes`. Start destination is `camera`, secondary flows include items
  list, selling, assistant, settings, developer options, TOS/Privacy, paywall.
- **Data boundaries**:
    - `androidApp/` – Compose UI, platform services (CameraX, permissions, telephony).
    - `shared/` – Kotlin Multiplatform models (`shared/core-models`), tracking, telemetry shells,
      export logic.
    - `core-*` + `android-*` modules – adapters/pipelines (CameraX bindings, ML, contracts).
    - `backend/` – Node/TypeScript service with `/health`, `/classify`, `/assist/chat` endpoints,
      configured by `SCANIUM_API_BASE_URL`/`.env -> PUBLIC_BASE_URL`.
- **State sources**: Compose screens bind to ViewModels (ItemsViewModel,
  ClassificationModeViewModel, Settings/Developer viewmodels, AssistantViewModel) which in turn use
  repositories (ScannedItemRepository, ListingDraftRepository, DiagnosticsRepository,
  SettingsRepository, AssistantRepository, CloudClassifier).
- **Test surface**: key hooks exist via DataStore flags (voice toggles, feature gating), Diagnostics
  API (System Health), classification preferences, FTUE repository, Items database.

***REMOVED******REMOVED*** Modules & Responsibilities

| Module               | Path                                                               | Notes relevant to testing                                                                                                                        |
|----------------------|--------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| App shell            | `androidApp/`                                                      | Compose screens, Navigation graph, ViewModels, CameraX integration, FTUE, Settings, Developer Options, AI assistant UI, Telemetry bootstrapping. |
| KMP shared models    | `shared/core-models`, `shared/core-export`, `shared/core-tracking` | Item/domain/assistant models reused across Android + iOS; no Android dependencies.                                                               |
| Camera adapters      | `android-camera-camerax`, `android-platform-adapters`              | Provide Bitmap↔ImageRef, Rect conversions, CameraX pipeline wrappers.                                                                            |
| Backend service      | `backend/`                                                         | Node server with `/health`, `/classify`, `/assist/chat`, eBay helpers. Used for diagnostics + remote classification.                             |
| Supporting libraries | `core-domainpack`, `core-tracking`, `core-contracts`, `app/`       | Domain pack resolution, telemetry contracts, etc. Useful for stubbing.                                                                           |

***REMOVED******REMOVED*** Key Entry Points & Screens

| Screen / Component                                         | Path                                                                                                             | Primary ViewModel / State                                                                  | Notable interactions                                                                                                                             |
|------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| MainActivity                                               | `androidApp/src/main/java/com/scanium/app/MainActivity.kt`                                                       | Observes `SettingsRepository.themeModeFlow` for theme                                      | Hosts `ScaniumApp()`                                                                                                                             |
| ScaniumApp / NavGraph                                      | `.../ScaniumApp.kt`, `.../navigation/NavGraph.kt`                                                                | ItemsViewModel, ClassificationModeViewModel, SettingsViewModel, PaywallViewModel           | Creates nav controller, wires flows, obtains TourViewModel.                                                                                      |
| CameraScreen                                               | `androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt`                                                | `ItemsViewModel`, `ClassificationModeViewModel`, `CameraViewModel`, `CameraXManager` state | Handles permissions, FTUE overlays, scan modes, capture gestures, settings overlay (`CameraSettingsOverlay.kt`), keep-awake logic, preview.      |
| ItemsListScreen                                            | `androidApp/src/main/java/com/scanium/app/items/ItemsListScreen.kt`                                              | `ItemsViewModel`                                                                           | Selection, swipe actions, overflow menu, assistant entry, long-press `DraftPreviewOverlay`.                                                      |
| SellOnEbayScreen / DraftReviewScreen / PostingAssistScreen | `androidApp/src/main/java/com/scanium/app/selling/ui/*.kt`                                                       | `ItemsViewModel`, `ListingDraftStore`                                                      | Listing composer, actions triggered by assistant suggestions.                                                                                    |
| AssistantScreen (selling)                                  | `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantScreen.kt`                                  | `AssistantViewModel`, `AssistantVoiceController`                                           | Chat log, pending actions, voice UI, IME behavior.                                                                                               |
| Assistant voice/controller                                 | `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantVoiceController.kt`, `VoiceStateMachine.kt` | Internal MutableStateFlows                                                                 | STT/TTS state machine, microphone/TTS lifecycle.                                                                                                 |
| Assistant backend repo                                     | `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantRepository.kt`                              | Uses `BuildConfig.SCANIUM_API_BASE_URL`                                                    | POST `/assist/chat`, handles offline fallback, correlation IDs.                                                                                  |
| SettingsScreen                                             | `androidApp/src/main/java/com/scanium/app/ui/settings/SettingsScreen.kt`                                         | `SettingsViewModel`                                                                        | Theme toggle, cloud/assistant toggles, voice toggles, navigation to developer/data usage/legal.                                                  |
| DeveloperOptionsScreen                                     | `androidApp/src/main/java/com/scanium/app/ui/settings/DeveloperOptionsScreen.kt`                                 | `DeveloperOptionsViewModel`, `DiagnosticsRepository`                                       | System health, backend ping, permissions audit, capability list, FTUE force toggles, crash tests.                                                |
| DiagnosticsRepository                                      | `androidApp/src/main/java/com/scanium/app/diagnostics/DiagnosticsRepository.kt`                                  | Emits `DiagnosticsState`                                                                   | Checks `/health`, network transport, permission status (Camera/Microphone/Storage), capability detection (SpeechRecognizer, TTS, Camera facing). |
| FTUE                                                       | `androidApp/src/main/java/com/scanium/app/ftue/TourViewModel.kt`, `FtueRepository.kt`                            | `TourViewModel` (step state), DataStore booleans                                           | Controls spotlight overlays on CameraScreen; developer option can force/resets.                                                                  |

***REMOVED******REMOVED*** Key ViewModels & State Sources

| ViewModel                    | Path                                                                                | Provides                                                                                                            | Used by                                                       |
|------------------------------|-------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------|
| ItemsViewModel               | `androidApp/src/main/java/com/scanium/app/items/ItemsViewModel.kt`                  | `items: StateFlow<List<ScannedItem>>`, `selectedItemIds`, `overlayTracks`, `similarityThreshold`, `itemAddedEvents` | Camera overlay, Items list, Assistant context, selling flows. |
| CameraViewModel              | `androidApp/src/main/java/com/scanium/app/camera/CameraViewModel.kt`                | `captureResolution`, recording state                                                                                | CameraScreen + settings overlay.                              |
| ClassificationModeViewModel  | `androidApp/src/main/java/com/scanium/app/settings/ClassificationModeViewModel.kt`  | toggles for cloud usage, low data mode, verbose logging, save cloud crops                                           | Camera settings overlay, diagnostics tags.                    |
| SettingsViewModel            | `androidApp/src/main/java/com/scanium/app/ui/settings/SettingsViewModel.kt`         | Theme, auto-save, assistant prefs, voice toggles, privacy safe mode, share diagnostics, FTUE force flag             | SettingsScreen, Developer gating, Telemetry.                  |
| DeveloperOptionsViewModel    | `androidApp/src/main/java/com/scanium/app/ui/settings/DeveloperOptionsViewModel.kt` | `diagnosticsState`, developer mode toggle, copy status, FTUE reset, crash triggers                                  | DeveloperOptionsScreen.                                       |
| AssistantViewModel (selling) | `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantViewModel.kt`  | Chat entries, pending actions, loading stages, suggested questions, events                                          | AssistantScreen.                                              |
| TourViewModel                | `androidApp/src/main/java/com/scanium/app/ftue/TourViewModel.kt`                    | `currentStep`, `isTourActive`, `targetBounds`                                                                       | CameraScreen overlays.                                        |
| PaywallViewModel / Billing   | `androidApp/src/main/java/com/scanium/app/billing/ui/PaywallViewModel.kt`           | Entitlement gating                                                                                                  | Settings → Upgrade path.                                      |

***REMOVED******REMOVED*** Repositories & Services

| Repository / Service                   | Path                                                                                | Purpose                                                                                                |
|----------------------------------------|-------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| SettingsRepository                     | `androidApp/src/main/java/com/scanium/app/data/SettingsRepository.kt`               | DataStore for theme, cloud classification, assistant toggles, developer mode, voice mode, FTUE resets. |
| ScannedItemRepository & Database       | `androidApp/src/main/java/com/scanium/app/items/persistence/*.kt`                   | Room database for scanned items, listing drafts.                                                       |
| ListingDraftRepository / Store         | `androidApp/src/main/java/com/scanium/app/selling/persistence/*.kt`                 | Draft persistence used by selling + assistant actions.                                                 |
| ListingDraftStore interface            | `androidApp/src/main/java/com/scanium/app/selling/persistence/ListingDraftStore.kt` | Abstraction for persistence; tests can inject fake store.                                              |
| EbayMarketplaceService + MockEbayApi   | `androidApp/src/main/java/com/scanium/app/selling/data/*.kt`                        | Marketplace calls, stubbed with local mock.                                                            |
| CloudClassifier                        | `androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifier.kt`     | Sends POST `{SCANIUM_API_BASE_URL}/classify`; uses Retrofit-style client; handles queue + metrics.     |
| OnDeviceClassifier / StableItemCropper | `androidApp/src/main/java/com/scanium/app/ml/classification/*.kt`                   | Local inference pipeline.                                                                              |
| AssistantRepository                    | `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantRepository.kt` | Builds requests to `/assist/chat`, handles allow-images gating, connectivity errors.                   |
| DiagnosticsRepository                  | `androidApp/src/main/java/com/scanium/app/diagnostics/DiagnosticsRepository.kt`     | `/health` ping, network/permission/capability snapshots.                                               |
| AndroidRemoteConfigProvider            | `androidApp/src/main/java/com/scanium/app/data/AndroidRemoteConfigProvider.kt`      | Caches remote config from backend for gating features.                                                 |
| FtueRepository                         | `androidApp/src/main/java/com/scanium/app/ftue/FtueRepository.kt`                   | DataStore for onboarding flags and permission education displays.                                      |

***REMOVED******REMOVED*** Camera Pipeline Pointers

- `CameraScreen.kt` orchestrates CameraX via `CameraXManager` (
  `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt`), binding Preview +
  ImageAnalysis + ImageCapture.
- `CameraViewModel` stores capture resolution, tied to the `CameraSettingsOverlay`.
- `ClassificationModeViewModel` toggles (cloud/on-device) feed into classification pipeline.
- `ItemsViewModel.overlayTracks` drives detection overlay (RectF with Compose `Canvas`).
- FTUE overlays computed in `tourTarget` extensions highlight UI controls; tests should assert
  overlay coordinates.

***REMOVED******REMOVED*** Items List Model

- `ScannedItem` model in `androidApp/src/main/java/com/scanium/app/items/ScannedItem.kt` (imports
  from shared models).
- Repository persists to Room; `ItemsViewModel` exposes `items` flow sorted by last update.
- Long-press preview uses `DraftPreviewOverlay.kt` and `ItemsListScreen` state (`previewItem`,
  `previewBounds`).
- Swipe actions implemented via `Modifier.swipeable` hooking into `ItemsListScreen`.
- Assistant entry button anchored bottom-right (floating action button).

***REMOVED******REMOVED*** Settings & DataStore Keys (Testing Focus)

| Preference Key                                           | Default                                                                                            | Notes / Tests                                     |
|----------------------------------------------------------|----------------------------------------------------------------------------------------------------|---------------------------------------------------|
| `theme_mode`                                             | System                                                                                             | Drives Compose theme in `MainActivity`.           |
| `allow_cloud_classification`                             | true                                                                                               | Enables remote classifier + telemetry tags.       |
| `allow_assistant`                                        | false                                                                                              | Feature flag for assistant availability.          |
| `share_diagnostics`                                      | false                                                                                              | Controls crash/telemetry in `ScaniumApplication`. |
| `developer_mode`                                         | false                                                                                              | Unlocks developer-only toggles.                   |
| `auto_save_enabled`                                      | false                                                                                              | Controls background photo saves.                  |
| `save_directory_uri`                                     | null                                                                                               | Document picker selection.                        |
| `allow_assistant_images`                                 | false                                                                                              | When true, assistant sends thumbnails.            |
| `assistant_language`/`tone`/`region`/`units`/`verbosity` | EN / NEUTRAL / EU / METRIC / NORMAL                                                                | Consumed by assistant payload.                    |
| `voice_mode_enabled`                                     | false                                                                                              | Shows mic button and voice toggle.                |
| `speak_answers_enabled`                                  | false                                                                                              | Enables TTS.                                      |
| `auto_send_transcript`                                   | false                                                                                              | Auto-send after STT completes.                    |
| `voice_language`                                         | "" (follow assistant)                                                                              | STT/TTS locale override.                          |
| Privacy Safe Mode composite                              | derived                                                                                            | Ensures sensitive toggles off.                    |
| FTUE flags (`permissionEducationShown`, `tourCompleted`) | stored via `FtueRepository` file `androidApp/src/main/java/com/scanium/app/ftue/FtueRepository.kt` | Used by developer option to reset.                |

***REMOVED******REMOVED*** Navigation Routes (from `Routes`)

| Route                                     | Destination                                         |
|-------------------------------------------|-----------------------------------------------------|
| `camera`                                  | `CameraScreen`                                      |
| `items_list`                              | `ItemsListScreen`                                   |
| `sell_on_ebay/{itemIds}`                  | `SellOnEbayScreen`                                  |
| `draft_review` / `draft_review/{itemId}`  | `DraftReviewScreen`                                 |
| `posting_assist`                          | `PostingAssistScreen`                               |
| `assistant`                               | `com.scanium.app.selling.assistant.AssistantScreen` |
| `settings`                                | `SettingsScreen`                                    |
| `developer_options`                       | `DeveloperOptionsScreen`                            |
| `data_usage`, `privacy`, `terms`, `about` | Legal/Transparency screens                          |
| `paywall`                                 | `PaywallScreen`                                     |

***REMOVED******REMOVED*** Backend & Diagnostics

- **Base URL**: `BuildConfig.SCANIUM_API_BASE_URL` (populated from `local.properties` keys
  `scanium.api.base.url`). Empty string disables network operations and surfaces alerts in Camera
  settings + Diagnostics.
- **Assistant endpoint**: `POST {BASE_URL}/assist/chat` (see `AssistantRepository`).
- **Classifier endpoint**: `POST {BASE_URL}/classify` (see `CloudClassifier`).
- **Health check**: `GET {BASE_URL}/health` triggered by `DiagnosticsRepository`.
- **Telemetry**: `ScaniumApplication` wires OTLP + Sentry (enabled when DSNs present). Developer
  runbook should verify share-diagnostics toggles.

***REMOVED******REMOVED*** Suggested Automation Tags / Hooks

| Screen                       | Suggested `testTag` / Semantics label ideas                                                                                                                       |
|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| CameraScreen                 | `testTag("cameraPreview")`, `testTag("shutterButton")`, `testTag("modeToggle-items")`, `testTag("settingsDrawer")`, `testTag("ftue-spotlight-{step}")`            |
| ItemsListScreen              | `testTag("items-list")`, `testTag("item-card-{id}")`, `testTag("fab-assistant")`, `testTag("action-sell")`, `testTag("swipe-undo")`, `testTag("preview-overlay")` |
| AssistantScreen              | `testTag("assistant-chat-list")`, `testTag("assistant-input")`, `testTag("voice-button")`, `testTag("tt-speaking-chip")`, `testTag("pending-action-card")`        |
| SettingsScreen               | `testTag("toggle-theme-light")`, `testTag("toggle-cloud-classification")`, `testTag("toggle-voice-mode")`, `testTag("list-item-developer-options")`               |
| DeveloperOptionsScreen       | `testTag("system-health-card")`, `testTag("diagnostics-refresh")`, `testTag("force-ftue-switch")`, `testTag("copy-diagnostics")`                                  |
| FTUE overlays                | `testTag("tour-overlay-step-{index}")` for verifying highlight placement.                                                                                         |
| Sell on eBay / PostingAssist | `testTag("sell-dialog")`, `testTag("listing-field-title")`, `testTag("error-banner-backend")`.                                                                    |

When adding instrumentation, prefer `Modifier.semantics { testTag = "..." }` on interactive
controls. Compose lists (LazyColumn) should expose stable keys from item IDs for automation.

***REMOVED******REMOVED*** Notes for Test Authors

- Camera permissions & microphone gating live inside CameraScreen and Assistant voice toggles; tests
  should be aware of DataStore state (use SettingsRepository or developer toggles to preconfigure).
- Items database can be seeded via `ScannedItemRepository` test helpers or by capturing sample
  frames.
- Developer diagnostics provide canonical place to fetch backend/permission status; use this instead
  of ad-hoc logging checks.
- FTUE/Tour: `FtueRepository` exposes flows; developer option toggles `forceFtueTour` so automation
  can force overlay screens.
