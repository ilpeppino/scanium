> Archived on 2025-12-20: superseded by docs/INDEX.md.
***REMOVED*** iOS Parity PR Roadmap

**Last Updated:** 2025-12-19
**Purpose:** Detailed PR-by-PR implementation roadmap with parallelization strategy
**Total PRs:** 42
**Estimated Timeline:** 8-10 weeks with 2-3 engineers

---

***REMOVED******REMOVED*** How to Use This Roadmap

- **PR Title:** Descriptive name for the pull request
- **Scope:** Files and modules to be created/modified
- **Acceptance Criteria:** What must work for PR to be merged
- **Risk Level:** LOW/MED/HIGH based on complexity and blast radius
- **Estimation:** Days (assuming 1 engineer)
- **Track:** Parallelization track (A/B/C/D/E)
- **Dependencies:** PR numbers that must merge first
- **Do Not Touch:** Files/modules that must remain unchanged

---

***REMOVED******REMOVED*** Parallelization Tracks

To maximize velocity, work can proceed on 5 concurrent tracks:

- **Track A: Camera & ML** - Camera UI, detection pipeline, ML services
- **Track B: Items List & UI** - Items list, detail view, gestures
- **Track C: Shared Integration** - KMP bridging, platform adapters
- **Track D: Navigation & Settings** - Navigation architecture, settings
- **Track E: Storage & Selling** - Photo save, eBay integration

**Tracks C and D are prerequisites for most other tracks. Tracks A, B, and E can run in parallel after Track C completes.**

---

***REMOVED******REMOVED*** Phase 0: Validation & Guardrails (Week 1)

***REMOVED******REMOVED******REMOVED*** PR-001: Validate XCFramework Integration
- **Track:** C
- **Scope:**
  - Verify `iosApp/Frameworks/Shared.xcframework` is embedded
  - Add import test in `iosApp/ScaniumiOS/ScaniumiOSApp.swift`
- **Acceptance Criteria:**
  - [ ] Can import `Shared` module
  - [ ] Can instantiate `SampleItemsProvider()`
  - [ ] No build errors
- **Risk:** LOW
- **Estimation:** 0.5 days
- **Dependencies:** None
- **Do Not Touch:** `shared/**`, `androidApp/**`

---

***REMOVED******REMOVED******REMOVED*** PR-002: Add Missing iOS Permissions
- **Track:** C
- **Scope:**
  - `iosApp/ScaniumiOS/Info.plist`
- **Changes:**
  - Add `NSCameraUsageDescription`
  - Add `NSPhotoLibraryAddUsageDescription`
- **Acceptance Criteria:**
  - [ ] Info.plist includes both permission keys
  - [ ] App builds successfully
- **Risk:** LOW
- **Estimation:** 0.5 days
- **Dependencies:** None
- **Do Not Touch:** `androidApp/src/main/AndroidManifest.xml`

---

***REMOVED******REMOVED******REMOVED*** PR-003: Create Parity Checklist
- **Track:** D
- **Scope:**
  - Create `docs/parity/PARITY_CHECKLIST.md`
- **Acceptance Criteria:**
  - [ ] Checklist includes all MVP features
  - [ ] Matches Android baseline capabilities
- **Risk:** LOW
- **Estimation:** 0.5 days
- **Dependencies:** None
- **Do Not Touch:** Any code files

---

***REMOVED******REMOVED******REMOVED*** PR-004: Add CI Parity Guard (Optional)
- **Track:** D
- **Scope:**
  - Create `.github/workflows/parity-guard.yml`
- **Acceptance Criteria:**
  - [ ] Workflow fails if `androidApp/**` modified in iOS PRs
  - [ ] Can override with `[android-change]` in PR title
- **Risk:** LOW
- **Estimation:** 1 day
- **Dependencies:** None
- **Do Not Touch:** Existing `.github/workflows/` Android workflows

---

***REMOVED******REMOVED*** Phase 1: Shared Brain Readiness (Week 2)

***REMOVED******REMOVED******REMOVED*** PR-005: Platform Adapters - Image Conversion
- **Track:** C
- **Scope:**
  - Create `iosApp/ScaniumiOS/PlatformAdapters/ImageAdapters.swift`
- **API:**
  - `extension ImageRef { func toUIImage() -> UIImage? }`
  - `extension UIImage { func toImageRef() -> ImageRef }`
