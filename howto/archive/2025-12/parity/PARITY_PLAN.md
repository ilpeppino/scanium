> Archived on 2025-12-20: superseded by docs/INDEX.md.

***REMOVED*** iOS-Android Parity Plan

**Last Updated:** 2025-12-19
**Goal:** Bring iOS to feature parity with Android while keeping Android stable
**Timeline:** 8-10 weeks (with 2-3 engineers working in parallel)
**Approach:** Phased delivery with incremental testing and validation

---

***REMOVED******REMOVED*** Guiding Principles

1. **Android Stability:** Do NOT modify Android production code. Android is the stable baseline.
2. **Shared Brain First:** Ensure all shared KMP APIs work on iOS before building iOS-specific
   features.
3. **Incremental Validation:** Each phase ends with a testable milestone.
4. **Parallel Tracks:** Maximize parallelization where dependencies allow (see PR_ROADMAP.md).
5. **No Rewrite:** Port Android patterns and logic to iOS; do not redesign.
6. **Evidence-Based:** Every task references concrete Android implementation files.

---

***REMOVED******REMOVED*** Phase 0: Validation & Guardrails (Week 1)

**Goal:** Ensure build infrastructure and guardrails are in place before development starts.

***REMOVED******REMOVED******REMOVED*** Objectives:

- Validate XCFramework integration
- Add iOS permissions to Info.plist
- Set up parity definition of done
- Create CI checks to prevent Android regressions

***REMOVED******REMOVED******REMOVED*** Tasks:

***REMOVED******REMOVED******REMOVED******REMOVED*** 0.1 XCFramework Linking Validation

- **Action:** Confirm `Shared.xcframework` is embedded in iOS app target
- **Files:** `iosApp/ScaniumiOS.xcodeproj`, `iosApp/Frameworks/`
- **Acceptance Criteria:** Can import `Shared` module and instantiate `SampleItemsProvider()`
- **Estimation:** 1 day
- **Do Not Touch:** Android build.gradle files

***REMOVED******REMOVED******REMOVED******REMOVED*** 0.2 Add Missing Permissions

- **Action:** Add required usage description keys to Info.plist
- **Files:** `iosApp/ScaniumiOS/Info.plist`
- **Changes:**
    - Add `NSCameraUsageDescription`: "Scanium uses the camera to detect and identify objects for
      cataloging and selling."
    - Add `NSPhotoLibraryAddUsageDescription`: "Scanium saves scanned item images to your Photos
      library."
- **Acceptance Criteria:** App can request camera and photo permissions
- **Estimation:** 0.5 days
- **Android Ref:** `androidApp/src/main/AndroidManifest.xml:5`

***REMOVED******REMOVED******REMOVED******REMOVED*** 0.3 Define Parity Checklist

- **Action:** Create checklist of must-have features for parity milestone
- **Files:** `docs/parity/PARITY_CHECKLIST.md` (new)
- **Contents:**
    - [ ] Camera preview works
    - [ ] Object detection runs live
    - [ ] Items appear in list
    - [ ] Can delete items
    - [ ] Can save to Photos
    - [ ] Can navigate between screens
- **Estimation:** 0.5 days

***REMOVED******REMOVED******REMOVED******REMOVED*** 0.4 CI Guardrails (Optional)

- **Action:** Add GitHub Actions check to prevent Android file modifications
- **Files:** `.github/workflows/parity-guard.yml` (new)
- **Check:** Fail PR if any `androidApp/**` files modified without explicit override
- **Estimation:** 1 day
- **Do Not Touch:** Existing Android CI workflows

***REMOVED******REMOVED******REMOVED*** Phase 0 Definition of Done:

- [ ] XCFramework imports successfully
- [ ] Info.plist has camera and photo permissions
- [ ] Parity checklist exists and is reviewed
- [ ] CI guardrails in place (if implemented)

**Phase 0 Duration:** 2-3 days

---

***REMOVED******REMOVED*** Phase 1: Shared Brain Readiness (Week 2)

