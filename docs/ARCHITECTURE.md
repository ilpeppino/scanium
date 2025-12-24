# Architecture

Single source of truth for how Scanium is structured and how we evolve it. Scanium is a **full-stack mobile application** with Android client, backend services, and observability infrastructure.

---

## Full System Architecture

### System Overview

```
┌───────────────────────────────────────────────────────────────────────┐
│                       Android Application                             │
│  • Kotlin + Jetpack Compose + Material 3                             │
│  • CameraX + ML Kit (on-device object detection)                     │
│  • MVVM architecture with StateFlow                                   │
│  • Multi-module: androidApp, shared libs, platform adapters          │
└────────────────────┬──────────────────────────────────────────────────┘
                     │
                     │ HTTPS API calls (ngrok in dev, TLS in prod)
                     │ OTLP telemetry export (logs, traces, metrics)
                     ▼
┌───────────────────────────────────────────────────────────────────────┐
│                     Backend API Server                                │
│  • Node.js 20 + TypeScript + Express.js                              │
│  • Prisma ORM + PostgreSQL database                                  │
│  • REST API endpoints for item sync, auth, marketplace integration   │
│  • OpenTelemetry instrumentation                                      │
└────────────────────┬──────────────────────────────────────────────────┘
                     │
                     │ OpenTelemetry Protocol (OTLP)
                     ▼
┌───────────────────────────────────────────────────────────────────────┐
│              Observability Stack (LGTM + Alloy)                       │
│  ┌──────────┐   ┌──────┐   ┌───────┐   ┌───────┐   ┌───────┐      │
│  │  Alloy   │──>│ Loki │   │ Tempo │   │ Mimir │   │Grafana│      │
│  │ (Router) │   │(Logs)│   │(Trace)│   │(Metric│   │ (Viz) │      │
│  └──────────┘   └──────┘   └───────┘   └───────┘   └───────┘      │
│  OTLP receiver   14d logs   7d traces   15d metrics  Dashboards      │
└───────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Technology | Purpose | Ports |
|-----------|-----------|---------|-------|
| **Android App** | Kotlin, Compose, ML Kit | Camera UI, object detection, local tracking | N/A (mobile) |
| **Backend API** | Node.js, TypeScript, Express | REST API, business logic, persistence | 8080 (HTTP) |
| **PostgreSQL** | PostgreSQL 16 (Docker) | Primary database | 5432 |
| **ngrok** | ngrok tunnel | Dev-only: Expose localhost to mobile devices | Dynamic |
| **Grafana** | Grafana 10.3.1 (Docker) | Visualization, dashboards, alerting | 3000 (HTTP) |
| **Alloy** | Grafana Alloy 1.0.0 | OTLP receiver, telemetry router | 4317 (gRPC), 4318 (HTTP) |
| **Loki** | Loki 2.9.3 (Docker) | Log aggregation and storage | 3100 (internal) |
| **Tempo** | Tempo 2.3.1 (Docker) | Distributed tracing backend | 3200 (internal) |
| **Mimir** | Mimir 2.11.0 (Docker) | Prometheus-compatible metrics | 9009 (internal) |

### Data Flow

**Mobile App → Backend:**
1. User scans object with camera (ML Kit on-device)
2. App tracks objects, confirms stable detections
3. App sends confirmed items to backend API (POST /items)
4. Backend validates, persists to PostgreSQL
5. Backend returns item ID, marketplace listing status

**Telemetry Flow:**
1. Backend exports OTLP data (logs, traces, metrics)
2. Alloy receives on ports 4317/4318, batches, routes
3. Loki stores logs (14-day retention)
4. Tempo stores traces (7-day retention)
5. Mimir stores metrics (15-day retention)
6. Grafana queries all backends for unified dashboards

---

## Android Application Architecture (Current State)
- **Build/tooling:** Java 17 toolchain; AGP 8.5.0; Kotlin 2.0.0 + Compose compiler 2.0.0; Compose BOM 2024.05.00 in `androidApp`; KSP 2.0.0-1.0.24. `./gradlew assembleDebug` is the main gate; SBOM + OWASP checks in `androidApp`.
- **Modules:**
  - Platform UI: `androidApp/` (Compose, navigation, view models).
  - Platform scanning: `android-camera-camerax` (CameraX), `android-ml-mlkit` (ML Kit analyzers), `android-platform-adapters` (Bitmap/Rect adapters).
  - Shared brain: `shared/core-models` (ImageRef, NormalizedRect, RawDetection, DetectionResult, ItemCategory + classification/config contracts), `shared/core-tracking` (ObjectTracker, math).
  - Domain taxonomy: `core-domainpack` (DomainPackRepository, BasicCategoryEngine, JSON config).
  - Shell namespaces: `core-contracts`, `core-scan`, `shared:test-utils` (test helpers for shared modules).
  - Android wrappers: `core-models`, `core-tracking` (typealiases to shared KMP).
- **Pipeline today:** CameraX (with WYSIWYG ViewPort alignment) → ML Kit detection → geometry-based filtering (edge gating) → adapters to `RawDetection` → `ObjectTracker` + `ItemAggregator` (with spatial-temporal merge fallback) → `ClassificationOrchestrator` (cloud/offline paths) → UI state in view models. Cloud classifier is config-driven via `BuildConfig`/`local.properties`; on-device labels act as fallback when unset.
  - **WYSIWYG Viewport (2024-12):** Preview + ImageAnalysis bound via `UseCaseGroup` with shared `ViewPort` ensures ML analysis sees only what user sees in Preview; eliminates off-screen phantom detections.
  - **Edge Gating (2024-12):** Geometry-based filtering using `ImageProxy.cropRect` + configurable inset margin (default 10%) drops partial/cut-off objects at screen edges; zero per-frame bitmap cropping.
  - **Spatial-Temporal Dedupe (2024-12):** Lightweight fallback merge policy in `shared/core-tracking` handles tracker ID churn via IoU/distance matching within time window (default 800ms); minimal memory footprint.

---

## Target Architecture (layers)
- **Presentation (platform-specific):** Compose UI (Android), future SwiftUI (iOS). Pure UI + state wiring only.
- **Platform Scanning Layer:** Camera + on-device detectors; emits portable `RawDetection` + thumbnails. Android = CameraX/ML Kit; iOS (future) = AVFoundation/Apple Vision.
- **Shared Brain (portable/KMP-ready):** Models, tracking/aggregation, classification/config contracts, domain mapping. No Android types allowed.
- **Integration:** `androidApp` wires platform scanning to shared brain and domain pack; iOS will mirror the same contracts later.

Mermaid (layered view):
```mermaid
flowchart TD
    UI[Presentation: Compose/SwiftUI] --> VM[ViewModels]
    VM --> Platform[Platform Scanning Layer<br/>CameraX+ML Kit / AVFoundation+Vision]
    Platform --> Adapters[Platform Adapters<br/>Bitmap/Rect -> ImageRef/NormalizedRect]
    Adapters --> Tracking[Shared Tracking & Aggregation]
    Tracking --> Classify[Classification Orchestrator]
    Classify --> Cloud[Cloud Classifier (backend proxy -> Google Vision)]
    Classify --> Fallback[On-device labels (fallback)]
    Classify --> Domain[Domain Pack Mapping]
    Domain --> VM