- **Acceptance Criteria:**
  - [ ] Can convert UIImage to ImageRef and back
  - [ ] Unit test passes
  - [ ] No data loss in round-trip conversion
- **Risk:** MED
- **Estimation:** 1 day
- **Dependencies:** PR-001
- **Do Not Touch:** `android-platform-adapters/**`

---

***REMOVED******REMOVED******REMOVED*** PR-006: Platform Adapters - Rect Conversion
- **Track:** C
- **Scope:**
  - Create `iosApp/ScaniumiOS/PlatformAdapters/RectAdapters.swift`
- **API:**
  - `extension CGRect { func normalized(imageWidth: Int, imageHeight: Int) -> NormalizedRect }`
  - `extension NormalizedRect { func toCGRect(imageWidth: Int, imageHeight: Int) -> CGRect }`
- **Acceptance Criteria:**
  - [ ] Can convert CGRect to NormalizedRect and back
  - [ ] Unit test passes
- **Risk:** LOW
- **Estimation:** 0.5 days
- **Dependencies:** PR-001
- **Do Not Touch:** `android-platform-adapters/**`

---

***REMOVED******REMOVED******REMOVED*** PR-007: Test ObjectTracker Integration
- **Track:** C
- **Scope:**
  - Create `iosApp/ScaniumiOSTests/` (new test target)
  - Create `iosApp/ScaniumiOSTests/ObjectTrackerIntegrationTest.swift`
- **Test:**
  - Instantiate `ObjectTracker` from shared
  - Feed mock `DetectionInfo` list
  - Verify confirmed candidates returned
- **Acceptance Criteria:**
  - [ ] Test passes
  - [ ] Tracker behaves same as Android tests
- **Risk:** MED
- **Estimation:** 1 day
- **Dependencies:** PR-001, PR-006
- **Do Not Touch:** `shared/core-tracking/**`

---

***REMOVED******REMOVED******REMOVED*** PR-008: Test ItemAggregator Integration
- **Track:** C
- **Scope:**
  - Create `iosApp/ScaniumiOSTests/ItemAggregatorIntegrationTest.swift`
- **Test:**
  - Instantiate `ItemAggregator` from shared
  - Feed similar detections
  - Verify deduplication and merging
- **Acceptance Criteria:**
  - [ ] Test passes
  - [ ] Aggregator behaves same as Android tests
- **Risk:** MED
- **Estimation:** 1 day
- **Dependencies:** PR-001, PR-005, PR-006
- **Do Not Touch:** `shared/core-tracking/**`

---

***REMOVED******REMOVED******REMOVED*** PR-009: Extend SharedBridge with Factory Methods
- **Track:** C
- **Scope:**
  - Modify `iosApp/ScaniumiOS/SharedBridge.swift`
- **API:**
  - `static func makeTracker() -> ObjectTracker`
  - `static func makeAggregator() -> ItemAggregator`
- **Acceptance Criteria:**
  - [ ] Can create tracker/aggregator from SwiftUI
  - [ ] Factory methods use default configs
- **Risk:** LOW
- **Estimation:** 0.5 days
- **Dependencies:** PR-007, PR-008
- **Do Not Touch:** Kotlin shared code

---

***REMOVED******REMOVED*** Phase 2: iOS Platform Adapters (Weeks 3-4)

***REMOVED******REMOVED******REMOVED*** PR-010: Validate Vision Barcode Service
- **Track:** A
- **Scope:**
  - Review `iosApp/ScaniumiOS/VisionBarcodeService.swift` (existing)
  - Create test: `iosApp/ScaniumiOSTests/VisionBarcodeServiceTest.swift`
- **Test:** Feed barcode images, verify payload extraction
- **Acceptance Criteria:**
  - [ ] Test passes with QR, EAN, Code128 barcodes
  - [ ] Output matches ML Kit format
- **Risk:** LOW
- **Estimation:** 0.5 days
- **Dependencies:** PR-001
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/ml/BarcodeScannerClient.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-011: Validate Vision Text Service
- **Track:** A
- **Scope:**
  - Review `iosApp/ScaniumiOS/VisionTextService.swift` (existing)
  - Create test: `iosApp/ScaniumiOSTests/VisionTextServiceTest.swift`
