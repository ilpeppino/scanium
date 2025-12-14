***REMOVED*** CLAUDE.md

Guidance for Claude Code when working with **Scanium** – a privacy-first Android app for real-time object detection, barcode scanning, and document OCR.

**Primary detection** happens **on-device** using Google ML Kit. **Enhanced classification** uses cloud API (default) or on-device CLIP (future) for fine-grained category recognition (23 categories via Domain Pack).

***REMOVED******REMOVED*** Project Essentials

- **Package**: `com.scanium.app`
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Min/Target SDK**: 24 / 34 (Android 7.0 / 14)
- **Required Java**: 17 (see `SETUP.md`)
- **Architecture**: Single-module MVVM, no DI framework

***REMOVED******REMOVED*** Commands

```bash
***REMOVED*** Build (auto-detects Java 17)
./build.sh assembleDebug
./build.sh assembleRelease

***REMOVED*** Test
./gradlew test                              ***REMOVED*** All unit tests (175+)
./gradlew test --tests "*ObjectTracker*"    ***REMOVED*** Single test class
./gradlew connectedAndroidTest              ***REMOVED*** Instrumented tests

***REMOVED*** Install & Debug
./gradlew installDebug
adb logcat | grep -E "ObjectTracker|CameraXManager|ObjectDetector"
```

***REMOVED******REMOVED*** Architecture Flow

```
Camera (CameraXManager)
  ↓ routes by ScanMode (OBJECT_DETECTION | BARCODE | DOCUMENT_TEXT)
ML Kit (ObjectDetectorClient | BarcodeScannerClient | DocumentTextRecognitionClient)
  ↓ STREAM_MODE (continuous) or SINGLE_IMAGE_MODE (tap)
ObjectTracker (frame-level deduplication via trackingId + spatial matching)
  ↓ confirms candidates → ScannedItem
ItemAggregator (session-level similarity-based deduplication)
  ↓ merges by weighted scoring (category/label/size/distance)
ItemsViewModel (StateFlow, shared across screens)
  ↓ ID-based final dedup
UI (CameraScreen, ItemsListScreen, SellOnEbayScreen)
```

***REMOVED******REMOVED*** Critical Invariants

***REMOVED******REMOVED******REMOVED*** 1. Dual Deduplication Strategy
- **Frame-level**: `ObjectTracker` uses ML Kit `trackingId` (STREAM_MODE) or IoU+distance fallback
  - Config: `minFramesToConfirm=1`, `minConfidence=0.2f`, permissive thresholds
  - Rationale: Session-level aggregator handles quality filtering
- **Session-level**: `ItemAggregator` merges by similarity (REALTIME preset: threshold 0.55)
  - Handles trackingId churn, camera movement, bounding box jitter
  - Weighted: category 40%, label 15%, size 20%, distance 25%

***REMOVED******REMOVED******REMOVED*** 2. Tracker Reset Triggers (OBJECT_DETECTION mode only)
- Starting new scan session (long-press)
- Switching scan modes
- Stopping scanning
- **Critical**: Prevents stale candidates across sessions

***REMOVED******REMOVED******REMOVED*** 3. ML Kit Detection Modes
- **SINGLE_IMAGE_MODE**: Tap captures (no tracking, better per-frame accuracy)
- **STREAM_MODE**: Continuous scan (provides `trackingId` for tracking pipeline)
- App auto-switches based on gesture (tap vs long-press)

***REMOVED******REMOVED******REMOVED*** 4. Scan Mode Routing
- **OBJECT_DETECTION**: → `ObjectDetectorClient` → tracker → aggregator
- **BARCODE**: → `BarcodeScannerClient` (instant recognition, no tracking)
- **DOCUMENT_TEXT**: → `DocumentTextRecognitionClient` (OCR, no tracking)

***REMOVED******REMOVED*** Key Files Map

**Camera & Processing**
- `camera/CameraXManager.kt` – CameraX lifecycle, mode routing, gesture handling
- `camera/ScanMode.kt` – Enum for OBJECT_DETECTION | BARCODE | DOCUMENT_TEXT
- `camera/ui/VerticalThresholdSlider.kt`, `ClassificationModeToggle.kt` – UI controls

**ML Kit Integration**
- `ml/ObjectDetectorClient.kt` – Wraps ML Kit Object Detection
- `ml/BarcodeScannerClient.kt` – Wraps ML Kit Barcode Scanning
- `ml/DocumentTextRecognitionClient.kt` – Wraps ML Kit Text Recognition
- `ml/ItemCategory.kt` – Enum mapping ML Kit's 5 coarse categories
- `ml/PricingEngine.kt` – Mock EUR price generation (replace with real API)

