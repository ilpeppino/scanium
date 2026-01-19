***REMOVED*** Monitoring Stack HOWTO

***REMOVED******REMOVED*** 1. Overview

Scanium uses a Grafana LGTM stack with Grafana Alloy as the OTLP receiver and router. It captures:

- Android app telemetry (logs, metrics, traces) over OTLP.
- Backend telemetry over OTLP plus Prometheus scrape of `/metrics`.
- System and pipeline health metrics (Alloy, Loki, Tempo, Mimir).

Key configs live under `monitoring/` and are provisioned via Docker Compose.

***REMOVED******REMOVED*** 2. Stack Components

Core services (see `monitoring/docker-compose.yml`):

- Grafana: dashboards and alerting (`monitoring/grafana/`).
- Alloy: OTLP receiver and router (`monitoring/alloy/config.alloy`).
- Loki: log storage (`monitoring/loki/loki.yaml`).
- Tempo: trace storage (`monitoring/tempo/tempo.yaml`).
- Mimir: metrics storage (`monitoring/mimir/mimir.yaml`).

OTLP receivers in `monitoring/alloy/config.alloy`:

- Mobile OTLP: `0.0.0.0:4318` (HTTP) and `0.0.0.0:4317` (gRPC).
- Backend OTLP: `0.0.0.0:4319` (HTTP) for NAS deployments.

***REMOVED******REMOVED*** 3. Metrics

Sources and flows:

- Backend exports OTLP metrics (`backend/src/infra/telemetry/index.ts`) and exposes `/metrics` for
  Prometheus scrape.
- Alloy scrapes:
    - Alloy, Loki, Tempo, Mimir internal metrics.
    - Backend `/metrics` (`prometheus.scrape "backend"` in `config.alloy`).
- Metrics are written to Mimir via Prometheus remote_write.

Retention:

- Mimir retention is 15 days (`monitoring/mimir/mimir.yaml`).

***REMOVED******REMOVED*** 4. Logs

Sources:

- Android app OTLP logs exported via `AndroidLogPortOtlp` to Alloy.
- Backend OTLP logs via OpenTelemetry SDK.
- Backend container logs shipped via `loki.source.docker` in Alloy (filtered to `scanium-backend`).

Retention:

- Loki retention is 72 hours (`monitoring/loki/loki.yaml`).

PII considerations:

- Mobile telemetry attributes are sanitized client-side (see `shared/telemetry-contract` and
  `androidApp/src/main/java/com/scanium/app/telemetry/`).
- Backend logging redacts sensitive headers (`backend/src/app.ts`).

***REMOVED******REMOVED*** 5. Dashboards

Provisioned dashboards live in `monitoring/grafana/dashboards/`, including:

- `ops-overview.json`
- `mobile-app-health.json`
- `backend-health.json`
- `backend-errors.json`
- `scan-performance.json`
- `traces-drilldown.json`
- `pipeline-health.json`

Datasources are provisioned in `monitoring/grafana/provisioning/datasources/datasources.yaml` with
Loki, Tempo, and Mimir wired for trace/log/metrics correlations.

***REMOVED******REMOVED*** 6. Alerts & Health

Alerting is provisioned via Grafana:

- Rules: `monitoring/grafana/provisioning/alerting/rules.yaml` (error rate spikes, telemetry drop,
  latency regression).
- Contact points: `monitoring/grafana/provisioning/alerting/contactpoints.yaml` (placeholders by
  default).
- Notification policies: `monitoring/grafana/provisioning/alerting/notification-policies.yaml`.

Health endpoints:

- Backend: `/health`, `/healthz`, `/readyz`, `/metrics` (`backend/src/modules/health/routes.ts`).
- Alloy: `/-/ready` on port 12345 (container-only).
- Loki/Tempo/Mimir: `/ready` endpoints (localhost-bound in compose).

***REMOVED******REMOVED*** 7. Security

Port exposure (local dev `monitoring/docker-compose.yml`):

- Public: Grafana `:3000`, Alloy OTLP `:4317/:4318`.
- Localhost-only: Loki `:3100`, Tempo `:3200`, Mimir `:9009`, Alloy UI `:12345`.

Grafana auth:

- Local dev enables anonymous admin (see `monitoring/docker-compose.yml`).
- NAS deployment uses env-based credentials (
  `deploy/nas/compose/docker-compose.nas.monitoring.yml`).

NAS hardening:

- Use Cloudflare Tunnel or VPN for Grafana access; avoid exposing Loki/Tempo/Mimir to the public
  internet.

***REMOVED******REMOVED*** 8. Interaction With Other Components

Android app -> Monitoring:

- OTLP logs/metrics/traces to Alloy using `BuildConfig.OTLP_ENDPOINT` and `OTLP_ENABLED`.
- Trace headers injected into backend calls via `TraceContextInterceptor`.

Backend -> Monitoring:

- OTLP export to Alloy (`OTEL_EXPORTER_OTLP_ENDPOINT`).
- `/metrics` scraped by Alloy for Prometheus/Mimir.

Config mismatch to note:

- Local backend compose uses `OTEL_EXPORTER_OTLP_ENDPOINT=http://alloy:4318` while Alloy config
  expects backend OTLP on `:4319`.
- NAS compose uses `http://scanium-alloy:4319` and matches `config.alloy`.

***REMOVED******REMOVED*** 9. Operational Notes

Disk usage:

- Loki and Tempo are the primary disk consumers. Retention values are tuned for NAS constraints.

Retention tuning:

- Loki: adjust `limits_config.retention_period` in `monitoring/loki/loki.yaml`.
- Tempo: adjust `compactor.compaction.block_retention` in `monitoring/tempo/tempo.yaml`.
- Mimir: adjust `blocks_storage.tsdb.retention_period` in `monitoring/mimir/mimir.yaml`.

Backups:

- Persisted data lives in `monitoring/data/*` (local) or `/volume1/docker/scanium/monitoring/*` (
  NAS). Snapshot these directories for backups.
