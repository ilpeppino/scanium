***REMOVED*** Incident Report: Alloy Persistence & Healthcheck Fix

**Date:** 2026-01-10
**Severity:** P1 - Critical (Monitoring system broken after restarts)
**Status:** RESOLVED

***REMOVED******REMOVED*** Summary

Fixed two critical issues preventing Grafana Alloy from functioning correctly after container
restarts:

1. **Ephemeral storage** causing Docker log replay storms and Loki rate-limit errors
2. **Broken healthcheck** causing permanent UNHEALTHY status (319 consecutive failures)

***REMOVED******REMOVED*** Problem Statement

***REMOVED******REMOVED******REMOVED*** Before Fix

**Healthcheck Status:**

- Container status: `Up 54 minutes (unhealthy)`
- Healthcheck failing: FailingStreak 319
- Error: `exec: "wget": executable file not found in $PATH`

**Log Replay Storm:**
After each Alloy restart, Docker container logs were replayed from the beginning, causing:

- Massive "entry too far behind" errors (logs from 2 days ago being rejected)
- "429 Too Many Requests" from Loki (ingestion rate limit exceeded)
- 1MB+ of stale log data being rejected per batch

**Root Causes:**

1. Alloy storage path `/var/lib/alloy/data` was not mounted persistently → lost tail positions on
   restart
2. Healthcheck used `wget` command which doesn't exist in `grafana/alloy:v1.0.0` image

***REMOVED******REMOVED*** Changes Implemented

***REMOVED******REMOVED******REMOVED*** 1. Added Persistent Storage Bind Mount

**File:** `monitoring/docker-compose.yml`
**Change:** Added volume mount to persist Alloy internal state

```diff
     volumes:
       - ./alloy/alloy.hcl:/etc/alloy/config.alloy:ro
       - /var/run/docker.sock:/var/run/docker.sock:ro
+      - ./data/alloy:/var/lib/alloy/data
```

**Created Directory:** `/volume1/docker/scanium/repo/monitoring/data/alloy`

**Persisted State Contents:**

- `alloy_seed.json` - Alloy internal state
- `loki.source.docker.backend/` - Docker log tail positions (prevents replay)
- `prometheus.remote_write.*/` - Prometheus WAL segments

***REMOVED******REMOVED******REMOVED*** 2. Fixed Healthcheck Command

**File:** `monitoring/docker-compose.yml`
**Change:** Replaced `wget` with `bash` + TCP socket test

```diff
     healthcheck:
-      test: ["CMD", "wget", "--spider", "-q", "http://localhost:12345/-/ready"]
+      test: ["CMD", "bash", "-c", "exec 3<>/dev/tcp/localhost/12345 && echo -e 'GET /-/ready HTTP/1.0\\r\\n\\r\\n' >&3 && timeout 2 cat <&3 | grep -q '200 OK'"]
       interval: 10s
       timeout: 5s
       retries: 3
       start_period: 10s
```

**Rationale:**

- `grafana/alloy:v1.0.0` doesn't include `wget` or `curl`
- `bash` is available and supports `/dev/tcp` pseudo-device
- Sends HTTP GET request to `/-/ready` endpoint and validates 200 OK response

***REMOVED******REMOVED*** Verification Results

***REMOVED******REMOVED******REMOVED*** Healthcheck Status: ✅ RESOLVED

**Before:**

```
scanium-alloy  Up 54 minutes (unhealthy)
FailingStreak: 319
```

**After:**

```
scanium-alloy  Up 21 seconds (healthy)
Status: "healthy"
```

**After Restart:**

```
scanium-alloy  Up 19 seconds (healthy)
Status: "healthy"
```

***REMOVED******REMOVED******REMOVED*** Storage Persistence: ✅ VERIFIED

**NAS Directory (after initial start):**

```
/volume1/docker/scanium/repo/monitoring/data/alloy/
├── alloy_seed.json
├── loki.source.docker.backend/
├── prometheus.remote_write.backend/
├── prometheus.remote_write.mobile/
├── prometheus.remote_write.pipeline/
└── remotecfg/
```

