***REMOVED*** Developer Guide

***REMOVED******REMOVED*** Prerequisites

***REMOVED******REMOVED******REMOVED*** Android Development
- JDK 17 (Gradle toolchain auto-detects)
- Android Studio with Android SDK + emulator or physical device
- `local.properties` with `sdk.dir=...` (copy from `local.properties.example`)

***REMOVED******REMOVED******REMOVED*** Backend Development
- Node.js 20+ (LTS recommended)
- npm or yarn
- Docker Desktop or Colima (for PostgreSQL and monitoring stack)
- ngrok (for mobile device testing): `brew install ngrok/ngrok/ngrok`

***REMOVED******REMOVED******REMOVED*** Optional (Observability)
- Docker Compose V2 (included with Docker Desktop)
- 4GB RAM available for Docker
- 10GB disk space for monitoring data

***REMOVED******REMOVED*** Cloud Classification Setup (config-driven)

Scanium uses cloud classification by default to identify items. To enable this feature:

1. **Create/update `local.properties`** in the project root:
   ```properties
   sdk.dir=/path/to/android-sdk

   ***REMOVED*** Cloud classification API configuration
   scanium.api.base.url=https://your-backend-url.com/api/v1
   scanium.api.key=your-dev-api-key
   ```

2. **Backend requirements**:
   - The backend must implement `POST /v1/classify` endpoint (see archived backend docs under `docs/_archive/2025-12/backend/` for reference)
   - Accepts multipart/form-data with `image` (JPEG) and `domainPackId` fields
   - Returns JSON with `domainCategoryId`, `confidence`, `label`, `attributes`, `requestId`

3. **Testing without backend**:
   - Leave `scanium.api.base.url` empty or unset
   - Cloud classifier will gracefully skip classification
   - Items will still appear with ML Kit detection labels

4. **CI/Production**:
   - Set environment variables: `SCANIUM_API_BASE_URL` and `SCANIUM_API_KEY`
   - Build will use env vars if `local.properties` keys are missing

**Security notes**:
- `local.properties` is gitignored by default
- Never commit API keys to version control
- Release builds require API base URL; fail-fast if missing

***REMOVED******REMOVED*** Settings Information Architecture

The redesigned Settings experience is organized into six categories (General, Camera & Scanning, AI Assistant, Notifications & Feedback, Data & Privacy, and Developer Options). Each category lives on its own screen with grouped toggles and actions, ensuring every preference has a single home. Refer to `docs/SETTINGS_IA.md` for a full mapping of settings → DataStore keys → screen location, plus guidance on adding new entries to the Settings home.

***REMOVED******REMOVED*** Local build & test

***REMOVED******REMOVED******REMOVED*** Build Variants (Flavors)

Scanium uses three product flavors for side-by-side installation and developer control:

| Flavor | App ID | App Name | Developer Options | Use Case |
|--------|--------|----------|-------------------|----------|
| **prod** | `com.scanium.app` | Scanium | Disabled | Production/stable release |
| **dev** | `com.scanium.app.dev` | Scanium Dev | Available | Internal development |
| **beta** | `com.scanium.app.beta` | Scanium Beta | Disabled | External beta testers |

**Side-by-side installation:** All three variants can be installed simultaneously on the same device (different applicationId). The launcher shows distinct app names for easy identification.

**Build commands:**

```bash
***REMOVED*** Prod builds (stable release)
./gradlew :androidApp:assembleProdDebug     ***REMOVED*** Prod debug APK
./gradlew :androidApp:assembleProdRelease   ***REMOVED*** Prod release APK

***REMOVED*** Dev builds (Developer Options accessible)
./gradlew :androidApp:assembleDevDebug      ***REMOVED*** Dev debug APK
./gradlew :androidApp:assembleDevRelease    ***REMOVED*** Dev release APK

***REMOVED*** Beta builds (Developer Options hidden)
./gradlew :androidApp:assembleBetaDebug     ***REMOVED*** Beta debug APK
./gradlew :androidApp:assembleBetaRelease   ***REMOVED*** Beta release APK
```

