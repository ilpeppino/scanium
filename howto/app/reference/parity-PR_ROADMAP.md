***REMOVED*** iOS Parity PR Roadmap

**Document Version:** 1.0
**Last Updated:** 2026-01-13
**Purpose:** PR-by-PR implementation roadmap with small, parallelizable slices

---

***REMOVED******REMOVED*** How to Use This Document

***REMOVED******REMOVED******REMOVED*** PR Structure

Each PR has:

- **Title:** Brief description (use as GitHub PR title)
- **Scope:** Files/modules touched
- **Acceptance Criteria:** Clear definition of done
- **Risk Level:** LOW/MED/HIGH based on complexity and blast radius
- **Do Not Touch:** Forbidden paths to protect Android baseline
- **Depends On:** Prerequisite PRs (must be merged first)
- **Parallel Track:** Which track this PR belongs to for parallelization

***REMOVED******REMOVED******REMOVED*** Parallelization Tracks

- **Track A: iOS UI:** SwiftUI screens and components
- **Track B: iOS Adapters:** Platform-specific services (camera, detection, networking)
- **Track C: KMP/Shared:** XCFramework build and shared module integration
- **Track D: Storage:** Database and file handling
- **Track E: Observability:** Logging, crash reporting, telemetry

**Tracks can run concurrently** as long as dependencies are satisfied. PRs within the same track
must be sequential.

---

***REMOVED******REMOVED*** Phase 0: Validation & Guardrails (2 PRs)

***REMOVED******REMOVED******REMOVED*** PR-0.1: Parity Documentation & Baseline

**Title:** `docs: Add iOS parity analysis and baseline documentation`

**Scope:**

- `docs/parity/ANDROID_BASELINE.md`
- `docs/parity/IOS_CURRENT.md`
- `docs/parity/GAP_MATRIX.md`
- `docs/parity/PARITY_PLAN.md`
- `docs/parity/PR_ROADMAP.md` (this file)
- Tag: `android-baseline-v1.0` on current commit

**Acceptance Criteria:**

- All 5 documentation files committed
- Android baseline tagged
- Parity dashboard linked in README
- No code changes

**Risk:** LOW

**Do Not Touch:** All Android code (`androidApp/`, `android-*/`)

**Depends On:** None

**Track:** N/A (documentation)

---

***REMOVED******REMOVED******REMOVED*** PR-0.2: iOS Parity CI Guard

**Title:** `ci: Add iOS parity guard to prevent Android modifications`

**Scope:**

- `.github/workflows/ios-parity-guard.yml`
- Documentation: `docs/parity/CI_GUARDRAILS.md`

**Acceptance Criteria:**

- CI check fails if PR touches `androidApp/` or `android-*` modules
- Allowed paths: `iosApp/`, `shared/`, `docs/parity/`, `docs/BUILD_IOS_FRAMEWORKS.md`
- Exception process documented
- Tested with sample PR

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-0.1

**Track:** N/A (infrastructure)

---

***REMOVED******REMOVED*** Phase 1: Shared Brain Readiness (5 PRs)

***REMOVED******REMOVED******REMOVED*** PR-1.1: XCFramework Build Automation

**Title:** `build: Add Gradle tasks for iOS XCFramework generation`

**Scope:**

- `shared/core-models/build.gradle.kts` (add iOS targets)
- `shared/core-tracking/build.gradle.kts`
- `shared/core-domainpack/build.gradle.kts`
- `shared/core-export/build.gradle.kts`
- `shared/telemetry/build.gradle.kts`
- `docs/BUILD_IOS_FRAMEWORKS.md` (new documentation)
- Gradle root settings for XCFramework tasks

**Acceptance Criteria:**

- `./gradlew :shared:core-models:assembleXCFramework` succeeds
- XCFramework output in `shared/core-models/build/XCFrameworks/`
- Repeat for all 5 shared modules
- Documentation includes build commands and troubleshooting

**Risk:** MED

**Do Not Touch:** Android-specific code, maintain backward compatibility

**Depends On:** PR-0.2

**Track:** C (KMP/Shared)

---

***REMOVED******REMOVED******REMOVED*** PR-1.2: iOS XCFramework Integration

**Title:** `ios: Integrate shared XCFrameworks into Xcode project`

**Scope:**

- `iosApp/Frameworks/` (directory for XCFrameworks)
- `iosApp/ScaniumiOS.xcodeproj/project.pbxproj` (framework linking)
- `iosApp/ScaniumiOS/ScaniumiOSApp.swift` (import test)
- `.gitignore` (ignore XCFramework binaries, track via build script)

**Acceptance Criteria:**

- XCFrameworks copied to `iosApp/Frameworks/`
- Xcode project configured with framework search paths
- `import Shared` compiles without errors in SwiftUI code
- Sample call to shared module succeeds (e.g., `SampleItemsProvider()`)

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-1.1

**Track:** C (KMP/Shared)

---

***REMOVED******REMOVED******REMOVED*** PR-1.3: Shared Module Initialization

**Title:** `ios: Initialize KMP shared session and data source`

**Scope:**

- `iosApp/ScaniumiOS/SharedBridge.swift` (remove TODOs at lines 74, 78)
- `iosApp/ScaniumiOS/ScaniumiOSApp.swift` (session lifecycle)
- `iosApp/ScaniumiOS/FeatureFlags.swift` (make `useMocks` dynamic)

**Acceptance Criteria:**

- `KmpBackedSession.start()` initializes shared session
- `SharedDataSource.loadItems()` calls `SampleItemsProvider().sampleItems()`
- Feature flag `useMocks` reads from UserDefaults
- Test: Set `useMocks = false`, app displays items from shared KMP (not hardcoded mocks)

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-1.2

