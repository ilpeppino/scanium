# iOS Parity Plan - Phased Roadmap

**Document Version:** 1.0
**Last Updated:** 2026-01-13
**Purpose:** Phased plan to bring iOS to Android parity while keeping Android stable

---

## Executive Summary

This plan achieves iOS-Android parity through **6 phases** optimized for:
- **Minimal Android disruption:** All work happens in iOS codebase
- **Incremental delivery:** Each phase is independently testable
- **Parallelization:** Multiple tracks can run concurrently (see PR Roadmap)
- **Risk mitigation:** Critical infrastructure first, features layered on top

**Total Estimated Duration:** 16-24 weeks (4-6 months) with 2-3 engineers

---

## Guiding Principles

### 1. DO NOT TOUCH ANDROID
- All PRs target `iosApp/` and `shared/` modules only
- Android code is frozen as baseline (source of truth)
- Any shared module changes must maintain backward compatibility with Android

### 2. Shared Brain First
- Leverage existing KMP shared modules (`core-tracking`, `core-domainpack`, `core-scan`, etc.)
- iOS should consume shared logic, not reimplement
- Only create iOS-specific code for platform adapters (UI, camera, networking)

### 3. Incremental Integration
- Each phase produces a working, testable iOS app
- Phase N builds on Phase N-1 without breaking previous work
- Feature flags gate incomplete work (e.g., `useMocks` toggle)

### 4. Definition of Done (Per Phase)
- All acceptance criteria met
- No HIGH-risk gaps remaining in phase scope
- PR merged to main branch
- iOS app builds and launches without crashes
- Manual QA pass (checklist provided per phase)

---

## Phase 0: Validation & Guardrails (Week 1-2, 1 engineer)

**Objective:** Establish parity definitions, build gates, and validation harness before implementation

### Tasks

#### 0.1 Parity Tracking Infrastructure
**Acceptance Criteria:**
- Parity dashboard created (spreadsheet or GitHub project board)
- All 78 gaps from Gap Matrix tracked with status (Backlog/In Progress/Done)
- Weekly status report template created

**Deliverables:**
- `docs/parity/PARITY_DASHBOARD.md` or GitHub Project board
- Status report template

**Assignee:** Lead engineer

---

#### 0.2 Forbidden Imports Linter
**Acceptance Criteria:**
- CI check that fails if iOS PRs touch `androidApp/` or Android-specific modules
- Allowed paths: `iosApp/`, `shared/`, `docs/parity/`
- Documented exceptions process

**Deliverables:**
- `.github/workflows/ios-parity-guard.yml` CI workflow
- Grep-based check: `git diff --name-only origin/main | grep -v "^iosApp\|^shared\|^docs/parity"`

**Assignee:** DevOps/lead engineer

---

#### 0.3 Android Baseline Freeze
**Acceptance Criteria:**
- Android codebase tagged with baseline version (e.g., `android-baseline-v1.0`)
- ANDROID_BASELINE.md committed to `docs/parity/`
- Android CI green and passing (all tests, builds, CVE scans)

**Deliverables:**
- Git tag: `android-baseline-v1.0`
- CI status badge in README

**Assignee:** Lead engineer

---

#### 0.4 iOS Build Validation Harness
**Acceptance Criteria:**
- iOS app builds successfully on CI (Xcode Cloud or GitHub Actions)
- Launch test (XCUITest): app launches without crash
- Smoke test: ContentView displays (even with mocks)

**Deliverables:**
- `.github/workflows/ios-build.yml` or Xcode Cloud scheme
- `iosAppTests/LaunchTest.swift` (XCTest)

**Assignee:** iOS engineer

---

### Phase 0 Definition of Done
- ✅ All 4 tasks complete
- ✅ CI green for iOS and Android
- ✅ Parity dashboard live
- ✅ Team trained on parity workflow

---

## Phase 1: Shared Brain Readiness (Week 3-5, 2 engineers)

**Objective:** Ensure all shared KMP APIs used by Android are callable from iOS

