#!/bin/bash

# Scanium Backend & Monitoring Stack Status Checker
# Checks health of all services and displays URLs for manual verification

set -eo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
BOLD='\033[1m'
DIM='\033[2m'
NC='\033[0m' # No Color

# Status indicators
HEALTHY="${GREEN}●${NC}"
UNHEALTHY="${RED}●${NC}"
STARTING="${YELLOW}●${NC}"
UNKNOWN="${DIM}○${NC}"

# Print functions
print_header() {
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BOLD}${CYAN}  $1${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

print_section() {
    echo ""
    echo -e "${MAGENTA}▸ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}! $1${NC}"
}

print_info() {
    echo -e "${DIM}  $1${NC}"
}

# Health check via HTTP endpoint
check_http() {
    local url=$1
    local timeout=${2:-3}
    local status_code

    status_code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout "$timeout" "$url" 2>/dev/null || echo "000")

    if [[ "$status_code" =~ ^(200|204|302)$ ]]; then
        echo "healthy"
    elif [[ "$status_code" == "000" ]]; then
        echo "unreachable"
    else
        echo "unhealthy:$status_code"
    fi
}

# Check TCP port connectivity
check_port() {
    local host=$1
    local port=$2
    local timeout=${3:-2}

    if timeout "$timeout" bash -c "</dev/tcp/$host/$port" 2>/dev/null; then
        echo "open"
    else
        echo "closed"
    fi
}

# Get Docker container health status
get_container_health() {
    local container=$1
    local result
    result=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null) || result="not_found"
    echo "$result" | tr -d '\n'
}

# Get Docker container running status
get_container_status() {
    local container=$1
    local result
    result=$(docker inspect --format='{{.State.Status}}' "$container" 2>/dev/null) || result="not_found"
    echo "$result" | tr -d '\n'
}

# Format status with color
format_status() {
    local status=$1
    case "$status" in
        healthy|running|open)
            echo -e "${HEALTHY} ${GREEN}$status${NC}"
            ;;
        starting)
            echo -e "${STARTING} ${YELLOW}$status${NC}"
            ;;
        unhealthy*|exited|dead|closed|unreachable)
            echo -e "${UNHEALTHY} ${RED}$status${NC}"
            ;;
        not_found)
            echo -e "${UNKNOWN} ${DIM}not running${NC}"
            ;;
        *)
            echo -e "${UNKNOWN} ${DIM}$status${NC}"
            ;;
    esac
}

# Counters for summary
TOTAL_CHECKS=0
HEALTHY_CHECKS=0
UNHEALTHY_CHECKS=0

# Track check result
track_check() {
    local status=$1
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    if [[ "$status" == "healthy" || "$status" == "running" || "$status" == "open" ]]; then
        HEALTHY_CHECKS=$((HEALTHY_CHECKS + 1))
    else
        UNHEALTHY_CHECKS=$((UNHEALTHY_CHECKS + 1))
    fi
}

# Main script
echo ""
echo -e "${GREEN}╔═══════════════════════════════════════════════════════════════════════════╗"
echo -e "║                    ${BOLD}Scanium Stack Health Check${NC}${GREEN}                            ║"
echo -e "╚═══════════════════════════════════════════════════════════════════════════╝${NC}"
echo -e "${DIM}  $(date '+%Y-%m-%d %H:%M:%S')${NC}"

# ============================================================================
# Backend Stack
# ============================================================================
print_header "Backend Stack"

print_section "PostgreSQL Database"
PG_STATUS=$(get_container_health "scanium-postgres")
track_check "$PG_STATUS"
echo -e "  Container:    $(format_status "$PG_STATUS")"
PG_PORT=$(check_port "localhost" 5432)
track_check "$PG_PORT"
echo -e "  Port 5432:    $(format_status "$PG_PORT")"
echo ""
echo -e "  ${BLUE}Manual check:${NC}"
print_info "psql -h localhost -U scanium -d scanium -c 'SELECT 1'"

print_section "Scanium API"
API_CONTAINER=$(get_container_health "scanium-api")
track_check "$API_CONTAINER"
echo -e "  Container:    $(format_status "$API_CONTAINER")"

# Check various health endpoints
API_HEALTH=$(check_http "http://localhost:8080/health")
track_check "$API_HEALTH"
echo -e "  /health:      $(format_status "$API_HEALTH")"

API_HEALTHZ=$(check_http "http://localhost:8080/healthz")
track_check "$API_HEALTHZ"
echo -e "  /healthz:     $(format_status "$API_HEALTHZ")"

API_READYZ=$(check_http "http://localhost:8080/readyz")
track_check "$API_READYZ"
echo -e "  /readyz:      $(format_status "$API_READYZ")"

echo ""
echo -e "  ${BLUE}Health Check URLs:${NC}"
print_info "curl http://localhost:8080/health   # Full health with assistant status"
print_info "curl http://localhost:8080/healthz  # Basic liveness probe"
print_info "curl http://localhost:8080/readyz   # Readiness (includes DB check)"

print_section "Cloudflare Tunnel"
CF_STATUS=$(get_container_status "scanium-cloudflared")
track_check "$CF_STATUS"
echo -e "  Container:    $(format_status "$CF_STATUS")"
print_info "Tunnel status is managed by Cloudflare dashboard"