**APK output locations:**
- `androidApp/build/outputs/apk/prod/debug/` - Prod debug APKs
- `androidApp/build/outputs/apk/prod/release/` - Prod release APKs
- `androidApp/build/outputs/apk/dev/debug/` - Dev debug APKs
- `androidApp/build/outputs/apk/dev/release/` - Dev release APKs
- `androidApp/build/outputs/apk/beta/debug/` - Beta debug APKs
- `androidApp/build/outputs/apk/beta/release/` - Beta release APKs

**Technical implementation:**
- `BuildConfig.DEV_MODE_ENABLED`: `true` for dev, `false` for prod/beta
- App name defined via `resValue` in each flavor (see `androidApp/build.gradle.kts`)
- Settings home hides Developer Options when `DEV_MODE_ENABLED = false`
- Navigation to `/settings/developer` redirects back in prod/beta builds
- All dev-only UI and features are gated behind this flag

***REMOVED******REMOVED******REMOVED*** With Android SDK (Workstation / Android Studio)
- `./scripts/build.sh assembleDebug` or `./gradlew assembleDevDebug` – build dev debug APK.
- `./gradlew installDevDebug` – install dev build on a connected device/emulator.
- `./gradlew test` – JVM unit tests (fast path).
- `./gradlew connectedAndroidTest` – instrumented/Compose UI tests (device required).
- `./gradlew lint` – static checks.
- Coverage: `./gradlew koverVerify` (thresholds: shared modules ≥85%, androidApp ≥75%; HTML under `*/build/reports/kover/html` and `androidApp/build/reports/jacoco/testDebugUnitTest/html`).

***REMOVED******REMOVED******REMOVED*** Container environments (Claude Code, Docker without Android SDK)

**⚠️ Limitation:** `./gradlew test` and `./gradlew lint` **fail without Android SDK**.

**Container-friendly validation:**
```bash
***REMOVED*** JVM-only pre-push checks (shared modules only)
./gradlew prePushJvmCheck

***REMOVED*** Install git pre-push hook for automatic validation
./scripts/dev/install-hooks.sh
```

**What works in containers:**
- ✅ JVM tests for shared modules: `./gradlew :shared:core-models:jvmTest :shared:core-tracking:jvmTest`
- ✅ Portability checks: `./gradlew checkPortableModules checkNoLegacyImports`
- ✅ Code editing, static analysis with `rg`/`grep`
- ✅ Git operations, documentation updates

**What requires Android SDK (use CI/workstation):**
- ❌ Building APKs: `./gradlew assembleDebug`
- ❌ Android unit tests: `./gradlew :androidApp:testDebugUnitTest`
- ❌ Lint checks: `./gradlew lint`
- ❌ Instrumented tests: `./gradlew connectedAndroidTest`

***REMOVED******REMOVED******REMOVED*** Automated local test-fix loop
- `./scripts/dev/run_tests.sh test` – run unit tests with JDK 17 enforced.
- `./scripts/dev/autofix_tests.sh test` – run tests, extract failures, and invoke Codex for fixes.
- Gradle always runs under JDK 17 via `scripts/dev/gradle17.sh`, even if other tooling uses JDK 21.

**Mobile testing workflow (container-friendly):**
1. Push changes to your branch
2. GitHub Actions builds APK automatically (see `.github/workflows/android-debug-apk.yml`)
3. Download `scanium-app-debug-apk` artifact from workflow run
4. Install APK on device for testing

See also `hooks/README.md` for pre-push validation setup.

***REMOVED******REMOVED******REMOVED*** Termux: Build APK to Downloads

Build debug APKs directly on your Android device using Termux. The APK is copied to Downloads for easy installation.

**Important:** Run these scripts from inside Termux on Android. The shebang (`***REMOVED***!/usr/bin/env bash`) is portable across macOS and Termux, but storage paths like `$HOME/storage` only exist in Termux.

**One-time setup:**
```bash
***REMOVED*** Grant Termux storage permission (required once)
termux-setup-storage

***REMOVED*** Verify storage access
./scripts/termux/termux-storage-setup.sh
```

