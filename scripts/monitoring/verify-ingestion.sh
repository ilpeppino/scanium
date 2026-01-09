***REMOVED***!/bin/bash
***REMOVED***
***REMOVED*** verify-ingestion.sh - CI/CD Regression Guard for Telemetry Ingestion
***REMOVED***
***REMOVED*** This script verifies that telemetry data is being ingested into the LGTM stack.
***REMOVED*** It is designed to be run in CI/CD pipelines to catch telemetry regressions early.
***REMOVED***
***REMOVED*** Exit Codes:
***REMOVED***   0 - All telemetry signals are being ingested correctly
***REMOVED***   1 - One or more telemetry signals are not being ingested
***REMOVED***
***REMOVED*** Usage:
***REMOVED***   bash scripts/monitoring/verify-ingestion.sh
***REMOVED***   bash scripts/monitoring/verify-ingestion.sh --strict  ***REMOVED*** Fail on any warning
***REMOVED***

set -euo pipefail

***REMOVED*** =============================================================================
***REMOVED*** Configuration
***REMOVED*** =============================================================================

GRAFANA_URL="${GRAFANA_URL:-http://127.0.0.1:3000}"
STRICT_MODE=false

***REMOVED*** Parse command line arguments
while [[ $***REMOVED*** -gt 0 ]]; do
    case $1 in
        --strict)
            STRICT_MODE=true
            shift
            ;;
        --help)
            echo "Usage: $0 [--strict]"
            echo ""
            echo "Verify that telemetry data is being ingested into the LGTM stack."
            echo ""
            echo "Options:"
            echo "  --strict    Fail on any warning (stricter validation)"
            echo "  --help      Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

***REMOVED*** =============================================================================
***REMOVED*** Colors and Formatting
***REMOVED*** =============================================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' ***REMOVED*** No Color
BOLD='\033[1m'

***REMOVED*** =============================================================================
***REMOVED*** Helper Functions
***REMOVED*** =============================================================================

log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $*"
}

success() {
    echo -e "${GREEN}✓${NC} $*"
}

warning() {
    echo -e "${YELLOW}⚠${NC} $*"
}

error() {
    echo -e "${RED}✗${NC} $*"
}

query_grafana_api() {
    local endpoint="$1"
    curl -s "${GRAFANA_URL}${endpoint}" || echo "{}"
}

***REMOVED*** =============================================================================
***REMOVED*** Validation Functions
***REMOVED*** =============================================================================

validate_mimir() {
    log "Validating Mimir (Metrics)..."

    local datasources
    datasources=$(query_grafana_api "/api/datasources")

    local mimir_uid
    mimir_uid=$(echo "$datasources" | jq -r '.[] | select(.type == "prometheus") | .uid' | head -1)

    if [[ -z "$mimir_uid" || "$mimir_uid" == "null" ]]; then
        error "Mimir datasource not found"
        return 1
    fi

    ***REMOVED*** Query for up metric to get series count
    local query='{"queries":[{"refId":"A","expr":"up","datasource":{"type":"prometheus","uid":"'"$mimir_uid"'"}}]}'
    local response
    response=$(query_grafana_api "/api/ds/query" -X POST -H "Content-Type: application/json" -d "$query")

    local series_count
    series_count=$(echo "$response" | jq -r '.results.A.frames[0].data.values[0] | length' 2>/dev/null || echo "0")

    ***REMOVED*** Query for job labels to check for application metrics
    local jobs_query='{"queries":[{"refId":"A","expr":"label_values(up, job)","datasource":{"type":"prometheus","uid":"'"$mimir_uid"'"}}]}'
    local jobs_response
    jobs_response=$(query_grafana_api "/api/ds/query" -X POST -H "Content-Type: application/json" -d "$jobs_query")

    local jobs
    jobs=$(echo "$jobs_response" | jq -r '.results.A.frames[0].data.values[0][]?' 2>/dev/null | grep -v '^\(alloy\|loki\|mimir\|tempo\)$' || echo "")

    if [[ "$series_count" -eq 0 ]]; then
        error "Mimir: No data series found"
        return 1
    elif [[ -z "$jobs" ]]; then
        warning "Mimir: Only LGTM stack metrics found, no application metrics"
        if [[ "$STRICT_MODE" == "true" ]]; then
            return 1
        fi
        return 0
    else
        success "Mimir: $series_count series, application jobs: $(echo "$jobs" | tr '\n' ',' | sed 's/,$//')"
        return 0
    fi
}

