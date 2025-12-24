***REMOVED*** Scanium Observability Stack

Complete observability infrastructure for Scanium mobile app using the **LGTM stack** (Loki, Grafana, Tempo, Mimir) with **Grafana Alloy** as the OTLP receiver.

***REMOVED******REMOVED*** Architecture

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

***REMOVED******REMOVED*** Components

| Service | Purpose | Ports | Storage |
|---------|---------|-------|---------|
| **Grafana** | Visualization & dashboards | 3000 (UI) | `./data/grafana` |
| **Alloy** | OTLP receiver & router | 4317 (gRPC), 4318 (HTTP), 12345 (UI) | In-memory |
| **Loki** | Log aggregation | 3100 (HTTP) | `./data/loki` |
| **Tempo** | Distributed tracing | 3200 (HTTP), 4317 (OTLP) | `./data/tempo` |
| **Mimir** | Metrics storage | 9009 (HTTP) | `./data/mimir` |

***REMOVED******REMOVED*** Quick Start

***REMOVED******REMOVED******REMOVED*** Prerequisites

- Docker 24.0+ (with Compose V2)
- 4GB RAM available for Docker
- 10GB disk space for data storage

***REMOVED******REMOVED******REMOVED*** 1. Start the Stack

```bash
cd monitoring
docker compose up -d
```

***REMOVED******REMOVED******REMOVED*** 2. Verify Services

Wait for all services to become healthy (~30 seconds):

```bash
docker compose ps
```

Expected output:
```
NAME                STATUS              PORTS
scanium-alloy      running (healthy)   0.0.0.0:4317-4318->4317-4318/tcp, 0.0.0.0:12345->12345/tcp
scanium-grafana    running (healthy)   0.0.0.0:3000->3000/tcp
scanium-loki       running (healthy)   0.0.0.0:3100->3100/tcp
scanium-mimir      running (healthy)   0.0.0.0:9009->9009/tcp
scanium-tempo      running (healthy)   0.0.0.0:3200->3200/tcp
```

***REMOVED******REMOVED******REMOVED*** 3. Access Grafana

Open http://localhost:3000 in your browser.

- **Authentication:** Disabled (anonymous admin access for local dev)
- **Datasources:** Pre-configured (Loki, Tempo, Mimir)

***REMOVED******REMOVED******REMOVED*** 4. Configure Android App

Edit `androidApp/local.properties`:

```properties
***REMOVED*** Enable OTLP export
scanium.otlp.enabled=true

***REMOVED*** OTLP endpoint (use 10.0.2.2 for Android emulator)
scanium.otlp.endpoint=http://10.0.2.2:4318
```

For physical Android device with USB debugging:

```bash
***REMOVED*** Forward port 4318 from device to host
adb reverse tcp:4318 tcp:4318

***REMOVED*** Then use localhost in app config
scanium.otlp.endpoint=http://localhost:4318
```

***REMOVED******REMOVED******REMOVED*** 5. Verify Telemetry Flow

Build and run the Android app:

```bash
cd ..  ***REMOVED*** Back to project root
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

***REMOVED******REMOVED*** Smoke Tests

***REMOVED******REMOVED******REMOVED*** Test 1: Send Test Log via OTLP HTTP

```bash
***REMOVED*** Send a test log event
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

***REMOVED******REMOVED******REMOVED*** Test 2: Send Test Metric via OTLP HTTP

```bash
***REMOVED*** Send a test metric
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

***REMOVED******REMOVED******REMOVED*** Test 3: Verify All Datasources

```bash
***REMOVED*** Check Grafana datasources health
curl -s http://localhost:3000/api/datasources | jq '.[].name, .[].basicAuthUser // "OK"'
```

Expected output:
```
"Loki"
"Tempo"
"Mimir"
```

***REMOVED******REMOVED******REMOVED*** Test 4: Check Service Health Endpoints

```bash
***REMOVED*** Alloy
curl -s http://localhost:12345/ready && echo " ✓ Alloy ready"

***REMOVED*** Loki
curl -s http://localhost:3100/ready && echo " ✓ Loki ready"

***REMOVED*** Tempo
curl -s http://localhost:3200/ready && echo " ✓ Tempo ready"