**Track:** C (KMP/Shared)

---

***REMOVED******REMOVED******REMOVED*** PR-1.4: Shared Type Mapping Tests

**Title:** `ios: Add unit tests for Kotlin-Swift type mapping`

**Scope:**

- `iosAppTests/SharedTypeTests.swift` (new XCTest suite)
- Test all Kotlin → Swift conversions: ScannedItem, ImageRef, NormalizedRect, Category,
  ListingStatus

**Acceptance Criteria:**

- Test suite validates all type conversions
- Nullability edge cases tested (nil handling)
- Tests pass with 100% coverage of type mapping code
- Document any workarounds for Kotlin/Swift interop issues

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-1.3

**Track:** C (KMP/Shared)

---

***REMOVED******REMOVED******REMOVED*** PR-1.5: Domain Pack Loading

**Title:** `ios: Load domain pack from iOS bundle`

**Scope:**

- `iosApp/ScaniumiOS/Resources/home_resale_domain_pack.json` (copy from Android `res/raw/`)
- `iosApp/ScaniumiOS/DomainPackLoader.swift` (new file)
- `iosApp/ScaniumiOS/ScaniumiOSApp.swift` (initialize on launch)

**Acceptance Criteria:**

- JSON file added to iOS bundle target
- `DomainPackLoader` reads JSON via `Bundle.main.path(forResource:ofType:)`
- Pass JSON to shared `DomainPackProvider.initialize()`
- App logs: "Domain Pack loaded: home_resale_domain_pack v1.0"

**Risk:** LOW

**Do Not Touch:** All Android code, do not modify JSON content

**Depends On:** PR-1.3

**Track:** C (KMP/Shared)

---

***REMOVED******REMOVED*** Phase 2: iOS Platform Adapters (6 PRs)

***REMOVED******REMOVED******REMOVED*** PR-2.1: CoreML Object Detection Model

**Title:** `ios: Add CoreML object detection model and initialize service`

**Scope:**

- `iosApp/Models/ObjectDetection.mlmodel` (or `YOLOv5.mlmodel`)
- `iosApp/ScaniumiOS/CoreMLObjectDetectionService.swift` (update to use model)
- `iosAppTests/ObjectDetectionTests.swift` (new test)

**Acceptance Criteria:**

- CoreML model obtained (YOLOv5 or MobileNet SSD converted to .mlmodel)
- Model added to Xcode target
- `CoreMLObjectDetectionService` initializes `VNCoreMLModel` successfully
- Test detects objects in sample image with bounding boxes
- Benchmark: Inference <100ms on iPhone 12

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-1.5 (shared types)

**Track:** B (iOS Adapters)

---

***REMOVED******REMOVED******REMOVED*** PR-2.2: Detection Orchestrator

**Title:** `ios: Implement detection orchestrator with throttling`

**Scope:**

- `iosApp/ScaniumiOS/Detection/DetectionOrchestrator.swift` (new)
- `iosApp/ScaniumiOS/Detection/ThrottlePolicy.swift` (new)
- `iosAppTests/DetectionOrchestratorTests.swift` (unit tests)

**Acceptance Criteria:**

- Orchestrator dispatches frames to 3 services in parallel: barcode, OCR, object detection
- Uses DispatchQueue.concurrent for parallel execution
- Throttling policy limits detections to max 5 per second (configurable)
- Aggregates results into single `DetectionResponse`
- Unit tests validate parallel execution and throttling

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-2.1 (object detection service ready)

**Track:** B (iOS Adapters)

---

***REMOVED******REMOVED******REMOVED*** PR-2.3: Object Tracking Integration

**Title:** `ios: Integrate shared ObjectTracker for stable tracking IDs`

**Scope:**

- `iosApp/ScaniumiOS/Detection/DetectionOrchestrator.swift` (add tracking call)
- Call shared `ObjectTracker.track(detections)` from orchestrator

**Acceptance Criteria:**

- Detections passed to shared `ObjectTracker`
- Stable tracking IDs assigned across frames
- Bounding boxes follow objects in overlay (test with video)
- Test: Track object for 10 frames, ID remains consistent

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-2.2, PR-1.5 (shared modules ready)

**Track:** B (iOS Adapters)

---

***REMOVED******REMOVED******REMOVED*** PR-2.4: Item Aggregation Integration

**Title:** `ios: Integrate shared ItemAggregator for deduplication`

**Scope:**

- `iosApp/ScaniumiOS/Detection/DetectionOrchestrator.swift` (add aggregation call)
- Call shared `ItemAggregator.aggregate(detections)`

**Acceptance Criteria:**

- Multiple detections of same object aggregated into single item
- Deduplication based on spatial overlap (IoU threshold)
- Test: 10 detections of same object → 1 aggregated item

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-2.3

**Track:** B (iOS Adapters)

---

***REMOVED******REMOVED******REMOVED*** PR-2.5: Detection Services Wiring

**Title:** `ios: Wire detection services to camera frame pipeline`

**Scope:**

- `iosApp/ScaniumiOS/AVFoundationFrameSource.swift` (update delegate)
- Call `DetectionOrchestrator.process(frame)` from frame delegate
- Store results in published state for UI consumption

**Acceptance Criteria:**

- Camera frames automatically sent to detection orchestrator
- Detection results available in SwiftUI state (@Published)
- Live detection working (test with physical device)

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-2.4

**Track:** B (iOS Adapters)

---

***REMOVED******REMOVED******REMOVED*** PR-2.6: Classification Placeholder

**Title:** `ios: Add classification orchestrator stub (cloud-only for MVP)`

**Scope:**

