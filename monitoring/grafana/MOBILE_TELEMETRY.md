***REMOVED*** Mobile Telemetry

Scanium mobile app sends telemetry events to the backend, which forwards them to the LGTM stack (Loki/Mimir) via Grafana Alloy.

***REMOVED******REMOVED*** Ingestion Path

1. **Mobile App**: Sends `POST /v1/telemetry/mobile` to Backend.
2. **Backend**: Validates request and emits OTLP Log Record to `scanium-alloy:4318`.
   - Adds `source="scanium-mobile"` attribute.
3. **Alloy**: Receives OTLP log.
   - Adds `source="scanium-mobile"` external label (via `mobile_http` receiver pipeline).
   - Forwards to Loki.

***REMOVED******REMOVED*** Event Schema

Payload:
```json
{
  "event_name": "scan.completed",
  "platform": "android",
  "app_version": "1.0.42",
  "env": "dev",
  "session_id": "optional-uuid",
  "properties": {
    "item_count": 5
  },
  "count": 1,
  "duration_ms": 150
}
```

***REMOVED******REMOVED*** querying

Loki:
```logql
{source="scanium-mobile"}
```

Use `event_name` label/attribute to filter:
```logql
{source="scanium-mobile"} | json | event_name="scan.completed"
```