***REMOVED*** Mimir
curl -s http://localhost:9009/ready && echo " ✓ Mimir ready"

***REMOVED*** Grafana
curl -s http://localhost:3000/api/health | jq -r '.database' && echo " ✓ Grafana ready"
```

***REMOVED******REMOVED*** Management Commands

***REMOVED******REMOVED******REMOVED*** View Logs

```bash
***REMOVED*** All services
docker compose logs -f

***REMOVED*** Specific service
docker compose logs -f alloy
docker compose logs -f grafana
docker compose logs -f loki
```

***REMOVED******REMOVED******REMOVED*** Restart Services

```bash
***REMOVED*** Restart all
docker compose restart

***REMOVED*** Restart specific service
docker compose restart alloy
```

***REMOVED******REMOVED******REMOVED*** Stop Stack

```bash
***REMOVED*** Stop without removing containers
docker compose stop

***REMOVED*** Stop and remove containers (keeps data)
docker compose down

***REMOVED*** Stop and remove everything including data (⚠️ DESTRUCTIVE)
docker compose down -v
rm -rf data/
```

***REMOVED******REMOVED******REMOVED*** Update Images

```bash
***REMOVED*** Pull latest images
docker compose pull

***REMOVED*** Recreate containers with new images
docker compose up -d --force-recreate
```

***REMOVED******REMOVED******REMOVED*** Check Resource Usage

```bash
docker compose stats
```

***REMOVED******REMOVED*** Data Retention

| Service | Retention Period | Configuration |
|---------|------------------|---------------|
| **Loki** | 14 days | `loki/loki.yaml` → `limits_config.retention_period` |
| **Tempo** | 7 days | `tempo/tempo.yaml` → `compactor.compaction.block_retention` |
| **Mimir** | 15 days | `mimir/mimir.yaml` → `limits.compactor_blocks_retention_period` |

To change retention, edit the config files and restart:

```bash
***REMOVED*** Example: Change Loki retention to 30 days
***REMOVED*** Edit loki/loki.yaml: retention_period: 720h

docker compose restart loki
```

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** No Data in Grafana

**Problem:** Grafana shows no data from datasources

**Check 1:** Verify datasources are configured
```bash
***REMOVED*** Should show 3 datasources
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

***REMOVED******REMOVED******REMOVED*** Alloy Not Receiving Telemetry

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

***REMOVED******REMOVED******REMOVED*** Android App Cannot Connect to Alloy

**Problem:** Android app shows connection errors

**For Emulator:**
```bash
***REMOVED*** Test from host (should work)
curl http://localhost:4318/v1/logs -d '{}' -H "Content-Type: application/json"

***REMOVED*** Emulator uses 10.0.2.2 to reach host
***REMOVED*** Verify in app config:
***REMOVED*** scanium.otlp.endpoint=http://10.0.2.2:4318
```

**For Physical Device:**
```bash
***REMOVED*** Set up port forwarding
adb reverse tcp:4318 tcp:4318

***REMOVED*** Verify in app config:
***REMOVED*** scanium.otlp.endpoint=http://localhost:4318

***REMOVED*** Test from device
adb shell curl http://localhost:4318/v1/logs -d '{}' -H "Content-Type: application/json"
```

***REMOVED******REMOVED******REMOVED*** High Disk Usage

**Problem:** Data directory growing too large

**Check disk usage:**
```bash
du -sh data/*
```

**Solution 1:** Reduce retention periods (see Data Retention section)

**Solution 2:** Manually compact data
```bash
***REMOVED*** Compact Loki
docker exec scanium-loki wget -qO- --post-data='' http://localhost:3100/loki/api/v1/delete

***REMOVED*** Trigger Tempo compaction
docker exec scanium-tempo wget -qO- --post-data='' http://localhost:3200/flush

***REMOVED*** Trigger Mimir compaction
docker exec scanium-mimir wget -qO- --post-data='' http://localhost:9009/compactor/flush
```

**Solution 3:** Clean old data
```bash
***REMOVED*** ⚠️ DESTRUCTIVE: Removes all stored data
docker compose down
rm -rf data/loki/* data/tempo/* data/mimir/*
docker compose up -d
```

***REMOVED******REMOVED******REMOVED*** Grafana Shows "Datasource Error"

