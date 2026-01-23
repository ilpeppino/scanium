> Archived on 2025-12-20: superseded by docs/INDEX.md.

# iOS Current State

**Last Updated:** 2025-12-19
**iOS App Module:** `iosApp/ScaniumiOS`
**Supporting Modules:** `shared/` (XCFramework integration)
**Total iOS Swift Files:** 11

---

## Executive Summary

The iOS implementation is in **early scaffolding phase**. Platform-specific ML services (Vision,
CoreML, AVFoundation) exist as isolated components, but there is **NO integrated UI, NO camera
screen, NO tracking integration, NO selling flow, and NO storage layer**. The app currently displays
only a static list of mock/shared items.

**Architecture:** SwiftUI (basic), AVFoundation (camera frame source), Vision Framework (ML),
CoreML (object detection)
**iOS Target:** iOS 14+ (assumed, not explicitly configured)
**Shared Integration:** Partial (can import Shared XCFramework and load items, but no live tracking)

---

## Capability Breakdown

### 1. Camera Capture

**Status:** 🟡 PARTIAL
**Module:** `iosApp/ScaniumiOS/AVFoundationFrameSource.swift`

#### What Exists:

- **AVFoundation Frame Source:**
    - `AVFoundationFrameSource.swift` - Basic camera session setup
    - Captures video frames via `AVCaptureSession`
    - `AVCaptureVideoDataOutputSampleBufferDelegate` for frame callbacks
    - JPEG encoding at 65% quality
    - Orientation handling for portrait mode

#### What's Missing:

- ❌ **NO Camera UI** - No SwiftUI view showing camera preview
- ❌ **NO Capture Button** - No shutter button or capture controls
- ❌ **NO Settings UI** - No resolution picker, mode switcher, threshold slider
- ❌ **NO Detection Overlay** - No bounding box visualization
- ❌ **NO Error Handling UI** - No permission prompts, error states
- ❌ **NO Orientation Support** - Limited to portrait only
- ❌ **NO Haptic Feedback** - No shutter sound or vibration
- ❌ **NO Integration** - Frame source not connected to any UI or ML pipeline

#### Evidence:

- Implemented: `iosApp/ScaniumiOS/AVFoundationFrameSource.swift:1-102`
- Missing UI: `iosApp/ScaniumiOS/ContentView.swift` - Only shows list, no camera view
- Missing Integration: No `CameraView.swift` or equivalent

#### Root Cause:

- Missing SwiftUI camera view
- Missing wiring between frame source and UI

---

### 2. ML / Object Detection

**Status:** 🟡 PARTIAL
**Module:** `iosApp/ScaniumiOS/` (multiple ML service files)

#### What Exists:

- **Vision Barcode Service:**
    - `VisionBarcodeService.swift` - Vision framework barcode detection
    - All barcode symbologies supported
    - Returns `BarcodeDetection` with payload and normalized rect
    - Async/await API

- **Vision Text Service:**
    - `VisionTextService.swift` - Vision framework OCR
    - Accurate recognition level with language correction
    - Returns `TextBlock` with text and normalized rect
    - Async/await API

- **CoreML Object Detection Service:**
    - `CoreMLObjectDetectionService.swift` - Vision + CoreML integration
    - Supports custom ML models
    - Returns `DetectedObject` with label, confidence, bounding box, tracking ID
    - Async/await API

#### What's Missing:

- ❌ **NO Pipeline Integration** - Services exist but are not called from any UI or camera flow
- ❌ **NO Real-time Processing** - No frame-by-frame analysis loop
- ❌ **NO Model Download/Management** - No dynamic model loading or update mechanism
- ❌ **NO Detection Logging** - No debug crop saving or logging
- ❌ **NO Multi-mode Switching** - No toggle between object/barcode/text modes
- ❌ **NO Performance Optimization** - No frame throttling or queue management

#### Evidence:

- Implemented:
    - `iosApp/ScaniumiOS/VisionBarcodeService.swift:1-31`
    - `iosApp/ScaniumiOS/VisionTextService.swift:1-32`
    - `iosApp/ScaniumiOS/CoreMLObjectDetectionService.swift:1-44`
- Missing Integration: No calls to these services in `ContentView.swift` or any other UI
- Missing Protocols: `PlatformContracts.swift:1-50` defines protocols but no concrete
  implementations are used

#### Root Cause:

- Missing camera UI to drive frame analysis
- Missing orchestration layer to coordinate ML services

---

### 3. Classification

**Status:** ❌ NOT IMPLEMENTED

#### What's Missing:

- ❌ **NO On-Device Classifier** - No Swift implementation of label-to-category mapping
- ❌ **NO Cloud Classifier** - No HTTP client for backend API
- ❌ **NO Classification Orchestrator** - No mode selection or fallback logic
- ❌ **NO Settings** - No classification mode toggle in UI
- ❌ **NO Persistence** - No UserDefaults for classification preferences

