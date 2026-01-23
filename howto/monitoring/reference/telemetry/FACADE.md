# Telemetry Facade

**Module:** `shared:telemetry`
**Package:** `com.scanium.telemetry.facade`
**Depends on:** `shared:telemetry-contract`
**Status:** Stable (v1.0)

## Overview

The telemetry facade provides a clean, dependency-injection-friendly API for emitting telemetry from
shared business logic. It abstracts backend implementations behind port interfaces, allowing
platform-specific code to plug in OpenTelemetry, Sentry, or custom analytics without changing shared
code.

## Architecture

```
┌─────────────────────────────────────────┐
│     Shared Business Logic (KMP)        │
│  ┌───────────────────────────────────┐  │
│  │   Telemetry Facade (this module) │  │
│  │  - event(), counter(), span()    │  │
│  │  - Auto-sanitization              │  │
│  │  - Attribute merging              │  │
│  └──────────┬────────────────────────┘  │
│             │ uses                      │
│  ┌──────────▼────────────────────────┐  │
│  │   Ports (interfaces)              │  │
│  │  - LogPort, MetricPort, TracePort │  │
│  └───────────────────────────────────┘  │
└─────────────────┬───────────────────────┘
                  │ implements
       ┌──────────▼──────────────┐
       │  Platform-Specific Code │
       │  - OpenTelemetry impl   │
       │  - Sentry impl          │
       │  - Custom impl          │
       └─────────────────────────┘
```

## Key Concepts

### 1. Port Interfaces

Ports define contracts for telemetry backends:

- **LogPort**: Emits structured log events
- **MetricPort**: Records counters, timers, gauges
- **TracePort**: Creates distributed tracing spans

### 2. Telemetry Facade

The main API used by shared code. Responsibilities:

- Merges user attributes with default attributes (platform, version, etc.)
- Sanitizes all attributes using `AttributeSanitizer` from telemetry-contract
- Enforces required attribute presence (fail-fast)
- Delegates to port implementations

### 3. Default Attributes Provider

Platform-specific code implements `DefaultAttributesProvider` to supply:

- `platform`: "android" or "ios"
- `app_version`: Semantic version
- `build`: Build number
- `env`: Environment (dev, staging, prod)
- `session_id`: Unique session identifier

### 4. NoOp Implementations

Provided for testing and scenarios where telemetry is disabled:

- `NoOpLogPort`
- `NoOpMetricPort`
- `NoOpTracePort`

## Usage

### 1. Platform Setup (Android Example)

```kotlin
// In Android-specific code (e.g., androidApp module)
class AndroidDefaultAttributes(
    private val context: Context,
    private val sessionManager: SessionManager
) : DefaultAttributesProvider {
    override fun getDefaultAttributes(): Map<String, String> = mapOf(
        TelemetryEvent.ATTR_PLATFORM to TelemetryEvent.PLATFORM_ANDROID,
        TelemetryEvent.ATTR_APP_VERSION to BuildConfig.VERSION_NAME,
        TelemetryEvent.ATTR_BUILD to BuildConfig.VERSION_CODE.toString(),
        TelemetryEvent.ATTR_ENV to getEnvironment(),
        TelemetryEvent.ATTR_SESSION_ID to sessionManager.currentSessionId
    )

    private fun getEnvironment(): String {
        return if (BuildConfig.DEBUG) {
            TelemetryEvent.ENV_DEV
        } else {
            TelemetryEvent.ENV_PROD
        }
    }
}

// Initialize telemetry (e.g., in Application.onCreate())
val telemetry = Telemetry(
    defaultAttributesProvider = AndroidDefaultAttributes(context, sessionManager),
    logPort = MyOpenTelemetryLogPort(),      // Or NoOpLogPort for now
    metricPort = MyOpenTelemetryMetricPort(), // Or NoOpMetricPort for now
    tracePort = MyOpenTelemetryTracePort()    // Or NoOpTracePort for now
)

// Make available to shared code (e.g., via dependency injection)
```

### 2. Shared Code Usage

#### Emitting Events

```kotlin
// In shared business logic
class ScanProcessor(private val telemetry: Telemetry) {

    fun startScan() {
        telemetry.info("scan.started", mapOf(
            "scan_mode" to "continuous",
            "resolution" to "high"
        ))
    }

    fun processingError(error: Exception) {
        telemetry.error("scan.processing_failed", mapOf(
            "error_type" to error::class.simpleName.orEmpty(),
            "error_message" to error.message.orEmpty()
        ))
    }
}
```

#### Recording Metrics

```kotlin
class MLClassifier(private val telemetry: Telemetry) {

    fun classify(image: Image): Classification {
        val start = Clock.System.now()

        val result = performClassification(image)

        val duration = (Clock.System.now() - start).inWholeMilliseconds

        // Record timing
        telemetry.timer("ml.classification_duration_ms", duration, mapOf(
            "model" to "mobilenet_v2",
            "confidence" to result.confidence.toString()
        ))

        // Increment counter
        telemetry.counter("ml.classifications_total", delta = 1)

        return result
    }
}
```

