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
echo

COMPOSE_DIR="$(dirname "$COMPOSE_FILE")"
cd "$COMPOSE_DIR"
export REPO_ROOT="$REPO_ROOT"

echo "== docker-compose ps =="
docker-compose -f "$COMPOSE_FILE" ps || true
echo

echo "== docker ps (scanium monitoring) =="
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | egrep "scanium-(grafana|loki|mimir|tempo|alloy)" || true
echo

NET="$(docker network ls --format '{{.Name}}' | grep -E 'compose_scanium_net|scanium_net' | head -n1 || true)"
echo "== Network =="; echo "${NET:-<not found>}"
echo

if [ -z "${NET:-}" ]; then
  echo "WARNING: No compose network found; skipping health checks."
  exit 0
fi

echo "== DNS inside network (one-off container) =="
docker run --rm --network "$NET" alpine:3.19 sh -lc '
  apk add --no-cache bind-tools >/dev/null
  for h in scanium-grafana scanium-loki scanium-mimir scanium-tempo scanium-alloy; do
    echo "--- $h"; nslookup "$h" || true
  done
' || true
echo

echo "== Health checks (note: /ready may be 503 briefly after start) =="
docker run --rm --network "$NET" curlimages/curl:8.6.0 -sS -i http://scanium-grafana:3000/api/health | head -n 20 || true
echo
docker run --rm --network "$NET" curlimages/curl:8.6.0 -sS -i http://scanium-mimir:9009/ready | head -n 12 || true
echo
docker run --rm --network "$NET" curlimages/curl:8.6.0 -sS -i http://scanium-loki:3100/ready  | head -n 12 || true
echo
docker run --rm --network "$NET" curlimages/curl:8.6.0 -sS -i http://scanium-tempo:3200/ready | head -n 12 || true
