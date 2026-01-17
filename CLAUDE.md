# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Scanium is a full-stack mobile application for real-time object detection, barcode scanning, and document OCR. Primary detection happens on-device using Google ML Kit; enhanced classification uses cloud APIs.

**Package**: `com.scanium.app` | **Language**: Kotlin (Android), TypeScript (Backend)

## Build Commands

### Android (requires Java 17)
```bash
./scripts/build.sh assembleDebug           # Auto-detects Java 17
./gradlew :androidApp:assembleDevDebug     # Dev flavor with Developer Options
./gradlew :androidApp:assembleProdDebug    # Production flavor
./gradlew test                             # All unit tests
./gradlew prePushJvmCheck                  # Fast JVM-only validation (no Android SDK needed)
./gradlew connectedAndroidTest             # Instrumented tests (device required)
./gradlew lint                             # Android Lint
./gradlew koverVerify                      # Code coverage
```

### Backend (Node.js 20+)
```bash
scripts/backend/start-dev.sh               # Start PostgreSQL + backend + ngrok + monitoring
scripts/backend/start-dev.sh --no-monitoring  # Backend only
scripts/backend/stop-dev.sh                # Stop all services
cd backend && npm run dev                  # Manual backend start
cd backend && npm test                     # Backend tests
cd backend && npm run prisma:migrate       # Database migrations
cd backend && npm run typecheck            # TypeScript validation
```

## Architecture

### Module Structure
```
androidApp/                    # Android app (Compose UI, CameraX, ML Kit, Hilt DI)
shared/
├── core-models/               # KMP-ready portable models (ImageRef, NormalizedRect, ScannedItem)
├── core-tracking/             # Platform-free tracking (ObjectTracker, ItemAggregator)
├── core-export/               # Export models (CSV, ZIP)
core-domainpack/               # Domain Pack system (23 categories, 10 attributes)
android-platform-adapters/     # Bitmap↔ImageRef, Rect↔NormalizedRect conversions
backend/                       # Fastify + TypeScript + Prisma + PostgreSQL
monitoring/                    # LGTM observability stack (Grafana, Loki, Tempo, Mimir, Alloy)
```

### Data Flow
```
CameraX → ML Kit Detection → ObjectTracker (frame dedup) → ItemAggregator (session dedup)
    → ItemsViewModel (StateFlow) → Compose UI
    → Optional: Cloud enrichment via /v1/items/enrich
```

### Key Patterns
- **MVVM + Hilt DI**: `@HiltViewModel`, `@AndroidEntryPoint` throughout
- **StateFlow**: UI observes via `collectAsState()`
- **Portable Models**: `core-models` and `core-tracking` have no Android dependencies
- **Platform Adapters**: Convert at boundaries using `android-platform-adapters`

## Testing

- **Unit tests**: `androidApp/src/test/` (JUnit4, Robolectric, Truth, MockK)
- **Instrumented tests**: `androidApp/src/androidTest/` (Compose UI)
- **Backend tests**: `backend/` (Vitest)
- **Container-friendly**: Use `./gradlew prePushJvmCheck` when Android SDK unavailable

## Build Flavors

| Flavor | App ID | Developer Options |
|--------|--------|-------------------|
| prod | `com.scanium.app` | Disabled |
| dev | `com.scanium.app.dev` | Available |
| beta | `com.scanium.app.beta` | Disabled |

## Key Files

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

## Configuration

**Tracker** (in CameraXManager.kt):
- `minFramesToConfirm=1`, `minConfidence=0.2f` (permissive, aggregator filters quality)

**Aggregator** (in ItemsViewModel.kt):
- Uses `AggregationPresets.REALTIME` (threshold 0.55)

**Cloud classification**: Set `scanium.api.base.url` and `scanium.api.key` in `local.properties`

## NAS Operations

Always use `ssh nas` prefix for Docker commands targeting the monitoring stack:
```bash
ssh nas "docker compose -p scanium-monitoring restart"
ssh nas "docker ps -a"
```
The monitoring stack runs on NAS; only backend's PostgreSQL runs locally.

## Documentation

See `howto/` for canonical documentation:
- `howto/project/reference/AGENTS.md` - AI agent guidelines
- `howto/project/reference/DEV_GUIDE.md` - Development workflow
- `howto/app/reference/` - Android architecture, releases
- `howto/monitoring/` - Observability stack