### Tasks

#### 1.1 XCFramework Build & Distribution
**Acceptance Criteria:**
- Gradle task successfully builds XCFrameworks for all shared modules
- XCFrameworks copied to `iosApp/Frameworks/`
- Xcode project configured with framework search paths
- iOS app imports `Shared` module without errors

**Deliverables:**
- Gradle task: `./gradlew :shared:core-models:assembleXCFramework` (repeat for all modules)
- Xcode framework linking (in project.pbxproj)
- `docs/BUILD_IOS_FRAMEWORKS.md` documentation

**Dependencies:** Phase 0 complete

**Assignee:** KMP specialist

**Evidence Check:**
- `iosApp/Frameworks/Shared.xcframework` exists
- `import Shared` compiles in SwiftUI code

---

#### 1.2 Shared Module Initialization
**Acceptance Criteria:**
- `SharedBridge.swift` initializes KMP session (remove TODOs at lines 74, 78)
- `KmpBackedSession.start()` calls shared session lifecycle
- `SharedDataSource.loadItems()` successfully calls `SampleItemsProvider().sampleItems()`
- Feature flag `useMocks` can toggle between mock and real data

**Deliverables:**
- Updated `SharedBridge.swift` with KMP initialization
- AppDelegate or App struct initializes session on launch
- UserDefaults-backed `useMocks` flag

**Dependencies:** 1.1 XCFramework build

**Assignee:** iOS engineer

**Evidence Check:**
- Set `useMocks = false`, app displays items from shared KMP

---

#### 1.3 Type Mapping Validation
**Acceptance Criteria:**
- All Kotlin types (ScannedItem, ImageRef, NormalizedRect, etc.) map correctly to Swift
- No runtime crashes from nullability mismatches
- Test suite validates type conversions (unit tests)

**Deliverables:**
- `iosAppTests/SharedTypeTests.swift` (XCTest suite)
- Fixed nullability issues (e.g., optional chaining)

**Dependencies:** 1.2 Shared initialization

**Assignee:** iOS engineer

**Evidence Check:**
- Test suite passes (100% of type mapping tests green)

---

#### 1.4 Domain Pack Loading
**Acceptance Criteria:**
- `home_resale_domain_pack.json` added to iOS bundle
- Shared `DomainPackProvider` initialized with JSON from bundle
- Category taxonomy available for classification

**Deliverables:**
- JSON file in `iosApp/ScaniumiOS/Resources/`
- Bundle loading code: `Bundle.main.path(forResource:ofType:)`
- Pass JSON to shared `DomainPackProvider`

**Dependencies:** 1.2 Shared initialization

**Assignee:** iOS engineer

**Evidence Check:**
- App logs show "Domain Pack loaded: home_resale_domain_pack v1.0"

---

### Phase 1 Definition of Done
- ✅ All 4 tasks complete
- ✅ iOS app successfully calls shared KMP modules
- ✅ Type mapping tests pass
- ✅ Domain Pack loaded and validated
- ✅ No Android code touched

---

## Phase 2: iOS Platform Adapters (Week 6-9, 2 engineers)

**Objective:** Complete and wire iOS platform adapters for detection services

### Tasks

#### 2.1 CoreML Object Detection Model Integration
**Acceptance Criteria:**
- CoreML model obtained (YOLOv5 or MobileNet converted to .mlmodel)
- Model added to Xcode project target
- `CoreMLObjectDetectionService.swift` initializes model successfully
- Detection test passes (unit test with sample image)

**Deliverables:**
- `iosApp/Models/ObjectDetection.mlmodel` (or equivalent)
- Updated `CoreMLObjectDetectionService.swift` (remove stub)
- `iosAppTests/ObjectDetectionTests.swift`

**Dependencies:** Phase 1 complete

**Assignee:** ML engineer + iOS engineer

**Evidence Check:**
- Service detects objects in test image with bounding boxes

---