**Inside Container:**

- Mount point: `/var/lib/alloy/data`
- Write test: ✅ Created `.persist_test` file successfully
- Data survives restart: ✅ All files present after restart with updated timestamps

***REMOVED******REMOVED******REMOVED*** Replay Storm Elimination: ✅ VERIFIED

**Before Restart (baseline logs):**

```
error="entry with timestamp 2026-01-08 14:15:26 ignored, reason: 'entry too far behind'"
error="server returned HTTP status 429 Too Many Requests (429): Ingestion rate limit exceeded"
```

**After Restart (with persistence):**

```
No replay storm errors found!
grep -iE 'entry too far behind|429|Too Many Requests|rate limit exceeded'
→ No matches
```

**Loki Status:**

```
Loki is clean - no rate limit errors!
```

***REMOVED******REMOVED*** How to Verify (Post-Deployment)

***REMOVED******REMOVED******REMOVED*** 1. Check Container Health

```bash
ssh nas
docker ps --format "table {{.Names}}\t{{.Status}}" | grep alloy
***REMOVED*** Expected: scanium-alloy  Up X seconds (healthy)
```

***REMOVED******REMOVED******REMOVED*** 2. Verify Persistent Storage

```bash
***REMOVED*** On NAS
ls -la /volume1/docker/scanium/repo/monitoring/data/alloy/
***REMOVED*** Should show: alloy_seed.json, loki.source.docker.backend/, etc.

***REMOVED*** Inside container
docker exec scanium-alloy ls -la /var/lib/alloy/data
***REMOVED*** Should show same files
```

***REMOVED******REMOVED******REMOVED*** 3. Test Restart (No Replay Storm)

```bash
cd /volume1/docker/scanium/repo/monitoring
docker-compose restart alloy
sleep 15
docker ps | grep alloy  ***REMOVED*** Should show (healthy)
docker logs --tail 100 scanium-alloy | grep -iE "429|too far behind"
***REMOVED*** Expected: No matches (or minimal, not sustained spam)
```

***REMOVED******REMOVED******REMOVED*** 4. Verify Healthcheck Command

```bash
docker inspect scanium-alloy | jq '.[0].State.Health'
***REMOVED*** Expected: "Status": "healthy"
```

***REMOVED******REMOVED*** Impact

**Before Fix:**

- Alloy permanently UNHEALTHY (319 failed healthchecks)
- Every restart caused log replay storm
- Loki ingestion rate-limited (429 errors)
- Monitoring gaps after each power cycle/restart

**After Fix:**

- Alloy HEALTHY within 20 seconds of start
- Zero replay storms (logs resume from last position)
- Zero Loki rate-limit errors
- Monitoring system resilient to NAS power cycles

***REMOVED******REMOVED*** Files Changed

- `monitoring/docker-compose.yml` - Added persistent storage + fixed healthcheck
- `monitoring/incident_data/alloy_before.log` - Baseline logs showing replay storm
- `monitoring/incident_data/alloy_after_restart.log` - Clean logs after fix
- `monitoring/incident_data/loki_after_restart.log` - Loki clean after fix

***REMOVED******REMOVED*** Next Steps

**Completed:**

- ✅ Persistent storage configured
- ✅ Healthcheck working reliably
- ✅ Restart resilience verified
- ✅ No replay storm confirmed

**Out of Scope (as per requirements):**

- ❌ Loki limits tuning (not needed - no more replay storms)
- ❌ Alloy batching optimization (not needed - working correctly)
- ❌ Dashboard updates (not required for this fix)

***REMOVED******REMOVED*** Related Documentation

- Persistence Audit Report: `monitoring/incident_data/AUDIT_persistence_after_power_cycles.md`
- Original Issue: Monitoring breaking after NAS restarts
- Root Cause: Ephemeral Alloy storage + broken healthcheck

---
**Report Author:** Claude Sonnet 4.5
**Verified On:** 2026-01-10 14:20 CET
**Commit:** (pending - see git log after push)
