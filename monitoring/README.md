# Scanium Observability Stack

Complete observability infrastructure for Scanium mobile app using the **LGTM stack** (Loki, Grafana, Tempo, Mimir) with **Grafana Alloy** as the OTLP receiver.

## Architecture

```
┌──────────────────────────────────────────────────┐
│          Scanium Android App                     │
│                                                   │
│  OTLP/HTTP Export (logs/metrics/traces)         │
└─────────────────┬────────────────────────────────┘
                  │
                  │ HTTP POST (JSON)
                  │ Port 4318
                  ▼
┌──────────────────────────────────────────────────┐
│         Grafana Alloy (OTLP Receiver)           │
│  - Receives OTLP over HTTP/gRPC                  │
│  - Batches telemetry data                        │
│  - Routes to backend stores                      │
└──────┬────────────┬────────────┬─────────────────┘
       │            │            │
       │ Logs       │ Metrics    │ Traces
       ▼            ▼            ▼
┌──────────┐  ┌──────────┐  ┌──────────┐
│   Loki   │  │  Mimir   │  │  Tempo   │
│  (Logs)  │  │(Metrics) │  │ (Traces) │
└────┬─────┘  └────┬─────┘  └────┬─────┘
     │             │             │
     │             │             │
     └─────────────┴─────────────┘
                   │
                   ▼
         ┌──────────────────┐
         │     Grafana      │
         │ (Visualization)  │
         └──────────────────┘
              Port 3000
```

## Components

| Service | Purpose | Ports | Storage |
|---------|---------|-------|---------|
| **Grafana** | Visualization & dashboards | 3000 (public) | `./data/grafana` |
| **Alloy** | OTLP receiver & router | 4317 (gRPC, public), 4318 (HTTP, public), 12345 (UI, localhost) | In-memory |
| **Loki** | Log aggregation | 3100 (localhost) | `./data/loki` |
| **Tempo** | Distributed tracing | 3200 (localhost), 4317 (OTLP, internal) | `./data/tempo` |
| **Mimir** | Metrics storage | 9009 (localhost) | `./data/mimir` |

> **Security Note:** Backend service ports (Loki, Tempo, Mimir, Alloy UI) are bound to localhost (127.0.0.1) only. OTLP ingestion ports (4317, 4318) and Grafana (3000) are publicly accessible for app telemetry.

## Quick Start

### Prerequisites

- Docker 24.0+ (with Compose V2)
- 4GB RAM available for Docker
- 10GB disk space for data storage

### 1. Start the Stack

```bash
cd monitoring
docker compose up -d
```

### 2. Verify Services

Wait for all services to become healthy (~30 seconds):

```bash
docker compose ps
```

Expected output:
```
NAME                STATUS              PORTS
scanium-alloy      running (healthy)   0.0.0.0:4317-4318->4317-4318/tcp, 127.0.0.1:12345->12345/tcp
scanium-grafana    running (healthy)   0.0.0.0:3000->3000/tcp
scanium-loki       running (healthy)   127.0.0.1:3100->3100/tcp
scanium-mimir      running (healthy)   127.0.0.1:9009->9009/tcp
scanium-tempo      running (healthy)   127.0.0.1:3200->3200/tcp
```

### 3. Access Grafana

Open http://localhost:3000 in your browser.

- **Authentication:** Disabled (anonymous admin access for local dev)
- **Datasources:** Pre-configured (Loki, Tempo, Mimir)

### 4. Configure Android App

Edit `androidApp/local.properties`:

```properties
# Enable OTLP export
scanium.otlp.enabled=true

# OTLP endpoint (use 10.0.2.2 for Android emulator)
scanium.otlp.endpoint=http://10.0.2.2:4318
```

For physical Android device with USB debugging:

```bash
# Forward port 4318 from device to host
adb reverse tcp:4318 tcp:4318

# Then use localhost in app config
scanium.otlp.endpoint=http://localhost:4318
```

### 5. Verify Telemetry Flow

Build and run the Android app:

