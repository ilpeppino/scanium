#!/usr/bin/env bash
# =============================================================================
# Scanium Docker Status
# =============================================================================
# Shows status of Docker containers with health and restart information.
# Prints logs for unhealthy/restarting/exited containers.
#
# Usage:
#   ./scripts/ops/docker_status.sh --help
#   ./scripts/ops/docker_status.sh --filter scanium
#   ./scripts/ops/docker_status.sh --include-all --json status.json
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
LOG_LINES=120
INCLUDE_ALL=false
JSON_OUT=""
HAS_ISSUES=false

# -----------------------------------------------------------------------------
# Help
# -----------------------------------------------------------------------------
show_help() {
  cat <<EOF
docker_status.sh - Scanium Docker container status

Usage: $(basename "$0") [OPTIONS]

Options:
  --filter NAME     Filter containers by name substring (default: scanium)
  --logs N          Number of log lines to show for unhealthy containers (default: 120)
  --include-all     Show all containers (ignore filter)
  --json FILE       Write JSON summary to FILE (inside repo tmp/)
  --help            Show this help message

Examples:
  # Show scanium containers
  ./scripts/ops/docker_status.sh

  # Show all containers
  ./scripts/ops/docker_status.sh --include-all

  # Export status as JSON
  ./scripts/ops/docker_status.sh --json tmp/status.json

Output columns:
  NAME       - Container name
  IMAGE      - Image name (truncated)
  STATUS     - Container status
  HEALTH     - Health status (if configured)
  RESTARTS   - Restart count
  PORTS      - Published ports

Exit codes:
  0 - All containers healthy
  1 - One or more containers unhealthy/exited/restarting

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
    --logs)
      LOG_LINES="$2"
      shift 2
      ;;
    --logs=*)
      LOG_LINES="${1#*=}"
      shift
      ;;
    --include-all)
      INCLUDE_ALL=true
      FILTER=""
      shift
      ;;
    --json)
      JSON_OUT="$2"
      shift 2
      ;;
    --json=*)
      JSON_OUT="${1#*=}"
      shift
      ;;
    *)
      die "Unknown option: $1. Use --help for usage."
      ;;
  esac
done

# Validate required commands
require_cmd docker

# -----------------------------------------------------------------------------
# Get container data
# -----------------------------------------------------------------------------
get_containers() {
  local format='{{.ID}}\t{{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'

  if [[ "$INCLUDE_ALL" == "true" ]]; then
    docker ps -a --format "$format" 2>/dev/null
  elif [[ -n "$FILTER" ]]; then
    docker ps -a --format "$format" 2>/dev/null | grep -i "$FILTER" || true
  else
    docker ps -a --format "$format" 2>/dev/null
  fi
}

get_container_health() {
  local container_id="$1"
  docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}N/A{{end}}' "$container_id" 2>/dev/null || echo "unknown"
}

get_container_restarts() {
  local container_id="$1"
  docker inspect --format='{{.RestartCount}}' "$container_id" 2>/dev/null || echo "0"
}

is_problematic() {
  local status="$1"
  local health="$2"

  # Check for problematic states
  case "$status" in
    *Exited*|*exited*) return 0 ;;
    *Restarting*|*restarting*) return 0 ;;
    *Dead*|*dead*) return 0 ;;
  esac

  case "$health" in
    unhealthy) return 0 ;;
  esac

  return 1
}

