# Grafana Dashboard Access Guide

**Updated**: 2026-01-11
**Grafana URL**: http://REDACTED_INTERNAL_IP:3000

---

## Quick Links

### Main Navigation

- **Home**: http://REDACTED_INTERNAL_IP:3000/
- **All Dashboards**: http://REDACTED_INTERNAL_IP:3000/dashboards
- **Scanium Folder**: http://REDACTED_INTERNAL_IP:3000/dashboards?folderIds=2

### Alternative Folder URLs

Both of these work:

- http://REDACTED_INTERNAL_IP:3000/dashboards/f/scanium/
- http://REDACTED_INTERNAL_IP:3000/dashboards/f/scanium/scanium

---

## All Scanium Dashboards (13 Total)

### Backend Monitoring

1. **Backend Errors** ⭐ (Fixed today)
    - http://REDACTED_INTERNAL_IP:3000/d/scanium-backend-errors/scanium-backend-errors
    - Error analysis: 4xx vs 5xx, error routes, logs, traces

2. **Backend Health**
    - http://REDACTED_INTERNAL_IP:3000/d/scanium-backend-health/scanium-backend-health
    - Health checks, uptime, error rates

3. **Backend API Performance**
    - http://REDACTED_INTERNAL_IP:3000/d/scanium-backend-api-perf/scanium-backend-api-performance
    - Request latency, throughput, error rates by route

### System Overview

4. **System Overview (RED)**
    - http://REDACTED_INTERNAL_IP:3000/d/scanium-system-overview/scanium-system-overview-red
    - Rate, Errors, Duration - high-level metrics

5. **Ops Overview**
    - http://REDACTED_INTERNAL_IP:3000/d/scanium-ops-overview/scanium-ops-overview
    - Operational overview across all services

### Performance & Reliability

6. **Performance & Latency**
    - http://REDACTED_INTERNAL_IP:3000/d/scanium-scan-performance/scanium-performance-and-latency
    - Latency percentiles, slow requests

7. **Errors & Failures**
    - http://REDACTED_INTERNAL_IP:3000/d/scanium-errors/scanium-errors-and-failures
    - Cross-service error tracking

8. **Pipeline Health**
    - http://REDACTED_INTERNAL_IP:3000/d/scanium-pipeline-health/scanium-pipeline-health
    - Data processing pipeline metrics

### Mobile & External Services

9. **Mobile App Health**
    - http://REDACTED_INTERNAL_IP:3000/d/scanium-mobile-app-health/scanium-mobile-app-health
    - Mobile app telemetry and health

10. **OpenAI Runtime**
    - http://REDACTED_INTERNAL_IP:3000/d/scanium-openai-runtime/scanium-openai-runtime
    - AI service metrics, latency, costs

### Observability Stack

11. **LGTM Stack Health**
    - http://REDACTED_INTERNAL_IP:3000/d/scanium-lgtm-health/scanium-lgtm-stack-health
    - Loki, Grafana, Tempo, Mimir monitoring

12. **Logs Explorer**
    - http://REDACTED_INTERNAL_IP:3000/d/scanium-logs-explorer/scanium-logs-explorer
    - Log search and analysis

13. **Traces Drilldown**
    - http://REDACTED_INTERNAL_IP:3000/d/scanium-traces-drilldown/scanium-traces-drilldown
    - Distributed trace analysis

---

## Troubleshooting Access Issues

### Issue: "Can't access folder URL"

**Symptom**: Navigating to http://REDACTED_INTERNAL_IP:3000/dashboards/f/scanium/ doesn't work

**Solutions**:

#### Option 1: Use the Dashboards List (Recommended)

1. Go to http://REDACTED_INTERNAL_IP:3000/dashboards
2. You'll see all 13 Scanium dashboards grouped in the "Scanium" folder
3. Click any dashboard to open it

#### Option 2: Use the Folder Filter

1. Go to http://REDACTED_INTERNAL_IP:3000/dashboards?folderIds=2
2. This shows only dashboards in the Scanium folder

#### Option 3: Use Direct Dashboard Links

Skip the folder view and use direct links from the list above.

### Issue: Blank Page or Loading Error

**Possible Causes**:

1. Browser cache issue
2. Grafana restarting
3. Network issue

**Solutions**:

```bash
# Check if Grafana is running
ssh nas "/usr/local/bin/docker ps | grep grafana"

# Check Grafana health
curl http://REDACTED_INTERNAL_IP:3000/api/health

# Restart Grafana if needed
ssh nas "cd /volume1/docker/scanium/repo/monitoring && /usr/local/bin/docker-compose restart grafana"

# Clear browser cache and try again
```

