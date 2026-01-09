***REMOVED***!/bin/bash
***REMOVED*** prove-telemetry.sh
***REMOVED*** Proves whether telemetry data exists in Grafana datasources
***REMOVED*** Non-negotiable: Does NOT leak secrets

set -euo pipefail

***REMOVED*** ============================================================================
***REMOVED*** Configuration
***REMOVED*** ============================================================================
GRAFANA_URL="${GRAFANA_URL:-http://127.0.0.1:3000}"
GRAFANA_TOKEN_FILE="${GRAFANA_TOKEN_FILE:-/volume1/docker/scanium/secrets/grafana_service_token.txt}"

***REMOVED*** Output files
OUTPUT_DIR="$(dirname "$0")/../../monitoring/grafana"
mkdir -p "$OUTPUT_DIR"
JSON_OUTPUT="$OUTPUT_DIR/telemetry-truth.json"
MD_OUTPUT="$OUTPUT_DIR/telemetry-truth.md"

***REMOVED*** ============================================================================
***REMOVED*** Helper functions
***REMOVED*** ============================================================================
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >&2
}

error() {
    log "ERROR: $*"
    exit 1
}

***REMOVED*** Read Grafana token securely (never echo)
read_token() {
    if [[ -f "$GRAFANA_TOKEN_FILE" ]]; then
        cat "$GRAFANA_TOKEN_FILE"
    else
        error "Grafana token file not found: $GRAFANA_TOKEN_FILE"
    fi
}

***REMOVED*** ============================================================================
***REMOVED*** Query Grafana API
***REMOVED*** ============================================================================
query_grafana_api() {
    local endpoint="$1"
    local token
    token=$(read_token)

    curl -s -H "Authorization: Bearer $token" \
         -H "Content-Type: application/json" \
         "$GRAFANA_URL$endpoint" || error "Failed to query $endpoint"
}

query_grafana_datasource() {
    local ds_uid="$1"
    local payload="$2"
    local token
    token=$(read_token)

    curl -s -X POST \
         -H "Authorization: Bearer $token" \
         -H "Content-Type: application/json" \
         -d "$payload" \
         "$GRAFANA_URL/api/ds/query?ds_type=prometheus&requestId=prove-telemetry" || true
}

query_loki() {
    local ds_uid="$1"
    local token
    token=$(read_token)

    ***REMOVED*** Query for label keys (labels API)
    curl -s -X GET \
         -H "Authorization: Bearer $token" \
         "$GRAFANA_URL/api/datasources/proxy/uid/$ds_uid/loki/api/v1/labels" || true
}

query_tempo() {
    local ds_uid="$1"
    local token
    token=$(read_token)

    ***REMOVED*** Query for services (Tempo search API)
    curl -s -X GET \
         -H "Authorization: Bearer $token" \
         "$GRAFANA_URL/api/datasources/proxy/uid/$ds_uid/api/search/tag/service.name/values" || true
}

