***REMOVED*** Diagnostics Ring Buffer

**Module:** `shared:diagnostics`
**Package:** `com.scanium.diagnostics`
**Depends on:** `shared:telemetry-contract`
**Status:** Stable (v1.0)

***REMOVED******REMOVED*** Overview

The diagnostics module provides bounded in-memory storage for recent telemetry events (breadcrumbs)
and the ability to build compact diagnostic bundles for crash reports or "send report"
functionality.

***REMOVED******REMOVED*** Key Features

- **Ring Buffer Storage:** FIFO eviction with configurable limits (event count + byte size)
- **Thread-Safe:** All operations synchronized for concurrent access
- **Memory-Only:** No file I/O (suitable for crash scenarios)
- **PII-Safe:** Events stored are already sanitized (from telemetry-contract)
- **JSON Export:** Compact, deterministic bundle format

***REMOVED******REMOVED*** Architecture

```
┌─────────────────────────────────────┐
│   Shared Code / Telemetry Facade   │
│                                     │
│  telemetry.event("scan.started")   │
│           ↓                         │
│  diagnosticsPort.appendBreadcrumb() │
└──────────────┬──────────────────────┘
               │
               ↓
┌──────────────────────────────────────┐
│   DefaultDiagnosticsPort             │
│  ┌────────────────────────────────┐  │
│  │  DiagnosticsBuffer (ring)      │  │
│  │  - Max 200 events              │  │
│  │  - Max 256KB bytes             │  │
│  │  - FIFO eviction               │  │
│  └────────────────────────────────┘  │
│                                      │
│  ┌────────────────────────────────┐  │
│  │  DiagnosticsBundleBuilder      │  │
│  │  - Builds JSON bundle          │  │
│  │  - Includes context + events   │  │
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘
               │
               ↓ (on crash or user request)
      buildDiagnosticsBundle()
               ↓
         ByteArray (JSON)
```

***REMOVED******REMOVED*** Components

***REMOVED******REMOVED******REMOVED*** 1. DiagnosticsBuffer

Thread-safe ring buffer with dual limits:

- **maxEvents**: Maximum number of events (default: 200)
- **maxBytes**: Maximum total byte size (default: 256KB)

When limits are exceeded, oldest events are evicted (FIFO).

```kotlin
val buffer = DiagnosticsBuffer(
    maxEvents = 200,
    maxBytes = 256 * 1024
)

buffer.append(telemetryEvent)
val snapshot = buffer.snapshot()  // Immutable copy
buffer.clear()
```

***REMOVED******REMOVED******REMOVED*** 2. DiagnosticsBundleBuilder

Builds JSON bundles for crash reports:

```kotlin
val builder = DiagnosticsBundleBuilder()

val context = mapOf(
    "platform" to "android",
    "app_version" to "1.0.0",
    "build" to "42",
    "env" to "prod",
    "session_id" to "session-abc-123"
)

val events = buffer.snapshot()

val jsonBytes = builder.buildJsonBytes(context, events)
// Or: val jsonString = builder.buildJsonString(context, events)
```

**Bundle Format:**

```json
{
  "generatedAt": "2025-12-24T10:30:00Z",
  "context": {
    "platform": "android",
    "app_version": "1.0.0",
    "build": "42",
    "env": "prod",
    "session_id": "session-abc-123"
  },
  "events": [
    {
      "name": "scan.started",
      "severity": "INFO",
      "timestamp": "2025-12-24T10:29:55Z",
      "attributes": {
        "scan_mode": "continuous"
      }
    },
    ...
  ]
}
```

***REMOVED******REMOVED******REMOVED*** 3. DiagnosticsPort

Interface for diagnostics collection:

```kotlin
interface DiagnosticsPort {
    fun appendBreadcrumb(event: TelemetryEvent)
    fun buildDiagnosticsBundle(): ByteArray
    fun clearBreadcrumbs()
    fun breadcrumbCount(): Int
}
```

**Implementations:**

- `DefaultDiagnosticsPort`: Uses DiagnosticsBuffer + DiagnosticsBundleBuilder
- `NoOpDiagnosticsPort`: Discards all breadcrumbs (testing/disabled)

***REMOVED******REMOVED*** Usage

***REMOVED******REMOVED******REMOVED*** 1. Setup (Platform Code)

```kotlin
// In Android app initialization
val diagnosticsPort = DefaultDiagnosticsPort(
    contextProvider = {
        mapOf(
            TelemetryEvent.ATTR_PLATFORM to TelemetryEvent.PLATFORM_ANDROID,
            TelemetryEvent.ATTR_APP_VERSION to BuildConfig.VERSION_NAME,
            TelemetryEvent.ATTR_BUILD to BuildConfig.VERSION_CODE.toString(),
            TelemetryEvent.ATTR_ENV to getEnvironment(),
            TelemetryEvent.ATTR_SESSION_ID to sessionManager.currentSessionId
        )
    },
    maxEvents = 200,
    maxBytes = 256 * 1024
)

// Integrate with Telemetry facade (see PR ***REMOVED***2)
val telemetry = Telemetry(
    defaultAttributesProvider = ...,
    logPort = ...,
    metricPort = ...,
    tracePort = ...,
    diagnosticsPort = diagnosticsPort  // Optional parameter
)
```

***REMOVED******REMOVED******REMOVED*** 2. Auto-Capture in Telemetry Facade

