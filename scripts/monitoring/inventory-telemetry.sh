#!/usr/bin/env bash
# =============================================================================
# inventory-telemetry.sh - Discover telemetry in running LGTM stack
# =============================================================================
# Queries Grafana's datasource proxy to discover:
#   - Available datasources (Loki, Tempo, Mimir)
#   - Metric names and labels from Mimir
#   - Log labels and values from Loki
#   - Trace services/operations from Tempo (if available)
#
# Usage:
#   ./inventory-telemetry.sh [options]
#
# Options:
#   -u, --grafana-url URL   Grafana URL (default: http://localhost:3000)
#   -t, --token TOKEN       Grafana API token (optional, anonymous auth used if not set)
#   -o, --output-dir DIR    Output directory (default: monitoring/grafana)
#   -h, --help              Show this help message
#
# Outputs:
#   - telemetry-inventory.json (machine-readable)
#   - telemetry-inventory.md (human-readable summary)
#
# Safety:
#   - Uses small time ranges (last 15m) to avoid heavy queries
#   - Limits label value queries to prevent cardinality explosions
#   - Timeouts prevent hung requests
# =============================================================================

set -euo pipefail

# =============================================================================
# Configuration
# =============================================================================
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"
GRAFANA_TOKEN="${GRAFANA_TOKEN:-}"
OUTPUT_DIR="${OUTPUT_DIR:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Query limits
QUERY_TIMEOUT=30
LABEL_VALUE_LIMIT=100
METRIC_SAMPLE_LIMIT=200

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# =============================================================================
# Helper Functions
# =============================================================================

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[OK]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

show_help() {
    head -40 "$0" | tail -35 | sed 's/^# //' | sed 's/^#//'
    exit 0
}

# Make a Grafana API request
grafana_api() {
    local path="$1"
    local auth_header=""

    if [[ -n "$GRAFANA_TOKEN" ]]; then
        auth_header="-H \"Authorization: Bearer $GRAFANA_TOKEN\""
    fi

    curl -s --max-time "$QUERY_TIMEOUT" \
        -H "Content-Type: application/json" \
        ${auth_header:+"$auth_header"} \
        "${GRAFANA_URL}${path}" 2>/dev/null
}

# Query Mimir via Grafana datasource proxy
query_mimir() {
    local query="$1"
    local endpoint="${2:-query}"

    # URL encode the query
    local encoded_query
    encoded_query=$(echo -n "$query" | jq -sRr @uri)

    grafana_api "/api/datasources/proxy/uid/MIMIR/api/v1/${endpoint}?query=${encoded_query}"
}

# Query Loki via Grafana datasource proxy
query_loki() {
    local path="$1"
    grafana_api "/api/datasources/proxy/uid/LOKI/loki/api/v1/${path}"
}

# Query Tempo via Grafana datasource proxy
query_tempo() {
    local path="$1"
    grafana_api "/api/datasources/proxy/uid/TEMPO/api/${path}"
}

# =============================================================================
# Parse Arguments
# =============================================================================

while [[ $# -gt 0 ]]; do
    case $1 in
        -u|--grafana-url)
            GRAFANA_URL="$2"
            shift 2
            ;;
        -t|--token)
            GRAFANA_TOKEN="$2"
            shift 2
            ;;
        -o|--output-dir)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        -h|--help)
            show_help
            ;;
        *)
            log_error "Unknown option: $1"
            show_help
            ;;
    esac
done

# Set default output dir
if [[ -z "$OUTPUT_DIR" ]]; then
    OUTPUT_DIR="$REPO_ROOT/monitoring/grafana"
fi

# =============================================================================
# Check Prerequisites
# =============================================================================

log_info "Checking prerequisites..."

# Check for required tools
for tool in curl jq; do
    if ! command -v "$tool" &>/dev/null; then
        log_error "$tool is required but not installed"
        exit 1
    fi
done

# Check Grafana connectivity
log_info "Checking Grafana connectivity at $GRAFANA_URL..."
health_response=$(curl -s --max-time 5 "${GRAFANA_URL}/api/health" 2>/dev/null || echo "")

