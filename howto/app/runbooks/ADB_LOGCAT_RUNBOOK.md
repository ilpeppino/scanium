# ADB Logcat Runbook — Scanium (Android)

## Quick Start (copy/paste)
- **Application IDs** (from `androidApp/build.gradle.kts`):
  - `prod`: `com.scanium.app`
  - `dev`: `com.scanium.app.dev`
  - `beta`: `com.scanium.app.beta`

```bash
# Replace package name for your flavor
adb logcat --pid=$(adb shell pidof -s com.scanium.app.dev) -v time \
  -s ScaniumApplication:V APP_BUILD:V StartupGuard:V StartupOrchestrator:V MainActivity:V
```

## Common Filters (How to use)
**Filter by tag**
```bash
adb logcat -s CameraXManager:V CAM_LIFE:V ScanPipeline:V
```

**Filter by PID / package**
```bash
adb logcat --pid=$(adb shell pidof -s com.scanium.app)
adb logcat --pid=$(adb shell pidof -s com.scanium.app.dev)
adb logcat --pid=$(adb shell pidof -s com.scanium.app.beta)
```

**Filter by priority (V/D/I/W/E/F)**
```bash
adb logcat *:W
adb logcat -s CameraXManager:W
```

**Dump current logs vs streaming**
```bash
adb logcat -d -v time
adb logcat -v time
```

**Clear buffer**
```bash
adb logcat -c
```

**Save to file**
```bash
adb logcat -d -v time > /tmp/scanium_logcat.txt
```

**Crash buffer**
```bash
adb logcat -b crash -v time
```

**Note on `ScaniumLog` tags**
- `ScaniumLog` prefixes tags as `Scanium/<TAG>` (see `androidApp/src/main/java/com/scanium/app/logging/ScaniumLog.kt`).
- Example: `Scanium/AssistantPreflight`, `Scanium/Assistant`.

## Quick Commands
1) **Startup + safe mode**
```bash
adb logcat -s ScaniumApplication:V APP_BUILD:V StartupGuard:V StartupOrchestrator:V MainActivity:V
```

2) **Camera session + preview binding**
```bash
adb logcat -s CameraXManager:V CameraScreen:V CAM_LIFE:V CameraFrameDims:V
```

3) **Scan pipeline diagnostics**
```bash
adb logcat -s ScanPipeline:V LiveScan:V
```

4) **Object detection (ML Kit)**
```bash
adb logcat -s ObjectDetectionEngine:V ObjectDetectorClient:V DetectionMapping:V CategoryResolver:V
```

5) **Barcode + document scanning**
```bash
adb logcat -s BarcodeDetectorClient:V DocumentTextRecognitionClient:V
```

6) **Tracking + overlay**
```bash
adb logcat -s DetectionLogger:V OverlayTrackManager:V
```

7) **Item creation + aggregation**
```bash
adb logcat -s ItemsViewModel:V ItemsUiFacade:V ItemsStateManager:V ItemClassificationCoord:V
```

8) **Persistence**
```bash
adb logcat -s ScannedItemRepository:V ItemsStateManager:V
```

9) **Vision enrichment (local + cloud)**
```bash
adb logcat -s VisionInsightsPrefiller:V VisionInsightsRepo:V EnrichmentRepo:V
```

10) **Assistant preflight + chat**
```bash
adb logcat -s "Scanium/AssistantPreflight":V AssistantHttp:V ScaniumNet:V ScaniumAuth:V ScaniumAssist:V AssistantRepo:V
```

11) **Cloud classifier**
```bash
adb logcat -s CloudClassifier:V CloudClassifierApi:V CloudCallGate:V ScaniumNet:V ScaniumAuth:V
```

12) **Item sync / upload**
```bash
adb logcat -s ItemSyncWorker:V ItemSyncManager:V FirstSyncManager:V
```

13) **Crashes + ANR**
```bash
adb logcat -b crash -v time
adb logcat -s AndroidRuntime:V ActivityManager:V
```