validate_loki() {
    log "Validating Loki (Logs)..."

    local datasources
    datasources=$(query_grafana_api "/api/datasources")

    local loki_uid
    loki_uid=$(echo "$datasources" | jq -r '.[] | select(.type == "loki") | .uid' | head -1)

    if [[ -z "$loki_uid" || "$loki_uid" == "null" ]]; then
        error "Loki datasource not found"
        return 1
    fi

    ***REMOVED*** Query for labels
    local query='{"queries":[{"refId":"A","expr":"{job=~\".+\"}","datasource":{"type":"loki","uid":"'"$loki_uid"'"}}]}'
    local response
    response=$(query_grafana_api "/api/ds/query" -X POST -H "Content-Type: application/json" -d "$query")

    ***REMOVED*** Get labels from Loki API directly
    local labels_response
    labels_response=$(curl -s "http://127.0.0.1:3100/loki/api/v1/labels" 2>/dev/null || echo '{"data":[]}')

    local label_count
    label_count=$(echo "$labels_response" | jq '.data | length' 2>/dev/null || echo "0")

    local has_source
    has_source=$(echo "$labels_response" | jq -r '.data[]?' 2>/dev/null | grep -q "source" && echo "true" || echo "false")

    if [[ "$label_count" -eq 0 ]]; then
        error "Loki: No labels found - no logs are being ingested"
        return 1
    elif [[ "$has_source" == "false" ]]; then
        warning "Loki: $label_count labels found, but no 'source' label (application logs missing?)"
        if [[ "$STRICT_MODE" == "true" ]]; then
            return 1
        fi
        return 0
    else
        success "Loki: $label_count labels including 'source' label"
        return 0
    fi
}

validate_tempo() {
    log "Validating Tempo (Traces)..."

    local datasources
    datasources=$(query_grafana_api "/api/datasources")

    local tempo_uid
    tempo_uid=$(echo "$datasources" | jq -r '.[] | select(.type == "tempo") | .uid' | head -1)

    if [[ -z "$tempo_uid" || "$tempo_uid" == "null" ]]; then
        error "Tempo datasource not found"
        return 1
    fi

    ***REMOVED*** Get services from Tempo API directly
    local services_response
    services_response=$(curl -s "http://127.0.0.1:3200/api/search/tag/service.name/values" 2>/dev/null || echo '{"tagValues":[]}')

    local service_count
    service_count=$(echo "$services_response" | jq '.tagValues | length' 2>/dev/null || echo "0")

    local services
    services=$(echo "$services_response" | jq -r '.tagValues[]?' 2>/dev/null | tr '\n' ',' | sed 's/,$//' || echo "")

    if [[ "$service_count" -eq 0 ]]; then
        error "Tempo: No services found - no traces are being ingested"
        return 1
    else
        success "Tempo: $service_count services ($services)"
        return 0
    fi
}

***REMOVED*** =============================================================================
***REMOVED*** Main Execution
***REMOVED*** =============================================================================

main() {
    echo ""
    echo -e "${BOLD}========================================${NC}"
    echo -e "${BOLD}  Telemetry Ingestion Verification${NC}"
    echo -e "${BOLD}========================================${NC}"
    echo ""

    if [[ "$STRICT_MODE" == "true" ]]; then
        log "Running in STRICT mode - warnings will cause failure"
    fi

    local mimir_status=0
    local loki_status=0
    local tempo_status=0

    ***REMOVED*** Validate each component
    validate_mimir || mimir_status=$?
    echo ""

    validate_loki || loki_status=$?
    echo ""

    validate_tempo || tempo_status=$?
    echo ""

    ***REMOVED*** Summary
    echo -e "${BOLD}========================================${NC}"
    echo -e "${BOLD}  Summary${NC}"
    echo -e "${BOLD}========================================${NC}"
    echo ""

    local total_failures=0

    if [[ $mimir_status -eq 0 ]]; then
        success "Mimir: PASS"
    else
        error "Mimir: FAIL"
        ((total_failures++))
    fi

    if [[ $loki_status -eq 0 ]]; then
        success "Loki: PASS"
    else
        error "Loki: FAIL"
        ((total_failures++))
    fi

    if [[ $tempo_status -eq 0 ]]; then
        success "Tempo: PASS"
    else
        error "Tempo: FAIL"
        ((total_failures++))
    fi

    echo ""

    if [[ $total_failures -eq 0 ]]; then
        echo -e "${GREEN}${BOLD}✓ All telemetry signals are being ingested correctly${NC}"
        echo ""
        return 0
    else
        echo -e "${RED}${BOLD}✗ $total_failures telemetry signal(s) failed validation${NC}"
        echo ""
        return 1
    fi
}

***REMOVED*** Run main function
main