#### 2.2 Detection Orchestrator
**Acceptance Criteria:**
- `DetectionOrchestrator.swift` created
- Dispatches frames to 3 services in parallel: barcode, OCR, object detection
- Aggregates results and returns `DetectionResponse`
- Throttling policy implemented (time-based, configurable)

**Deliverables:**
- `iosApp/ScaniumiOS/Detection/DetectionOrchestrator.swift`
- `iosApp/ScaniumiOS/Detection/ThrottlePolicy.swift`
- Unit tests for orchestrator

**Dependencies:** 2.1 Object detection model

**Assignee:** iOS engineer

**Evidence Check:**
- Orchestrator processes 10 frames, runs detections, returns aggregated results

---

#### 2.3 Object Tracking Integration
**Acceptance Criteria:**
- Shared `ObjectTracker` (from `core-tracking`) called from iOS
- Detections passed to tracker, stable tracking IDs assigned
- Bounding boxes follow objects across frames

**Deliverables:**
- Tracking integration in `DetectionOrchestrator.swift`
- Call shared `ObjectTracker.track(detections)`

**Dependencies:** 2.2 Detection orchestrator, Phase 1

**Assignee:** iOS engineer

**Evidence Check:**
- Tracked objects maintain IDs across 5+ frames (test with video)

---

#### 2.4 Item Aggregation Integration
**Acceptance Criteria:**
- Shared `ItemAggregator` (from `core-tracking`) called from iOS
- Multiple detections of same object aggregated into single item
- Deduplication based on spatial overlap (IoU threshold)

**Deliverables:**
- Aggregation integration in detection pipeline
- Call shared `ItemAggregator.aggregate(detections)`

**Dependencies:** 2.3 Tracking integration

**Assignee:** iOS engineer

**Evidence Check:**
- 10 detections of same object → 1 aggregated item

---

### Phase 2 Definition of Done
- ✅ All 4 tasks complete
- ✅ Detection pipeline (barcode + OCR + OD) fully functional
- ✅ Tracking and aggregation integrated
- ✅ Unit tests pass for all services
- ✅ No Android code touched

---

## Phase 3: iOS UI Parity (Week 10-15, 2 engineers)

**Objective:** Build interactive UI matching Android feature set

### Track 3A: Camera & Capture (Week 10-12)

#### 3A.1 Camera Preview Screen
**Acceptance Criteria:**
- `CameraView.swift` created with `AVCaptureVideoPreviewLayer`
- Preview displays live camera feed
- Integrates existing `AVFoundationFrameSource.swift`
- Navigation from list screen to camera screen

**Deliverables:**
- `iosApp/ScaniumiOS/Views/CameraView.swift`
- Navigation link in `ContentView.swift`

**Dependencies:** Phase 2 complete

**Assignee:** iOS UI engineer

---

#### 3A.2 Shutter Button & Capture Modes
**Acceptance Criteria:**
- `ShutterButton.swift` with tap + long-press gestures
- Tap: single-frame capture, saves image to database
- Long-press: continuous scanning, live overlay updates

**Deliverables:**
- `iosApp/ScaniumiOS/Components/ShutterButton.swift`
- Gesture handlers in `CameraView.swift`

**Dependencies:** 3A.1 Camera view

**Assignee:** iOS UI engineer

---

#### 3A.3 Detection Overlay (Real-Time)
**Acceptance Criteria:**
- SwiftUI overlay on camera view
- Draws bounding boxes for detected objects
- Shows confidence scores
- Updates in real-time (30 FPS)

**Deliverables:**
- `iosApp/ScaniumiOS/Views/DetectionOverlay.swift`
- Canvas-based drawing for bounding boxes

**Dependencies:** 3A.2 Shutter button, Phase 2 (detection orchestrator)

**Assignee:** iOS UI engineer

---

#### 3A.4 Camera Permissions
**Acceptance Criteria:**
- `NSCameraUsageDescription` added to Info.plist
- Permission request on first camera access
- Graceful degradation if denied (show alert, link to settings)

**Deliverables:**
- Updated `Info.plist`
- Permission check in `CameraView.onAppear`

