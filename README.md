# Objecta

An Android application that uses on-device machine learning to detect objects through the camera and estimate their prices in real-time.

## Overview

Objecta is a camera-first Android app that demonstrates object detection and price estimation using Google ML Kit. Point your camera at everyday items, and the app will identify them and provide estimated price ranges in EUR.

## Features

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

## Tech Stack

### Core Technologies
- **Kotlin** - Primary programming language
- **Jetpack Compose** - Modern declarative UI framework
- **Material 3** - Material Design components and theming

### Camera & ML
- **CameraX** - Camera API for preview and image capture
- **ML Kit Object Detection** - On-device object detection and classification with tracking
- **ML Kit Barcode Scanning** - On-device barcode and QR code scanning
- **Image Analysis** - Real-time video stream processing with multi-frame candidate tracking
- **Sound Effects** - Camera shutter sound feedback

### Architecture & State
- **MVVM Pattern** - ViewModel-based architecture
- **Kotlin Coroutines** - Asynchronous programming
- **StateFlow** - Reactive state management
- **Navigation Compose** - Type-safe navigation
- **Lifecycle Components** - Android lifecycle-aware components

## Architecture

The project follows a **Simplified MVVM architecture** with feature-based package organization:

```
app/src/main/java/com/example/objecta/
├── camera/          # Camera functionality, CameraX, mode switching
├── items/           # Detected items management and display
├── ml/              # ML Kit integration, detection pipeline, pricing
│   ├── CandidateTracker.kt      # Multi-frame detection pipeline
│   ├── DetectionCandidate.kt    # Candidate data model
│   ├── DetectionLogger.kt       # Debug logging system
│   ├── RawDetection.kt          # Raw ML Kit detection data
│   ├── ObjectDetectorClient.kt  # Object detection wrapper
│   ├── BarcodeScannerClient.kt  # Barcode scanning wrapper
│   ├── PricingEngine.kt         # Price generation
│   └── ItemCategory.kt          # Category mapping
└── navigation/      # Navigation graph setup
```

For detailed architecture documentation, see [ARCHITECTURE.md](./ARCHITECTURE.md).

### Key Architectural Decisions

- **Single-module structure** - Appropriate for PoC scope
- **No DI framework** - Manual constructor injection for simplicity
- **Camera-first UX** - App opens directly to camera screen
- **On-device ML** - Privacy-focused with no network calls
- **Reactive state management** - StateFlow for UI state updates
- **Mocked pricing** - Local price estimation (ready for API integration)

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 11 or higher
- Android SDK with minimum API 24 (Android 7.0)
- Target API 35 (Android 15)

## Setup

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd objecta
   ```

2. Open the project in Android Studio

3. Sync Gradle dependencies

4. Run the app on an emulator or physical device

## Building

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

### Testing
```bash
./gradlew test                    # Run 110 unit tests
./gradlew connectedAndroidTest    # Run instrumented tests (requires device)
```

## Project Structure

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

## Usage

1. **Launch the app** - Camera screen opens automatically
2. **Select scan mode** - Use the bottom slider to switch between:
   - **Item Recognition** - Detect everyday objects
   - **Barcode** - Scan barcodes and QR codes
3. **Point at objects** - Aim your camera at items you want to identify
4. **Start scanning** - Tap the scan button to begin continuous detection
5. **View results** - Detected objects appear with confidence levels and estimated prices
6. **Manage items** - Tap "Items (N)" to see all detected objects
7. **View details** - Tap any item to see full details including confidence score

## Permissions

The app requires the following permission:
- **Camera** (`android.permission.CAMERA`) - For object detection and capture

## Current Limitations

- **Mocked pricing data** - Prices are generated locally based on category
- **No persistence** - Items are stored in memory only (cleared on app close)
- **ML Kit categories** - Object detection limited to 5 coarse categories (Fashion, Food, Home goods, Places, Plants)
- **No backend** - All processing is local

## Test Coverage

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

## Future Enhancements

### Planned Features
- Real pricing API integration
- Local database persistence (Room)
- Historical price tracking and analytics
- Barcode/QR code scanning
- Multi-currency support
- Share detected items
- Compare prices across retailers

### Technical Improvements
- Multi-module architecture
- Dependency injection (Hilt)
- ✅ ~~Comprehensive test coverage~~ (COMPLETED - 110 tests)
- CI/CD pipeline
- Backend service integration
- Cloud-based ML models
- Custom TensorFlow Lite model for fine-grained product recognition

## License

[Add your license here]

## Contributing

[Add contributing guidelines here]

## Contact

[Add contact information here]
