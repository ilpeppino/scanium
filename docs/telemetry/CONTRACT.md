***REMOVED*** Telemetry Contract

**Module:** `shared:telemetry-contract`
**Package:** `com.scanium.telemetry`
**Status:** Stable (v1.0)

***REMOVED******REMOVED*** Overview

The telemetry contract defines a stable, platform-agnostic schema for capturing observability events across Scanium's Android and iOS applications. This contract is PII-safe by default and designed to integrate with future telemetry backends (OpenTelemetry, Sentry, custom analytics).

***REMOVED******REMOVED*** Key Principles

1. **Platform-agnostic**: No Android or iOS platform imports in this module
2. **PII-safe by default**: All attributes are sanitized before export
3. **Stable API**: Changes to this contract require careful versioning
4. **Minimal dependencies**: Only kotlinx.datetime and kotlinx.serialization

***REMOVED******REMOVED*** Schema

***REMOVED******REMOVED******REMOVED*** TelemetryEvent

The core event model:

```kotlin
@Serializable
data class TelemetryEvent(
    val name: String,                      // e.g., "scan.started"
    val severity: TelemetrySeverity,       // DEBUG, INFO, WARN, ERROR, FATAL
    val timestamp: Instant,                // UTC timestamp
    val attributes: Map<String, String>    // Key-value metadata
)
```

***REMOVED******REMOVED******REMOVED*** Required Attributes

Every telemetry event **must** include these attributes:

| Attribute       | Description                        | Example        |
|-----------------|------------------------------------|----------------|
| `platform`      | Platform identifier                | "android", "ios" |
| `app_version`   | Semantic version                   | "1.2.3"        |
| `build`         | Build number                       | "42"           |
| `env`           | Environment                        | "dev", "staging", "prod" |
| `session_id`    | Unique session identifier          | "uuid-abc-123" |

***REMOVED******REMOVED******REMOVED*** Optional Attributes

| Attribute   | Description                          | Example        |
|-------------|--------------------------------------|----------------|
| `trace_id`  | Distributed tracing identifier       | "trace-xyz-789" |

***REMOVED******REMOVED******REMOVED*** Example Event

```kotlin
val event = TelemetryEvent(
    name = "scan.completed",
    severity = TelemetrySeverity.INFO,
    timestamp = Clock.System.now(),
    attributes = mapOf(
        "platform" to "android",
        "app_version" to "1.0.0",
        "build" to "1",
        "env" to "prod",
        "session_id" to "abc-123",
        "scan_duration_ms" to "1234",
        "items_detected" to "5"
    )
)
```

***REMOVED******REMOVED*** Naming Conventions

All event names follow the pattern: `<prefix>.<action>`

***REMOVED******REMOVED******REMOVED*** Prefixes

| Prefix      | Usage                                  | Examples                          |
|-------------|----------------------------------------|-----------------------------------|
| `scan.*`    | Scan-related operations                | `scan.started`, `scan.completed`  |
| `ml.*`      | ML/AI model operations                 | `ml.inference_started`, `ml.classification_failed` |
| `storage.*` | Storage/persistence operations         | `storage.saved`, `storage.sync_failed` |
| `export.*`  | Export operations (CSV, JSON, etc.)    | `export.csv_completed`, `export.failed` |
| `ui.*`      | UI interactions                        | `ui.button_clicked`, `ui.screen_viewed` |
| `error.*`   | Error events                           | `error.network_timeout`, `error.parse_failed` |

***REMOVED******REMOVED******REMOVED*** Naming Rules

- Use lowercase with underscores for multi-word actions
- Use past tense for completed actions: `completed`, `failed`
- Use present tense for ongoing states: `started`, `in_progress`
- Keep names concise and descriptive

***REMOVED******REMOVED******REMOVED*** Validation

Use `TelemetryEventNaming.isValidEventName(name)` to validate event names:

```kotlin
TelemetryEventNaming.isValidEventName("scan.started")  // true
TelemetryEventNaming.isValidEventName("SCAN.STARTED")  // false (uppercase)
TelemetryEventNaming.isValidEventName("scan")          // false (no action)
```

***REMOVED******REMOVED*** PII Redaction

The `AttributeSanitizer` automatically redacts PII from event attributes before export.