**Tracking System**
- `tracking/ObjectTracker.kt` – Multi-frame tracking, candidate confirmation/expiry
- `tracking/ObjectCandidate.kt` – Spatial data (bbox, center, IoU/distance methods)
- `tracking/TrackerConfig.kt` – Tunable thresholds (see `CameraXManager.kt`)

**Aggregation System**
- `aggregation/ItemAggregator.kt` – Similarity-based session deduplication
- `aggregation/AggregationPresets.kt` – 6 presets (REALTIME used by default)
- `aggregation/AggregatedItem.kt` – Merged detection with confidence/timestamps

**State Management**
- `items/ItemsViewModel.kt` – Centralized `StateFlow<List<ScannedItem>>`, ID-based dedup
- `items/ScannedItem.kt` – Immutable item model (id, thumbnail, category, confidence, etc.)
- `items/ItemListingStatus.kt` – eBay listing states (NOT_LISTED, LISTING_IN_PROGRESS, etc.)

**Domain Pack (Fine-Grained Categories)**
- `domain/config/DomainPack.kt` – 23 categories + 10 attributes schema
- `domain/repository/LocalDomainPackRepository.kt` – Loads JSON from `res/raw/home_resale_domain_pack.json`
- `domain/category/BasicCategoryEngine.kt` – ML Kit label → DomainCategory matching
- `domain/DomainPackProvider.kt` – Singleton initialized in `MainActivity`

**Cloud Classification System**
- `ml/classification/CloudClassifier.kt` – Uploads cropped items to backend API (multipart, retry, EXIF stripping)
- `ml/classification/ClassificationOrchestrator.kt` – Queue with max concurrency=2, exponential backoff retry
- `ml/classification/ClassificationResult.kt` – Domain category, attributes, status (PENDING/SUCCESS/FAILED)
- `ml/classification/ClassificationMode.kt` – Enum: ON_DEVICE | CLOUD (default: CLOUD)
- `data/ClassificationPreferences.kt` – Persists user's mode selection (DataStore)
- `settings/ClassificationModeViewModel.kt` – Exposes classification mode as StateFlow
- **Configuration**: Set `scanium.api.base.url` and `scanium.api.key` in `local.properties` (see `/docs/features/CLOUD_CLASSIFICATION.md`)

**eBay Selling (Mock)**
- `selling/data/MockEbayApi.kt` – Configurable mock (delays, failure modes)
- `selling/data/EbayMarketplaceService.kt` – Orchestrates listing creation
- `selling/ui/ListingViewModel.kt` – Draft management, posting workflow
- `selling/util/ListingImagePreparer.kt` – Image scaling/quality for listings

**Navigation & Entry**
- `MainActivity.kt` – Initializes DomainPackProvider, hosts NavHost
- `navigation/Routes.kt` – Compose nav destinations (CAMERA, ITEMS_LIST, SELL_ON_EBAY)

***REMOVED******REMOVED*** Configuration Tuning

**Tracker** (`CameraXManager.kt`):
```kotlin
TrackerConfig(
    minFramesToConfirm = 1,      // Instant (aggregator filters quality)
    minConfidence = 0.2f,         // Low (20%) for inclusive detection
    minBoxArea = 0.0005f,         // Accept tiny objects
    maxFrameGap = 8,              // Forgiving spatial matching
    expiryFrames = 15             // ~12s at 800ms analysis interval
)
```

**Aggregator** (`ItemsViewModel.kt`):
```kotlin
ItemAggregator(config = AggregationPresets.REALTIME)  // threshold 0.55, see AggregationPresets.kt
```

**Image Analysis** (`CameraXManager.kt`):
```kotlin
val analysisIntervalMs = 800L  // Process every 800ms
```

***REMOVED******REMOVED*** Testing

- **175+ tests**: 110 tracking/detection, 61 domain pack, 4+ eBay selling
- **Unit**: `app/src/test/` (JUnit 4, Robolectric, Truth, MockK, Coroutines Test)
- **Instrumented**: `app/src/androidTest/` (Compose Testing)
- See `md/testing/TEST_SUITE.md` for detailed coverage

***REMOVED******REMOVED*** KMP/iOS Porting Guardrails