14) **Settings + auth**
```bash
adb logcat -s SettingsViewModel:V AuthRepository:V AuthTokenInterceptor:V CredentialManagerAuthLauncher:V
```

15) **Save snapshot to file**
```bash
adb logcat -d -v time > /tmp/scanium_logcat.txt
```

## Pipeline Verification

### 1) App startup / safe mode
**What it does:** Application init, crash-loop detection, startup phases, splash, first composition.

**Success looks like:**
- `APP_BUILD`: `versionName=... versionCode=... flavor=... buildType=...`
- `StartupGuard`: `Startup attempt recorded. Safe mode: ...`
- `StartupGuard`: `Startup success recorded. Safe mode disabled for next launch.`
- `MainActivity`: `Startup success recorded - crash loop detection reset`

**Failure looks like:**
- `StartupGuard`: `Quick crash detected...` / `Crash loop detected (...)`
- `ScaniumApplication`: `Starting in SAFE MODE due to crash loop`
- `MainActivity`: `Failed to initialize Domain Pack provider`

**Command:**
```bash
adb logcat -s ScaniumApplication:V APP_BUILD:V StartupGuard:V StartupOrchestrator:V MainActivity:V
```

### 2) Camera session & preview binding
**What it does:** Binds CameraX, starts preview + analyzer, handles lifecycle/watchdog.

**Success looks like:**
- `CameraXManager`: `Ensuring ML Kit models are ready...` → `All ML Kit models initialized successfully`
- `CAM_LIFE`: `SESSION_START: id=...` / `LIFECYCLE: ...`
- `CameraXManager`: `startPreviewDetection: Called...` / `FIRST_FRAME: Preview detection received first frame`

**Failure looks like:**
- `CameraXManager`: `Failed to bind camera use cases` / `No camera hardware detected`
- `CameraScreen`: `Failed to bind camera`
- `CameraXManager`: `WATCHDOG: Recovery FAILED after ... attempts`

**Command:**
```bash
adb logcat -s CameraXManager:V CameraScreen:V CAM_LIFE:V CameraFrameDims:V
```

### 3) Scan pipeline (live scan)
**What it does:** Frame gating, throttling, detection invocation, scan metrics.

**Success looks like:**
- `ScanPipeline`: `=== SESSION STARTED ===`
- `ScanPipeline`: `[DETECT] mode=... size=...` and `[RESULT] detections=... itemsAdded=...`
- `LiveScan`: `=== LIVE SCAN DIAGNOSTICS ENABLED ===` (if enabled)

**Failure looks like:**
- `ScanPipeline`: `[SKIP] Detection skipped: ...`
- `LiveScan`: `GATING_REJECT` / low confidence / `itemAdded=false`

**Command:**
```bash
adb logcat -s ScanPipeline:V LiveScan:V
```

### 4) ML detection (objects)
**What it does:** ML Kit object detection + mapping to app models.

**Success looks like:**
- `ObjectDetectionEngine`: `Creating STREAM_MODE detector` / `ML Kit process() SUCCESS`
- `ObjectDetectorClient`: `Detected X objects` → `Converted to X scanned items...`

**Failure looks like:**
- `ObjectDetectionEngine`: `ML Kit process() FAILED`
- `ObjectDetectorClient`: `Error detecting objects`

**Command:**
```bash
adb logcat -s ObjectDetectionEngine:V ObjectDetectorClient:V DetectionMapping:V CategoryResolver:V
```

### 5) Barcode + document scanning
**What it does:** ML Kit barcode and text recognition.

**Success looks like:**
- `BarcodeDetectorClient`: `Detected X barcode(s)` → `Converted to X scanned items`
- `DocumentTextRecognitionClient`: `Recognized text length: ...` → `Created document item ...`

**Failure looks like:**
- `BarcodeDetectorClient`: `Error scanning barcodes`
- `DocumentTextRecognitionClient`: `Error recognizing text`

**Command:**
```bash
adb logcat -s BarcodeDetectorClient:V DocumentTextRecognitionClient:V
```

