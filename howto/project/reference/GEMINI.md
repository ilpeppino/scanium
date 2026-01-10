***REMOVED*** Scanium Context for Gemini

This file provides essential context about the Scanium project to assist Gemini in understanding the codebase, architecture, and development workflow.

***REMOVED******REMOVED*** 1. Project Overview

**Scanium** is a camera-first Android application that uses on-device machine learning to detect objects, classify them, and estimate prices in real-time. It features a mock eBay selling integration.

*   **Primary Goal:** Real-time object detection and price estimation with a seamless "scan-to-sell" workflow.
*   **Architecture Strategy:** "Shared Brain" approach. Core logic (models, tracking, domain rules) is kept in portable Kotlin modules (`shared/*`) to support future iOS expansion, while UI and hardware integration remain platform-specific.
*   **Privacy:** Privacy-first. Processing is primarily on-device (ML Kit). Cloud classification (Google Vision) is optional and configurable.

***REMOVED******REMOVED*** 2. Technology Stack

***REMOVED******REMOVED******REMOVED*** Mobile (Android)
*   **Language:** Kotlin (Targeting Java 17).
*   **UI Framework:** Jetpack Compose (Material 3).
*   **Architecture:** MVVM (Model-View-ViewModel) with Unidirectional Data Flow (UDF).
*   **Camera:** CameraX (Preview + ImageAnalysis).
*   **ML:** Google ML Kit (Object Detection, Barcode Scanning, Text Recognition).
*   **Concurrency:** Kotlin Coroutines & StateFlow.
*   **Build System:** Gradle (Kotlin DSL).
*   **Testing:** JUnit 4, Robolectric, Truth, MockK, Coroutines Test.

***REMOVED******REMOVED******REMOVED*** Backend (`/backend`)
*   **Runtime:** Node.js (v20+).
*   **Language:** TypeScript.
*   **Framework:** Fastify (HTTP server).
*   **Database:** Prisma ORM + PostgreSQL 16.
*   **External APIs:** Google Cloud Vision (via proxy), Anthropic Claude (AI Assistant), OpenAI (Vision insights).
*   **Telemetry:** OpenTelemetry SDK (logs, traces, metrics).
*   **Tools:** Docker, Vitest, ngrok (dev tunneling).

***REMOVED******REMOVED******REMOVED*** Observability (`/monitoring`)
*   **Stack:** LGTM (Loki, Grafana, Tempo, Mimir) + Grafana Alloy.
*   **Router:** Grafana Alloy (OTLP receiver and data router).
*   **Logs:** Loki (14-day retention).
*   **Traces:** Tempo (7-day retention).
*   **Metrics:** Mimir (Prometheus-compatible, 15-day retention).
*   **Visualization:** Grafana dashboards with auto-provisioned datasources.

***REMOVED******REMOVED*** 3. Project Structure

The project is a multi-module Gradle project with a distinct separation between platform code and shared logic.

```
/
├── androidApp/                 ***REMOVED*** Main Android Application (UI, ViewModels, Integration)
│   ├── src/main/java/com/scanium/app/
│   │   ├── camera/             ***REMOVED*** CameraX & Overlay UI
│   │   ├── ml/                 ***REMOVED*** ML Kit Clients & Pricing Engine
│   │   ├── items/              ***REMOVED*** Item List & Details UI
│   │   ├── assistant/          ***REMOVED*** AI Assistant (Claude/OpenAI integration)
│   │   ├── classification/     ***REMOVED*** Classifier providers (Mock, NoOp)
│   │   ├── selling/            ***REMOVED*** eBay Integration (flavor-gated)
│   │   ├── ftue/               ***REMOVED*** First Time User Experience & Tours
│   │   ├── voice/              ***REMOVED*** Voice control & state machine
│   │   ├── audio/              ***REMOVED*** Sound effects & audio feedback
│   │   ├── telemetry/          ***REMOVED*** OpenTelemetry OTLP export
│   │   ├── diagnostics/        ***REMOVED*** System health & backend connectivity
│   │   ├── settings/           ***REMOVED*** Settings screens & preferences
│   │   ├── ui/                 ***REMOVED*** Shared UI components & theme
│   │   ├── navigation/         ***REMOVED*** Navigation graph
│   │   └── di/                 ***REMOVED*** Hilt dependency injection
├── shared/                     ***REMOVED*** Portable Kotlin Code (KMP-ready)
│   ├── core-models/            ***REMOVED*** Shared data models (ImageRef, NormalizedRect, ScannedItem)
│   ├── core-tracking/          ***REMOVED*** Pure logic for object tracking & aggregation
│   ├── core-export/            ***REMOVED*** Export models & mappers (CSV, ZIP)
│   └── test-utils/             ***REMOVED*** Shared test helpers
├── core-domainpack/            ***REMOVED*** Domain configuration & category mapping
├── android-camera-camerax/     ***REMOVED*** CameraX wrapper libraries
├── android-ml-mlkit/           ***REMOVED*** ML Kit wrapper libraries
├── android-platform-adapters/  ***REMOVED*** Adapters for Bitmap/Rect <-> Shared Models
├── backend/                    ***REMOVED*** Fastify Backend Service
└── monitoring/                 ***REMOVED*** LGTM Observability Stack (Grafana, Loki, Tempo, Mimir, Alloy)
```

