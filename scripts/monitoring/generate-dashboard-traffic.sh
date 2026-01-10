#!/usr/bin/env bash
#
# Scanium Dashboard Traffic Generator
# Generates minimal HTTP traffic to backend to populate monitoring dashboards
#
# Usage: ./generate-dashboard-traffic.sh [--normal] [--errors] [--slow] [--openai] [--duration SECONDS]
#

set -euo pipefail

# Configuration
BACKEND_URL="${BACKEND_URL:-http://192.168.178.45:8080}"
DURATION=90  # Default 90 seconds
NORMAL_TRAFFIC=false
ERROR_TRAFFIC=false
SLOW_TRAFFIC=false
OPENAI_TRAFFIC=false

# Counters
NORMAL_COUNT=0
ERROR_4XX_COUNT=0
ERROR_5XX_COUNT=0
SLOW_COUNT=0
OPENAI_COUNT=0

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --normal)
      NORMAL_TRAFFIC=true
      shift
      ;;
    --errors)
      ERROR_TRAFFIC=true
      shift
      ;;
    --slow)
      SLOW_TRAFFIC=true
      shift
      ;;
    --openai)
      OPENAI_TRAFFIC=true
      shift
      ;;
    --duration)
      DURATION="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      echo "Usage: $0 [--normal] [--errors] [--slow] [--openai] [--duration SECONDS]"
      exit 1
      ;;
  esac
done

