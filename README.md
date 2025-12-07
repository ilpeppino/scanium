***REMOVED*** Objecta

An Android application that uses on-device machine learning to detect objects through the camera and estimate their prices in real-time.

***REMOVED******REMOVED*** Overview

Objecta is a camera-first Android app that demonstrates object detection and price estimation using Google ML Kit. Point your camera at everyday items, and the app will identify them and provide estimated price ranges in EUR.

***REMOVED******REMOVED*** Features

- **Real-Time Object Detection**: Uses Google ML Kit for on-device object detection and classification
- **Multi-Frame Detection Pipeline**: Intelligent candidate tracking system that promotes only stable, high-confidence detections
- **Confidence-Aware Recognition**: Items classified with LOW/MEDIUM/HIGH confidence levels based on multi-frame observation
- **Dual Scan Modes**:
  - **Item Recognition**: Detect everyday objects with ML Kit Object Detection
  - **Barcode Scanning**: Scan barcodes and QR codes with ML Kit Barcode Scanning
- **Mode Switcher**: Elegant slider UI to switch between scan modes with camera-style animations
- **Price Estimation**: Category-based price ranges in EUR for detected objects
- **Items Management**: View and manage all detected items with their estimated prices
- **Privacy-First**: All processing happens on-device with no cloud calls
- **Debug Logging**: Comprehensive detection statistics and threshold tuning in debug builds

***REMOVED******REMOVED*** Tech Stack

***REMOVED******REMOVED******REMOVED*** Core Technologies
- **Kotlin** - Primary programming language
- **Jetpack Compose** - Modern declarative UI framework
- **Material 3** - Material Design components and theming

***REMOVED******REMOVED******REMOVED*** Camera & ML
- **CameraX** - Camera API for preview and image capture
- **ML Kit Object Detection** - On-device object detection and classification with tracking
- **ML Kit Barcode Scanning** - On-device barcode and QR code scanning
- **Image Analysis** - Real-time video stream processing with multi-frame candidate tracking
- **Sound Effects** - Camera shutter sound feedback

***REMOVED******REMOVED******REMOVED*** Architecture & State
- **MVVM Pattern** - ViewModel-based architecture
- **Kotlin Coroutines** - Asynchronous programming
- **StateFlow** - Reactive state management
- **Navigation Compose** - Type-safe navigation
- **Lifecycle Components** - Android lifecycle-aware components

***REMOVED******REMOVED*** Architecture

The project follows a **Simplified MVVM architecture** with feature-based package organization:

```
app/src/main/java/com/example/objecta/
├── camera/          ***REMOVED*** Camera functionality, CameraX, mode switching
├── items/           ***REMOVED*** Detected items management and display
├── ml/              ***REMOVED*** ML Kit integration, detection pipeline, pricing
│   ├── CandidateTracker.kt      ***REMOVED*** Multi-frame detection pipeline
│   ├── DetectionCandidate.kt    ***REMOVED*** Candidate data model
│   ├── DetectionLogger.kt       ***REMOVED*** Debug logging system
│   ├── RawDetection.kt          ***REMOVED*** Raw ML Kit detection data
│   ├── ObjectDetectorClient.kt  ***REMOVED*** Object detection wrapper
│   ├── BarcodeScannerClient.kt  ***REMOVED*** Barcode scanning wrapper
│   ├── PricingEngine.kt         ***REMOVED*** Price generation
│   └── ItemCategory.kt          ***REMOVED*** Category mapping
└── navigation/      ***REMOVED*** Navigation graph setup
```

For detailed architecture documentation, see [ARCHITECTURE.md](./ARCHITECTURE.md).

***REMOVED******REMOVED******REMOVED*** Key Architectural Decisions

- **Single-module structure** - Appropriate for PoC scope
- **No DI framework** - Manual constructor injection for simplicity
- **Camera-first UX** - App opens directly to camera screen
- **On-device ML** - Privacy-focused with no network calls
- **Reactive state management** - StateFlow for UI state updates
- **Mocked pricing** - Local price estimation (ready for API integration)

***REMOVED******REMOVED*** Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 11 or higher
- Android SDK with minimum API 24 (Android 7.0)
- Target API 35 (Android 15)

