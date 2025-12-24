***REMOVED*** Scanium

An Android application that uses on-device machine learning to detect objects through the camera and estimate their prices in real-time.

***REMOVED******REMOVED*** Documentation

See [docs/INDEX.md](docs/INDEX.md) for the canonical doc set and entry point.

***REMOVED******REMOVED*** Testing

***REMOVED******REMOVED******REMOVED*** With Android SDK (Workstation / CI)
- Run KMP unit tests: `./gradlew :shared:core-models:test :shared:core-tracking:test`
- Run Android unit tests: `./gradlew :androidApp:testDebugUnitTest`
- Run all tests: `./gradlew test`
- Run coverage: `./gradlew koverVerify`
- Instrumented tests (emulator/device required): `./gradlew :androidApp:connectedDebugAndroidTest`

***REMOVED******REMOVED******REMOVED*** Container environments (Claude Code, Docker without Android SDK)
**⚠️ Note:** Full test suite requires Android SDK. Use JVM-only validation:
- JVM tests (shared modules): `./gradlew :shared:core-models:jvmTest :shared:core-tracking:jvmTest`
- Pre-push validation: `./gradlew prePushJvmCheck`
- Install pre-push hook: `./scripts/dev/install-hooks.sh`

See `docs/DEV_GUIDE.md` for details.

***REMOVED******REMOVED*** Overview

Scanium is a camera-first Android app that demonstrates object detection and price estimation using Google ML Kit. Point your camera at everyday items, and the app will identify them and provide estimated price ranges in EUR.

***REMOVED******REMOVED*** Features

- **Real-Time Object Detection**: Uses Google ML Kit for on-device object detection and classification
- **Domain Pack Category System**: Config-driven fine-grained categorization beyond ML Kit's 5 coarse categories
  - 23 specific categories (sofa, chair, laptop, TV, shoes, etc.)
  - 10 extractable attributes (brand, color, material, size, condition, etc.)
  - JSON-based configuration for easy extension
  - Ready for CLIP, OCR, and cloud-based attribute extraction
- **Visual Detection Overlay**: Real-time bounding boxes and labels displayed on detected objects
  - Live visualization of ML Kit detections with category labels
  - Confidence scores shown on each detection
  - Automatic overlay updates during continuous scanning
  - Clean UI with minimal overlay text
- **Intelligent Object Tracking**: Multi-frame tracking system that eliminates duplicate detections of the same object
  - ML Kit trackingId-based matching with spatial fallback
  - Confirmation thresholds ensure stable, confident detections
  - Automatic expiry of objects that leave the frame
- **Multiple Scan Modes**:
  - **Object Detection**: Point at objects and scan continuously with de-duplication
  - **Barcode Scanning**: Scan QR codes and barcodes with instant recognition
  - **Document Text**: Extract text from documents and images using OCR
- **Dual Capture Modes**:
  - Tap to capture: Take a photo and detect objects in the image
  - Long-press to scan: Continuous detection while holding the button
- **Price Estimation**: Category-based price ranges for detected objects
- **Items Management**: View and manage all detected items with their estimated prices
- **eBay Selling Integration (Mock)**: Complete marketplace flow with realistic simulation
  - Multi-select items from detected list
  - Review and edit listing drafts (title, price, condition)
  - High-quality image preparation for web/mobile listings
  - Mock eBay API with configurable delays and failure modes
  - Real-time listing status tracking (Posting, Listed, Failed)
  - View listing URLs and status badges
  - Debug settings for testing various scenarios
- **Privacy-First**: All processing happens on-device with no cloud calls
- **Debug Logging**: Comprehensive detection statistics and threshold tuning in debug builds

***REMOVED******REMOVED*** Tech Stack