**Build and install:**
```bash
***REMOVED*** Build debug APK and copy to Downloads
./scripts/termux/build_debug_to_downloads.sh

***REMOVED*** Then:
***REMOVED*** 1. Open Files app
***REMOVED*** 2. Navigate to Downloads/scanium-apk/
***REMOVED*** 3. Tap the APK to install
```

**Requirements:**
- Termux with `git`, JDK 17, and Android SDK installed
- Storage permission granted via `termux-setup-storage`
- ~2GB free space for Gradle cache

***REMOVED******REMOVED******REMOVED*** Termux: Run Tests via Tailscale SSH

Run Gradle tests and autofix loops on your Mac from Termux over Tailscale SSH. Avoids Termux's JDK/toolchain limitations and works over mobile networks.

**Important:** Run these scripts from inside Termux on Android. The shebang is portable, but storage paths require Termux.

**One-time setup:**
1. Install Tailscale on both Mac and Android phone
2. Enable Remote Login on Mac: System Settings → General → Sharing → Remote Login
3. Generate SSH key in Termux and add to Mac:
   ```bash
   ssh-keygen -t ed25519
   cat ~/.ssh/id_ed25519.pub  ***REMOVED*** Copy this
   ***REMOVED*** On Mac: append to ~/.ssh/authorized_keys
   ```
4. Grant Termux storage access:
   ```bash
   termux-setup-storage
   ```
5. Configure remote environment:
   ```bash
   cp scripts/termux/remote_env.example scripts/termux/remote_env
   ***REMOVED*** Edit remote_env with your Mac's Tailscale IP (e.g., 100.x.x.x)
   ```

**Run autofix tests remotely:**
```bash
./scripts/termux/remote_autofix_tests.sh
***REMOVED*** Runs autofix_tests.sh on Mac, pulls logs to Downloads/scanium-ci/
```

**Build APK remotely:**
```bash
./scripts/termux/remote_build_pull_apk.sh
***REMOVED*** Builds debug APK on Mac, copies to Downloads/scanium-apk/
```

**Dry run (print commands without executing):**
```bash
DRY_RUN=1 ./scripts/termux/remote_autofix_tests.sh
```

**Troubleshooting:**
- Check Tailscale status: `tailscale status`
- Test SSH manually: `ssh user@100.x.x.x`
- Ensure Mac is not sleeping (caffeinate or disable sleep)

***REMOVED******REMOVED******REMOVED*** Local CI (quota-free)

Run GitHub Actions workflows locally to avoid CI quota limits:

```bash
./scripts/ci/local-ci.sh doctor    ***REMOVED*** Check prerequisites (Java 17, jq, etc.)
./scripts/ci/local-ci.sh coverage  ***REMOVED*** Run tests + coverage verification
./scripts/ci/local-ci.sh security  ***REMOVED*** Run OWASP CVE scan
./scripts/ci/local-ci.sh all       ***REMOVED*** Run both
```

**Outputs:** `tmp/ci/coverage/` and `tmp/ci/security/` (gitignored)

**Mirrors:**
- `coverage.yml`: `./gradlew clean test koverVerify` + `koverHtmlReport` + `jacocoTestReport`
- `security-cve-scan.yml`: `./gradlew dependencyCheckAnalyze` + fail on HIGH/CRITICAL CVEs

