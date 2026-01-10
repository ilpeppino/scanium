# Mobile Telemetry (OTLP) - Option 2

**Status**: ✅ Implemented
**Last Updated**: 2026-01-10

## Overview

Mobile telemetry is sent directly from the Scanium Android app to Grafana Alloy using **OTLP (OpenTelemetry Protocol) over HTTP**. This is the structural fix (Option 2) that replaces the previous backend-mediated approach (Option C).

**Flow**: `Mobile App` → `OTLP HTTP` → `Alloy:4318` → `Loki` → `Grafana Dashboards`

## Architecture

### Components

1. **Mobile App (Android)**
   - Uses `AndroidLogPortOtlp` to export telemetry events
   - Sends OTLP HTTP requests to `http://192.168.178.45:4318/v1/logs`
   - Configured via `local.properties` (`scanium.otlp.endpoint`, `scanium.otlp.enabled`)
   - Batching: 100 events or 5s timeout
   - Queue: 500 max, drop oldest on overflow
   - Retry: 3 attempts with exponential backoff

2. **Grafana Alloy (NAS)**
   - OTLP HTTP receiver on `0.0.0.0:4318`
   - Attribute processor extracts key attributes as Loki labels
   - Batch processor reduces network overhead
   - Loki exporter forwards to Loki

3. **Loki (NAS)**
   - Stores mobile logs with source=`scanium-mobile`
   - Queryable via LogQL in Grafana dashboards

## Event Schema

### Required Attributes (Auto-populated)

Every event includes these attributes as Loki labels:

| Attribute    | Example           | Description                       |
|--------------|-------------------|-----------------------------------|
| `platform`   | `"android"`       | Mobile platform                   |
| `app_version`| `"1.0.0"`         | Semantic version                  |
| `build`      | `"42"`            | Build number                      |
| `env`        | `"dev"` / `"prod"`| Environment                       |
| `build_type` | `"dev"` / `"beta"` / `"prod"` | Build flavor          |
| `session_id` | `"uuid-123..."`   | Random UUID per app launch        |
| `data_region`| `"US"` / `"EU"`   | Data residency region             |
| `event_name` | `"scan.started"`  | Event identifier                  |

### Event Naming Convention

Events follow the pattern: `<domain>.<action>`

**Domains:**
- `app.*` - Application lifecycle (e.g., `app.started`)
- `scan.*` - Scanning workflow (e.g., `scan.started`, `scan.confirmed`)
- `share.*` - Export/sharing (e.g., `share.export_zip`)
- `ai.*` - AI assistant (e.g., `ai.generate_clicked`)
- `error.*` - Errors and exceptions (e.g., `error.exception`)
- `ml.*` - Machine learning (e.g., `ml.classification_completed`)

### Event Catalog

| Event Name                 | Attributes                          | Description                          |
|----------------------------|-------------------------------------|--------------------------------------|
| `app.started`              | `launch_type`                       | App launched                         |
| `scan.started`             | `scan_source`                       | User started scanning                |
| `scan.created_item`        | `item_type`, `has_barcode`, `has_nutrition` | Item detected/created   |
| `scan.confirmed`           | `items_detected`, `scan_duration_ms`| User saved scan                      |
| `scan.cancelled`           | `reason`                            | User cancelled scan                  |
| `share.opened`             | `context`                           | Share sheet opened                   |
| `share.export_zip`         | `item_count`, `include_images`      | ZIP export created                   |
| `ai.generate_clicked`      | `context`, `ai_enabled`, `send_pictures_to_ai` | AI assistant triggered  |
| `error.exception`          | `error_code`, `error_category`, `is_recoverable` | Error occurred         |
| `ml.classification_completed` | `classification_mode`, `duration_ms`, `success` | Item classified     |

## Privacy & PII Redaction

**Hard Rules (enforced by `AttributeSanitizer`):**

- ❌ **NO user-generated content**: names, prompts, AI responses, text, images
- ❌ **NO device identifiers**: IMEI, Android ID, MAC address
- ❌ **NO location data**: GPS coordinates, IP addresses, city
- ❌ **NO authentication data**: tokens, passwords, API keys, cookies
- ❌ **NO personal identifiers**: email, phone, user IDs

**Allowed:**
- ✅ Session IDs (random UUID per launch, not linked to user)
- ✅ App version, build number, platform
- ✅ Numeric metrics (counts, durations)
- ✅ Boolean flags (feature enabled/disabled)
- ✅ Low-cardinality categories (scan source, build type)

**Attribute Sanitization:**
- Attributes matching blocked patterns are dropped
- Values > 200 chars are truncated
- Validation happens in `TelemetryEvent` before OTLP export

## Configuration

### Mobile App (`local.properties`)

```properties
# OTLP Telemetry (Option 2)
scanium.otlp.endpoint=http://192.168.178.45:4318
scanium.otlp.enabled=true
scanium.telemetry.data_region=US
```

### Alloy (`monitoring/alloy/alloy.hcl`)

OTLP receiver and attribute extraction are configured in the Alloy pipeline:

```hcl
// OTLP HTTP receiver
otelcol.receiver.otlp "mobile_http" {
  http {
    endpoint = "0.0.0.0:4318"
  }
  output {
    logs = [otelcol.processor.batch.mobile.input]
  }
}

// Extract key attributes as Loki labels
otelcol.processor.attributes "mobile" {
  action {
    key = "loki.attribute.labels"
    action = "insert"
    value = "event_name, platform, app_version, build_type, env"
  }
  output {
    logs = [otelcol.exporter.loki.mobile.input]
  }
}

// Export to Loki
otelcol.exporter.loki "mobile" {
  forward_to = [loki.write.mobile.receiver]
}

loki.write "mobile" {
  endpoint {
    url = "http://loki:3100/loki/api/v1/push"
  }
  external_labels = {
    source = "scanium-mobile",
  }
}
```

