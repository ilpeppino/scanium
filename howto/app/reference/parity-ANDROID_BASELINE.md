# Android Baseline - Source of Truth

**Document Version:** 1.0
**Last Updated:** 2026-01-13
**Purpose:** Enumerate all implemented Android capabilities as the baseline for iOS parity

---

## Executive Summary

The Android application is a **production-ready, feature-complete** implementation with:

- Mature camera pipeline (CameraX) with multi-mode capture
- Multi-modal ML detection (object detection, barcode, OCR)
- Sophisticated on-device and cloud classification
- Room-based persistence with history tracking
- Full eBay listing integration
- Advanced UI with Compose Material 3
- Production observability (Sentry + OTLP telemetry)
- Feature flags and product flavors (prod/dev/beta)

---

## 1. CAPTURE - Camera & Frame Processing

### Module Ownership

- **Primary:** `androidApp/src/main/java/com/scanium/app/camera/`
- **Platform Layer:** `android-camera-camerax/`
- **Shared Brain:** `core-scan/` (scan mode definitions)

### Key Capabilities

#### A. Camera UX

**Entry Point:** `CameraScreen.kt`

```
androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt
```

**Features:**

- Full-screen camera preview with CameraX integration
- Single shutter button with dual-mode interaction:
    - **Tap:** Single-frame capture with sourcePhotoId tracking
    - **Long-press:** Continuous scanning with real-time detection overlay
- Real-time object detection overlay with motion enhancements
- Settings overlay for tuning detection parameters in-session
- Live diagnostics panel (dev-only): FPS, latency, frame drops

#### B. Capture/Resolution Settings

**Configuration:** `CaptureResolution` enum

```
androidApp/src/main/java/com/scanium/app/camera/CaptureResolution.kt
```

**Modes:**

- `LOW`: Reduced resolution for testing/preview
- `NORMAL`: Standard capture (default, enforced in beta/prod)
- `HIGH`: High-resolution capture (dev-only, feature-flagged)

**Runtime Enforcement:**

- Beta/prod builds clamp to NORMAL via `FeatureFlags.allowHighResolution`
- User preference stored in DataStore

#### C. Orientation Handling

**Implementation:** `CameraXManager.kt`

```
androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt
```

**Features:**

- Device rotation awareness via `OrientationEventListener`
- Image rotation degrees tracking from CameraX
- Preview surface transform for proper display orientation
- Crop rect management for frame alignment
- EXIF orientation metadata on saved images

#### D. Frame Processing Pipeline

**Components:**

```
androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt
androidApp/src/main/java/com/scanium/app/camera/ImageUtils.kt
```

**Pipeline:**

1. YUV_420_888 frame from CameraX `ImageAnalysis` use case
2. Lazy JPEG conversion (only on detection/capture)
3. Bitmap generation with performance monitoring
4. Configurable JPEG quality (default 85%)
5. Orientation correction via EXIF
6. MediaStore saving for gallery integration

**Performance Optimizations:**

- Lazy bitmap generation (only when detections found)
- Throttled frame analysis (adaptive policy)
- Background thread processing
- Watchdog recovery for camera failures (600ms initial, 800ms retry)

---

## 2. ML - Detection & Classification

### Module Ownership

- **Primary:** `androidApp/src/main/java/com/scanium/app/ml/`
- **Platform Layer:** `android-ml-mlkit/`
- **Shared Brain:** `core-domainpack/` (classification), `core-models/` (data types)

### Key Capabilities

#### A. Object Detection

**Entry Point:** `ObjectDetectorClient.kt`

```
androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt
```

**Technology:** ML Kit Object Detection & Tracking API

**Configuration:**

- Multi-object mode enabled
- Confidence threshold: 0.3 (category assignment)
- Classification enabled
- Bounding box tightening ratio: 0.04 (reduced crop margin)
- Edge gating: 10% inset margin (filters partial objects at frame edges)

**Output:**

- `DetectionResponse` with list of detected items
- Each item includes:
    - Category assignment (8 categories)
    - Confidence score
    - Normalized bounding box (0-1 range)
    - Tracking ID (ML Kit assigns)
    - Thumbnail bitmap (lazily generated)

