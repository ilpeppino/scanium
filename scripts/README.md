***REMOVED*** Scripts

Centralized entry points for repo automation. Run from the repo root unless noted.

***REMOVED******REMOVED*** Active scripts

***REMOVED******REMOVED******REMOVED*** Build & Android
- `scripts/build.sh [gradle args]` – Finds Java 17 and runs Gradle with the provided arguments (works across macOS/Linux/CI).
- `scripts/dev/test_ml_kit_detection.sh` – Installs the Android app on a connected device and tails ML Kit logs (requires Android SDK + device).

***REMOVED******REMOVED******REMOVED*** Backend Development
- `scripts/backend/start-dev.sh [--with-monitoring|--no-monitoring]` – Starts backend dev stack (PostgreSQL, API, ngrok) and optionally the monitoring stack (Grafana, Loki, Tempo, Mimir, Alloy). Monitoring is enabled by default. Use `MONITORING=0` env var to override.
- `scripts/backend/stop-dev.sh [--with-monitoring]` – Stops backend dev services (8080/ngrok/PostgreSQL). Use `--with-monitoring` to also stop the monitoring stack.
- `scripts/backend/verify-setup.sh` – Sanity-checks backend `.env`, dependencies, Prisma generation, and tests.

***REMOVED******REMOVED******REMOVED*** Observability Stack
- `scripts/monitoring/start-monitoring.sh` – Starts the LGTM observability stack (Grafana, Loki, Tempo, Mimir) + Alloy OTLP receiver. Performs health checks and displays status.
- `scripts/monitoring/stop-monitoring.sh` – Stops the monitoring stack containers.
- `scripts/monitoring/print-urls.sh` – Displays access URLs, health status, and management commands for the monitoring stack. Shows Grafana dashboard, OTLP endpoints, and backend storage URLs.

***REMOVED******REMOVED******REMOVED*** Development Tools
- `scripts/dev/install-hooks.sh` – Installs git hooks from `hooks/pre-push`.
- `scripts/tools/create-github-issues.sh` – Converts Markdown issue templates under `docs/issues` into GitHub issues using `gh`.

***REMOVED******REMOVED******REMOVED*** Termux (Android on-device builds)

**Run these scripts from inside Termux on Android.** The shebang (`***REMOVED***!/usr/bin/env bash`) is portable, but storage paths like `$HOME/storage` only exist in Termux.

- `scripts/termux/termux-storage-setup.sh` – One-time check/setup for Termux storage access.
- `scripts/termux/build_debug_to_downloads.sh` – Build debug APK and copy to Downloads for installation.
- `scripts/termux/remote_autofix_tests.sh` – Run autofix tests on remote Mac via Tailscale SSH.
- `scripts/termux/remote_build_pull_apk.sh` – Build APK on remote Mac and pull to phone Downloads.

***REMOVED******REMOVED*** Archive
- Place deprecated or personal scripts under `scripts/_archive/YYYY-MM/` with a short README explaining the replacement.