## Testing & Verification

### 1. Send Test Event from CLI

```bash
curl -X POST http://192.168.178.45:4318/v1/logs \
  -H "Content-Type: application/json" \
  -d '{
  "resourceLogs": [{
    "resource": {
      "attributes": [
        {"key": "service.name", "value": {"stringValue": "scanium-mobile"}},
        {"key": "service.version", "value": {"stringValue": "1.0.0-test"}},
        {"key": "deployment.environment", "value": {"stringValue": "dev"}}
      ]
    },
    "scopeLogs": [{
      "scope": {"name": "com.scanium.telemetry.test", "version": "1.0.0"},
      "logRecords": [{
        "timeUnixNano": "'$(date +%s)'000000000",
        "severityNumber": 9,
        "severityText": "INFO",
        "body": {"stringValue": "test.event"},
        "attributes": [
          {"key": "platform", "value": {"stringValue": "android"}},
          {"key": "app_version", "value": {"stringValue": "1.0.0-test"}},
          {"key": "build", "value": {"stringValue": "1"}},
          {"key": "env", "value": {"stringValue": "dev"}},
          {"key": "build_type", "value": {"stringValue": "dev"}},
          {"key": "session_id", "value": {"stringValue": "test-123"}},
          {"key": "data_region", "value": {"stringValue": "US"}},
          {"key": "event_name", "value": {"stringValue": "test.cli_probe"}}
        ]
      }]
    }]
  }]
}'
```

### 2. Verify in Loki

```bash
# Run proof script
./scripts/monitoring/prove-mobile-telemetry.sh

# Or query manually
ssh nas 'curl -G "http://127.0.0.1:3100/loki/api/v1/query_range" \
  --data-urlencode "query={source=\"scanium-mobile\", event_name=\"test.cli_probe\"}" \
  --data-urlencode "start=$(date -u +%s)000000000" \
  --data-urlencode "end=$(date -u +%s)000000000"'
```

### 3. Test from Device

1. Build and install app: `./gradlew installDevDebug`
2. Launch app on device (emulator or physical)
3. Perform actions: open app → scan → save
4. Run proof script: `./scripts/monitoring/prove-mobile-telemetry.sh`
5. Open Grafana dashboard: `Scanium - Mobile App Health`

### 4. Verify Dashboard

Open http://192.168.178.45:3000 and check:
- **Event Rate by Type**: Shows `app.started`, `scan.started`, etc.
- **Platform Distribution**: Shows `android` (and `ios` when implemented)
- **Active Sessions**: Counts unique `session_id` values
- **Top Events**: Breakdown by `event_name`

## Troubleshooting

### No logs in Loki

1. **Check Alloy receiver is running:**
   ```bash
   ssh nas "docker logs --tail 50 scanium-alloy | grep 'Starting HTTP server.*4318'"
   ```

2. **Check Alloy received the log:**
   ```bash
   ssh nas "docker logs --tail 100 scanium-alloy | grep -i mobile"
   ```

3. **Verify device can reach Alloy:**
   - Emulator: Use `http://10.0.2.2:4318` (not `localhost`)
   - Physical device: Use `http://192.168.178.45:4318` (same LAN)

4. **Check mobile app logs:**
   ```bash
   adb logcat -s OtlpHttpExporter AndroidLogPortOtlp
   ```

### Logs in Loki but labels missing

If logs appear but `event_name` is in the JSON body instead of labels:

1. Verify `otelcol.processor.attributes` is configured in Alloy
2. Restart Alloy: `ssh nas "cd /volume1/docker/scanium/repo/monitoring && docker-compose restart alloy"`
3. Re-send test log and verify labels

### Dashboard panels empty

1. Check label names in dashboard queries match Loki labels
2. Verify time range includes recent data
3. Check Loki datasource is configured: `LOKI` UID

## Performance & Overhead

- **Batch size**: 100 events (configurable in `TelemetryConfig`)
- **Flush interval**: 5s (configurable)
- **Max queue**: 500 events (drop oldest when full)
- **Network**: ~10KB per batch (compressed OTLP/JSON)
- **CPU**: Negligible (<1% on modern devices)
- **Battery**: Minimal impact (batched, async)

## Migration from Option C

**Old flow (Option C):**
```
Mobile App → Backend API (/v1/telemetry/mobile) → Backend logs → Alloy → Loki
```

**New flow (Option 2):**
```
Mobile App → Alloy (OTLP HTTP :4318) → Loki
```

**Benefits:**
- ✅ **Resilient**: No dependency on backend availability
- ✅ **Lower latency**: Direct to Alloy (no backend hop)
- ✅ **Standard protocol**: OTLP is vendor-neutral
- ✅ **Simpler**: Fewer moving parts

**Migration steps:**
1. ✅ Configure Alloy OTLP receiver
2. ✅ Update mobile app to use OTLP telemetry
3. ✅ Deprecate `MobileTelemetryClient` (backend API approach)
4. ✅ Verify dashboard queries work with new labels
5. ⏳ Remove backend `/v1/telemetry/mobile` endpoint (optional)

## See Also

- [Telemetry Facade](../../howto/app/TELEMETRY_FACADE.md) - Shared telemetry API
- [Alloy Configuration](../alloy/alloy.hcl) - OTLP receiver setup
- [Mobile App Health Dashboard](./dashboards/mobile-app-health.json) - Grafana dashboard
- [Proof Script](../../scripts/monitoring/prove-mobile-telemetry.sh) - Verification tool