```bash
cd ..  # Back to project root
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Use the app (scan items, navigate screens) to generate telemetry, then check:

**a) Alloy UI** - http://localhost:12345
- Navigate to "Graph" view
- Verify OTLP receivers show metrics

**b) Grafana Explore** - http://localhost:3000/explore
- Select "Loki" datasource
- Run query: `{source="scanium-mobile"}`
- Should see log entries from the app

**c) Grafana Explore (Metrics)** - http://localhost:3000/explore
- Select "Mimir" datasource
- Run query: `{source="scanium-mobile"}`
- Should see metrics

**d) Grafana Explore (Traces)** - http://localhost:3000/explore
- Select "Tempo" datasource
- Click "Search" tab
- Filter by service.name = "scanium-mobile"
- Should see trace spans

## Smoke Tests

### Test 1: Send Test Log via OTLP HTTP

```bash
# Send a test log event
curl -X POST http://localhost:4318/v1/logs \
  -H "Content-Type: application/json" \
  -d '{
    "resourceLogs": [{
      "resource": {
        "attributes": [
          {"key": "service.name", "value": {"stringValue": "test-service"}},
          {"key": "service.version", "value": {"stringValue": "1.0.0"}}
        ]
      },
      "scopeLogs": [{
        "scope": {"name": "test"},
        "logRecords": [{
          "timeUnixNano": "1735042200000000000",
          "severityNumber": 9,
          "severityText": "INFO",
          "body": {"stringValue": "Test log from curl"}
        }]
      }]
    }]
  }'
```

Verify in Grafana:
```logql
{service_name="test-service"} |= "Test log from curl"
```

### Test 2: Send Test Metric via OTLP HTTP

```bash
# Send a test metric
curl -X POST http://localhost:4318/v1/metrics \
  -H "Content-Type: application/json" \
  -d '{
    "resourceMetrics": [{
      "resource": {
        "attributes": [
          {"key": "service.name", "value": {"stringValue": "test-service"}}
        ]
      },
      "scopeMetrics": [{
        "scope": {"name": "test"},
        "metrics": [{
          "name": "test_counter",
          "sum": {
            "dataPoints": [{
              "timeUnixNano": "1735042200000000000",
              "asInt": 42
            }],
            "aggregationTemporality": 2,
            "isMonotonic": true
          }
        }]
      }]
    }]
  }'
```

Verify in Grafana (Mimir datasource):
```promql
test_counter{service_name="test-service"}
```

### Test 3: Verify All Datasources

```bash
# Check Grafana datasources health
curl -s http://localhost:3000/api/datasources | jq '.[].name, .[].basicAuthUser // "OK"'
```

Expected output:
```
"Loki"
"Tempo"
"Mimir"
```

### Test 4: Check Service Health Endpoints

```bash
# Alloy
curl -s http://localhost:12345/ready && echo " ✓ Alloy ready"

# Loki
curl -s http://localhost:3100/ready && echo " ✓ Loki ready"

# Tempo
curl -s http://localhost:3200/ready && echo " ✓ Tempo ready"

# Mimir
curl -s http://localhost:9009/ready && echo " ✓ Mimir ready"

# Grafana
curl -s http://localhost:3000/api/health | jq -r '.database' && echo " ✓ Grafana ready"
```

## Management Commands

### View Logs

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f alloy
docker compose logs -f grafana
docker compose logs -f loki
```

### Restart Services

```bash
# Restart all
docker compose restart

# Restart specific service
docker compose restart alloy
```

### Stop Stack

```bash
# Stop without removing containers
docker compose stop

# Stop and remove containers (keeps data)
docker compose down

# Stop and remove everything including data (⚠️ DESTRUCTIVE)
docker compose down -v
rm -rf data/
```

### Update Images

```bash
# Pull latest images
docker compose pull

# Recreate containers with new images
docker compose up -d --force-recreate
```

### Check Resource Usage

```bash
docker compose stats
```

## Data Retention

