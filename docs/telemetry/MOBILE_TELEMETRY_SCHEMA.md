***REMOVED*** Mobile Telemetry Schema

***REMOVED******REMOVED*** Overview

This document defines the mobile telemetry schema for Scanium mobile apps (Android/iOS). Mobile events are sent to the backend via HTTPS, logged as structured JSON, and ingested into Loki via Alloy docker log pipeline.

***REMOVED******REMOVED*** Core Principles

1. **Low Cardinality**: Only a small set of attributes become Loki labels
2. **No PII**: Never collect user identifiers, GPS coordinates, photos, barcodes, item titles, or prompt text
3. **Rate Limited**: Telemetry is batched and rate-limited to prevent abuse
4. **Redacted**: All events are automatically redacted on the backend
5. **Opt-in Ready**: Infrastructure supports user opt-out (via feature flags)

***REMOVED******REMOVED*** Event Schema

Each telemetry event has the following structure:

```json
{
  "event_name": "scan_started",
  "platform": "android",
  "app_version": "1.2.3",
  "build_type": "beta",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "request_id": "req_abc123",
  "timestamp_ms": 1736532000000,
  "attributes": {
    "key1": "value1",
    "key2": 123
  }
}
```

***REMOVED******REMOVED******REMOVED*** Required Fields

| Field | Type | Description | Cardinality | Loki Label? |
|-------|------|-------------|-------------|-------------|
| `event_name` | string | Event identifier from fixed set | Low (~10) | YES |
| `platform` | string | `android` or `ios` | Low (2) | YES |
| `app_version` | string | Semantic version (e.g., `1.2.3`) | Low (~10) | YES |
| `build_type` | string | `dev`, `beta`, or `prod` | Low (3) | YES |
| `timestamp_ms` | number | Client timestamp (milliseconds since epoch) | N/A | NO |

***REMOVED******REMOVED******REMOVED*** Optional Fields

| Field | Type | Description | Cardinality | Loki Label? |
|-------|------|-------------|-------------|-------------|
| `session_id` | string | Random UUID per app launch | High | NO |
| `request_id` | string | Request correlation ID (if available) | High | NO |
| `attributes` | object | Event-specific metadata (limited keys) | Varies | NO |

***REMOVED******REMOVED*** Loki Labels

**ONLY these fields become Loki labels:**
- `source` (always `"scanium-mobile"`)
- `event_name`
- `platform`
- `app_version`
- `build_type`

All other fields remain inside the JSON log line and are **NOT** promoted to labels. This prevents cardinality explosion.

***REMOVED******REMOVED*** Event Catalog

***REMOVED******REMOVED******REMOVED*** App Lifecycle Events

***REMOVED******REMOVED******REMOVED******REMOVED*** `app_launch`
Emitted when the app starts.

**Attributes:**
- `launch_type` (string): `cold_start`, `warm_start`, or `hot_start`
- `previous_version` (string, optional): Previous app version if upgrade detected

**Example:**
```json
{
  "event_name": "app_launch",
  "platform": "android",
  "app_version": "1.2.3",
  "build_type": "beta",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp_ms": 1736532000000,
  "attributes": {
    "launch_type": "cold_start"
  }
}
```

***REMOVED******REMOVED******REMOVED*** Scanning Events

***REMOVED******REMOVED******REMOVED******REMOVED*** `scan_started`
Emitted when user initiates a scan.

**Attributes:**
- `scan_source` (string): `camera`, `gallery`, or `share`

**Example:**
```json
{
  "event_name": "scan_started",
  "platform": "android",
  "app_version": "1.2.3",
  "build_type": "beta",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp_ms": 1736532010000,
  "attributes": {
    "scan_source": "camera"
  }
}
```

***REMOVED******REMOVED******REMOVED******REMOVED*** `scan_completed`
Emitted when a scan finishes successfully.

