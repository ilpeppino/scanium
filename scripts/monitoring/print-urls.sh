#!/bin/bash

# Scanium Monitoring Stack - Print Access URLs
# Displays URLs, health status, and quick tips

set -eo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Print functions
print_status() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

# Get script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
MONITORING_DIR="$(cd "${SCRIPT_DIR}/../../monitoring" && pwd)"
PROJECT_NAME="scanium-monitoring"

# Detect local IP address for LAN access (useful for testing from mobile devices)
detect_lan_ip() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        LAN_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "")
    else
        # Linux
        LAN_IP=$(hostname -I 2>/dev/null | awk '{print $1}' || echo "")
    fi
    echo "$LAN_IP"
}

# Health check function
check_endpoint() {
    local url=$1
    local name=$2
    if curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null | grep -qE "^(200|302|401)$"; then
        echo -e "${GREEN}✓${NC}"
    else
        echo -e "${RED}✗${NC}"
    fi
}

# Check if monitoring stack is running
RUNNING=$(docker compose -p "$PROJECT_NAME" ps -q 2>/dev/null | wc -l | tr -d ' ')

if [ "$RUNNING" -eq 0 ]; then
    print_warning "Monitoring stack is not running"
    echo "Start with: scripts/monitoring/start-monitoring.sh"
    echo "Or use: scripts/backend/start-dev.sh (includes monitoring)"
    return 1 2>/dev/null || exit 1
fi

LAN_IP=$(detect_lan_ip)

echo ""
echo -e "${GREEN}╔═══════════════════════════════════════════════════════════════╗"
echo "║          Scanium Observability Stack (LGTM + Alloy)          ║"
echo "╚═══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Main dashboard access
echo -e "${CYAN}━━━ Grafana Dashboards ━━━${NC}"
GRAFANA_STATUS=$(check_endpoint "http://localhost:3000/api/health" "Grafana")
echo -e "  $GRAFANA_STATUS Grafana UI:       ${BLUE}http://localhost:3000${NC}"
if [ -n "$LAN_IP" ]; then
    echo -e "                       ${BLUE}http://${LAN_IP}:3000${NC} (LAN access)"
fi
echo -e "     ${YELLOW}ℹ${NC}  Anonymous login enabled (dev mode - no credentials needed)"
echo -e "     ${YELLOW}ℹ${NC}  Dashboards provisioned from: monitoring/grafana/dashboards/"
echo ""

# OTLP ingestion endpoints
echo -e "${CYAN}━━━ OTLP Telemetry Ingestion (for apps) ━━━${NC}"
ALLOY_GRPC_STATUS=$(bash -c "timeout 1 bash -c '</dev/tcp/localhost/4317' &>/dev/null && echo -e '${GREEN}✓${NC}' || echo -e '${RED}✗${NC}'")
ALLOY_HTTP_STATUS=$(check_endpoint "http://localhost:4318" "Alloy HTTP")
echo -e "  $ALLOY_GRPC_STATUS OTLP gRPC:       ${BLUE}localhost:4317${NC}"
if [ -n "$LAN_IP" ]; then
    echo -e "                       ${BLUE}${LAN_IP}:4317${NC} (LAN access)"
fi
echo -e "  $ALLOY_HTTP_STATUS OTLP HTTP:       ${BLUE}http://localhost:4318${NC}"
if [ -n "$LAN_IP" ]; then
    echo -e "                       ${BLUE}http://${LAN_IP}:4318${NC} (LAN access)"
fi
echo -e "     ${YELLOW}ℹ${NC}  Send traces, metrics, logs from your app to these endpoints"
echo ""