#### B. Barcode Scanning

**Entry Point:** `BarcodeDetectorClient.kt`

```
androidApp/src/main/java/com/scanium/app/ml/BarcodeDetectorClient.kt
```

**Technology:** ML Kit Barcode Scanning API

**Supported Formats:**

- UPC-A, UPC-E
- EAN-8, EAN-13
- Code-39, Code-93, Code-128
- ITF (Interleaved 2 of 5)
- Codabar
- QR Code
- Data Matrix
- PDF-417
- Aztec

**Features:**

- All formats scanned simultaneously
- Raw value extraction
- Format identification
- Bounding box for each detected barcode
- Converts barcode → `ScannedItem` with category inference

#### C. Text Recognition (OCR)

**Entry Point:** `DocumentTextRecognitionClient.kt`

```
androidApp/src/main/java/com/scanium/app/ml/DocumentTextRecognitionClient.kt
```

**Technology:** ML Kit Text Recognition v2 (Latin script)

**Configuration:**

- Minimum text length: 3 characters
- Maximum text length: 10KB (SEC-006: prevents memory exhaustion)
- Text block aggregation (all blocks → single document item)

**Output:**

- Full recognized text as string
- Bounding box for text region
- Confidence per text element
- Language script identification

#### D. Classification

**Entry Point:** `ClassificationOrchestrator.kt`

```
androidApp/src/main/java/com/scanium/app/ml/classification/ClassificationOrchestrator.kt
```

**Architecture:**

- Adapter layer delegating to shared KMP `ClassificationOrchestrator`
- Dual-mode classification:
    1. **ON_DEVICE:** CLIP-based, offline, no latency
    2. **CLOUD:** Backend API with Vision insights
- Configurable concurrency (default: 2 parallel requests)
- Retry logic with exponential backoff (default: 3 retries)

**On-Device Classifier:**

```
androidApp/src/main/java/com/scanium/app/ml/classification/OnDeviceClassifier.kt
```

- CLIP model for zero-shot classification
- Category taxonomy from Domain Pack
- Offline-capable
- Returns category + confidence

**Cloud Classifier:**

```
androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifier.kt
```

- Backend API endpoint: `/v1/classify`
- HMAC-SHA256 request signing (SEC-003)
- Vision insights (color, brand, attributes)
- Conflict detection between on-device and cloud results
- Retry interceptor with backoff

**Cloud Call Gating:**

```
androidApp/src/main/java/com/scanium/app/ml/classification/CloudCallGate.kt
```

- Cost-aware gating to avoid excessive cloud requests
- Configurable thresholds (user preferences)

---

## 3. TRACKING - Object Tracking & Aggregation

### Module Ownership

- **Shared Brain:** `core-tracking/src/main/java/com/scanium/app/tracking/`
- **Android Adapter:** Type aliases in `androidApp`

### Key Capabilities

#### A. Object Tracking

**Implementation:** `ObjectTracker.kt`

```
core-tracking/src/main/java/com/scanium/app/tracking/ObjectTracker.kt
```

**Purpose:**

- Tracks detections across frames
- Assigns stable tracking IDs
- Maintains object state (position, velocity)
- Handles occlusion and re-appearance

**Output:**

- `DetectionInfo` with tracking state
- Used for overlay rendering (bounding boxes follow objects)

#### B. Item Aggregation

**Implementation:** `ItemAggregator.kt`

```
core-tracking/src/main/java/com/scanium/app/aggregation/ItemAggregator.kt
```

**Purpose:**

- Aggregates multiple detections of same object
- Deduplication based on spatial overlap (IoU threshold)
- Confidence tier assignment (Low/Medium/High)

**Aggregation Presets:**

- Configurable via settings
- Determines when to "lock in" a detection

---

## 4. DOMAIN PACKS - Taxonomies & Classification

### Module Ownership

- **Shared Brain:** `core-domainpack/`
- **Android Adapter:** Embedded JSON asset loading

### Key Capabilities

#### A. Domain Pack Loading

**Entry Point:** `DomainPackProvider.kt`

