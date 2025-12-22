# Scanium – Codex Context

## 1) Project Snapshot (10–15 lines)
- Camera-first Android app (Jetpack Compose) that scans objects live and shows estimated EUR price ranges using on-device ML Kit.
- Primary flows: continuous camera scanning, item list management, optional mock selling to eBay-like flow.
- Scan modes: object detection, barcode/QR, document text OCR; tap-to-capture and long-press continuous scanning.
- Privacy: detection/pricing runs on-device; optional cloud classification is supported for enhanced labels.
- Offline-first and low-latency expectations; keep camera FPS stable and overlays responsive.
- Target devices: Android API 24+ with CameraX; price demo for EU resale market.

## 2) Current Architecture (very compact)
- Simplified MVVM: Compose UI screens → `ItemsViewModel` (shared state) → camera/ml/tracking/domain/selling layers.
- State lives in `StateFlow`; UI collects via `collectAsState()`; `ItemsViewModel` aggregates detections and exposes item list/state.
- Camera side: `CameraScreen` hosts preview/overlays; `CameraXManager` drives Capture/Analysis pipelines with scan mode routing.
- ML layer wraps ML Kit (`ObjectDetectorClient`, `BarcodeScannerClient`, `DocumentTextRecognitionClient`) plus pricing and logging helpers.
- Tracking via `ObjectTracker` + session aggregation to de-dup frames; results feed back into `ItemsViewModel`.
- Navigation in `navigation/NavGraph.kt`; app entry in `MainActivity` → `ScaniumApp` composable.
- Scan mode routing is driven by `ScanMode` enum; camera manager selects the appropriate analyzer (objects, barcodes, text) and pipes detections through tracker/aggregator.

## 3) “Shared Brain” Target for iOS/KMP
- Goal: extract platform-neutral logic for future KMP/iOS: detection session orchestration, tracking pipeline, aggregation/dedup, pricing, domain pack engine/contracts.
- Shared pieces: session state machines, tracking heuristics, aggregation thresholds, domain pack parsing/selection, category mapping, and business rules around selling workflow state.
- Platform-specific keepers: CameraX pipeline, ML Kit calls and image data types, Android permissions, Compose UI, haptics/audio, file access; iOS would swap native camera/ML wrappers and UI.
- Domain pack JSON/config and selection interfaces should be portable; per-platform only the IO/loaders differ.

## 4) Critical Invariants (must not break)
- De-dup rules: prefer ML Kit `trackingId`; fallback to spatial/IoU matching; `ItemsViewModel` aggregation/session dedupe prevents duplicate items when tracking IDs change.
- Tracker reset: reset/clear candidates when scan mode changes, scanning stops, or starting a new session to avoid stale matches.
- Domain pack contracts: config-driven taxonomy; `domainCategoryId` optional/non-breaking—existing ItemCategory consumers must continue working if field is absent.
- Session aggregation thresholds should remain tuned for continuous scanning (REALTIME preset); changing them requires re-validating UX and tests.

## 5) Key Files Map (paths only)
### Entry points
- `androidApp/src/main/java/com/scanium/app/MainActivity.kt` – Android entry; sets up Compose host.
- `androidApp/src/main/java/com/scanium/app/ScaniumApp.kt` – Root composable + navigation wiring.
- `androidApp/src/main/java/com/scanium/app/navigation/NavGraph.kt` – Navigation destinations/routes.

### Camera
- `androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt` – Compose camera UI with overlays.
- `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt` – CameraX lifecycle, analyzer selection per scan mode.
- `androidApp/src/main/java/com/scanium/app/camera/DetectionOverlay.kt` – Draws bounding boxes/labels.
- `core-models/src/main/java/com/scanium/app/camera/ScanMode.kt` – Enum for object/barcode/document scan modes.

