# Scanium Context for Gemini

This file provides essential context about the Scanium project to assist Gemini in understanding the
codebase, architecture, and development workflow.

## 1. Project Overview

**Scanium** is a camera-first Android application that uses on-device machine learning to detect
objects, classify them, and estimate prices in real-time. It features a mock eBay selling
integration.

* **Primary Goal:** Real-time object detection and price estimation with a seamless "scan-to-sell"
  workflow.
* **Architecture Strategy:** "Shared Brain" approach. Core logic (models, tracking, domain rules) is
  kept in portable Kotlin modules (`shared/*`) to support future iOS expansion, while UI and
  hardware integration remain platform-specific.
* **Privacy:** Privacy-first. Processing is primarily on-device (ML Kit). Cloud classification (
  Google Vision) is optional and configurable.

## 2. Technology Stack

### Mobile (Android)

* **Language:** Kotlin (Targeting Java 17).
* **UI Framework:** Jetpack Compose (Material 3).
* **Architecture:** MVVM (Model-View-ViewModel) with Unidirectional Data Flow (UDF).
* **Camera:** CameraX (Preview + ImageAnalysis).
* **ML:** Google ML Kit (Object Detection, Barcode Scanning, Text Recognition).
* **Concurrency:** Kotlin Coroutines & StateFlow.
* **Build System:** Gradle (Kotlin DSL).
* **Testing:** JUnit 4, Robolectric, Truth, MockK, Coroutines Test.

### Backend (`/backend`)

* **Runtime:** Node.js (v20+).
* **Language:** TypeScript.
* **Framework:** Fastify (HTTP server).
* **Database:** Prisma ORM + PostgreSQL 16.
* **External APIs:** Google Cloud Vision (via proxy), Anthropic Claude (AI Assistant), OpenAI (
  Vision insights).
* **Telemetry:** OpenTelemetry SDK (logs, traces, metrics).
* **Tools:** Docker, Vitest, ngrok (dev tunneling).

### Observability (`/monitoring`)

* **Stack:** LGTM (Loki, Grafana, Tempo, Mimir) + Grafana Alloy.
* **Router:** Grafana Alloy (OTLP receiver and data router).
* **Logs:** Loki (14-day retention).
* **Traces:** Tempo (7-day retention).
* **Metrics:** Mimir (Prometheus-compatible, 15-day retention).
* **Visualization:** Grafana dashboards with auto-provisioned datasources.

## 3. Project Structure

The project is a multi-module Gradle project with a distinct separation between platform code and
shared logic.

```
/
├── androidApp/                 # Main Android Application (UI, ViewModels, Integration)
│   ├── src/main/java/com/scanium/app/
│   │   ├── camera/             # CameraX & Overlay UI
│   │   ├── ml/                 # ML Kit Clients & Pricing Engine
│   │   ├── items/              # Item List & Details UI
│   │   ├── assistant/          # AI Assistant (Claude/OpenAI integration)
│   │   ├── classification/     # Classifier providers (Mock, NoOp)
│   │   ├── selling/            # eBay Integration (flavor-gated)
│   │   ├── ftue/               # First Time User Experience & Tours
│   │   ├── voice/              # Voice control & state machine
│   │   ├── audio/              # Sound effects & audio feedback
│   │   ├── telemetry/          # OpenTelemetry OTLP export
│   │   ├── diagnostics/        # System health & backend connectivity
│   │   ├── settings/           # Settings screens & preferences
│   │   ├── ui/                 # Shared UI components & theme
│   │   ├── navigation/         # Navigation graph
│   │   └── di/                 # Hilt dependency injection
├── shared/                     # Portable Kotlin Code (KMP-ready)
│   ├── core-models/            # Shared data models (ImageRef, NormalizedRect, ScannedItem)
│   ├── core-tracking/          # Pure logic for object tracking & aggregation
│   ├── core-export/            # Export models & mappers (CSV, ZIP)
│   └── test-utils/             # Shared test helpers
├── core-domainpack/            # Domain configuration & category mapping
├── android-camera-camerax/     # CameraX wrapper libraries
├── android-ml-mlkit/           # ML Kit wrapper libraries
├── android-platform-adapters/  # Adapters for Bitmap/Rect <-> Shared Models
├── backend/                    # Fastify Backend Service
└── monitoring/                 # LGTM Observability Stack (Grafana, Loki, Tempo, Mimir, Alloy)
```

## 4. Key Architectural Concepts

### The Detection Pipeline

1. **Capture:** CameraX produces frames. `ViewPort` alignment ensures the analysis matches the
   user's preview.