#### Evidence:

- No classification files in `iosApp/ScaniumiOS/`
- No HTTP client or URLSession usage for cloud API
- No settings screen or settings model

#### Root Cause:

- Not started - requires UI and backend integration

---

### 4. Object Tracking & Aggregation

**Status:** 🟡 PARTIAL (Shared Available, Not Integrated)
**Shared Module:** `shared/core-tracking` (KMP XCFramework)

#### What Exists:

- **Shared KMP Modules Available:**
    - `ObjectTracker` and `ItemAggregator` compiled to XCFramework
    - Can be imported via `#if canImport(Shared)`
    - Basic bridging in `SharedBridge.swift`

#### What's Missing:

- ❌ **NO Integration** - Tracker and aggregator not instantiated or called from iOS code
- ❌ **NO Live Tracking** - No frame-by-frame detection feeding into tracker
- ❌ **NO Real-time Deduplication** - iOS does not use aggregator for item similarity
- ❌ **NO Threshold Control** - No UI or state management for similarity threshold
- ❌ **NO State Management** - No ObservableObject or @Published properties for tracking state

#### Evidence:

- Shared available: `iosApp/ScaniumiOS/SharedBridge.swift:96-98` - Can load sample items from KMP
- Not integrated: No calls to `ObjectTracker.processFrame()` or `ItemAggregator.processDetection()`
- Static data only: `ContentView.swift:4-6` loads items once, no live updates

#### Root Cause:

- Missing camera + ML integration to generate live detections
- Missing SwiftUI state management for tracking pipeline

---

### 5. Items List & Details UI

**Status:** 🟡 PARTIAL
**Module:** `iosApp/ScaniumiOS/ContentView.swift`

#### What Exists:

- **Basic Items List:**
    - `ContentView.swift` - SwiftUI List with NavigationStack
    - Displays scanned items (mock or shared data)
    - Shows: category, price range, confidence, recognized text, barcode, listing status
    - System icon placeholder (`shippingbox`)

#### What's Missing:

- ❌ **NO Thumbnail Display** - No image loading or display
- ❌ **NO Detail View** - No tap-to-expand detail modal
- ❌ **NO Swipe-to-Delete** - No gesture handling for deletion
- ❌ **NO Multi-Select Mode** - No checkboxes or batch operations
- ❌ **NO Floating Action Button** - No save/sell actions
- ❌ **NO Empty State** - No "No items yet" placeholder
- ❌ **NO Pull-to-Refresh** - No refresh gesture
- ❌ **NO Navigation** - Single view, no navigation to camera or selling screens

#### Evidence:

- Implemented: `iosApp/ScaniumiOS/ContentView.swift:3-56` - Basic list
- Missing: No `ItemDetailView.swift`, no swipe gestures, no action buttons

#### Root Cause:

- UI development paused at prototype stage
- Missing navigation and state management architecture

---

### 6. Storage & Gallery Export

**Status:** ❌ NOT IMPLEMENTED

#### What's Missing:

- ❌ **NO Photo Library Integration** - No PhotoKit or PHPhotoLibrary usage
- ❌ **NO Save to Photos** - No batch save operation
- ❌ **NO Album Management** - No "Scanium" album creation
- ❌ **NO Error Handling** - No permission checks or failure reporting
- ❌ **NO High-Res Export** - No image URI or file path handling

#### Evidence:

- No PhotoKit imports in any Swift file
- No save functionality in `ContentView.swift`

#### Root Cause:

- Not started - requires Photos framework integration and UI

---

### 7. eBay Selling Integration

**Status:** ❌ NOT IMPLEMENTED

#### What's Missing:

- ❌ **NO Selling Screen** - No SwiftUI view for listing creation
- ❌ **NO Marketplace Service** - No Swift equivalent of `EbayMarketplaceService`
- ❌ **NO Listing Models** - No Swift domain models for listings
- ❌ **NO Mock API** - No testing infrastructure for selling flow
- ❌ **NO eBay API Client** - No OAuth or REST integration

#### Evidence:

- Only mention: `iosApp/ScaniumiOS/MockItems.swift:15` - `listingStatus` field in mock data
- No selling files: `find iosApp -name "*Sell*" -o -name "*Listing*" -o -name "*Ebay*"` returns
  nothing
- No navigation: `ContentView.swift` has no navigation to selling screen

#### Root Cause:

- Not started - requires full UI flow, API client, and state management

---

### 8. Navigation

**Status:** ❌ NOT IMPLEMENTED (Single View Only)
**Module:** `iosApp/ScaniumiOS/ContentView.swift`