**Problem:** Datasource health checks fail

**Check datasource URLs in container network:**
```bash
***REMOVED*** Grafana should reach other services via internal network
docker exec scanium-grafana nslookup loki
docker exec scanium-grafana nslookup tempo
docker exec scanium-grafana nslookup mimir
```

**Reset datasources:**
```bash
***REMOVED*** Remove Grafana data and restart
docker compose down
rm -rf data/grafana/*
docker compose up -d grafana
```

Datasources will be re-provisioned automatically.

***REMOVED******REMOVED******REMOVED*** Container Fails to Start

**Check logs:**
```bash
docker compose logs <service-name>
```

**Common issues:**

1. **Port already in use**
   ```bash
   ***REMOVED*** Find process using port
   lsof -i :3000  ***REMOVED*** Grafana
   lsof -i :4318  ***REMOVED*** Alloy

   ***REMOVED*** Kill process or change port in docker-compose.yml
   ```

2. **Insufficient disk space**
   ```bash
   df -h
   ***REMOVED*** Free up space or change data volume location
   ```

3. **Permission issues**
   ```bash
   ***REMOVED*** Fix data directory permissions
   sudo chown -R $(id -u):$(id -g) data/
   ```

***REMOVED******REMOVED*** Performance Tuning

***REMOVED******REMOVED******REMOVED*** For Low-Resource Environments (NAS/Raspberry Pi)

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

***REMOVED******REMOVED******REMOVED*** For High-Volume Environments

Increase batch sizes and timeouts in `alloy/alloy.hcl`:

```hcl
otelcol.processor.batch "mobile" {
  send_batch_size     = 500      ***REMOVED*** Increase from 100
  send_batch_max_size = 1000     ***REMOVED*** Increase from 200
  timeout             = "10s"     ***REMOVED*** Increase from 5s
}
```

***REMOVED******REMOVED*** Production Considerations

This setup is optimized for **local development and NAS deployment**. For production:

***REMOVED******REMOVED******REMOVED*** Security

- [ ] Enable Grafana authentication (remove anonymous access)
- [ ] Add TLS/SSL termination (reverse proxy)
- [ ] Use secrets management (not environment variables)
- [ ] Restrict network access (firewall rules)
- [ ] Enable OTLP TLS (currently disabled)

***REMOVED******REMOVED******REMOVED*** Scalability

- [ ] Use external storage (S3, GCS, Azure Blob)
- [ ] Run Mimir/Loki/Tempo in distributed mode
- [ ] Add load balancer for Alloy
- [ ] Implement authentication between services

***REMOVED******REMOVED******REMOVED*** Reliability

- [ ] Set up automated backups
- [ ] Configure alerting (Alertmanager)
- [ ] Monitor the monitoring stack itself
- [ ] Implement high availability (multiple replicas)

***REMOVED******REMOVED*** Backup & Restore

***REMOVED******REMOVED******REMOVED*** Backup

```bash
***REMOVED*** Stop services
docker compose stop

***REMOVED*** Backup data directory
tar -czf scanium-monitoring-backup-$(date +%Y%m%d).tar.gz data/

***REMOVED*** Restart services
docker compose start
```

***REMOVED******REMOVED******REMOVED*** Restore

```bash
***REMOVED*** Stop services
docker compose down

***REMOVED*** Restore data
tar -xzf scanium-monitoring-backup-YYYYMMDD.tar.gz

***REMOVED*** Start services
docker compose up -d
```

***REMOVED******REMOVED*** Useful Links

- **Grafana UI:** http://localhost:3000
- **Alloy UI:** http://localhost:12345
- **Loki API:** http://localhost:3100
- **Tempo API:** http://localhost:3200
- **Mimir API:** http://localhost:9009

***REMOVED******REMOVED*** Support

For issues with this observability stack:
1. Check logs: `docker compose logs`
2. Verify health: Run smoke tests above
3. Consult troubleshooting guide
4. Check component docs:
   - [Grafana Alloy](https://grafana.com/docs/alloy/latest/)
   - [Grafana Loki](https://grafana.com/docs/loki/latest/)
   - [Grafana Tempo](https://grafana.com/docs/tempo/latest/)
   - [Grafana Mimir](https://grafana.com/docs/mimir/latest/)
