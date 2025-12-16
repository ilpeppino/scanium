***REMOVED*** CLAUDE.md

Guidance for Claude Code when working with **Scanium** – a privacy-first Android app for real-time object detection, barcode scanning, and document OCR.

**Primary detection** happens **on-device** using Google ML Kit. **Enhanced classification** uses cloud API (default) or on-device CLIP (future) for fine-grained category recognition (23 categories via Domain Pack).

***REMOVED******REMOVED*** Project Essentials

- **Package**: `com.scanium.app`
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Min/Target SDK**: 24 / 34 (Android 7.0 / 14)
- **Required Java**: 17 (see `SETUP.md`)
- **Architecture**: Multi-module Gradle (9 modules), MVVM, no DI framework

***REMOVED******REMOVED*** Module Structure

```
scanium/
├── androidApp/                    ***REMOVED*** Main Android app module (UI, navigation, entry point)
├── core-models/                   ***REMOVED*** Platform-independent data models and portable types
│   └── ImageRef, NormalizedRect, ItemCategory, ScanMode, ScannedItem, DetectionResult, RawDetection
├── core-tracking/                 ***REMOVED*** Platform-independent tracking and aggregation logic
│   └── ObjectTracker, ObjectCandidate, ItemAggregator, Logger interface
├── core-domainpack/               ***REMOVED*** Domain Pack system (categories, attributes, repository)
│   └── DomainPack, DomainCategory, BasicCategoryEngine, LocalDomainPackRepository
├── core-scan/                     ***REMOVED*** Scan-related logic (placeholder for future KMP scan contracts)
├── core-contracts/                ***REMOVED*** Platform-independent contracts and interfaces
├── android-ml-mlkit/              ***REMOVED*** ML Kit Android wrappers (placeholder for modularization)
├── android-camera-camerax/        ***REMOVED*** CameraX Android wrappers (placeholder for modularization)
├── android-platform-adapters/     ***REMOVED*** Conversions between Android types and portable types
    └── ImageAdapters (Bitmap ↔ ImageRef), RectAdapters (Rect/RectF ↔ NormalizedRect)
```

**Dependencies**:
- `androidApp` → `android-platform-adapters`, `android-ml-mlkit`, `android-camera-camerax`, `core-scan`, `core-domainpack`, `core-tracking`, `core-contracts`, `core-models`
- `core-domainpack` → `core-models`
- `core-tracking` → `core-models`
- `android-platform-adapters` → `core-models`

**Note**: `app/` module is legacy (resources only), all code moved to `androidApp/`

***REMOVED******REMOVED*** Commands