| Service | Retention Period | Configuration |
|---------|------------------|---------------|
| **Loki** | 14 days | `loki/loki.yaml` → `limits_config.retention_period` |
| **Tempo** | 7 days | `tempo/tempo.yaml` → `compactor.compaction.block_retention` |
| **Mimir** | 15 days | `mimir/mimir.yaml` → `limits.compactor_blocks_retention_period` |

To change retention, edit the config files and restart:

```bash
# Example: Change Loki retention to 30 days
# Edit loki/loki.yaml: retention_period: 720h

docker compose restart loki
```

## Troubleshooting

### No Data in Grafana

**Problem:** Grafana shows no data from datasources

**Check 1:** Verify datasources are configured
```bash
# Should show 3 datasources
curl -s http://localhost:3000/api/datasources | jq '. | length'
```

**Check 2:** Verify datasources are healthy
```bash
curl -s http://localhost:3000/api/datasources | jq '.[] | {name: .name, url: .url}'
```

**Check 3:** Test datasource connectivity from Grafana container
```bash
docker exec scanium-grafana wget -qO- http://loki:3100/ready
docker exec scanium-grafana wget -qO- http://tempo:3200/ready
docker exec scanium-grafana wget -qO- http://mimir:9009/ready
```

### Alloy Not Receiving Telemetry

**Problem:** Alloy shows no incoming OTLP data

**Check 1:** Verify Alloy is listening
```bash
docker exec scanium-alloy netstat -tuln | grep 4318
```

**Check 2:** Test OTLP endpoint from host
```bash
curl -v http://localhost:4318/v1/logs \
  -H "Content-Type: application/json" \
  -d '{"resourceLogs":[]}'
```

**Check 3:** View Alloy logs
```bash
docker compose logs alloy | grep -i error
```

**Check 4:** Enable debug logging

Uncomment the debug exporter in `alloy/alloy.hcl`:
```hcl
otelcol.exporter.logging "debug" {
  verbosity = "detailed"
}
```

Restart Alloy:
```bash
docker compose restart alloy
```

### Android App Cannot Connect to Alloy

**Problem:** Android app shows connection errors

**For Emulator:**
```bash
# Test from host (should work)
curl http://localhost:4318/v1/logs -d '{}' -H "Content-Type: application/json"

# Emulator uses 10.0.2.2 to reach host
# Verify in app config:
# scanium.otlp.endpoint=http://10.0.2.2:4318
```

**For Physical Device:**
```bash
# Set up port forwarding
adb reverse tcp:4318 tcp:4318

# Verify in app config:
# scanium.otlp.endpoint=http://localhost:4318

# Test from device
adb shell curl http://localhost:4318/v1/logs -d '{}' -H "Content-Type: application/json"
```

### High Disk Usage

**Problem:** Data directory growing too large

**Check disk usage:**
```bash
du -sh data/*
```

**Solution 1:** Reduce retention periods (see Data Retention section)

**Solution 2:** Manually compact data
```bash
# Compact Loki
docker exec scanium-loki wget -qO- --post-data='' http://localhost:3100/loki/api/v1/delete

# Trigger Tempo compaction
docker exec scanium-tempo wget -qO- --post-data='' http://localhost:3200/flush

# Trigger Mimir compaction
docker exec scanium-mimir wget -qO- --post-data='' http://localhost:9009/compactor/flush
```

**Solution 3:** Clean old data
```bash
# ⚠️ DESTRUCTIVE: Removes all stored data
docker compose down
rm -rf data/loki/* data/tempo/* data/mimir/*
docker compose up -d
```

### Grafana Shows "Datasource Error"

**Problem:** Datasource health checks fail

**Check datasource URLs in container network:**
```bash
# Grafana should reach other services via internal network
docker exec scanium-grafana nslookup loki
docker exec scanium-grafana nslookup tempo
docker exec scanium-grafana nslookup mimir
```

**Reset datasources:**
```bash
# Remove Grafana data and restart
docker compose down
rm -rf data/grafana/*
docker compose up -d grafana
```

Datasources will be re-provisioned automatically.

### Container Fails to Start

