***REMOVED***!/bin/sh
***REMOVED*** =============================================================================
***REMOVED*** Smoke Test Loop - runs smoke.sh every 5 minutes
***REMOVED*** =============================================================================
***REMOVED*** Used by the smoke-monitor Docker container.
***REMOVED*** Logs to /logs/smoke.log with automatic rotation.
***REMOVED*** =============================================================================

SCRIPT_DIR="$(dirname "$0")"
LOG_FILE="${LOG_DIR:-/logs}/smoke.log"
INTERVAL="${SMOKE_INTERVAL:-300}"
MAX_LINES=10000
KEEP_LINES=5000

log() {
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] $*"
}

log "Smoke monitor started (interval=${INTERVAL}s)"

while true; do
  ***REMOVED*** Run smoke test
  "${SCRIPT_DIR}/smoke.sh" >> "$LOG_FILE" 2>&1

  ***REMOVED*** Rotate log if too large
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
