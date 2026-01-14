# Log Level Schema Proof

**Date:** 2026-01-14
**Investigator:** Observability Agent

## Summary

The Logs Explorer dashboard panels that filter by `level` return no data because the queries use `| json` parser, but the logs from `source="scanium-backend"` are in **logfmt** format (key=value pairs), not JSON.

## PHASE 1: Is `level` a Loki Label?

**Result: NO**

```bash
$ ssh nas "curl -s http://127.0.0.1:3100/loki/api/v1/labels | jq -r '.data[]' | sort"
env
source
```

Only `env` and `source` are Loki labels. `level` is NOT a Loki label.

## PHASE 2: Log Format Analysis

### Raw Log Samples

```
# Sample 1 (Grafana alerting):
logger=ngalert.sender.router rule_uid=pipeline-tempo-down org_id=1 t=2026-01-14T22:43:09.518893224Z level=info msg="Sending alerts to local notifier" count=1

# Sample 2 (Mimir query frontend):
ts=2026-01-14T22:43:09.482737873Z caller=handler.go:324 level=info user=anonymous msg="query stats" component=query-frontend method=POST path=/prometheus/api/v1/query_range ...

# Sample 3 (Loki query engine):
level=info ts=2026-01-14T22:43:08.435496821Z caller=metrics.go:159 component=querier org_id=fake traceID=7137c58c2ff4b613 latency=fast query="sum(count_over_time(..." ...

# Sample 4 (Error log):
logger=ngalert.notifier.alertmanager 1=(MISSING) t=2026-01-14T22:43:08.306409508Z level=error component=alertmanager orgID=1 component=dispatcher msg="Notify for alerts failed" ...
```

### Findings

| Question | Answer | Evidence |
|----------|--------|----------|
| Is `level` a Loki label? | **NO** | Only `env` and `source` in `/loki/api/v1/labels` |
| Log format | **logfmt** (key=value) | Lines do NOT start with `{`, use `key=value` syntax |
| Is `level` in log content? | **YES** | `level=info`, `level=error` present in all samples |
| `level` value type | **STRING** | Values: `info`, `error`, `warn` (not numeric) |

### Alternative Fields Found

| Field | Present | Example |
|-------|---------|---------|
| `level` | YES | `level=info`, `level=error` |
| `msg` | YES | `msg="query stats"` |
| `component` | YES | `component=querier` |
| `caller` | YES | `caller=metrics.go:159` |

## Root Cause

The Logs Explorer dashboard queries use:
```logql
{source=~"$source", env=~"$env"} | json | level=~"error|fatal"
```

But logs are in **logfmt** format, not JSON. The `| json` parser fails silently, and the `level` field filter never matches.

## Fix Path

**Path A - Dashboard-only fix (minimal)**

Change all occurrences of `| json` to `| logfmt` in `logs-explorer.json`.

This is the minimal safe fix because:
1. No backend/ingestion changes required
2. Level values are already strings matching the filter values (info/warn/error)
3. Logfmt parser correctly extracts key=value fields