**Attributes:**
- `duration_ms` (number): Time from scan_started to scan_completed
- `item_count` (number): Number of items detected (NOT the actual items)
- `has_nutrition_data` (boolean): Whether nutrition data was extracted

**Example:**
```json
{
  "event_name": "scan_completed",
  "platform": "android",
  "app_version": "1.2.3",
  "build_type": "beta",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp_ms": 1736532015000,
  "attributes": {
    "duration_ms": 5000,
    "item_count": 3,
    "has_nutrition_data": true
  }
}
```

***REMOVED******REMOVED******REMOVED*** AI Assistant Events

***REMOVED******REMOVED******REMOVED******REMOVED*** `assist_clicked`
Emitted when user taps the AI assistant button.

**Attributes:**
- `context` (string): `scan_result`, `item_detail`, or `other`

**Example:**
```json
{
  "event_name": "assist_clicked",
  "platform": "android",
  "app_version": "1.2.3",
  "build_type": "beta",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp_ms": 1736532020000,
  "attributes": {
    "context": "scan_result"
  }
}
```

***REMOVED******REMOVED******REMOVED*** Sharing Events

***REMOVED******REMOVED******REMOVED******REMOVED*** `share_started`
Emitted when user initiates a share action.

**Attributes:**
- `share_type` (string): `text`, `image`, or `receipt`

**Example:**
```json
{
  "event_name": "share_started",
  "platform": "android",
  "app_version": "1.2.3",
  "build_type": "beta",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp_ms": 1736532030000,
  "attributes": {
    "share_type": "receipt"
  }
}
```

***REMOVED******REMOVED******REMOVED*** Error Events

***REMOVED******REMOVED******REMOVED******REMOVED*** `error_shown`
Emitted when an error is displayed to the user.

**Attributes:**
- `error_code` (string): Fixed set of error codes (e.g., `NETWORK_ERROR`, `PARSE_ERROR`)
- `error_category` (string): `network`, `parsing`, `permission`, or `other`
- `is_recoverable` (boolean): Whether user can retry

**Example:**
```json
{
  "event_name": "error_shown",
  "platform": "android",
  "app_version": "1.2.3",
  "build_type": "beta",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp_ms": 1736532040000,
  "attributes": {
    "error_code": "NETWORK_ERROR",
    "error_category": "network",
    "is_recoverable": true
  }
}
```

***REMOVED******REMOVED******REMOVED******REMOVED*** `crash_marker`
Emitted manually when app detects it recovered from a crash (not actual crash logs).

**Attributes:**
- `crash_type` (string): `uncaught_exception`, `anr`, or `other`

**Example:**
```json
{
  "event_name": "crash_marker",
  "platform": "android",
  "app_version": "1.2.3",
  "build_type": "beta",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp_ms": 1736532050000,
  "attributes": {
    "crash_type": "uncaught_exception"
  }
}
```

***REMOVED******REMOVED*** Security

***REMOVED******REMOVED******REMOVED*** Authentication

Mobile clients authenticate using one of:
1. **Shared Secret**: `X-Client-Telemetry-Key` header (simple, beta-ready)
2. **Cloudflare Access**: Service token (if already configured)

The shared secret is stored in the backend's secrets mechanism and is NOT committed to the repository.

***REMOVED******REMOVED******REMOVED*** Rate Limiting

- **Per IP**: 100 events per minute
- **Per Session**: 10 events per second (if `session_id` provided)
- Exceeded limits return `429 Too Many Requests`

***REMOVED******REMOVED******REMOVED*** Redaction

All events are logged through a redaction filter that:
- Removes any PII-like patterns
- Strips headers (except those explicitly allowed)
- Validates against schema
- Rejects events with disallowed attributes

***REMOVED******REMOVED*** Backend Ingestion

***REMOVED******REMOVED******REMOVED*** Endpoint

```
POST /v1/telemetry/mobile
Content-Type: application/json
X-Client-Telemetry-Key: <secret>

{
  "event_name": "scan_started",
  "platform": "android",
  ...
}
```

