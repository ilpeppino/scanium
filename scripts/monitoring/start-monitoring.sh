***REMOVED***!/bin/bash

***REMOVED*** Scanium Monitoring Stack Startup Script
***REMOVED*** Starts LGTM stack (Loki, Grafana, Tempo, Mimir) + Alloy

set -eo pipefail

***REMOVED*** Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' ***REMOVED*** No Color

***REMOVED*** Print functions
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

***REMOVED*** Get script directory and monitoring directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
MONITORING_DIR="$(cd "${SCRIPT_DIR}/../../monitoring" && pwd)"
PROJECT_NAME="scanium-monitoring"

***REMOVED*** Check if Docker is available
if ! command -v docker &> /dev/null; then
    print_error "Docker is not installed or not in PATH"
    echo "Monitoring stack requires Docker. Skipping."
    return 1 2>/dev/null || exit 1
fi

***REMOVED*** Check if Docker daemon is running
if ! docker info &> /dev/null; then
    print_error "Docker daemon is not running"
    echo "Start Docker Desktop or Colima and try again."
    return 1 2>/dev/null || exit 1
fi

print_status "Starting monitoring stack (LGTM + Alloy)..."

***REMOVED*** Change to monitoring directory
cd "$MONITORING_DIR"

***REMOVED*** Check if containers are already running
RUNNING_CONTAINERS=$(docker compose -p "$PROJECT_NAME" ps -q 2>/dev/null | wc -l | tr -d ' ')

if [ "$RUNNING_CONTAINERS" -gt 0 ]; then
    print_status "Monitoring stack is already running"
    ***REMOVED*** Check if all services are healthy
    UNHEALTHY=$(docker compose -p "$PROJECT_NAME" ps --filter "health=unhealthy" -q 2>/dev/null | wc -l | tr -d ' ')
    if [ "$UNHEALTHY" -gt 0 ]; then
        print_warning "Some services are unhealthy. Run: docker compose -p $PROJECT_NAME ps"
    fi
else
    ***REMOVED*** Start containers in detached mode
    if docker compose -p "$PROJECT_NAME" up -d; then
        print_success "Monitoring containers started"
    else
        print_error "Failed to start monitoring containers"
        echo "Run: docker compose -p $PROJECT_NAME logs"
        return 1 2>/dev/null || exit 1
    fi
fi

***REMOVED*** Wait for services to be healthy
print_status "Waiting for services to be ready..."

***REMOVED*** Function to check if a service is healthy
check_service_health() {
    local service=$1
    local max_wait=${2:-30}
    local waited=0

    while [ $waited -lt $max_wait ]; do
        HEALTH=$(docker inspect --format='{{.State.Health.Status}}' "scanium-$service" 2>/dev/null || echo "starting")
        if [ "$HEALTH" = "healthy" ]; then
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
        echo -n "."
    done
    echo ""
    return 1
}

***REMOVED*** Check Grafana (most important for user)
if check_service_health "grafana" 45; then
    print_success "Grafana is ready"
else
    print_warning "Grafana health check timeout (may still be starting)"
fi

***REMOVED*** Check Alloy
if check_service_health "alloy" 30; then
    print_success "Alloy is ready"
else
    print_warning "Alloy health check timeout (may still be starting)"
fi

***REMOVED*** Quick check for other services (don't block on them)
for service in loki tempo mimir; do
    HEALTH=$(docker inspect --format='{{.State.Health.Status}}' "scanium-$service" 2>/dev/null || echo "unknown")
    if [ "$HEALTH" = "healthy" ]; then
        print_success "$service is ready"
    else
        print_status "$service is $HEALTH"
    fi
done

echo ""
print_success "Monitoring stack startup complete"
echo ""

***REMOVED*** Return to original directory
cd - > /dev/null