- **Test:** Feed text images, verify OCR output
- **Acceptance Criteria:**
  - [ ] Test passes with document images
  - [ ] Text extraction matches ML Kit
- **Risk:** LOW
- **Estimation:** 0.5 days
- **Dependencies:** PR-001
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/ml/DocumentTextRecognitionClient.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-012: Validate CoreML Object Detection Service
- **Track:** A
- **Scope:**
  - Review `iosApp/ScaniumiOS/CoreMLObjectDetectionService.swift` (existing)
  - Bundle test CoreML model in Xcode project
  - Create test: `iosApp/ScaniumiOSTests/CoreMLObjectDetectionServiceTest.swift`
- **Test:** Feed object images, verify detections
- **Acceptance Criteria:**
  - [ ] Test passes with bicycle, laptop, etc.
  - [ ] Bounding boxes and labels match ML Kit
- **Risk:** MED
- **Estimation:** 1 day
- **Dependencies:** PR-001
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-013: Implement On-Device Classifier
- **Track:** A
- **Scope:**
  - Create `iosApp/ScaniumiOS/Classification/OnDeviceClassifier.swift`
- **Logic:** Port label→category mapping from Android
- **Acceptance Criteria:**
  - [ ] Given "bicycle" label, returns correct category
  - [ ] Matches Android classification output
  - [ ] Unit test passes
- **Risk:** MED
- **Estimation:** 1 day
- **Dependencies:** PR-012
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/ml/classification/OnDeviceClassifier.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-014: Implement Cloud Classifier
- **Track:** A
- **Scope:**
  - Create `iosApp/ScaniumiOS/Classification/CloudClassifier.swift`
- **API:** URLSession POST to cloud backend
- **Auth:** Read API key from Info.plist
- **Acceptance Criteria:**
  - [ ] Can POST image as base64 JPEG
  - [ ] Parses JSON response with category and confidence
  - [ ] Integration test with mock server passes
- **Risk:** MED
- **Estimation:** 2 days
- **Dependencies:** PR-005
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifier.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-015: Implement Classification Orchestrator
- **Track:** A
- **Scope:**
  - Create `iosApp/ScaniumiOS/Classification/ClassificationOrchestrator.swift`
- **Logic:** Mode-based routing with cloud→on-device fallback
- **Acceptance Criteria:**
  - [ ] Routes to correct classifier based on mode
  - [ ] Fallback works on cloud failure
  - [ ] Unit test passes
- **Risk:** LOW
- **Estimation:** 1 day
- **Dependencies:** PR-013, PR-014
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/ml/classification/ClassificationOrchestrator.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-016: Add Classification Mode Persistence
- **Track:** D
- **Scope:**
  - Create `iosApp/ScaniumiOS/Settings/ClassificationPreferences.swift`
- **API:** `@AppStorage("classificationMode") var mode: ClassificationMode`
- **Acceptance Criteria:**
  - [ ] Mode persists across app launches
  - [ ] Defaults to `.onDevice`
- **Risk:** LOW
- **Estimation:** 0.5 days
- **Dependencies:** None
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/data/ClassificationPreferences.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-017: Add API Configuration to Info.plist
- **Track:** D
- **Scope:**
  - Modify `iosApp/ScaniumiOS/Info.plist`
- **Changes:**
  - Add `CLOUD_CLASSIFIER_URL` key
  - Add `CLOUD_CLASSIFIER_API_KEY` key
- **Acceptance Criteria:**
  - [ ] CloudClassifier can read config from Info.plist
- **Risk:** LOW
- **Estimation:** 0.5 days
- **Dependencies:** None
- **Do Not Touch:** `androidApp/build.gradle.kts`

---

***REMOVED******REMOVED*** Phase 3: iOS UI Parity (Weeks 5-7)

***REMOVED******REMOVED******REMOVED*** Track A: Camera UI

***REMOVED******REMOVED******REMOVED*** PR-018: Create Camera Preview View
- **Track:** A
- **Scope:**
  - Create `iosApp/ScaniumiOS/Camera/CameraView.swift`
  - Create `iosApp/ScaniumiOS/Camera/CameraPreviewLayer.swift` (UIViewRepresentable)
- **UI:** Full-screen camera preview with AVFoundation
- **Acceptance Criteria:**
  - [ ] Camera preview shows live feed
  - [ ] Works on real device