```

---

## Data Flow (stable items only for cloud)
1. Camera frame (with ViewPort-aligned cropRect) → ML Kit detector → **geometry filtering** (drop detections outside visible viewport + edge inset) → `RawDetection` (normalized bbox, coarse label, thumbnail).
2. `ObjectTracker` processes frame-level detections (spatial matching + ID tracking); `ItemAggregator` merges via similarity scoring + **spatial-temporal fallback** (handles ID churn); only **stable items** (confirmed + thumbnail) are eligible for cloud upload.
3. `ClassificationOrchestrator` (bounded concurrency=2, retries) decides mode:
   - `CLOUD`: send thumbnail to backend proxy (Google Vision), async.
   - `ON_DEVICE`/`FALLBACK`: use coarse labels when cloud unavailable/unconfigured.
4. `DomainPackRepository` + `BasicCategoryEngine` map classifier output to domain categories/attributes.
5. View models push updated UI state (overlays, item list, selling flow).

**Performance characteristics:**
- Edge filtering: zero allocations (primitives only), no bitmap operations.
- Spatial-temporal merge: O(1) per candidate, stores only bbox center + timestamp + category.
- Rate-limited logging prevents log spam (viewport: once, cropRect: 5s, edge drops: 5s).

Mermaid (pipeline):
```mermaid
flowchart LR
    Frames[Camera Frames<br/>ViewPort-aligned] --> Detect[ML Kit Detection]
    Detect --> Filter[Geometry Filter<br/>cropRect + edge inset]
    Filter --> Raw[RawDetection]
    Raw --> Track[ObjectTracker<br/>spatial matching]
    Track --> Agg[ItemAggregator<br/>similarity + spatial-temporal fallback]
    Agg -- stable items only --> Queue[ClassificationOrchestrator]
    Queue -->|cloud| Cloud[Backend Proxy -> Google Vision]
    Queue -->|fallback| OnDevice[Coarse labels]
    Cloud --> Map[Domain Pack Mapping]
    OnDevice --> Map
    Map --> VM[ViewModels/StateFlow]
    VM --> UI[Compose UI]