**Goal:** Ensure all shared KMP APIs used by Android are callable and functional from iOS.

***REMOVED******REMOVED******REMOVED*** Objectives:

- Validate `ObjectTracker` and `ItemAggregator` work on iOS
- Create Swift platform adapters for shared types
- Test shared data models round-trip

***REMOVED******REMOVED******REMOVED*** Tasks:

***REMOVED******REMOVED******REMOVED******REMOVED*** 1.1 Platform Adapters: Image Conversion

- **Action:** Create Swift extensions for `ImageRef` ↔ `UIImage` conversion
- **Files:** `iosApp/ScaniumiOS/PlatformAdapters/ImageAdapters.swift` (new)
- **API:**
  ```swift
  extension ImageRef {
      func toUIImage() -> UIImage?
  }
  extension UIImage {
      func toImageRef(mimeType: String = "image/jpeg") -> ImageRef
  }
  ```
- **Acceptance Criteria:** Can convert UIImage to ImageRef and back without data loss
- **Estimation:** 1 day
- **Android Ref:**
  `android-platform-adapters/src/main/java/com/scanium/android/platform/adapters/ImageAdapters.kt`

***REMOVED******REMOVED******REMOVED******REMOVED*** 1.2 Platform Adapters: Rect Conversion

- **Action:** Create Swift extensions for `CGRect` ↔ `NormalizedRect` conversion
- **Files:** `iosApp/ScaniumiOS/PlatformAdapters/RectAdapters.swift` (new)
- **API:**
  ```swift
  extension CGRect {
      func normalized(imageWidth: Int, imageHeight: Int) -> NormalizedRect
  }
  extension NormalizedRect {
      func toCGRect(imageWidth: Int, imageHeight: Int) -> CGRect
  }
  ```
- **Acceptance Criteria:** Can convert Vision API bounding boxes to NormalizedRect
- **Estimation:** 0.5 days
- **Android Ref:**
  `android-platform-adapters/src/main/java/com/scanium/android/platform/adapters/RectAdapters.kt`

***REMOVED******REMOVED******REMOVED******REMOVED*** 1.3 Test ObjectTracker Integration

- **Action:** Write Swift test to instantiate `ObjectTracker` and call `processFrame()`
- **Files:** `iosApp/ScaniumiOSTests/ObjectTrackerIntegrationTest.swift` (new test target)
- **Test:** Feed mock detections, verify candidates are tracked
- **Acceptance Criteria:** Test passes, tracker returns confirmed candidates
- **Estimation:** 1 day
- **Android Ref:**
  `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ObjectTracker.kt`

***REMOVED******REMOVED******REMOVED******REMOVED*** 1.4 Test ItemAggregator Integration

- **Action:** Write Swift test to instantiate `ItemAggregator` and call `processDetection()`
- **Files:** `iosApp/ScaniumiOSTests/ItemAggregatorIntegrationTest.swift` (new)
- **Test:** Feed similar detections, verify deduplication
- **Acceptance Criteria:** Test passes, aggregator merges similar items
- **Estimation:** 1 day
- **Android Ref:**
  `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ItemAggregator.kt`

***REMOVED******REMOVED******REMOVED******REMOVED*** 1.5 Extend SharedBridge

- **Action:** Add methods to SharedBridge for creating tracker/aggregator instances
- **Files:** `iosApp/ScaniumiOS/SharedBridge.swift`
- **API:**
  ```swift
  static func makeTracker(config: TrackerConfig = TrackerConfig()) -> ObjectTracker
  static func makeAggregator(config: AggregationConfig = AggregationPresets.REALTIME) -> ItemAggregator
  ```
- **Acceptance Criteria:** Can create tracker/aggregator from SwiftUI views
- **Estimation:** 0.5 days

***REMOVED******REMOVED******REMOVED*** Phase 1 Definition of Done:

- [ ] Image and rect conversion utilities exist and pass tests
- [ ] ObjectTracker and ItemAggregator can be instantiated and called from Swift
- [ ] SharedBridge provides factory methods for shared components
- [ ] All Phase 1 tests pass