**Check logs:**
```bash
docker compose logs <service-name>
```

**Common issues:**

1. **Port already in use**
   ```bash
   # Find process using port
   lsof -i :3000  # Grafana
   lsof -i :4318  # Alloy

   # Kill process or change port in docker-compose.yml
   ```

2. **Insufficient disk space**
   ```bash
   df -h
   # Free up space or change data volume location
   ```

3. **Permission issues**
   ```bash
   # Fix data directory permissions
   sudo chown -R $(id -u):$(id -g) data/
   ```

## Performance Tuning

### For Low-Resource Environments (NAS/Raspberry Pi)

Edit `docker-compose.yml` to add resource limits:

```yaml
services:
  loki:
    deploy:
      resources:
        limits:
          memory: 512M
        reservations:
          memory: 256M
```

### For High-Volume Environments

Increase batch sizes and timeouts in `alloy/alloy.hcl`:

```hcl
otelcol.processor.batch "mobile" {
  send_batch_size     = 500      # Increase from 100
  send_batch_max_size = 1000     # Increase from 200
  timeout             = "10s"     # Increase from 5s
}
```

## Alerting

Scanium includes baseline alerts for monitoring application health. Alerts are provisioned automatically via Grafana's alerting provisioning system.

### Alert Overview

| Alert | Type | Condition | Severity |
|-------|------|-----------|----------|
| **Error Rate Spike** | Loki (logs) | > 50 errors/10min (prod) | Critical |
| **Telemetry Drop** | Loki (logs) | 0 events in 15min (prod) | Critical |
| **No Scan Sessions** | Loki (logs) | 0 sessions in 30min (prod) | Warning |
| **Inference Latency** | Mimir (metrics) | p95 > 2000ms (prod) | Warning |
| **Crash Spike** | Sentry | See Sentry setup below | N/A |

### Configuring Contact Points

Alerts use placeholder webhook URLs by default. To receive notifications, configure the following environment variables in `docker-compose.yml`:

```yaml
# Add to grafana service environment section
environment:
  # Webhook URLs (replace with your actual endpoints)
  - SCANIUM_ALERT_WEBHOOK_URL=https://your-webhook.example.com/alerts
  - SCANIUM_ALERT_WEBHOOK_URL_PROD=https://your-webhook.example.com/alerts/prod
  - SCANIUM_ALERT_WEBHOOK_URL_DEV=https://your-webhook.example.com/alerts/dev

  # Email (requires SMTP configuration)
  - GF_SMTP_ENABLED=true
  - GF_SMTP_HOST=smtp.example.com:587
  - GF_SMTP_USER=alerts@example.com
  - GF_SMTP_PASSWORD=${SMTP_PASSWORD}  # Use Docker secrets in production
  - GF_SMTP_FROM_ADDRESS=alerts@example.com
  - SCANIUM_ALERT_EMAIL=team@example.com
```

**Important:** Never commit secrets to the repository. Use environment variables, Docker secrets, or a secret manager.

### Notification Routing

Alerts are automatically routed based on the `env` label:

| Environment | Contact Point | Group Wait | Repeat Interval |
|-------------|---------------|------------|-----------------|
| `prod` | scanium-high-priority | 10s | 1h |
| `stage` | scanium-low-priority | 1m | 8h |
| `dev` | scanium-low-priority | 2m | 24h |

### Alert Thresholds

Thresholds are configured in `grafana/provisioning/alerting/rules.yaml`. Default values:

| Alert | Prod | Stage | Dev |
|-------|------|-------|-----|
| Error rate (errors/10min) | 50 | 100 | 200 |
| Telemetry drop (min events) | 1 | N/A | N/A |
| Inference latency p95 (ms) | 2000 | 3000 | N/A |

To adjust thresholds, edit the `params` values in the rules file.

### Sentry Crash Alerts (Not in Grafana)

Crash spike monitoring should be configured directly in Sentry:

