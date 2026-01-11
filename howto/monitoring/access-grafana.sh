***REMOVED***!/usr/bin/env bash
***REMOVED*** Quick script to access Grafana via SSH tunnel

set -euo pipefail

PORT="${1:-3000}"
NAS_HOST="${2:-nas}"

echo "=== Grafana SSH Tunnel ==="
echo "Local port: $PORT"
echo "NAS host: $NAS_HOST"
echo

***REMOVED*** Check if port is already in use
if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "⚠️  Port $PORT is already in use!"
    echo
    echo "Options:"
    echo "  1. Use a different port: $0 8080"
    echo "  2. Kill the process using port $PORT:"
    lsof -Pi :$PORT -sTCP:LISTEN
    exit 1
fi

***REMOVED*** Check if NAS is reachable
if ! ssh -q -o ConnectTimeout=5 $NAS_HOST exit 2>/dev/null; then
    echo "❌ Cannot connect to $NAS_HOST via SSH"
    echo
    echo "Make sure:"
    echo "  1. SSH is configured: ~/.ssh/config has 'Host nas' entry"
    echo "  2. NAS is reachable: ping REDACTED_INTERNAL_IP"
    echo "  3. You can SSH manually: ssh $NAS_HOST"
    exit 1
fi

***REMOVED*** Check if Grafana is running on NAS
echo "Checking if Grafana is running on NAS..."
if ! ssh $NAS_HOST "/usr/local/bin/docker ps | grep -q scanium-grafana"; then
    echo "❌ Grafana container is not running on NAS!"
    echo
    echo "Start it with:"
    echo "  ssh $NAS_HOST 'cd /volume1/docker/scanium/repo/monitoring && /usr/local/bin/docker-compose up -d grafana'"
    exit 1
fi

echo "✅ Grafana is running on NAS"
echo

***REMOVED*** Create SSH tunnel
echo "Creating SSH tunnel..."
echo "  ssh -L $PORT:localhost:3000 $NAS_HOST"
echo
echo "✅ Tunnel created!"
echo
echo "📊 Access Grafana at: http://localhost:$PORT"
echo
echo "Quick links:"
echo "  • All Dashboards:   http://localhost:$PORT/dashboards"
echo "  • Backend Errors:   http://localhost:$PORT/d/scanium-backend-errors"
echo "  • System Overview:  http://localhost:$PORT/d/scanium-system-overview"
echo
echo "Press Ctrl+C to close the tunnel and exit"
echo

***REMOVED*** Open browser (optional)
if command -v open >/dev/null 2>&1; then
    echo "Opening browser..."
    sleep 2
    open "http://localhost:$PORT/dashboards" &
fi

***REMOVED*** Keep tunnel open
ssh -o ServerAliveInterval=60 -L $PORT:localhost:3000 $NAS_HOST
