# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

## Project Overview

Scanium is a full-stack mobile application for real-time object detection, barcode scanning, and
document OCR. Primary detection happens on-device using Google ML Kit; enhanced classification uses
cloud APIs (Google Vision via backend proxy).

**Package**: `com.scanium.app` | **Language**: Kotlin (Android), TypeScript (Backend)

**Key Capabilities**: Object detection, barcode/QR scanning, document OCR, AI Assistant (Claude/OpenAI), voice control, export to CSV/ZIP

## Build Commands

### Android (requires Java 17)

```bash
./scripts/build.sh assembleDebug           # Auto-detects Java 17
./gradlew :androidApp:assembleDevDebug     # Dev flavor with Developer Options
./gradlew :androidApp:assembleProdDebug    # Production flavor
./gradlew test                             # All unit tests
./gradlew :androidApp:testDebugUnitTest    # Android unit tests only
./gradlew :shared:core-tracking:test --tests "ObjectTrackerTest"  # Single test class
./gradlew prePushJvmCheck                  # Fast JVM-only validation (no Android SDK needed)
./gradlew connectedAndroidTest             # Instrumented tests (device required)
./gradlew lint                             # Android Lint
./gradlew koverVerify                      # Code coverage (thresholds: shared ≥85%, androidApp ≥75%)
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
├── camera/                    # CameraX integration, detection overlay
├── items/                     # Item list, details, state management
├── ml/                        # ML Kit clients, pricing engine, vision extraction
├── assistant/                 # AI Assistant (Claude/OpenAI integration)
├── selling/                   # Marketplace integration (eBay, flavor-gated)
├── diagnostics/               # System health checks, Developer Options
└── telemetry/                 # OpenTelemetry OTLP export
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
- **Portable Models**: `core-models` and `core-tracking` have no Android dependencies (enforced by `checkPortableModules`)
- **Platform Adapters**: Convert at boundaries using `android-platform-adapters` (`Bitmap.toImageRefJpeg`, `Rect.toNormalizedRect`)
- **Config-driven Cloud**: Cloud classification activates only when `scanium.api.base.url` and `scanium.api.key` are set

## Testing

- **Unit tests**: `androidApp/src/test/` (JUnit4, Robolectric, Truth, MockK)
- **Shared module tests**: `shared/core-models/src/test/`, `shared/core-tracking/src/test/`
- **Instrumented tests**: `androidApp/src/androidTest/` (Compose UI)
- **Backend tests**: `backend/` (Vitest) - run with `cd backend && npm test`
- **Container-friendly**: Use `./gradlew prePushJvmCheck` when Android SDK unavailable
- **Golden tests**: `backend/src/modules/vision/routes.golden.test.ts` validates Vision extraction pipeline

## Build Flavors

| Flavor | App ID                 | Developer Options |
|--------|------------------------|-------------------|
| prod   | `com.scanium.app`      | Disabled          |
| dev    | `com.scanium.app.dev`  | Available         |
| beta   | `com.scanium.app.beta` | Disabled          |

## Key Files

**Android**:

- `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt` - Camera lifecycle, ViewPort alignment
- `androidApp/src/main/java/com/scanium/app/items/ItemsViewModel.kt` - State management
- `androidApp/src/main/java/com/scanium/app/items/state/ItemsStateManager.kt` - Item state, vision insights
- `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt` - ML Kit wrapper
- `androidApp/src/main/java/com/scanium/app/ml/VisionInsightsPrefiller.kt` - 3-layer vision pipeline
- `androidApp/src/main/java/com/scanium/app/assistant/` - AI Assistant integration
- `androidApp/src/main/java/com/scanium/app/navigation/NavGraph.kt` - Navigation routes

**Shared**:

- `shared/core-tracking/src/commonMain/.../ObjectTracker.kt` - Multi-frame tracking
- `shared/core-tracking/src/commonMain/.../ItemAggregator.kt` - Session deduplication
- `shared/core-tracking/src/commonMain/.../SpatialTemporalMergePolicy.kt` - Fallback dedup for ID churn

**Backend**:

- `backend/src/main.ts` - Fastify server entry
- `backend/src/modules/vision/routes.ts` - Vision API proxy, itemType derivation
- `backend/src/modules/enrich/pipeline.ts` - 3-stage enrichment pipeline
- `backend/prisma/schema.prisma` - Database schema

## Configuration

**Tracker** (in CameraXManager.kt):

- `minFramesToConfirm=1`, `minConfidence=0.2f` (permissive, aggregator filters quality)

**Aggregator** (in ItemsViewModel.kt):

- Uses `AggregationPresets.REALTIME` (threshold 0.55)

**Edge Gating** (in CameraXManager.kt):

- `EDGE_INSET_MARGIN_RATIO=0.10` (10% inset from edges to filter partial objects)

**Cloud classification** (in `local.properties`):

```properties
scanium.api.base.url=https://your-backend-url.com/api/v1
scanium.api.key=your-dev-api-key
```

When unset, cloud classifier is skipped and ML Kit on-device labels are used as fallback.

## NAS Operations

Always use `ssh nas` prefix for Docker commands targeting the monitoring stack:

```bash
ssh nas "docker compose -p scanium-monitoring restart"
ssh nas "docker ps -a"
```

The monitoring stack runs on NAS; only backend's PostgreSQL runs locally.

## Vision Pipeline

Three-layer extraction flow from camera scan to assistant context:

1. **Layer A (Local)**: ML Kit OCR + Android Palette - runs immediately (~100-200ms)
2. **Layer B (Cloud)**: POST `/v1/vision/insights` - Vision API via backend (~1-2s)
3. **Layer C (Enrichment)**: POST `/v1/items/enrich` - normalized attributes + draft title (~5-15s)

**Key distinction**: `category` = high-level classification (Cosmetics, Electronics); `itemType` = concrete sellable noun (Lip Balm, T-Shirt)

## Documentation

See `howto/` for canonical documentation:

- `howto/project/reference/AGENTS.md` - AI agent guidelines
- `howto/project/reference/DEV_GUIDE.md` - Development workflow
- `howto/project/reference/ARCHITECTURE.md` - Full system architecture
- `howto/app/reference/` - Android architecture, releases
- `howto/monitoring/` - Observability stack
