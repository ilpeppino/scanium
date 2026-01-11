#!/usr/bin/env bash
# Access Grafana from mobile via Tailscale + SSH tunnel
# This creates a tunnel that binds to all interfaces (0.0.0.0)
# allowing mobile devices to connect via Mac's Tailscale IP

set -euo pipefail

PORT="${1:-3000}"
NAS_HOST="${2:-nas}"

echo "=== Grafana SSH Tunnel for Tailscale Access ==="
echo

# Get Tailscale IP
TAILSCALE_IP=$(tailscale ip -4 2>/dev/null || echo "")
if [ -z "$TAILSCALE_IP" ]; then
    echo "⚠️  Tailscale IP not found. Make sure Tailscale is running."
    echo "Run: tailscale status"
    exit 1
fi

echo "Mac Tailscale IP: $TAILSCALE_IP"
echo "Tunnel port: $PORT"
echo "NAS host: $NAS_HOST"
echo

# Check if port is already in use
if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "⚠️  Port $PORT is already in use!"
    echo
    echo "Existing process:"
    lsof -Pi :$PORT -sTCP:LISTEN
    echo
    echo "Options:"
    echo "  1. Kill existing tunnel: pkill -f 'ssh.*$PORT:localhost:3000'"
    echo "  2. Use different port: $0 8080"
    exit 1
fi

# Check if NAS is reachable
if ! ssh -q -o ConnectTimeout=5 $NAS_HOST exit 2>/dev/null; then
    echo "❌ Cannot connect to $NAS_HOST via SSH"
    echo
    echo "Make sure SSH is configured and NAS is reachable"
    exit 1
fi

# Check if Grafana is running on NAS
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

# Create SSH tunnel bound to 0.0.0.0 (all interfaces)
echo "Creating SSH tunnel (binding to all interfaces)..."
echo "  ssh -L 0.0.0.0:$PORT:localhost:3000 $NAS_HOST"
echo
echo "✅ Tunnel created!"
echo
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📱 From your MOBILE browser, visit:"
echo
echo "    http://$TAILSCALE_IP:$PORT"
echo
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo
echo "Quick links (replace IP in your mobile browser):"
echo "  • All Dashboards:   http://$TAILSCALE_IP:$PORT/dashboards"
echo "  • Backend Errors:   http://$TAILSCALE_IP:$PORT/d/scanium-backend-errors"
echo "  • System Overview:  http://$TAILSCALE_IP:$PORT/d/scanium-system-overview"
echo
echo "💡 Bookmark this URL on your mobile device!"
echo
echo "Press Ctrl+C to close the tunnel and exit"
echo

# Keep tunnel open with keepalive
ssh -o ServerAliveInterval=60 -L 0.0.0.0:$PORT:localhost:3000 $NAS_HOST
