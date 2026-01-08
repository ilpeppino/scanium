# Grafana No-Data Root Cause and Fix

## Root Cause
- Backend metrics were not being scraped into Mimir, so dashboard queries returned empty series.
- Backend HTTP metrics and traces were not emitted automatically (no request-level instrumentation hook), so even local `/metrics` had no HTTP samples.
- OpenAI dashboards queried `openai_*` metrics that do not exist; the backend exports `scanium_assistant_*` instead.
- Loki logs are not ingested because the backend container uses the Synology `db` logging driver (not readable by `loki.source.docker`).

## Fix Applied
- Added Fastify hooks to emit HTTP metrics and request spans in `backend/src/app.ts`.
- Added Alloy backend scrape and a dedicated remote_write with labels in `monitoring/alloy/config.alloy`.
- Updated dashboards to use `scanium_http_*` and `scanium_assistant_*` metrics:
  - `monitoring/grafana/dashboards/backend-api-performance.json`
  - `monitoring/grafana/dashboards/backend-errors.json`
  - `monitoring/grafana/dashboards/system-overview.json`
  - `monitoring/grafana/dashboards/traces-drilldown.json`
  - `monitoring/grafana/dashboards/openai-runtime.json`
- Added a docker socket mount for Alloy in `deploy/nas/compose/docker-compose.nas.monitoring.yml` to enable future log ingestion (requires a compatible logging driver).

## How to Validate
1) Generate traffic:
   - `curl http://127.0.0.1:8080/v1/config` (repeat a few times)
2) Metrics (Mimir):
   - Query: `sum(rate(scanium_http_requests_total[5m]))`
3) Traces (Tempo):
   - Search for `scanium-backend` in the last 15 minutes.
4) Dashboards:
   - System Overview / Backend API Performance / Backend Errors should now show data.

## Remaining Gap (Logs)
- Loki ingestion from Docker logs is blocked by the `db` log driver.
- To enable Logs Explorer, change the backend container log driver to `json-file` or ship logs via OTLP.

## Regression Prevention
- Keep `/metrics` scrape active in Alloy for backend.
- Ensure any new dashboards reference `scanium_*` metrics unless exporters are changed.