# Backend storage endpoints (localhost only)
echo -e "${CYAN}━━━ Backend Storage (internal/debugging) ━━━${NC}"
LOKI_STATUS=$(check_endpoint "http://localhost:3100/ready" "Loki")
TEMPO_STATUS=$(check_endpoint "http://localhost:3200/ready" "Tempo")
MIMIR_STATUS=$(check_endpoint "http://localhost:9009/ready" "Mimir")
echo -e "  $LOKI_STATUS Loki (logs):      ${BLUE}http://localhost:3100${NC}"
echo -e "  $TEMPO_STATUS Tempo (traces):   ${BLUE}http://localhost:3200${NC}"
echo -e "  $MIMIR_STATUS Mimir (metrics):  ${BLUE}http://localhost:9009${NC}"
echo -e "     ${YELLOW}ℹ${NC}  These are accessed via Grafana datasources (no direct UI)"
echo ""

# Alloy UI (localhost only)
echo -e "${CYAN}━━━ Alloy Admin UI (localhost only) ━━━${NC}"
ALLOY_UI_STATUS=$(check_endpoint "http://localhost:12345" "Alloy UI")
echo -e "  $ALLOY_UI_STATUS Alloy UI:        ${BLUE}http://localhost:12345${NC}"
echo -e "     ${YELLOW}ℹ${NC}  View Alloy configuration and internal metrics"
echo ""

# Management commands
echo -e "${GREEN}╔═══════════════════════════════════════════════════════════════╗"
echo "║          Management Commands                                  ║"
echo "╚═══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "View all container status:"
echo "  ${BLUE}docker compose -p $PROJECT_NAME ps${NC}"
echo ""
echo "View logs (all services):"
echo "  ${BLUE}docker compose -p $PROJECT_NAME logs -f${NC}"
echo ""
echo "View logs (specific service):"
echo "  ${BLUE}docker compose -p $PROJECT_NAME logs -f grafana${NC}"
echo "  ${BLUE}docker compose -p $PROJECT_NAME logs -f alloy${NC}"
echo ""
echo "Restart a service:"
echo "  ${BLUE}docker compose -p $PROJECT_NAME restart grafana${NC}"
echo ""
echo "Stop monitoring stack:"
echo "  ${BLUE}scripts/monitoring/stop-monitoring.sh${NC}"
echo "  ${BLUE}docker compose -p $PROJECT_NAME down${NC}"
echo ""

# Health summary
echo -e "${GREEN}╔═══════════════════════════════════════════════════════════════╗"
echo "║          Quick Health Check                                   ║"
echo "╚═══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Count healthy services
TOTAL_SERVICES=5
HEALTHY_COUNT=0

for service in grafana alloy loki tempo mimir; do
    HEALTH=$(docker inspect --format='{{.State.Health.Status}}' "scanium-$service" 2>/dev/null || echo "unknown")
    if [ "$HEALTH" = "healthy" ]; then
        echo -e "  ${GREEN}✓${NC} $service is healthy"
        HEALTHY_COUNT=$((HEALTHY_COUNT + 1))
    elif [ "$HEALTH" = "starting" ]; then
        echo -e "  ${YELLOW}⚡${NC} $service is starting..."
    else
        echo -e "  ${RED}✗${NC} $service is $HEALTH"
    fi
done

echo ""
if [ $HEALTHY_COUNT -eq $TOTAL_SERVICES ]; then
    print_success "All services healthy ($HEALTHY_COUNT/$TOTAL_SERVICES)"
else
    print_warning "Some services not ready ($HEALTHY_COUNT/$TOTAL_SERVICES healthy)"
    echo "Check logs with: docker compose -p $PROJECT_NAME logs"
fi
echo ""

# Data persistence note
echo -e "${YELLOW}╔═══════════════════════════════════════════════════════════════╗"
echo "║          Data Persistence                                     ║"
echo "╚═══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "Data stored in: ${BLUE}$MONITORING_DIR/data/${NC}"
echo "  - grafana/ (dashboards, users, settings)"
echo "  - loki/    (log data)"
echo "  - tempo/   (trace data)"
echo "  - mimir/   (metrics data)"
echo ""
echo "To reset all data: ${RED}rm -rf $MONITORING_DIR/data/*${NC}"
echo ""