### 6) Tracking + overlay mapping
**What it does:** Tracking diagnostics and overlay mapping for detected objects.

**Success looks like:**
- `DetectionLogger`: `Frame #... | Summary: raw=... valid=... promoted=...`
- `OverlayTrackManager`: `[OVERLAY] Track count changed: ...` / `READY aggregated=...`

**Failure looks like:**
- `DetectionLogger`: `REJECTED | id=... reason=...`
- `OverlayTrackManager`: `WARNING: ... detections but 0 tracks after mapping!`

**Command:**
```bash
adb logcat -s DetectionLogger:V OverlayTrackManager:V
```

### 7) Item creation + aggregation
**What it does:** Convert detections into items, aggregate, and manage state.

**Success looks like:**
- `ItemsViewModel`: `createItemFromDetection: ...` → `Item created from detection: ...`
- `ItemsStateManager`: `>>> addItemsSync: Processing ...`
- `ItemsUiFacade`: `Aggregated ... items`

**Failure looks like:**
- `ItemsViewModel`: `Classification failed for detection ...` / `No pending detection found ...`
- `ItemsStateManager`: `SCAN_ENRICH: ❌ Cannot apply vision insights - item NOT FOUND`

**Command:**
```bash
adb logcat -s ItemsViewModel:V ItemsUiFacade:V ItemsStateManager:V ItemClassificationCoord:V
```

### 8) Persistence (DB)
**What it does:** Save/load items to local database and emit errors.

**Success looks like:**
- `ScannedItemRepository`: `Loaded items ...` / `Persisting items ...`

**Failure looks like:**
- `ScannedItemRepository`: `Persistence failed during ...`

**Command:**
```bash
adb logcat -s ScannedItemRepository:V
```

### 9) UI list rendering + navigation
**What it does:** Navigate to item list and render ItemsList UI.

**Success looks like:**
- `ItemsUiFacade`: `Auto-opening item list after scan (items added: ...)`
- `NavGraph`: `Photo added to item ...`

**Failure looks like:**
- No list navigation event after items are added (likely missing UI logs today).

**Command:**
```bash
adb logcat -s ItemsUiFacade:V NavGraph:V
```

### 10) Vision enrichment (local + cloud)
**What it does:** Apply OCR/colors locally, call cloud insights/enrichment.

**Success looks like:**
- `VisionInsightsPrefiller`: `SCAN_ENRICH: Starting extraction ...`
- `VisionInsightsPrefiller`: `SCAN_ENRICH: Local extraction complete ...`
- `VisionInsightsPrefiller`: `SCAN_ENRICH: Cloud extraction complete ...`

**Failure looks like:**
- `VisionInsightsRepo`: `SCAN_ENRICH: SCANIUM_API_BASE_URL is not configured`
- `VisionInsightsPrefiller`: `SCAN_ENRICH: Local extraction failed`

**Command:**
```bash
adb logcat -s VisionInsightsPrefiller:V VisionInsightsRepo:V EnrichmentRepo:V
```

### 11) Backend calls (preflight / assistant / config)
**What it does:** Health checks, assistant chat requests, API key checks.

**Success looks like:**
- `Scanium/AssistantPreflight`: `Preflight: using cached result ...` / `Warmup: completed successfully`
- `ScaniumNet`: `AssistantApi: endpoint=...` / `FeatureFlags: healthEndpoint=...`
- `ScaniumAuth`: `Adding X-API-Key header` / `Adding Authorization header`

**Failure looks like:**
- `Scanium/AssistantPreflight`: `Preflight: NOT_CONFIGURED (no base URL)`
- `ScaniumAuth`: `apiKey is NULL - X-API-Key header will NOT be added!`
- `AssistantRepo`: `Assistant backend error: ...`

**Command:**
```bash
adb logcat -s "Scanium/AssistantPreflight":V AssistantHttp:V ScaniumNet:V ScaniumAuth:V ScaniumAssist:V AssistantRepo:V
```

### 12) Sync / upload
**What it does:** Push/pull items with backend (WorkManager).