***REMOVED******REMOVED******REMOVED*** Android Application
- **Kotlin** - Primary programming language
- **Jetpack Compose** - Modern declarative UI framework
- **Material 3** - Material Design components and theming with Scanium branding
- **CameraX** - Camera API for preview and image capture
- **ML Kit Object Detection** - On-device object detection and classification with tracking
- **ML Kit Barcode Scanning** - On-device barcode and QR code scanning
- **Image Analysis** - Real-time video stream processing with multi-frame candidate tracking
- **MVVM Pattern** - ViewModel-based architecture
- **Kotlin Coroutines** - Asynchronous programming
- **StateFlow** - Reactive state management
- **Navigation Compose** - Type-safe navigation
- **Lifecycle Components** - Android lifecycle-aware components
- **Kotlinx Serialization** - JSON parsing for Domain Pack configuration

***REMOVED******REMOVED******REMOVED*** Backend Services
- **Node.js + TypeScript** - Backend API server
- **Prisma** - Database ORM and migrations
- **PostgreSQL** - Primary database
- **Express.js** - HTTP server framework
- **ngrok** - Development tunneling for mobile device testing
- **Docker Compose** - Container orchestration for local development

***REMOVED******REMOVED******REMOVED*** Observability Stack (LGTM + Alloy)
- **Grafana** - Visualization dashboards and alerting
- **Alloy** - OpenTelemetry (OTLP) receiver and router
- **Loki** - Log aggregation and storage
- **Tempo** - Distributed tracing backend
- **Mimir** - Prometheus-compatible metrics storage
- **Docker Compose** - Containerized observability infrastructure

***REMOVED******REMOVED*** Architecture

Scanium is a **full-stack mobile application** consisting of three main components:

***REMOVED******REMOVED******REMOVED*** System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                      Android Application                        │
│  (Kotlin + Compose + ML Kit + CameraX)                         │
└────────────────────┬────────────────────────────────────────────┘
                     │ HTTPS (ngrok tunnel in dev)
                     │ OTLP telemetry (Alloy)
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Backend API Server                           │
│  (Node.js + TypeScript + Prisma + PostgreSQL)                  │
└────────────────────┬────────────────────────────────────────────┘
                     │ OpenTelemetry
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│              Observability Stack (LGTM + Alloy)                │
│  Grafana → Loki (logs) + Tempo (traces) + Mimir (metrics)      │
└─────────────────────────────────────────────────────────────────┘
```

***REMOVED******REMOVED******REMOVED*** Android Application

The Android app follows a **Simplified MVVM architecture** with feature-based package organization:

```
androidApp/src/main/java/com/scanium/app/
├── camera/          ***REMOVED*** Camera functionality, CameraX, mode switching
├── domain/          ***REMOVED*** Domain Pack system (config, repository, category engine)
├── items/           ***REMOVED*** Detected items management and display
├── ml/              ***REMOVED*** Object detection and pricing logic
├── tracking/        ***REMOVED*** Object tracking and de-duplication system
├── selling/         ***REMOVED*** eBay marketplace integration (mock)
│   ├── data/        ***REMOVED*** API, repository, marketplace service
│   ├── domain/      ***REMOVED*** Listing models, status, conditions
│   ├── ui/          ***REMOVED*** Sell screen, listing VM, debug settings
│   └── util/        ***REMOVED*** Image preparation, draft mapping
└── navigation/      ***REMOVED*** Navigation graph setup
```

***REMOVED******REMOVED******REMOVED*** Backend Services

```
backend/
├── src/
│   ├── index.ts            ***REMOVED*** Express server entry point
│   ├── routes/             ***REMOVED*** API endpoints
│   ├── services/           ***REMOVED*** Business logic
│   └── middleware/         ***REMOVED*** Auth, validation, error handling
├── prisma/
│   ├── schema.prisma       ***REMOVED*** Database schema
│   └── migrations/         ***REMOVED*** Version-controlled schema changes
└── docker-compose.yml      ***REMOVED*** PostgreSQL container
```

***REMOVED******REMOVED******REMOVED*** Observability Infrastructure

```
monitoring/
├── docker-compose.yml      ***REMOVED*** LGTM stack + Alloy services
├── grafana/
│   ├── provisioning/       ***REMOVED*** Auto-configured datasources
│   └── dashboards/         ***REMOVED*** Pre-built visualization dashboards
├── alloy/alloy.hcl         ***REMOVED*** OTLP routing configuration
├── loki/loki.yaml          ***REMOVED*** Log aggregation config
├── tempo/tempo.yaml        ***REMOVED*** Distributed tracing config
└── mimir/mimir.yaml        ***REMOVED*** Metrics storage config
```

For detailed architecture documentation, see [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md).
For tracking implementation details, see [TRACKING_IMPLEMENTATION.md](./md/features/TRACKING_IMPLEMENTATION.md).

***REMOVED******REMOVED******REMOVED*** Key Architectural Decisions

- **Multi-module structure** - Android app plus shared core libraries for models, tracking, and domain packs
- **No DI framework** - Manual constructor injection for simplicity
- **Camera-first UX** - App opens directly to camera screen
- **On-device ML** - Privacy-focused with no network calls
- **Reactive state management** - StateFlow for UI state updates
- **Mocked pricing** - Local price estimation (ready for API integration)

***REMOVED******REMOVED*** Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- **JDK 17** (required) - See [SETUP.md](./SETUP.md) for installation instructions
- Android SDK with minimum API 24 (Android 7.0)
- Target API 34 (Android 14)

***REMOVED******REMOVED*** Setup

For detailed cross-platform setup instructions (macOS, Linux, Windows), see [SETUP.md](./SETUP.md).

***REMOVED******REMOVED******REMOVED*** Android Application Setup

**Quick Start:**

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd scanium
   ```

