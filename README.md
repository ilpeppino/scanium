# Objecta

An Android application that uses on-device machine learning to detect objects through the camera and estimate their prices in real-time.

## Overview

Objecta is a camera-first Android app that demonstrates object detection and price estimation using Google ML Kit. Point your camera at everyday items, and the app will identify them and provide estimated price ranges in EUR.

## Features

- **Real-Time Object Detection**: Uses Google ML Kit for on-device object detection and classification
- **Dual Capture Modes**:
  - Tap to capture: Take a photo and detect objects in the image
  - Long-press to scan: Continuous detection while holding the button
- **Price Estimation**: Category-based price ranges for detected objects
- **Items Management**: View and manage all detected items with their estimated prices
- **Privacy-First**: All processing happens on-device with no cloud calls

## Tech Stack

### Core Technologies
- **Kotlin** - Primary programming language
- **Jetpack Compose** - Modern declarative UI framework
- **Material 3** - Material Design components and theming

### Camera & ML
- **CameraX** - Camera API for preview and image capture
- **ML Kit Object Detection** - On-device object detection and classification
- **Image Analysis** - Real-time video stream processing

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
├── camera/          # Camera functionality and CameraX integration
├── items/           # Detected items management and display
├── ml/              # Object detection and pricing logic
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
2. **Point at objects** - Aim your camera at items you want to identify
3. **Capture**:
   - **Tap** the camera button to capture a single photo
   - **Long-press** the camera button to scan continuously
4. **View results** - Detected objects appear with estimated prices
5. **Manage items** - Tap "View Items" to see all detected objects
6. **Delete items** - Swipe left on items in the list to remove them

## Permissions

The app requires the following permission:
- **Camera** (`android.permission.CAMERA`) - For object detection and capture

## Current Limitations

- **Mocked pricing data** - Prices are generated locally based on category
- **No persistence** - Items are stored in memory only
- **Single-module** - Not optimized for large-scale development
- **No backend** - All processing is local

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
- Comprehensive test coverage
- CI/CD pipeline
- Backend service integration
- Cloud-based ML models

## License

[Add your license here]

## Contributing

[Add contributing guidelines here]

## Contact

[Add contact information here]