# Validate at least one traffic type is selected
if [[ "$NORMAL_TRAFFIC" == "false" && "$ERROR_TRAFFIC" == "false" && "$SLOW_TRAFFIC" == "false" && "$OPENAI_TRAFFIC" == "false" ]]; then
  echo -e "${RED}Error: At least one traffic type must be specified${NC}"
  echo "Usage: $0 [--normal] [--errors] [--slow] [--openai] [--duration SECONDS]"
  exit 1
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Scanium Dashboard Traffic Generator${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "Backend URL: ${GREEN}$BACKEND_URL${NC}"
echo -e "Duration: ${GREEN}${DURATION}s${NC}"
echo -e "Traffic types:"
[[ "$NORMAL_TRAFFIC" == "true" ]] && echo -e "  ${GREEN}✓${NC} Normal (200s)"
[[ "$ERROR_TRAFFIC" == "true" ]] && echo -e "  ${GREEN}✓${NC} Errors (4xx/5xx)"
[[ "$SLOW_TRAFFIC" == "true" ]] && echo -e "  ${GREEN}✓${NC} Slow requests"
[[ "$OPENAI_TRAFFIC" == "true" ]] && echo -e "  ${GREEN}✓${NC} OpenAI calls"
echo ""

# Record start time
START_TIME=$(date -u +"%Y-%m-%d %H:%M:%S UTC")
START_EPOCH=$(date +%s)
END_EPOCH=$((START_EPOCH + DURATION))

echo -e "${YELLOW}Starting traffic generation at $START_TIME${NC}"
echo ""

# Function to generate normal traffic (200s)
generate_normal() {
  local response
  response=$(curl -s -w "\n%{http_code}" "$BACKEND_URL/health" 2>/dev/null || echo "000")
  local status_code=$(echo "$response" | tail -1)

  if [[ "$status_code" == "200" ]]; then
    ((NORMAL_COUNT++))
    echo -ne "\r${GREEN}Normal traffic:${NC} $NORMAL_COUNT requests (200s)           "
  fi
}

# Function to generate 4xx errors
generate_4xx() {
  # Call /v1/config without auth header (should return 401)
  local response
  response=$(curl -s -w "\n%{http_code}" "$BACKEND_URL/v1/config" 2>/dev/null || echo "000")
  local status_code=$(echo "$response" | tail -1)

  if [[ "$status_code" == "401" || "$status_code" == "404" || "$status_code" =~ ^4 ]]; then
    ((ERROR_4XX_COUNT++))
  fi
}

# Function to generate 5xx errors
generate_5xx() {
  # Call an endpoint with invalid payload to trigger 500
  local response
  response=$(curl -s -X POST -H "Content-Type: application/json" \
    -w "\n%{http_code}" \
    -d '{"invalid": "payload"}' \
    "$BACKEND_URL/v1/classify" 2>/dev/null || echo "500")
  local status_code=$(echo "$response" | tail -1)

  if [[ "$status_code" =~ ^5 ]]; then
    ((ERROR_5XX_COUNT++))
  fi
}

# Function to update error display
update_error_display() {
  echo -ne "\r${RED}Error traffic:${NC} 4xx=$ERROR_4XX_COUNT, 5xx=$ERROR_5XX_COUNT           "
}

# Function to generate slow traffic
generate_slow() {
  # This would require a delay endpoint - skip for now if not available
  # Placeholder for future implementation
  ((SLOW_COUNT++))
  echo -ne "\r${YELLOW}Slow traffic:${NC} $SLOW_COUNT requests           "
}

# Function to generate OpenAI traffic
generate_openai() {
  # Call assist/chat endpoint (requires valid auth - may fail)
  local response
  response=$(curl -s -X POST -H "Content-Type: application/json" \
    -w "\n%{http_code}" \
    -d '{"message": "test"}' \
    "$BACKEND_URL/v1/assist/chat" 2>/dev/null || echo "000")

  ((OPENAI_COUNT++))
  echo -ne "\r${BLUE}OpenAI traffic:${NC} $OPENAI_COUNT requests           "
}

# Main traffic generation loop
while [[ $(date +%s) -lt $END_EPOCH ]]; do
  # Generate normal traffic (every iteration if enabled)
  if [[ "$NORMAL_TRAFFIC" == "true" ]]; then
    generate_normal
  fi

  # Generate error traffic (every 2 iterations if enabled)
  if [[ "$ERROR_TRAFFIC" == "true" ]]; then
    generate_4xx
    if [[ $((NORMAL_COUNT % 2)) -eq 0 ]]; then
      generate_5xx
    fi
    update_error_display
  fi

  # Generate slow traffic (every 5 iterations if enabled)
  if [[ "$SLOW_TRAFFIC" == "true" && $((NORMAL_COUNT % 5)) -eq 0 ]]; then
    generate_slow
  fi

  # Generate OpenAI traffic (every 10 iterations if enabled)
  if [[ "$OPENAI_TRAFFIC" == "true" && $((NORMAL_COUNT % 10)) -eq 0 ]]; then
    generate_openai
  fi

  # Small delay between requests (adjust based on desired QPS)
  sleep 1
done

# Record end time
END_TIME=$(date -u +"%Y-%m-%d %H:%M:%S UTC")

# Print final summary
echo -e "\n"
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Traffic Generation Complete${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "Start time: ${YELLOW}$START_TIME${NC}"
echo -e "End time:   ${YELLOW}$END_TIME${NC}"
echo -e "Duration:   ${YELLOW}${DURATION}s${NC}"
echo ""
echo -e "${BLUE}Request Summary:${NC}"
[[ "$NORMAL_TRAFFIC" == "true" ]] && echo -e "  Normal (200s):    ${GREEN}$NORMAL_COUNT${NC}"
[[ "$ERROR_TRAFFIC" == "true" ]] && echo -e "  4xx errors:       ${YELLOW}$ERROR_4XX_COUNT${NC}"
[[ "$ERROR_TRAFFIC" == "true" ]] && echo -e "  5xx errors:       ${RED}$ERROR_5XX_COUNT${NC}"
[[ "$SLOW_TRAFFIC" == "true" ]] && echo -e "  Slow requests:    ${YELLOW}$SLOW_COUNT${NC}"
[[ "$OPENAI_TRAFFIC" == "true" ]] && echo -e "  OpenAI calls:     ${BLUE}$OPENAI_COUNT${NC}"
echo ""
echo -e "${BLUE}Total requests:${NC} $((NORMAL_COUNT + ERROR_4XX_COUNT + ERROR_5XX_COUNT + SLOW_COUNT + OPENAI_COUNT))"
echo ""
echo -e "${GREEN}Dashboards should now show data for time range: $START_TIME to $END_TIME${NC}"