2. **Detection:** ML Kit detects objects.
3. **Filtering:** "Edge Gating" drops detections near the screen edges to prevent partial matches.
4. **Standardization:** Android `Rect`/`Bitmap` are converted to portable `NormalizedRect`/
   `ImageRef`.
5. **Tracking:** `ObjectTracker` (in `shared/core-tracking`) associates frames to objects spatially.
6. **Aggregation:** `ItemAggregator` stabilizes detections over time using "Spatial-Temporal
   Deduplication".
7. **Classification:** `ClassificationOrchestrator` chooses between Cloud (Google Vision) or
   On-Device fallback.
8. **Mapping:** `DomainPack` maps raw labels to user-friendly categories and prices.

### Modules & Dependencies

* **Rule:** `shared/*` modules must NEVER depend on Android APIs (`android.*`).
* **Rule:** `androidApp` integrates everything.
* **Rule:** Feature logic (like Pricing or Selling) should reside in distinct packages or modules.

## 5. Development Workflow

### Building & Running

* **Build Debug APK:** `./scripts/build.sh assembleDebug` (Auto-detects Java 17) or
  `./gradlew assembleDebug`
* **Run Tests:** `./gradlew test` (Runs JVM/Robolectric unit tests)
* **Run Instrumented Tests:** `./gradlew connectedAndroidTest` (Requires device/emulator)
* **Clean:** `./gradlew clean`

### Backend & Observability

* **Start Full Stack:** `scripts/backend/start-dev.sh` (backend + PostgreSQL + ngrok + monitoring)
* **Start Backend Only:** `scripts/backend/start-dev.sh --no-monitoring`
* **Stop Everything:** `scripts/backend/stop-dev.sh --with-monitoring`
* **Directory:** `cd backend`
* **Install:** `npm install`
* **Run Dev:** `npm run dev` (or use start-dev.sh script)
* **Test:** `npm test`
* **Database Migrations:** `npm run prisma:migrate`
* **View URLs:** `scripts/monitoring/print-urls.sh`

### Coding Conventions

* **Style:** Kotlin official style. 4-space indent.
* **Composables:** PascalCase, `@Composable` annotation.
* **State:** Expose `StateFlow` from ViewModels. Collect as state in UI.
* **Tests:** Name tests `whenCondition_thenResult`. Use `RobolectricTestRunner` for
  Android-dependent unit tests.

### AI Agent Workflow

* **Commit & Push:** When a task is complete and all tests pass, commit your changes and push to
  main. Use descriptive commit messages in imperative mood (e.g., "Add voice control feature", "Fix
  pricing engine bug").
* **Docker & NAS:** The monitoring stack runs on a NAS. Always prefix Docker commands with
  `ssh nas`. Examples:
    * `ssh nas "docker compose -p scanium-monitoring restart grafana"`
    * `ssh nas "docker ps -a"`
    * `ssh nas "ls -la /volume1/docker/scanium/"`
    * The backend's PostgreSQL container runs locally and doesn't need `ssh nas`.
* **Mac + NAS Invariant Workflow (Mandatory):**
    * **Repo alignment:** Check `git rev-parse HEAD` on Mac and NAS before any change. If they
      differ, stop and align both to the same commit. Never hot-fix runtime state.
    * **Runtime inventory:** Before diagnosing, collect NAS state: `docker ps`, `docker network ls`,
      and `docker inspect` for `scanium-backend`, `scanium-alloy`, `scanium-grafana` (include
      networks and compose labels).
    * **Network drift handling:** Treat drift as normal; do not permanently fix with
      `docker network connect`. Encode final fixes in compose files with explicit shared networks.
    * **Compose consistency:** Detect whether NAS uses `docker compose` or `docker-compose` and use
      only that binary from the correct working directory.
    * **Redeploy + verify:** All fixes must be committed and redeployed. Verify container health,
      DNS resolution, and telemetry/log/metrics flow after deploy.

## 6. Important Files

* `settings.gradle.kts`: Module definition.
* `androidApp/build.gradle.kts`: Main app dependencies and config.
* `docs/INDEX.md`: Documentation index and entry point.
* `docs/ARCHITECTURE.md`: Full system architecture (Android + backend + observability).
* `docs/CODEX_CONTEXT.md`: Agent quickmap for AI assistants.
* `docs/DEV_GUIDE.md`: Development workflow and setup guide.
* `docs/PRODUCT.md`: Current app behavior, screens, and user flows.
* `AGENTS.md`: Repository guidelines for AI agents.
* `GEMINI.md`: This file - Scanium context for Gemini.
* `README.md`: Project overview and quick start.
* `backend/package.json`: Backend dependencies and scripts.
* `monitoring/docker-compose.yml`: Observability stack configuration.