**Success looks like:**
- `ItemSyncWorker`: `Sync completed: pushed=... pulled=... conflicts=...`
- `ItemSyncManager`: `Sync completed successfully`

**Failure looks like:**
- `ItemSyncWorker`: `Sync failed: Network error` / `Server error`
- `ItemSyncManager`: `Sync failed`

**Command:**
```bash
adb logcat -s ItemSyncWorker:V ItemSyncManager:V FirstSyncManager:V
```

## Functional Scenarios

### Scan once and ensure item appears in list
```bash
adb logcat -s CameraXManager:V ScanPipeline:V ObjectDetectorClient:V ItemsViewModel:V ItemsStateManager:V ItemsUiFacade:V NavGraph:V
```

### Continuous scanning mode (boxes appear + items added)
```bash
adb logcat -s CameraXManager:V ScanPipeline:V LiveScan:V DetectionLogger:V OverlayTrackManager:V ItemsStateManager:V
```

### Editing item details + persistence
```bash
adb logcat -s ItemDetailViewModel:V AddPhotoHandler:V ItemPhotoManager:V EditItemScreenV3:V ScannedItemRepository:V ItemsStateManager:V
```

### Backend/preflight/config/assistant calls
```bash
adb logcat -s "Scanium/AssistantPreflight":V AssistantHttp:V ScaniumNet:V ScaniumAuth:V ScaniumAssist:V AssistantRepo:V
```

### Upload/sync behavior
```bash
adb logcat -s ItemSyncWorker:V ItemSyncManager:V FirstSyncManager:V
```

### App startup / splash / crash loops
```bash
adb logcat -s ScaniumApplication:V APP_BUILD:V StartupGuard:V StartupOrchestrator:V MainActivity:V AndroidRuntime:V
```

## Crashes & ANR
```bash
# Last crash buffer
adb logcat -b crash -v time

# Runtime crashes + ANR hints
adb logcat -s AndroidRuntime:V ActivityManager:V
```

## Networking (HTTP status, timeouts, TLS, DNS)
```bash
# Assistant + preflight
adb logcat -s "Scanium/AssistantPreflight":V AssistantHttp:V ScaniumNet:V ScaniumAuth:V ScaniumAssist:V AssistantRepo:V

# Cloud classifier / vision insights
adb logcat -s CloudClassifierApi:V VisionInsightsRepo:V ScaniumNet:V ScaniumAuth:V
```

Notes:
- There is no OkHttp logging interceptor; rely on `ScaniumNet`, `ScaniumAuth`, `AssistantRepo`, `AssistantHttp` logs for request context.
- TLS/cert pinning messages appear under `CloudClassifier` and `VisionInsightsRepo` (e.g., certificate pinning enabled/disabled).

## Storage / DB
```bash
adb logcat -s ScannedItemRepository:V ItemPhotoManager:V MediaStoreSaver:V
```

## Appendix: Tags and Sources