- **Risk:** MED
- **Estimation:** 2 days
- **Dependencies:** PR-002
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-019: Add Shutter Button
- **Track:** A
- **Scope:**
  - Create `iosApp/ScaniumiOS/Camera/ShutterButton.swift`
- **UI:** Circular button with animation, haptics, sound
- **Acceptance Criteria:**
  - [ ] Button appears on camera view
  - [ ] Tapping triggers haptic and sound feedback
  - [ ] Frame capture callback invoked
- **Risk:** LOW
- **Estimation:** 1 day
- **Dependencies:** PR-018
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/camera/ShutterButton.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-020: Add Detection Overlay
- **Track:** A
- **Scope:**
  - Create `iosApp/ScaniumiOS/Camera/DetectionOverlay.swift`
- **UI:** SwiftUI Canvas overlay with bounding boxes
- **Acceptance Criteria:**
  - [ ] Overlay renders bounding boxes on camera preview
  - [ ] Color-coded by confidence (green/yellow/red)
- **Risk:** MED
- **Estimation:** 2 days
- **Dependencies:** PR-018, PR-006
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/camera/DetectionOverlay.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-021: Add Mode Switcher
- **Track:** A
- **Scope:**
  - Create `iosApp/ScaniumiOS/Camera/ModeSwitcher.swift`
- **UI:** Segmented control (object/barcode/text)
- **Acceptance Criteria:**
  - [ ] Switcher appears on camera view
  - [ ] Changing mode updates detection service
- **Risk:** LOW
- **Estimation:** 1 day
- **Dependencies:** PR-018
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/camera/ModeSwitcher.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-022: Add Settings Sheet
- **Track:** A
- **Scope:**
  - Create `iosApp/ScaniumiOS/Camera/CameraSettingsSheet.swift`
- **UI:** Sheet with resolution picker and threshold slider
- **Acceptance Criteria:**
  - [ ] Settings button opens sheet
  - [ ] Changing settings updates camera config
- **Risk:** LOW
- **Estimation:** 1 day
- **Dependencies:** PR-018
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/camera/CameraSettingsOverlay.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-023: Create Camera ViewModel
- **Track:** A
- **Scope:**
  - Create `iosApp/ScaniumiOS/Camera/CameraViewModel.swift`
- **State:**
  - `@Published var detections: [DetectedObject]`
  - `@Published var mode: DetectionMode`
  - `@Published var resolution: CaptureResolution`
- **Acceptance Criteria:**
  - [ ] ViewModel manages camera state
  - [ ] Can be observed from CameraView
- **Risk:** LOW
- **Estimation:** 1 day
- **Dependencies:** PR-018
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/camera/CameraViewModel.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-024: Wire ML Pipeline to Camera
- **Track:** A
- **Scope:**
  - Modify `iosApp/ScaniumiOS/Camera/CameraViewModel.swift`
  - Integrate with `AVFoundationFrameSource.swift`
- **Logic:**
  1. Frame callback delivers CGImage
  2. Call ML service based on mode
  3. Feed detections to ObjectTracker
  4. Promote candidates to ItemAggregator
  5. Update items list via shared ViewModel
- **Acceptance Criteria:**
  - [ ] Detections appear in real-time
  - [ ] Bounding boxes show on overlay
  - [ ] Items added to list automatically