# -----------------------------------------------------------------------------
# Print table
# -----------------------------------------------------------------------------
print_table() {
  local containers="$1"

  if [[ -z "$containers" ]]; then
    log_info "No containers found matching filter: ${FILTER:-<all>}"
    return
  fi

  # Print header
  printf "%-25s %-35s %-20s %-12s %-8s %s\n" \
    "NAME" "IMAGE" "STATUS" "HEALTH" "RESTARTS" "PORTS"
  printf "%-25s %-35s %-20s %-12s %-8s %s\n" \
    "-------------------------" "-----------------------------------" "--------------------" "------------" "--------" "-----"

  # Print rows
  while IFS=$'\t' read -r id name image status ports; do
    [[ -z "$id" ]] && continue

    local health
    health=$(get_container_health "$id")
    local restarts
    restarts=$(get_container_restarts "$id")

    # Truncate long values
    local name_short="${name:0:25}"
    local image_short="${image:0:35}"
    local status_short="${status:0:20}"
    local ports_short="${ports:0:40}"

    printf "%-25s %-35s %-20s %-12s %-8s %s\n" \
      "$name_short" "$image_short" "$status_short" "$health" "$restarts" "$ports_short"

    # Track issues
    if is_problematic "$status" "$health"; then
      HAS_ISSUES=true
    fi
  done <<< "$containers"
}

# -----------------------------------------------------------------------------
# Show logs for problematic containers
# -----------------------------------------------------------------------------
show_problem_logs() {
  local containers="$1"

  if [[ -z "$containers" ]]; then
    return
  fi

  local shown_header=false

  while IFS=$'\t' read -r id name image status ports; do
    [[ -z "$id" ]] && continue

    local health
    health=$(get_container_health "$id")

    if is_problematic "$status" "$health"; then
      if [[ "$shown_header" == "false" ]]; then
        echo ""
        log_warn "Showing logs for problematic containers (last $LOG_LINES lines):"
        shown_header=true
      fi

      echo ""
      echo "=== $name (status: $status, health: $health) ==="
      docker logs --tail "$LOG_LINES" "$id" 2>&1 | redact_secrets || echo "  (no logs available)"
    fi
  done <<< "$containers"
}

# -----------------------------------------------------------------------------
# Generate JSON output
# -----------------------------------------------------------------------------
generate_json() {
  local containers="$1"
  local output_file="$2"

  local repo_root
  repo_root="$(ensure_repo_root)"

  # Ensure output is in tmp/
  local tmp_dir="$repo_root/tmp"
  mkdir -p "$tmp_dir"

  # Handle relative paths
  if [[ "$output_file" != /* ]]; then
    output_file="$repo_root/$output_file"
  fi

  # Verify file is in repo
  case "$output_file" in
    "$repo_root"/*)
      ;;
    *)
      die "JSON output must be inside repo: $output_file"
      ;;
  esac

  # Build JSON
  local json='{"timestamp":"'"$(timestamp)"'","containers":['
  local first=true

  while IFS=$'\t' read -r id name image status ports; do
    [[ -z "$id" ]] && continue

    local health
    health=$(get_container_health "$id")
    local restarts
    restarts=$(get_container_restarts "$id")

    local problematic="false"
    is_problematic "$status" "$health" && problematic="true"

    [[ "$first" == "false" ]] && json+=","
    first=false

    # Escape special characters for JSON
    name="${name//\"/\\\"}"
    image="${image//\"/\\\"}"
    status="${status//\"/\\\"}"
    ports="${ports//\"/\\\"}"

    json+='{"id":"'"$id"'","name":"'"$name"'","image":"'"$image"'","status":"'"$status"'","health":"'"$health"'","restarts":'"$restarts"',"ports":"'"$ports"'","problematic":'"$problematic"'}'
  done <<< "$containers"

  json+='],"has_issues":'"$(if $HAS_ISSUES; then echo "true"; else echo "false"; fi)"'}'

  echo "$json" > "$output_file"
  log_info "JSON written to: $output_file"
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------
log_info "Docker container status (filter: ${FILTER:-<all>})"
echo ""

containers=$(get_containers)
print_table "$containers"
show_problem_logs "$containers"

if [[ -n "$JSON_OUT" ]]; then
  generate_json "$containers" "$JSON_OUT"
fi

echo ""
if $HAS_ISSUES; then
  log_fail "One or more containers have issues"
  exit 1
else
  log_success "All containers healthy"
  exit 0
fi