2. Ensure Java 17 is installed (see SETUP.md if needed)

3. Open the project in Android Studio, or build from command line:
   ```bash
   ./scripts/build.sh assembleDebug  ***REMOVED*** Auto-detects Java 17
   ```

4. Run the app on an emulator or physical device

***REMOVED******REMOVED******REMOVED*** Backend Development Setup

**Prerequisites:**
- Node.js 20+
- Docker (for PostgreSQL and monitoring stack)
- ngrok (for mobile device testing)

**One-Command Startup (Recommended):**

```bash
***REMOVED*** Start backend + monitoring stack together
scripts/backend/start-dev.sh

***REMOVED*** This automatically starts:
***REMOVED*** - PostgreSQL database
***REMOVED*** - Backend API server (port 8080)
***REMOVED*** - ngrok tunnel (for mobile testing)
***REMOVED*** - Observability stack (Grafana, Loki, Tempo, Mimir, Alloy)
```

**What You Get:**
- Backend API: http://localhost:8080
- ngrok Public URL: Displayed in terminal (update mobile app with this URL)
- Grafana Dashboards: http://localhost:3000
- OTLP Endpoints: localhost:4317 (gRPC), localhost:4318 (HTTP)
- Health checks and status for all services

**Options:**
```bash
***REMOVED*** Skip monitoring stack
scripts/backend/start-dev.sh --no-monitoring

***REMOVED*** Stop all services
scripts/backend/stop-dev.sh

***REMOVED*** Stop including monitoring
scripts/backend/stop-dev.sh --with-monitoring

***REMOVED*** View monitoring URLs and health status
scripts/monitoring/print-urls.sh
```

**Backend Configuration:**
1. Copy `.env.example` to `.env` in the `backend/` directory
2. Configure required environment variables
3. Run database migrations: `cd backend && npm run prisma:migrate`

See [docs/DEV_GUIDE.md](./docs/DEV_GUIDE.md) and [monitoring/README.md](./monitoring/README.md) for detailed setup instructions.

***REMOVED******REMOVED*** Building

***REMOVED******REMOVED******REMOVED*** Using Portable Build Script (Recommended)
```bash
./scripts/build.sh assembleDebug          ***REMOVED*** Build debug APK (auto-detects Java 17)
./scripts/build.sh assembleRelease        ***REMOVED*** Build release APK
./scripts/build.sh test                   ***REMOVED*** Run unit tests
./scripts/build.sh clean                  ***REMOVED*** Clean build artifacts
```

The `build.sh` script automatically finds Java 17 on your system across macOS, Linux, and Windows.