- **Risk:** HIGH
- **Estimation:** 3 days
- **Dependencies:** PR-009, PR-012, PR-020, PR-023
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt`

---

***REMOVED******REMOVED******REMOVED*** Track B: Items List & Details

***REMOVED******REMOVED******REMOVED*** PR-025: Create Items ViewModel
- **Track:** B
- **Scope:**
  - Create `iosApp/ScaniumiOS/Items/ItemsViewModel.swift`
- **State:**
  - `@Published var items: [ScannedItem]`
  - `func addItem(ScannedItem)`
  - `func deleteItems([String])`
  - `func deleteAll()`
- **Acceptance Criteria:**
  - [ ] ViewModel manages items state
  - [ ] Items update reactively
- **Risk:** LOW
- **Estimation:** 1 day
- **Dependencies:** PR-009
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/items/ItemsViewModel.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-026: Enhance Items List View
- **Track:** B
- **Scope:**
  - Rename `iosApp/ScaniumiOS/ContentView.swift` to `ItemsListView.swift`
  - Enhance with AsyncImage, swipe-to-delete, multi-select
- **UI:**
  - AsyncImage for thumbnails
  - Swipe actions for delete
  - EditButton for multi-select
  - Empty state view
- **Acceptance Criteria:**
  - [ ] List shows thumbnails (or placeholders)
  - [ ] Swipe-to-delete works
  - [ ] Multi-select mode works
- **Risk:** MED
- **Estimation:** 2 days
- **Dependencies:** PR-025, PR-005
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/items/ItemsListScreen.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-027: Create Item Detail View
- **Track:** B
- **Scope:**
  - Create `iosApp/ScaniumiOS/Items/ItemDetailView.swift`
- **UI:** Full-screen modal with high-res image and details
- **Acceptance Criteria:**
  - [ ] Tapping item opens detail view
  - [ ] Shows image, category, confidence, price, text, barcode
  - [ ] Delete button works
- **Risk:** LOW
- **Estimation:** 1 day
- **Dependencies:** PR-026
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/items/ItemDetailDialog.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-028: Add Floating Action Button
- **Track:** B
- **Scope:**
  - Create `iosApp/ScaniumiOS/Items/FloatingActionButton.swift`
- **UI:** Custom FAB with Menu (Save to Gallery, Sell on eBay)
- **Acceptance Criteria:**
  - [ ] FAB appears on items list
  - [ ] Actions enabled only when items selected
- **Risk:** LOW
- **Estimation:** 1 day
- **Dependencies:** PR-026
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/items/ItemsListScreen.kt`

---

***REMOVED******REMOVED******REMOVED*** Track C: Navigation

***REMOVED******REMOVED******REMOVED*** PR-029: Create Navigation Architecture
- **Track:** D
- **Scope:**
  - Create `iosApp/ScaniumiOS/Navigation/Router.swift`
  - Create `iosApp/ScaniumiOS/Navigation/Route.swift`
- **Routes:**
  - `.camera` (start)
  - `.itemsList`
  - `.sellOnEbay(itemIds: [String])`
- **Acceptance Criteria:**
  - [ ] NavigationStack with NavigationPath
  - [ ] Can push and pop routes
- **Risk:** MED
- **Estimation:** 1 day
- **Dependencies:** None
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/navigation/NavGraph.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-030: Wire Navigation to Views
- **Track:** D
- **Scope:**
  - Modify `iosApp/ScaniumiOS/ScaniumiOSApp.swift`
  - Add NavigationLink to `CameraView.swift` and `ItemsListView.swift`
- **Acceptance Criteria:**
  - [ ] Can navigate camera → items list
  - [ ] Can navigate items list → sell screen
  - [ ] Back button works
- **Risk:** LOW
- **Estimation:** 0.5 days
- **Dependencies:** PR-018, PR-026, PR-029
- **Do Not Touch:** Android navigation files

---

***REMOVED******REMOVED*** Phase 4: Storage & Export Parity (Week 8)

***REMOVED******REMOVED******REMOVED*** PR-031: Implement Photo Library Saver
- **Track:** E
- **Scope:**
  - Create `iosApp/ScaniumiOS/Storage/PhotoLibrarySaver.swift`
- **API:**
  - `func saveImagesToPhotos(images: [(itemId: String, image: UIImage)]) async -> SaveResult`
- **Logic:**
  - Request photo permission
  - Create "Scanium" album
  - Save images to album
- **Acceptance Criteria:**
  - [ ] Images saved to Photos
  - [ ] Album created if not exists
  - [ ] Returns success/failure counts
- **Risk:** MED
- **Estimation:** 2 days
- **Dependencies:** PR-002, PR-005
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/media/MediaStoreSaver.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-032: Create SaveResult Model
- **Track:** E
- **Scope:**
  - Create `iosApp/ScaniumiOS/Storage/SaveResult.swift`
- **Model:**
  - `successCount: Int`
  - `failureCount: Int`
  - `errors: [String]`
  - `statusMessage: String`
- **Acceptance Criteria:**
  - [ ] Model matches Android `SaveResult`
- **Risk:** LOW
- **Estimation:** 0.5 days
- **Dependencies:** None
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/media/MediaStoreSaver.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-033: Wire Save Action to FAB
- **Track:** E
- **Scope:**
  - Modify `iosApp/ScaniumiOS/Items/ItemsListView.swift`
