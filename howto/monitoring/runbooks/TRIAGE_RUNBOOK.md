***REMOVED*** Sentry → Grafana Triage Runbook

Step-by-step workflow for investigating Sentry issues using the full observability stack.

***REMOVED******REMOVED*** Quick Reference

| Tool    | Purpose                           | URL                       |
|---------|-----------------------------------|---------------------------|
| Sentry  | Crash reports, stack traces       | Your Sentry project URL   |
| Grafana | Logs, metrics, traces, dashboards | http://localhost:3000     |
| Loki    | Log queries                       | Grafana → Explore → Loki  |
| Tempo   | Distributed traces                | Grafana → Explore → Tempo |
| Mimir   | Metrics queries                   | Grafana → Explore → Mimir |

***REMOVED******REMOVED*** Triage Workflow Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  1. SENTRY ALERT                                                │
│     └── Crash spike / New issue / Regression                   │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│  2. EXTRACT CONTEXT FROM SENTRY                                 │
│     └── session_id, app_version, platform, env, trace_id       │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│  3. JUMP TO GRAFANA                                             │
│     └── Filter dashboards by extracted values                  │
└────────────────────────┬────────────────────────────────────────┘
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  Loki Logs  │  │ Tempo Traces│  │Mimir Metrics│
│  (context)  │  │  (timing)   │  │  (trends)   │
└─────────────┘  └─────────────┘  └─────────────┘
         │               │               │
         └───────────────┼───────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│  4. ROOT CAUSE ANALYSIS                                         │
│     └── Correlate data across all sources                      │
└─────────────────────────────────────────────────────────────────┘
```

***REMOVED******REMOVED*** Step 1: Receive Sentry Alert

When you receive a Sentry notification:

1. Click the issue link to open in Sentry
2. Note the issue summary (exception type, message)
3. Check the issue stats (frequency, affected users)

***REMOVED******REMOVED*** Step 2: Extract Context from Sentry Issue

***REMOVED******REMOVED******REMOVED*** Key Tags to Extract

In the Sentry issue detail view, locate these tags in the sidebar:

| Tag           | Example                      | Use For                                |
|---------------|------------------------------|----------------------------------------|
| `session_id`  | `cls-550e8400-e29b-41d4-...` | Find all events from same user session |
| `app_version` | `1.0.42`                     | Filter logs/metrics by version         |
| `build`       | `42`                         | Precise build identification           |
| `platform`    | `android`                    | Platform filtering                     |
| `env`         | `prod`                       | Environment isolation                  |
| `scan_mode`   | `CLOUD`                      | Classification mode context            |

***REMOVED******REMOVED******REMOVED*** Extract from Event Details

1. **Tags Panel:** Click "Tags" in sidebar to see all tags
2. **Breadcrumbs:** Review recent app events before crash
3. **Diagnostics Attachment:** Download `diagnostics.json` for full context
4. **Device Info:** Note device model, OS version
5. **Release:** Note exact release version (`com.scanium.app@1.0.42+42`)

***REMOVED******REMOVED******REMOVED*** Copy Session ID

The `session_id` tag is the primary correlation key:

```
session_id: cls-550e8400-e29b-41d4-a716-446655440000
```

Copy this value for Grafana queries.

***REMOVED******REMOVED*** Step 3: Jump to Grafana

***REMOVED******REMOVED******REMOVED*** Option A: App Health Dashboard (Recommended Start)

1. Open Grafana: http://localhost:3000
2. Navigate to: Dashboards → Scanium → App Health
3. Set filters at top:
    - **Environment:** `prod` (or `dev`)
    - **Version:** `1.0.42` (from Sentry)
    - **Time range:** 30 minutes around crash timestamp

***REMOVED******REMOVED******REMOVED*** Option B: Direct Loki Query (Session-Specific)

1. Open Grafana Explore: http://localhost:3000/explore
2. Select **Loki** datasource
3. Run query with `session_id`:

```logql
{source="scanium-mobile", session_id="cls-550e8400-e29b-41d4-a716-446655440000"}
```

***REMOVED******REMOVED******REMOVED*** Option C: Build URL with Filters

Construct a direct URL to Grafana Explore with pre-filled filters:

```
http://localhost:3000/explore?left=["now-1h","now","Loki",{"expr":"{source=\"scanium-mobile\", session_id=\"cls-550e8400-...\"}"} ]
```

***REMOVED******REMOVED*** Step 4: Loki Log Analysis

***REMOVED******REMOVED******REMOVED*** Query by Session ID

Find all logs from the affected session:

```logql
{source="scanium-mobile", session_id="cls-550e8400-e29b-41d4-a716-446655440000"}
```

***REMOVED******REMOVED******REMOVED*** Query by Version (Broader)

Find patterns across all users on a version:

```logql
{source="scanium-mobile", app_version="1.0.42", env="prod"}
| json
| level="ERROR"
```

***REMOVED******REMOVED******REMOVED*** Query with Context Lines

Get surrounding context for errors:

```logql
{source="scanium-mobile", session_id="cls-550e8400-..."}
| json
| line_format "{{.timestamp}} [{{.level}}] {{.message}}"
```

***REMOVED******REMOVED******REMOVED*** Common Filter Patterns

```logql
***REMOVED*** Errors only
{source="scanium-mobile", env="prod"} |= "ERROR"

