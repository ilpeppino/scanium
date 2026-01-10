#!/bin/sh
# =============================================================================
# Smoke Test Loop - runs smoke.sh every 5 minutes
# =============================================================================
# Used by the smoke-monitor Docker container.
# Logs to /logs/smoke.log with automatic rotation.
#
# Environment variables:
#   SMOKE_BASE_URL  - Base URL to test (default: https://scanium.gtemp1.com)
#   SCANIUM_API_KEY - API key for authenticated tests
#   SMOKE_INTERVAL  - Seconds between tests (default: 300)
#   LOG_DIR         - Log directory (default: /logs)
# =============================================================================

SCRIPT_DIR="$(dirname "$0")"
LOG_FILE="${LOG_DIR:-/logs}/smoke.log"
INTERVAL="${SMOKE_INTERVAL:-300}"
BASE_URL="${SMOKE_BASE_URL:-https://scanium.gtemp1.com}"
MAX_LINES=10000
KEEP_LINES=5000

log() {
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] $*"
}

log "Smoke monitor started (interval=${INTERVAL}s, url=${BASE_URL})"

while true; do
  # Run smoke test
  "${SCRIPT_DIR}/smoke.sh" --base-url "$BASE_URL" >> "$LOG_FILE" 2>&1

  # Rotate log if too large
  if [ -f "$LOG_FILE" ]; then
    lines=$(wc -l < "$LOG_FILE")
    if [ "$lines" -gt "$MAX_LINES" ]; then
      log "Rotating log (${lines} lines)" >> "$LOG_FILE"
      tail -"$KEEP_LINES" "$LOG_FILE" > "${LOG_FILE}.tmp"
      mv "${LOG_FILE}.tmp" "$LOG_FILE"
    fi
  fi

  sleep "$INTERVAL"
done