**Dependencies:** 3A.1 Camera view

**Assignee:** iOS engineer

---

### Track 3B: List & Detail Screens (Week 10-13)

#### 3B.1 Items List Interactivity
**Acceptance Criteria:**
- Tap item → navigate to detail screen
- Swipe to delete with confirmation dialog
- Toolbar buttons: Export, Share, Settings
- Pull-to-refresh reloads from database

**Deliverables:**
- Updated `ContentView.swift` with tap/swipe handlers
- Navigation to detail screen
- `.refreshable {}` modifier

**Dependencies:** Phase 1 (shared data)

**Assignee:** iOS UI engineer

---

#### 3B.2 Item Detail/Edit Screen
**Acceptance Criteria:**
- `ItemDetailView.swift` displays full item details
- Image preview with pinch-to-zoom
- Editable fields: label, category, price
- Save button updates database

**Deliverables:**
- `iosApp/ScaniumiOS/Views/ItemDetailView.swift`
- Image zoom with `MagnificationGesture`
- Database update on save

**Dependencies:** 3B.1 List interactivity, Phase 4 (database)

**Assignee:** iOS UI engineer

---

#### 3B.3 Selection Mode (Multi-Select)
**Acceptance Criteria:**
- Long-press item → enter selection mode
- Checkboxes appear on list items
- Toolbar actions: Delete All, Export All, Cancel

**Deliverables:**
- `EditMode` environment in `ContentView.swift`
- Batch actions in toolbar

**Dependencies:** 3B.1 List interactivity

**Assignee:** iOS UI engineer

---

#### 3B.4 Empty State & Confirmation Dialogs
**Acceptance Criteria:**
- Empty state view when list has no items
- Delete confirmation dialog (`.confirmationDialog()`)
- Error alerts for failed operations

**Deliverables:**
- `EmptyStateView.swift`
- Confirmation dialog on delete

**Dependencies:** 3B.1 List interactivity

**Assignee:** iOS UI engineer

---

### Track 3C: Settings Screens (Week 11-13)

#### 3C.1 Settings Navigation Structure
**Acceptance Criteria:**
- Settings root screen with navigation links
- Subsections: General, Camera, Privacy, Storage
- UserDefaults-backed settings storage

**Deliverables:**
- `iosApp/ScaniumiOS/Views/Settings/SettingsRootView.swift`
- Subsection screens (4 screens)

**Dependencies:** Phase 1

**Assignee:** iOS UI engineer

---

#### 3C.2 Camera Settings
**Acceptance Criteria:**
- Resolution picker (LOW/NORMAL/HIGH)
- Detection threshold sliders
- Settings persist to UserDefaults
- Applied to camera session on next launch

**Deliverables:**
- `CameraSettingsView.swift`
- Bind to `AVFoundationFrameSource` configuration

**Dependencies:** 3C.1 Settings structure

**Assignee:** iOS engineer

---

#### 3C.3 Privacy & Terms
**Acceptance Criteria:**
- Privacy screen with links to terms/policy
- Terms acceptance tracked in UserDefaults
- Displayed on first launch (onboarding)

**Deliverables:**
- `PrivacySettingsView.swift`
- Terms acceptance logic

**Dependencies:** 3C.1 Settings structure

**Assignee:** iOS UI engineer

---

### Phase 3 Definition of Done
- ✅ All Track 3A, 3B, 3C tasks complete
- ✅ Camera preview functional with live detection
- ✅ List/detail screens interactive with CRUD operations
- ✅ Settings screens functional with persistence
- ✅ UI tests pass for critical flows (capture, list, detail)
- ✅ No Android code touched

---

## Phase 4: Storage & Persistence (Week 13-15, 1 engineer, parallel with Phase 3)

**Objective:** Implement local database and image storage

### Tasks

#### 4.1 Database Technology Selection
**Acceptance Criteria:**
- Decision documented: Core Data vs SQLDelight (KMP) vs Realm
- **Recommendation:** SQLDelight (KMP) for parity with Android
- Rationale: Shared schema, shared queries, maximum code reuse