### Issue: "Unauthorized" or Login Prompt

**Expected Behavior**: Grafana is configured with anonymous access enabled. You should NOT see a
login prompt.

**If you see a login prompt**:

1. Check docker-compose environment variables:
   ```bash
   ssh nas "/usr/local/bin/docker exec scanium-grafana env | grep GF_AUTH"
   ```
   Expected output:
   ```
   GF_AUTH_ANONYMOUS_ENABLED=true
   GF_AUTH_ANONYMOUS_ORG_ROLE=Admin
   GF_AUTH_DISABLE_LOGIN_FORM=true
   ```

2. If variables are missing, restart Grafana:
   ```bash
   ssh nas "cd /volume1/docker/scanium/repo/monitoring && /usr/local/bin/docker-compose restart grafana"
   ```

### Issue: Dashboards Show "No data"

**If Backend Errors dashboard shows no data**:

1. Check if you've generated recent traffic (error metrics decay without traffic)
2. Run traffic generator:
   ```bash
   ssh nas "cd /volume1/docker/scanium/repo && bash howto/monitoring/generate-error-traffic.sh http://172.23.0.5:8080"
   ```
3. Wait 90 seconds for metrics scrape, then refresh dashboard

---

## Grafana API Endpoints

Useful for debugging:

```bash
# List all folders
curl -s "http://REDACTED_INTERNAL_IP:3000/api/search?type=dash-folder" | jq '.'

# List all dashboards
curl -s "http://REDACTED_INTERNAL_IP:3000/api/search?type=dash-db" | jq '.'

# List dashboards in Scanium folder
curl -s "http://REDACTED_INTERNAL_IP:3000/api/search?folderIds=2" | jq '.[] | {title, url}'

# Get folder details
curl -s "http://REDACTED_INTERNAL_IP:3000/api/folders/scanium" | jq '.'

# Check Grafana health
curl -s "http://REDACTED_INTERNAL_IP:3000/api/health" | jq '.'
```

---

## Configuration

### Grafana Settings

- **Version**: 10.3.1
- **Port**: 3000 (exposed on all interfaces)
- **Anonymous Access**: Enabled (Admin role)
- **Datasources**: Loki, Tempo, Mimir (auto-provisioned)
- **Dashboards**: Auto-provisioned from `/var/lib/grafana/dashboards`

### Dashboard Provisioning

```yaml
Provider: Scanium Dashboards
Folder: Scanium (UID: scanium)
Path: /var/lib/grafana/dashboards
Update Interval: 30 seconds
UI Updates: Allowed
```

### Docker Container

```bash
# Container name
scanium-grafana

# Check status
ssh nas "/usr/local/bin/docker ps | grep grafana"

# View logs
ssh nas "/usr/local/bin/docker logs -f scanium-grafana"

# Restart
ssh nas "cd /volume1/docker/scanium/repo/monitoring && /usr/local/bin/docker-compose restart grafana"
```

---

## Recent Changes (2026-01-11)

✅ Fixed **Backend Errors** dashboard:

- Added `allValue` properties to variables
- All panels now showing data correctly
- Verified working at 09:50 UTC

✅ Generated test error traffic:

- 178 requests over 90 seconds
- Mix of 200, 401, 404 status codes
- ~56 errors visible in last hour

---

## Support

If you continue to have access issues:

1. **Verify Grafana is running**:
   ```bash
   curl http://REDACTED_INTERNAL_IP:3000/api/health
   ```

2. **Check container logs**:
   ```bash
   ssh nas "/usr/local/bin/docker logs --tail 50 scanium-grafana"
   ```

3. **Test from different device**: Try accessing from another computer/phone on the same network

4. **Check browser console**: Open browser DevTools (F12) and check for errors in Console tab

---

## Recommended Dashboards to Start With

1. **System Overview (RED)** - Best starting point for overall health
    - http://REDACTED_INTERNAL_IP:3000/d/scanium-system-overview/scanium-system-overview-red

2. **Backend Errors** - Deep dive into errors (just fixed today!)
    - http://REDACTED_INTERNAL_IP:3000/d/scanium-backend-errors/scanium-backend-errors

3. **Ops Overview** - Operational view across all services
    - http://REDACTED_INTERNAL_IP:3000/d/scanium-ops-overview/scanium-ops-overview

---

**Last Updated**: 2026-01-11T10:05:00Z
