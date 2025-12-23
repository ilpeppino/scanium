***REMOVED***!/bin/bash

***REMOVED*** Scanium Backend Development Startup Script
***REMOVED*** Starts PostgreSQL, Backend Server, and ngrok with error handling

set -eo pipefail  ***REMOVED*** Exit on error or pipeline failure

***REMOVED*** Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' ***REMOVED*** No Color

***REMOVED*** PID files for cleanup
BACKEND_PID=""
NGROK_PID=""

***REMOVED*** Cleanup function
cleanup() {
    echo ""
    echo -e "${YELLOW}🛑 Shutting down services...${NC}"

    if [ ! -z "$BACKEND_PID" ]; then
        echo -e "${BLUE}Stopping backend server (PID: $BACKEND_PID)${NC}"
        kill $BACKEND_PID 2>/dev/null || true
    fi

    if [ ! -z "$NGROK_PID" ]; then
        echo -e "${BLUE}Stopping ngrok (PID: $NGROK_PID)${NC}"
        kill $NGROK_PID 2>/dev/null || true
    fi

    echo -e "${GREEN}✅ Services stopped${NC}"
    exit 0
}

***REMOVED*** Set up trap for Ctrl+C
trap cleanup SIGINT SIGTERM

***REMOVED*** Function to print status
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

***REMOVED*** Banner
echo -e "${GREEN}"
echo "╔═══════════════════════════════════════════╗"
echo "║   Scanium Backend Development Startup    ║"
echo "╚═══════════════════════════════════════════╝"
echo -e "${NC}"

***REMOVED*** Change to backend directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/../../backend" && pwd)"
cd "$BACKEND_DIR"
print_status "Working directory: $BACKEND_DIR"
echo ""

***REMOVED*** 1. Check prerequisites
print_status "Checking prerequisites..."

***REMOVED*** Check Node.js
if ! command -v node &> /dev/null; then
    print_error "Node.js is not installed"
    exit 1
fi
NODE_VERSION=$(node --version)
print_success "Node.js $NODE_VERSION"

***REMOVED*** Check Docker/Colima
if ! command -v docker &> /dev/null; then
    print_error "Docker is not installed"
    exit 1
fi
print_success "Docker installed"

***REMOVED*** Check if using Colima
if command -v colima &> /dev/null; then
    COLIMA_STATUS=$(colima status 2>&1 || echo "stopped")
    if [[ $COLIMA_STATUS == *"colima is running"* ]]; then
        print_success "Colima is running"
    else
        print_warning "Colima is not running. Starting Colima..."
        colima start --cpu 4 --memory 8
        sleep 5
        print_success "Colima started"
    fi

    ***REMOVED*** Check Docker context
    CURRENT_CONTEXT=$(docker context show)
    if [ "$CURRENT_CONTEXT" != "colima" ]; then
        print_warning "Docker context is '$CURRENT_CONTEXT', switching to 'colima'"
        docker context use colima
        print_success "Switched to colima context"
    fi
fi

***REMOVED*** Check ngrok
if ! command -v ngrok &> /dev/null; then
    print_error "ngrok is not installed"
    echo "Install with: brew install ngrok/ngrok/ngrok"
    exit 1
fi
print_success "ngrok installed"

***REMOVED*** Check if ngrok is authenticated
if ! ngrok config check &> /dev/null; then
    print_warning "ngrok may not be authenticated"
    echo "Run: ngrok config add-authtoken YOUR_TOKEN"
fi

echo ""

***REMOVED*** 2. Check .env file
print_status "Checking .env file..."
if [ ! -f .env ]; then
    print_error ".env file not found"
    echo "Copy .env.example to .env and configure it"
    exit 1
fi
print_success ".env file exists"
echo ""

***REMOVED*** 3. Check if ports are available
print_status "Checking ports..."

if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    print_warning "Port 8080 is already in use"
    read -p "Kill the process? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        lsof -ti:8080 | xargs kill -9
        print_success "Killed process on port 8080"
        sleep 1
    else
        print_error "Cannot start backend on port 8080"
        exit 1
    fi
fi

echo ""

***REMOVED*** 4. Start PostgreSQL
print_status "Starting PostgreSQL..."

if docker ps --filter name=scanium-postgres --format '{{.Names}}' | grep -q scanium-postgres; then
    print_success "PostgreSQL is already running"