**Phase 1 Duration:** 4-5 days

---

***REMOVED******REMOVED*** Phase 2: iOS Platform Adapters (Weeks 3-4)

**Goal:** Implement iOS-specific ML services and wire them to shared contracts.

***REMOVED******REMOVED******REMOVED*** Objectives:

- Complete Vision barcode and text services (already exist, validate)
- Complete CoreML object detection service (already exists, validate)
- Create classification adapters (on-device and cloud)

***REMOVED******REMOVED******REMOVED*** Tasks:

***REMOVED******REMOVED******REMOVED******REMOVED*** 2.1 Validate Vision Barcode Service

- **Action:** Test `VisionBarcodeService` with sample images
- **Files:** `iosApp/ScaniumiOS/VisionBarcodeService.swift` (existing)
- **Test:** Feed barcode images, verify correct payload extraction
- **Acceptance Criteria:** Barcode service returns detections matching ML Kit output
- **Estimation:** 0.5 days
- **Android Ref:** `androidApp/src/main/java/com/scanium/app/ml/BarcodeScannerClient.kt`

***REMOVED******REMOVED******REMOVED******REMOVED*** 2.2 Validate Vision Text Service

- **Action:** Test `VisionTextService` with sample images
- **Files:** `iosApp/ScaniumiOS/VisionTextService.swift` (existing)
- **Test:** Feed text images, verify correct OCR output
- **Acceptance Criteria:** Text service returns text blocks matching ML Kit output
- **Estimation:** 0.5 days
- **Android Ref:** `androidApp/src/main/java/com/scanium/app/ml/DocumentTextRecognitionClient.kt`

***REMOVED******REMOVED******REMOVED******REMOVED*** 2.3 Validate CoreML Object Detection Service

- **Action:** Bundle a test CoreML model and test `CoreMLObjectDetectionService`
- **Files:** `iosApp/ScaniumiOS/CoreMLObjectDetectionService.swift` (existing)
- **Test:** Feed object images, verify detections with labels and bounding boxes
- **Acceptance Criteria:** Object detection returns results matching ML Kit output
- **Estimation:** 1 day
- **Android Ref:** `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt`

***REMOVED******REMOVED******REMOVED******REMOVED*** 2.4 Implement On-Device Classifier

- **Action:** Port label→category mapping logic to Swift
- **Files:** `iosApp/ScaniumiOS/Classification/OnDeviceClassifier.swift` (new)
- **Logic:** Map Vision/CoreML labels to `ItemCategory` enum (same rules as Android)
- **Acceptance Criteria:** Given "bicycle" label, returns `.electronics` or `.homeGood` (match
  Android logic)
- **Estimation:** 1 day
- **Android Ref:**
  `androidApp/src/main/java/com/scanium/app/ml/classification/OnDeviceClassifier.kt:15`

***REMOVED******REMOVED******REMOVED******REMOVED*** 2.5 Implement Cloud Classifier

- **Action:** Create URLSession HTTP client for cloud classifier API
- **Files:** `iosApp/ScaniumiOS/Classification/CloudClassifier.swift` (new)
- **API:** POST image as base64 JPEG to backend, parse JSON response
- **Auth:** Add API key from Info.plist
- **Acceptance Criteria:** Given image, returns category and confidence from backend
- **Estimation:** 2 days
- **Android Ref:**
  `androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifier.kt:50`

***REMOVED******REMOVED******REMOVED******REMOVED*** 2.6 Implement Classification Orchestrator

- **Action:** Create mode-based classifier with fallback logic
- **Files:** `iosApp/ScaniumiOS/Classification/ClassificationOrchestrator.swift` (new)
- **Logic:** If mode=cloud → try cloud → fallback to on-device; if mode=on-device → on-device only
- **Acceptance Criteria:** Orchestrator routes to correct classifier and handles errors
- **Estimation:** 1 day
- **Android Ref:**
  `androidApp/src/main/java/com/scanium/app/ml/classification/ClassificationOrchestrator.kt:45`