- `iosApp/ScaniumiOS/Classification/ClassificationOrchestrator.swift` (stub)
- Defer on-device CLIP model to post-MVP
- Focus on cloud classification (Phase 5)

**Acceptance Criteria:**

- Stub accepts detections, returns placeholder categories
- Ready for cloud integration in Phase 5
- No crashes, graceful degradation

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-2.4

**Track:** B (iOS Adapters)

---

***REMOVED******REMOVED*** Phase 3: iOS UI Parity (14 PRs)

***REMOVED******REMOVED******REMOVED*** Track 3A: Camera & Capture (5 PRs)

***REMOVED******REMOVED******REMOVED*** PR-3A.1: Camera Preview Screen

**Title:** `ios: Add camera preview screen with AVCaptureVideoPreviewLayer`

**Scope:**

- `iosApp/ScaniumiOS/Views/CameraView.swift` (new)
- `iosApp/ScaniumiOS/ContentView.swift` (add navigation link)
- Integrate `AVFoundationFrameSource`

**Acceptance Criteria:**

- Camera preview displays live feed
- Navigation from list screen to camera screen works
- Orientation handled correctly (portrait/landscape)
- No crashes on camera access (permission handled in next PR)

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-2.5 (detection wiring ready)

**Track:** A (iOS UI)

---

***REMOVED******REMOVED******REMOVED*** PR-3A.2: Camera Permissions

**Title:** `ios: Add camera permission handling and Info.plist keys`

**Scope:**

- `iosApp/ScaniumiOS/Info.plist` (add `NSCameraUsageDescription`)
- `iosApp/ScaniumiOS/Views/CameraView.swift` (permission check in onAppear)
- Alert dialog if permission denied with link to Settings

**Acceptance Criteria:**

- Permission requested on first camera access
- User-friendly description: "Scanium uses the camera to detect and scan items for resale."
- Graceful degradation if denied (show alert, disable camera)

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-3A.1

**Track:** A (iOS UI)

---

***REMOVED******REMOVED******REMOVED*** PR-3A.3: Shutter Button & Capture Modes

**Title:** `ios: Add shutter button with tap/long-press gestures`

**Scope:**

- `iosApp/ScaniumiOS/Components/ShutterButton.swift` (new)
- `iosApp/ScaniumiOS/Views/CameraView.swift` (integrate button)
- Gesture handlers: TapGesture (single capture), LongPressGesture (continuous scanning)

**Acceptance Criteria:**

- Tap: single-frame capture, saves image to database (Phase 4 dependency - stub for now)
- Long-press: continuous scanning mode, detection overlay updates in real-time
- Visual feedback: button scales on press (animation)

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-3A.2

**Track:** A (iOS UI)

---

***REMOVED******REMOVED******REMOVED*** PR-3A.4: Detection Overlay (Real-Time)

**Title:** `ios: Add real-time detection overlay with bounding boxes`

**Scope:**

- `iosApp/ScaniumiOS/Views/DetectionOverlay.swift` (new)
- SwiftUI Canvas for drawing bounding boxes
- Subscribe to detection results from orchestrator

**Acceptance Criteria:**

- Bounding boxes drawn for detected objects
- Confidence scores displayed above boxes
- Updates in real-time (30 FPS)
- Color-coded by confidence level (green >80%, yellow 50-80%, red <50%)

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-3A.3, PR-2.5 (detection results available)

**Track:** A (iOS UI)

---

***REMOVED******REMOVED******REMOVED*** PR-3A.5: Camera Settings Overlay (Optional)

**Title:** `ios: Add in-camera settings overlay for resolution/threshold`

**Scope:**

- `iosApp/ScaniumiOS/Views/CameraSettingsOverlay.swift` (new)
- Sheet presented from camera view
- Bind to UserDefaults for persistence

**Acceptance Criteria:**

- Settings sheet accessible from camera toolbar
- Resolution picker (LOW/NORMAL/HIGH)
- Detection threshold slider
- Settings applied immediately to camera session

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-3A.4

**Track:** A (iOS UI)

---

***REMOVED******REMOVED******REMOVED*** Track 3B: List & Detail Screens (6 PRs)

***REMOVED******REMOVED******REMOVED*** PR-3B.1: Items List Interactivity

**Title:** `ios: Add tap/swipe gestures and toolbar actions to items list`

**Scope:**

- `iosApp/ScaniumiOS/ContentView.swift` (update list)
- Tap gesture: navigate to detail screen (stub for now)
- Swipe to delete with `.swipeActions()` modifier
- Toolbar buttons: Export, Share, Settings (stubs)

**Acceptance Criteria:**

- Tap item → navigate to detail screen (placeholder)
- Swipe item → delete with confirmation dialog
- Pull-to-refresh reloads from database (Phase 4 dependency - stub for now)

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-1.5 (shared data available)

**Track:** A (iOS UI) - Can run in parallel with Track 3A

---

***REMOVED******REMOVED******REMOVED*** PR-3B.2: Item Detail/Edit Screen

**Title:** `ios: Add item detail screen with image zoom and editable fields`

**Scope:**

- `iosApp/ScaniumiOS/Views/ItemDetailView.swift` (new)
- Image preview with pinch-to-zoom (`MagnificationGesture`)
- Editable fields: label (TextField), category (Picker), price (TextField)
- Save button updates database (Phase 4 dependency - stub for now)

**Acceptance Criteria:**

- Detail screen displays full item details
- Image zoom works smoothly
- Edited fields update local state
- Save button (stubbed until database PR)

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-3B.1

**Track:** A (iOS UI)

---

***REMOVED******REMOVED******REMOVED*** PR-3B.3: Selection Mode (Multi-Select)

**Title:** `ios: Add multi-select mode with batch actions`

