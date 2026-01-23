#!/usr/bin/env bash
set -euo pipefail

# Resolve the directory where this script lives (even if called via symlink)
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

# Try to locate repo root:
# 1) if git is available and script is inside the repo, use git root
# 2) otherwise, fall back to SCRIPT_DIR and let user place script in repo
if command -v git >/dev/null 2>&1 && git -C "$SCRIPT_DIR" rev-parse --show-toplevel >/dev/null 2>&1; then
  REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
else
  REPO_ROOT="$SCRIPT_DIR"
fi

COMPOSE_FILE="$REPO_ROOT/deploy/nas/compose/docker-compose.nas.monitoring.yml"

echo "▶ Repo root: $REPO_ROOT"
echo "▶ Compose file: $COMPOSE_FILE"

if [ ! -f "$COMPOSE_FILE" ]; then
  echo
  echo "❌ Compose file not found."
  echo "   Expected: $COMPOSE_FILE"
  echo
  echo "Fix options:"
  echo "  1) Move this script into the repo (anywhere inside it) and re-run."
  echo "  2) Or export COMPOSE_FILE to an absolute path, e.g.:"
  echo "     COMPOSE_FILE=/volume1/docker/scanium/repo/deploy/nas/compose/docker-compose.nas.monitoring.yml ./monitoring-dev.sh"
  exit 1
fi

echo
echo "▶ Starting monitoring stack (docker-compose v1)..."
docker-compose -f "$COMPOSE_FILE" up -d

echo
echo "== Detecting Docker network =="
NET="$(docker inspect scanium-grafana --format '{{range $k,$v := .NetworkSettings.Networks}}{{println $k}}{{end}}' | head -n1 || true)"
if [ -z "$NET" ]; then
  echo "❌ Could not detect Docker network from scanium-grafana."
  echo "   Is Grafana container running? Try: docker ps | grep scanium-grafana"
  exit 1
fi
echo "NET=$NET"
echo

check_http() {
  local name="$1"
  local url="$2"
  local expect="${3:-200}"

  local code="000"
  code="$(docker run --rm --network "$NET" curlimages/curl:8.6.0 -sS -o /dev/null -w "%{http_code}" "$url" || true)"
  if [ "$code" = "$expect" ]; then
    printf "✅ %-8s %s (%s)\n" "$name" "$url" "$code"
    return 0
  fi
  printf "⏳ %-8s %s (%s)\n" "$name" "$url" "$code"
  return 1
}

wait_for() {
  local name="$1"
  local url="$2"
  local expect="${3:-200}"
  local tries="${4:-60}"
  local sleep_s="${5:-2}"

  for i in $(seq 1 "$tries"); do
    if check_http "$name" "$url" "$expect"; then
      return 0
    fi
    echo "   retry $i/$tries..."
    sleep "$sleep_s"
  done

  echo "❌ Timeout waiting for $name to return HTTP $expect at $url"
  return 1
}

echo "== Waiting for core services (DNS + HTTP) =="
wait_for loki    "http://loki:3100/ready" 200 60 2
wait_for tempo   "http://tempo:3200/ready" 200 60 2
wait_for mimir   "http://mimir:9009/ready" 200 90 2
wait_for grafana "http://grafana:3000/api/health" 200 60 2

echo
echo "== Sanity check: Mimir query (up) =="
docker run --rm --network "$NET" curlimages/curl:8.6.0 -sS \
  "http://mimir:9009/prometheus/api/v1/query?query=up" | sed -n '1,120p' || true

echo
echo "============================================================"
echo "✅ Monitoring DEV stack is up and reachable."
echo
echo "Open:"
echo "  Grafana UI : http://<NAS-IP>:3000"
echo "  Alloy UI   : http://<NAS-IP>:12345"
echo
echo "Grafana Data Sources (use THESE URLs inside Grafana):"
echo "  Prometheus/Mimir : http://mimir:9009/prometheus"
echo "  Loki             : http://loki:3100"
echo "  Tempo            : http://tempo:3200"
echo "============================================================"