***REMOVED******REMOVED******REMOVED******REMOVED*** 2.7 Add Classification Mode Persistence

- **Action:** Create UserDefaults wrapper for classification mode preference
- **Files:** `iosApp/ScaniumiOS/Settings/ClassificationPreferences.swift` (new)
- **API:** `@AppStorage("classificationMode") var mode: ClassificationMode`
- **Acceptance Criteria:** Mode persists across app launches
- **Estimation:** 0.5 days
- **Android Ref:** `androidApp/src/main/java/com/scanium/app/data/ClassificationPreferences.kt`

***REMOVED******REMOVED******REMOVED*** Phase 2 Definition of Done:

- [ ] All ML services validated with test images
- [ ] On-device classifier works (matches Android output)
- [ ] Cloud classifier works (calls backend API)
- [ ] Classification orchestrator routes correctly
- [ ] Classification mode persists in UserDefaults

**Phase 2 Duration:** 6-7 days

---

***REMOVED******REMOVED*** Phase 3: iOS UI Parity (Weeks 5-7)

**Goal:** Build camera UI, items list, detail view, and navigation matching Android UX.

***REMOVED******REMOVED******REMOVED*** Track A: Camera UI (can run in parallel with Track B)

***REMOVED******REMOVED******REMOVED******REMOVED*** 3.1 Create Camera Preview View

- **Action:** Build SwiftUI wrapper for AVCaptureVideoPreviewLayer
- **Files:** `iosApp/ScaniumiOS/Camera/CameraView.swift` (new)
- **UI:** Full-screen camera preview with AVFoundation session
- **Acceptance Criteria:** Camera preview shows live feed
- **Estimation:** 2 days
- **Android Ref:** `androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt:45`

***REMOVED******REMOVED******REMOVED******REMOVED*** 3.2 Add Capture Button

- **Action:** Add shutter button with tap gesture and haptic feedback
- **Files:** `iosApp/ScaniumiOS/Camera/ShutterButton.swift` (new)
- **UI:** Circular button with animation on tap
- **Haptics:** Use `UIImpactFeedbackGenerator`
- **Sound:** Use `AudioServicesPlaySystemSound` for shutter sound
- **Acceptance Criteria:** Tapping button captures frame and provides feedback
- **Estimation:** 1 day
- **Android Ref:** `androidApp/src/main/java/com/scanium/app/camera/ShutterButton.kt`

***REMOVED******REMOVED******REMOVED******REMOVED*** 3.3 Add Detection Overlay

- **Action:** Draw bounding boxes on camera preview using SwiftUI Canvas
- **Files:** `iosApp/ScaniumiOS/Camera/DetectionOverlay.swift` (new)
- **UI:** Overlay with color-coded boxes (green=high conf, yellow=med, red=low)
- **Acceptance Criteria:** Bounding boxes appear on detected objects in real-time
- **Estimation:** 2 days
- **Android Ref:** `androidApp/src/main/java/com/scanium/app/camera/DetectionOverlay.kt`

***REMOVED******REMOVED******REMOVED******REMOVED*** 3.4 Add Mode Switcher

- **Action:** Add segmented control to toggle object/barcode/text modes
- **Files:** `iosApp/ScaniumiOS/Camera/ModeSwitcher.swift` (new)
- **UI:** Segmented control (Picker) at top or bottom of camera
- **Acceptance Criteria:** Switching mode changes active ML service
- **Estimation:** 1 day
- **Android Ref:** `androidApp/src/main/java/com/scanium/app/camera/ModeSwitcher.kt`

***REMOVED******REMOVED******REMOVED******REMOVED*** 3.5 Add Settings Overlay

