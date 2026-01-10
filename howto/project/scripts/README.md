# Scripts

Centralized entry points for repo automation. Run from the repo root unless noted.

## Script Master

**Recommended:** Use the interactive Script Master launcher for discovering and running scripts:

```bash
./script-master              # Interactive menu
./script-master --list       # List all scripts
./script-master --run dev run-tests  # Run specific script
```

See [docs/DEV_SCRIPTS.md](../docs/DEV_SCRIPTS.md) for comprehensive documentation.

## Quick Reference

### Build & Android
- `scripts/build.sh [gradle args]` – Portable Gradle build with JDK 17 auto-detection
- `scripts/android/build-install-devdebug.sh` – Build devDebug, install, and smoke test
- `scripts/android/set-backend-cloudflare-dev.sh` – Configure Cloudflare backend URL
- `scripts/dev/test_ml_kit_detection.sh` – Test ML Kit barcode detection on device

### Backend Development
- `scripts/backend/start-dev.sh [--with-monitoring|--no-monitoring]` – Start dev stack (PostgreSQL, API, ngrok, monitoring)
- `scripts/backend/stop-dev.sh [--with-monitoring]` – Stop backend dev services
- `scripts/backend/check-status.sh` – Comprehensive health check of stack
- `scripts/backend/verify-setup.sh` – Verify backend setup (.env, deps, Prisma)

### Observability Stack
- `scripts/monitoring/start-monitoring.sh` – Start LGTM stack (Grafana, Loki, Tempo, Mimir) + Alloy
- `scripts/monitoring/stop-monitoring.sh` – Stop monitoring containers
- `scripts/monitoring/print-urls.sh` – Display access URLs and health status

### CI / Quality
- `scripts/ci/local-ci.sh` – Run local CI checks (coverage, security, lint)
- `scripts/ci/doctor.sh` – Check CI prerequisites (Java, Gradle, Docker)
- `scripts/dev/verify_scripts.sh` – Verify all scripts for common issues

### Ops / NAS
- `scripts/ops/smoke.sh` – Backend endpoint smoke tests
- `scripts/ops/docker_status.sh` – Show Docker container status
- `scripts/ops/collect_support_bundle.sh` – Collect diagnostic bundle

### Termux (Android on-device)
Run from Termux on Android:

- `scripts/termux/termux-storage-setup.sh` – One-time storage access setup
- `scripts/termux/build_debug_to_downloads.sh` – Build debug APK locally
- `scripts/termux/remote_build_pull_apk.sh` – Build on Mac, pull to phone
- `scripts/termux/remote_autofix_tests.sh` – Run AI test fixer on remote Mac

## Script Standards

All scripts follow these conventions:
- Shebang: `#!/usr/bin/env bash`
- Error handling: `set -euo pipefail`
- Help flag: `--help` for usage info

Scripts can source the common library:
```bash
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
```

## Directory Structure

```
scripts/
├── lib/common.sh           # Shared library
├── android/                # Android build tools
├── backend/                # Backend server management
├── ci/                     # CI and quality checks
├── dev/                    # Development tools
├── monitoring/             # Observability stack
├── ops/                    # Operations (NAS, Docker)
├── termux/                 # Termux phone scripts
├── tools/                  # Utility scripts
└── scripts_manifest.json   # Script Master manifest
```

## Deprecated Scripts

These scripts are deprecated and forward to new locations:
- `scripts/android-build-install-dev.sh` → `scripts/android/build-install-devdebug.sh`
- `scripts/android-configure-backend-dev.sh` → `scripts/android/set-backend-cloudflare-dev.sh`
