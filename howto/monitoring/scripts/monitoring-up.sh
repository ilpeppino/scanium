#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="deploy/nas/compose/docker-compose.nas.monitoring.yml"

echo "▶ Starting monitoring stack..."
docker-compose -f "$COMPOSE_FILE" up -d

echo
echo "✔ Monitoring stack started"
echo
echo "Access points:"
echo "  Grafana : http://<NAS-IP>:3000"
echo "  Alloy   : http://<NAS-IP>:12345"
echo
echo "Tip: first start may take ~30s for Mimir to become fully ready."
