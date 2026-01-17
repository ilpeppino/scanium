# Scanium

An Android application that uses on-device machine learning to detect objects through the camera and estimate their prices in real-time.

## Documentation

See [howto/](howto/) for the canonical documentation entry point. The `howto/` directory consolidates all operational documentation, runbooks, architecture guides, and deployment procedures.

## Testing

### With Android SDK (Workstation / CI)
- Run KMP unit tests: `./gradlew :shared:core-models:test :shared:core-tracking:test`
- Run Android unit tests: `./gradlew :androidApp:testDebugUnitTest`
- Run all tests: `./gradlew test`
- Run coverage: `./gradlew koverVerify`
- Instrumented tests (emulator/device required): `./gradlew :androidApp:connectedDebugAndroidTest`

### Container environments (Claude Code, Docker without Android SDK)
**⚠️ Note:** Full test suite requires Android SDK. Use JVM-only validation:
- JVM tests (shared modules): `./gradlew :shared:core-models:jvmTest :shared:core-tracking:jvmTest`
- Pre-push validation: `./gradlew prePushJvmCheck`
- Install pre-push hook: `./scripts/dev/install-hooks.sh`

See `howto/project/reference/DEV_GUIDE.md` for details.

## Overview

Scanium is a camera-first Android app that demonstrates object detection and price estimation using Google ML Kit. Point your camera at everyday items, and the app will identify them and provide estimated price ranges in EUR.

## Features

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
- **Export-First Sharing**: Export selected items to CSV/ZIP for spreadsheets, chat apps, or marketplaces (marketplace integrations temporarily disabled)
  - Multi-select items from detected list
  - Export CSV summaries and ZIP bundles with images
  - Share exports via standard Android share sheet
- **Privacy-First**: All processing happens on-device with no cloud calls
- **Debug Logging**: Comprehensive detection statistics and threshold tuning in debug builds
- **Developer Options (Debug)**: System Health diagnostics panel showing backend connectivity, network status, permissions, and device capabilities with auto-refresh and clipboard export

## Tech Stack

### Android Application
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

### Backend Services
- **Node.js + TypeScript** - Backend API server
- **Prisma** - Database ORM and migrations
- **PostgreSQL** - Primary database
- **Fastify** - HTTP server framework
- **ngrok** - Development tunneling for mobile device testing
- **Docker Compose** - Container orchestration for local development

### Observability Stack (LGTM + Alloy)
- **Grafana** - Visualization dashboards and alerting
- **Alloy** - OpenTelemetry (OTLP) receiver and router
- **Loki** - Log aggregation and storage
- **Tempo** - Distributed tracing backend
- **Mimir** - Prometheus-compatible metrics storage
- **Docker Compose** - Containerized observability infrastructure

## Architecture

Scanium is a **full-stack mobile application** consisting of three main components:

### System Overview

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

### Android Application

The Android app follows a **Simplified MVVM architecture** with feature-based package organization:

```
androidApp/src/main/java/com/scanium/app/
├── camera/          # Camera functionality, CameraX, mode switching
├── diagnostics/     # System health diagnostics (backend, network, permissions)
├── domain/          # Domain Pack system (config, repository, category engine)
├── items/           # Detected items management and display
├── ml/              # Object detection and pricing logic
├── tracking/        # Object tracking and de-duplication system
├── selling/         # eBay marketplace integration (mock)
│   ├── data/        # API, repository, marketplace service
│   ├── domain/      # Listing models, status, conditions
│   ├── ui/          # Marketplace screen, listing VM, debug settings
│   └── util/        # Image preparation, draft mapping
├── ui/settings/     # Settings and Developer Options screens
└── navigation/      # Navigation graph setup
```

### Backend Services

```
backend/
├── src/
│   ├── main.ts             # Fastify server entry point
│   ├── routes/             # API endpoints
│   ├── services/           # Business logic
│   ├── modules/            # Feature modules (classifier, assistant)
│   └── middleware/         # Auth, validation, error handling
├── prisma/
│   ├── schema.prisma       # Database schema
│   └── migrations/         # Version-controlled schema changes
└── docker-compose.yml      # PostgreSQL container
```

### Observability Infrastructure

```
monitoring/
├── docker-compose.yml      # LGTM stack + Alloy services
├── grafana/
│   ├── provisioning/       # Auto-configured datasources
│   └── dashboards/         # Pre-built visualization dashboards
├── alloy/alloy.hcl         # OTLP routing configuration
├── loki/loki.yaml          # Log aggregation config
├── tempo/tempo.yaml        # Distributed tracing config
└── mimir/mimir.yaml        # Metrics storage config
```

For detailed architecture documentation, see [howto/project/reference/ARCHITECTURE.md](./howto/project/reference/ARCHITECTURE.md).

### Key Architectural Decisions