***REMOVED******REMOVED******REMOVED*** PII Denylist

The following attribute keys are **automatically redacted** (case-insensitive, substring match):

**Personal Identifiers:**
- `email`, `phone`, `address`, `full_name`, `username`, `user_id`

**Authentication:**
- `token`, `auth`, `password`, `api_key`, `secret`, `credentials`, `cookie`, `session_token`, `access_token`, `bearer`

**Location:**
- `gps`, `latitude`, `lat`, `longitude`, `lon`, `location`, `coordinates`

**Biometric:**
- `fingerprint`, `face_id`, `biometric`

**Payment:**
- `credit_card`, `card_number`, `cvv`, `billing`, `payment`

**Device Identifiers:**
- `imei`, `device_id`, `mac_address`, `serial_number`, `uuid`

**Other:**
- `ssn`, `passport`, `license`, `ip_address`, `ip`

***REMOVED******REMOVED******REMOVED*** Sanitization Rules

1. **PII Removal**: Attributes matching the denylist are replaced with `[REDACTED]`
2. **Value Truncation**: String values exceeding 1024 characters are truncated with `...`
3. **Required Validation**: Use `AttributeSanitizer.validateRequiredAttributes()` to ensure all required fields are present

***REMOVED******REMOVED******REMOVED*** Usage

```kotlin
// Sanitize a raw attributes map
val raw = mapOf(
    "scan_duration_ms" to "1234",
    "email" to "user@example.com"
)
val sanitized = AttributeSanitizer.sanitize(raw)
// Result: {"scan_duration_ms": "1234", "email": "[REDACTED]"}

// Sanitize an entire event
val event = TelemetryEvent(...)
val sanitizedEvent = AttributeSanitizer.sanitizeEvent(event)
```

***REMOVED******REMOVED*** Integration Guidelines

***REMOVED******REMOVED******REMOVED*** Creating Events

```kotlin
import com.scanium.telemetry.*
import kotlinx.datetime.Clock

fun logScanStarted(sessionId: String, platform: String) {
    val event = TelemetryEvent(
        name = "scan.started",
        severity = TelemetrySeverity.INFO,
        timestamp = Clock.System.now(),
        attributes = mapOf(
            TelemetryEvent.ATTR_PLATFORM to platform,
            TelemetryEvent.ATTR_APP_VERSION to BuildConfig.VERSION_NAME,
            TelemetryEvent.ATTR_BUILD to BuildConfig.VERSION_CODE.toString(),
            TelemetryEvent.ATTR_ENV to TelemetryEvent.ENV_PROD,
            TelemetryEvent.ATTR_SESSION_ID to sessionId
        )
    )

    // Sanitize before sending to backend
    val sanitized = AttributeSanitizer.sanitizeEvent(event)

    // Send to telemetry backend (implementation in future PR)
    // telemetryBackend.send(sanitized)
}
```

***REMOVED******REMOVED******REMOVED*** Best Practices

1. **Always sanitize** events before exporting to external systems
2. **Validate required attributes** before creating events
3. **Use naming convention helpers** to ensure consistency
4. **Avoid PII in custom attributes** - the sanitizer is a safety net, not a license to include PII
5. **Keep attribute values concise** - aim for < 256 chars when possible
6. **Use structured keys** - prefer `scan_duration_ms` over `duration` for clarity

***REMOVED******REMOVED*** Testing

Run module tests:

```bash
./gradlew :shared:telemetry-contract:jvmTest
./gradlew :shared:telemetry-contract:testDebugUnitTest
./gradlew :shared:telemetry-contract:iosX64Test
```

***REMOVED******REMOVED*** Future Enhancements

This contract is intentionally minimal for PR ***REMOVED***1. Future PRs may add:

- OpenTelemetry integration (spans, traces)
- Sentry integration (error tracking)
- Batch event APIs
- Event filtering/sampling
- Custom backend adapters

***REMOVED******REMOVED*** Migration Notes

This module is new as of PR ***REMOVED***1. No migration required.

***REMOVED******REMOVED*** API Stability

This contract is **stable** as of v1.0. Breaking changes will require:
1. Major version bump
2. Deprecation period for removed fields
3. Migration guide for consumers