1. **Create a Sentry project** for Scanium (if not already done)
2. **Configure alert rules** in Sentry:
   - Go to **Alerts → Create Alert Rule**
   - Select **Issue Alert** for crash grouping
   - Condition: "Number of events is more than X in Y minutes"
   - Recommended: > 10 crashes in 10 minutes for production
3. **Set up integrations** (Slack, PagerDuty, email) in Sentry settings
4. **Add Sentry DSN** to the Android app configuration

This approach is preferred because:
- Sentry has richer crash symbolication and grouping
- Native crash data isn't always forwarded to Grafana
- Sentry provides better crash-specific context

### Test Procedures

Use these procedures to verify alerts are working correctly in development.

#### Test A: Error Rate Spike

Trigger: Send multiple error logs to exceed threshold.

```bash
# Send 60 error events (exceeds dev threshold of 50)
for i in {1..60}; do
  curl -X POST http://localhost:4318/v1/logs \
    -H "Content-Type: application/json" \
    -d '{
      "resourceLogs": [{
        "resource": {
          "attributes": [
            {"key": "service.name", "value": {"stringValue": "scanium-mobile"}},
            {"key": "source", "value": {"stringValue": "scanium-mobile"}},
            {"key": "env", "value": {"stringValue": "dev"}}
          ]
        },
        "scopeLogs": [{
          "scope": {"name": "test"},
          "logRecords": [{
            "timeUnixNano": "'$(date +%s)000000000'",
            "severityNumber": 17,
            "severityText": "ERROR",
            "body": {"stringValue": "Test error event '$i' - simulated crash exception"}
          }]
        }]
      }]
    }' 2>/dev/null
  echo "Sent error $i"
done

echo "✓ Sent 60 error events. Check Grafana Alerting UI in ~5-10 minutes."
```

Verify: Navigate to Grafana → Alerting → Alert rules → "Error Rate Spike (dev)"

#### Test B: Telemetry Drop

This alert only fires in production and requires NO events for 15 minutes. To test:

1. Temporarily modify the rule to use `env="dev"`
2. Stop sending any telemetry for 15+ minutes
3. Verify the alert fires

```bash
# Quick verification that telemetry query works
curl -s 'http://localhost:3100/loki/api/v1/query' \
  --data-urlencode 'query=sum(count_over_time({source="scanium-mobile", env="dev"} [15m]))' \
  | jq '.data.result'
```

#### Test C: Inference Latency

Trigger: Send metrics with high latency values.

```bash
# Send histogram metric with high latency value
# Note: This requires the actual metric name used in your app
# Adjust 'ml_inference_latency_ms_bucket' to match your metric

TIMESTAMP=$(date +%s)000

curl -X POST http://localhost:4318/v1/metrics \
  -H "Content-Type: application/json" \
  -d '{
    "resourceMetrics": [{
      "resource": {
        "attributes": [
          {"key": "service.name", "value": {"stringValue": "scanium-mobile"}},
          {"key": "env", "value": {"stringValue": "dev"}}
        ]
      },
      "scopeMetrics": [{
        "scope": {"name": "ml"},
        "metrics": [{
          "name": "ml_inference_latency_ms",
          "histogram": {
            "dataPoints": [{
              "timeUnixNano": "'$TIMESTAMP'",
              "count": 100,
              "sum": 350000,
              "bucketCounts": [0, 0, 0, 0, 0, 0, 100],
              "explicitBounds": [100, 250, 500, 1000, 2000, 5000]
            }],
            "aggregationTemporality": 2
          }
        }]
      }]
    }]
  }'

echo "✓ Sent high-latency histogram. Check Mimir for metric, then alert status."
```

Verify:
```bash
# Check if metric is ingested
curl -s 'http://localhost:9009/prometheus/api/v1/query' \
  --data-urlencode 'query=ml_inference_latency_ms_bucket' | jq '.data.result | length'
```

#### Verify All Alerts in UI

1. Navigate to http://localhost:3000/alerting/list
2. Check "Scanium Alerts" folder
3. All rules should show "Normal" or "Pending" (not "Error")
4. Click a rule to see evaluation history