- **Multi-module structure** - Android app plus shared core libraries for models, tracking, and domain packs
- **Hilt DI** - Dagger Hilt for dependency injection (`@HiltViewModel`, `@AndroidEntryPoint`)
- **Camera-first UX** - App opens directly to camera screen
- **On-device ML by default** - Privacy-focused with optional cloud classification when API keys configured
- **Reactive state management** - StateFlow for UI state updates
- **Backend integration** - Node.js backend with PostgreSQL for persistence and marketplace features

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- **JDK 17** (required) - See [SETUP.md](./SETUP.md) for installation instructions
- Android SDK with minimum API 24 (Android 7.0)
- Target API 34 (Android 14)

## Setup

For detailed cross-platform setup instructions (macOS, Linux, Windows), see [SETUP.md](./SETUP.md).

### Android Application Setup

**Quick Start:**

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd scanium
   ```

2. Ensure Java 17 is installed (see SETUP.md if needed)

3. Open the project in Android Studio, or build from command line:
   ```bash
   ./scripts/build.sh assembleDebug  # Auto-detects Java 17
   ```

4. Run the app on an emulator or physical device

### Backend Development Setup

**Prerequisites:**
- Node.js 20+
- Docker (for PostgreSQL and monitoring stack)
- ngrok (for mobile device testing)

**One-Command Startup (Recommended):**

```bash
# Start backend + monitoring stack together
scripts/backend/start-dev.sh

# This automatically starts:
# - PostgreSQL database
# - Backend API server (port 8080)
# - ngrok tunnel (for mobile testing)
# - Observability stack (Grafana, Loki, Tempo, Mimir, Alloy)
```

**What You Get:**
- Backend API: http://localhost:8080
- ngrok Public URL: Displayed in terminal (update mobile app with this URL)
- Grafana Dashboards: http://localhost:3000
- OTLP Endpoints: localhost:4317 (gRPC), localhost:4318 (HTTP)
- Health checks and status for all services

**Options:**
```bash
# Skip monitoring stack
scripts/backend/start-dev.sh --no-monitoring

# Stop all services
scripts/backend/stop-dev.sh

# Stop including monitoring
scripts/backend/stop-dev.sh --with-monitoring