```
core-domainpack/src/main/java/com/scanium/app/domain/DomainPackProvider.kt
```

**Features:**

- Singleton initialization at app startup
- Loads from `res/raw/home_resale_domain_pack.json`
- Sets up `CategoryEngine` for taxonomy mapping

#### B. Domain Pack Validation

**Schema:** `DomainPack.kt`

```
core-domainpack/src/main/java/com/scanium/app/domain/config/DomainPack.kt
```

**Fields:**

- `id`, `name`, `version`, `description`
- `categories`: Fine-grained taxonomy (e.g., "sofa", "laptop")
- `attributes`: Extractable properties (brand, color, condition)

**Validation:**

- JSON deserialization with kotlinx.serialization
- Schema version check
- Required field validation

#### C. Classification Flow

**Components:**

```
core-domainpack/src/main/java/com/scanium/app/domain/config/DomainCategory.kt
core-domainpack/src/main/java/com/scanium/app/domain/config/DomainAttribute.kt
core-domainpack/src/main/java/com/scanium/app/domain/category/CategoryEngine.kt
```

**Flow:**

1. Detection → coarse category (8 categories)
2. Domain Pack → fine-grained category mapping
3. `CategoryEngine` → applies taxonomy rules
4. `DomainAttribute` → extraction methods (OCR, CLIP, Barcode, Cloud)

**Category Hierarchy:**

- Coarse: `ItemCategory` enum (8 categories: Fashion, HomeGood, Food, etc.)
- Fine: `DomainCategory` (100+ categories in pack)
- Mapping: Configured per Domain Pack

---

## 5. STORAGE - Persistence & Export

### Module Ownership

- **Primary:** `androidApp/src/main/java/com/scanium/app/items/persistence/`
- **Export:** `androidApp/src/main/java/com/scanium/app/items/export/`
- **Shared Export:** `shared/core-export/`

### Key Capabilities

#### A. Local Persistence (Room Database)

**Entry Point:** `ScannedItemDatabase.kt`

```
androidApp/src/main/java/com/scanium/app/items/persistence/ScannedItemDatabase.kt
```

**Tables:**

1. `scanned_items`: Main item storage
2. `scanned_item_history`: Audit trail with timestamps
3. `listing_drafts`: eBay draft storage

**DAO Operations:**

```
androidApp/src/main/java/com/scanium/app/items/persistence/ScannedItemDao.kt
androidApp/src/main/java/com/scanium/app/selling/persistence/ListingDraftDao.kt
```

**Features:**

- CRUD with upsert (insert or update)
- Batch operations for bulk inserts
- History tracking with latest hash queries
- Listing draft lifecycle management
- Flow-based reactive queries for UI

#### B. Saved Images

**Implementation:** `MediaStoreSaver.kt`

```
androidApp/src/main/java/com/scanium/app/media/MediaStoreSaver.kt
```

**Features:**

- Android 10+ scoped storage support
- Saves to MediaStore Pictures collection
- Returns `content://` Uri for sharing
- EXIF metadata preservation
- MIME type: image/jpeg

**Cache Management:**

```
androidApp/src/main/java/com/scanium/app/media/StorageHelper.kt
```

- Cache directory management
- Temporary file handling
- Cleanup on low storage

#### C. Export Functionality

**Formats:**

1. **CSV Export:** `CsvExportWriter.kt`
   ```
   androidApp/src/main/java/com/scanium/app/items/export/CsvExportWriter.kt
   ```
    - Tabular CSV with headers
    - Item attributes and classifications
    - Price ranges, confidence scores

2. **Zip Export:** Referenced in `ItemsListScreen.kt`
    - Bundles images + CSV data
    - ShareSheet integration for file sharing

**Shared Export Module:**

```
shared/core-export/
```

- KMP-based export logic (used by both platforms)
- Format-agnostic export pipeline

---

## 6. UI - Screens & Components

### Module Ownership

- **Primary:** `androidApp/src/main/java/com/scanium/app/ui/`
- **Theme:** `androidApp/src/main/java/com/scanium/app/ui/theme/`

### Key Capabilities

#### A. Item List Screen

**Entry Point:** `ItemsListScreen.kt`