### Silencing Alerts

To temporarily silence alerts during maintenance:

1. Go to Grafana → Alerting → Silences
2. Click "Add Silence"
3. Add matchers (e.g., `env=dev` or `alertname=Error Rate Spike (dev)`)
4. Set duration and save

### Alert Troubleshooting

**Alerts show "Error" state:**
- Check Grafana logs: `docker compose logs grafana | grep -i alert`
- Verify datasource connectivity in Grafana UI
- Ensure queries return data in Explore view

**Alerts never fire:**
- Verify alert rule is enabled (not paused)
- Check "for" duration hasn't been met yet
- Use Explore to test the query manually
- Check that labels match notification policy routes

**No notifications received:**
- Verify contact point configuration in UI
- Check webhook endpoint is accessible from container
- For email, verify SMTP settings are correct
- Check Grafana logs for delivery errors

## Pipeline Self-Observability

The observability stack monitors itself using Prometheus-style metrics scraped by Alloy. This enables dashboards and alerts for the pipeline health.

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Pipeline Self-Monitoring                      │
├──────────────┬──────────────┬──────────────┬───────────────────┤
│  Alloy       │    Loki      │   Tempo      │    Mimir          │
│  :12345      │   :3100      │   :3200      │   :9009           │
│  /metrics    │   /metrics   │   /metrics   │   /metrics        │
└──────┬───────┴──────┬───────┴──────┬───────┴───────┬───────────┘
       │              │              │               │
       └──────────────┴──────────────┴───────────────┘
                              │
                    Alloy prometheus.scrape
                              │
                              ▼
                    ┌─────────────────┐
                    │     Mimir       │
                    │ (source=pipeline)│
                    └────────┬────────┘
                             │
              ┌──────────────┴──────────────┐
              │                             │
              ▼                             ▼
     ┌─────────────────┐         ┌─────────────────┐
     │   Dashboards    │         │     Alerts      │
     │ Pipeline Health │         │ Pipeline Health │
     └─────────────────┘         └─────────────────┘
```

### Metrics Endpoints

All metrics are available via Prometheus `/metrics` endpoints:

| Service | Endpoint | Binding | Purpose |
|---------|----------|---------|---------|
| Alloy | `localhost:12345/metrics` | localhost only | Collector metrics |
| Loki | `localhost:3100/metrics` | localhost only | Log storage metrics |
| Tempo | `localhost:3200/metrics` | localhost only | Trace storage metrics |
| Mimir | `localhost:9009/metrics` | localhost only | Metrics storage metrics |

**Note:** Debug ports are bound to localhost (127.0.0.1) for security. Access from within Docker network uses internal hostnames.

### Verify Metrics Collection

#### From the host machine (localhost access):

```bash
# Verify Alloy is exposing metrics
curl -s http://localhost:12345/metrics | head -20

# Verify Loki metrics
curl -s http://localhost:3100/metrics | head -20

# Verify Tempo metrics
curl -s http://localhost:3200/metrics | head -20

# Verify Mimir metrics
curl -s http://localhost:9009/metrics | head -20
```

#### From within the Docker network:

```bash
# Test from Alloy container (internal network access)
docker exec scanium-alloy wget -qO- http://loki:3100/metrics | head -10
docker exec scanium-alloy wget -qO- http://tempo:3200/metrics | head -10
docker exec scanium-alloy wget -qO- http://mimir:9009/metrics | head -10

# Alloy scrapes its own metrics via localhost
docker exec scanium-alloy wget -qO- http://localhost:12345/metrics | head -10
```

### Verify Metrics in Mimir

After starting the stack, wait ~30 seconds for scrapes, then query Mimir:

```bash
# Check for pipeline metrics in Mimir
curl -s 'http://localhost:9009/prometheus/api/v1/query' \
  --data-urlencode 'query=up{source="pipeline"}' | jq '.data.result'