- **Action:** Create settings sheet with resolution picker and threshold slider
- **Files:** `iosApp/ScaniumiOS/Camera/CameraSettingsSheet.swift` (new)
- **UI:** Sheet modal with Picker for resolution, Slider for threshold
- **Acceptance Criteria:** Changing settings updates camera and aggregator config
- **Estimation:** 1 day
- **Android Ref:** `androidApp/src/main/java/com/scanium/app/camera/CameraSettingsOverlay.kt`

***REMOVED******REMOVED******REMOVED******REMOVED*** 3.6 Wire ML Pipeline

- **Action:** Integrate frame analysis loop with ML services and tracker
- **Files:** `iosApp/ScaniumiOS/Camera/CameraViewModel.swift` (new)
- **Logic:**
    1. AVFoundation delivers frame
    2. Convert to CGImage
    3. Call appropriate ML service (object/barcode/text)
    4. Feed detections to ObjectTracker
    5. Promote confirmed candidates to ItemAggregator
    6. Update items list state
- **Acceptance Criteria:** Detections appear in items list in real-time
- **Estimation:** 3 days
- **Android Ref:** `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt:180`

***REMOVED******REMOVED******REMOVED*** Track B: Items List & Details (can run in parallel with Track A)

***REMOVED******REMOVED******REMOVED******REMOVED*** 3.7 Create Items List State Management

- **Action:** Build ItemsViewModel as ObservableObject
- **Files:** `iosApp/ScaniumiOS/Items/ItemsViewModel.swift` (new)
- **State:**
    - `@Published var items: [ScannedItem]`
    - `func addItem(ScannedItem)`
    - `func deleteItems([String])`
    - `func deleteAll()`
- **Acceptance Criteria:** Items update reactively in UI
- **Estimation:** 1 day
- **Android Ref:** `androidApp/src/main/java/com/scanium/app/items/ItemsViewModel.kt`

***REMOVED******REMOVED******REMOVED******REMOVED*** 3.8 Enhance Items List View

- **Action:** Replace ContentView with full-featured items list
- **Files:** `iosApp/ScaniumiOS/Items/ItemsListView.swift` (rename ContentView)
- **UI:**
    - AsyncImage for thumbnails (use placeholder if nil)
    - Swipe-to-delete gesture
    - Tap for detail view
    - Multi-select mode with EditButton
    - Empty state view
- **Acceptance Criteria:** List matches Android UX
- **Estimation:** 2 days
- **Android Ref:** `androidApp/src/main/java/com/scanium/app/items/ItemsListScreen.kt`

***REMOVED******REMOVED******REMOVED******REMOVED*** 3.9 Create Item Detail View

- **Action:** Build full-screen detail modal
- **Files:** `iosApp/ScaniumiOS/Items/ItemDetailView.swift` (new)
- **UI:**
    - High-res image (AsyncImage with URL or file path)
    - Category, confidence, price range
    - Recognized text and barcode (if present)
    - Delete button
- **Acceptance Criteria:** Tapping item shows detail modal
- **Estimation:** 1 day
- **Android Ref:** `androidApp/src/main/java/com/scanium/app/items/ItemDetailDialog.kt:30`

***REMOVED******REMOVED******REMOVED******REMOVED*** 3.10 Add Floating Action Button

- **Action:** Create FAB with dropdown for Save/Sell actions
- **Files:** `iosApp/ScaniumiOS/Items/FloatingActionButton.swift` (new)
- **UI:** Custom floating button with Menu or action sheet
- **Actions:** Save to Gallery, Sell on eBay (enabled only if items selected)
- **Acceptance Criteria:** FAB appears on items list, actions trigger correctly
- **Estimation:** 1 day
- **Android Ref:** `androidApp/src/main/java/com/scanium/app/items/ItemsListScreen.kt:250`

***REMOVED******REMOVED******REMOVED*** Track C: Navigation

***REMOVED******REMOVED******REMOVED******REMOVED*** 3.11 Create Navigation Architecture

- **Action:** Set up NavigationStack with path-based routing
- **Files:** `iosApp/ScaniumiOS/Navigation/Router.swift` (new)
- **Routes:**
    - `.camera` (start destination)
    - `.itemsList`
    - `.sellOnEbay(itemIds: [String])`