The telemetry facade can automatically append breadcrumbs:

```kotlin
// In shared code - business logic doesn't change
telemetry.info("scan.started", mapOf("mode" to "continuous"))
// ↓ Automatically captured as breadcrumb by diagnosticsPort
```

***REMOVED******REMOVED******REMOVED*** 3. Crash Report Integration

```kotlin
// In platform-specific crash handler
class CrashHandler(
    private val diagnosticsPort: DiagnosticsPort
) {
    fun handleUncaughtException(exception: Throwable) {
        // Build diagnostics bundle
        val diagnosticsBundle = diagnosticsPort.buildDiagnosticsBundle()

        // Attach to crash report
        crashReporter.sendCrash(
            exception = exception,
            attachments = mapOf(
                "diagnostics.json" to diagnosticsBundle
            )
        )

        // Clear breadcrumbs for next session
        diagnosticsPort.clearBreadcrumbs()
    }
}
```

***REMOVED******REMOVED******REMOVED*** 4. Manual "Send Report" Feature

```kotlin
// User clicks "Send Report" button
class ReportSender(
    private val diagnosticsPort: DiagnosticsPort,
    private val httpClient: HttpClient
) {
    suspend fun sendUserReport(userMessage: String) {
        val diagnosticsBundle = diagnosticsPort.buildDiagnosticsBundle()

        httpClient.post("/api/reports") {
            setBody(multipartFormData {
                append("message", userMessage)
                append("diagnostics", diagnosticsBundle, Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                    append(HttpHeaders.ContentDisposition, "filename=diagnostics.json")
                })
            })
        }

        // Clear after successful send
        diagnosticsPort.clearBreadcrumbs()
    }
}
```

***REMOVED******REMOVED*** Ring Buffer Behavior

***REMOVED******REMOVED******REMOVED*** Event Count Limit

When the buffer reaches `maxEvents`, the oldest event is evicted:

```kotlin
val buffer = DiagnosticsBuffer(maxEvents = 3, maxBytes = 100000)

buffer.append(event1)  // Buffer: [event1]
buffer.append(event2)  // Buffer: [event1, event2]
buffer.append(event3)  // Buffer: [event1, event2, event3]
buffer.append(event4)  // Buffer: [event2, event3, event4] (event1 evicted)
```

***REMOVED******REMOVED******REMOVED*** Byte Size Limit

When adding an event would exceed `maxBytes`, old events are evicted:

```kotlin
val buffer = DiagnosticsBuffer(maxEvents = 100, maxBytes = 1024)

buffer.append(largeEvent1)  // 800 bytes - OK
buffer.append(largeEvent2)  // 400 bytes - would exceed limit
                            // → event1 evicted first, then event2 added
```

***REMOVED******REMOVED******REMOVED*** Single Large Event

If a single event is larger than `maxBytes`, it's silently dropped:

```kotlin
val buffer = DiagnosticsBuffer(maxBytes = 1024)

val hugeEvent = createEvent(attributes = "x".repeat(10000))
buffer.append(hugeEvent)  // Silently dropped, buffer remains empty
```

***REMOVED******REMOVED*** Thread Safety

All operations are synchronized:

```kotlin
// Thread 1
diagnosticsPort.appendBreadcrumb(event1)

// Thread 2 (concurrent)
diagnosticsPort.appendBreadcrumb(event2)

// Thread 3 (concurrent)
val bundle = diagnosticsPort.buildDiagnosticsBundle()

// All operations are safe
```

***REMOVED******REMOVED*** Best Practices

1. **Set Appropriate Limits:**
    - `maxEvents = 200` captures ~10 minutes of activity at ~20 events/min
    - `maxBytes = 256KB` is conservative for mobile crash reporters

2. **Clear After Send:**
    - Always clear breadcrumbs after successfully sending a report
    - Prevents duplicate reports with stale breadcrumbs

3. **Context Provider:**
    - Keep context provider lightweight (called on every bundle build)
    - Include only essential fields (platform, version, session)

4. **NoOp for Testing:**
    - Use `NoOpDiagnosticsPort` in unit tests to avoid memory accumulation
    - Use real port in integration tests to verify breadcrumb capture

5. **Memory Management:**
    - Default limits (200 events, 256KB) are safe for most apps
    - Reduce limits for memory-constrained environments
    - Monitor `breadcrumbCount()` in development

***REMOVED******REMOVED*** Limitations

- **Memory-Only:** Breadcrumbs are lost if app is force-killed before crash handling
- **No Persistence:** No disk storage (by design for crash safety)
- **Approximate Byte Size:** Size estimation uses JSON serialization (slight overhead)
- **Single Buffer:** No separate buffers for different event types

***REMOVED******REMOVED*** Future Enhancements (Post-PR ***REMOVED***3)

- Persistent breadcrumb storage (optional, for non-crash scenarios)
- Breadcrumb sampling strategies (e.g., keep all errors, sample info)
- Multiple named buffers (e.g., separate UI vs business logic breadcrumbs)
- Compression for large bundles
- Automatic bundle size limits in builder

***REMOVED******REMOVED*** Migration Notes

This module is new as of PR ***REMOVED***3. No migration required.

***REMOVED******REMOVED*** API Stability

This module is **stable** as of v1.0. Breaking changes will require:

1. Major version bump
2. Deprecation period for removed methods
3. Migration guide for consumers
