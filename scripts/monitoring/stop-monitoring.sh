***REMOVED***!/bin/bash

***REMOVED*** Scanium Monitoring Stack Stop Script
***REMOVED*** Stops LGTM stack (Loki, Grafana, Tempo, Mimir) + Alloy

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

print_status "Stopping monitoring stack..."

***REMOVED*** Change to monitoring directory
cd "$MONITORING_DIR"

***REMOVED*** Check if containers are running
RUNNING_CONTAINERS=$(docker compose -p "$PROJECT_NAME" ps -q 2>/dev/null | wc -l | tr -d ' ')

if [ "$RUNNING_CONTAINERS" -eq 0 ]; then
    print_status "Monitoring stack is not running"
else
    ***REMOVED*** Stop containers
    if docker compose -p "$PROJECT_NAME" down; then
        print_success "Monitoring stack stopped"
    else
        print_error "Failed to stop monitoring stack"
        return 1 2>/dev/null || exit 1
    fi
fi

***REMOVED*** Return to original directory
cd - > /dev/null