#### What Exists:

- **NavigationStack:**
    - `ContentView.swift:9` - SwiftUI NavigationStack wrapper
    - Navigation title: "Scanium (iOS)"

#### What's Missing:

- ❌ **NO Routes** - No route definitions or navigation paths
- ❌ **NO Camera Screen** - No camera destination
- ❌ **NO Selling Screen** - No selling destination
- ❌ **NO Detail Screen** - No item detail destination
- ❌ **NO Navigation State** - No NavigationPath or coordinator
- ❌ **NO Deep Linking** - No URL scheme handling

#### Evidence:

- Single view: `ContentView.swift:3-56` - No `NavigationLink` or `.navigationDestination()`
- No router: No `Router.swift` or equivalent

#### Root Cause:

- App architecture not yet defined for multi-screen navigation

---

### 9. Theming & UI

**Status:** 🟡 PARTIAL (System Defaults Only)

#### What Exists:

- **SwiftUI System Theme:**
    - Automatic light/dark mode from iOS
    - Material icons from SF Symbols
    - Default fonts and spacing

#### What's Missing:

- ❌ **NO Custom Theme** - No brand colors or custom color palette
- ❌ **NO Typography** - No custom fonts or type scale
- ❌ **NO Design System** - No reusable components or style guide
- ❌ **NO Icons** - Only system SF Symbols, no custom camera icons

#### Evidence:

- Default theme: `ContentView.swift:12-40` - Uses system colors and fonts
- No theme file: No `Theme.swift` or `Colors.swift`

#### Root Cause:

- Design system not yet ported from Android

---

### 10. Data Models & Platform Adapters

**Status:** 🟡 PARTIAL (Bridging Exists)
**Module:** `iosApp/ScaniumiOS/` (multiple bridging files)

#### What Exists:

- **Shared Bridge:**
    - `SharedBridge.swift` - Abstraction layer for KMP Shared framework
    - `DataSource` protocol for loading items
    - `Session` protocol for lifecycle management
    - Mock and KMP-backed implementations
    - Swift-to-Kotlin type mapping (ScannedItem, ItemCategory, etc.)

- **Type Aliases:**
    - `ScaniumSharedTypes.swift` - Swift type aliases for shared types
    - `ScannedItem`, `ItemCategory`, `ItemListingStatus`, `PriceRange`, etc.

- **Platform Contracts:**
    - `PlatformContracts.swift` - Protocol definitions for ML services
    - `FrameSource`, `BarcodeService`, `TextRecognitionService`, `ObjectDetectionService`
    - `ImageRef`, `NormalizedRect` Swift structs

- **Mock Data:**
    - `MockItems.swift` - Static sample items for testing

#### What's Missing:

- ❌ **NO Platform Adapters** - No Swift equivalent of `android-platform-adapters`
- ❌ **NO Image Conversion Utilities** - No UIImage ↔ ImageRef helpers
- ❌ **NO Rect Conversion** - No CGRect ↔ NormalizedRect utilities
- ❌ **NO Async Bridging** - No Swift async/await bridges to KMP coroutines

#### Evidence:

- Implemented:
    - `iosApp/ScaniumiOS/SharedBridge.swift:1-173`
    - `iosApp/ScaniumiOS/ScaniumSharedTypes.swift:1-120` (assumed based on usage)
    - `iosApp/ScaniumiOS/PlatformContracts.swift:1-50` (assumed)
- Missing: No `PlatformAdapters.swift` or image conversion utilities

#### Root Cause:

- Bridging exists but not yet extended for full platform interop

---

### 11. Build & Security

**Status:** 🟡 PARTIAL (Basic Xcode Project)
**Module:** `iosApp/ScaniumiOS.xcodeproj`

#### What Exists:

- **Xcode Project:**
    - Basic iOS app target
    - SwiftUI lifecycle
    - `Info.plist` with bundle ID and version

#### What's Missing:

- ❌ **NO Shared Framework Linking** - XCFramework not yet integrated into build
- ❌ **NO API Configuration** - No Info.plist keys for cloud classifier URL/API key
- ❌ **NO Build Schemes** - No Debug/Release configurations with feature flags
- ❌ **NO Security Hardening** - No App Transport Security config, no certificate pinning
- ❌ **NO SBOM** - No dependency scanning or supply chain security
- ❌ **NO Code Signing** - Basic automatic signing only

#### Evidence:

- Basic plist: `iosApp/ScaniumiOS/Info.plist:1-30` - Minimal config
- No Frameworks: `iosApp/Frameworks/` directory exists but likely empty or not linked
- No build config: No `.xcconfig` files for environment-specific settings

#### Root Cause:

- Project setup not yet finalized for production

---

### 12. Testing

**Status:** ❌ NOT IMPLEMENTED