**Deliverables:**
- `docs/parity/DATABASE_DECISION.md`

**Dependencies:** Phase 1

**Assignee:** Lead engineer

---

#### 4.2 Core Data or SQLDelight Setup
**Acceptance Criteria:**
- Database schema matches Android (3 tables: scanned_items, history, drafts)
- DAO layer implemented (insert, update, delete, query)
- Migration strategy defined

**Deliverables:**
- If Core Data: `.xcdatamodeld` file + NSManagedObject subclasses
- If SQLDelight: Shared `.sq` files + iOS Kotlin bridge
- `iosApp/ScaniumiOS/Persistence/` module

**Dependencies:** 4.1 Technology selection

**Assignee:** iOS engineer

---

#### 4.3 Image File Storage
**Acceptance Criteria:**
- Captured images saved to app documents directory
- Thumbnails cached in NSCache (LRU)
- Cleanup policy for old images (configurable, e.g., 30 days)

**Deliverables:**
- `ImageStorage.swift` (FileManager wrapper)
- `ThumbnailCache.swift` (NSCache-based)

**Dependencies:** Phase 3A (camera capture)

**Assignee:** iOS engineer

---

#### 4.4 PHPhotoLibrary Integration
**Acceptance Criteria:**
- `NSPhotoLibraryAddUsageDescription` in Info.plist
- Save to Photos album on user request (share sheet action)
- Permission request with graceful denial handling

**Deliverables:**
- `PHPhotoLibrary.shared().performChanges()` integration
- Share sheet with "Save Image" action

**Dependencies:** 4.3 Image storage

**Assignee:** iOS engineer

---

#### 4.5 History Tracking
**Acceptance Criteria:**
- All item updates logged to history table
- Timestamp + change description recorded
- Query latest history for item (audit trail)

**Deliverables:**
- History table schema
- Insert history on item save

**Dependencies:** 4.2 Database setup

**Assignee:** iOS engineer

---

### Phase 4 Definition of Done
- ✅ All 5 tasks complete
- ✅ Database functional with CRUD operations
- ✅ Images saved and loaded from file system
- ✅ History tracking operational
- ✅ Data persists across app restarts (test by force-quit → relaunch)
- ✅ No Android code touched

---

## Phase 5: Networking & Backend Integration (Week 16-19, 2 engineers, parallel with Phase 6)

**Objective:** Implement backend API client and eBay integration

### Track 5A: Backend API (Week 16-18)

#### 5A.1 URLSession Backend Client
**Acceptance Criteria:**
- `BackendClient.swift` with async/await API
- Endpoints: `/v1/classify`, `/v1/items`
- Error handling for HTTP 4xx/5xx
- Retry logic with exponential backoff (max 3 retries)

**Deliverables:**
- `iosApp/ScaniumiOS/Networking/BackendClient.swift`
- `NetworkError.swift` (enum for error types)

**Dependencies:** Phase 1 (API key config)

**Assignee:** iOS engineer

---

#### 5A.2 Request Signing (HMAC-SHA256)
**Acceptance Criteria:**
- HMAC-SHA256 signing using CryptoKit
- Headers: `X-Request-Timestamp`, `X-Request-Signature`
- Signature matches Android implementation (validate with test endpoint)

**Deliverables:**
- `RequestSigner.swift` (port from Android `RequestSigner.kt`)
- Unit tests for signature generation

**Dependencies:** 5A.1 Backend client

**Assignee:** iOS engineer

---

#### 5A.3 Cloud Classification Integration
**Acceptance Criteria:**
- `/v1/classify` endpoint called with image + metadata
- Response parsed (category, confidence, vision insights)
- Integrated into classification orchestrator (shared KMP)

**Deliverables:**
- Classification endpoint wrapper in `BackendClient.swift`
- Call from shared `ClassificationOrchestrator`

**Dependencies:** 5A.2 Request signing, Phase 1 (shared orchestrator)

**Assignee:** iOS engineer

---