# Should return 4 results (alloy, loki, tempo, mimir) with value 1
```

In Grafana Explore (http://localhost:3000/explore):
1. Select **Mimir** datasource
2. Query: `up{source="pipeline"}`
3. Should show 4 time series for each service

### Key Metrics Reference

Pipeline metrics are labeled with `source="pipeline"` to distinguish from app telemetry.

#### Alloy Metrics

| Metric | Description |
|--------|-------------|
| `up{job="alloy"}` | 1 if Alloy is up, 0 if down |
| `alloy_build_info` | Version and build information |
| `process_start_time_seconds` | Unix timestamp when Alloy started |
| `otelcol_receiver_accepted_log_records` | Logs accepted by OTLP receiver |
| `otelcol_receiver_accepted_metric_points` | Metrics accepted by OTLP receiver |
| `otelcol_receiver_accepted_spans` | Spans accepted by OTLP receiver |
| `otelcol_receiver_refused_*` | Records refused (indicates errors) |
| `otelcol_exporter_sent_*` | Records successfully exported |
| `otelcol_exporter_send_failed_*` | Export failures (backend issues) |
| `otelcol_exporter_queue_size` | Current queue size |
| `otelcol_exporter_queue_capacity` | Maximum queue capacity |

#### Loki Metrics

| Metric | Description |
|--------|-------------|
| `up{job="loki"}` | 1 if Loki is up, 0 if down |
| `loki_ingester_memory_streams` | Active log streams in memory |
| `loki_request_duration_seconds_*` | Request latency histogram |
| `loki_distributor_bytes_received_total` | Total bytes received |

#### Tempo Metrics

| Metric | Description |
|--------|-------------|
| `up{job="tempo"}` | 1 if Tempo is up, 0 if down |
| `tempo_ingester_live_traces` | Active traces in ingester |
| `tempo_request_duration_seconds_*` | Request latency histogram |

#### Mimir Metrics

| Metric | Description |
|--------|-------------|
| `up{job="mimir"}` | 1 if Mimir is up, 0 if down |
| `cortex_ingester_memory_series` | Active metric series in memory |
| `cortex_request_duration_seconds_*` | Request latency histogram |

### Pipeline Dashboard

The **Pipeline Health** dashboard (http://localhost:3000/d/scanium-pipeline-health) includes a "Pipeline Self-Observability" section with panels for:

- **Service Status:** UP/DOWN status for all services
- **Alloy Build Info:** Version information
- **Alloy Uptime:** Time since last restart
- **OTLP Receiver Metrics:** Records accepted/refused per signal type
- **Exporter Metrics:** Records sent and failures
- **Queue Metrics:** Queue size and capacity
- **Backend Health:** Loki/Tempo/Mimir request rates

### Pipeline Alerts

Alerts are defined in `grafana/provisioning/alerting/rules.yaml` under the "Scanium - Pipeline Health" group:

| Alert | Condition | Severity |
|-------|-----------|----------|
| Alloy Down | `up{job="alloy"} == 0` for 2m | Critical |
| Loki Down | `up{job="loki"} == 0` for 2m | Critical |
| Tempo Down | `up{job="tempo"} == 0` for 2m | Critical |
| Mimir Down | `up{job="mimir"} == 0` for 2m | Critical |
| Log Export Failures | Any failed log exports in 5m | Critical |
| Metric Export Failures | Any failed metric exports in 5m | Critical |
| Span Export Failures | Any failed span exports in 5m | Critical |
| Receiver Refusing Data | Any refused records in 5m | Warning |
| Queue Backpressure | Queue > 80% capacity for 5m | Warning |

### Pipeline Troubleshooting

#### Test: Trigger Exporter Failure Alert

To safely test exporter failure alerts in dev:

```bash
# Step 1: Temporarily break Loki connectivity
docker compose stop loki

# Step 2: Send a test log to Alloy (will fail to export)
curl -X POST http://localhost:4318/v1/logs \
  -H "Content-Type: application/json" \
  -d '{
    "resourceLogs": [{
      "resource": {"attributes": [{"key": "service.name", "value": {"stringValue": "test"}}]},
      "scopeLogs": [{"logRecords": [{"body": {"stringValue": "Test log during Loki outage"}}]}]
    }]
  }'