```
androidApp/src/main/java/com/scanium/app/items/ItemsListScreen.kt
```

**Features:**

- Scrollable list with thumbnail previews
- Item selection (single + multi-select)
- Actions: Edit, Delete, Share, Export
- Empty state handling
- Real-time classification progress indicators
- Pull-to-refresh
- Search/filter (dev-only)

#### B. Item Details Dialog

**Entry Point:** `EditItemScreenV3.kt`

```
androidApp/src/main/java/com/scanium/app/items/EditItemScreenV3.kt
```

**Features:**

- Full-screen item editor
- Image preview with pinch-to-zoom
- Category override dropdown
- Price range manual entry
- Recognized text display (OCR results)
- Barcode display with copy button
- Confidence score visualization
- Save/Cancel actions

#### C. Gestures & Interactions

**Implementation:** Compose gesture modifiers

**Supported:**

- Tap: Item selection
- Long-press: Multi-select mode activation
- Swipe: Delete action (iOS-style)
- Pinch-to-zoom: Image preview
- Pull-to-refresh: List reload

#### D. Delete/Selection Flows

**Entry Point:** `ItemsViewModel.kt`

```
androidApp/src/main/java/com/scanium/app/items/ItemsViewModel.kt
```

**Features:**

- Single item delete with confirmation dialog
- Batch delete for selected items
- Undo support (soft delete with 5-second window)
- Selection state management (StateFlow)

---

## 7. NETWORKING - Backend & eBay Integration

### Module Ownership

- **Primary:** `androidApp/src/main/java/com/scanium/app/network/`
- **eBay:** `androidApp/src/main/java/com/scanium/app/selling/`

### Key Capabilities

#### A. Backend Integration

**Entry Point:** `RequestSigner.kt`

```
androidApp/src/main/java/com/scanium/app/network/security/RequestSigner.kt
```

**Security (SEC-003):**

- HMAC-SHA256 request signing
- Headers:
    - `X-Request-Timestamp`: Unix timestamp (prevents replay attacks)
    - `X-Request-Signature`: HMAC signature
- Supports JSON and multipart requests
- API key from `BuildConfig.SCANIUM_API_KEY`

**Endpoints:**

- `/v1/classify`: Cloud classification
- `/v1/items`: Item metadata sync
- `/v1/listings`: eBay listing management

**HTTP Client:** OkHttp 4.12.0

- Connection pooling
- Retry interceptor with exponential backoff
- Certificate pinning (optional, SEC-003)

#### B. eBay Integration

**Interface:** `EbayApi.kt`

```
androidApp/src/main/java/com/scanium/app/selling/data/EbayApi.kt
```

**Methods:**

- `createListing(draft: ListingDraft): Listing`
- `getListingStatus(listingId: ListingId): ListingStatus`
- `endListing(listingId: ListingId): Unit`

**Implementation:**

- Production: `EbayMarketplaceService.kt` (OAuth 2.0 flow)
- Dev/Test: `MockEbayApi.kt` (configurable responses)

**Features:**

- Listing draft creation with AI-generated titles/descriptions
- Image upload (up to 12 images)
- Category assignment from eBay taxonomy
- Price/shipping configuration
- Listing lifecycle management (active → sold → ended)

---

## 8. LOGGING/MONITORING - Observability

### Module Ownership

- **Primary:** `androidApp/src/main/java/com/scanium/app/crash/`
- **Telemetry:** `shared/telemetry/`, `shared/telemetry-contract/`
- **Logging:** `androidApp/src/main/java/com/scanium/app/logging/`

### Key Capabilities

#### A. Sentry Crash Reporting

**Entry Point:** `AndroidCrashPortAdapter.kt`

```
androidApp/src/main/java/com/scanium/app/crash/AndroidCrashPortAdapter.kt
```

**Features:**

- Bridges vendor-neutral `CrashPort` to Sentry SDK
- Exception capture with stack traces
- Tags for filtering/grouping:
    - `app.flavor`: prod/dev/beta
    - `app.build_type`: debug/release
    - `user.edition`: FREE/PREMIUM