**Scope:**

- `iosApp/ScaniumiOS/ContentView.swift` (add EditMode)
- Long-press item → enter selection mode
- Checkboxes appear on list items
- Toolbar: Delete All, Export All, Cancel buttons

**Acceptance Criteria:**

- Long-press activates selection mode
- Multiple items selectable with checkboxes
- Batch delete confirmation dialog
- Cancel returns to normal mode

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-3B.1

**Track:** A (iOS UI)

---

***REMOVED******REMOVED******REMOVED*** PR-3B.4: Empty State & Error Handling

**Title:** `ios: Add empty state view and error alerts`

**Scope:**

- `iosApp/ScaniumiOS/Views/EmptyStateView.swift` (new)
- Conditional display in `ContentView.swift` when list is empty
- Error alerts for failed operations (`.alert()` modifier)

**Acceptance Criteria:**

- Empty state shows friendly message: "No items yet. Tap camera to start scanning."
- CTA button navigates to camera screen
- Error alerts display for delete failures, network errors, etc.

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-3B.1

**Track:** A (iOS UI)

---

***REMOVED******REMOVED******REMOVED*** PR-3B.5: Confirmation Dialogs

**Title:** `ios: Add confirmation dialogs for destructive actions`

**Scope:**

- `iosApp/ScaniumiOS/ContentView.swift` (add `.confirmationDialog()`)
- Delete confirmation (single + batch)
- Destructive action styling (red button)

**Acceptance Criteria:**

- Delete action shows confirmation: "Are you sure? This cannot be undone."
- Cancel button dismisses dialog
- Confirm button proceeds with deletion

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-3B.3

**Track:** A (iOS UI)

---

***REMOVED******REMOVED******REMOVED*** PR-3B.6: Pull-to-Refresh

**Title:** `ios: Add pull-to-refresh to items list`

**Scope:**

- `iosApp/ScaniumiOS/ContentView.swift` (add `.refreshable {}` modifier)
- Reload items from database on refresh

**Acceptance Criteria:**

- Pull-down gesture triggers refresh animation
- Items reload from database (Phase 4 dependency - stub for now)
- Spinner displays during reload

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-3B.1

**Track:** A (iOS UI)

---

***REMOVED******REMOVED******REMOVED*** Track 3C: Settings Screens (3 PRs)

***REMOVED******REMOVED******REMOVED*** PR-3C.1: Settings Navigation Structure

**Title:** `ios: Add settings root screen and subsections`

**Scope:**

- `iosApp/ScaniumiOS/Views/Settings/SettingsRootView.swift` (new)
- `iosApp/ScaniumiOS/Views/Settings/GeneralSettingsView.swift` (stub)
- `iosApp/ScaniumiOS/Views/Settings/CameraSettingsView.swift` (stub)
- `iosApp/ScaniumiOS/Views/Settings/PrivacySettingsView.swift` (stub)
- `iosApp/ScaniumiOS/Views/Settings/StorageSettingsView.swift` (stub)
- Navigation from ContentView toolbar

**Acceptance Criteria:**

- Settings root displays list of subsections
- Each subsection navigates to detail screen (stubs)
- UserDefaults-backed settings storage (future PRs)

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-3B.1 (list screen with toolbar)

**Track:** A (iOS UI) - Can run in parallel with Tracks 3A and 3B

---

***REMOVED******REMOVED******REMOVED*** PR-3C.2: Camera Settings Implementation

**Title:** `ios: Implement camera settings screen with resolution and thresholds`

**Scope:**

- `iosApp/ScaniumiOS/Views/Settings/CameraSettingsView.swift` (implement)
- Resolution Picker (LOW/NORMAL/HIGH)
- Detection threshold Slider
- Bind to UserDefaults for persistence

**Acceptance Criteria:**

- Settings saved to UserDefaults
- Camera session applies settings on next launch
- Settings match Android defaults

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-3C.1

**Track:** A (iOS UI)

---

***REMOVED******REMOVED******REMOVED*** PR-3C.3: Privacy & Terms Settings

**Title:** `ios: Add privacy settings screen with terms/policy links`

**Scope:**

- `iosApp/ScaniumiOS/Views/Settings/PrivacySettingsView.swift` (implement)
- Links to terms of service and privacy policy (open in Safari)
- Terms acceptance toggle (UserDefaults)

**Acceptance Criteria:**

- Links open in Safari or in-app browser (SafariServices)
- Terms acceptance tracked (required on first launch)
- Data usage policy displayed

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-3C.1

**Track:** A (iOS UI)

---

***REMOVED******REMOVED*** Phase 4: Storage & Persistence (8 PRs)

***REMOVED******REMOVED******REMOVED*** PR-4.1: Database Technology Decision

**Title:** `docs: Document database technology choice for iOS`

**Scope:**

- `docs/parity/DATABASE_DECISION.md` (new)

**Acceptance Criteria:**

- Decision: Core Data vs SQLDelight (KMP) vs Realm
- Recommendation: **SQLDelight** for maximum parity with Android
- Rationale documented (shared schema, shared queries, code reuse)
- Fallback option: Core Data with manual schema mapping

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-1.5 (shared modules ready)

**Track:** D (Storage)

---

***REMOVED******REMOVED******REMOVED*** PR-4.2: SQLDelight iOS Configuration (Recommended Path)

**Title:** `ios: Configure SQLDelight for iOS with shared schema`

**Scope:**

- `shared/database/build.gradle.kts` (add iOS target)
- `shared/database/src/commonMain/sqldelight/com/scanium/db/ScannedItems.sq` (shared schema)
- iOS Kotlin bridge for database access

**Acceptance Criteria:**

