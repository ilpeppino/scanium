***REMOVED*** Scanium Monitoring Stack Changelog

All notable changes to the monitoring stack (LGTM + Alloy) are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

***REMOVED******REMOVED*** [1.1.0] - 2026-01-14

***REMOVED******REMOVED******REMOVED*** Added

- **Spanmetrics connector** (GH-408): Alloy now generates `traces_spanmetrics_*` metrics from trace
  spans
    - `traces_spanmetrics_calls_total` - counter of span calls by service, span name, kind, and
      status
    - `traces_spanmetrics_latency_bucket` - histogram of span latencies
    - Enables the Traces Drilldown dashboard panels that were previously showing no data
    - Histogram buckets: 10ms, 25ms, 50ms, 100ms, 250ms, 500ms, 1s, 2.5s, 5s, 10s

***REMOVED******REMOVED******REMOVED*** Changed

- Updated Traces Drilldown dashboard to use `service_name` label (standard spanmetrics output)

***REMOVED******REMOVED******REMOVED*** Fixed

- Traces Drilldown dashboard: 10 of 14 panels now functional with span metrics data

***REMOVED******REMOVED*** [1.0.3] - 2026-01-14

***REMOVED******REMOVED******REMOVED*** Fixed

- Alloy healthcheck now uses bash TCP check instead of wget (17bbbb9)

***REMOVED******REMOVED*** [1.0.2] - 2026-01-10

***REMOVED******REMOVED******REMOVED*** Fixed

- Tempo compaction permission errors by running as root (GH-409, e209fe8)

***REMOVED******REMOVED*** [1.0.1] - 2026-01-10

***REMOVED******REMOVED******REMOVED*** Added

- Histogram metric support for ML inference latency (GH-407)

***REMOVED******REMOVED*** [1.0.0] - 2026-01-01

***REMOVED******REMOVED******REMOVED*** Added

- Initial LGTM stack deployment
    - Grafana Alloy v1.0.0 as OTLP receiver
    - Loki 2.9.3 for log storage
    - Tempo 2.7.0 for trace storage
    - Mimir 2.11.0 for metrics storage
    - Grafana for visualization
- Dashboards:
    - Backend API Performance
    - Backend Errors
    - Backend Health
    - Errors Overview
    - LGTM Stack Health
    - Logs Explorer
    - Mobile App Health
    - OpenAI Runtime
    - Ops Overview
    - Pipeline Health
    - Scan Performance
    - System Overview
    - Traces Drilldown
- Mobile OTLP telemetry ingestion (HTTP/gRPC)
- Backend OTLP telemetry ingestion
- Pipeline self-observability (scrape metrics from all LGTM services)
