# Android App HOWTO

## 1. Overview

The Scanium Android app is a camera-first scanner that detects items, aggregates stable detections, and helps users produce export-ready listings (CSV/ZIP) with optional cloud enrichment. The core flow is:

scan -> recognize -> aggregate -> price -> export

Primary user flows are implemented in Compose screens defined in `androidApp/src/main/java/com/scanium/app/navigation/NavGraph.kt` and driven by ViewModels (MVVM).

Supported modes (from `core-models/src/main/java/com/scanium/app/camera/ScanMode.kt` and `core-models/src/main/java/com/scanium/app/ml/classification/ClassificationMode.kt`):

- Scan modes: object detection, barcode, document text.
- Classification modes: on-device vs cloud (user-selectable).
- Capture modes: tap to capture, long-press to start continuous scanning (`androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt`).

## 2. High-Level Architecture

Modules and packages used by the Android app:

- UI/feature layer: `androidApp/` (Compose UI, ViewModels, navigation, settings).
- Platform scanning: `androidApp/src/main/java/com/scanium/app/camera/` (CameraXManager, overlays, scan UI).
- ML plumbing: `androidApp/src/main/java/com/scanium/app/ml/` (ObjectDetectorClient, BarcodeDetectorClient, DocumentTextRecognitionClient).
- Platform adapters: `android-platform-adapters/` (Bitmap/Rect -> ImageRef/NormalizedRect).
- Shared models/tracking: `shared/core-models/`, `shared/core-tracking/` (KMP-friendly models and tracking).
- Domain taxonomy: `core-domainpack/` (DomainPackRepository, BasicCategoryEngine).
- Aggregation and dedupe: `core-tracking/` (Android typealiases) and `androidApp/src/main/java/com/scanium/app/aggregation/`.

Key layers and responsibilities:

- UI + navigation: Compose screens in `androidApp/src/main/java/com/scanium/app/` and `NavGraph.kt`.
- ViewModels/state: `ItemsViewModel`, `CameraViewModel`, `ClassificationModeViewModel` (MVVM with StateFlow).
- Platform scanning: `CameraXManager` wires CameraX to ML Kit detectors.
- Tracking/aggregation: `ObjectTracker` + `ItemAggregator` merge detections into stable items.
- Domain mapping: `DomainPackRepository` and `BasicCategoryEngine` map classification to domain categories.

## 3. Camera & Scanning Pipeline

Core pipeline components and flow:

- Camera setup: `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt` binds `Preview`, `ImageAnalysis`, and `ImageCapture` using CameraX.
- Analysis configuration: `ImageAnalysis` uses `STRATEGY_KEEP_ONLY_LATEST` and a 1280x720 target resolution for ML throughput.
- Analyzer routing: `DetectionRouter` (`androidApp/src/main/java/com/scanium/app/camera/detection/DetectionRouter.kt`) decides which detector to run and enforces throttling.
- Object detection: `ObjectDetectorClient` wraps ML Kit Object Detection (STREAM for scanning, SINGLE_IMAGE for captures).
- Barcode detection: `BarcodeDetectorClient` (ML Kit barcode scanning).
- Document scanning: `DocumentTextRecognitionClient` (ML Kit text recognition).

Frame lifecycle and throttling:

- `CameraXManager.startCameraSession()` creates a new detection scope and resets trackers on resume.
- `DetectionRouter` throttles per detector type and supports adaptive throttling for low-power mode.
- When a frame is skipped or throttled, the `ImageProxy` is closed to avoid backpressure.

Geometry filtering and crop behavior:

- `ObjectDetectorClient` applies edge gating using `ImageProxy.cropRect` to drop partial edge detections (see `EDGE_INSET_MARGIN_RATIO` in `CameraXManager` and `ObjectDetectorClient` safe-zone checks).
- ML Kit does not honor cropRect for analysis, so the cropRect is used for filtering and overlay mapping rather than as an analysis input.

## 4. Tracking, Deduplication & Aggregation

Tracking and aggregation are split between frame-level tracking and session-level aggregation:

- Frame-level tracking: `ObjectTracker` in `CameraXManager` uses `TrackerConfig` to match candidates across frames.
- Session-level aggregation: `ItemAggregator` in `androidApp/src/main/java/com/scanium/app/aggregation/ItemAggregator.kt` merges similar detections into stable `AggregatedItem`s.
- State coordination: `ItemsStateManager` (`androidApp/src/main/java/com/scanium/app/items/state/ItemsStateManager.kt`) applies aggregation results and publishes the item list via StateFlow.
- Dedupe for barcode/document: `DedupeHelper` in `DetectionRouter` filters repeated barcode/document hits within a time window.

Conceptually:

- Raw detections are converted to `ScannedItem`s and `DetectionResult`s (for overlays).
- Object tracking stabilizes detections and reduces flicker.
- Aggregation merges multiple detections of the same physical item into one `AggregatedItem` for list/export/classification.

## 5. UI & UX