**Tip:** Set `NVD_API_KEY` for faster security scans (get key at https://nvd.nist.gov/developers/request-an-api-key)

***REMOVED******REMOVED******REMOVED*** Gradle Configuration Sanity Check

Fast Gradle configuration validation (quota-free, no network required):
```bash
./scripts/ci/gradle_sanity.sh       ***REMOVED*** Validate plugin resolution
./scripts/dev/autofix_tests.sh test ***REMOVED*** Runs sanity check before tests
```

**Plugin versions centralized:** Hilt and KSP versions are managed in `gradle/libs.versions.toml`.

***REMOVED******REMOVED******REMOVED*** Codex container limitations (factual)
- Android SDK/emulator are not available; JVM-only Gradle tasks are the safe path.
- Networked device access (ADB) is unavailable; use CI artifacts for APKs.
- Avoid installing system packages; rely on provided Gradle wrapper and scripts.

***REMOVED******REMOVED*** Deduplication & Detection Quality Tuning

**Viewport Alignment (WYSIWYG):**
- Ensures ML analysis sees only what user sees in Preview
- Configuration: Automatic via `ViewPort` + `UseCaseGroup` in `CameraXManager.kt`
- Logging: One-time log on startup showing viewport dimensions and edge inset

**Edge Gating (Drop Partial Objects):**
- Filters detections too close to screen edges (likely cut-off objects)
- Configuration: `CameraXManager.EDGE_INSET_MARGIN_RATIO` (default: 0.10 = 10%)
- Increase for stricter filtering, decrease to allow more edge objects
- Logging: Rate-limited (every 5 seconds) when detections are dropped

**Spatial-Temporal Deduplication:**
- Fallback merge policy for tracker ID churn
- Configuration: `SpatialTemporalMergePolicy.MergeConfig` in `shared/core-tracking`
- Presets: `DEFAULT` (balanced), `STRICT` (conservative), `LENIENT` (aggressive)
- Logging: Merge events logged via `ItemAggregator` with "SPATIAL-TEMPORAL MERGE" prefix

**Testing deduplication changes:**
```bash
***REMOVED*** Run unit tests for merge policy (Android-free)
./gradlew :shared:core-tracking:test --tests "SpatialTemporalMergePolicyTest"

***REMOVED*** Run all tracking tests
./gradlew :shared:core-tracking:test

***REMOVED*** Build and install for on-device testing
./gradlew installDebug
```

**On-device validation checklist:**

- [ ] Point camera at edge objects: verify off-screen objects don't create items
- [ ] Slowly pan camera: verify fewer duplicates appear
- [ ] Check overlay remains smooth and stable (no lag)
- [ ] Verify categories still match correctly
- [ ] Test with different lighting conditions

***REMOVED******REMOVED*** Debugging tips

- **Developer Options**: Access Settings → Developer Options for System Health diagnostics (backend, network, permissions). See section below.
- Use Logcat filters for tags like `CameraXManager`, `ObjectDetectorClient`, `CloudClassifier`, `ItemsViewModel`.
- **New viewport/filtering logs:** Search for `[VIEWPORT]`, `[CROP_RECT]`, `[EDGE_FILTER]` tags.
- **Deduplication logs:** Search for "SPATIAL-TEMPORAL MERGE" in `ItemAggregator` output.
- **AI Assistant logs:** Filter for `AssistantRepo` tag to see request/response activity and multipart image uploads.
- Detection overlays live in `androidApp/src/main/java/com/scanium/app/camera/DetectionOverlay.kt`; tweak drawing there.
- Aggregation/tracking behavior is covered by tests in `androidApp/src/test/...` and `core-tracking/src/test/...`; add golden tests when changing heuristics.
- For ML Kit analyzer crashes, enable verbose logs in the respective client classes under `androidApp/src/main/java/com/scanium/app/ml/`.

---

***REMOVED******REMOVED*** Developer Options (Dev Flavor Only)

Developer Options provides runtime diagnostics for troubleshooting connectivity, permissions, and device capabilities.

> **Note:** Developer Options are only available in **dev** flavor builds. Beta builds (`assembleBetaDebug`, `assembleBetaRelease`) completely hide this feature to prevent external testers from accessing internal debugging tools.

***REMOVED******REMOVED******REMOVED*** Accessing Developer Options

1. Launch the app (**dev** flavor build)
2. Navigate to **Settings** (gear icon on camera screen)
3. Tap **Developer Options** in the developer section

For dev debug builds, Developer Options is always visible. For dev release builds, the user must first enable "Developer Mode" toggle (requires access via a debug build first).

***REMOVED******REMOVED******REMOVED*** System Health Panel

The System Health panel displays real-time diagnostics:

| Check | What It Tests | Status Values |
|-------|---------------|---------------|
| **Backend** | Pings `/health` endpoint | Healthy (200), Degraded (401-403), Down (5xx/timeout) |
| **Network** | Network connectivity | Transport type (WiFi/Cellular/VPN), Metered status |
| **Permissions** | Camera, Microphone | Granted / Not granted |
| **Capabilities** | Speech recognition, TTS, Camera lenses | Available / Unavailable |
| **App Config** | Version, build, device, SDK, base URL | Informational |

***REMOVED******REMOVED******REMOVED*** Features

- **Auto-refresh**: Toggle to refresh diagnostics every 15 seconds
- **Manual refresh**: Tap refresh button for immediate update
- **Copy diagnostics**: Copy plaintext summary to clipboard for sharing
- **Developer Mode**: Toggle to unlock all features for testing
- **FTUE Tour**: Reset or force-enable first-time experience tour
- **Crash Test**: Send test exceptions to Sentry

***REMOVED******REMOVED******REMOVED*** Interpreting Results

**Backend "Down" status:**
- Check if backend is running: `scripts/backend/start-dev.sh`
- Verify base URL in Settings matches ngrok URL
- Check network connectivity (WiFi/Cellular)

**Permissions not granted:**
- App will prompt on first use
- Go to Android Settings → Apps → Scanium → Permissions

**Capabilities unavailable:**
- Speech recognition requires Google app installed
- Camera lenses depend on device hardware

***REMOVED******REMOVED******REMOVED*** Copying Diagnostics for Support

1. Open Developer Options
2. Tap the copy button (clipboard icon)
3. Paste in support tickets, Slack, or email

Example output:
```
=== Scanium Diagnostics ===
Generated: 2025-12-26 10:30:45

--- App Info ---
Version: 1.0.0 (123)
Build: debug
Device: Google Pixel 7
Android: 14 (SDK 34)

--- Backend ---
Status: HEALTHY
Detail: OK (200)
Latency: 145ms
Base URL: https://abc123.ngrok-free.dev

--- Network ---
Connected: Yes
Transport: WIFI
Metered: No

--- Permissions ---
Camera: Granted
Microphone: Granted

--- Capabilities ---
Speech Recognition: Available
Text-to-Speech: Available
Camera Lenses: Back, Front
```

***REMOVED******REMOVED******REMOVED*** Aggregation Accuracy (Overlay Filter)

A debug-only control for filtering which bounding boxes are shown on the camera overlay based on confidence threshold. This is useful for:

- **Verifying detection quality**: See only high-confidence detections to assess model accuracy
- **Debugging aggregation behavior**: Check if low-confidence detections are affecting the experience
- **Understanding confidence distribution**: Visually see how many detections fall into each confidence tier

**Location:** Developer Options → Detection & Performance section

**Usage:**
1. Open Developer Options (dev flavor builds only)
2. Find "Aggregation Accuracy (Overlay Filter)" slider
3. Adjust slider from left to right:
   - **All** (leftmost): Show all detected bboxes
   - **Low+**: Show bboxes with ≥25% confidence
   - **Medium+**: Show bboxes with ≥50% confidence
   - **High only** (rightmost): Show only bboxes with ≥75% confidence

**Important Notes:**
- This control affects **only visibility** (what bboxes are drawn)
- Detection, tracking, and aggregation logic are **NOT affected**
- Stroke widths remain exactly as they are for whatever bboxes are shown
- Setting persists across app restarts (stored in DataStore)
- Only available in debug/dev builds

**Technical Details:**
- Tier definitions: `ConfidenceTiers.kt` in camera package
- Filter applied in `MotionEnhancedOverlay.kt` before passing to `DetectionOverlay`
- Settings stored via `SettingsRepository.devOverlayAccuracyStepFlow`

---

***REMOVED******REMOVED*** Backend Development Workflow

***REMOVED******REMOVED******REMOVED*** Quick Start

**One-command startup** (recommended):
```bash
***REMOVED*** Start everything: PostgreSQL + backend server + ngrok + monitoring stack
scripts/backend/start-dev.sh

***REMOVED*** What you get:
***REMOVED*** ✅ PostgreSQL database (port 5432)
***REMOVED*** ✅ Backend API server (port 8080)
***REMOVED*** ✅ ngrok tunnel (public URL for mobile testing)
***REMOVED*** ✅ Observability stack (Grafana, Loki, Tempo, Mimir, Alloy)
```

**Skip monitoring** (backend only):
```bash
scripts/backend/start-dev.sh --no-monitoring
```

**Environment variable override**:
```bash
MONITORING=0 scripts/backend/start-dev.sh
```

***REMOVED******REMOVED******REMOVED*** Initial Setup

1. **Install dependencies**:
   ```bash
   cd backend
   npm install
   ```

2. **Configure environment**:
   ```bash
   cp .env.example .env
   ***REMOVED*** Edit .env with your configuration (DATABASE_URL, API keys, etc.)
   ```

3. **Run database migrations**:
   ```bash
   npm run prisma:migrate
   ***REMOVED*** Or: npx prisma migrate dev
   ```

4. **Generate Prisma Client**:
   ```bash
   npm run prisma:generate
   ***REMOVED*** Or: npx prisma generate
   ```

***REMOVED******REMOVED******REMOVED*** Development Commands

**Start backend** (manual):
```bash
cd backend
npm run dev              ***REMOVED*** Runs with nodemon (auto-restart on changes)
```

**Database operations**:
```bash
***REMOVED*** Run migrations
npm run prisma:migrate            ***REMOVED*** Apply pending migrations
npx prisma migrate dev --name <name>  ***REMOVED*** Create new migration

***REMOVED*** Database management
npx prisma studio                  ***REMOVED*** Open Prisma Studio GUI (localhost:5555)
npx prisma db push                 ***REMOVED*** Push schema changes without migration
npx prisma migrate reset           ***REMOVED*** Reset database (⚠️ deletes all data)
```

**Testing**:
```bash
npm test                   ***REMOVED*** Run backend tests
npm run test:watch         ***REMOVED*** Watch mode
npm run test:coverage      ***REMOVED*** Coverage report
```

**Type checking & linting**:
```bash
npm run type-check         ***REMOVED*** TypeScript type checking
npm run lint               ***REMOVED*** ESLint
npm run format             ***REMOVED*** Prettier formatting
```

***REMOVED******REMOVED******REMOVED*** Backend API Endpoints

**Health & Status:**
- `GET /healthz` - Health check (returns `{ status: "ok" }`)

**Authentication (eBay OAuth):**
- `POST /auth/ebay/start` - Initiate OAuth flow
- `GET /auth/ebay/callback` - OAuth callback handler
- `GET /auth/ebay/status` - Connection status

*(Additional endpoints documented in `backend/src/routes/`)*

***REMOVED******REMOVED******REMOVED*** Database Schema Changes

1. **Edit schema**: Modify `backend/prisma/schema.prisma`
2. **Create migration**: `npx prisma migrate dev --name add_user_table`
3. **Generate client**: `npx prisma generate` (or `npm run prisma:generate`)
4. **Test changes**: Run backend tests, verify in Prisma Studio
5. **Commit migration**: Commit `prisma/migrations/` directory

**Example schema change**:
```prisma
// backend/prisma/schema.prisma
model Item {
  id          String   @id @default(cuid())
  name        String
  category    String
  price       Float?
  imageUrl    String?
  userId      String   // Added field
  user        User     @relation(fields: [userId], references: [id])
  createdAt   DateTime @default(now())
  updatedAt   DateTime @updatedAt
}

model User {
  id          String   @id @default(cuid())
  email       String   @unique
  items       Item[]
  createdAt   DateTime @default(now())
}
```

***REMOVED******REMOVED******REMOVED*** Mobile Device Testing

1. **Start backend with ngrok**:
   ```bash
   scripts/backend/start-dev.sh
   ***REMOVED*** Note the ngrok URL (e.g., https://abc123.ngrok-free.dev)
   ```

2. **Update Android app**:
   ```kotlin
   // In SettingsScreen.kt or configuration
   ScaniumApi("https://abc123.ngrok-free.dev")
   ```

3. **Rebuild and install app**:
   ```bash
   ./gradlew installDebug
   ```

4. **Test from mobile device**:
   - App connects to backend via ngrok tunnel
   - View logs in `backend/.dev-server.log`
   - View telemetry in Grafana (if monitoring enabled)

***REMOVED******REMOVED******REMOVED*** Troubleshooting Backend

**PostgreSQL not starting**:
```bash
***REMOVED*** Check container status
docker ps --filter name=scanium-postgres

***REMOVED*** View logs
docker logs scanium-postgres

***REMOVED*** Restart container
docker restart scanium-postgres
```

**Backend server crashes**:
```bash
***REMOVED*** View logs
tail -f backend/.dev-server.log

***REMOVED*** Check Node.js version
node --version  ***REMOVED*** Should be 20+

***REMOVED*** Reinstall dependencies
cd backend && rm -rf node_modules package-lock.json && npm install
```

**ngrok URL changed**:
- The start-dev.sh script will prompt you to update `.env`
- Accept the prompt to automatically update `PUBLIC_BASE_URL`
- Restart backend server if needed

**Database connection errors**:
```bash
***REMOVED*** Check DATABASE_URL in .env
cat backend/.env | grep DATABASE_URL

***REMOVED*** Test PostgreSQL connection
docker exec scanium-postgres psql -U scanium -d scanium -c "SELECT 1"

***REMOVED*** Reset database (⚠️ deletes all data)
cd backend && npx prisma migrate reset
```

---

***REMOVED******REMOVED*** Observability Stack

***REMOVED******REMOVED******REMOVED*** Overview

Scanium uses the **LGTM stack** (Loki, Grafana, Tempo, Mimir) + Grafana Alloy for comprehensive observability:

- **Grafana**: Visualization dashboards (port 3000)
- **Alloy**: OpenTelemetry receiver (ports 4317 gRPC, 4318 HTTP)
- **Loki**: Log aggregation (14-day retention)
- **Tempo**: Distributed tracing (7-day retention)
- **Mimir**: Prometheus-compatible metrics (15-day retention)

***REMOVED******REMOVED******REMOVED*** Access Monitoring

**View all URLs and health status**:
```bash
scripts/monitoring/print-urls.sh
```

**Common URLs**:
- Grafana: http://localhost:3000 (anonymous admin access in dev)
- Alloy OTLP gRPC: localhost:4317 (for telemetry ingestion)
- Alloy OTLP HTTP: http://localhost:4318
- Alloy UI: http://localhost:12345 (localhost only, debugging)
- Loki: http://localhost:3100 (internal, access via Grafana)
- Tempo: http://localhost:3200 (internal, access via Grafana)
- Mimir: http://localhost:9009 (internal, access via Grafana)

***REMOVED******REMOVED******REMOVED*** Managing Monitoring Stack

**Start monitoring stack** (standalone):
```bash
scripts/monitoring/start-monitoring.sh
```

**Stop monitoring stack**:
```bash
scripts/monitoring/stop-monitoring.sh

***REMOVED*** Or with backend
scripts/backend/stop-dev.sh --with-monitoring
```

**View logs**:
```bash
***REMOVED*** All services
docker compose -p scanium-monitoring logs -f

***REMOVED*** Specific service
docker compose -p scanium-monitoring logs -f grafana
docker compose -p scanium-monitoring logs -f alloy
docker compose -p scanium-monitoring logs -f loki
```

**Restart a service**:
```bash
docker compose -p scanium-monitoring restart grafana
```

**Health checks**:
```bash
***REMOVED*** Grafana
curl http://localhost:3000/api/health

***REMOVED*** Loki
curl http://localhost:3100/ready

***REMOVED*** Tempo
curl http://localhost:3200/ready

***REMOVED*** Mimir
curl http://localhost:9009/ready

***REMOVED*** Or use the helper script
scripts/monitoring/print-urls.sh  ***REMOVED*** Shows health status of all services
```

***REMOVED******REMOVED******REMOVED*** Dashboards

**Pre-provisioned dashboards** are in `monitoring/grafana/dashboards/`:
- Pipeline health metrics (LGTM stack self-observability)
- Backend API performance
- *(Add custom dashboards as JSON files here)*

**Add a new dashboard**:
1. Create dashboard in Grafana UI (http://localhost:3000)
2. Export as JSON
3. Save to `monitoring/grafana/dashboards/my-dashboard.json`
4. Restart Grafana: `docker compose -p scanium-monitoring restart grafana`

***REMOVED******REMOVED******REMOVED*** Data Persistence

**Data stored in** `monitoring/data/`:
- `grafana/` - Dashboard settings, users, preferences
- `loki/` - Log data
- `tempo/` - Trace data
- `mimir/` - Metrics data

**Reset all data**:
```bash
***REMOVED*** ⚠️ This deletes all monitoring data
rm -rf monitoring/data/*

***REMOVED*** Recreate containers
docker compose -p scanium-monitoring down
docker compose -p scanium-monitoring up -d
```

***REMOVED******REMOVED******REMOVED*** Configuring Retention

Edit retention in config files:

**Loki** (`monitoring/loki/loki.yaml`):
```yaml
limits_config:
  retention_period: 336h  ***REMOVED*** 14 days (default)
```

**Tempo** (`monitoring/tempo/tempo.yaml`):
```yaml
compactor:
  compaction:
    block_retention: 168h  ***REMOVED*** 7 days (default)
```

**Mimir** (`monitoring/mimir/mimir.yaml`):
```yaml
limits:
  compactor_blocks_retention_period: 360h  ***REMOVED*** 15 days (default)
```

After editing, restart containers:
```bash
docker compose -p scanium-monitoring down
docker compose -p scanium-monitoring up -d
```

***REMOVED******REMOVED******REMOVED*** Sending Telemetry

**From backend** (OpenTelemetry SDK):
```typescript
// Backend automatically exports to Alloy
// Configured in backend/src/index.ts
// OTLP endpoint: http://localhost:4318 (HTTP)
```

**From Android app** (future):
```kotlin
// Configure OTLP exporter to send to Alloy
// Use local.properties to configure endpoint:
scanium.otlp.enabled=true
scanium.otlp.endpoint=http://<lan-ip>:4318
```

***REMOVED******REMOVED******REMOVED*** Troubleshooting Monitoring

**Grafana not accessible**:
```bash
***REMOVED*** Check if running
docker ps --filter name=scanium-grafana

***REMOVED*** View logs
docker compose -p scanium-monitoring logs grafana

***REMOVED*** Restart
docker compose -p scanium-monitoring restart grafana
```

**No data in dashboards**:
1. Check datasources are configured: Grafana → Connections → Data sources
2. Verify Alloy is receiving data: http://localhost:12345
3. Check backend is exporting telemetry (logs in `.dev-server.log`)
4. Verify Loki/Tempo/Mimir are healthy: `scripts/monitoring/print-urls.sh`

**Containers not starting**:
```bash
***REMOVED*** Check Docker daemon
docker info

***REMOVED*** View compose logs
docker compose -p scanium-monitoring logs

***REMOVED*** Check disk space
df -h  ***REMOVED*** Need 10GB for data storage

***REMOVED*** Check Docker resources
docker stats  ***REMOVED*** Ensure 4GB RAM available
```

**Pre-existing config issue (Tempo)**:
- Known issue: Tempo config has deprecated field (line 67)
- Stack will work with Loki and Mimir; Tempo may restart
- Fix by updating `monitoring/tempo/tempo.yaml` (separate task)

---

***REMOVED******REMOVED*** Development Workflow Summary

***REMOVED******REMOVED******REMOVED*** Full Stack Development

1. **Start everything**:
   ```bash
   scripts/backend/start-dev.sh  ***REMOVED*** Backend + PostgreSQL + ngrok + monitoring
   ```

2. **Develop Android app**:
   ```bash
   ./gradlew installDebug        ***REMOVED*** Build and install on device
   ```

3. **View telemetry**:
   - Open Grafana: http://localhost:3000
   - Explore logs, traces, metrics

4. **Stop services**:
   ```bash
   scripts/backend/stop-dev.sh --with-monitoring
   ```

***REMOVED******REMOVED******REMOVED*** Backend-Only Development

1. **Start backend** (no monitoring):
   ```bash
   scripts/backend/start-dev.sh --no-monitoring
   ```

2. **Develop and test**:
   ```bash
   cd backend
   npm run dev          ***REMOVED*** Auto-restart on changes
   npm test             ***REMOVED*** Run tests
   ```

3. **Stop backend**:
   ```bash
   scripts/backend/stop-dev.sh
   ```
