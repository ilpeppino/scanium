# Dashboard Wiring Audit

## Dashboard inventory
- monitoring/grafana/dashboards/backend-api-performance.json
- monitoring/grafana/dashboards/backend-errors.json
- monitoring/grafana/dashboards/backend-health.json
- monitoring/grafana/dashboards/errors.json
- monitoring/grafana/dashboards/lgtm-stack-health.json
- monitoring/grafana/dashboards/logs-explorer.json
- monitoring/grafana/dashboards/mobile-app-health.json
- monitoring/grafana/dashboards/openai-runtime.json
- monitoring/grafana/dashboards/ops-overview.json
- monitoring/grafana/dashboards/pipeline-health.json
- monitoring/grafana/dashboards/scan-performance.json
- monitoring/grafana/dashboards/system-overview.json
- monitoring/grafana/dashboards/traces-drilldown.json

## Datasource UID mapping
- MIMIR: prometheus
- LOKI: loki
- TEMPO: tempo

## Fixes applied
- monitoring/grafana/dashboards/backend-errors.json: updated TraceQL drilldown query and Explore link to use `status = error` and regex match for `resource.service.name`.
- monitoring/grafana/dashboards/pipeline-health.json: changed Tempo trace filter to use regex match on `resource.service.name`.
- monitoring/grafana/dashboards/scan-performance.json: changed Tempo trace filter to use regex match on `span.http.method`.
- monitoring/grafana/dashboards/traces-drilldown.json: updated TraceQL error filter to use `status = error`.