else
    docker compose up -d postgres
    print_status "Waiting for PostgreSQL to be ready..."

    ***REMOVED*** Wait for PostgreSQL to be healthy
    MAX_WAIT=30
    WAITED=0
    while [ $WAITED -lt $MAX_WAIT ]; do
        if docker exec scanium-postgres pg_isready -U scanium &> /dev/null; then
            print_success "PostgreSQL is ready"
            break
        fi
        sleep 1
        WAITED=$((WAITED + 1))
        echo -n "."
    done
    echo ""

    if [ $WAITED -eq $MAX_WAIT ]; then
        print_error "PostgreSQL failed to start"
        docker logs scanium-postgres --tail 20
        exit 1
    fi
fi

***REMOVED*** Verify PostgreSQL is accessible
if docker exec scanium-postgres psql -U scanium -d scanium -c "SELECT 1" &> /dev/null; then
    print_success "PostgreSQL connection verified"
else
    print_error "Cannot connect to PostgreSQL"
    exit 1
fi

echo ""

***REMOVED*** 5. Start Backend Server
print_status "Starting backend server..."

***REMOVED*** Start backend in background
npm run dev > .dev-server.log 2>&1 &
BACKEND_PID=$!

print_status "Backend server started (PID: $BACKEND_PID)"
print_status "Waiting for server to be ready..."

***REMOVED*** Wait for backend to be ready
MAX_WAIT=30
WAITED=0
while [ $WAITED -lt $MAX_WAIT ]; do
    if curl -s http://localhost:8080/healthz > /dev/null 2>&1; then
        print_success "Backend server is ready"
        break
    fi
    if ! kill -0 $BACKEND_PID 2>/dev/null; then
        print_error "Backend server crashed"
        echo "Last 20 lines of log:"
        tail -20 .dev-server.log
        exit 1
    fi
    sleep 1
    WAITED=$((WAITED + 1))
    echo -n "."
done
echo ""

if [ $WAITED -eq $MAX_WAIT ]; then
    print_error "Backend server failed to start"
    echo "Last 20 lines of log:"
    tail -20 .dev-server.log
    kill $BACKEND_PID 2>/dev/null
    exit 1
fi

***REMOVED*** Test health endpoint
HEALTH_RESPONSE=$(curl -s http://localhost:8080/healthz)
if [[ $HEALTH_RESPONSE == *"ok"* ]]; then
    print_success "Health check passed"
else
    print_error "Health check failed: $HEALTH_RESPONSE"
    kill $BACKEND_PID 2>/dev/null
    exit 1
fi

echo ""

***REMOVED*** 6. Start ngrok
print_status "Starting ngrok tunnel..."

***REMOVED*** Start ngrok in background
ngrok http 8080 --log=stdout > .ngrok.log 2>&1 &
NGROK_PID=$!

print_status "ngrok started (PID: $NGROK_PID)"
print_status "Waiting for ngrok tunnel..."

NGROK_API="http://127.0.0.1:4040/api/tunnels"
PUBLIC_URL="$(curl -fsS "$NGROK_API" | grep -oE 'https://[^"]+' | head -n 1 || true)"

if [ -n "$PUBLIC_URL" ]; then
  echo "✅ ngrok public URL: $PUBLIC_URL"
  echo "   Example health:   $PUBLIC_URL/health"
else
  echo "⚠️  ngrok started but public URL not found via $NGROK_API"
  echo "   Try: curl -s $NGROK_API"
fi


***REMOVED*** Wait for ngrok to start and get URL
MAX_WAIT=15
WAITED=0
NGROK_URL=""

while [ $WAITED -lt $MAX_WAIT ]; do
    if [ -f .ngrok.log ]; then
        NGROK_URL=$(grep -o 'https://[a-zA-Z0-9\-]*\.ngrok-free\.dev' .ngrok.log | head -1)
        if [ ! -z "$NGROK_URL" ]; then
            break
        fi
    fi

    if ! kill -0 $NGROK_PID 2>/dev/null; then
        print_error "ngrok crashed"
        echo "ngrok log:"
        cat .ngrok.log 2>/dev/null || echo "No log available"
        kill $BACKEND_PID 2>/dev/null
        exit 1
    fi

    sleep 1
    WAITED=$((WAITED + 1))
    echo -n "."
done
echo ""

if [ -z "$NGROK_URL" ]; then
    print_error "Failed to get ngrok URL"
    echo "ngrok log:"
    cat .ngrok.log 2>/dev/null || echo "No log available"
    kill $BACKEND_PID 2>/dev/null
    kill $NGROK_PID 2>/dev/null
    exit 1
fi

print_success "ngrok tunnel established"
echo ""