***REMOVED******REMOVED******REMOVED*** Using Gradle Directly
```bash
./gradlew assembleDebug           ***REMOVED*** Ensure Java 17 is active
./gradlew assembleRelease
./gradlew test                    ***REMOVED*** Run unit tests
./gradlew connectedAndroidTest    ***REMOVED*** Run instrumented tests (requires device)
```

***REMOVED******REMOVED******REMOVED*** Mobile testing via GitHub Actions artifact
- Each push to `main` builds a debug APK in the **Android Debug APK** workflow.
- In GitHub Actions, download the `scanium-app-debug-apk` artifact from the latest run.
- Unzip the archive and install `app-debug.apk` on your device (enable unknown sources if needed).

***REMOVED******REMOVED*** Project Structure

```
scanium/
├── androidApp/                ***REMOVED*** Compose UI + feature orchestration
│   ├── camera/                ***REMOVED*** CameraScreen, CameraXManager, DetectionOverlay
│   ├── items/                 ***REMOVED*** ItemsListScreen, ItemDetailDialog, ItemsViewModel
│   ├── ml/                    ***REMOVED*** ObjectDetectorClient, BarcodeScannerClient, PricingEngine
│   ├── navigation/            ***REMOVED*** ScaniumNavGraph + routes
│   └── ui/                    ***REMOVED*** Material 3 theme and shared components
├── android-platform-adapters/ ***REMOVED*** Bitmap/Rect adapters → ImageRef/NormalizedRect
├── android-camera-camerax/    ***REMOVED*** CameraX helpers
├── android-ml-mlkit/          ***REMOVED*** ML Kit plumbing
├── core-models/               ***REMOVED*** Portable models (ScannedItem, ImageRef, NormalizedRect)
├── core-tracking/             ***REMOVED*** Tracking math (ObjectTracker, AggregatedItem)
├── core-domainpack/, core-scan/, core-contracts/ ***REMOVED*** Shared contracts
├── backend/                   ***REMOVED*** Node.js API server
│   ├── src/                   ***REMOVED*** TypeScript source (routes, services, middleware)
│   ├── prisma/                ***REMOVED*** Database schema and migrations
│   ├── docker-compose.yml     ***REMOVED*** PostgreSQL container
│   └── .env                   ***REMOVED*** Environment configuration (gitignored)
├── monitoring/                ***REMOVED*** Observability stack (LGTM + Alloy)
│   ├── docker-compose.yml     ***REMOVED*** Grafana, Loki, Tempo, Mimir, Alloy
│   ├── grafana/               ***REMOVED*** Dashboards and datasource provisioning
│   ├── alloy/                 ***REMOVED*** OTLP routing configuration
│   ├── loki/, tempo/, mimir/  ***REMOVED*** Backend storage configs
│   └── data/                  ***REMOVED*** Persistent data volumes (gitignored)
├── scripts/                   ***REMOVED*** Build and development automation
│   ├── backend/               ***REMOVED*** Backend dev scripts (start-dev, stop-dev)
│   ├── monitoring/            ***REMOVED*** Monitoring scripts (start, stop, print-urls)
│   └── build.sh               ***REMOVED*** Java 17 auto-detection for Android builds
├── docs/                      ***REMOVED*** Project documentation
│   ├── ARCHITECTURE.md        ***REMOVED*** System architecture
│   ├── DEV_GUIDE.md           ***REMOVED*** Development workflow
│   └── CODEX_CONTEXT.md       ***REMOVED*** Agent quickmap
├── md/                        ***REMOVED*** Feature docs, fixes, testing guides
├── AGENTS.md
├── ROADMAP.md
└── README.md
```

***REMOVED******REMOVED*** Usage

1. **Launch the app** - Camera screen opens automatically
2. **Select scan mode** - Swipe to switch between:
   - **Object Detection**: Detect and price everyday objects
   - **Barcode**: Scan QR codes and barcodes
   - **Document**: Extract text from documents