***REMOVED*** ============================================================================
***REMOVED*** Main logic
***REMOVED*** ============================================================================
main() {
    log "Starting telemetry proof..."
    log "Grafana URL: $GRAFANA_URL"

    ***REMOVED*** Get datasources
    log "Fetching datasources..."
    DATASOURCES=$(query_grafana_api "/api/datasources")

    ***REMOVED*** Parse datasource UIDs
    MIMIR_UID=$(echo "$DATASOURCES" | jq -r '.[] | select(.type == "prometheus") | .uid' | head -1)
    LOKI_UID=$(echo "$DATASOURCES" | jq -r '.[] | select(.type == "loki") | .uid' | head -1)
    TEMPO_UID=$(echo "$DATASOURCES" | jq -r '.[] | select(.type == "tempo") | .uid' | head -1)

    log "Mimir UID: $MIMIR_UID"
    log "Loki UID: $LOKI_UID"
    log "Tempo UID: $TEMPO_UID"

    ***REMOVED*** Initialize results
    RESULTS='{"timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'", "datasources": {}}'

    ***REMOVED*** ========================================================================
    ***REMOVED*** Test Mimir (Prometheus)
    ***REMOVED*** ========================================================================
    if [[ -n "$MIMIR_UID" && "$MIMIR_UID" != "null" ]]; then
        log "Testing Mimir..."

        ***REMOVED*** Query for 'up' metric (last 15 minutes)
        MIMIR_QUERY='{
            "queries": [{
                "refId": "A",
                "expr": "up",
                "datasource": {"type": "prometheus", "uid": "'$MIMIR_UID'"},
                "instant": false,
                "range": true,
                "intervalMs": 60000,
                "maxDataPoints": 100
            }],
            "from": "'$(($(date +%s) - 900))000'",
            "to": "'$(date +%s)000'"
        }'

        MIMIR_RESPONSE=$(query_grafana_datasource "$MIMIR_UID" "$MIMIR_QUERY")

        ***REMOVED*** Extract series labels
        MIMIR_DATA_POINTS=$(echo "$MIMIR_RESPONSE" | jq -r '.results.A.frames[].data.values[0][]? // empty' 2>/dev/null)
        if [[ -z "$MIMIR_DATA_POINTS" ]]; then
            MIMIR_SERIES="0"
        else
            MIMIR_SERIES=$(echo "$MIMIR_DATA_POINTS" | wc -l | tr -d ' ')
        fi
        MIMIR_JOBS=$(echo "$MIMIR_RESPONSE" | jq -r '.results.A.frames[].schema.fields[1].labels.job? // empty' 2>/dev/null | sort -u || echo "")

        RESULTS=$(echo "$RESULTS" | jq --arg series "$MIMIR_SERIES" --arg jobs "$MIMIR_JOBS" \
            '.datasources.mimir = {
                "status": "queried",
                "series_count": ($series | tonumber),
                "jobs": ($jobs | split("\n") | map(select(length > 0)))
            }')

        log "Mimir: Found $MIMIR_SERIES data points, jobs: $MIMIR_JOBS"
    else
        log "Mimir datasource not found"
        RESULTS=$(echo "$RESULTS" | jq '.datasources.mimir = {"status": "not_found"}')
    fi

    ***REMOVED*** ========================================================================
    ***REMOVED*** Test Loki
    ***REMOVED*** ========================================================================
    if [[ -n "$LOKI_UID" && "$LOKI_UID" != "null" ]]; then
        log "Testing Loki..."

        LOKI_RESPONSE=$(query_loki "$LOKI_UID")
        LOKI_LABELS=$(echo "$LOKI_RESPONSE" | jq -r '.data[]? // empty' 2>/dev/null | sort || echo "")
        if [[ -z "$LOKI_LABELS" ]]; then
            LOKI_LABEL_COUNT="0"
        else
            LOKI_LABEL_COUNT=$(echo "$LOKI_LABELS" | grep -c -v '^$' || echo "0")
        fi

        ***REMOVED*** Check for specific labels
        HAS_SOURCE=$(echo "$LOKI_LABELS" | grep -q "source" && echo "true" || echo "false")

        RESULTS=$(echo "$RESULTS" | jq --arg count "$LOKI_LABEL_COUNT" \
                                        --arg labels "$LOKI_LABELS" \
                                        --argjson has_source "$HAS_SOURCE" \
            '.datasources.loki = {
                "status": "queried",
                "label_count": ($count | tonumber),
                "labels": ($labels | split("\n") | map(select(length > 0))),
                "has_source_label": $has_source
            }')

        log "Loki: Found $LOKI_LABEL_COUNT labels, has 'source': $HAS_SOURCE"
    else
        log "Loki datasource not found"
        RESULTS=$(echo "$RESULTS" | jq '.datasources.loki = {"status": "not_found"}')
    fi

    ***REMOVED*** ========================================================================
    ***REMOVED*** Test Tempo
    ***REMOVED*** ========================================================================
    if [[ -n "$TEMPO_UID" && "$TEMPO_UID" != "null" ]]; then
        log "Testing Tempo..."

        TEMPO_RESPONSE=$(query_tempo "$TEMPO_UID")
        TEMPO_SERVICES=$(echo "$TEMPO_RESPONSE" | jq -r '.tagValues[]?.value? // empty' 2>/dev/null | sort || echo "")
        if [[ -z "$TEMPO_SERVICES" ]]; then
            TEMPO_SERVICE_COUNT="0"
        else
            TEMPO_SERVICE_COUNT=$(echo "$TEMPO_SERVICES" | grep -c -v '^$' || echo "0")
        fi

        RESULTS=$(echo "$RESULTS" | jq --arg count "$TEMPO_SERVICE_COUNT" \
                                        --arg services "$TEMPO_SERVICES" \
            '.datasources.tempo = {
                "status": "queried",
                "service_count": ($count | tonumber),
                "services": ($services | split("\n") | map(select(length > 0)))
            }')

        log "Tempo: Found $TEMPO_SERVICE_COUNT services"
    else
        log "Tempo datasource not found"
        RESULTS=$(echo "$RESULTS" | jq '.datasources.tempo = {"status": "not_found"}')
    fi

    ***REMOVED*** ========================================================================
    ***REMOVED*** Save results
    ***REMOVED*** ========================================================================
    echo "$RESULTS" | jq '.' > "$JSON_OUTPUT"
    log "Saved JSON results to: $JSON_OUTPUT"

    ***REMOVED*** Generate markdown report
    cat > "$MD_OUTPUT" <<EOF
