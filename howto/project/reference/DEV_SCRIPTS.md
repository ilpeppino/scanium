# Scanium Developer Scripts

This document describes the script infrastructure in the Scanium repository, including the Script
Master launcher and all available development scripts.

## Quick Start

```bash
# Launch interactive Script Master
./script-master

# List all scripts for your platform
./script-master --list

# Run a specific script directly
./script-master --run dev run-tests
./script-master --run backend start-dev
```

## Script Master

The **Script Master** (`./script-master`) is an interactive menu-driven launcher for all Scanium
scripts. It provides:

- **Area-based organization**: Scripts grouped by function (Dev, Android, Backend, CI, etc.)
- **Platform awareness**: Only shows scripts compatible with your platform (macOS, Linux, Termux)
- **Safe execution**: Warns before running scripts that modify state
- **Logging**: All script output is saved to `tmp/script-master/`
- **Help integration**: Displays script descriptions and can show `--help` output

### Usage

```bash
# Interactive mode (recommended)
./script-master

# Non-interactive mode
./script-master --list                    # List all scripts
./script-master --run <area> <script>     # Run specific script
./script-master --help                    # Show help
```

### Adding a New Script to Script Master

1. Create your script in the appropriate directory (e.g., `scripts/dev/`)
2. Add an entry to `scripts/scripts_manifest.json`:

```json
{
  "id": "my-script",
  "label": "My Script",
  "path": "scripts/dev/my-script.sh",
  "description": "What this script does",
  "supports_help": true,
  "supports_dry_run": false,
  "safe": true,
  "environment": ["macos", "linux"]
}
```

3. The script will appear in Script Master on next launch

## Script Areas

### Development (Mac)

Local development tools for macOS.

| Script                         | Description                                      |
|--------------------------------|--------------------------------------------------|
| `scripts/build.sh`             | Portable Gradle build with JDK 17 auto-detection |
| `scripts/dev/run_tests.sh`     | Run Android unit tests with logging              |
| `scripts/dev/autofix_tests.sh` | AI-assisted test fixing loop                     |
| `scripts/dev/install-hooks.sh` | Install git pre-commit/pre-push hooks            |

### Android

Build and device tools for Android development.

| Script                                          | Description                             |
|-------------------------------------------------|-----------------------------------------|
| `scripts/android/build-install-devdebug.sh`     | Build devDebug, install, run smoke test |
| `scripts/android/set-backend-cloudflare-dev.sh` | Configure Cloudflare backend URL        |
| `scripts/dev/verify-backend-config.sh`          | Verify Android backend configuration    |
| `scripts/dev/test_ml_kit_detection.sh`          | Test ML Kit barcode detection           |

### Backend

Backend development server management.

| Script                            | Description                                  |
|-----------------------------------|----------------------------------------------|
| `scripts/backend/start-dev.sh`    | Start PostgreSQL, backend, ngrok, monitoring |
| `scripts/backend/stop-dev.sh`     | Stop all backend development services        |
| `scripts/backend/check-status.sh` | Comprehensive health check of stack          |
| `scripts/backend/verify-setup.sh` | Verify backend .env, deps, Prisma            |

### CI / Quality

Continuous integration and code quality tools.

| Script                       | Description                                    |
|------------------------------|------------------------------------------------|
| `scripts/ci/local-ci.sh`     | Run local CI checks (coverage, security, lint) |
| `scripts/ci/doctor.sh`       | Check CI prerequisites                         |
| `scripts/ci/run_coverage.sh` | Run test coverage analysis                     |
| `scripts/ci/run_security.sh` | Run CVE security scan                          |

### Monitoring

Observability stack management (LGTM + Alloy).

| Script                                      | Description                  |
|---------------------------------------------|------------------------------|
| `scripts/monitoring/start-monitoring.sh`    | Start LGTM stack             |
| `scripts/monitoring/stop-monitoring.sh`     | Stop monitoring containers   |
| `scripts/monitoring/print-urls.sh`          | Print monitoring stack URLs  |
| `scripts/monitoring/inventory-telemetry.sh` | Discover available telemetry |

### Ops / NAS