- **Acceptance Criteria:** Can navigate between screens with back button
- **Estimation:** 1 day
- **Android Ref:** `androidApp/src/main/java/com/scanium/app/navigation/NavGraph.kt:21`

***REMOVED******REMOVED******REMOVED******REMOVED*** 3.12 Wire Navigation

- **Action:** Add NavigationLink from camera to items list, items list to sell screen
- **Files:** `iosApp/ScaniumiOS/ScaniumiOSApp.swift`, `CameraView.swift`, `ItemsListView.swift`
- **Acceptance Criteria:** Can navigate camera → items → sell → back
- **Estimation:** 0.5 days

***REMOVED******REMOVED******REMOVED*** Phase 3 Definition of Done:

- [ ] Camera preview shows live feed
- [ ] Detections appear as bounding boxes
- [ ] Items list updates in real-time
- [ ] Can delete items (swipe or multi-select)
- [ ] Can view item details
- [ ] Can navigate between camera, list, and sell screens
- [ ] FAB shows save/sell actions

**Phase 3 Duration:** 12-14 days (with 2 engineers on Tracks A & B in parallel)

---

***REMOVED******REMOVED*** Phase 4: Storage & Export Parity (Week 8)

**Goal:** Implement photo library save matching Android MediaStore functionality.

***REMOVED******REMOVED******REMOVED*** Tasks:

***REMOVED******REMOVED******REMOVED******REMOVED*** 4.1 Implement Photo Library Save

- **Action:** Use PHPhotoLibrary to save images to Photos
- **Files:** `iosApp/ScaniumiOS/Storage/PhotoLibrarySaver.swift` (new)
- **API:**
  ```swift
  func saveImagesToPhotos(images: [(itemId: String, image: UIImage)]) async -> SaveResult
  ```
- **Logic:**
    1. Request photo library permission
    2. Create "Scanium" album (if not exists)
    3. Save images to album
    4. Return success/failure counts
- **Acceptance Criteria:** Images saved to Photos in "Scanium" album
- **Estimation:** 2 days
- **Android Ref:** `androidApp/src/main/java/com/scanium/app/media/MediaStoreSaver.kt:58`

***REMOVED******REMOVED******REMOVED******REMOVED*** 4.2 Wire Save Action

- **Action:** Connect FAB "Save to Gallery" action to PhotoLibrarySaver
- **Files:** `iosApp/ScaniumiOS/Items/ItemsListView.swift`
- **Logic:** Call `saveImagesToPhotos()` for selected items, show alert with result
- **Acceptance Criteria:** Selecting items and tapping Save exports to Photos
- **Estimation:** 0.5 days

***REMOVED******REMOVED******REMOVED******REMOVED*** 4.3 Add Error Handling

- **Action:** Handle permission denied, save failures, partial success
- **Files:** `iosApp/ScaniumiOS/Storage/SaveResult.swift` (new)
- **UI:** Show alert with success count and error messages
- **Acceptance Criteria:** User sees meaningful error messages
- **Estimation:** 0.5 days
- **Android Ref:** `androidApp/src/main/java/com/scanium/app/media/MediaStoreSaver.kt:23`

***REMOVED******REMOVED******REMOVED*** Phase 4 Definition of Done:

- [ ] Can save selected items to Photos
- [ ] Images appear in "Scanium" album
- [ ] Error handling works (permission denied, save failure)
- [ ] User sees success/failure feedback

**Phase 4 Duration:** 3 days

---

***REMOVED******REMOVED*** Phase 5: Selling Flow (Optional - Week 9)

**Goal:** Implement eBay selling screen matching Android (mock API for v1).

**Note:** This phase is optional for MVP parity. If eBay integration is not a priority, defer to
post-parity.

***REMOVED******REMOVED******REMOVED*** Tasks:

***REMOVED******REMOVED******REMOVED******REMOVED*** 5.1 Create Selling Screen UI