Navigation and screens (see `androidApp/src/main/java/com/scanium/app/navigation/NavGraph.kt`):

- `CameraScreen`: main entry point with scan modes, shutter control, overlays.
- `ItemsListScreen` and `EditItemsScreen`: list and edit aggregated items.
- `AssistantScreen`: export assistant chat UI.
- Settings screens: `Settings*Screen` and `DeveloperOptionsScreen`.

Camera overlays:

- `CameraGuidanceOverlay`, `DocumentScanOverlay`, and `DocumentAlignmentOverlay` for scanning guidance and document detection cues.
- Debug overlays for diagnostics and detection boxes are toggled via settings (see `SettingsRepository`).

Controls and debug features:

- Developer Options (dev flavor only) expose document detection, adaptive throttling, ROI diagnostics, and pipeline debug.
- Camera settings and scan modes are available from the camera UI (see `CameraScreen.kt`).

Accessibility considerations:

- Compose semantics/test tags are defined in `androidApp/src/main/java/com/scanium/app/testing/TestSemantics.kt` for UI testing.
- No explicit accessibility audit is documented in the repo; treat accessibility requirements as TBD.

## 6. Configuration & Feature Flags

Build-time configuration is injected via BuildConfig fields in `androidApp/build.gradle.kts`:

- `SCANIUM_API_BASE_URL`, `SCANIUM_API_KEY`: backend base URL and API key.
- `OTLP_ENDPOINT`, `OTLP_ENABLED`: mobile OTLP telemetry export.
- `SENTRY_DSN`: crash reporting via Sentry.
- `SCANIUM_API_CERTIFICATE_PIN`: optional TLS pin for backend.
- Flavor flags: `DEV_MODE_ENABLED`, `FEATURE_AI_ASSISTANT`, `FEATURE_ITEM_DIAGNOSTICS`.

Runtime settings are stored in `SettingsRepository` and used by `CameraScreen` and ViewModels:

- Detection toggles: barcode/document detection, adaptive throttling, overlays.
- Scan guidance and ROI diagnostics.
- Classification preferences and cloud-allowed toggle.

Remote config:

- `AndroidRemoteConfigProvider` (`androidApp/src/main/java/com/scanium/app/data/AndroidRemoteConfigProvider.kt`) uses `/v1/config` to fetch remote config.

## 7. Interaction With Backend

Backend calls are optional and gated by configuration. Primary integration points:

- Cloud classification: `CloudClassifier` -> `POST /v1/classify?enrichAttributes=true` (multipart image + domainPackId).
- Assistant chat: `AssistantRepository` -> `POST /v1/assist/chat` (JSON or multipart with images).
- Assistant warmup: `AssistantPreflight` -> `POST /v1/assist/warmup`.
- Vision insights: `VisionInsightsRepository` -> `POST /v1/vision/insights` (multipart image).
- Enrichment pipeline: `EnrichmentRepository` -> `POST /v1/items/enrich`, then poll `GET /v1/items/enrich/status/:requestId`.
- Remote config: `GET /v1/config` with `X-API-Key`.

Failure handling:

- Cloud classification and assistant calls treat 4xx as non-retryable and 5xx/429/timeouts as retryable (`CloudClassifier`, `AssistantRepository`).
- All backend integrations degrade gracefully when base URL or API key is missing (local-only behavior remains available).

Known gap:

- `HealthCheckRepository` checks `/v1/preflight` and `/v1/assist/status` (`androidApp/src/main/java/com/scanium/app/monitoring/HealthCheckRepository.kt`), but these endpoints are not present in the backend codebase. Treat those checks as best-effort until backend routes exist.

## 8. Interaction With Monitoring

Mobile telemetry uses OTLP over HTTP when enabled:

- OTLP export is configured in `ScaniumApplication` via `OtlpConfiguration` and `Telemetry` facade.
- Logs/metrics/traces are exported to `${OTLP_ENDPOINT}/v1/{logs,metrics,traces}`.
- Trace context is propagated to backend requests via `TraceContextInterceptor`.

Crash reporting:

- `AndroidCrashPortAdapter` bridges the CrashPort to Sentry, attaching diagnostics bundles when enabled.

Deprecated path:

- `MobileTelemetryClient` (Option C) is deprecated in `ScaniumApplication`; OTLP is the primary pipeline.

## 9. Build & Run Notes

Local run (Android Studio / device):

- Use `./gradlew :androidApp:assembleDevDebug` to build the dev APK.
- Configure `local.properties` with:
  - `scanium.api.base.url` and `scanium.api.base.url.debug`
  - `scanium.api.key`
  - `scanium.otlp.endpoint` and `scanium.otlp.enabled`

Common pitfalls:

- Missing `scanium.api.base.url` or API key disables cloud classification and assistant features.
- OTLP endpoint must be reachable from the device (`10.0.2.2` for emulator, LAN IP for physical device).
- `/v1/assist/warmup` must be routed through the backend (Cloudflare or LAN) for assistant preflight to succeed.