### Track 5B: eBay Integration (Week 17-19, only if eBay is MVP)

#### 5B.1 eBay OAuth 2.0 Flow
**Acceptance Criteria:**
- `ASWebAuthenticationSession` for OAuth redirect flow
- User consent screen displayed
- OAuth tokens (access + refresh) stored in Keychain
- Token refresh logic on expiration

**Deliverables:**
- `EbayAuthService.swift`
- Keychain wrapper for secure token storage

**Dependencies:** Phase 1 (API key config)

**Assignee:** iOS engineer

---

#### 5B.2 eBay API Client
**Acceptance Criteria:**
- Endpoints: `createListing`, `getListingStatus`, `endListing`
- OAuth token injection in Authorization header
- Listing draft model matches Android

**Deliverables:**
- `EbayApiClient.swift`
- Listing models (ListingDraft, Listing, ListingStatus)

**Dependencies:** 5B.1 OAuth flow

**Assignee:** iOS engineer

---

#### 5B.3 eBay Listing Screens
**Acceptance Criteria:**
- Draft form screen (title, description, price, images)
- Review screen before submission
- Listing status screen (in progress, active, failed)

**Deliverables:**
- `EbayDraftView.swift`, `EbayReviewView.swift`, `EbayStatusView.swift`
- Navigation flow from item detail → draft → review → submit

**Dependencies:** 5B.2 eBay API client, Phase 4 (drafts database)

**Assignee:** iOS UI engineer

---

### Phase 5 Definition of Done
- ✅ All Track 5A tasks complete (backend API working)
- ✅ Cloud classification functional (test with real backend)
- ✅ If eBay is MVP: All Track 5B tasks complete (listing flow end-to-end)
- ✅ Integration tests pass for API client and eBay flow
- ✅ No Android code touched

---

## Phase 6: Observability & Polish (Week 20-24, 1 engineer, parallel with Phase 5)

**Objective:** Production observability, error handling, UX polish

### Track 6A: Crash Reporting & Telemetry (Week 20-21)

#### 6A.1 Sentry iOS SDK Integration
**Acceptance Criteria:**
- Sentry iOS SDK added via SPM or CocoaPods
- Initialized in AppDelegate with DSN from build config
- Crash capture tested (force crash, verify in Sentry dashboard)

**Deliverables:**
- Sentry dependency in project
- `ScaniumApp.swift` or AppDelegate initialization
- Test crash verified

**Dependencies:** None (can start immediately)

**Assignee:** iOS engineer

---

#### 6A.2 Sentry Breadcrumbs & Tags
**Acceptance Criteria:**
- Breadcrumbs for key events (screen transitions, API calls, detections)
- Tags: app.flavor (prod/dev), user.edition, device.model
- Context: User ID, app version, OS version

**Deliverables:**
- `Sentry.addBreadcrumb()` calls in critical paths
- `Sentry.configureScope()` for tags

**Dependencies:** 6A.1 Sentry integration

**Assignee:** iOS engineer

---

#### 6A.3 Structured Logging (Optional)
**Acceptance Criteria:**
- `ScaniumLog.swift` facade over OSLog
- Log levels: DEBUG, INFO, ERROR
- Correlation IDs for request tracing

**Deliverables:**
- `ScaniumLog.swift` (port from Android `ScaniumLog.kt`)

**Dependencies:** None

**Assignee:** iOS engineer

---

### Track 6B: UX Polish (Week 22-24)

#### 6B.1 Audio Feedback (Sounds)
**Acceptance Criteria:**
- Capture sound (shutter click)
- Success sound (item added)
- Error sound (detection failed)
- Sounds gated by settings (enable/disable)

**Deliverables:**
- `SoundManager.swift` (AVAudioPlayer or SystemSoundID)
- Sound files in bundle
- Settings toggle

**Dependencies:** Phase 3 (UI)

**Assignee:** iOS engineer

---

#### 6B.2 Motion Components (Optional)
**Acceptance Criteria:**
- Scan frame appear animation (spring curve)
- Lightning pulse on detection (flash effect)
- Price count-up animation

