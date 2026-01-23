#!/bin/bash

# Scanium Backend Development Stop Script
# Gracefully stops all development services

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

# Parse command-line flags
STOP_MONITORING=0

for arg in "$@"; do
    case $arg in
        --with-monitoring)
            STOP_MONITORING=1
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --with-monitoring     Also stop monitoring stack"
            echo "  -h, --help           Show this help message"
            echo ""
            exit 0
            ;;
        *)
            echo "Unknown option: $arg"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

echo -e "${YELLOW}"
echo "╔═══════════════════════════════════════════╗"
echo "║   Stopping Scanium Development Services  ║"
echo "╚═══════════════════════════════════════════╝"
echo -e "${NC}"
echo ""

# Stop backend server (port 8080)
print_status "Stopping backend server..."
BACKEND_PIDS=$(lsof -ti:8080 2>/dev/null)
if [ ! -z "$BACKEND_PIDS" ]; then
    echo "$BACKEND_PIDS" | xargs kill 2>/dev/null || true
    sleep 1
    # Force kill if still running
    BACKEND_PIDS=$(lsof -ti:8080 2>/dev/null)
    if [ ! -z "$BACKEND_PIDS" ]; then
        echo "$BACKEND_PIDS" | xargs kill -9 2>/dev/null || true
    fi
    print_success "Backend server stopped"
else
    print_warning "Backend server not running"
fi

# Stop ngrok
print_status "Stopping ngrok..."
NGROK_PIDS=$(pgrep -f "ngrok http" 2>/dev/null)
if [ ! -z "$NGROK_PIDS" ]; then
    echo "$NGROK_PIDS" | xargs kill 2>/dev/null || true
    sleep 1
    # Force kill if still running
    NGROK_PIDS=$(pgrep -f "ngrok http" 2>/dev/null)
    if [ ! -z "$NGROK_PIDS" ]; then
        echo "$NGROK_PIDS" | xargs kill -9 2>/dev/null || true
    fi
    print_success "ngrok stopped"
else
    print_warning "ngrok not running"
fi

# Stop PostgreSQL
print_status "Stopping PostgreSQL..."
if docker ps --filter name=scanium-postgres --format '{{.Names}}' | grep -q scanium-postgres; then
    cd "$(dirname "$0")"
    docker compose down
    print_success "PostgreSQL stopped"
else
    print_warning "PostgreSQL not running"
fi

# Stop monitoring stack (if requested)
if [ "$STOP_MONITORING" = "1" ]; then
    SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
    MONITORING_SCRIPT="${SCRIPT_DIR}/../monitoring/stop-monitoring.sh"

    if [ -f "$MONITORING_SCRIPT" ]; then
        bash "$MONITORING_SCRIPT"
    else
        print_warning "Monitoring stop script not found: $MONITORING_SCRIPT"
    fi
else
    # Check if monitoring is running and inform user
    MONITORING_RUNNING=$(docker compose -p scanium-monitoring ps -q 2>/dev/null | wc -l | tr -d ' ')
    if [ "$MONITORING_RUNNING" -gt 0 ]; then
        print_status "Monitoring stack is still running"
        echo "  To stop: $0 --with-monitoring"
        echo "  Or run: scripts/monitoring/stop-monitoring.sh"
    fi
fi

# Clean up log files (optional)
if [ -f .dev-server.log ] || [ -f .ngrok.log ]; then
    read -p "Delete log files? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        rm -f .dev-server.log .ngrok.log
        print_success "Log files deleted"
    fi
fi

echo ""
echo -e "${GREEN}✅ All services stopped${NC}"
