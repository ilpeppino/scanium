#!/usr/bin/env sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../../.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/deploy/nas/compose/docker-compose.nas.monitoring.yml"

if [ ! -f "$COMPOSE_FILE" ]; then
  echo "ERROR: Compose file not found: $COMPOSE_FILE" >&2
  exit 1
fi

echo "▶ Repo root: $REPO_ROOT"
echo "▶ Compose file: $COMPOSE_FILE"

COMPOSE_DIR="$(dirname "$COMPOSE_FILE")"
cd "$COMPOSE_DIR"
export REPO_ROOT="$REPO_ROOT"

echo
echo "== Down (keep volumes) =="
docker-compose -f "$COMPOSE_FILE" down || true

echo
echo "== Remaining scanium monitoring containers (should be none) =="
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | egrep "scanium-(grafana|loki|mimir|tempo|alloy)" || true