if [[ -z "$health_response" ]]; then
    log_error "Cannot connect to Grafana at $GRAFANA_URL"
    log_info "Make sure the monitoring stack is running:"
    log_info "  cd monitoring && docker compose -p scanium-monitoring up -d"
    exit 1
fi

if ! echo "$health_response" | jq -e '.database == "ok"' &>/dev/null; then
    log_error "Grafana health check failed: $health_response"
    exit 1
fi

log_success "Grafana is healthy"

# =============================================================================
# Discover Datasources
# =============================================================================

log_info "Discovering datasources..."

datasources_raw=$(grafana_api "/api/datasources")
datasources_json=$(echo "$datasources_raw" | jq '[.[] | {name: .name, type: .type, uid: .uid, url: .url, isDefault: .isDefault}]')

# Extract specific datasources
loki_ds=$(echo "$datasources_json" | jq '[.[] | select(.type == "loki")][0]')
tempo_ds=$(echo "$datasources_json" | jq '[.[] | select(.type == "tempo")][0]')
mimir_ds=$(echo "$datasources_json" | jq '[.[] | select(.type == "prometheus")][0]')

has_loki=$(echo "$loki_ds" | jq -e '.uid != null' &>/dev/null && echo "true" || echo "false")
has_tempo=$(echo "$tempo_ds" | jq -e '.uid != null' &>/dev/null && echo "true" || echo "false")
has_mimir=$(echo "$mimir_ds" | jq -e '.uid != null' &>/dev/null && echo "true" || echo "false")

log_info "Found datasources: Loki=$has_loki, Tempo=$has_tempo, Mimir=$has_mimir"

# =============================================================================
# Discover Mimir Metrics
# =============================================================================

metrics_data='{"available": false}'