# ============================================================================
# Monitoring Stack
# ============================================================================
print_header "Monitoring Stack (LGTM + Alloy)"

print_section "Grafana (Dashboards & Visualization)"
GRAFANA_STATUS=$(get_container_health "scanium-grafana")
track_check "$GRAFANA_STATUS"
echo -e "  Container:    $(format_status "$GRAFANA_STATUS")"
GRAFANA_HTTP=$(check_http "http://localhost:3000/api/health")
track_check "$GRAFANA_HTTP"
echo -e "  HTTP:         $(format_status "$GRAFANA_HTTP")"
echo ""
echo -e "  ${BLUE}URLs:${NC}"
print_info "Dashboard:  http://localhost:3000"
print_info "Health:     http://localhost:3000/api/health"

print_section "Alloy (OTLP Receiver)"
ALLOY_STATUS=$(get_container_health "scanium-alloy")
track_check "$ALLOY_STATUS"
echo -e "  Container:    $(format_status "$ALLOY_STATUS")"
ALLOY_GRPC=$(check_port "localhost" 4317)
track_check "$ALLOY_GRPC"
echo -e "  OTLP gRPC:    $(format_status "$ALLOY_GRPC") (port 4317)"
ALLOY_HTTP=$(check_port "localhost" 4318)
track_check "$ALLOY_HTTP"
echo -e "  OTLP HTTP:    $(format_status "$ALLOY_HTTP") (port 4318)"
ALLOY_UI=$(check_http "http://localhost:12345/ready")
track_check "$ALLOY_UI"
echo -e "  Admin UI:     $(format_status "$ALLOY_UI")"
echo ""
echo -e "  ${BLUE}URLs:${NC}"
print_info "Admin UI:   http://localhost:12345"
print_info "OTLP gRPC:  localhost:4317"
print_info "OTLP HTTP:  http://localhost:4318"

print_section "Loki (Log Storage)"
LOKI_STATUS=$(get_container_health "scanium-loki")
track_check "$LOKI_STATUS"
echo -e "  Container:    $(format_status "$LOKI_STATUS")"
LOKI_HTTP=$(check_http "http://localhost:3100/ready")
track_check "$LOKI_HTTP"
echo -e "  Ready:        $(format_status "$LOKI_HTTP")"
echo ""
echo -e "  ${BLUE}URLs:${NC}"
print_info "Ready:      http://localhost:3100/ready"
print_info "Metrics:    http://localhost:3100/metrics"

print_section "Tempo (Trace Storage)"
TEMPO_STATUS=$(get_container_health "scanium-tempo")
track_check "$TEMPO_STATUS"
echo -e "  Container:    $(format_status "$TEMPO_STATUS")"
TEMPO_HTTP=$(check_http "http://localhost:3200/ready")
track_check "$TEMPO_HTTP"
echo -e "  Ready:        $(format_status "$TEMPO_HTTP")"
echo ""
echo -e "  ${BLUE}URLs:${NC}"
print_info "Ready:      http://localhost:3200/ready"
print_info "Status:     http://localhost:3200/status"

print_section "Mimir (Metrics Storage)"
MIMIR_STATUS=$(get_container_health "scanium-mimir")
track_check "$MIMIR_STATUS"
echo -e "  Container:    $(format_status "$MIMIR_STATUS")"
MIMIR_HTTP=$(check_http "http://localhost:9009/ready")
track_check "$MIMIR_HTTP"
echo -e "  Ready:        $(format_status "$MIMIR_HTTP")"
echo ""
echo -e "  ${BLUE}URLs:${NC}"
print_info "Ready:      http://localhost:9009/ready"
print_info "Config:     http://localhost:9009/config"

# ============================================================================
# Summary
# ============================================================================
print_header "Summary"

echo ""
if [ $UNHEALTHY_CHECKS -eq 0 ]; then
    echo -e "  ${GREEN}${BOLD}All systems operational${NC}"
    echo -e "  ${GREEN}✓${NC} ${HEALTHY_CHECKS}/${TOTAL_CHECKS} checks passed"
else
    echo -e "  ${YELLOW}${BOLD}Some services need attention${NC}"
    echo -e "  ${GREEN}✓${NC} ${HEALTHY_CHECKS} healthy  ${RED}✗${NC} ${UNHEALTHY_CHECKS} unhealthy"
fi

echo ""
echo -e "${DIM}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "${BOLD}Quick Commands:${NC}"
echo ""
echo -e "  ${CYAN}Start backend:${NC}     scripts/backend/start-dev.sh"
echo -e "  ${CYAN}Stop backend:${NC}      scripts/backend/stop-dev.sh"
echo -e "  ${CYAN}Start monitoring:${NC}  scripts/monitoring/start-monitoring.sh"
echo -e "  ${CYAN}Stop monitoring:${NC}   scripts/monitoring/stop-monitoring.sh"
echo ""
echo -e "  ${CYAN}View backend logs:${NC}"
echo -e "    docker logs -f scanium-api"
echo -e "    docker logs -f scanium-postgres"
echo ""
echo -e "  ${CYAN}View monitoring logs:${NC}"
echo -e "    docker compose -p scanium-monitoring logs -f"
echo ""

# Exit with error code if any checks failed
if [ $UNHEALTHY_CHECKS -gt 0 ]; then
    exit 1
fi