Operations, Docker, and NAS management.

| Script                                  | Description                  |
|-----------------------------------------|------------------------------|
| `scripts/ops/smoke.sh`                  | Backend endpoint smoke tests |
| `scripts/ops/docker_status.sh`          | Show Docker container status |
| `scripts/ops/collect_support_bundle.sh` | Collect diagnostic bundle    |
| `scripts/ops/nas_vision_preflight.sh`   | NAS vision preflight checks  |

### Termux (Phone)

Scripts for running on Android via Termux.

| Script                                       | Description                          |
|----------------------------------------------|--------------------------------------|
| `scripts/termux/build_debug_to_downloads.sh` | Build APK locally in Termux          |
| `scripts/termux/remote_build_pull_apk.sh`    | Build on Mac via SSH, pull to phone  |
| `scripts/termux/remote_autofix_tests.sh`     | Run AI test fixer on remote Mac      |
| `scripts/termux/termux-storage-setup.sh`     | Configure Termux storage permissions |

## Script Standards

All bash scripts in this repository follow these conventions:

### Shebang

```bash
#!/usr/bin/env bash
```

Use `env bash` for portability across macOS, Linux, and Termux.

### Error Handling

```bash
set -euo pipefail
```

- `-e`: Exit on error
- `-u`: Error on undefined variables
- `-o pipefail`: Pipeline fails if any command fails

### Common Library

Scripts can source the shared library for common functionality:

```bash
source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"
```

This provides:

- **Platform detection**: `is_macos`, `is_linux`, `is_termux`, `get_platform`
- **Logging**: `log_info`, `log_warn`, `log_error`, `log_success`, `die`
- **Checks**: `require_cmd`, `require_env`, `has_cmd`
- **Repo root**: `ensure_repo_root`
- **Portable operations**: `portable_realpath`, `portable_sed_i`
- **Security**: `redact_secrets`

### Help Flag

Scripts should support `--help`:

```bash
case "${1:-}" in
  --help|-h)
    show_help
    exit 0
    ;;
esac
```

## Verifying Scripts

Run the verification tool to check all scripts for common issues:

```bash
./scripts/dev/verify_scripts.sh
```

Options:

- `--fix`: Attempt to fix common issues
- `--verbose`: Show detailed output

The tool checks:

- Shebang lines
- Executable permissions
- CRLF line endings
- Hardcoded Termux paths

Results are saved to `tmp/scripts_verify_report.md`.

## Directory Structure

```
scripts/
├── lib/
│   └── common.sh           # Shared library
├── android/                # Android build tools
├── backend/                # Backend server management
├── ci/                     # CI and quality checks
├── dev/                    # Development tools
├── monitoring/             # Observability stack
├── ops/
│   └── lib/
│       └── common.sh       # Ops-specific library
├── termux/                 # Termux phone scripts
├── tools/                  # Utility scripts
├── scripts_manifest.json   # Script Master manifest
├── build.sh               # Main build script
└── *.py                    # Python asset generators
```

## Troubleshooting

### Script Master Not Finding Scripts

- Ensure `jq` is installed: `brew install jq` (macOS) or `pkg install jq` (Termux)
- Check that `scripts/scripts_manifest.json` exists
- Verify scripts are compatible with your platform

### SSH Authentication Issues (Termux Scripts)

- Ensure SSH keys are set up: `ssh-keygen -t ed25519`
- Copy key to Mac: `ssh-copy-id user@mac-hostname`
- Test connection: `ssh user@mac-hostname`

### Backend Not Starting

1. Check Docker/Colima is running: `docker info`
2. Check port availability: `lsof -i :8080`
3. Verify `.env` file exists in `backend/`
4. Run doctor: `./scripts/ci/doctor.sh`

### Monitoring Stack Issues

1. Check Docker context: `docker context show`
2. Verify Colima (if using): `colima status`
3. Check container health: `./scripts/ops/docker_status.sh`

## See Also

- [Backend Setup Guide](./ops/BACKEND_SETUP.md)
- [Termux Development](./ops/TERMUX_SETUP.md)
- [Monitoring Stack](./ops/MONITORING.md)