**Deliverables:**
- SwiftUI animation modifiers
- Apply to detection overlay and list items

**Dependencies:** Phase 3 (UI)

**Assignee:** iOS UI engineer

---

#### 6B.3 Theme System (Optional)
**Acceptance Criteria:**
- Custom color palette (match Android branding)
- Dark mode support (beyond system default)
- Theme toggle in settings

**Deliverables:**
- `ThemeManager.swift`
- Apply to all views

**Dependencies:** Phase 3C (settings)

**Assignee:** iOS UI engineer

---

#### 6B.4 FTUE (First-Time User Experience)
**Acceptance Criteria:**
- Onboarding screens (3-5 slides)
- Permission education (camera, photos)
- Terms acceptance prompt

**Deliverables:**
- `OnboardingView.swift`
- Show once on first launch (UserDefaults flag)

**Dependencies:** Phase 3 (UI)

**Assignee:** iOS UI engineer

---

### Phase 6 Definition of Done
- ✅ All Track 6A and 6B tasks complete
- ✅ Sentry captures crashes with breadcrumbs and tags
- ✅ Audio feedback functional
- ✅ Motion polish applied (if scoped in)
- ✅ FTUE flow tested with new users
- ✅ No Android code touched

---

## Post-Parity: Future Enhancements (Deferred)

These features are NOT required for parity but may be roadmapped later:

1. **Billing/IAP (StoreKit):** In-app purchases for premium features (Gap 15.3)
2. **Voice/Assistant:** Speech recognition + AI assistant (Gap 15.4)
3. **Advanced Observability:** OTLP telemetry export (Gap 13.4)
4. **Testing:** Comprehensive unit/UI/integration tests (Gap 18)
5. **Code Obfuscation:** Release build hardening (Gap 17.3)
6. **SBOM/CVE Scanning:** Supply chain security (Gaps 17.4, 17.5)

---

## Parity Completion Criteria

### Definition of Done (Overall)
- ✅ All 6 phases complete
- ✅ All 19 HIGH-risk gaps closed (from Gap Matrix)
- ✅ All 28 MED-risk gaps closed or deferred with justification
- ✅ iOS app passes parity checklist (see below)
- ✅ Android codebase unchanged (diff shows 0 Android files touched)
- ✅ Manual QA pass on iOS device (not just simulator)
- ✅ Parity dashboard shows 100% completion

### Parity Checklist (Manual QA)
Run through this checklist on physical iOS device:

#### Camera & Capture
- [ ] Camera preview displays live feed
- [ ] Tap shutter button → captures image, adds to list
- [ ] Long-press shutter button → continuous scanning, live overlay
- [ ] Bounding boxes appear on detected objects
- [ ] Barcode detected and displayed in list item
- [ ] OCR text extracted and displayed in list item
- [ ] Object detection assigns correct category

#### List & Detail
- [ ] Items list displays all captured items
- [ ] Tap item → detail screen shows full image and metadata
- [ ] Edit item properties → save → updates in list
- [ ] Swipe item → delete with confirmation → removed from list
- [ ] Pull-to-refresh → reloads from database
- [ ] Long-press item → multi-select mode → batch delete works

#### Settings
- [ ] Navigate to settings → all subsections present
- [ ] Change camera resolution → applies on next launch
- [ ] Change detection threshold → affects overlay
- [ ] Toggle audio feedback → sounds play/muted
- [ ] View privacy policy → link opens in browser

#### Storage
- [ ] Force-quit app → relaunch → items still present (persistence)
- [ ] Capture image → image file exists in app documents
- [ ] Share image → save to Photos → appears in Photos app
- [ ] Export CSV → file generated → open in Files app

#### Networking (if backend available)
- [ ] Item classification requests backend → receives category
- [ ] Request signing succeeds (no 401 errors)
- [ ] Network failure → retry logic works → eventual success