- SQLDelight generates iOS Kotlin code
- Schema matches Android (3 tables: scanned_items, history, drafts)
- XCFramework includes database module
- Test query from iOS (select all items)

**Risk:** MED

**Do Not Touch:** Android schema (maintain backward compatibility)

**Depends On:** PR-4.1, PR-1.2 (XCFramework integration)

**Track:** D (Storage)

---

***REMOVED******REMOVED******REMOVED*** PR-4.3 (Alternative): Core Data Setup

**Title:** `ios: Set up Core Data with schema matching Android`

**Scope:**

- `iosApp/ScaniumiOS/Scanium.xcdatamodeld` (new Core Data model)
- 3 entities: ScannedItem, ScannedItemHistory, ListingDraft
- NSManagedObject subclasses generated

**Acceptance Criteria:**

- Core Data stack initialized in AppDelegate
- Schema fields match Android Room schema
- Attributes: id (String), labelText (String), category (Int32), confidence (Double), timestamp (
  Date), etc.
- Migration strategy defined (lightweight migrations)

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-4.1

**Track:** D (Storage)

**Note:** Only implement this PR if SQLDelight (PR-4.2) is not feasible

---

***REMOVED******REMOVED******REMOVED*** PR-4.4: DAO Layer Implementation

**Title:** `ios: Implement DAO layer for CRUD operations`

**Scope:**

- `iosApp/ScaniumiOS/Persistence/ScannedItemDAO.swift` (new)
- If SQLDelight: Wrapper around shared queries
- If Core Data: NSFetchRequest-based CRUD
- Methods: insert, update, delete, fetchAll, fetchById

**Acceptance Criteria:**

- All CRUD operations functional
- Batch insert for multiple items
- Query by ID, query all, query by category
- Unit tests for DAO layer

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-4.2 or PR-4.3 (database setup)

**Track:** D (Storage)

---

***REMOVED******REMOVED******REMOVED*** PR-4.5: Image File Storage

**Title:** `ios: Implement image file storage with cache management`

**Scope:**

- `iosApp/ScaniumiOS/Storage/ImageStorage.swift` (new)
- `iosApp/ScaniumiOS/Storage/ThumbnailCache.swift` (new)
- FileManager wrapper for saving/loading images

**Acceptance Criteria:**

- Images saved to app documents directory
- Thumbnails cached in NSCache (LRU, max 100 images)
- Cleanup policy for old images (configurable, e.g., delete after 30 days)
- Test: Save image, retrieve image, verify file exists

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-3A.3 (camera capture)

**Track:** D (Storage)

---

***REMOVED******REMOVED******REMOVED*** PR-4.6: PHPhotoLibrary Integration

**Title:** `ios: Add photo library integration for saving images`

**Scope:**

- `iosApp/ScaniumiOS/Info.plist` (add `NSPhotoLibraryAddUsageDescription`)
- `iosApp/ScaniumiOS/Storage/PhotoLibrarySaver.swift` (new)
- Permission request and save flow

**Acceptance Criteria:**

- Permission requested when user taps "Save to Photos"
- Image saved to Photos app via `PHPhotoLibrary.shared().performChanges()`
- Success/failure feedback to user (toast or alert)

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-4.5

**Track:** D (Storage)

---

***REMOVED******REMOVED******REMOVED*** PR-4.7: History Tracking

**Title:** `ios: Implement history tracking for item changes`

**Scope:**

- `iosApp/ScaniumiOS/Persistence/HistoryDAO.swift` (new)
- History table: itemId, timestamp, change description
- Insert history on item save/update

**Acceptance Criteria:**

- All item updates logged to history table
- Query latest history for item
- Audit trail visible in detail screen (future PR)

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-4.4 (DAO layer)

**Track:** D (Storage)

---

***REMOVED******REMOVED******REMOVED*** PR-4.8: Wire Storage to UI

**Title:** `ios: Wire database persistence to camera and list screens`

**Scope:**

- `iosApp/ScaniumiOS/Views/CameraView.swift` (save captured items to DB)
- `iosApp/ScaniumiOS/ContentView.swift` (load items from DB)
- `iosApp/ScaniumiOS/Views/ItemDetailView.swift` (update item in DB)

**Acceptance Criteria:**

- Captured items persist across app restarts
- List reloads from database on launch
- Edit item → save → persists to database
- Test: Capture item, force-quit app, relaunch, item still present

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-4.4, PR-3A.3, PR-3B.2

**Track:** D (Storage)

---

***REMOVED******REMOVED*** Phase 5: Networking & Backend Integration (9 PRs)

***REMOVED******REMOVED******REMOVED*** Track 5A: Backend API (5 PRs)

***REMOVED******REMOVED******REMOVED*** PR-5A.1: Backend API Client Foundation

**Title:** `ios: Add URLSession-based backend API client`

**Scope:**

- `iosApp/ScaniumiOS/Networking/BackendClient.swift` (new)
- `iosApp/ScaniumiOS/Networking/NetworkError.swift` (new)
- Async/await API with error handling

**Acceptance Criteria:**

- Client supports GET, POST, PUT, DELETE
- Error handling for HTTP 4xx/5xx
- Timeout configuration (30s default)
- Test with mock URLProtocol (unit test)

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-1.5 (shared types)

**Track:** B (iOS Adapters) - Can run in parallel with Phase 4

---

***REMOVED******REMOVED******REMOVED*** PR-5A.2: Request Signing (HMAC-SHA256)

**Title:** `ios: Implement HMAC-SHA256 request signing with CryptoKit`

**Scope:**

- `iosApp/ScaniumiOS/Networking/RequestSigner.swift` (new)
- Port HMAC logic from Android `RequestSigner.kt`
- Headers: `X-Request-Timestamp`, `X-Request-Signature`