**Goal**: Share Scanium's "brain" (tracking, aggregation, state management) across Android/iOS while keeping platform-specific UI/camera/ML.

***REMOVED******REMOVED******REMOVED*** Shared Code Rules (Future `shared/` Module)
1. **NO Android Dependencies**:
   - Forbidden: `android.*`, `androidx.*`, `CameraX`, `ML Kit` classes
   - Allowed: Kotlin stdlib, Coroutines, Kotlinx Serialization, expect/actual
2. **Platform Interfaces**:
   - Define `expect interface CameraProvider` (actual: `AndroidCameraProvider`, `IOSCameraProvider`)
   - Define `expect interface MLKitProvider` (actual: platform-specific ML Kit wrappers)
   - Define `expect class ImageBitmap` (actual: Android `Bitmap`, iOS `UIImage`)
3. **Shared Components** (Candidates for KMP):
   - `ObjectTracker` (pure Kotlin, no platform deps)
   - `ItemAggregator` (pure Kotlin)
   - `ItemsViewModel` logic (extract platform-agnostic state)
   - `DomainPack` models + `BasicCategoryEngine` (pure Kotlin)
   - `ScannedItem`, `TrackerConfig`, `AggregationPresets` (data models)
4. **Platform-Specific** (Stays in `app/` or `iosApp/`):
   - `CameraXManager` → Android only
   - `ObjectDetectorClient`, `BarcodeScannerClient`, `DocumentTextRecognitionClient` → Wrap in platform providers
   - Compose UI → Android; SwiftUI → iOS
   - `MainActivity`, navigation → Platform-specific entry points

***REMOVED******REMOVED******REMOVED*** Migration Strategy
1. Extract shared models to `shared/commonMain` (no android.* deps)
2. Define platform interfaces (`CameraProvider`, `MLKitProvider`, `ImageBitmap`)
3. Implement Android actuals in `shared/androidMain`
4. Create iOS actuals in `shared/iosMain` (Swift-Kotlin interop)
5. Keep Android build green after every PR (CI gate)
6. Gradually move business logic to `shared/` (tracker → aggregator → ViewModel)

***REMOVED******REMOVED******REMOVED*** Non-Negotiables
- Android must remain fully functional during/after KMP migration
- No breaking changes to Android UI/UX
- Platform-specific optimizations allowed (e.g., Android ML Kit vs iOS Core ML)
- Shared code must not assume Android threading (use `Dispatchers.Default`, not `Dispatchers.Main`)

***REMOVED******REMOVED*** Known Limitations

- **No persistence**: In-memory only (ViewModel state cleared on app close)
- **Mocked pricing**: `PricingEngine.kt` generates EUR ranges locally
- **Mocked eBay**: `MockEbayApi` simulates marketplace (ready for real API swap)
- **ML Kit categories**: 5 coarse categories → mitigated by Domain Pack (23 fine-grained) + Cloud Classification
- **Single module**: Will need multi-module for KMP (`:shared`, `:app`, `:iosApp`)
- **Cloud classification**: Requires backend API (see `/docs/features/CLOUD_CLASSIFICATION.md` for setup)
- **On-device CLIP**: Placeholder implementation; real TFLite CLIP model not integrated yet
- **Attribute extraction**: Cloud API supports attributes map; on-device extraction not implemented

***REMOVED******REMOVED*** Reference Documentation

**Setup**: `SETUP.md` (Java 17 cross-platform), `README.md` (features/usage)

**Architecture**:
- `md/architecture/ARCHITECTURE.md` – Comprehensive system design
- `md/architecture/DOMAIN_PACK_ARCHITECTURE.md` – Category taxonomy, JSON schema
- `md/features/TRACKING_IMPLEMENTATION.md` – Tracking deep-dive

**Features**:
- `docs/features/CLOUD_CLASSIFICATION.md` – Cloud-first classification, API contract, retry logic, privacy
- `md/features/EBAY_SELLING_INTEGRATION.md` – Marketplace flow, mock config

**Testing**:
- `md/testing/TEST_SUITE.md` – Coverage matrix, frameworks
- `md/testing/TEST_CHECKLIST.md` – Pre-release validation
- `md/debugging/DIAGNOSTIC_LOG_GUIDE.md` – ML Kit debugging

**Config**:
- `res/raw/home_resale_domain_pack.json` – 23 categories, 10 attributes (live config)

---

**Progressive Disclosure**: Use ripgrep to locate symbols before reading files. Reference deep docs only when needed. Keep Android green.
