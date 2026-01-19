***REMOVED*** Logs Explorer Fix Verification

**Date:** 2026-01-14
**Status:** VERIFIED

***REMOVED******REMOVED*** Fix Applied

Changed all `| json` parser calls to `| logfmt` in `logs-explorer.json` because logs are in logfmt
format, not JSON.

***REMOVED******REMOVED*** Verification Queries

***REMOVED******REMOVED******REMOVED*** Query 1: Error Count (should return > 0)

```bash
curl -sG http://127.0.0.1:3100/loki/api/v1/query \
  --data-urlencode 'query=sum(count_over_time({source="scanium-backend"} | logfmt | level=~"error|fatal" [10m]))'
```

**Result:** 107 errors in last 10 minutes

***REMOVED******REMOVED******REMOVED*** Query 2: Info Log Count (should return > 0)

```bash
curl -sG http://127.0.0.1:3100/loki/api/v1/query \
  --data-urlencode 'query=sum(count_over_time({source="scanium-backend"} | logfmt | level="info" [10m]))'
```

**Result:** 708 info logs in last 10 minutes

***REMOVED******REMOVED******REMOVED*** Query 3: Distribution by Level

```bash
curl -sG http://127.0.0.1:3100/loki/api/v1/query \
  --data-urlencode 'query=sum by (level) (count_over_time({source="scanium-backend"} | logfmt [10m]))'
```

**Result:**
| Level | Count (10m) |
|-------|-------------|
| error | 107 |
| info | 710 |
| warn | 62 |

***REMOVED******REMOVED*** Dashboard Panels Verified

| Panel ID | Panel Title           | Query Pattern                        | Status  |
|----------|-----------------------|--------------------------------------|---------|
| 3        | Errors (1h)           | `\| logfmt \| level=~"error\|fatal"` | WORKING |
| 4        | Warnings (1h)         | `\| logfmt \| level="warn"`          | WORKING |
| 5        | Error Rate (%)        | `\| logfmt \| level=~"error\|fatal"` | WORKING |
| 6        | Log Volume by Level   | `\| logfmt \| level="..."`           | WORKING |
| 7        | Level Distribution    | `\| logfmt`                          | WORKING |
| 9        | Error Rate Over Time  | `\| logfmt \| level=~"error\|fatal"` | WORKING |
| 10       | Top Error Types       | `\| logfmt \| level=~"error\|fatal"` | WORKING |
| 12       | Unique Error Messages | `\| logfmt \| level=~"error\|fatal"` | WORKING |
| 14       | Live Log Stream       | `\| logfmt \| level=~"$level"`       | WORKING |

***REMOVED******REMOVED*** Grafana Restart

```bash
docker-compose restart grafana
***REMOVED*** Restarting scanium-grafana ... done
```

***REMOVED******REMOVED*** Conclusion

All Logs Explorer panels that filter by `level` now return data correctly.