3. **Point at objects** - Aim your camera at items you want to identify
4. **Capture**:
   - **Tap** the camera button to capture a single photo
   - **Long-press** to start continuous scanning
   - **Double-tap** to stop scanning
5. **View results** - Detected objects appear in two ways:
   - **Visual Overlay**: Real-time bounding boxes and labels shown on camera preview
   - **Items List**: Tap "View Items" to see all detected objects with details
   - In continuous scanning mode, each physical object appears only once (de-duplicated)
   - The tracker confirms objects instantly for responsive detection
6. **Manage items** - Tap "View Items" to see all detected objects
7. **Sell on eBay (Mock)**:
   - **Long-press** an item to enter selection mode
   - **Tap** to select multiple items
   - Tap **"Sell on eBay"** in the top bar
   - **Review and edit** listing drafts (title, price, condition)
   - Tap **"Post to eBay (Mock)"** to create listings
   - Watch **real-time status updates** (Posting → Listed/Failed)
   - Return to items list to see **status badges**
   - Tap **"View"** on listed items to open mock listing URL
8. **Delete items** - Tap the delete icon in the top bar to clear all items

***REMOVED******REMOVED*** Permissions

The app requires the following permission:
- **Camera** (`android.permission.CAMERA`) - For object detection and capture

***REMOVED******REMOVED*** Current Limitations

- **Mocked pricing data** - Prices are generated locally based on category
 - **Local-only persistence** - Items are stored on-device; history is captured but not surfaced in UI (no cloud sync/export yet)
- **ML Kit categories** - Object detection limited to 5 coarse categories (Fashion, Food, Home goods, Places, Plants)
- **No backend** - All processing is local

***REMOVED******REMOVED*** Test Coverage

The project includes comprehensive test coverage with **171 total tests**:

**Unit Tests:**
- **Tracking & Detection** (110 tests):
  - ObjectTracker (tracking and de-duplication logic)
  - ObjectCandidate (spatial matching algorithms)
  - TrackingPipelineIntegration (end-to-end scenarios)
  - DetectionResult (overlay data model validation)
  - ItemsViewModel (state management)
  - PricingEngine, ScannedItem, ItemCategory
- **Domain Pack System** (61 tests):
  - DomainPack (data model and helper methods)
  - LocalDomainPackRepository (JSON loading, caching, validation)
  - CategoryMapper (category conversion and validation)
  - BasicCategoryEngine (ML Kit label matching, priority handling)
  - DomainPackProvider (singleton initialization, thread safety)

**Instrumented Tests:**
- DetectionOverlay UI tests (bounding box rendering)
- ModeSwitcher UI tests
- ItemsViewModel integration tests

**Test Frameworks:**
JUnit 4, Robolectric (SDK 28), Truth assertions, MockK, Coroutines Test, Compose Testing, Kotlinx Serialization Test

***REMOVED******REMOVED*** Future Enhancements

***REMOVED******REMOVED******REMOVED*** Planned Features
- Real pricing API integration
- Historical price tracking and analytics
- Multi-currency support
- Share detected items
- Compare prices across retailers
- Color-based object matching for improved tracking

***REMOVED******REMOVED******REMOVED*** Technical Improvements
- Multi-module architecture
- Dependency injection (Hilt)
- Enhanced test coverage
- CI/CD pipeline
- Backend service integration
- Cloud-based ML models
- Adaptive tracking thresholds based on scene complexity

***REMOVED******REMOVED******REMOVED*** Recently Implemented ✅
- ✅ **eBay Selling Integration (Mock)**: Complete end-to-end marketplace flow with real scanning + mocked eBay
  - Multi-selection UI with long-press and tap gestures
  - Draft review screen with editable titles, prices, and conditions
  - High-quality image preparation (ListingImagePreparer) with resolution scaling and quality logging
  - Realistic Mock eBay API with configurable network delays (400-1200ms) and failure modes
  - Real-time listing status tracking (NOT_LISTED → LISTING_IN_PROGRESS → LISTED_ACTIVE/LISTING_FAILED)
  - Status badges with color coding in items list
  - "View listing" button to open mock URLs
  - Debug settings dialog for testing failure scenarios
  - Full ViewModel communication layer (ItemsViewModel ↔ ListingViewModel)
  - 4 new unit tests for selling components