#### Creating Spans

```kotlin
class StorageManager(private val telemetry: Telemetry) {

    suspend fun exportData(format: String): Result<File> {
        // Option 1: Manual span management
        val span = telemetry.beginSpan("storage.export", mapOf(
            "format" to format
        ))

        return try {
            val file = performExport(format)
            span.setAttribute("file_size_bytes", file.length().toString())
            Result.success(file)
        } catch (e: Exception) {
            span.recordError(e.message ?: "Export failed")
            Result.failure(e)
        } finally {
            span.end()
        }
    }

    suspend fun importData(file: File): Result<Unit> {
        // Option 2: Automatic span management
        return telemetry.span("storage.import", mapOf(
            "file_name" to file.name,
            "file_size_bytes" to file.length().toString()
        )) { span ->
            try {
                performImport(file)
                Result.success(Unit)
            } catch (e: Exception) {
                // Exception is automatically recorded by span() helper
                Result.failure(e)
            }
        }
    }
}
```

## Sanitization and Required Attributes

### Automatic PII Sanitization

All user-provided attributes are automatically sanitized:

```kotlin
telemetry.info("user.login", mapOf(
    "username" to "john_doe",       // Will be redacted
    "email" to "john@example.com",  // Will be redacted
    "login_method" to "oauth"       // Safe, will be preserved
))

// Result: {
//   "username": "[REDACTED]",
//   "email": "[REDACTED]",
//   "login_method": "oauth",
//   "platform": "android",
//   "app_version": "1.0.0",
//   ...
// }
```

### Required Attributes Enforcement

The facade validates that required attributes are present. If missing, it throws
`IllegalStateException`:

```kotlin
// BAD: DefaultAttributesProvider missing required fields
class BrokenProvider : DefaultAttributesProvider {
    override fun getDefaultAttributes() = mapOf(
        "platform" to "android"
        // Missing: app_version, build, env, session_id
    )
}

val telemetry = Telemetry(
    defaultAttributesProvider = BrokenProvider(),
    logPort = NoOpLogPort,
    metricPort = NoOpMetricPort,
    tracePort = NoOpTracePort
)

// This will throw IllegalStateException
telemetry.info("test.event")
// Exception: Missing required telemetry attributes: app_version, build, env, session_id
```

### Attribute Merging

User attributes override default attributes:

```kotlin
// Default provider supplies env="prod"
telemetry.info("test.event", mapOf(
    "env" to "staging"  // Overrides default
))
// Result: env will be "staging"
```

## Testing

### Using NoOp Ports

```kotlin
class MyServiceTest {
    @Test
    fun testBusinessLogic() {
        val telemetry = Telemetry(
            defaultAttributesProvider = TestDefaultAttributesProvider(),
            logPort = NoOpLogPort,
            metricPort = NoOpMetricPort,
            tracePort = NoOpTracePort
        )

        val service = MyService(telemetry)
        service.doWork()

        // Assert business logic, telemetry is silently discarded
    }
}
```

### Using Test Doubles

```kotlin
class CapturingLogPort : LogPort {
    val events = mutableListOf<TelemetryEvent>()

    override fun emit(event: TelemetryEvent) {
        events.add(event)
    }
}

class MyServiceTest {
    @Test
    fun testEventEmission() {
        val logPort = CapturingLogPort()
        val telemetry = Telemetry(
            defaultAttributesProvider = TestDefaultAttributesProvider(),
            logPort = logPort,
            metricPort = NoOpMetricPort,
            tracePort = NoOpTracePort
        )

        val service = MyService(telemetry)
        service.doWork()

        assertEquals(1, logPort.events.size)
        assertEquals("work.completed", logPort.events[0].name)
    }
}
```

## Best Practices

1. **Dependency Injection**: Inject `Telemetry` into shared code classes rather than accessing a
   global instance
2. **Minimal Attributes**: Only include attributes that add value for debugging/analysis
3. **Avoid PII**: Never intentionally include PII; sanitizer is a safety net
4. **Use Naming Conventions**: Follow the event naming patterns from `TelemetryEventNaming`
5. **Fail-Fast**: Let the facade throw exceptions for misconfiguration in development/tests
6. **Span Lifecycle**: Always end spans, preferably using the `span { }` helper or try-finally
7. **Platform Separation**: Keep backend implementations in platform-specific modules

## Future Enhancements (Post-PR #2)

- OpenTelemetry port implementations (PR #3)
- Sentry port implementations (PR #4)
- Batching/buffering for high-volume metrics
- Sampling strategies for traces
- Context propagation for distributed tracing

## Migration Notes

This module is new as of PR #2. No migration required.

## API Stability

This facade is **stable** as of v1.0. Breaking changes will require:

1. Major version bump
2. Deprecation period for removed methods
3. Migration guide for consumers
