***REMOVED*** Scanium Ops Scripts

Phase 1 maintenance scripts for monitoring and troubleshooting the Scanium backend.

***REMOVED******REMOVED*** Quick Start

```bash
***REMOVED*** Run smoke tests against production
./scripts/ops/smoke.sh --base-url https://scanium.gtemp1.com

***REMOVED*** Run smoke tests with authentication
SCANIUM_API_KEY=your-key ./scripts/ops/smoke.sh --base-url https://scanium.gtemp1.com

***REMOVED*** Check Docker container status
./scripts/ops/docker_status.sh

***REMOVED*** Collect support bundle for debugging
./scripts/ops/collect_support_bundle.sh
```

***REMOVED******REMOVED*** Scripts

***REMOVED******REMOVED******REMOVED*** smoke.sh

Tests backend endpoints for reachability and authentication.

```bash
./scripts/ops/smoke.sh --help
./scripts/ops/smoke.sh --base-url https://scanium.gtemp1.com --timeout 15
```

**Endpoints tested:**
- `GET /health` - Health check (expect 200)
- `GET /v1/config` - Config endpoint (200 with key, 401 without)
- `GET /v1/preflight` - Preflight check (200 with key, 401 without)
- `GET /v1/assist/status` - Assist status (200/403 with key, 401/403 without)

***REMOVED******REMOVED******REMOVED*** docker_status.sh

Shows Docker container status with health and restart information.

```bash
./scripts/ops/docker_status.sh --help
./scripts/ops/docker_status.sh --filter scanium
./scripts/ops/docker_status.sh --include-all --json tmp/status.json
```

**Features:**
- Table output with name, image, status, health, restarts, ports
- Automatic log output for unhealthy/restarting/exited containers
- Optional JSON export

***REMOVED******REMOVED******REMOVED*** collect_support_bundle.sh

Collects diagnostic information for troubleshooting.

```bash
./scripts/ops/collect_support_bundle.sh --help
./scripts/ops/collect_support_bundle.sh --no-logs
./scripts/ops/collect_support_bundle.sh --out tmp/debug.tar.gz
```

**Bundle contents:**
- Docker container list and inspect output
- Network configuration
- Docker compose files (if present)
- Container logs (optional)
- Ops scripts for reference

***REMOVED******REMOVED*** Passing API Keys Safely

**Option 1: Environment variable (recommended)**
```bash
export SCANIUM_API_KEY=your-api-key
./scripts/ops/smoke.sh --base-url https://scanium.gtemp1.com
```

**Option 2: Inline (for one-off commands)**
```bash
SCANIUM_API_KEY=your-key ./scripts/ops/smoke.sh --base-url https://scanium.gtemp1.com
```

**Option 3: Command line argument (less secure - visible in process list)**
```bash
./scripts/ops/smoke.sh --base-url https://scanium.gtemp1.com --api-key your-key
```

***REMOVED******REMOVED*** NAS Cron Scheduling

For monitoring on the Synology NAS:

**Ephemeral container approach (recommended):**
```bash
***REMOVED*** Run every 5 minutes
*/5 * * * * docker run --rm -v /volume1/docker/scanium:/app scanium-ops /app/scripts/ops/smoke.sh --base-url https://scanium.gtemp1.com >> /var/log/scanium-smoke.log 2>&1
```

**Direct execution (if scripts are accessible):**
```bash
***REMOVED*** Run every 5 minutes
*/5 * * * * /volume1/docker/scanium/scripts/ops/smoke.sh --base-url https://scanium.gtemp1.com >> /var/log/scanium-smoke.log 2>&1

***REMOVED*** Daily status check at 6 AM
0 6 * * * /volume1/docker/scanium/scripts/ops/docker_status.sh >> /var/log/scanium-status.log 2>&1
```

***REMOVED******REMOVED*** Safety Notes

- All scripts support `--help`
- All scripts are idempotent (safe to run multiple times)
- Secrets are automatically redacted from output
- Scripts require only POSIX tools + docker CLI + curl
- Works on both macOS (development) and Linux (NAS)

***REMOVED******REMOVED*** Security

These scripts are designed to be safe:

- **Redaction:** API keys, tokens, passwords, and auth headers are automatically redacted
- **No secrets in output:** Support bundles never include `.env` files or `local.properties`
- **Read-only:** Scripts only read data, never modify containers or configuration

***REMOVED******REMOVED*** Phase 1

These scripts are designed as Phase 1 of the ops toolkit. Future phases may include:
- Dedicated ops container for NAS
- Prometheus metrics export
- Automated alerting integration
- Log aggregation