- Breadcrumbs for event sequence tracking
- Diagnostic bundles (capped at 128KB, SEC-004)
- Thread-safe integration

**Configuration:**

```kotlin
// androidApp/build.gradle.kts
buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
```

**Sentry DSN:** Embedded in APK (intentionally, per Sentry design)

- Mitigations: Rate limiting, IP filtering, DSN rotation

#### B. OTLP Telemetry

**Configuration:**

```kotlin
// androidApp/build.gradle.kts
buildConfigField("String", "OTLP_ENDPOINT", "\"$otlpEndpoint\"")
buildConfigField("boolean", "OTLP_ENABLED", otlpEnabled)
```

**Implementation:** `shared/telemetry/`

- OpenTelemetry Protocol (OTLP) export
- Traces: Classification requests, API calls
- Metrics: Frame processing FPS, detection latency
- Logs: Structured logging with context

**Integration Points:**

- `ClassificationOrchestrator`: Classification timing
- `CameraXManager`: Frame processing metrics
- `RequestSigner`: API request traces

#### C. Logging Infrastructure

**Entry Point:** `ScaniumLog.kt`

```
androidApp/src/main/java/com/scanium/app/logging/ScaniumLog.kt
```

**Features:**

- Centralized logging facade
- Log levels: VERBOSE, DEBUG, INFO, WARN, ERROR
- Correlation ID propagation (see below)

**Correlation IDs:**

```
androidApp/src/main/java/com/scanium/app/logging/CorrelationIds.kt
```

- UUID-based trace ID generation
- Propagates across async boundaries
- Used for distributed tracing

---

## 9. SECURITY/PRIVACY - Permissions & Data Handling

### Module Ownership

- **Primary:** `androidApp/`
- **Documentation:** `docs/security/` (if present)

### Key Capabilities

#### A. Permissions

**Manifest:** `androidApp/src/main/AndroidManifest.xml`

**Required Permissions:**

1. `CAMERA`: Camera access for frame capture
2. `WRITE_EXTERNAL_STORAGE`: Image saving (Android < 10)
3. `READ_EXTERNAL_STORAGE`: Image loading (Android < 13)
4. `INTERNET`: Backend API, eBay integration
5. `ACCESS_NETWORK_STATE`: Connectivity checks

**Runtime Permission Handling:**

```
androidApp/src/main/java/com/scanium/app/ftue/PermissionEducationDialog.kt
```

- Accompanist Permissions library
- Educational dialog before requesting
- Graceful degradation if denied

#### B. Data Handling

**SEC-001: API Key Management**

- API keys from `local.properties` (not committed)
- Environment variables in CI/CD
- Stored in Android Keystore at runtime (future enhancement)

**SEC-002: Sentry DSN**

