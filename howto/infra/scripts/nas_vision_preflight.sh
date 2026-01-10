***REMOVED***!/usr/bin/env bash
***REMOVED*** =============================================================================
***REMOVED*** Scanium NAS Vision Preflight
***REMOVED*** =============================================================================
***REMOVED*** Verifies backend vision wiring on Synology via SSH:
***REMOVED*** - Compose file exists
***REMOVED*** - Vision service account file exists
***REMOVED*** - Backend container is running
***REMOVED*** - Vision/assistant env flags present
***REMOVED*** - Vision insights route is reachable (not 404)
***REMOVED***
***REMOVED*** Usage:
***REMOVED***   ./scripts/ops/nas_vision_preflight.sh --help
***REMOVED***   ./scripts/ops/nas_vision_preflight.sh --host nas
***REMOVED***
***REMOVED*** =============================================================================

***REMOVED*** Source common library
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
***REMOVED*** shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** Defaults
***REMOVED*** -----------------------------------------------------------------------------
HOST="nas"
REMOTE_REPO="/volume1/docker/scanium/repo"
REMOTE_COMPOSE=""
SERVICE="backend"
CONTAINER="scanium-backend"
BASE_URL="http://127.0.0.1:8080"
FAILED=0

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** Help
***REMOVED*** -----------------------------------------------------------------------------
show_help() {
  cat <<EOF
nas_vision_preflight.sh - Verify NAS backend vision configuration via SSH

Usage: $(basename "$0") [OPTIONS]

Options:
  --host NAME             SSH host (default: nas)
  --remote-repo PATH      Remote repo root (default: /volume1/docker/scanium/repo)
  --compose-file PATH     Remote compose file (default: <remote-repo>/deploy/nas/compose/docker-compose.nas.backend.yml)
  --service NAME          Compose service name (default: backend)
  --container NAME        Backend container name (default: scanium-backend)
  --base-url URL          Backend base URL on NAS (default: http://127.0.0.1:8080)
  --help                  Show this help message

Examples:
  ./scripts/ops/nas_vision_preflight.sh --host nas
  ./scripts/ops/nas_vision_preflight.sh --remote-repo /volume1/docker/scanium/repo

EOF
}

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** Parse arguments
***REMOVED*** -----------------------------------------------------------------------------
while [[ $***REMOVED*** -gt 0 ]]; do
  case "$1" in
    --help|-h)
      show_help
      exit 0
      ;;
    --host)
      HOST="$2"
      shift 2
      ;;
    --host=*)
      HOST="${1***REMOVED****=}"
      shift
      ;;
    --remote-repo)
      REMOTE_REPO="$2"
      shift 2
      ;;
    --remote-repo=*)
      REMOTE_REPO="${1***REMOVED****=}"
      shift
      ;;
    --compose-file)
      REMOTE_COMPOSE="$2"
      shift 2
      ;;
    --compose-file=*)
      REMOTE_COMPOSE="${1***REMOVED****=}"
      shift
      ;;
    --service)
      SERVICE="$2"
      shift 2
      ;;
    --service=*)
      SERVICE="${1***REMOVED****=}"
      shift
      ;;
    --container)
      CONTAINER="$2"
      shift 2
      ;;
    --container=*)
      CONTAINER="${1***REMOVED****=}"
      shift
      ;;
    --base-url)
      BASE_URL="$2"
      shift 2
      ;;
    --base-url=*)
      BASE_URL="${1***REMOVED****=}"
      shift
      ;;
    *)
      die "Unknown option: $1. Use --help for usage."
      ;;
  esac
done

if [[ -z "$REMOTE_COMPOSE" ]]; then
  REMOTE_COMPOSE="${REMOTE_REPO}/deploy/nas/compose/docker-compose.nas.backend.yml"
fi

require_cmd ssh

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** SSH helper
***REMOVED*** -----------------------------------------------------------------------------
ssh_run() {
  local cmd="$1"
  ssh "$HOST" "$cmd"
}

ssh_run_silent() {
  local cmd="$1"
  ssh "$HOST" "$cmd" 2>/dev/null
}

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** Checks
***REMOVED*** -----------------------------------------------------------------------------
log_info "Running NAS vision preflight on ${HOST}"
log_info "Remote compose: ${REMOTE_COMPOSE}"
log_info "Backend container: ${CONTAINER}"
log_info "Backend base URL: ${BASE_URL}"
echo ""

if ssh_run_silent "test -f '$REMOTE_COMPOSE'"; then
  log_success "Compose file exists"
else
  log_fail "Compose file missing: $REMOTE_COMPOSE"
  FAILED=1
fi

if ssh_run_silent "test -f /volume1/docker/scanium/secrets/vision-sa.json"; then
  log_success "Vision service account present"
else
  log_fail "Vision service account missing: /volume1/docker/scanium/secrets/vision-sa.json"
  FAILED=1
fi

if ssh_run_silent "docker ps --filter name=${CONTAINER} --format '{{.ID}}' | grep -q ."; then
  log_success "Backend container is running"
else
  log_fail "Backend container not running: ${CONTAINER}"
  FAILED=1
fi

log_info "Backend environment (vision + assistant flags):"
if env_output=$(ssh_run "docker inspect ${CONTAINER} --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -E '^(VISION_|VISION_FEATURE|GOOGLE_APPLICATION_CREDENTIALS|SCANIUM_CLASSIFIER_PROVIDER|ASSIST_|ASSISTANT_|OPENAI_|ANTHROPIC_)' || true"); then
  echo "$env_output" | redact_secrets
else
  log_warn "Failed to read container env"
  FAILED=1
fi

log_info "Checking vision insights route on ${BASE_URL}"
status_code=$(ssh_run_silent "curl -s -o /dev/null -w '%{http_code}' ${BASE_URL%/}/v1/vision/insights || echo 000" || echo "000")
if [[ "$status_code" == "404" || "$status_code" == "000" ]]; then
  log_fail "/v1/vision/insights reachable check failed (HTTP $status_code)"
  FAILED=1
else
  log_success "/v1/vision/insights reachable (HTTP $status_code)"
fi

echo ""
if [[ "$FAILED" -eq 0 ]]; then
  log_success "NAS vision preflight passed"
  exit 0
else
  log_fail "NAS vision preflight failed"
  exit 1
fi