# Step 3: Wait ~2 minutes, check exporter failure metrics
curl -s 'http://localhost:9009/prometheus/api/v1/query' \
  --data-urlencode 'query=otelcol_exporter_send_failed_log_records{source="pipeline"}' | jq

# Step 4: Check alert status in Grafana
# Navigate to http://localhost:3000/alerting/list

# Step 5: Restore Loki
docker compose start loki

# Step 6: Verify recovery (failures should stop, alert should resolve)
```

#### Test: Service Down Alert

```bash
# Stop a service to trigger "down" alert
docker compose stop tempo

# Wait 2+ minutes, then check alerts
# Navigate to http://localhost:3000/alerting/list
# "Tempo Down" alert should be firing

# Restore service
docker compose start tempo
```

#### Diagnose Exporter Issues

```bash
# Check Alloy logs for export errors
docker compose logs alloy | grep -i "error\|failed\|refused"

# Check exporter queue status
curl -s 'http://localhost:9009/prometheus/api/v1/query' \
  --data-urlencode 'query=otelcol_exporter_queue_size{source="pipeline"}' | jq

# Check if backends are reachable from Alloy
docker exec scanium-alloy wget -qO- http://loki:3100/ready && echo "Loki OK"
docker exec scanium-alloy wget -qO- http://tempo:3200/ready && echo "Tempo OK"
docker exec scanium-alloy wget -qO- http://mimir:9009/ready && echo "Mimir OK"
```

#### High Queue Size / Backpressure

If queue metrics show high values:

1. Check backend health (Loki/Tempo/Mimir)
2. Check network connectivity between containers
3. Consider increasing queue capacity in `alloy.hcl`:
   ```hcl
   prometheus.remote_write "pipeline" {
     endpoint {
       capacity = 10000  # Increase from default
     }
   }
   ```
4. Check for resource constraints (CPU/memory)

## Production Considerations

This setup is optimized for **local development and NAS deployment**. For production:

### Security

- [ ] Enable Grafana authentication (remove anonymous access)
- [ ] Add TLS/SSL termination (reverse proxy)
- [ ] Use secrets management (not environment variables)
- [ ] Restrict network access (firewall rules)
- [ ] Enable OTLP TLS (currently disabled)

### Scalability

- [ ] Use external storage (S3, GCS, Azure Blob)
- [ ] Run Mimir/Loki/Tempo in distributed mode
- [ ] Add load balancer for Alloy
- [ ] Implement authentication between services

### Reliability

- [ ] Set up automated backups
- [x] Configure alerting (Alertmanager) - ✅ Baseline alerts provisioned
- [x] Monitor the monitoring stack itself - ✅ Pipeline health metrics enabled
- [ ] Implement high availability (multiple replicas)

## Backup & Restore

### Backup

```bash
# Stop services
docker compose stop

# Backup data directory
tar -czf scanium-monitoring-backup-$(date +%Y%m%d).tar.gz data/

# Restart services
docker compose start
```

### Restore

```bash
# Stop services
docker compose down

# Restore data
tar -xzf scanium-monitoring-backup-YYYYMMDD.tar.gz

# Start services
docker compose up -d
```

## Useful Links

- **Grafana UI:** http://localhost:3000
- **Alloy UI:** http://localhost:12345
- **Loki API:** http://localhost:3100
- **Tempo API:** http://localhost:3200
- **Mimir API:** http://localhost:9009

## Support

For issues with this observability stack:
1. Check logs: `docker compose logs`
2. Verify health: Run smoke tests above
3. Consult troubleshooting guide
4. Check component docs:
   - [Grafana Alloy](https://grafana.com/docs/alloy/latest/)
   - [Grafana Loki](https://grafana.com/docs/loki/latest/)
   - [Grafana Tempo](https://grafana.com/docs/tempo/latest/)
   - [Grafana Mimir](https://grafana.com/docs/mimir/latest/)
