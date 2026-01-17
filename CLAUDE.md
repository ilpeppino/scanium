***REMOVED*** CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

***REMOVED******REMOVED*** Project Overview

Scanium is a full-stack mobile application for real-time object detection, barcode scanning, and document OCR. Primary detection happens on-device using Google ML Kit; enhanced classification uses cloud APIs.

**Package**: `com.scanium.app` | **Language**: Kotlin (Android), TypeScript (Backend)

***REMOVED******REMOVED*** Build Commands

***REMOVED******REMOVED******REMOVED*** Android (requires Java 17)
```bash
./scripts/build.sh assembleDebug           ***REMOVED*** Auto-detects Java 17
./gradlew :androidApp:assembleDevDebug     ***REMOVED*** Dev flavor with Developer Options
./gradlew :androidApp:assembleProdDebug    ***REMOVED*** Production flavor
./gradlew test                             ***REMOVED*** All unit tests
./gradlew prePushJvmCheck                  ***REMOVED*** Fast JVM-only validation (no Android SDK needed)
./gradlew connectedAndroidTest             ***REMOVED*** Instrumented tests (device required)
./gradlew lint                             ***REMOVED*** Android Lint
./gradlew koverVerify                      ***REMOVED*** Code coverage
```

***REMOVED******REMOVED******REMOVED*** Backend (Node.js 20+)
```bash
scripts/backend/start-dev.sh               ***REMOVED*** Start PostgreSQL + backend + ngrok + monitoring
scripts/backend/start-dev.sh --no-monitoring  ***REMOVED*** Backend only
scripts/backend/stop-dev.sh                ***REMOVED*** Stop all services
cd backend && npm run dev                  ***REMOVED*** Manual backend start
cd backend && npm test                     ***REMOVED*** Backend tests
cd backend && npm run prisma:migrate       ***REMOVED*** Database migrations
cd backend && npm run typecheck            ***REMOVED*** TypeScript validation
```

***REMOVED******REMOVED*** Architecture

***REMOVED******REMOVED******REMOVED*** Module Structure
```
androidApp/                    ***REMOVED*** Android app (Compose UI, CameraX, ML Kit, Hilt DI)
shared/
├── core-models/               ***REMOVED*** KMP-ready portable models (ImageRef, NormalizedRect, ScannedItem)
├── core-tracking/             ***REMOVED*** Platform-free tracking (ObjectTracker, ItemAggregator)
├── core-export/               ***REMOVED*** Export models (CSV, ZIP)
core-domainpack/               ***REMOVED*** Domain Pack system (23 categories, 10 attributes)
android-platform-adapters/     ***REMOVED*** Bitmap↔ImageRef, Rect↔NormalizedRect conversions
backend/                       ***REMOVED*** Fastify + TypeScript + Prisma + PostgreSQL
monitoring/                    ***REMOVED*** LGTM observability stack (Grafana, Loki, Tempo, Mimir, Alloy)
```

***REMOVED******REMOVED******REMOVED*** Data Flow
```
CameraX → ML Kit Detection → ObjectTracker (frame dedup) → ItemAggregator (session dedup)
    → ItemsViewModel (StateFlow) → Compose UI
    → Optional: Cloud enrichment via /v1/items/enrich
```

***REMOVED******REMOVED******REMOVED*** Key Patterns
- **MVVM + Hilt DI**: `@HiltViewModel`, `@AndroidEntryPoint` throughout
- **StateFlow**: UI observes via `collectAsState()`
- **Portable Models**: `core-models` and `core-tracking` have no Android dependencies
- **Platform Adapters**: Convert at boundaries using `android-platform-adapters`

***REMOVED******REMOVED*** Testing

- **Unit tests**: `androidApp/src/test/` (JUnit4, Robolectric, Truth, MockK)
- **Instrumented tests**: `androidApp/src/androidTest/` (Compose UI)
- **Backend tests**: `backend/` (Vitest)
- **Container-friendly**: Use `./gradlew prePushJvmCheck` when Android SDK unavailable

***REMOVED******REMOVED*** Build Flavors

| Flavor | App ID | Developer Options |
|--------|--------|-------------------|
| prod | `com.scanium.app` | Disabled |
| dev | `com.scanium.app.dev` | Available |
| beta | `com.scanium.app.beta` | Disabled |

***REMOVED******REMOVED*** Key Files

**Android**:
- `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt` - Camera lifecycle
- `androidApp/src/main/java/com/scanium/app/items/ItemsViewModel.kt` - State management
- `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt` - ML Kit wrapper

**Shared**:
- `shared/core-tracking/src/commonMain/.../ObjectTracker.kt` - Multi-frame tracking
- `shared/core-tracking/src/commonMain/.../ItemAggregator.kt` - Session deduplication

**Backend**:
- `backend/src/main.ts` - Fastify server entry
- `backend/prisma/schema.prisma` - Database schema

***REMOVED******REMOVED*** Configuration

**Tracker** (in CameraXManager.kt):
- `minFramesToConfirm=1`, `minConfidence=0.2f` (permissive, aggregator filters quality)

**Aggregator** (in ItemsViewModel.kt):
- Uses `AggregationPresets.REALTIME` (threshold 0.55)

**Cloud classification**: Set `scanium.api.base.url` and `scanium.api.key` in `local.properties`

***REMOVED******REMOVED*** NAS Operations

Always use `ssh nas` prefix for Docker commands targeting the monitoring stack:
```bash
ssh nas "docker compose -p scanium-monitoring restart"
ssh nas "docker ps -a"
```
The monitoring stack runs on NAS; only backend's PostgreSQL runs locally.

***REMOVED******REMOVED*** Documentation

See `howto/` for canonical documentation:
- `howto/project/reference/AGENTS.md` - AI agent guidelines
- `howto/project/reference/DEV_GUIDE.md` - Development workflow
- `howto/app/reference/` - Android architecture, releases
- `howto/monitoring/` - Observability stack