# View monitoring URLs and health status
scripts/monitoring/print-urls.sh
```

**Backend Configuration:**
1. Copy `.env.example` to `.env` in the `backend/` directory
2. Configure required environment variables
3. Run database migrations: `cd backend && npm run prisma:migrate`

See [howto/project/reference/DEV_GUIDE.md](./howto/project/reference/DEV_GUIDE.md) and [howto/monitoring/README.md](./howto/monitoring/README.md) for detailed setup instructions.

## Building

### Using Portable Build Script (Recommended)
```bash
./scripts/build.sh assembleDebug          # Build debug APK (auto-detects Java 17)
./scripts/build.sh assembleRelease        # Build release APK
./scripts/build.sh test                   # Run unit tests
./scripts/build.sh clean                  # Clean build artifacts
```

The `build.sh` script automatically finds Java 17 on your system across macOS, Linux, and Windows.

### Using Gradle Directly
```bash
./gradlew assembleDebug           # Ensure Java 17 is active
./gradlew assembleRelease
./gradlew test                    # Run unit tests
./gradlew connectedAndroidTest    # Run instrumented tests (requires device)
```

### Mobile testing via GitHub Actions artifact
- Each push to `main` builds a debug APK in the **Android Debug APK** workflow.
- In GitHub Actions, download the `scanium-app-debug-apk` artifact from the latest run.
- Unzip the archive and install `app-debug.apk` on your device (enable unknown sources if needed).

## Project Structure

```
scanium/
├── androidApp/                # Compose UI + feature orchestration
│   ├── camera/                # CameraScreen, CameraXManager, DetectionOverlay
│   ├── items/                 # ItemsListScreen, ItemDetailDialog, ItemsViewModel
│   ├── ml/                    # ObjectDetectorClient, BarcodeScannerClient, PricingEngine
│   ├── navigation/            # ScaniumNavGraph + routes
│   └── ui/                    # Material 3 theme and shared components
├── android-platform-adapters/ # Bitmap/Rect adapters → ImageRef/NormalizedRect
├── android-camera-camerax/    # CameraX helpers
├── android-ml-mlkit/          # ML Kit plumbing
├── core-models/               # Portable models (ScannedItem, ImageRef, NormalizedRect)
├── core-tracking/             # Tracking math (ObjectTracker, AggregatedItem)
├── core-domainpack/, core-scan/, core-contracts/ # Shared contracts
├── backend/                   # Node.js API server
│   ├── src/                   # TypeScript source (routes, services, middleware)
│   ├── prisma/                # Database schema and migrations
│   ├── docker-compose.yml     # PostgreSQL container
│   └── .env                   # Environment configuration (gitignored)
├── monitoring/                # Observability stack (LGTM + Alloy)
│   ├── docker-compose.yml     # Grafana, Loki, Tempo, Mimir, Alloy
│   ├── grafana/               # Dashboards and datasource provisioning
│   ├── alloy/                 # OTLP routing configuration
│   ├── loki/, tempo/, mimir/  # Backend storage configs
│   └── data/                  # Persistent data volumes (gitignored)
├── scripts/                   # Build and development automation
│   ├── backend/               # Backend dev scripts (start-dev, stop-dev)
│   ├── monitoring/            # Monitoring scripts (start, stop, print-urls)
│   └── build.sh               # Java 17 auto-detection for Android builds
├── docs/                      # Project documentation
│   ├── ARCHITECTURE.md        # System architecture
│   ├── DEV_GUIDE.md           # Development workflow
│   └── CODEX_CONTEXT.md       # Agent quickmap
├── md/                        # Feature docs, fixes, testing guides
├── AGENTS.md
├── ROADMAP.md
└── README.md
```

## Usage

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
7. **Export items**:
   - **Long-press** an item to enter selection mode
   - **Tap** to select multiple items
   - Tap **"Export"** to open export options
   - Choose **CSV** for spreadsheets or **ZIP** for images + data
   - Share the export with chat apps or other tools
   - Marketplace integrations are **coming later** (currently disabled)
8. **Delete items** - Tap the delete icon in the top bar to clear all items

## Permissions

The app requires the following permission:
- **Camera** (`android.permission.CAMERA`) - For object detection and capture

## Current Limitations

- **Mocked pricing data** - Prices are generated locally based on category (API integration ready)
- **Local-only sync** - Items stored on-device; backend sync available but cloud sync not yet enabled
- **Marketplace disabled** - eBay integration implemented but temporarily disabled in UI (export-first flow active)

## Test Coverage

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

## Future Enhancements

### Planned Features
- Real pricing API integration
- Historical price tracking and analytics
- Multi-currency support
- Share detected items
- Compare prices across retailers
- Color-based object matching for improved tracking

### Technical Improvements
- iOS client using shared KMP modules
- Cloud-based ML models for enhanced classification
- Adaptive tracking thresholds based on scene complexity
- End-to-end telemetry from Android app to Grafana
- Production deployment configuration (Kubernetes/Cloud Run)

### Recently Implemented ✅
- ✅ **Hilt Dependency Injection**: Full DI setup with @HiltViewModel, @AndroidEntryPoint for testability and modularity
- ✅ **Backend Services**: Node.js + TypeScript + Prisma + PostgreSQL with LGTM observability stack
- ✅ **Developer Options with System Health**: Debug-only diagnostics panel showing backend connectivity, network status, permissions, device capabilities, and app configuration with auto-refresh and clipboard export
- ✅ **WCAG 2.1 Accessibility**: TalkBack support with proper semantics, 48dp touch targets, traversal order, and screen reader announcements
- ✅ **Export-First Sharing (CSV/ZIP)**: Export selected items for spreadsheets, chat apps, or marketplaces
  - Multi-selection UI with long-press and tap gestures
  - CSV summaries + ZIP bundles with images
  - Share sheet integration for quick handoff
  - Marketplace integrations are disabled while export-first UX ships
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

## Documentation

See **[howto/](howto/)** for the complete documentation entry point and directory guide.

### Quick Links
| Topic | Location |
|-------|----------|
| Architecture | [Camera Pipeline](./howto/app/reference/architecture-camera-pipeline.md), [Item Aggregation](./howto/app/reference/architecture-item-aggregation.md) |
| Releases | [Release Checklist](./howto/app/releases/RELEASE_CHECKLIST.md), [Release & Rollback](./howto/app/releases/RELEASE_AND_ROLLBACK.md) |
| Security | [Security Guidelines](./howto/infra/security/SECURITY.md), [NAS Security](./howto/infra/security/NAS_DEPLOYMENT_SECURITY_CHECKLIST.md) |
| Monitoring | [Grafana Access](./howto/monitoring/reference/GRAFANA_ACCESS_GUIDE.md), [Stack Changelog](./howto/monitoring/CHANGELOG.md) |
| KMP Migration | [Migration Map](./howto/MIGRATION_MAP.md) |

### Repository Guidelines
- [AGENTS.md](./howto/project/reference/AGENTS.md) - Guidelines for AI agents working on the project
- [GEMINI.md](./howto/project/reference/GEMINI.md) - Scanium context for Gemini AI

### Archive
Historical documentation from December 2025 is archived in `howto/archive/2025-12/`.

## License

[Add your license here]

## Contributing

[Add contributing guidelines here]

## Contact

[Add contact information here]
