***REMOVED***!/bin/bash

***REMOVED*** Scanium Backend Development Stop Script
***REMOVED*** Gracefully stops all development services

***REMOVED*** Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' ***REMOVED*** No Color

print_status() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

echo -e "${YELLOW}"
echo "╔═══════════════════════════════════════════╗"
echo "║   Stopping Scanium Development Services  ║"
echo "╚═══════════════════════════════════════════╝"
echo -e "${NC}"
echo ""

***REMOVED*** Stop backend server (port 8080)
print_status "Stopping backend server..."
BACKEND_PIDS=$(lsof -ti:8080 2>/dev/null)
if [ ! -z "$BACKEND_PIDS" ]; then
    echo "$BACKEND_PIDS" | xargs kill 2>/dev/null || true
    sleep 1
    ***REMOVED*** Force kill if still running
    BACKEND_PIDS=$(lsof -ti:8080 2>/dev/null)
    if [ ! -z "$BACKEND_PIDS" ]; then
        echo "$BACKEND_PIDS" | xargs kill -9 2>/dev/null || true
    fi
    print_success "Backend server stopped"
else
    print_warning "Backend server not running"
fi

***REMOVED*** Stop ngrok
print_status "Stopping ngrok..."
NGROK_PIDS=$(pgrep -f "ngrok http" 2>/dev/null)
if [ ! -z "$NGROK_PIDS" ]; then
    echo "$NGROK_PIDS" | xargs kill 2>/dev/null || true
    sleep 1
    ***REMOVED*** Force kill if still running
    NGROK_PIDS=$(pgrep -f "ngrok http" 2>/dev/null)
    if [ ! -z "$NGROK_PIDS" ]; then
        echo "$NGROK_PIDS" | xargs kill -9 2>/dev/null || true
    fi
    print_success "ngrok stopped"
else
    print_warning "ngrok not running"
fi

***REMOVED*** Stop PostgreSQL
print_status "Stopping PostgreSQL..."
if docker ps --filter name=scanium-postgres --format '{{.Names}}' | grep -q scanium-postgres; then
    cd "$(dirname "$0")"
    docker compose down
    print_success "PostgreSQL stopped"
else
    print_warning "PostgreSQL not running"
fi

***REMOVED*** Clean up log files (optional)
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