***REMOVED*** Classification events
{source="scanium-mobile"} |= "scan." or |= "classification."

***REMOVED*** Network errors
{source="scanium-mobile"} |= "network" or |= "timeout" or |= "connection"

***REMOVED*** ML inference issues
{source="scanium-mobile"} |= "inference" or |= "model"
```

***REMOVED******REMOVED******REMOVED*** Parse JSON Logs

```logql
{source="scanium-mobile", env="prod"}
| json
| level="ERROR"
| line_format "{{.timestamp}} {{.name}}: {{.message}}"
```

***REMOVED******REMOVED*** Step 5: Tempo Trace Analysis

***REMOVED******REMOVED******REMOVED*** Find Traces by Session

In Grafana Explore with Tempo datasource:

1. Select **Search** tab
2. Filter by:
    - `service.name = scanium-mobile`
    - `session_id = cls-550e8400-...` (custom attribute)
3. Click trace to view spans

***REMOVED******REMOVED******REMOVED*** Trace Analysis Checklist

- [ ] Check span durations for anomalies
- [ ] Look for error spans (red)
- [ ] Check gaps between spans (blocking?)
- [ ] Verify network call timing
- [ ] Check ML inference duration

***REMOVED******REMOVED******REMOVED*** Link from Logs to Traces

If logs include `trace_id`:

```logql
{source="scanium-mobile", session_id="cls-..."} | json | trace_id!=""
```

Click the trace ID in results to jump to Tempo.

***REMOVED******REMOVED*** Step 6: Mimir Metrics Analysis

***REMOVED******REMOVED******REMOVED*** Query Inference Latency

```promql
***REMOVED*** P95 inference latency by version
histogram_quantile(0.95,
  sum by(le, app_version) (
    rate(ml_inference_latency_ms_bucket{env="prod"}[5m])
  )
)
```

***REMOVED******REMOVED******REMOVED*** Query Error Rate by Version

```promql
***REMOVED*** Error rate per version
sum by(app_version) (
  rate(scanium_errors_total{env="prod"}[5m])
)
```

***REMOVED******REMOVED******REMOVED*** Query Session Counts

```promql
***REMOVED*** Active sessions per version
count by(app_version) (
  scanium_active_sessions{env="prod"}
)
```

***REMOVED******REMOVED******REMOVED*** Compare Versions

```promql
***REMOVED*** Error rate comparison: affected vs baseline
sum(rate(scanium_errors_total{app_version="1.0.42", env="prod"}[5m]))
/
sum(rate(scanium_errors_total{app_version="1.0.41", env="prod"}[5m]))
```

***REMOVED******REMOVED*** Step 7: Correlate and Diagnose

***REMOVED******REMOVED******REMOVED*** Correlation Table

Build a timeline of events:

| Time     | Source | Event                                 |
|----------|--------|---------------------------------------|
| 10:29:50 | Loki   | `scan.started` with `scan_mode=LOCAL` |
| 10:29:52 | Tempo  | Inference span started                |
| 10:29:54 | Mimir  | Latency spike to 3000ms               |
| 10:29:55 | Loki   | `ERROR: OutOfMemoryError`             |
| 10:29:55 | Sentry | Crash captured                        |

***REMOVED******REMOVED******REMOVED*** Common Root Causes

| Symptom                      | Likely Cause              | Where to Look               |
|------------------------------|---------------------------|-----------------------------|
| Crashes after version update | Regression in new code    | Sentry release compare      |
| Timeouts on network calls    | Backend latency/errors    | Tempo spans, backend logs   |
| OOM crashes                  | Memory leak, large models | Heap dumps, model size      |
| Inference failures           | Model compatibility       | Loki ML logs, model version |
| Random crashes               | Race conditions           | Breadcrumbs, thread info    |

***REMOVED******REMOVED*** Step 8: Document and Resolve

***REMOVED******REMOVED******REMOVED*** Update Sentry Issue

1. Add findings to issue comments
2. Link to Grafana dashboard/queries
3. Assign to appropriate owner
4. Set priority based on impact

***REMOVED******REMOVED******REMOVED*** Create Fix

1. Reference Sentry issue ID in commit message
2. Include relevant log excerpts
3. Tag fix version in Sentry

***REMOVED******REMOVED******REMOVED*** Verify Fix

After deploying fix:

1. Monitor Sentry for regression
2. Compare error rates in Grafana
3. Verify in new version builds

***REMOVED******REMOVED*** Dashboard Quick Links

***REMOVED******REMOVED******REMOVED*** Pre-built Dashboards

| Dashboard        | Purpose                        | Path                                    |
|------------------|--------------------------------|-----------------------------------------|
| App Health       | Overall app health metrics     | Dashboards → Scanium → App Health       |
| Scan Performance | Classification latency/success | Dashboards → Scanium → Scan Performance |
| Pipeline Health  | OTLP pipeline status           | Dashboards → Scanium → Pipeline Health  |
| Usage            | User activity patterns         | Dashboards → Scanium → Usage            |

***REMOVED******REMOVED******REMOVED*** Useful Explore Queries

Save these as Grafana "starred" queries:

**Loki - Errors by Session:**

```logql
{source="scanium-mobile", env="prod"} |= "ERROR" | json | line_format "{{.session_id}}: {{.message}}"
```

**Loki - Classification Events:**

```logql
{source="scanium-mobile"} |~ "scan\\.(started|completed|failed)"
```

**Mimir - Error Rate Trend:**

```promql
sum(increase(scanium_errors_total{env="prod"}[1h])) by (app_version)
```

***REMOVED******REMOVED*** Troubleshooting Common Issues

***REMOVED******REMOVED******REMOVED*** No Logs Found for Session ID

1. **Check time range:** Ensure it covers the crash timestamp
2. **Check OTLP export:** Was telemetry enabled in that build?
3. **Check user consent:** Did user have "Share Diagnostics" enabled?
4. **Check Loki retention:** Data older than 14 days is purged

***REMOVED******REMOVED******REMOVED*** Missing Traces

1. **Check sampling rate:** Dev = 100%, Prod = 10%
2. **Check trace propagation:** Is `trace_id` included in logs?
3. **Check Tempo retention:** 7-day default

***REMOVED******REMOVED******REMOVED*** Metrics Not Matching Logs

1. **Check aggregation windows:** Metrics use 1m/5m buckets
2. **Check label cardinality:** High cardinality may cause drops
3. **Check time alignment:** Use same time range everywhere

***REMOVED******REMOVED*** Escalation Path

| Severity              | Action                                       | Contact             |
|-----------------------|----------------------------------------------|---------------------|
| P1 (Crash spike)      | Immediate investigation + potential rollback | On-call + team lead |
| P2 (New critical bug) | Same-day investigation                       | Assigned owner      |
| P3 (Regression)       | Next sprint                                  | Backlog             |
| P4 (Edge case)        | When capacity allows                         | Backlog             |

***REMOVED******REMOVED*** See Also

- [SENTRY_ALERTING.md](./SENTRY_ALERTING.md) - Sentry configuration and alert rules
- [monitoring/README.md](../../monitoring/README.md) - LGTM stack setup
- [docs/telemetry/CONTRACT.md](../telemetry/CONTRACT.md) - Telemetry event definitions