```bash
***REMOVED*** Build (auto-detects Java 17)
./build.sh assembleDebug
./build.sh assembleRelease

***REMOVED*** Test (local with Android SDK + Java 17)
./gradlew test                              ***REMOVED*** All unit tests (175+)
./gradlew test --tests "*ObjectTracker*"    ***REMOVED*** Single test class
./gradlew connectedAndroidTest              ***REMOVED*** Instrumented tests

***REMOVED*** CI-First Testing (Codex container without Android SDK)
***REMOVED*** Push to main → GitHub Actions builds APK → Download artifact → Install on device
***REMOVED*** See docs/CI_TESTING.md for details

***REMOVED*** Install & Debug
./gradlew installDebug
adb logcat | grep -E "ObjectTraacker|CameraXManager|ObjectDetector"
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

***REMOVED******REMOVED******REMOVED*** Core Modules (Platform-Independent)

**:core-models** – Portable types and data models (Android-free)
- `model/ImageRef.kt` – Platform-agnostic image reference (sealed class: `ImageRef.Bytes`)
- `model/NormalizedRect.kt` – Normalized bounding box (0-1 coordinates) with `isNormalized()`, `clampToUnit()`
- `ml/ItemCategory.kt` – Enum mapping ML Kit's 5 coarse categories
- `ml/DetectionResult.kt` – Real-time detection result (uses `NormalizedRect`, removed legacy `Rect`)
- `ml/RawDetection.kt` – Raw ML Kit detection (transitioning: has `boundingBox: Rect?`, `bboxNorm: NormalizedRect?`, `thumbnailRef: ImageRef?`)
- `items/ScannedItem.kt` – Immutable item model (uses `ImageRef`, `NormalizedRect`, still has `Uri` for platform compatibility)
- `camera/ScanMode.kt` – Enum for OBJECT_DETECTION | BARCODE | DOCUMENT_TEXT
- `ml/classification/ClassificationMode.kt` – Enum: ON_DEVICE | CLOUD

**:core-tracking** – Platform-independent tracking and aggregation (Android-free)
- `tracking/ObjectTracker.kt` – Multi-frame tracking using `NormalizedRect` for spatial matching (prefers normalized boxes)
- `tracking/ObjectCandidate.kt` – Candidate state (uses `NormalizedRect`, removed legacy `RectF`)
- `tracking/TrackerConfig.kt` – Tunable thresholds
- `tracking/Logger.kt` – Platform-agnostic logging interface
- `tracking/DetectionInfo.kt` – Input to tracker (uses `NormalizedRect`, `ImageRef`)
- `aggregation/ItemAggregator.kt` – Similarity-based session deduplication (uses Logger)
- `aggregation/AggregationPresets.kt` – 6 presets (REALTIME used by default)
- `aggregation/AggregatedItem.kt` – Merged detection with confidence/timestamps

**:core-domainpack** – Domain Pack system (moved from androidApp)
- `domain/config/DomainPack.kt` – 23 categories + 10 attributes schema
- `domain/config/DomainCategory.kt`, `DomainAttribute.kt` – Category and attribute models
- `domain/repository/DomainPackRepository.kt` – Repository interface
- `domain/repository/LocalDomainPackRepository.kt` – Loads JSON from `res/raw/home_resale_domain_pack.json`
- `domain/category/BasicCategoryEngine.kt` – ML Kit label → DomainCategory matching
- `domain/DomainPackProvider.kt` – Singleton initialized in `MainActivity`

**:android-platform-adapters** – Android ↔ portable type conversions
- `adapters/ImageAdapters.kt` – `Bitmap.toImageRefJpeg()`, `ImageRef.Bytes.toBitmap()`
- `adapters/RectAdapters.kt` – Rect/RectF ↔ NormalizedRect conversions (placeholder)

***REMOVED******REMOVED******REMOVED*** App Module (Android-Specific)

**Camera & Processing**
- `camera/CameraXManager.kt` – CameraX lifecycle, mode routing, gesture handling
- `camera/ui/VerticalThresholdSlider.kt`, `ClassificationModeToggle.kt` – UI controls

**ML Kit Integration** (Android wrappers)
- `ml/ObjectDetectorClient.kt` – Wraps ML Kit Object Detection, converts to portable types
- `ml/BarcodeScannerClient.kt` – Wraps ML Kit Barcode Scanning
- `ml/DocumentTextRecognitionClient.kt` – Wraps ML Kit Text Recognition
- `ml/PricingEngine.kt` – Mock EUR price generation (replace with real API)

**State Management**
- `items/ItemsViewModel.kt` – Centralized `StateFlow<List<ScannedItem>>`, ID-based dedup
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

***REMOVED******REMOVED*** KMP/iOS Porting Status

**Goal**: Share Scanium's "brain" (tracking, aggregation, state management) across Android/iOS while keeping platform-specific UI/camera/ML.

***REMOVED******REMOVED******REMOVED*** ✅ Completed (Phase 1: Core Modules)
1. **Multi-module Gradle structure established**:
   - `:core-models` – Platform-independent data models
   - `:core-tracking` – Platform-independent tracking/aggregation
   - `:app` – Android-specific implementation
2. **Portable types introduced**:
   - `ImageRef` – Platform-agnostic image reference (replaces `Bitmap`)
   - `NormalizedRect` – Portable bounding box with 0-1 coordinates (replaces `RectF`)
   - `Logger` – Platform-agnostic logging interface (replaces `android.util.Log`)
3. **Core modules are Android-free**:
   - ✅ `core-models`: No Android dependencies
   - ✅ `core-tracking`: No Android dependencies (uses Logger, ImageRef, NormalizedRect)
   - ✅ CI builds successfully without Android SDK in core modules

***REMOVED******REMOVED******REMOVED*** 🚧 Remaining Work (Phase 2: KMP Conversion)
1. Convert `:core-models` to KMP `commonMain`
2. Convert `:core-tracking` to KMP `commonMain`
3. Implement platform actuals:
   - Android: `AndroidLogger` wrapping `android.util.Log`
   - iOS: `IOSLogger` wrapping `NSLog`/`os_log`
4. Create iOS app target (`:iosApp`) with SwiftUI
5. Implement iOS platform providers for ML/camera

***REMOVED******REMOVED******REMOVED*** Shared Code Rules
1. **NO Android Dependencies** in `:core-*` modules:
   - ❌ Forbidden: `android.*`, `androidx.*`, `CameraX`, `ML Kit` classes
   - ✅ Allowed: Kotlin stdlib, Coroutines, Kotlinx Serialization, expect/actual
2. **Platform Interfaces**:
   - ✅ `Logger` – Platform-agnostic logging (implemented)
   - ✅ `ImageRef` – Platform-agnostic image (implemented)
   - ✅ `NormalizedRect` – Platform-agnostic geometry (implemented)
   - 🚧 Future: `expect interface CameraProvider`, `expect interface MLProvider`
3. **Platform-Specific** (Stays in `:app` or future `:iosApp`):
   - `CameraXManager` → Android only
   - `ObjectDetectorClient`, `BarcodeScannerClient` → Wrap in platform providers
   - Compose UI → Android; SwiftUI → iOS
   - `MainActivity`, navigation → Platform-specific entry points

***REMOVED******REMOVED******REMOVED*** Non-Negotiables
- Android must remain fully functional during/after KMP migration
- No breaking changes to Android UI/UX
- Platform-specific optimizations allowed (e.g., Android ML Kit vs iOS Core ML)
- Shared code must not assume Android threading (use `Dispatchers.Default`, not `Dispatchers.Main`)
- CI must validate Android builds on every push (enforced via GitHub Actions)

***REMOVED******REMOVED*** Known Limitations

- **No persistence**: In-memory only (ViewModel state cleared on app close)
- **Mocked pricing**: `PricingEngine.kt` generates EUR ranges locally
- **Mocked eBay**: `MockEbayApi` simulates marketplace (ready for real API swap)
- **ML Kit categories**: 5 coarse categories → mitigated by Domain Pack (23 fine-grained) + Cloud Classification
- **Core modules not yet KMP**: Platform-independent but need conversion to `commonMain/androidMain/iosMain`
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
- `docs/CI_TESTING.md` – CI-first testing workflow for Codex containers
- `md/testing/TEST_SUITE.md` – Coverage matrix, frameworks
- `md/testing/TEST_CHECKLIST.md` – Pre-release validation
- `md/debugging/DIAGNOSTIC_LOG_GUIDE.md` – ML Kit debugging

**CI/CD**:
- `.github/workflows/android-debug-apk.yml` – Builds APK on every push to main
- Artifact: `scanium-app-debug-apk` (download from GitHub Actions)

**Config**:
- `res/raw/home_resale_domain_pack.json` – 23 categories, 10 attributes (live config)

---

**Progressive Disclosure**: Use ripgrep to locate symbols before reading files. Reference deep docs only when needed. Keep Android green.