- **Logic:** Call `saveImagesToPhotos()` when FAB "Save" tapped
- **Acceptance Criteria:**
  - [ ] Tapping Save exports selected items to Photos
  - [ ] Alert shows success/failure message
- **Risk:** LOW
- **Estimation:** 0.5 days
- **Dependencies:** PR-028, PR-031, PR-032
- **Do Not Touch:** Android code

---

***REMOVED******REMOVED*** Phase 5: Selling Flow (Week 9 - Optional)

***REMOVED******REMOVED******REMOVED*** PR-034: Create Selling Screen UI
- **Track:** E
- **Scope:**
  - Create `iosApp/ScaniumiOS/Selling/SellOnEbayView.swift`
- **UI:** Form with title, description, price, condition per item
- **Acceptance Criteria:**
  - [ ] UI matches Android selling screen
  - [ ] Can edit listing details
- **Risk:** MED
- **Estimation:** 3 days
- **Dependencies:** PR-029
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/selling/ui/SellOnEbayScreen.kt`

---

***REMOVED******REMOVED******REMOVED*** PR-035: Create Listing Domain Models
- **Track:** E
- **Scope:**
  - Create `iosApp/ScaniumiOS/Selling/Models/Listing.swift`
  - Create `iosApp/ScaniumiOS/Selling/Models/ListingCondition.swift`
  - Create `iosApp/ScaniumiOS/Selling/Models/ListingStatus.swift`
- **Models:** Match Kotlin domain models
- **Acceptance Criteria:**
  - [ ] Models match Android equivalents
- **Risk:** LOW
- **Estimation:** 1 day
- **Dependencies:** None
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/selling/domain/`

---

***REMOVED******REMOVED******REMOVED*** PR-036: Implement Marketplace Service
- **Track:** E
- **Scope:**
  - Create `iosApp/ScaniumiOS/Selling/EbayMarketplaceService.swift`
  - Create `iosApp/ScaniumiOS/Selling/MockEbayApi.swift`
- **API:** URLSession POST to mock eBay endpoint
- **Acceptance Criteria:**
  - [ ] Can post listings (mock)
  - [ ] Returns listing ID and status
- **Risk:** MED
- **Estimation:** 2 days
- **Dependencies:** PR-035
- **Do Not Touch:** `androidApp/src/main/java/com/scanium/app/selling/data/`

---

***REMOVED******REMOVED******REMOVED*** PR-037: Wire Selling Flow to Navigation
- **Track:** E
- **Scope:**
  - Modify `iosApp/ScaniumiOS/Items/ItemsListView.swift`
- **Logic:** FAB "Sell on eBay" navigates to sell screen with item IDs
- **Acceptance Criteria:**
  - [ ] Can navigate to sell screen
  - [ ] Selected items pre-populated
- **Risk:** LOW
- **Estimation:** 0.5 days
- **Dependencies:** PR-030, PR-034, PR-036
- **Do Not Touch:** Android code

---

***REMOVED******REMOVED*** Phase 6: Final Polish & Validation (Week 10)

***REMOVED******REMOVED******REMOVED*** PR-038: Add Structured Logging
- **Track:** D
- **Scope:**
  - Create `iosApp/ScaniumiOS/Logging/Logger.swift`
- **API:** `Logger.i()`, `Logger.e()`, `Logger.d()` (matches Android Log)
- **Acceptance Criteria:**
  - [ ] Uses OSLog instead of print()
  - [ ] Log levels work correctly
- **Risk:** LOW
- **Estimation:** 1 day
- **Dependencies:** None
- **Do Not Touch:** Android logging

---

***REMOVED******REMOVED******REMOVED*** PR-039: Add Crash Reporting (Optional)
- **Track:** D
- **Scope:**
  - Add Sentry SDK to Xcode project
  - Initialize in `iosApp/ScaniumiOS/ScaniumiOSApp.swift`
- **Acceptance Criteria:**
  - [ ] Sentry initialized on app launch
  - [ ] Test crash reported to dashboard
- **Risk:** LOW
- **Estimation:** 1 day
- **Dependencies:** None
- **Do Not Touch:** Android (unless adding Sentry there too)

---

***REMOVED******REMOVED******REMOVED*** PR-040: Performance Optimization
- **Track:** A
- **Scope:**
  - Profile camera frame rate with Instruments
  - Optimize image conversion, ML latency