***REMOVED******REMOVED*** 4. Key Architectural Concepts

***REMOVED******REMOVED******REMOVED*** The Detection Pipeline
1.  **Capture:** CameraX produces frames. `ViewPort` alignment ensures the analysis matches the user's preview.
2.  **Detection:** ML Kit detects objects.
3.  **Filtering:** "Edge Gating" drops detections near the screen edges to prevent partial matches.
4.  **Standardization:** Android `Rect`/`Bitmap` are converted to portable `NormalizedRect`/`ImageRef`.
5.  **Tracking:** `ObjectTracker` (in `shared/core-tracking`) associates frames to objects spatially.
6.  **Aggregation:** `ItemAggregator` stabilizes detections over time using "Spatial-Temporal Deduplication".
7.  **Classification:** `ClassificationOrchestrator` chooses between Cloud (Google Vision) or On-Device fallback.
8.  **Mapping:** `DomainPack` maps raw labels to user-friendly categories and prices.

***REMOVED******REMOVED******REMOVED*** Modules & Dependencies
*   **Rule:** `shared/*` modules must NEVER depend on Android APIs (`android.*`).
*   **Rule:** `androidApp` integrates everything.
*   **Rule:** Feature logic (like Pricing or Selling) should reside in distinct packages or modules.

***REMOVED******REMOVED*** 5. Development Workflow

***REMOVED******REMOVED******REMOVED*** Building & Running
*   **Build Debug APK:** `./scripts/build.sh assembleDebug` (Auto-detects Java 17) or `./gradlew assembleDebug`
*   **Run Tests:** `./gradlew test` (Runs JVM/Robolectric unit tests)
*   **Run Instrumented Tests:** `./gradlew connectedAndroidTest` (Requires device/emulator)
*   **Clean:** `./gradlew clean`

***REMOVED******REMOVED******REMOVED*** Backend & Observability
*   **Start Full Stack:** `scripts/backend/start-dev.sh` (backend + PostgreSQL + ngrok + monitoring)
*   **Start Backend Only:** `scripts/backend/start-dev.sh --no-monitoring`
*   **Stop Everything:** `scripts/backend/stop-dev.sh --with-monitoring`
*   **Directory:** `cd backend`
*   **Install:** `npm install`
*   **Run Dev:** `npm run dev` (or use start-dev.sh script)
*   **Test:** `npm test`
*   **Database Migrations:** `npm run prisma:migrate`
*   **View URLs:** `scripts/monitoring/print-urls.sh`

***REMOVED******REMOVED******REMOVED*** Coding Conventions
*   **Style:** Kotlin official style. 4-space indent.
*   **Composables:** PascalCase, `@Composable` annotation.
*   **State:** Expose `StateFlow` from ViewModels. Collect as state in UI.
*   **Tests:** Name tests `whenCondition_thenResult`. Use `RobolectricTestRunner` for Android-dependent unit tests.

***REMOVED******REMOVED******REMOVED*** AI Agent Workflow
*   **Commit & Push:** When a task is complete and all tests pass, commit your changes and push to main. Use descriptive commit messages in imperative mood (e.g., "Add voice control feature", "Fix pricing engine bug").
*   **Docker & NAS:** The monitoring stack runs on a NAS. Always prefix Docker commands with `ssh nas`. Examples:
    *   `ssh nas "docker compose -p scanium-monitoring restart grafana"`
    *   `ssh nas "docker ps -a"`
    *   `ssh nas "ls -la /volume1/docker/scanium/"`
    *   The backend's PostgreSQL container runs locally and doesn't need `ssh nas`.

***REMOVED******REMOVED*** 6. Important Files
*   `settings.gradle.kts`: Module definition.
*   `androidApp/build.gradle.kts`: Main app dependencies and config.
*   `docs/INDEX.md`: Documentation index and entry point.
*   `docs/ARCHITECTURE.md`: Full system architecture (Android + backend + observability).
*   `docs/CODEX_CONTEXT.md`: Agent quickmap for AI assistants.
*   `docs/DEV_GUIDE.md`: Development workflow and setup guide.
*   `docs/PRODUCT.md`: Current app behavior, screens, and user flows.
*   `AGENTS.md`: Repository guidelines for AI agents.
*   `GEMINI.md`: This file - Scanium context for Gemini.
*   `README.md`: Project overview and quick start.
*   `backend/package.json`: Backend dependencies and scripts.
*   `monitoring/docker-compose.yml`: Observability stack configuration.