### ML
- `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt` – ML Kit object detector wrapper + detection pipeline entry.
- `androidApp/src/main/java/com/scanium/app/ml/BarcodeScannerClient.kt` – Barcode/QR analyzer.
- `androidApp/src/main/java/com/scanium/app/ml/DocumentTextRecognitionClient.kt` – OCR analyzer.
- `androidApp/src/main/java/com/scanium/app/ml/PricingEngine.kt` – Demo EUR price range generator.
- `androidApp/src/main/java/com/scanium/app/ml/DetectionLogger.kt` – Debug stats/logging.

### Tracking
- `core-tracking/src/main/java/com/scanium/app/tracking/ObjectTracker.kt` – Multi-frame tracker with trackingId + spatial fallback.
- `core-tracking/src/main/java/com/scanium/app/tracking/ObjectCandidate.kt` – Candidate state, IoU/distance helpers.
- `core-tracking/src/main/java/com/scanium/app/aggregation/ItemAggregator.kt` – Session aggregation/dedup.

### Domain pack
- `core-domainpack/src/main/java/com/scanium/app/domain/DomainPackProvider.kt` – Singleton accessor to packs.
- `core-domainpack/src/main/java/com/scanium/app/domain/repository/LocalDomainPackRepository.kt` – Loads JSON packs from resources.
- `core-domainpack/src/main/java/com/scanium/app/domain/category/BasicCategoryEngine.kt` – Domain category selection using labels/prompts.
- `core-domainpack/src/main/java/com/scanium/app/domain/category/CategoryMapper.kt` – Maps domain categories to `ItemCategory`.
- `core-domainpack/src/main/res/raw/home_resale_domain_pack.json` – Default config.

### Items/state
- `androidApp/src/main/java/com/scanium/app/items/ItemsViewModel.kt` – Shared state, aggregation/dedup, classification orchestration.
- `core-models/src/main/java/com/scanium/app/items/ScannedItem.kt` – Promoted detection model (typealias to shared).
- `core-models/src/main/java/com/scanium/app/model/ImageRef.kt` – Portable image reference model.

### Selling (mock eBay)
- `androidApp/src/main/java/com/scanium/app/selling/ui/SellOnEbayScreen.kt` – UI to select items and list them.
- `androidApp/src/main/java/com/scanium/app/selling/domain/Listing.kt` – Listing status models.
- `androidApp/src/main/java/com/scanium/app/selling/data/MockEbayApi.kt` – Mock API for listing creation.
- `androidApp/src/main/java/com/scanium/app/selling/util/ListingTitleBuilder.kt` – Listing title generation.

### Tests (top 5)
- `core-tracking/src/test/java/com/scanium/app/tracking/ObjectTrackerTest.kt` – Tracker confirmation/expiry cases.
- `core-tracking/src/test/java/com/scanium/app/tracking/ObjectCandidateTest.kt` – Candidate math/IoU checks.
- `androidApp/src/test/java/com/scanium/app/items/ItemsViewModelTest.kt` – State/aggregation dedup tests.
- `androidApp/src/test/java/com/scanium/app/ml/PricingEngineTest.kt` – Price generation ranges.
- `androidApp/src/test/java/com/scanium/app/domain/category/BasicCategoryEngineTest.kt` – Domain pack category selection.

## 6) Build/Test Commands (short)
- `./scripts/build.sh assembleDebug` or `./gradlew assembleDebug` – build APK.
- `./scripts/build.sh test` or `./gradlew test` – JVM unit tests (fast path).
- `./gradlew connectedAndroidTest` – instrumented/UI tests (device/emulator required).
- `./gradlew lint` – lint checks.

## 7) How to Work Efficiently in This Repo (token-saving rules)
- Search-first workflow: use `rg` to find symbols, open only minimal file slices (progressive disclosure).
- Prefer patch edits over wholesale rewrites; keep Compose/state patterns intact.
- Run fast JVM tests before touching camera/instrumented code; avoid expensive UI tests unless necessary.
- Keep changes self-contained; respect aggregation/tracking invariants and domain pack contracts before modifying pipelines.