- **Changes:**
  - Reduce frame rate to 5-10 fps if needed
  - Add frame throttling in CameraViewModel
  - Debounce UI updates
- **Acceptance Criteria:**
  - [ ] Camera runs at 30 fps
  - [ ] ML latency <100ms
  - [ ] No UI jank
- **Risk:** MED
- **Estimation:** 1 day
- **Dependencies:** PR-024
- **Do Not Touch:** Android code

---

***REMOVED******REMOVED******REMOVED*** PR-041: Final Parity Validation
- **Track:** D
- **Scope:**
  - Run parity checklist (from PR-003)
  - Manual end-to-end testing iOS vs Android
- **Test:**
  - Camera → scan objects → items appear → save to photos → sell on eBay
- **Acceptance Criteria:**
  - [ ] All parity checklist items pass
  - [ ] iOS behavior matches Android
- **Risk:** LOW
- **Estimation:** 2 days
- **Dependencies:** All prior PRs
- **Do Not Touch:** Android code

---

***REMOVED******REMOVED******REMOVED*** PR-042: Documentation Update
- **Track:** D
- **Scope:**
  - Update `README.md` with iOS setup instructions
  - Update `docs/parity/` with final status
- **Acceptance Criteria:**
  - [ ] README includes iOS build instructions
  - [ ] Parity docs reflect 100% completion
- **Risk:** LOW
- **Estimation:** 0.5 days
- **Dependencies:** PR-041
- **Do Not Touch:** Android documentation (unless adding cross-platform notes)

---

***REMOVED******REMOVED*** Parallelization Matrix

| Week | Track A (Camera/ML) | Track B (Items UI) | Track C (Shared) | Track D (Nav/Settings) | Track E (Storage/Selling) |
|------|---------------------|-------------------|------------------|----------------------|--------------------------|
| 1 | - | - | PR-001, PR-002 | PR-003, PR-004 | - |
| 2 | - | - | PR-005, PR-006, PR-007, PR-008, PR-009 | - | - |
| 3 | PR-010, PR-011, PR-012, PR-013 | - | - | PR-016, PR-017 | - |
| 4 | PR-014, PR-015 | - | - | - | - |
| 5 | PR-018, PR-019, PR-020 | PR-025, PR-026 | - | PR-029 | - |
| 6 | PR-021, PR-022, PR-023 | PR-027, PR-028 | - | PR-030 | - |
| 7 | PR-024 | - | - | - | - |
| 8 | - | - | - | - | PR-031, PR-032, PR-033 |
| 9 | - | - | - | - | PR-034, PR-035, PR-036, PR-037 |
| 10 | PR-040 | - | - | PR-038, PR-039, PR-041, PR-042 | - |

**Critical Path:** PR-001 → PR-009 → PR-012 → PR-018 → PR-023 → PR-024 → PR-041

---

***REMOVED******REMOVED*** Risk Mitigation Per Track

***REMOVED******REMOVED******REMOVED*** Track A Risks:
- **Risk:** CoreML models underperform vs ML Kit
- **Mitigation:** PR-012 validates early, fallback to cloud classifier

***REMOVED******REMOVED******REMOVED*** Track B Risks:
- **Risk:** Image loading from ImageRef fails
- **Mitigation:** PR-005 validates conversion early, use placeholders as fallback

***REMOVED******REMOVED******REMOVED*** Track C Risks:
- **Risk:** Shared KMP APIs don't work on iOS
- **Mitigation:** PR-007, PR-008 validate early in Phase 1

***REMOVED******REMOVED******REMOVED*** Track E Risks:
- **Risk:** Photo permission denied by user
- **Mitigation:** Graceful error handling in PR-033

---

***REMOVED******REMOVED*** Success Metrics

After all PRs merged:
- [ ] iOS app has 100% feature parity with Android
- [ ] All HIGH and MED risk gaps resolved
- [ ] Parity checklist 100% complete
- [ ] No Android regressions (all Android tests pass)
- [ ] iOS app runs smoothly on real device
- [ ] Performance targets met (30 fps, <100ms ML latency)

---

***REMOVED******REMOVED*** Next Steps

1. Assign PRs to engineers based on tracks
2. Create GitHub project board with PR columns
3. Begin Phase 0 PRs (PR-001 through PR-004)
4. Track progress weekly and adjust timeline as needed