```

---

## Deduplication & Detection Quality Configuration

**WYSIWYG Viewport Alignment:**
- **Location:** `CameraXManager.kt` (androidApp)
- **Mechanism:** `ViewPort` + `UseCaseGroup` ensures Preview and ImageAnalysis share the same field of view.
- **Result:** ML analysis only processes pixels visible to the user; eliminates off-screen "phantom" detections.

**Edge Gating (Geometry-Based Filtering):**
- **Location:** `ObjectDetectorClient.kt` (android-ml-mlkit)
- **Configuration:** `CameraXManager.EDGE_INSET_MARGIN_RATIO` (default: 0.10 = 10% inset from each edge)
- **Mechanism:** Filter detections whose center falls outside `ImageProxy.cropRect` minus inset margin.
- **Performance:** Zero allocations; uses primitive int/float arithmetic only; no bitmap operations.
- **Tuning:** Increase ratio (e.g., 0.15) for stricter filtering; decrease (e.g., 0.05) to allow more edge objects.

**Spatial-Temporal Merge Policy:**
- **Location:** `SpatialTemporalMergePolicy.kt` (shared/core-tracking - Android-free)
- **Purpose:** Fallback deduplication when tracker IDs churn or regular similarity scoring fails.
- **Configuration:** `MergeConfig` with presets:
  ```kotlin
  // Default: balanced merge decisions
  val DEFAULT = MergeConfig(
      timeWindowMs = 800L,           // Merge within 800ms
      minIoU = 0.3f,                 // Minimum overlap 30%
      requireCategoryMatch = true,    // Categories must match
      useIoU = true                   // Use IoU vs center distance
  )

  // Strict: fewer merges, more conservative
  val STRICT = MergeConfig(
      timeWindowMs = 500L,
      minIoU = 0.5f,
      requireCategoryMatch = true
  )

  // Lenient: more merges, less conservative
  val LENIENT = MergeConfig(
      timeWindowMs = 1200L,
      minIoU = 0.2f,
      requireCategoryMatch = false
  )
  ```
- **Metrics:** IoU (Intersection over Union) or normalized center distance.
- **Memory:** Stores only bbox center (2 floats), timestamp (1 long), category (1 int) per candidate.
- **Integration:** `ItemAggregator` consults merge policy when similarity scoring fails.

**Performance Impact:**
- CPU: Negligible (geometry checks use primitives; no image processing).
- Memory: Minimal (~24 bytes per active candidate in merge cache).
- Latency: No measurable impact (filtering happens before expensive operations).

---

## Module/Package Boundaries & Dependency Rules
- Shared modules (`shared/*`) are Android-free; enforced by `checkPortableModules`.
- Platform modules (`android-*`) do not depend on each other except adapters can be a leaf helper; none depend on `androidApp`.
- `androidApp` is the only integration point (wires UI + platform + shared).
- `core-domainpack` depends on shared models but not on platform code.
- Shell modules (`core-contracts`, `core-scan`) stay lightweight; no Android types.

## Security posture (concise)
- Network + classification defaults keep processing on-device; cloud classification only activates when `SCANIUM_API_BASE_URL`/`SCANIUM_API_KEY` are set (via `local.properties` or environment). See `androidApp/build.gradle.kts` BuildConfig entries.
- OWASP Dependency-Check and CycloneDX SBOM run from `androidApp` (see Gradle plugins) and are exercised via `security-cve-scan.yml`.
- Android network security config lives at `androidApp/src/main/res/xml/network_security_config.xml`; release builds enable R8/ProGuard per `proguard-rules.pro`.

---

## Cloud Classification Flow (Google Vision via backend proxy)
- **Trigger:** Only stable aggregated items with thumbnails.
- **Config:** `CloudClassifierConfig` + `CloudConfigProvider` (Android impl reads BuildConfig from `local.properties`/env: `scanium.api.base.url`, `scanium.api.key`). No secrets in source.
- **Transport:** OkHttp multipart JPEG upload to backend proxy; timeouts 10s/10s; retries on 408/429/5xx; EXIF stripped via re-encode.
- **Backend:** Holds Google credentials, rate limits, logs, maps Vision output to domain categories.
- **Fallback:** When config missing or network down, orchestrator uses on-device labels; results marked as fallback.
- **Testing:** Mock classifier for JVM tests; cloud path optional/gated by env.

---

## Build Guardrails
- Java 17 toolchain (root + androidApp).
- Commands: `./gradlew assembleDebug` (must stay green), `./gradlew test` (fast, offline), `./gradlew connectedAndroidTest` (device-only), `./gradlew lint` (optional/CI).
- Security/lint: CycloneDX + OWASP Dependency Check active in `androidApp`.

---

## Cross-Platform Readiness (iOS Prep)
- Contracts and models live in shared modules; no Android imports.
- Future iOS will implement:
  - Platform scanning adapter (Vision/AVFoundation → `RawDetection`/`ImageRef`).
  - Cloud classifier client using the same `CloudClassifierConfig`.
- Android remains unblocked; shared code already compiles for Android; iOS targets can be added later without touching Android.

---

## Backend Services Architecture

### Technology Stack
- **Runtime:** Node.js 20+ with TypeScript 5.x
- **Framework:** Express.js for HTTP server
- **ORM:** Prisma for type-safe database access
- **Database:** PostgreSQL 16 (Alpine Docker image)
- **Dev Tools:** ngrok for mobile device tunneling
- **Telemetry:** OpenTelemetry SDK for logs, traces, metrics

### Directory Structure
```
backend/
├── src/
│   ├── index.ts              # Express server entry point
│   ├── routes/               # API endpoint definitions
│   │   ├── items.ts          # Item CRUD operations
│   │   ├── auth/             # Authentication endpoints
│   │   └── health.ts         # Health check endpoint
│   ├── services/             # Business logic layer
│   ├── middleware/           # Express middleware (auth, validation)
│   └── types/                # TypeScript type definitions
├── prisma/
│   ├── schema.prisma         # Database schema (models, relations)
│   ├── migrations/           # Version-controlled schema changes
│   └── seed.ts               # Database seeding script
├── docker-compose.yml        # PostgreSQL container definition
├── package.json
├── tsconfig.json
└── .env                      # Environment vars (gitignored)
```

### API Endpoints (Current)
- `GET /healthz` - Health check (used by startup scripts)
- `POST /auth/ebay/start` - Initiate eBay OAuth flow
- `GET /auth/ebay/callback` - eBay OAuth callback handler
- `GET /auth/ebay/status` - Check eBay connection status
- *(Additional endpoints to be documented as implemented)*

### Database Schema (Prisma)
- **Items:** Scanned objects with metadata, category, pricing
- **Users:** User accounts and authentication
- **ListingDrafts:** Marketplace listing drafts
- *(Schema evolves with migrations; see `prisma/schema.prisma`)*

---

## Observability Stack Architecture

### LGTM Stack Components

**Grafana Alloy (OTLP Router):**
- Receives telemetry from backend via OTLP (gRPC port 4317, HTTP port 4318)
- Batches and routes data to appropriate backends (Loki, Tempo, Mimir)
- Provides admin UI on localhost:12345 for debugging
- Configuration: `monitoring/alloy/alloy.hcl`

**Loki (Log Aggregation):**
- Stores structured logs with labels
- 14-day retention by default (configurable)
- Accessible via Grafana datasource
- Local storage: `monitoring/data/loki/`
- Config: `monitoring/loki/loki.yaml`

**Tempo (Distributed Tracing):**
- Stores distributed traces with span relationships
- 7-day retention by default
- Supports TraceQL for advanced queries
- Local storage: `monitoring/data/tempo/`
- Config: `monitoring/tempo/tempo.yaml`

**Mimir (Metrics Storage):**
- Prometheus-compatible metrics storage
- 15-day retention by default
- Supports PromQL queries
- Local storage: `monitoring/data/mimir/`
- Config: `monitoring/mimir/mimir.yaml`

**Grafana (Visualization):**
- Dashboard UI on port 3000
- Pre-configured datasources (Loki, Tempo, Mimir)
- Pre-provisioned dashboards from `monitoring/grafana/dashboards/`
- Anonymous admin access for local dev (disable in production)
- Persistent storage: `monitoring/data/grafana/`

### Monitoring Stack Management

**Startup:**
```bash
# Integrated with backend
scripts/backend/start-dev.sh              # Starts backend + monitoring

# Standalone monitoring
scripts/monitoring/start-monitoring.sh    # Monitoring only
```

**Status & URLs:**
```bash
scripts/monitoring/print-urls.sh          # Health checks, access URLs
```

**Shutdown:**
```bash
scripts/backend/stop-dev.sh --with-monitoring  # Stop everything
scripts/monitoring/stop-monitoring.sh          # Stop monitoring only
```

**Container Management:**
- Project name: `scanium-monitoring` (avoids collisions)
- Network: `scanium-observability` (bridge)
- Logs: `docker compose -p scanium-monitoring logs -f [service]`
- Restart: `docker compose -p scanium-monitoring restart [service]`

### Data Persistence
- All data stored in `monitoring/data/` (gitignored)
- Grafana settings, users, dashboards persist across restarts
- To reset all monitoring data: `rm -rf monitoring/data/*`
- Dashboards provisioned from `monitoring/grafana/dashboards/` (version-controlled)

---

## Development Workflow

### One-Command Dev Environment

The `scripts/backend/start-dev.sh` script provides integrated startup:

**What it does:**
1. ✅ Validates prerequisites (Node.js, Docker, ngrok)
2. ✅ Starts PostgreSQL container (health checks)
3. ✅ Starts backend server (health checks on /healthz)
4. ✅ Starts ngrok tunnel (extracts public URL)
5. ✅ Optionally starts monitoring stack (default: enabled)
6. ✅ Displays all access URLs and management commands
7. ✅ Handles graceful shutdown on Ctrl+C

**Flags:**
- `--with-monitoring` (default) - Start monitoring stack
- `--no-monitoring` - Skip monitoring stack
- `MONITORING=0/1` env var override

**Idempotency:**
- Safe to run multiple times
- Detects already-running containers
- No duplicate containers created

**Output:**
- Backend: http://localhost:8080
- ngrok: Public URL (updates .env if changed)
- Grafana: http://localhost:3000 (if monitoring enabled)
- OTLP: localhost:4317 (gRPC), localhost:4318 (HTTP)

### Mobile Device Testing Workflow

1. Run `scripts/backend/start-dev.sh`
2. Note the ngrok URL (e.g., https://abc123.ngrok-free.dev)
3. Update Android app's `SettingsScreen.kt` with ngrok URL
4. Rebuild and install app on mobile device
5. App communicates with backend via ngrok tunnel
6. View telemetry in Grafana dashboards

### Debugging & Troubleshooting

**Backend logs:**
```bash
tail -f backend/.dev-server.log
```

**PostgreSQL logs:**
```bash
docker logs scanium-postgres
```

**Monitoring services:**
```bash
docker compose -p scanium-monitoring logs -f grafana
docker compose -p scanium-monitoring logs -f alloy
```

**Health checks:**
```bash
curl http://localhost:8080/healthz          # Backend
curl http://localhost:3000/api/health       # Grafana
curl http://localhost:3100/ready            # Loki
curl http://localhost:3200/ready            # Tempo
curl http://localhost:9009/ready            # Mimir
```

---

## Roadmap (high level)
- ✅ Backend API server with PostgreSQL (done)
- ✅ Observability stack with LGTM + Alloy (done)
- ✅ Integrated dev startup workflow (done)
- Harden shared contracts and config (done)
- Adapt orchestrator to shared contracts; add mocks/tests
- Route classifier outputs through domain pack mapping and surface status in UI
- Add iOS clients against the same contracts once Android path is stable
- Production deployment configuration (Kubernetes/Cloud Run)
- Backend API authentication and authorization
- End-to-end telemetry from Android app to Grafana
