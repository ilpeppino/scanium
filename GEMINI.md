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
*   **Framework:** Fastify.
*   **Database:** Prisma ORM.
*   **External APIs:** Google Cloud Vision (via proxy).
*   **Tools:** Docker, Vitest.

***REMOVED******REMOVED*** 3. Project Structure

The project is a multi-module Gradle project with a distinct separation between platform code and shared logic.

```
/
├── androidApp/                 ***REMOVED*** Main Android Application (UI, ViewModels, Integration)
│   ├── src/main/java/com/scanium/app/
│   │   ├── camera/             ***REMOVED*** CameraX & Overlay UI
│   │   ├── ml/                 ***REMOVED*** ML Kit Clients & Pricing Engine
│   │   ├── items/              ***REMOVED*** Item List & Details UI
│   │   ├── tracking/           ***REMOVED*** Tracking & Deduplication Logic
│   │   └── selling/            ***REMOVED*** Mock eBay Integration
├── shared/                     ***REMOVED*** Portable Kotlin Code (KMP-ready)
│   ├── core-models/            ***REMOVED*** Shared data models (ImageRef, NormalizedRect, ScannedItem)
│   ├── core-tracking/          ***REMOVED*** Pure logic for object tracking & aggregation
│   └── test-utils/             ***REMOVED*** Shared test helpers
├── core-domainpack/            ***REMOVED*** Domain configuration & category mapping
├── android-camera-camerax/     ***REMOVED*** CameraX wrapper libraries
├── android-ml-mlkit/           ***REMOVED*** ML Kit wrapper libraries
├── android-platform-adapters/  ***REMOVED*** Adapters for Bitmap/Rect <-> Shared Models
└── backend/                    ***REMOVED*** Node.js Backend Service
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

***REMOVED******REMOVED******REMOVED*** Backend
*   **Directory:** `cd backend`
*   **Install:** `npm install`
*   **Run Dev:** `npm run dev`
*   **Test:** `npm test`

***REMOVED******REMOVED******REMOVED*** Coding Conventions
*   **Style:** Kotlin official style. 4-space indent.
*   **Composables:** PascalCase, `@Composable` annotation.
*   **State:** Expose `StateFlow` from ViewModels. Collect as state in UI.
*   **Tests:** Name tests `whenCondition_thenResult`. Use `RobolectricTestRunner` for Android-dependent unit tests.

***REMOVED******REMOVED*** 6. Important Files
*   `settings.gradle.kts`: Module definition.
*   `androidApp/build.gradle.kts`: Main app dependencies and config.
*   `docs/ARCHITECTURE.md`: Detailed architectural decisions.
*   `docs/CODEX_CONTEXT.md`: Context about AI agent working on the project
*   `AGENTS.md`: Context about AI agents working on the project.
*   `backend/package.json`: Backend dependencies and scripts.