#### eBay (if scoped in MVP)
- [ ] Navigate to eBay listing flow from item detail
- [ ] OAuth login → redirects → tokens stored
- [ ] Create draft → submit → listing appears in eBay account
- [ ] Check listing status → shows "Active"

#### Observability
- [ ] Force crash → crash appears in Sentry dashboard
- [ ] Breadcrumbs present in crash report
- [ ] Tags correctly set (app.flavor, device.model)

#### Edge Cases
- [ ] Deny camera permission → graceful error message
- [ ] Airplane mode → offline mode works (no crashes)
- [ ] Device rotation → UI adapts correctly
- [ ] Low storage warning → cleanup policy triggers

---

## Risk Mitigation

### Risk 1: XCFramework Build Issues
**Mitigation:**
- Phase 1 focuses solely on build infrastructure
- Test XCFramework integration in isolation before later phases
- Fallback: Manual framework distribution if Gradle automation fails

### Risk 2: Kotlin/Swift Interop Issues
**Mitigation:**
- Phase 1 includes type mapping validation tests
- Discover nullability issues early
- Document workarounds in `docs/parity/KMP_INTEROP_NOTES.md`

### Risk 3: CoreML Model Performance
**Mitigation:**
- Phase 2 validates model performance on target devices (iPhone 12+)
- Benchmark inference latency (target: <100ms per frame)
- Fallback: Cloud-only classification if on-device too slow

### Risk 4: Database Migration Complexity
**Mitigation:**
- Phase 4 starts with schema design review
- Choose SQLDelight (KMP) for shared schema definition
- Fallback: Core Data with manual schema mapping

### Risk 5: Backend API Availability
**Mitigation:**
- Phase 5 includes mock backend for testing
- Backend team provides staging environment
- Integration tests validate contract before production

### Risk 6: eBay OAuth Complexity
**Mitigation:**
- Phase 5B is conditional (only if eBay is MVP)
- Use eBay sandbox environment for development
- Document OAuth flow in `docs/parity/EBAY_OAUTH_GUIDE.md`

---

## Parallelization Strategy (Max Efficiency)

To minimize overall duration, run these phases in parallel:

| Weeks | Track A (iOS Engineer 1) | Track B (iOS Engineer 2) | Track C (KMP Specialist) |
|---|---|---|---|
| 1-2 | Phase 0: Validation | Phase 0: Validation | Phase 0: Validation |
| 3-5 | Phase 1: Shared Brain (iOS side) | Phase 1: Testing & validation | Phase 1: XCFramework build |
| 6-9 | Phase 2: Detection Orchestrator | Phase 2: CoreML model integration | Phase 2: Support (review PRs) |
| 10-12 | Phase 3A: Camera UI | Phase 3B: List/Detail UI | Phase 4: Database setup |
| 13-15 | Phase 3C: Settings UI | Phase 3B: Continued | Phase 4: Storage & history |
| 16-18 | Phase 5A: Backend API | Phase 5B: eBay integration | Phase 6A: Sentry integration |
| 19-21 | Phase 5: Continued | Phase 5B: eBay UI | Phase 6A: Logging |
| 22-24 | Phase 6B: UX Polish | Phase 6B: Motion/theme | QA & parity checklist |

**Parallel Tracks:**
- Phases 3 + 4 can run in parallel (different engineers)
- Phases 5 + 6 can run in parallel (backend vs observability)

**Critical Path:** Phase 0 → Phase 1 → Phase 2 → Phase 3A (camera UI)

---

## Success Metrics

### Quantitative
- **Lines of Code Added:** ~8,000-12,000 LOC (iOS Swift)
- **Gaps Closed:** 47 gaps (19 HIGH + 28 MED)
- **Test Coverage:** ≥70% for new iOS code
- **Crash-Free Rate:** ≥99% (Sentry metric)

### Qualitative
- **User Feedback:** iOS users report feature parity with Android
- **Team Velocity:** iOS features ship at same pace as Android (post-parity)
- **Code Reuse:** ≥60% of business logic in shared KMP modules

---

## END OF PARITY PLAN