- Embedded in APK (Sentry's designed behavior)
- Semi-public by design (does not grant read access)
- Mitigations: Rate limiting, IP filtering, DSN rotation

**SEC-003: TLS Certificate Pinning**

- Optional certificate pin hash in config
- SHA-256 public key pinning
- Prevents MITM attacks

**SEC-004: Diagnostic Bundle Size**

- Crash diagnostics capped at 128KB
- Prevents memory exhaustion on Sentry

**SEC-006: OCR Text Length**

- Maximum 10KB per document
- Prevents memory exhaustion

---

## 10. ADDITIONAL ANDROID FEATURES

### A. Audio/Sound Feedback

**Entry Point:** `AndroidSoundManager.kt`

```
androidApp/src/main/java/com/scanium/app/audio/AndroidSoundManager.kt
```

**Features:**

- ToneGenerator-based playback
- Sounds: CAPTURE, ITEM_ADDED, SELECT, DELETE, ERROR, SEND, RECEIVED, EXPORT
- Rate limiting to prevent audio spam
- Device sound policy awareness (silent mode)
- Settings-driven enable/disable

### B. Accessibility (FTUE)

**Entry Point:** `TourViewModel.kt`

```
androidApp/src/main/java/com/scanium/app/ftue/TourViewModel.kt
```

**Features:**

- First-Time User Experience (FTUE) guided walkthrough
- Permission education dialogs
- `tourTarget` modifier for highlighting UI elements
- Skip/complete tracking

### C. Billing/Monetization

**Entry Point:** `BillingRepository.kt`

```
androidApp/src/main/java/com/scanium/app/billing/BillingRepository.kt
```

**Features:**

- Google Play Billing integration
- User editions: FREE, PREMIUM
- Entitlement tracking (cloud + local cache)
- Paywall screen for upsell
- Purchase token validation

### D. Voice/Assistant

**Entry Point:** `VoiceStateMachine.kt`

```
androidApp/src/main/java/com/scanium/app/voice/VoiceStateMachine.kt
```

**States:** IDLE, LISTENING, TRANSCRIBING, SPEAKING, ERROR

**Assistant Features:**

- AI-powered listing generation
- Multi-step request staging
- Progress tracking (Sending → Thinking → Extracting Vision → Generating)
- Error recovery and retry logic

### E. Theme & Design System

**Entry Point:** `Theme.kt`

```
androidApp/src/main/java/com/scanium/app/ui/theme/Theme.kt
```

**Features:**

- Material 3 with custom branding
- Theme modes: SYSTEM, LIGHT, DARK
- User preference stored in DataStore

**Motion Components:**

```
androidApp/src/main/java/com/scanium/app/ui/motion/
```

- Lightning scan pulse animation
- Scan frame appear animation
- Price count-up animation
- Motion-enhanced overlay effects

---

## DEPENDENCY SUMMARY

### Android-Specific Modules

- `androidApp`: Main application module
- `android-camera-camerax`: CameraX integration (currently minimal)
- `android-ml-mlkit`: ML Kit wrappers (currently minimal)
- `android-platform-adapters`: Platform-specific adapters

### Shared KMP Modules (Used by Android)

- `core-models`: Shared data types (ScannedItem, ImageRef, etc.)
- `core-tracking`: Object tracking and aggregation
- `core-domainpack`: Domain pack configuration
- `core-scan`: Scan mode definitions
- `core-contracts`: Service contracts
- `shared/core-export`: Export functionality
- `shared/telemetry`: Telemetry facade
- `shared/telemetry-contract`: Telemetry interfaces

---

## ARCHITECTURE PATTERNS

1. **ARCH-001: Hilt DI Framework**
    - Replaces manual DI
    - Modules: AppModule, ClassificationModule, DatabaseModule, BillingModule

2. **MVVM with Compose**
    - ViewModels with StateFlow for reactive UI
    - Compose-based UI (no XML)

3. **Adapter Pattern**
    - `AndroidCrashPortAdapter`: Vendor-neutral crash reporting
    - `ClassifierAdapter`: Shared classification interface

4. **Facade Pattern**
    - Telemetry facade for observability
    - SoundManager abstraction

5. **Reducer Pattern**
    - `VoiceStateMachine`: Testable state management

6. **Feature Flags & Product Flavors**
    - BuildConfig-driven feature gating
    - Flavors: prod, dev, beta (side-by-side installation)

---

## BUILD CONFIGURATION

### Product Flavors

1. **prod**: Production, no dev mode, applicationId: `com.scanium.app`
2. **dev**: Developer mode enabled, applicationId: `com.scanium.app.dev`
3. **beta**: Beta testing, no dev mode, applicationId: `com.scanium.app.beta`

### Build Types

1. **debug**: LAN backend URL, no obfuscation
2. **release**: Remote backend URL, ProGuard enabled, signed

### Security Features

- SBOM generation (CycloneDX)
- CVE scanning (OWASP Dependency-Check)
- Fail build on CVSS >= 7.0

---

## KEY STATISTICS

- **Total Kotlin files in androidApp:** 100+ files
- **Lines of Kotlin code:** ~15,000+ lines (estimated)
- **Compose screens:** 15+ screens
- **Room tables:** 3 tables (scanned_items, scanned_item_history, listing_drafts)
- **ML models:** 3 integrated (Object Detection, Barcode, Text Recognition)
- **Product flavors:** 3 (prod, dev, beta)
- **Supported Android versions:** API 24-35 (Android 7.0 - Android 14+)

---

## END OF ANDROID BASELINE