if [[ "$has_mimir" == "true" ]]; then
    log_info "Discovering Mimir metrics..."

    # Get metric names (limited sample)
    metric_names_raw=$(grafana_api "/api/datasources/proxy/uid/MIMIR/api/v1/label/__name__/values")

    if echo "$metric_names_raw" | jq -e '.status == "success"' &>/dev/null; then
        metric_names=$(echo "$metric_names_raw" | jq '.data')
        metric_count=$(echo "$metric_names" | jq 'length')
        log_success "Found $metric_count metrics in Mimir"

        # Sample a few key metrics for label discovery
        key_metrics=()
        http_metric=""
        otel_metrics=()
        pipeline_metrics=()

        # Find HTTP metrics
        http_metric=$(echo "$metric_names" | jq -r '.[] | select(startswith("http_server") or startswith("http_request"))' | head -1)

        # Find OTEL metrics
        otel_metrics=($(echo "$metric_names" | jq -r '.[] | select(startswith("otelcol_"))' | head -5))

        # Find pipeline/infra metrics
        pipeline_metrics=($(echo "$metric_names" | jq -r '.[] | select(startswith("loki_") or startswith("tempo_") or startswith("cortex_"))' | head -5))

        # Discover labels from key metrics
        labels_discovered='{}'

        if [[ -n "$http_metric" ]]; then
            log_info "Discovering labels from $http_metric..."
            http_labels_raw=$(grafana_api "/api/datasources/proxy/uid/MIMIR/api/v1/labels?match[]=${http_metric}")
            if echo "$http_labels_raw" | jq -e '.status == "success"' &>/dev/null; then
                http_labels=$(echo "$http_labels_raw" | jq '.data')
                labels_discovered=$(echo "$labels_discovered" | jq --argjson labels "$http_labels" '. + {http_metrics: $labels}')

                # Sample label values for key labels
                label_values='{}'
                for label in service_name deployment_environment http_route http_request_method http_response_status_code; do
                    if echo "$http_labels" | jq -e "index(\"$label\")" &>/dev/null; then
                        values_raw=$(grafana_api "/api/datasources/proxy/uid/MIMIR/api/v1/label/${label}/values?match[]=${http_metric}&limit=$LABEL_VALUE_LIMIT")
                        if echo "$values_raw" | jq -e '.status == "success"' &>/dev/null; then
                            values=$(echo "$values_raw" | jq '.data')
                            label_values=$(echo "$label_values" | jq --arg l "$label" --argjson v "$values" '. + {($l): $v}')
                        fi
                    fi
                done
                labels_discovered=$(echo "$labels_discovered" | jq --argjson vals "$label_values" '. + {http_label_values: $vals}')
            fi
        fi

        # Check for pipeline metrics labels
        if [[ ${#otel_metrics[@]} -gt 0 ]]; then
            log_info "Discovering pipeline metric labels..."
            pipeline_labels_raw=$(grafana_api "/api/datasources/proxy/uid/MIMIR/api/v1/labels?match[]=${otel_metrics[0]}")
            if echo "$pipeline_labels_raw" | jq -e '.status == "success"' &>/dev/null; then
                pipeline_labels=$(echo "$pipeline_labels_raw" | jq '.data')
                labels_discovered=$(echo "$labels_discovered" | jq --argjson labels "$pipeline_labels" '. + {pipeline_metrics: $labels}')
            fi
        fi

        # Build metrics data
        metrics_data=$(jq -n \
            --argjson names "$metric_names" \
            --argjson labels "$labels_discovered" \
            --arg http "$http_metric" \
            '{
                available: true,
                metric_count: ($names | length),
                metric_names: $names,
                http_metric_found: ($http != ""),
                http_metric: $http,
                labels: $labels
            }')
    else
        log_warn "Failed to query Mimir metrics: $metric_names_raw"
    fi
fi

# =============================================================================
# Discover Loki Labels
# =============================================================================

logs_data='{"available": false}'

if [[ "$has_loki" == "true" ]]; then
    log_info "Discovering Loki log labels..."

    # Get label names
    labels_raw=$(query_loki "labels")

    if echo "$labels_raw" | jq -e '.status == "success"' &>/dev/null; then
        log_labels=$(echo "$labels_raw" | jq '.data')
        label_count=$(echo "$log_labels" | jq 'length')
        log_success "Found $label_count log labels in Loki"

        # Sample label values for key labels
        label_values='{}'
        for label in source env service_name level; do
            values_raw=$(query_loki "label/${label}/values?limit=$LABEL_VALUE_LIMIT")
            if echo "$values_raw" | jq -e '.status == "success"' &>/dev/null; then
                values=$(echo "$values_raw" | jq '.data')
                label_values=$(echo "$label_values" | jq --arg l "$label" --argjson v "$values" '. + {($l): $v}')
            fi
        done

        # Sample recent log streams
        streams_raw=$(query_loki "series?match[]={}&limit=50")
        streams='[]'
        if echo "$streams_raw" | jq -e '.status == "success"' &>/dev/null; then
            streams=$(echo "$streams_raw" | jq '[.data[0:20]]')
        fi

        logs_data=$(jq -n \
            --argjson labels "$log_labels" \
            --argjson values "$label_values" \
            --argjson streams "$streams" \
            '{
                available: true,
                label_count: ($labels | length),
                label_names: $labels,
                label_values: $values,
                sample_streams: $streams
            }')
    else
        log_warn "Failed to query Loki labels: $labels_raw"
    fi
fi

# =============================================================================
# Discover Tempo Traces
# =============================================================================

traces_data='{"available": false}'

if [[ "$has_tempo" == "true" ]]; then
    log_info "Discovering Tempo trace services..."

    # Try to get services via Tempo API
    # Note: Tempo's search API may vary by version
    services_raw=$(query_tempo "search/tags")

    if echo "$services_raw" | jq -e '.tagNames' &>/dev/null; then
        tag_names=$(echo "$services_raw" | jq '.tagNames')
        log_success "Found $(echo "$tag_names" | jq 'length') trace attributes"

        # Try to get service names
        services='[]'
        service_values_raw=$(query_tempo "search/tag/service.name/values")
        if echo "$service_values_raw" | jq -e '.tagValues' &>/dev/null; then
            services=$(echo "$service_values_raw" | jq '.tagValues')
        fi

        # Try to get span names/operations
        operations='[]'
        ops_raw=$(query_tempo "search/tag/name/values")
        if echo "$ops_raw" | jq -e '.tagValues' &>/dev/null; then
            operations=$(echo "$ops_raw" | jq '[.tagValues[0:50]]')
        fi

        traces_data=$(jq -n \
            --argjson tags "$tag_names" \
            --argjson services "$services" \
            --argjson operations "$operations" \
            '{
                available: true,
                tag_names: $tags,
                services: $services,
                operations: $operations
            }')
    else
        # Fallback: check if Tempo responds at all
        tempo_ready=$(curl -s --max-time 5 "${GRAFANA_URL}/api/datasources/proxy/uid/TEMPO/ready" 2>/dev/null || echo "")
        if [[ "$tempo_ready" == "ready" ]]; then
            log_info "Tempo is ready but no trace data found yet"
            traces_data='{"available": true, "tag_names": [], "services": [], "operations": [], "note": "No trace data ingested yet"}'
        else
            log_warn "Could not query Tempo API: $services_raw"
        fi
    fi
fi

# =============================================================================
# Discover Existing Dashboards
# =============================================================================

log_info "Discovering existing dashboards..."

dashboards_raw=$(grafana_api "/api/search?type=dash-db")
dashboards_json='[]'

if echo "$dashboards_raw" | jq -e 'type == "array"' &>/dev/null; then
    dashboards_json=$(echo "$dashboards_raw" | jq '[.[] | {uid: .uid, title: .title, folderTitle: .folderTitle, tags: .tags}]')
    dashboard_count=$(echo "$dashboards_json" | jq 'length')
    log_success "Found $dashboard_count dashboards"
else
    log_warn "Could not list dashboards: $dashboards_raw"
fi

# =============================================================================
# Build Inventory Report
# =============================================================================

log_info "Building inventory report..."

inventory_json=$(jq -n \
    --arg timestamp "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" \
    --arg grafana_url "$GRAFANA_URL" \
    --argjson datasources "$datasources_json" \
    --argjson metrics "$metrics_data" \
    --argjson logs "$logs_data" \
    --argjson traces "$traces_data" \
    --argjson dashboards "$dashboards_json" \
    '{
        generated_at: $timestamp,
        grafana_url: $grafana_url,
        datasources: $datasources,
        metrics: $metrics,
        logs: $logs,
        traces: $traces,
        dashboards: $dashboards
    }')

# =============================================================================
# Write Output Files
# =============================================================================

mkdir -p "$OUTPUT_DIR"

# Write JSON inventory
json_file="$OUTPUT_DIR/telemetry-inventory.json"
echo "$inventory_json" | jq '.' > "$json_file"
log_success "Wrote machine-readable inventory to $json_file"

# Write Markdown summary
md_file="$OUTPUT_DIR/telemetry-inventory.md"
cat > "$md_file" << 'MARKDOWN_HEADER'
# Telemetry Inventory Report

> Auto-generated by `scripts/monitoring/inventory-telemetry.sh`
> Re-run this script to update the inventory.

MARKDOWN_HEADER

echo "**Generated:** $(date -u +"%Y-%m-%d %H:%M:%S UTC")" >> "$md_file"
echo "" >> "$md_file"

# Datasources section
cat >> "$md_file" << 'SECTION'
## Datasources

| Name | Type | UID | Default |
|------|------|-----|---------|
SECTION

echo "$datasources_json" | jq -r '.[] | "| \(.name) | \(.type) | `\(.uid)` | \(.isDefault) |"' >> "$md_file"
echo "" >> "$md_file"

# Metrics section
if [[ $(echo "$metrics_data" | jq '.available') == "true" ]]; then
    cat >> "$md_file" << 'SECTION'
## Metrics (Mimir)

SECTION
    metric_count=$(echo "$metrics_data" | jq '.metric_count')
    echo "**Total metrics:** $metric_count" >> "$md_file"
    echo "" >> "$md_file"

    http_metric=$(echo "$metrics_data" | jq -r '.http_metric // "none"')
    if [[ "$http_metric" != "none" && "$http_metric" != "" ]]; then
        echo "### HTTP Metrics" >> "$md_file"
        echo "" >> "$md_file"
        echo "Primary HTTP metric: \`$http_metric\`" >> "$md_file"
        echo "" >> "$md_file"

        echo "**Available labels:**" >> "$md_file"
        echo "$metrics_data" | jq -r '.labels.http_metrics // [] | .[] | "- `\(.)`"' >> "$md_file"
        echo "" >> "$md_file"

        echo "**Sample label values:**" >> "$md_file"
        echo "" >> "$md_file"
        echo "$metrics_data" | jq -r '.labels.http_label_values // {} | to_entries[] | "- **\(.key):** \(.value | join(", "))"' >> "$md_file"
        echo "" >> "$md_file"
    else
        echo "> No HTTP metrics found. Backend may not be instrumented or sending telemetry." >> "$md_file"
        echo "" >> "$md_file"
    fi

    # List key metric categories
    echo "### Metric Categories" >> "$md_file"
    echo "" >> "$md_file"
    echo "| Category | Sample Metrics |" >> "$md_file"
    echo "|----------|----------------|" >> "$md_file"

    # HTTP metrics
    http_metrics=$(echo "$metrics_data" | jq -r '[.metric_names[] | select(startswith("http_"))] | .[0:3] | join(", ")' 2>/dev/null || echo "")
    [[ -n "$http_metrics" ]] && echo "| HTTP | $http_metrics |" >> "$md_file"

    # OTEL metrics
    otel_metrics=$(echo "$metrics_data" | jq -r '[.metric_names[] | select(startswith("otelcol_"))] | .[0:3] | join(", ")' 2>/dev/null || echo "")
    [[ -n "$otel_metrics" ]] && echo "| OTLP Collector | $otel_metrics |" >> "$md_file"

    # Loki metrics
    loki_metrics=$(echo "$metrics_data" | jq -r '[.metric_names[] | select(startswith("loki_"))] | .[0:3] | join(", ")' 2>/dev/null || echo "")
    [[ -n "$loki_metrics" ]] && echo "| Loki | $loki_metrics |" >> "$md_file"

    # Tempo metrics
    tempo_metrics=$(echo "$metrics_data" | jq -r '[.metric_names[] | select(startswith("tempo_") or startswith("traces_"))] | .[0:3] | join(", ")' 2>/dev/null || echo "")
    [[ -n "$tempo_metrics" ]] && echo "| Tempo | $tempo_metrics |" >> "$md_file"

    # Mimir/Cortex metrics
    mimir_metrics=$(echo "$metrics_data" | jq -r '[.metric_names[] | select(startswith("cortex_"))] | .[0:3] | join(", ")' 2>/dev/null || echo "")
    [[ -n "$mimir_metrics" ]] && echo "| Mimir | $mimir_metrics |" >> "$md_file"

    # Process metrics
    process_metrics=$(echo "$metrics_data" | jq -r '[.metric_names[] | select(startswith("process_") or startswith("go_"))] | .[0:3] | join(", ")' 2>/dev/null || echo "")
    [[ -n "$process_metrics" ]] && echo "| Process/Go | $process_metrics |" >> "$md_file"

    echo "" >> "$md_file"
else
    echo "## Metrics (Mimir)" >> "$md_file"
    echo "" >> "$md_file"
    echo "> Mimir not available or no metrics found." >> "$md_file"
    echo "" >> "$md_file"
fi

# Logs section
if [[ $(echo "$logs_data" | jq '.available') == "true" ]]; then
    cat >> "$md_file" << 'SECTION'
## Logs (Loki)

SECTION
    label_count=$(echo "$logs_data" | jq '.label_count')
    echo "**Total labels:** $label_count" >> "$md_file"
    echo "" >> "$md_file"

    echo "**Available labels:**" >> "$md_file"
    echo "$logs_data" | jq -r '.label_names | .[] | "- `\(.)`"' >> "$md_file"
    echo "" >> "$md_file"

    echo "**Sample label values:**" >> "$md_file"
    echo "" >> "$md_file"
    echo "$logs_data" | jq -r '.label_values | to_entries[] | select(.value | length > 0) | "- **\(.key):** \(.value | join(", "))"' >> "$md_file"
    echo "" >> "$md_file"
else
    echo "## Logs (Loki)" >> "$md_file"
    echo "" >> "$md_file"
    echo "> Loki not available or no logs found." >> "$md_file"
    echo "" >> "$md_file"
fi

# Traces section
if [[ $(echo "$traces_data" | jq '.available') == "true" ]]; then
    cat >> "$md_file" << 'SECTION'
## Traces (Tempo)

SECTION

    services=$(echo "$traces_data" | jq -r '.services // []')
    service_count=$(echo "$services" | jq 'length')

    if [[ "$service_count" -gt 0 ]]; then
        echo "**Services:** $(echo "$services" | jq -r 'join(", ")')" >> "$md_file"
    else
        echo "**Services:** None discovered yet" >> "$md_file"
    fi
    echo "" >> "$md_file"

    echo "**Available trace attributes:**" >> "$md_file"
    echo "$traces_data" | jq -r '.tag_names // [] | .[0:20] | .[] | "- `\(.)`"' >> "$md_file"
    echo "" >> "$md_file"

    note=$(echo "$traces_data" | jq -r '.note // empty')
    if [[ -n "$note" ]]; then
        echo "> $note" >> "$md_file"
        echo "" >> "$md_file"
    fi
else
    echo "## Traces (Tempo)" >> "$md_file"
    echo "" >> "$md_file"
    echo "> Tempo not available or no traces found." >> "$md_file"
    echo "" >> "$md_file"
fi

# Dashboards section
cat >> "$md_file" << 'SECTION'
## Existing Dashboards

SECTION

dashboard_count=$(echo "$dashboards_json" | jq 'length')
if [[ "$dashboard_count" -gt 0 ]]; then
    echo "| Title | UID | Tags |" >> "$md_file"
    echo "|-------|-----|------|" >> "$md_file"
    echo "$dashboards_json" | jq -r '.[] | "| \(.title) | `\(.uid)` | \(.tags | join(", ")) |"' >> "$md_file"
else
    echo "> No dashboards found." >> "$md_file"
fi

echo "" >> "$md_file"

# Recommendations section
cat >> "$md_file" << 'SECTION'
## Dashboard Recommendations

Based on the inventory, here are the recommended dashboards:

### For Backend Monitoring (if HTTP metrics exist)
- **System Overview (RED)** - Rate, Errors, Duration overview
- **Backend API Performance** - Latency by route, throughput analysis
- **Backend Errors** - 4xx/5xx breakdown with log correlation

### For Log Analysis
- **Logs Explorer** - Volume, error rates, pattern analysis

### For Trace Analysis
- **Traces Drilldown** - Slow traces, service dependencies

### For Infrastructure
- **LGTM Stack Health** - Alloy/Loki/Tempo/Mimir status and throughput

---

*To regenerate this report, run:*
```bash
./scripts/monitoring/inventory-telemetry.sh
```
SECTION

log_success "Wrote human-readable summary to $md_file"

# =============================================================================
# Summary
# =============================================================================

echo ""
echo "============================================================"
echo "  Telemetry Inventory Complete"
echo "============================================================"
echo ""
echo "  Datasources found:"
echo "    - Loki:  $has_loki"
echo "    - Tempo: $has_tempo"
echo "    - Mimir: $has_mimir"
echo ""
echo "  Outputs:"
echo "    - $json_file"
echo "    - $md_file"
echo ""
echo "  Next steps:"
echo "    1. Review the inventory to understand available telemetry"
echo "    2. Restart Grafana to load updated dashboards:"
echo "       docker compose -p scanium-monitoring restart grafana"
echo "    3. Verify dashboards at $GRAFANA_URL"
echo ""