***REMOVED******REMOVED*** Setup

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd objecta
   ```

2. Open the project in Android Studio

3. Sync Gradle dependencies

4. Run the app on an emulator or physical device

***REMOVED******REMOVED*** Building

***REMOVED******REMOVED******REMOVED*** Debug Build
```bash
./gradlew assembleDebug
```

***REMOVED******REMOVED******REMOVED*** Release Build
```bash
./gradlew assembleRelease
```

***REMOVED******REMOVED******REMOVED*** Testing
```bash
./gradlew test                    ***REMOVED*** Run 110 unit tests
./gradlew connectedAndroidTest    ***REMOVED*** Run instrumented tests (requires device)
```

***REMOVED******REMOVED*** Project Structure

```
objecta/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/objecta/
│   │   │   │   ├── camera/
│   │   │   │   │   ├── CameraScreen.kt
│   │   │   │   │   └── CameraXManager.kt
│   │   │   │   ├── items/
│   │   │   │   │   ├── ItemsScreen.kt
│   │   │   │   │   └── ItemsViewModel.kt
│   │   │   │   ├── ml/
│   │   │   │   │   ├── DetectedItem.kt
│   │   │   │   │   ├── ObjectDetectorClient.kt
│   │   │   │   │   └── PricingEngine.kt
│   │   │   │   ├── navigation/
│   │   │   │   │   └── NavGraph.kt
│   │   │   │   └── MainActivity.kt
│   │   │   └── AndroidManifest.xml
│   │   └── res/
│   └── build.gradle.kts
├── gradle/
├── ARCHITECTURE.md
└── README.md
```

***REMOVED******REMOVED*** Usage

1. **Launch the app** - Camera screen opens automatically
2. **Select scan mode** - Use the bottom slider to switch between:
   - **Item Recognition** - Detect everyday objects
   - **Barcode** - Scan barcodes and QR codes
3. **Point at objects** - Aim your camera at items you want to identify
4. **Start scanning** - Tap the scan button to begin continuous detection
5. **View results** - Detected objects appear with confidence levels and estimated prices
6. **Manage items** - Tap "Items (N)" to see all detected objects
7. **View details** - Tap any item to see full details including confidence score

***REMOVED******REMOVED*** Permissions

The app requires the following permission:
- **Camera** (`android.permission.CAMERA`) - For object detection and capture

***REMOVED******REMOVED*** Current Limitations

- **Mocked pricing data** - Prices are generated locally based on category
- **No persistence** - Items are stored in memory only (cleared on app close)
- **ML Kit categories** - Object detection limited to 5 coarse categories (Fashion, Food, Home goods, Places, Plants)
- **No backend** - All processing is local

***REMOVED******REMOVED*** Test Coverage

The project includes comprehensive test coverage:
- **110 total tests** (all passing ✅)
- **Unit tests** (7 test files):
  - CandidateTracker (multi-frame detection logic)
  - DetectionCandidate (promotion criteria)
  - ItemsViewModel (state management)
  - PricingEngine, ScannedItem, ItemCategory
  - FakeObjectDetector (test fixtures)
- **Instrumented tests** (2 test files):
  - ModeSwitcher UI tests
  - ItemsViewModel integration tests
- **Test framework**: JUnit 4, Robolectric, Truth assertions, Mockk, Coroutines Test

***REMOVED******REMOVED*** Future Enhancements

***REMOVED******REMOVED******REMOVED*** Planned Features
- Real pricing API integration
- Local database persistence (Room)
- Historical price tracking and analytics
- Barcode/QR code scanning
- Multi-currency support
- Share detected items
- Compare prices across retailers

***REMOVED******REMOVED******REMOVED*** Technical Improvements
- Multi-module architecture
- Dependency injection (Hilt)
- ✅ ~~Comprehensive test coverage~~ (COMPLETED - 110 tests)
- CI/CD pipeline
- Backend service integration
- Cloud-based ML models
- Custom TensorFlow Lite model for fine-grained product recognition

***REMOVED******REMOVED*** License

[Add your license here]

***REMOVED******REMOVED*** Contributing

[Add contributing guidelines here]

***REMOVED******REMOVED*** Contact

[Add contact information here]