**Acceptance Criteria:**

- HMAC-SHA256 signature generated using CryptoKit
- Timestamp in Unix seconds
- Signature matches Android implementation (validated with test endpoint)
- Unit tests for signature generation

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-5A.1

**Track:** B (iOS Adapters)

---

***REMOVED******REMOVED******REMOVED*** PR-5A.3: API Key Configuration

**Title:** `ios: Add API key configuration via build settings`

**Scope:**

- `iosApp/ScaniumiOS/Config/APIConfig.swift` (new)
- Xcode build settings: `SCANIUM_API_KEY`, `SCANIUM_API_BASE_URL`
- Access via `Bundle.main.infoDictionary`

**Acceptance Criteria:**

- API key read from build settings (not hardcoded)
- Different keys for dev/beta/prod schemes (Phase 6)
- Fallback to empty string if not set (fail gracefully)

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-5A.1

**Track:** B (iOS Adapters)

---

***REMOVED******REMOVED******REMOVED*** PR-5A.4: Retry Logic with Exponential Backoff

**Title:** `ios: Add retry logic with exponential backoff to backend client`

**Scope:**

- `iosApp/ScaniumiOS/Networking/RetryPolicy.swift` (new)
- Integrate into `BackendClient.swift`

**Acceptance Criteria:**

- Retry on 5xx errors and timeout (max 3 retries)
- Exponential backoff: 1s, 2s, 4s
- No retry on 4xx errors (client errors)
- Unit tests validate retry behavior

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-5A.1

**Track:** B (iOS Adapters)

---

***REMOVED******REMOVED******REMOVED*** PR-5A.5: Cloud Classification Endpoint

**Title:** `ios: Implement /v1/classify endpoint integration`

**Scope:**

- `iosApp/ScaniumiOS/Networking/BackendClient.swift` (add classify method)
- `iosApp/ScaniumiOS/Classification/CloudClassifier.swift` (new)
- Wire to shared `ClassificationOrchestrator`

**Acceptance Criteria:**

- POST request to `/v1/classify` with image + metadata
- Response parsed (category, confidence, vision insights)
- Integrated into classification pipeline
- Test with mock backend (stub response)

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-5A.2, PR-5A.3, PR-2.6 (classification stub)

**Track:** B (iOS Adapters)

---

***REMOVED******REMOVED******REMOVED*** Track 5B: eBay Integration (4 PRs, conditional on MVP scope)

***REMOVED******REMOVED******REMOVED*** PR-5B.1: eBay OAuth 2.0 Flow

**Title:** `ios: Implement eBay OAuth 2.0 flow with ASWebAuthenticationSession`

**Scope:**

- `iosApp/ScaniumiOS/eBay/EbayAuthService.swift` (new)
- `iosApp/ScaniumiOS/Storage/KeychainWrapper.swift` (new)
- OAuth redirect URL scheme in Info.plist

**Acceptance Criteria:**

- User consent screen displayed via `ASWebAuthenticationSession`
- Redirect URL captured with authorization code
- Token exchange (code → access token + refresh token)
- Tokens stored in Keychain securely

**Risk:** HIGH

**Do Not Touch:** All Android code

**Depends On:** PR-5A.3 (API config)

**Track:** B (iOS Adapters) - Can run in parallel with Track 5A after PR-5A.3

**Note:** Only implement if eBay is MVP requirement

---

***REMOVED******REMOVED******REMOVED*** PR-5B.2: eBay API Client

**Title:** `ios: Implement eBay API client with OAuth token injection`

**Scope:**

- `iosApp/ScaniumiOS/eBay/EbayApiClient.swift` (new)
- `iosApp/ScaniumiOS/eBay/Models/` (ListingDraft, Listing, ListingStatus)
- Endpoints: createListing, getListingStatus, endListing

**Acceptance Criteria:**

- OAuth token injected in Authorization header
- Endpoints match Android implementation
- Error handling for eBay API errors (rate limits, validation errors)
- Test with eBay sandbox environment

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-5B.1

**Track:** B (iOS Adapters)

---

***REMOVED******REMOVED******REMOVED*** PR-5B.3: eBay Listing Draft Screen

**Title:** `ios: Add eBay listing draft form screen`

**Scope:**

- `iosApp/ScaniumiOS/Views/eBay/EbayDraftView.swift` (new)
- Form fields: title, description, price, category, images
- Navigation from item detail screen

**Acceptance Criteria:**

- Form displays with pre-filled item data
- User can edit all fields
- Save draft button stores to database (drafts table)
- Continue to review screen

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-5B.2, PR-4.4 (database for drafts)

**Track:** A (iOS UI) - Can run in parallel with backend work

---

***REMOVED******REMOVED******REMOVED*** PR-5B.4: eBay Review & Submit Screen

**Title:** `ios: Add eBay listing review and submit screen`

**Scope:**

- `iosApp/ScaniumiOS/Views/eBay/EbayReviewView.swift` (new)
- `iosApp/ScaniumiOS/Views/eBay/EbayStatusView.swift` (new)
- Submit flow with loading state

**Acceptance Criteria:**

- Review screen displays draft summary
- Submit button calls `createListing()` API
- Loading state during submission
- Success: Navigate to status screen (listing ID, URL)
- Failure: Display error alert, allow retry

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-5B.3

**Track:** A (iOS UI)

---

***REMOVED******REMOVED*** Phase 6: Observability & Polish (9 PRs)

***REMOVED******REMOVED******REMOVED*** Track 6A: Crash Reporting & Telemetry (4 PRs)

***REMOVED******REMOVED******REMOVED*** PR-6A.1: Sentry iOS SDK Integration