***REMOVED*** Telemetry Truth Report

Generated: $(date)

***REMOVED******REMOVED*** Summary

$(echo "$RESULTS" | jq -r '
    if .datasources.mimir.series_count > 0 then "✅ Mimir has data (" + (.datasources.mimir.series_count | tostring) + " series)" else "❌ Mimir has NO data" end,
    if .datasources.loki.label_count > 0 then "✅ Loki has labels (" + (.datasources.loki.label_count | tostring) + " labels)" else "❌ Loki has NO labels" end,
    if .datasources.tempo.service_count > 0 then "✅ Tempo has services (" + (.datasources.tempo.service_count | tostring) + " services)" else "❌ Tempo has NO services" end
')

***REMOVED******REMOVED*** Mimir (Metrics)

- **Series count**: $(echo "$RESULTS" | jq -r '.datasources.mimir.series_count // "N/A"')
- **Jobs found**:
$(echo "$RESULTS" | jq -r '.datasources.mimir.jobs[]? // empty' | sed 's/^/  - /')

***REMOVED******REMOVED*** Loki (Logs)

- **Label count**: $(echo "$RESULTS" | jq -r '.datasources.loki.label_count // "N/A"')
- **Has 'source' label**: $(echo "$RESULTS" | jq -r '.datasources.loki.has_source_label // "N/A"')
- **Labels found**:
$(echo "$RESULTS" | jq -r '.datasources.loki.labels[]? // empty' | sed 's/^/  - /')

***REMOVED******REMOVED*** Tempo (Traces)

- **Service count**: $(echo "$RESULTS" | jq -r '.datasources.tempo.service_count // "N/A"')
- **Services found**:
$(echo "$RESULTS" | jq -r '.datasources.tempo.services[]? // empty' | sed 's/^/  - /')

***REMOVED******REMOVED*** Decision Gate

$(echo "$RESULTS" | jq -r '
    if (.datasources.mimir.jobs // [] | map(select(test("^(alloy|loki|mimir|tempo|grafana)$"))) | length) == (.datasources.mimir.jobs // [] | length) and (.datasources.mimir.jobs // [] | length) > 0 then
        "⚠️  WARNING: Mimir only has LGTM stack jobs. App metrics NOT ingested."
    else
        "✅ Mimir has non-stack jobs (app metrics likely present)"
    end,

    if (.datasources.loki.label_count // 0) == 0 then
        "⚠️  WARNING: Loki has zero labels. Logs NOT ingested."
    else
        "✅ Loki has labels (logs ingested)"
    end,

    if (.datasources.tempo.service_count // 0) == 0 then
        "⚠️  WARNING: Tempo has zero services. Traces NOT ingested."
    else
        "✅ Tempo has services (traces ingested)"
    end
')

***REMOVED******REMOVED*** Raw Data

\`\`\`json
$(echo "$RESULTS" | jq '.')
\`\`\`
EOF

    log "Saved markdown report to: $MD_OUTPUT"

    ***REMOVED*** Print summary to stdout
    cat "$MD_OUTPUT"

    log "Telemetry proof complete!"
}

main "$@"
