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

# Make relative bind-mounts resolve correctly
COMPOSE_DIR="$(dirname "$COMPOSE_FILE")"
cd "$COMPOSE_DIR"

# Export so ${REPO_ROOT} in compose works
export REPO_ROOT="$REPO_ROOT"

echo
echo "== Pull images (best-effort) =="
docker-compose -f "$COMPOSE_FILE" pull || true

echo
echo "== Up (detached) =="
docker-compose -f "$COMPOSE_FILE" up -d

echo
echo "== Quick status =="
docker-compose -f "$COMPOSE_FILE" ps || true

echo
echo "== Wait for readiness (up to 60s; Grafana should be 200; Loki/Tempo may be 503 for ~15s) =="

NET="$(docker network ls --format '{{.Name}}' | grep -E 'compose_scanium_net|scanium_net' | head -n1 || true)"
echo "Network: ${NET:-<not found>}"

if [ -z "${NET:-}" ]; then
  echo "WARNING: No compose network found; skipping health checks."
  exit 0
fi

# Retry loop from inside the compose network (DNS works there now)
i=0
while [ "$i" -lt 12 ]; do
  i=$((i+1))
  echo
  echo "Attempt $i/12..."

  # Grafana health (should go 200 quickly)
  docker run --rm --network "$NET" curlimages/curl:8.6.0 -sS -o /dev/null -w "grafana=%{http_code}\n" \
    http://scanium-grafana:3000/api/health || true

  # /ready endpoints (may return 503 for a short warm-up)
  docker run --rm --network "$NET" curlimages/curl:8.6.0 -sS -o /dev/null -w "mimir=%{http_code}\n" \
    http://scanium-mimir:9009/ready || true
  docker run --rm --network "$NET" curlimages/curl:8.6.0 -sS -o /dev/null -w "loki=%{http_code}\n" \
    http://scanium-loki:3100/ready || true
  docker run --rm --network "$NET" curlimages/curl:8.6.0 -sS -o /dev/null -w "tempo=%{http_code}\n" \
    http://scanium-tempo:3200/ready || true

  # Exit early if all are 200
  codes="$(docker run --rm --network "$NET" curlimages/curl:8.6.0 -sS -o /dev/null -w "%{http_code} %{http_code} %{http_code} %{http_code}\n" \
    http://scanium-grafana:3000/api/health \
    http://scanium-mimir:9009/ready \
    http://scanium-loki:3100/ready \
    http://scanium-tempo:3200/ready || true)"

  # If curl failed, try again
  echo "codes: ${codes:-<none>}"
  echo "$codes" | grep -q '^200 200 200 200$' && {
    echo "✅ All healthy."
    exit 0
  }

  sleep 5
done

echo
echo "⚠️ Not all services returned 200 within the wait window."
echo "Tip: check logs: docker logs scanium-loki|scanium-mimir|scanium-tempo|scanium-alloy|scanium-grafana"
exit 0