### Tag inventory (TAG constants)
```
AdaptiveThrottlePolicy	androidApp/src/main/java/com/scanium/app/camera/detection/AdaptiveThrottlePolicy.kt
AddPhotoHandler	androidApp/src/main/java/com/scanium/app/items/edit/AddPhotoHandler.kt
AndroidBillingProvider	androidApp/src/main/java/com/scanium/app/billing/AndroidBillingProvider.kt
Assistant	androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantViewModel.kt
AssistantHttp	androidApp/src/main/java/com/scanium/app/selling/assistant/network/AssistantOkHttpClientFactory.kt
AssistantPreflight	androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantPreflight.kt, androidApp/src/main/java/com/scanium/app/selling/assistant/PreflightPolicy.kt
AssistantRepository	androidApp/src/main/java/com/scanium/app/assistant/AssistantRepository.kt
AssistantRetry	androidApp/src/main/java/com/scanium/app/selling/assistant/network/AssistantRetryInterceptor.kt
AssistantViewModel	androidApp/src/main/java/com/scanium/app/assistant/AssistantViewModel.kt
AssistantVoice	androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantVoiceController.kt
AuthRepository	androidApp/src/main/java/com/scanium/app/auth/AuthRepository.kt
AuthTokenInterceptor	androidApp/src/main/java/com/scanium/app/network/AuthTokenInterceptor.kt
BackendHealthGate	androidApp/src/androidTest/java/com/scanium/app/regression/BackendHealthGate.kt
BarcodeDetectorClient	androidApp/src/main/java/com/scanium/app/ml/BarcodeDetectorClient.kt
BasicCategoryEngine	core-domainpack/src/main/java/com/scanium/app/domain/category/BasicCategoryEngine.kt
BuildInfoReceiver	androidApp/src/dev/java/com/scanium/app/debug/BuildInfoReceiver.kt
BundleZipExporter	androidApp/src/main/java/com/scanium/app/items/export/bundle/BundleZipExporter.kt
CAM_LIFE	androidApp/src/main/java/com/scanium/app/camera/CameraSessionController.kt
CORR	androidApp/src/main/java/com/scanium/app/camera/geom/CorrelationDebug.kt
CameraFtueViewModel	androidApp/src/main/java/com/scanium/app/ftue/CameraFtueViewModel.kt
CameraXManager	androidApp/src/main/java/com/scanium/app/camera/CameraFrameAnalyzer.kt, androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt
CategoryMapper	core-domainpack/src/main/java/com/scanium/app/domain/category/CategoryMapper.kt
CategoryResolver	androidApp/src/main/java/com/scanium/app/ml/detector/CategoryResolver.kt
ClassificationOrchestrator	shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/classification/ClassificationOrchestrator.kt
ClassificationPrefs	androidApp/src/main/java/com/scanium/app/data/ClassificationPreferences.kt
CloudCallGate	androidApp/src/main/java/com/scanium/app/ml/classification/CloudCallGate.kt
CloudClassifier	androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifier.kt
CloudClassifierApi	androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifierApi.kt
CredentialManagerAuthLauncher	androidApp/src/main/java/com/scanium/app/auth/CredentialManagerAuthLauncher.kt
CropBasedEnricher	androidApp/src/main/java/com/scanium/app/ml/CropBasedEnricher.kt
DedupeHelper	androidApp/src/main/java/com/scanium/app/camera/detection/DedupeHelper.kt
DetectionLogger	androidApp/src/main/java/com/scanium/app/ml/DetectionLogger.kt
DetectionMapping	androidApp/src/main/java/com/scanium/app/ml/detector/DetectionMapping.kt
DetectionRouter	androidApp/src/main/java/com/scanium/app/camera/detection/DetectionRouter.kt
DevConfigOverride	androidApp/src/main/java/com/scanium/app/config/DevConfigOverride.kt
DevHealthMonitor	androidApp/src/main/java/com/scanium/app/monitoring/DevHealthMonitorScheduler.kt, androidApp/src/main/java/com/scanium/app/monitoring/DevHealthMonitorWorker.kt
DocumentCandidateDetector	androidApp/src/main/java/com/scanium/app/camera/detection/DocumentCandidateDetector.kt
DocumentTextRecognitionClient	androidApp/src/main/java/com/scanium/app/ml/DocumentTextRecognitionClient.kt
DomainPackProvider	core-domainpack/src/main/java/com/scanium/app/domain/DomainPackProvider.kt
EbayMarketplaceService	androidApp/src/main/java/com/scanium/app/selling/data/EbayMarketplaceService.kt
EditItemFtueViewModel	androidApp/src/main/java/com/scanium/app/ftue/EditItemFtueViewModel.kt
EnrichmentPolicy	androidApp/src/main/java/com/scanium/app/quality/EnrichmentPolicy.kt
EnrichmentRepo	androidApp/src/main/java/com/scanium/app/enrichment/EnrichmentRepository.kt
ExportAssistant	androidApp/src/main/java/com/scanium/app/items/edit/ExportAssistantViewModel.kt
ExportBundleRepo	androidApp/src/main/java/com/scanium/app/items/export/bundle/ExportBundleRepository.kt
ExportViewModel	androidApp/src/main/java/com/scanium/app/items/export/bundle/ExportViewModel.kt
FTUE_CAMERA_UI	androidApp/src/main/java/com/scanium/app/ftue/CameraUiFtueAnchorRegistry.kt, androidApp/src/main/java/com/scanium/app/ftue/CameraUiFtueViewModel.kt
FakeBillingProvider	androidApp/src/main/java/com/scanium/app/billing/FakeBillingProvider.kt
FirstSyncManager	androidApp/src/main/java/com/scanium/app/items/sync/FirstSyncManager.kt
GeomMap	androidApp/src/main/java/com/scanium/app/camera/DetectionGeometryMapper.kt
GeometryMapper	androidApp/src/main/java/com/scanium/app/camera/geom/GeometryMapper.kt
HealthCheck	androidApp/src/main/java/com/scanium/app/monitoring/HealthCheckRepository.kt
ImageAttachmentBuilder	androidApp/src/main/java/com/scanium/app/selling/assistant/ImageAttachmentBuilder.kt
ImageRefExtensions	androidApp/src/main/java/com/scanium/app/model/ImageRefExtensions.kt
ImageUtils	androidApp/src/main/java/com/scanium/app/camera/ImageUtils.kt
ItemAggregator	core-tracking/src/main/java/com/scanium/app/aggregation/ItemAggregator.kt, shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ItemAggregator.kt
ItemAttributeLocalizer	androidApp/src/main/java/com/scanium/app/items/ItemAttributeLocalizer.kt
ItemClassificationCoord	androidApp/src/main/java/com/scanium/app/items/classification/ItemClassificationCoordinator.kt
ItemDetailViewModel	androidApp/src/main/java/com/scanium/app/items/edit/ItemDetailViewModel.kt
ItemPhotoManager	androidApp/src/main/java/com/scanium/app/items/photos/ItemPhotoManager.kt
ItemSyncManager	androidApp/src/main/java/com/scanium/app/items/sync/ItemSyncManager.kt
ItemSyncWorker	androidApp/src/main/java/com/scanium/app/items/sync/ItemSyncWorker.kt
ItemsListFtueViewModel	androidApp/src/main/java/com/scanium/app/ftue/ItemsListFtueViewModel.kt
ItemsStateManager	androidApp/src/main/java/com/scanium/app/items/state/ItemsPersistence.kt, androidApp/src/main/java/com/scanium/app/items/state/ItemsStateManager.kt, androidApp/src/main/java/com/scanium/app/items/state/ItemsStateStore.kt, androidApp/src/main/java/com/scanium/app/items/state/ItemsTelemetry.kt
ItemsUiFacade	androidApp/src/main/java/com/scanium/app/items/ItemsUiFacade.kt
ItemsViewModel	androidApp/src/main/java/com/scanium/app/items/ItemsViewModel.kt
ListingGenerationVM	androidApp/src/main/java/com/scanium/app/selling/generation/ListingGenerationViewModel.kt
ListingImagePreparer	androidApp/src/main/java/com/scanium/app/selling/util/ListingImagePreparer.kt
ListingStatusManager	androidApp/src/main/java/com/scanium/app/items/listing/ListingStatusManager.kt
ListingViewModel	androidApp/src/main/java/com/scanium/app/selling/ui/ListingViewModel.kt
LiveScan	androidApp/src/main/java/com/scanium/app/camera/detection/LiveScanDiagnostics.kt
LocalDomainPackRepo	core-domainpack/src/main/java/com/scanium/app/domain/repository/LocalDomainPackRepository.kt
LocalVisionExtractor	androidApp/src/main/java/com/scanium/app/ml/LocalVisionExtractor.kt
MainActivity	androidApp/src/main/java/com/scanium/app/MainActivity.kt
MarketplaceRepository	androidApp/src/main/java/com/scanium/app/data/MarketplaceRepository.kt
MediaStoreSaver	androidApp/src/main/java/com/scanium/app/media/MediaStoreSaver.kt
MobileTelemetry	androidApp/src/main/java/com/scanium/app/telemetry/MobileTelemetryClient.kt
MockEbayApi	androidApp/src/main/java/com/scanium/app/selling/data/MockEbayApi.kt
NetworkTypeCollector	androidApp/src/main/java/com/scanium/app/telemetry/NetworkTypeCollector.kt
ObjectDetectionEngine	androidApp/src/main/java/com/scanium/app/ml/detector/ObjectDetectionEngine.kt
ObjectDetectorClient	androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt
ObjectTracker	shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ObjectTracker.kt
OnDeviceClassifier	androidApp/src/main/java/com/scanium/app/ml/classification/OnDeviceClassifier.kt
OverlayTrackManager	androidApp/src/main/java/com/scanium/app/items/overlay/OverlayTrackManager.kt
OverlayTransforms	androidApp/src/main/java/com/scanium/app/camera/OverlayTransforms.kt
PerItemDedupeHelper	androidApp/src/main/java/com/scanium/app/items/photos/PerItemDedupeHelper.kt
PlainTextExporter	androidApp/src/main/java/com/scanium/app/items/export/bundle/PlainTextExporter.kt
PostingAssist	androidApp/src/main/java/com/scanium/app/selling/ui/PostingAssistViewModel.kt
PriceEstimationRepo	androidApp/src/main/java/com/scanium/app/pricing/PriceEstimationRepository.kt
ScanPipeline	androidApp/src/main/java/com/scanium/app/camera/detection/ScanPipelineDiagnostics.kt
ScaniumAuth	androidApp/src/main/java/com/scanium/app/config/SecureApiKeyStore.kt
ScannedItemRepository	androidApp/src/main/java/com/scanium/app/items/persistence/ScannedItemRepository.kt
SettingsFtueViewModel	androidApp/src/main/java/com/scanium/app/ftue/SettingsFtueViewModel.kt
StableItemCropper	androidApp/src/main/java/com/scanium/app/ml/classification/StableItemCropper.kt
StartupGuard	androidApp/src/main/java/com/scanium/app/startup/StartupGuard.kt
StartupOrchestrator	androidApp/src/main/java/com/scanium/app/startup/StartupOrchestrator.kt
TestBridge	androidApp/src/main/java/com/scanium/app/testing/TestBridge.kt
TestConfigOverride	androidApp/src/main/java/com/scanium/app/testing/TestConfigOverride.kt
ThrottleHelper	androidApp/src/main/java/com/scanium/app/camera/detection/ThrottleHelper.kt
TtsController	androidApp/src/main/java/com/scanium/app/assistant/tts/TtsController.kt
TtsManager	androidApp/src/main/java/com/scanium/app/assistant/tts/TtsManager.kt
VisionInsightsPrefiller	androidApp/src/main/java/com/scanium/app/ml/VisionInsightsPrefiller.kt
VisionInsightsRepo	androidApp/src/main/java/com/scanium/app/ml/VisionInsightsRepository.kt
VoiceController	androidApp/src/main/java/com/scanium/app/voice/VoiceController.kt
```