- ✅ **Domain Pack Category System** (Track A): Config-driven fine-grained categorization with 23 categories and 10 attributes
- ✅ **Cross-Platform Build System**: Portable build.sh script and Java 17 toolchain for multi-machine development
- ✅ **UI Refinements**: Slim vertical slider, clean overlay text, minimal camera interface
- ✅ **Visual Detection Overlay**: Real-time bounding boxes and labels on camera preview
- ✅ **Object Tracking & De-duplication**: Multi-frame tracking with ML Kit integration
- ✅ **Barcode/QR Code Scanning**: Real-time barcode detection
- ✅ **Local Persistence & History**: Room-backed storage with full item change-log (not yet exposed in UI)
- ✅ **Document Text Recognition**: OCR for document scanning
- ✅ **Comprehensive Test Suite**: 175+ tests covering tracking, detection, domain pack, and selling systems
- ✅ **SINGLE_IMAGE_MODE Detection**: More accurate object detection for both tap and long-press
- ✅ **Scanium Branding**: Complete visual rebrand with new color scheme and identity

***REMOVED******REMOVED*** Documentation

All project documentation is organized in the `md/` folder by category:

***REMOVED******REMOVED******REMOVED*** Architecture & Design
- [Architecture Overview](./md/architecture/ARCHITECTURE.md) - Overall system architecture and design decisions

***REMOVED******REMOVED******REMOVED*** Features
- [Aggregation System](./md/features/AGGREGATION_SYSTEM.md) - Real-time item aggregation and similarity matching
- [Threshold Slider](./md/features/THRESHOLD_SLIDER.md) - Interactive threshold tuning UI component
- [Object Tracking](./md/features/TRACKING_IMPLEMENTATION.md) - Multi-frame object tracking system
- [eBay Selling Integration](./md/features/EBAY_SELLING_INTEGRATION.md) - Complete marketplace flow with mock eBay API

***REMOVED******REMOVED******REMOVED*** Testing
- [Test Suite](./md/testing/TEST_SUITE.md) - Comprehensive test documentation and coverage
- [Test Checklist](./md/testing/TEST_CHECKLIST.md) - Manual testing checklist and procedures

***REMOVED******REMOVED******REMOVED*** Bug Fixes & Solutions
- [ML Kit Zero Detections Fix](./md/fixes/ML_KIT_ZERO_DETECTIONS_FIX.md)
- [ML Kit Native Crash Fix](./md/fixes/ML_KIT_NATIVE_CRASH_FIX.md)
- [Bitmap Crash Fix](./md/fixes/BITMAP_CRASH_FIX.md)
- [Memory Crash Fix](./md/fixes/MEMORY_CRASH_FIX.md)
- [Session Deduplication Fix](./md/fixes/SESSION_DEDUPLICATION_FIX.md)
- [ML Kit Fix Summary](./md/fixes/ML_KIT_FIX_SUMMARY.md)

***REMOVED******REMOVED******REMOVED*** Improvements
- [ML Kit Improvements](./md/improvements/ML_KIT_IMPROVEMENTS.md) - Performance and accuracy enhancements

***REMOVED******REMOVED******REMOVED*** Debugging & Diagnostics
- [Debug Investigation Guide](./md/debugging/DEBUG_INVESTIGATION.md)
- [Diagnostic Logging Guide](./md/debugging/DIAGNOSTIC_LOG_GUIDE.md)

***REMOVED******REMOVED******REMOVED*** Other Documentation
- [Agents Guide](./AGENTS.md) - Information about AI agents used in development
- [Roadmap](./ROADMAP.md) - Future development plans and priorities

***REMOVED******REMOVED*** License

[Add your license here]

***REMOVED******REMOVED*** Contributing

[Add contributing guidelines here]

***REMOVED******REMOVED*** Contact

[Add contact information here]