**Title:** `ios: Integrate Sentry iOS SDK for crash reporting`

**Scope:**

- `iosApp/Podfile` or Package.swift (add Sentry dependency)
- `iosApp/ScaniumiOS/ScaniumiOSApp.swift` (initialize Sentry)
- Sentry DSN from build config

**Acceptance Criteria:**

- Sentry SDK added via SPM or CocoaPods
- Initialized on app launch with DSN from build settings
- Test crash captured (force crash, verify in Sentry dashboard)

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** None (can start immediately)

**Track:** E (Observability)

---

***REMOVED******REMOVED******REMOVED*** PR-6A.2: Sentry Breadcrumbs & Tags

**Title:** `ios: Add Sentry breadcrumbs and tags for context`

**Scope:**

- `iosApp/ScaniumiOS/` (add `Sentry.addBreadcrumb()` calls in key paths)
- Screen transitions, API calls, detection events
- Tags: app.flavor, user.edition, device.model

**Acceptance Criteria:**

- Breadcrumbs logged for critical events
- Tags set via `Sentry.configureScope()`
- Crash reports include breadcrumbs and tags for debugging

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-6A.1

**Track:** E (Observability)

---

***REMOVED******REMOVED******REMOVED*** PR-6A.3: Structured Logging (Optional)

**Title:** `ios: Add structured logging facade with OSLog`

**Scope:**

- `iosApp/ScaniumiOS/Logging/ScaniumLog.swift` (new)
- Log levels: DEBUG, INFO, ERROR
- Wrapper around OSLog for structured logging

**Acceptance Criteria:**

- Logging facade replaces `print()` statements
- Logs visible in Xcode Console and system logs
- Log levels configurable via build settings

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** None

**Track:** E (Observability)

---

***REMOVED******REMOVED******REMOVED*** PR-6A.4: Correlation IDs

**Title:** `ios: Add correlation ID generation for distributed tracing`

**Scope:**

- `iosApp/ScaniumiOS/Logging/CorrelationID.swift` (new)
- Generate UUID per API request
- Propagate in `X-Correlation-ID` header

**Acceptance Criteria:**

- Correlation ID generated for each backend request
- Included in request headers
- Logged with all events for request tracing

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-5A.1 (backend client)

**Track:** E (Observability)

---

***REMOVED******REMOVED******REMOVED*** Track 6B: UX Polish (5 PRs)

***REMOVED******REMOVED******REMOVED*** PR-6B.1: Audio Feedback (Sounds)

**Title:** `ios: Add audio feedback for capture and success events`

**Scope:**

- `iosApp/ScaniumiOS/Audio/SoundManager.swift` (new)
- Sound files: capture.wav, success.wav, error.wav
- AVAudioPlayer or SystemSoundID

**Acceptance Criteria:**

- Capture sound plays on shutter tap
- Success sound plays on item added
- Error sound plays on detection failure
- Sounds gated by settings toggle (UserDefaults)

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-3A.3 (camera capture), PR-3C.2 (settings)

**Track:** A (iOS UI) - Can run in parallel with Track 6A

---

***REMOVED******REMOVED******REMOVED*** PR-6B.2: Motion Components (Optional)

**Title:** `ios: Add motion effects to detection overlay and list`

**Scope:**

- `iosApp/ScaniumiOS/Views/DetectionOverlay.swift` (add animations)
- `iosApp/ScaniumiOS/ContentView.swift` (add list item animations)
- SwiftUI animation modifiers (spring curves)

**Acceptance Criteria:**

- Scan frame appear animation (spring, scale 0.8 → 1.0)
- Lightning pulse on detection (opacity flash)
- List item insert animation (slide + fade)

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-3A.4 (detection overlay), PR-3B.1 (list)

**Track:** A (iOS UI)

---

***REMOVED******REMOVED******REMOVED*** PR-6B.3: Theme System (Optional)

**Title:** `ios: Add custom theme system with color palette`

**Scope:**

- `iosApp/ScaniumiOS/Theme/ThemeManager.swift` (new)
- `iosApp/ScaniumiOS/Theme/Colors.swift` (custom palette)
- Apply to all views

**Acceptance Criteria:**

- Custom color palette matching Android branding
- Dark mode support (beyond system default)
- Theme toggle in settings (UserDefaults)

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-3C.2 (settings)

**Track:** A (iOS UI)

---

***REMOVED******REMOVED******REMOVED*** PR-6B.4: FTUE (First-Time User Experience)

**Title:** `ios: Add onboarding flow with permission education`

**Scope:**

- `iosApp/ScaniumiOS/Views/Onboarding/OnboardingView.swift` (new)
- 3-5 onboarding slides (feature highlights)
- Permission education screens
- Show once on first launch (UserDefaults flag)

**Acceptance Criteria:**

- Onboarding displayed on first launch
- Permission education before requesting camera/photos
- Skip button available
- Done button dismisses and sets UserDefaults flag

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-3A.2 (camera permissions)

**Track:** A (iOS UI)

---

***REMOVED******REMOVED******REMOVED*** PR-6B.5: Export Functionality

**Title:** `ios: Add CSV and Zip export with ShareSheet`

**Scope:**

- `iosApp/ScaniumiOS/Export/ExportManager.swift` (new)
- Call shared `core-export` module for CSV generation
- Zip creation via Compression framework
- UIActivityViewController (ShareSheet) presentation

**Acceptance Criteria:**

- CSV export generates tabular data
- Zip export bundles images + CSV
- ShareSheet displays with export options (Save to Files, AirDrop, etc.)

**Risk:** MED

**Do Not Touch:** All Android code

**Depends On:** PR-4.8 (database wiring), PR-1.5 (shared export module)

