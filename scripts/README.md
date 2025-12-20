***REMOVED*** Scripts

Centralized entry points for repo automation. Run from the repo root unless noted.

***REMOVED******REMOVED*** Active scripts
- `scripts/build.sh [gradle args]` – Finds Java 17 and runs Gradle with the provided arguments (works across macOS/Linux/CI).
- `scripts/backend/start-dev.sh` – Starts backend dev stack (runs from `backend/`, expects Node/Prisma/docker if available).
- `scripts/backend/stop-dev.sh` – Stops backend dev services on default ports (8080/ngrok).
- `scripts/backend/verify-setup.sh` – Sanity-checks backend `.env`, dependencies, Prisma generation, and tests.
- `scripts/dev/install-hooks.sh` – Installs git hooks from `hooks/pre-push`.
- `scripts/dev/test_ml_kit_detection.sh` – Installs the Android app on a connected device and tails ML Kit logs (requires Android SDK + device).
- `scripts/tools/create-github-issues.sh` – Converts Markdown issue templates under `docs/issues` into GitHub issues using `gh`.

***REMOVED******REMOVED*** Archive
- Place deprecated or personal scripts under `scripts/_archive/YYYY-MM/` with a short README explaining the replacement.