***REMOVED******REMOVED******REMOVED*** Response

- **202 Accepted**: Event accepted and will be processed
- **400 Bad Request**: Invalid schema
- **401 Unauthorized**: Missing or invalid authentication
- **429 Too Many Requests**: Rate limit exceeded
- **500 Internal Server Error**: Backend error

***REMOVED******REMOVED******REMOVED*** Logging Behavior

For every accepted event, the backend logs ONE structured JSON line to stdout:

```json
{
  "source": "scanium-mobile",
  "event_name": "scan_started",
  "platform": "android",
  "app_version": "1.2.3",
  "build_type": "beta",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp_ms": 1736532010000,
  "attributes": {
    "scan_source": "camera"
  }
}
```

This log line is then:
1. Captured by Alloy via docker log pipeline
2. Parsed and labeled (source, event_name, platform, app_version, build_type)
3. Pushed to Loki
4. Queryable in Grafana

***REMOVED******REMOVED*** Observability

***REMOVED******REMOVED******REMOVED*** Loki Queries

***REMOVED******REMOVED******REMOVED******REMOVED*** All mobile events
```logql
{source="scanium-mobile"}
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Events by platform
```logql
{source="scanium-mobile", platform="android"}
{source="scanium-mobile", platform="ios"}
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Events by type
```logql
{source="scanium-mobile", event_name="scan_started"}
{source="scanium-mobile", event_name="error_shown"}
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Events by version
```logql
{source="scanium-mobile", app_version="1.2.3"}
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Events by build type
```logql
{source="scanium-mobile", build_type="beta"}
{source="scanium-mobile", build_type="prod"}
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Error events only
```logql
{source="scanium-mobile", event_name=~"error_shown|crash_marker"}
```

***REMOVED******REMOVED******REMOVED*** Dashboards

Mobile telemetry is visualized in:
- **Scanium - Mobile App Health** (`mobile-app-health.json`)
  - Event rate by type
  - Platform distribution
  - Version adoption
  - Error rate
  - Session analysis

***REMOVED******REMOVED*** Client Implementation

***REMOVED******REMOVED******REMOVED*** Android

See `/mobile/android/app/src/main/java/com/scanium/TelemetryClient.kt`

**Key features:**
- Background queue (does not block UI thread)
- Batching (sends every 30s or 10 events, whichever first)
- Retry logic with exponential backoff
- Feature flag support (`ENABLE_TELEMETRY`)
- Debug mode (manual test event button in dev builds)

***REMOVED******REMOVED******REMOVED*** iOS

(To be implemented - follow Android pattern)

***REMOVED******REMOVED*** Migration Path

***REMOVED******REMOVED******REMOVED*** Phase 1: Beta (Current)
- Android only
- Shared secret authentication
- Manual monitoring in Grafana

***REMOVED******REMOVED******REMOVED*** Phase 2: Production
- iOS support
- User opt-out UI
- Automated alerts for error spikes

***REMOVED******REMOVED******REMOVED*** Phase 3: Advanced
- Client-side sampling
- Trace correlation (OTLP traces)
- Custom attributes validation

***REMOVED******REMOVED*** Compliance Notes

- **No PII**: This schema is designed to be GDPR/CCPA friendly (no personal data)
- **User Control**: Telemetry can be disabled via feature flag (future: opt-out UI)
- **Data Retention**: Loki retention is 30 days (configurable)
- **Data Export**: Loki data can be exported via LogQL API if needed

***REMOVED******REMOVED*** Appendix: Disallowed Attributes

The following MUST NEVER appear in telemetry:
- User identifiers (user_id, email, phone, device_id, IMEI, etc.)
- Location data (GPS coordinates, IP address, city, etc.)
- Personal content (item names, barcode values, receipt text, prompt text, photos)
- Credentials (tokens, passwords, API keys)
- High-cardinality data (timestamps as labels, request IDs as labels)

If any of these are detected in an event, the entire event is rejected with a 400 error.