**Track:** A (iOS UI)

---

***REMOVED******REMOVED*** Post-Parity: Build & Deployment (5 PRs, optional)

***REMOVED******REMOVED******REMOVED*** PR-7.1: Product Flavors (Xcode Schemes)

**Title:** `ios: Add Xcode schemes for Prod/Dev/Beta variants`

**Scope:**

- Xcode schemes: Prod, Dev, Beta
- Distinct bundle IDs for side-by-side installation
- Build settings per scheme (API keys, feature flags)

**Acceptance Criteria:**

- 3 schemes configured
- Bundle IDs: `com.scanium.app`, `com.scanium.app.dev`, `com.scanium.app.beta`
- Schemes build successfully

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** None

---

***REMOVED******REMOVED******REMOVED*** PR-7.2: Feature Flags (Build-Time)

**Title:** `ios: Add build-time feature flags via compiler directives`

**Scope:**

- Swift compiler flags: `DEBUG`, `DEV_MODE`
- Feature gating in code: `***REMOVED***if DEBUG`

**Acceptance Criteria:**

- Feature flags gate dev-only features (debug overlays, dev menu)
- Prod builds exclude gated features

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-7.1

---

***REMOVED******REMOVED******REMOVED*** PR-7.3: Code Obfuscation (Release Builds)

**Title:** `ios: Enable code obfuscation for release builds`

**Scope:**

- Xcode build settings: Strip Swift Symbols, Hide Symbols
- Release configuration only

**Acceptance Criteria:**

- Release builds strip symbols
- IPA harder to reverse engineer (validate with disassembler)

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** PR-7.1

---

***REMOVED******REMOVED******REMOVED*** PR-7.4: SBOM Generation (Optional)

**Title:** `ios: Add SBOM generation for supply chain security`

**Scope:**

- CocoaPods or SPM plugin for SBOM generation
- CI integration

**Acceptance Criteria:**

- SBOM generated in CycloneDX format
- Includes all dependencies (SPM, CocoaPods)

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** None

---

***REMOVED******REMOVED******REMOVED*** PR-7.5: CVE Scanning (Optional)

**Title:** `ios: Add automated CVE scanning to CI pipeline`

**Scope:**

- CI integration: Snyk or OWASP
- Scan on every PR

**Acceptance Criteria:**

- CVE scanning runs automatically
- Fails PR if high-severity vulnerabilities found

**Risk:** LOW

**Do Not Touch:** All Android code

**Depends On:** None

---

***REMOVED******REMOVED*** Parallelization Summary

***REMOVED******REMOVED******REMOVED*** Maximum Parallelization (3 Engineers)

**Week 1-2 (Phase 0):** All engineers on documentation and validation (no parallelization)

**Week 3-5 (Phase 1):**

- **Engineer A (iOS):** PR-1.3, PR-1.4 (after PR-1.2)
- **Engineer B (iOS):** PR-1.5 (after PR-1.2)
- **Engineer C (KMP):** PR-1.1, PR-1.2

**Week 6-9 (Phase 2):**

- **Engineer A (iOS):** PR-2.2, PR-2.3, PR-2.4, PR-2.5 (sequential)
- **Engineer B (iOS):** PR-2.6 (stub, can run anytime)
- **Engineer C (ML):** PR-2.1 (model integration)

**Week 10-15 (Phase 3):**

- **Engineer A (iOS UI):** Track 3A (camera) → PR-3A.1 through PR-3A.5 (sequential)
- **Engineer B (iOS UI):** Track 3B (list/detail) → PR-3B.1 through PR-3B.6 (sequential)
- **Engineer A or B:** Track 3C (settings) → PR-3C.1 through PR-3C.3 (after Track 3A or 3B)

**Week 13-15 (Phase 4, parallel with Phase 3):**

- **Engineer C (Storage):** PR-4.1 through PR-4.8 (sequential)

**Week 16-21 (Phase 5):**

- **Engineer A (iOS):** Track 5A (backend) → PR-5A.1 through PR-5A.5 (sequential)
- **Engineer B (iOS):** Track 5B (eBay) → PR-5B.1 through PR-5B.4 (sequential, after PR-5A.3)

**Week 20-24 (Phase 6, parallel with Phase 5):**

- **Engineer C (Observability):** Track 6A → PR-6A.1 through PR-6A.4 (sequential)
- **Engineer A or B (UI):** Track 6B → PR-6B.1 through PR-6B.5 (after Track 3 complete)

---

***REMOVED******REMOVED*** Total PRs

- **Phase 0:** 2 PRs
- **Phase 1:** 5 PRs
- **Phase 2:** 6 PRs
- **Phase 3:** 14 PRs (Track 3A: 5, Track 3B: 6, Track 3C: 3)
- **Phase 4:** 8 PRs
- **Phase 5:** 9 PRs (Track 5A: 5, Track 5B: 4)
- **Phase 6:** 9 PRs (Track 6A: 4, Track 6B: 5)
- **Post-Parity (Optional):** 5 PRs

**Total: 53 PRs** (48 for parity, 5 optional)

---

***REMOVED******REMOVED*** Do Not Touch - Forbidden Paths

ALL PRs must avoid touching these paths (enforced by CI guard from PR-0.2):

```
androidApp/
android-camera-camerax/
android-ml-mlkit/
android-platform-adapters/
build.gradle.kts (root, if Android-specific)
gradle.properties (if Android-specific)
```

**Allowed paths:**

```
iosApp/
shared/ (with backward compatibility for Android)
docs/parity/
docs/BUILD_IOS_FRAMEWORKS.md
.github/workflows/ (for iOS CI only)
```

---

***REMOVED******REMOVED*** END OF PR ROADMAP
