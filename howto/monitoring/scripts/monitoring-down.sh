#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="deploy/nas/compose/docker-compose.nas.monitoring.yml"

echo "■ Stopping monitoring stack..."
docker-compose -f "$COMPOSE_FILE" down

echo
echo "✔ Monitoring stack stopped"
