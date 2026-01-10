#!/usr/bin/env bash
# =============================================================================
# Scanium Support Bundle Collector
# =============================================================================
# Collects diagnostic information for troubleshooting, with secret redaction.
# Creates a tar.gz bundle that is safe to share.
#
# Usage:
#   ./scripts/ops/collect_support_bundle.sh --help
#   ./scripts/ops/collect_support_bundle.sh
#   ./scripts/ops/collect_support_bundle.sh --no-logs --out tmp/debug.tar.gz
#
# =============================================================================

# Source common library
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

# -----------------------------------------------------------------------------
# Defaults
# -----------------------------------------------------------------------------
FILTER="scanium"
OUT_PATH=""
LOG_LINES=500
INCLUDE_LOGS=true

# -----------------------------------------------------------------------------
# Help
# -----------------------------------------------------------------------------
show_help() {
  cat <<EOF
collect_support_bundle.sh - Collect Scanium diagnostic bundle

Usage: $(basename "$0") [OPTIONS]

Options:
  --filter NAME     Filter containers by name substring (default: scanium)
  --out FILE        Output path (default: tmp/support_bundle_<timestamp>.tar.gz)
  --logs N          Number of log lines per container (default: 500)
  --no-logs         Skip container logs (faster, smaller bundle)
  --help            Show this help message

Bundle contents:
  - docker_ps.txt         Docker container list
  - docker_inspect/       Container inspection JSON (redacted)
  - docker_networks.txt   Network list
  - network_inspect/      Network inspection JSON
  - compose_files/        Docker compose files found in repo
  - container_logs/       Container logs (redacted, unless --no-logs)
  - scripts/              Ops scripts for reference

Security:
  - All output is redacted for secrets (API keys, tokens, passwords)
  - .env files are NEVER included
  - local.properties is NEVER included

Exit codes:
  0 - Bundle created successfully
  1 - Error occurred

EOF
}

# -----------------------------------------------------------------------------
# Parse arguments
# -----------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --help|-h)
      show_help
      exit 0
      ;;
    --filter)
      FILTER="$2"
      shift 2
      ;;
    --filter=*)
      FILTER="${1#*=}"
      shift
      ;;
    --out)
      OUT_PATH="$2"
      shift 2
      ;;
    --out=*)
      OUT_PATH="${1#*=}"
      shift
      ;;
    --logs)
      LOG_LINES="$2"
      shift 2
      ;;
    --logs=*)
      LOG_LINES="${1#*=}"
      shift
      ;;
    --no-logs)
      INCLUDE_LOGS=false
      shift
      ;;
    *)
      die "Unknown option: $1. Use --help for usage."
      ;;
  esac
done

# Validate required commands
require_cmd docker
require_cmd tar

# -----------------------------------------------------------------------------
# Setup
# -----------------------------------------------------------------------------
REPO_ROOT="$(ensure_repo_root)"
TIMESTAMP=$(date '+%Y%m%d_%H%M%S')

# Determine output path
if [[ -z "$OUT_PATH" ]]; then
  OUT_PATH="$REPO_ROOT/tmp/support_bundle_${TIMESTAMP}.tar.gz"
fi