### Tag inventory (literal `Log.*("tag", ...)` calls)
```
$PREFIX/$tag	androidApp/src/main/java/com/scanium/app/logging/ScaniumLog.kt
APP_BUILD	androidApp/src/main/java/com/scanium/app/ScaniumApplication.kt
AssetCatalogSource	androidApp/src/main/java/com/scanium/app/catalog/impl/AssetCatalogSource.kt
AssistantRepo	androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantApi.kt
AuthRepository	androidApp/src/main/java/com/scanium/app/auth/AuthRepository.kt
CameraFrameDims	androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt
CameraScreen	androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt
DiagnosticsTest	androidApp/src/main/java/com/scanium/app/ui/settings/SettingsViewModel.kt
EditItemScreenV3	androidApp/src/main/java/com/scanium/app/items/edit/EditItemScreenV3.kt
ExportAssistant	androidApp/src/main/java/com/scanium/app/items/edit/ExportAssistantSheet.kt
FTUE_CAMERA_UI	androidApp/src/main/java/com/scanium/app/ftue/CameraUiFtueOverlay.kt
InAppBrowser	androidApp/src/main/java/com/scanium/app/util/InAppBrowser.kt
ListingTitleBuilder	androidApp/src/main/java/com/scanium/app/selling/util/ListingTitleBuilder.kt
NavGraph	androidApp/src/main/java/com/scanium/app/navigation/NavGraph.kt
OverlayDebug	androidApp/src/main/java/com/scanium/app/camera/DetectionOverlay.kt
PostingAssist	androidApp/src/main/java/com/scanium/app/selling/ui/PostingAssistScreen.kt
ScaniumApplication	androidApp/src/main/java/com/scanium/app/ScaniumApplication.kt
ScaniumAssist	androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantApi.kt
ScaniumAuth	androidApp/src/main/java/com/scanium/app/data/AndroidFeatureFlagRepository.kt, androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifierApi.kt, androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantApi.kt
ScaniumNet	androidApp/src/main/java/com/scanium/app/data/AndroidFeatureFlagRepository.kt, androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifierApi.kt, androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantApi.kt
SettingsViewModel	androidApp/src/main/java/com/scanium/app/ui/settings/SettingsViewModel.kt
SpotlightOverlay	androidApp/src/main/java/com/scanium/app/ftue/SpotlightTourOverlay.kt
VisionEnrichmentState	androidApp/src/main/java/com/scanium/app/ml/VisionEnrichmentState.kt
```

