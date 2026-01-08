***REMOVED*** Grafana No-Data Root Cause and Fix

***REMOVED******REMOVED*** Root Cause
- Backend metrics were not being scraped into Mimir, so dashboard queries returned empty series.
- Backend HTTP metrics and traces were not emitted automatically (no request-level instrumentation hook), so even local `/metrics` had no HTTP samples.
- OpenAI dashboards queried `openai_*` metrics that do not exist; the backend exports `scanium_assistant_*` instead.
- Loki logs are not ingested because the backend container uses the Synology `db` logging driver (not readable by `loki.source.docker`).

***REMOVED******REMOVED*** Fix Applied
- Added Fastify hooks to emit HTTP metrics and request spans in `backend/src/app.ts`.
- Added Alloy backend scrape and a dedicated remote_write with labels in `monitoring/alloy/config.alloy`.
- Updated dashboards to use `scanium_http_*` and `scanium_assistant_*` metrics:
  - `monitoring/grafana/dashboards/backend-api-performance.json`
  - `monitoring/grafana/dashboards/backend-errors.json`
  - `monitoring/grafana/dashboards/system-overview.json`
  - `monitoring/grafana/dashboards/traces-drilldown.json`
  - `monitoring/grafana/dashboards/openai-runtime.json`
- Added a docker socket mount for Alloy in `deploy/nas/compose/docker-compose.nas.monitoring.yml` to enable future log ingestion (requires a compatible logging driver).

***REMOVED******REMOVED*** How to Validate
1) Generate traffic:
   - `curl http://127.0.0.1:8080/v1/config` (repeat a few times)
2) Metrics (Mimir):
   - Query: `sum(rate(scanium_http_requests_total[5m]))`
3) Traces (Tempo):
   - Search for `scanium-backend` in the last 15 minutes.
4) Dashboards:
   - System Overview / Backend API Performance / Backend Errors should now show data.

***REMOVED******REMOVED*** Logs Status
- Backend logs are now exported via OTLP and also available via Docker `json-file` logging.
- Loki queries like `{source="scanium-backend"}` should return recent entries.

***REMOVED******REMOVED*** Regression Prevention
- Keep `/metrics` scrape active in Alloy for backend.
- Ensure any new dashboards reference `scanium_*` metrics unless exporters are changed.