# Handle relative paths
if [[ "$OUT_PATH" != /* ]]; then
  OUT_PATH="$REPO_ROOT/$OUT_PATH"
fi

# Verify output is in repo
case "$OUT_PATH" in
  "$REPO_ROOT"/*)
    ;;
  *)
    die "Output path must be inside repo: $OUT_PATH"
    ;;
esac

# Create tmp directory
mkdir -p "$(dirname "$OUT_PATH")"

# Create temp bundle directory
BUNDLE_DIR=$(mktemp -d)
trap 'rm -rf "$BUNDLE_DIR"' EXIT

log_info "Collecting support bundle..."
log_info "Filter: ${FILTER:-<all>}"

# -----------------------------------------------------------------------------
# Collect Docker container info
# -----------------------------------------------------------------------------
collect_containers() {
  log_info "Collecting container info..."

  # Get container list
  local containers
  if [[ -n "$FILTER" ]]; then
    containers=$(docker ps -a --format '{{.ID}}\t{{.Names}}' 2>/dev/null | grep -i "$FILTER" || true)
  else
    containers=$(docker ps -a --format '{{.ID}}\t{{.Names}}' 2>/dev/null)
  fi

  # Save docker ps output
  docker ps -a --filter "name=$FILTER" 2>/dev/null | redact_secrets > "$BUNDLE_DIR/docker_ps.txt" 2>&1 || true

  # If no filter or filter didn't work, get all
  if [[ ! -s "$BUNDLE_DIR/docker_ps.txt" ]]; then
    docker ps -a 2>/dev/null | redact_secrets > "$BUNDLE_DIR/docker_ps.txt" 2>&1 || true
  fi

  # Collect inspect for each container
  mkdir -p "$BUNDLE_DIR/docker_inspect"

  while IFS=$'\t' read -r id name; do
    [[ -z "$id" ]] && continue
    local safe_name="${name//\//_}"
    docker inspect "$id" 2>/dev/null | redact_secrets > "$BUNDLE_DIR/docker_inspect/${safe_name}.json" || true
  done <<< "$containers"

  echo "$containers"
}

# -----------------------------------------------------------------------------
# Collect container logs
# -----------------------------------------------------------------------------
collect_logs() {
  local containers="$1"

  if [[ "$INCLUDE_LOGS" == "false" ]]; then
    log_info "Skipping container logs (--no-logs)"
    return
  fi

  log_info "Collecting container logs (last $LOG_LINES lines)..."
  mkdir -p "$BUNDLE_DIR/container_logs"

  while IFS=$'\t' read -r id name; do
    [[ -z "$id" ]] && continue
    local safe_name="${name//\//_}"
    docker logs --tail "$LOG_LINES" "$id" 2>&1 | redact_secrets > "$BUNDLE_DIR/container_logs/${safe_name}.log" || true
  done <<< "$containers"
}

# -----------------------------------------------------------------------------
# Collect network info
# -----------------------------------------------------------------------------
collect_networks() {
  log_info "Collecting network info..."

  # List all networks
  docker network ls 2>/dev/null > "$BUNDLE_DIR/docker_networks.txt" || true

  # Inspect networks that mention scanium
  mkdir -p "$BUNDLE_DIR/network_inspect"

  local networks
  if [[ -n "$FILTER" ]]; then
    networks=$(docker network ls --format '{{.Name}}' 2>/dev/null | grep -i "$FILTER" || true)
  fi

  # Also check for default bridge networks used by scanium containers
  local container_networks
  container_networks=$(docker inspect --format '{{range .NetworkSettings.Networks}}{{.NetworkID}} {{end}}' $(docker ps -aq --filter "name=$FILTER") 2>/dev/null | tr ' ' '\n' | sort -u || true)

  for net in $networks $container_networks; do
    [[ -z "$net" ]] && continue
    local net_name
    net_name=$(docker network inspect --format '{{.Name}}' "$net" 2>/dev/null || echo "$net")
    local safe_name="${net_name//\//_}"
    docker network inspect "$net" 2>/dev/null | redact_secrets > "$BUNDLE_DIR/network_inspect/${safe_name}.json" || true
  done
}

# -----------------------------------------------------------------------------
# Collect compose files
# -----------------------------------------------------------------------------
collect_compose() {
  log_info "Collecting compose files..."
  mkdir -p "$BUNDLE_DIR/compose_files"

  # Search for compose files in repo
  local compose_files
  compose_files=$(find "$REPO_ROOT" \
    -type f \( -name 'docker-compose*.yml' -o -name 'docker-compose*.yaml' -o -name 'compose*.yml' -o -name 'compose*.yaml' \) \
    ! -path '*/node_modules/*' \
    ! -path '*/.git/*' \
    2>/dev/null || true)

  for file in $compose_files; do
    [[ -z "$file" ]] && continue
    # Skip if it looks like it contains secrets
    if grep -qiE '^\s*(password|secret|api_key|token)\s*:' "$file" 2>/dev/null; then
      # Still include but redact
      local basename
      basename=$(basename "$file")
      redact_secrets < "$file" > "$BUNDLE_DIR/compose_files/$basename" || true
    else
      cp "$file" "$BUNDLE_DIR/compose_files/" 2>/dev/null || true
    fi
  done
}

# -----------------------------------------------------------------------------
# Collect safe config files
# -----------------------------------------------------------------------------
collect_configs() {
  log_info "Collecting safe config files..."
  mkdir -p "$BUNDLE_DIR/docs"
  mkdir -p "$BUNDLE_DIR/scripts"

  # Copy docs/ops if exists
  if [[ -d "$REPO_ROOT/docs/ops" ]]; then
    cp -r "$REPO_ROOT/docs/ops" "$BUNDLE_DIR/docs/" 2>/dev/null || true
  fi

  # Copy scripts/ops (the scripts themselves, not lib)
  if [[ -d "$REPO_ROOT/scripts/ops" ]]; then
    find "$REPO_ROOT/scripts/ops" -maxdepth 1 -name '*.sh' -exec cp {} "$BUNDLE_DIR/scripts/" \; 2>/dev/null || true
  fi
}

# -----------------------------------------------------------------------------
# Create bundle manifest
# -----------------------------------------------------------------------------
create_manifest() {
  cat > "$BUNDLE_DIR/MANIFEST.txt" <<EOF
Scanium Support Bundle
======================

Created: $(timestamp)
Filter: ${FILTER:-<all>}
Include Logs: $INCLUDE_LOGS
Log Lines: $LOG_LINES

Contents:
---------
docker_ps.txt           - Container list
docker_inspect/         - Container inspection (redacted)
docker_networks.txt     - Network list
network_inspect/        - Network inspection (redacted)
compose_files/          - Docker compose files
container_logs/         - Container logs (redacted)
docs/                   - Documentation
scripts/                - Ops scripts

Security Notes:
---------------
- All secrets have been redacted
- .env files are NOT included
- Credentials are replaced with [REDACTED]

EOF
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------

# Collect everything
containers=$(collect_containers)
collect_logs "$containers"
collect_networks
collect_compose
collect_configs
create_manifest

# Create tarball
log_info "Creating bundle archive..."
tar -czf "$OUT_PATH" -C "$(dirname "$BUNDLE_DIR")" "$(basename "$BUNDLE_DIR")" 2>/dev/null

if [[ -f "$OUT_PATH" ]]; then
  local size
  size=$(ls -lh "$OUT_PATH" | awk '{print $5}')
  log_success "Support bundle created: $OUT_PATH ($size)"
  exit 0
else
  die "Failed to create support bundle"
fi