### Tag inventory (literal `ScaniumLog.*("tag", ...)` calls)
```
DraftReview	androidApp/src/main/java/com/scanium/app/selling/ui/DraftReviewViewModel.kt
```

### Key logging sources by area
- **Camera**: `CameraXManager.kt`, `CameraScreen.kt`, `CameraSessionController.kt`, `CameraFrameAnalyzer.kt`, `DetectionOverlay.kt`
- **Scanning**: `ScanPipelineDiagnostics.kt`, `LiveScanDiagnostics.kt`, `ObjectDetectionEngine.kt`, `ObjectDetectorClient.kt`, `DetectionMapping.kt`, `BarcodeDetectorClient.kt`, `DocumentTextRecognitionClient.kt`
- **Item creation/aggregation**: `ItemsViewModel.kt`, `ItemsUiFacade.kt`, `ItemsStateManager.kt`, `ItemClassificationCoordinator.kt`, `ItemAggregator.kt`
- **Repository/DB**: `ScannedItemRepository.kt`, `ItemsPersistence.kt`, `ItemsStateStore.kt`, `ItemPhotoManager.kt`, `MediaStoreSaver.kt`
- **Networking**: `AssistantApi.kt`, `AssistantPreflight.kt`, `AssistantOkHttpClientFactory.kt`, `CloudClassifierApi.kt`, `VisionInsightsRepository.kt`, `AndroidFeatureFlagRepository.kt`, `AuthRepository.kt`
- **ViewModels**: `ItemsViewModel.kt`, `AssistantViewModel.kt`, `ExportAssistantViewModel.kt`, `ListingViewModel.kt`, `SettingsViewModel.kt`, `MainActivity.kt`

### Missing/Minimal logs to consider (not implemented)
- **ItemsList UI rendering** (`ItemsListScreen.kt`, `ItemsListContent.kt`): add a single `Log.d("ItemsListScreen", "render items=${state.items.size}")` on state change to confirm list rendering when debugging missing items.
- **ItemsApi network layer** (`ItemsApi.kt`): log endpoint + HTTP status for sync requests to trace server errors (keep to one info log per request).
- **NavGraph list navigation** (`NavGraph.kt`): add a single log when navigating to `ItemsListScreen` to confirm route transitions.