- **Action:** Build listing creation form
- **Files:** `iosApp/ScaniumiOS/Selling/SellOnEbayView.swift` (new)
- **UI:**
    - List of selected items
    - Per-item fields: title, description, price, condition
    - Post to eBay button
    - Listing status indicators
- **Acceptance Criteria:** UI matches Android selling screen
- **Estimation:** 3 days
- **Android Ref:** `androidApp/src/main/java/com/scanium/app/selling/ui/SellOnEbayScreen.kt:39`

***REMOVED******REMOVED******REMOVED******REMOVED*** 5.2 Create Marketplace Service

- **Action:** Port EbayMarketplaceService to Swift with mock API
- **Files:**
    - `iosApp/ScaniumiOS/Selling/EbayMarketplaceService.swift` (new)
    - `iosApp/ScaniumiOS/Selling/MockEbayApi.swift` (new)
- **API:** URLSession POST to mock endpoint (configurable delay/failure)
- **Acceptance Criteria:** Can post listings to mock API
- **Estimation:** 2 days
- **Android Ref:**
  `androidApp/src/main/java/com/scanium/app/selling/data/EbayMarketplaceService.kt:25`

***REMOVED******REMOVED******REMOVED******REMOVED*** 5.3 Create Domain Models

- **Action:** Port Listing, ListingCondition, ListingStatus to Swift
- **Files:** `iosApp/ScaniumiOS/Selling/Models/` (new)
- **Models:** `Listing`, `ListingCondition`, `ListingStatus`, `ListingImage`, `ListingError`
- **Acceptance Criteria:** Models match Kotlin equivalents
- **Estimation:** 1 day
- **Android Ref:** `androidApp/src/main/java/com/scanium/app/selling/domain/`

***REMOVED******REMOVED******REMOVED******REMOVED*** 5.4 Wire Selling Flow

- **Action:** Connect items list FAB to selling screen
- **Files:** `iosApp/ScaniumiOS/Items/ItemsListView.swift`
- **Navigation:** Pass selected item IDs to selling screen
- **Acceptance Criteria:** Can navigate from items list to selling screen with pre-populated items
- **Estimation:** 0.5 days

***REMOVED******REMOVED******REMOVED*** Phase 5 Definition of Done:

- [ ] Selling screen shows selected items
- [ ] Can edit listing details (title, price, condition)
- [ ] Can post listings (to mock API)
- [ ] Listing status updates in UI
- [ ] Can navigate back to items list

**Phase 5 Duration:** 6-7 days

---

***REMOVED******REMOVED*** Phase 6: Observability & Final Polish (Week 10)

**Goal:** Add logging, crash reporting, and final parity validation.

***REMOVED******REMOVED******REMOVED*** Tasks:

***REMOVED******REMOVED******REMOVED******REMOVED*** 6.1 Add Structured Logging

- **Action:** Create OSLog wrapper for consistent logging
- **Files:** `iosApp/ScaniumiOS/Logging/Logger.swift` (new)
- **API:** `Logger.i()`, `Logger.e()`, `Logger.d()` (matches Android Log)
- **Acceptance Criteria:** App uses OSLog instead of print()
- **Estimation:** 1 day
- **Android Ref:** Android Log usage throughout `androidApp/`

***REMOVED******REMOVED******REMOVED******REMOVED*** 6.2 Add Crash Reporting (Optional)

- **Action:** Integrate Sentry SDK for iOS
- **Files:** `iosApp/ScaniumiOS/ScaniumiOSApp.swift`
- **Setup:** Initialize Sentry in app launch
- **Acceptance Criteria:** Crashes reported to Sentry dashboard
- **Estimation:** 1 day
- **Note:** Only if Sentry also added to Android (not currently implemented)

***REMOVED******REMOVED******REMOVED******REMOVED*** 6.3 Final Parity Validation

