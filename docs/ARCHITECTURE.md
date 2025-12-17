# Architecture

## Module overview
- **androidApp** – Compose UI, navigation, CameraX/ML Kit analyzers, view models, selling UI.
- **core-models** – Platform-neutral models (`ImageRef`, `NormalizedRect`, `ScannedItem`, `ScanMode`).
- **core-tracking** – Platform-free tracking and aggregation helpers (`ObjectTracker`, `ObjectCandidate`, presets).
- **core-domainpack** – Domain pack config/models, category engine, repository interfaces.
- **core-scan / core-contracts** – Namespaces for future scan/domain contracts (no Android deps).
- **android-camera-camerax / android-ml-mlkit** – Library shells for CameraX and ML Kit namespaces.
- **android-platform-adapters** – Converters between Android types (Bitmap/Rect/ImageProxy) and shared models.
- **shared:core-models / shared:core-tracking** – KMP-friendly modules; keep Android types out.

## Key flows (current Android implementation)
1. **Camera preview** → `CameraScreen` hosts preview/overlays and forwards frames to `CameraXManager`.
2. **Analyzer selection** → `CameraXManager` chooses analyzer per `ScanMode` (objects, barcodes, documents).
3. **ML inference** → analyzers in `ml` package wrap ML Kit clients, emit `RawDetection` with normalized geometry and optional thumbnails.
4. **Tracking & aggregation** → `ObjectTracker` + aggregation presets convert detections into stable items; IoU + trackingId dedupe protect against flicker.
5. **Item orchestration** → `ItemsViewModel` maintains session state, routes detections/classification, and exposes UI-ready `StateFlow`.
6. **UI** → Compose screens (`CameraScreen`, `ItemsListScreen`, `SellScreen`) render lists, overlays, and selling flow.
7. **Persistence/backends** → Mock selling/marketplace code lives in `selling` package; cloud classification exists but requires explicit backend config (see TODO in PRODUCT/SECURITY).

## Where to look in code (truth sources)
- `androidApp/src/main/java/com/scanium/app/MainActivity.kt` – Android entry point / Compose host.
- `androidApp/src/main/java/com/scanium/app/ScaniumApp.kt` – Root composable + navigation wiring.
- `androidApp/src/main/java/com/scanium/app/navigation/NavGraph.kt` – Destinations and routes.
- `androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt` – Preview, gestures, overlays.
- `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt` – CameraX lifecycle and analyzer routing.
- `androidApp/src/main/java/com/scanium/app/camera/DetectionOverlay.kt` – Overlay rendering.
- `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt` – Object detection pipeline.
- `androidApp/src/main/java/com/scanium/app/ml/BarcodeScannerClient.kt` – Barcode/QR analyzer.
- `androidApp/src/main/java/com/scanium/app/ml/DocumentTextRecognitionClient.kt` – OCR analyzer.
- `androidApp/src/main/java/com/scanium/app/items/ItemsViewModel.kt` – Aggregation/state orchestration.
- `androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifier.kt` – Cloud classification stub (disabled unless configured).
- `core-tracking/src/main/java/com/scanium/app/tracking/ObjectTracker.kt` – Tracking logic (platform-neutral math).
- `core-domainpack/src/main/java/com/scanium/app/domain/category/BasicCategoryEngine.kt` – Domain pack category selection.

## Shared brain guardrails (KMP)
- Keep shared modules free of Android types; adapt at module boundaries using `android-platform-adapters`.
- Shared logic includes: session state machine, tracking/aggregation, domain pack selection, analytics contracts.
- Platform-specific: CameraX/ML Kit analyzers, Compose/SwiftUI UI, permissions, media IO.