***REMOVED*** 7. Check if PUBLIC_BASE_URL needs updating
CURRENT_PUBLIC_URL=$(grep "^PUBLIC_BASE_URL=" .env | cut -d'=' -f2)

if [ "$CURRENT_PUBLIC_URL" != "$NGROK_URL" ]; then
    print_warning "ngrok URL has changed!"
    echo -e "${YELLOW}Old URL: $CURRENT_PUBLIC_URL${NC}"
    echo -e "${YELLOW}New URL: $NGROK_URL${NC}"
    echo ""
    echo -e "${RED}ACTION REQUIRED:${NC}"
    echo "1. Update .env file: PUBLIC_BASE_URL=$NGROK_URL"
    echo "2. Update eBay RuName redirect URL in developer portal"
    echo "3. Update mobile app SettingsScreen.kt with new URL"
    echo "4. Restart backend server (this script will exit)"
    echo ""
    read -p "Update .env automatically? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        ***REMOVED*** Backup .env
        cp .env .env.backup
        ***REMOVED*** Update PUBLIC_BASE_URL
        sed -i.tmp "s|^PUBLIC_BASE_URL=.*|PUBLIC_BASE_URL=$NGROK_URL|" .env
        rm .env.tmp 2>/dev/null || true
        print_success ".env updated (backup saved as .env.backup)"
        echo ""
        print_warning "Restarting backend server with new URL..."
        kill $BACKEND_PID 2>/dev/null
        sleep 2
        npm run dev > .dev-server.log 2>&1 &
        BACKEND_PID=$!
        sleep 3
        if curl -s http://localhost:8080/healthz > /dev/null 2>&1; then
            print_success "Backend server restarted"
        else
            print_error "Backend failed to restart"
            exit 1
        fi
    fi
fi

***REMOVED*** 8. Test ngrok endpoint
print_status "Testing ngrok endpoint..."
NGROK_HEALTH=$(curl -s "$NGROK_URL/healthz" 2>&1)
if [[ $NGROK_HEALTH == *"ok"* ]]; then
    print_success "ngrok tunnel working"
else
    print_error "ngrok tunnel not working: $NGROK_HEALTH"
fi

echo ""

***REMOVED*** 9. Display summary
echo -e "${GREEN}"
echo "╔═══════════════════════════════════════════╗"
echo "║          Services Running                 ║"
echo "╚═══════════════════════════════════════════╝"
echo -e "${NC}"

echo -e "${BLUE}PostgreSQL:${NC}      localhost:5432 (Container: scanium-postgres)"
echo -e "${BLUE}Backend Server:${NC}  http://localhost:8080 (PID: $BACKEND_PID)"
echo -e "${BLUE}ngrok Tunnel:${NC}    $NGROK_URL (PID: $NGROK_PID)"
echo ""

echo -e "${GREEN}╔═══════════════════════════════════════════╗"
echo "║          Quick Test Commands              ║"
echo "╚═══════════════════════════════════════════╝${NC}"
echo ""
echo "Health check:"
echo "  curl http://localhost:8080/healthz"
echo ""
echo "eBay OAuth start:"
echo "  curl -X POST $NGROK_URL/auth/ebay/start"
echo ""
echo "eBay connection status:"
echo "  curl $NGROK_URL/auth/ebay/status"
echo ""
echo "View backend logs:"
echo "  tail -f .dev-server.log"
echo ""
echo "View ngrok logs:"
echo "  tail -f .ngrok.log"
echo ""
echo "ngrok web interface:"
echo "  http://localhost:4040"
echo ""

echo -e "${YELLOW}╔═══════════════════════════════════════════╗"
echo "║          Mobile App Configuration         ║"
echo "╚═══════════════════════════════════════════╝${NC}"
echo ""
echo "Update your mobile app with this URL:"
echo -e "${GREEN}$NGROK_URL${NC}"
echo ""
echo "In SettingsScreen.kt, update:"
echo "  ScaniumApi(\"$NGROK_URL\")"
echo ""

if [ "$CURRENT_PUBLIC_URL" != "$NGROK_URL" ]; then
    echo -e "${RED}╔═══════════════════════════════════════════╗"
    echo "║          eBay RuName Update Needed        ║"
    echo "╚═══════════════════════════════════════════╝${NC}"
    echo ""
    echo "1. Go to: https://developer.ebay.com/my/keys"
    echo "2. Update RuName redirect URL to:"
    echo -e "   ${GREEN}$NGROK_URL/auth/ebay/callback${NC}"
    echo ""
fi

echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"
echo ""

***REMOVED*** Wait for user to stop
wait $BACKEND_PID $NGROK_PID