#### What's Missing:

- ❌ **NO Unit Tests** - No XCTest files
- ❌ **NO UI Tests** - No XCUI tests
- ❌ **NO Shared Tests** - KMP shared tests exist but iOS may not run them
- ❌ **NO Test Fixtures** - No test data or mocks beyond `MockItems.swift`

#### Evidence:

- No test files: `find iosApp -name "*Test*.swift" -o -name "*Tests.swift"` returns nothing
- No test targets: Xcode project likely has no test targets configured

#### Root Cause:

- Testing infrastructure not yet set up

---

### 13. Observability & Logging

**Status:** ❌ NOT IMPLEMENTED

#### What's Missing:

- ❌ **NO Crash Reporting** - No Sentry or Firebase Crashlytics
- ❌ **NO Analytics** - No Firebase Analytics or Mixpanel
- ❌ **NO Performance Monitoring** - No Firebase Performance or custom metrics
- ❌ **NO Logging Framework** - Uses print() only, no OSLog or structured logging

#### Evidence:

- No observability imports in any Swift file
- No `os.log` usage

#### Root Cause:

- Observability not yet prioritized

---

### 14. Permissions & Info.plist

**Status:** ⚠️ INCOMPLETE (Camera Permission Missing)
**Module:** `iosApp/ScaniumiOS/Info.plist`

#### What Exists:

- **Basic Info.plist:**
    - Bundle ID, version, display name
    - Scene manifest (single scene)

#### What's Missing:

- ❌ **NO Camera Permission** - Missing `NSCameraUsageDescription` key
- ❌ **NO Photo Library Permission** - Missing `NSPhotoLibraryAddUsageDescription` (for saving)
- ❌ **NO Background Modes** - No background processing entitlements (if needed)

#### Evidence:

- Missing keys: `iosApp/ScaniumiOS/Info.plist:1-30` - No usage description keys

#### Root Cause:

- Permissions not yet added because camera UI not implemented

---

### 15. Feature Flags

**Status:** 🟡 PARTIAL
**Module:** `iosApp/ScaniumiOS/FeatureFlags.swift`

#### What Exists:

- **FeatureFlags:**
    - `FeatureFlags.swift` - Static flags for testing
    - `useMocks` flag to toggle between mock and shared data

#### What's Missing:

- ❌ **NO Remote Config** - No dynamic feature flags
- ❌ **NO A/B Testing** - No experimentation framework
- ❌ **NO Persistence** - Flags are hardcoded, not user-configurable

#### Evidence:

- Implemented: `iosApp/ScaniumiOS/FeatureFlags.swift` (assumed, referenced in `ContentView.swift:5`)

#### Root Cause:

- Feature flag system exists but not yet extended

---

## Summary Table

| Capability           | Status | Evidence                   | Notes                             |
|----------------------|--------|----------------------------|-----------------------------------|
| Camera UI            | ❌      | No CameraView.swift        | Frame source exists but no UI     |
| ML Services          | 🟡     | 3 service files exist      | Services exist but not integrated |
| Classification       | ❌      | No classifier files        | Not started                       |
| Tracking Integration | ❌      | SharedBridge.swift         | Shared available, not used        |
| Items List           | 🟡     | ContentView.swift          | Basic list, no actions            |
| Item Details         | ❌      | No detail view             | Not started                       |
| Storage/Export       | ❌      | No PhotoKit usage          | Not started                       |
| eBay Selling         | ❌      | No selling files           | Not started                       |
| Navigation           | ❌      | Single view only           | Not started                       |
| Theming              | 🟡     | System defaults            | No custom theme                   |
| Platform Adapters    | 🟡     | SharedBridge exists        | Minimal bridging                  |
| Build Config         | 🟡     | Basic Xcode project        | Not production-ready              |
| Testing              | ❌      | No test files              | Not started                       |
| Observability        | ❌      | No logging/crash reporting | Not started                       |
| Permissions          | ⚠️     | Info.plist incomplete      | Missing camera permission         |

**Legend:**

- ✅ Complete
- 🟡 Partial (some components exist but not integrated or incomplete)
- ❌ Not Implemented
- ⚠️ Incomplete/Broken

---

## Overall Assessment

The iOS app is **~15% complete** compared to Android baseline:

- **Platform Services (AVFoundation, Vision, CoreML):** Exist as isolated components
- **UI:** Single static list view only
- **Integration:** Shared KMP framework can be imported, but not actively used
- **User Flow:** No camera → scan → review → save/sell flow

**Critical Gaps:**

1. No camera UI
2. No live ML integration
3. No tracking/aggregation integration
4. No selling flow
5. No storage/export
6. No navigation
7. No testing

**Next Step:** Build gap matrix and prioritized parity plan.