- **Action:** Run parity checklist (from Phase 0) and fix any gaps
- **Files:** `docs/parity/PARITY_CHECKLIST.md`
- **Test:** Manual end-to-end flow on iOS vs Android
- **Acceptance Criteria:** All checklist items pass
- **Estimation:** 2 days

***REMOVED******REMOVED******REMOVED******REMOVED*** 6.4 Performance Optimization

- **Action:** Profile camera frame rate, ML latency, UI responsiveness
- **Tools:** Xcode Instruments
- **Fixes:** Reduce frame rate if needed, optimize image conversion, debounce UI updates
- **Acceptance Criteria:** Camera runs at 30 fps, ML latency <100ms
- **Estimation:** 1 day

***REMOVED******REMOVED******REMOVED*** Phase 6 Definition of Done:

- [ ] Structured logging in place
- [ ] Crash reporting configured (if applicable)
- [ ] Parity checklist 100% complete
- [ ] Performance meets targets

**Phase 6 Duration:** 5 days

---

***REMOVED******REMOVED*** Testing Strategy (Continuous)

Throughout all phases:

***REMOVED******REMOVED******REMOVED*** Unit Tests:

- Write XCTest for each new ViewModel, service, and utility
- Target: 70%+ code coverage

***REMOVED******REMOVED******REMOVED*** Integration Tests:

- Test shared KMP integration (tracker, aggregator)
- Test ML services with sample images

***REMOVED******REMOVED******REMOVED*** Manual Testing:

- Test camera → scan → list → save flow on real device
- Compare iOS and Android side-by-side

***REMOVED******REMOVED******REMOVED*** Regression Testing:

- Run Android tests after each iOS PR to ensure no regressions (CI check)

---

***REMOVED******REMOVED*** Risk Mitigation

***REMOVED******REMOVED******REMOVED*** Risk 1: Shared KMP APIs Don't Work on iOS

- **Mitigation:** Phase 1 validates shared APIs early
- **Fallback:** Create iOS-native equivalents if KMP bridging fails

***REMOVED******REMOVED******REMOVED*** Risk 2: CoreML Models Underperform vs ML Kit

- **Mitigation:** Phase 2 validates ML accuracy with test images
- **Fallback:** Use cloud classifier as primary, on-device as fallback

***REMOVED******REMOVED******REMOVED*** Risk 3: SwiftUI Performance Issues

- **Mitigation:** Phase 6 includes performance profiling
- **Fallback:** Optimize with UIKit hybrid approach if needed

***REMOVED******REMOVED******REMOVED*** Risk 4: Timeline Overruns

- **Mitigation:** Prioritize critical path (Phases 0-4), defer Phase 5 if needed
- **Fallback:** Ship MVP without selling flow, add in v1.1

---

***REMOVED******REMOVED*** Staffing Recommendations

**Optimal Team:**

- **Engineer 1:** Camera UI + ML Integration (Tracks A + ML services)
- **Engineer 2:** Items List + Navigation + Storage (Tracks B + C + Phase 4)
- **Engineer 3:** Shared integration + Platform adapters + Testing (Phases 1, 2, 6)

**Minimum Team:**

- **2 Engineers:** Can complete in 10-12 weeks with some sequential work

---

***REMOVED******REMOVED*** Success Criteria

iOS is at parity when:

1. ✅ Camera preview works with live object detection
2. ✅ Items appear in list as they're scanned
3. ✅ Can delete items (swipe or multi-select)
4. ✅ Can save items to Photos
5. ✅ Can navigate between camera, list, and sell screens
6. ✅ Shared KMP tracking and aggregation work correctly
7. ✅ Classification (on-device or cloud) matches Android accuracy
8. ✅ All critical gaps (HIGH risk) resolved
9. ✅ Parity checklist 100% complete
10. ✅ No Android regressions introduced

---

***REMOVED******REMOVED*** Next Steps

1. Review this plan with stakeholders
2. Assign engineers to tracks
3. Kick off Phase 0 (validation)
4. Begin parallel development in Phases 1-3
5. Track progress in PR roadmap (see PR_ROADMAP.md)
