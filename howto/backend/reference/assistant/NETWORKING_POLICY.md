# Assistant Networking Policy

This document describes the unified HTTP timeout and retry policy for all assistant-related network
requests in the Scanium Android app.

## Overview

All assistant HTTP clients use a centralized configuration (`AssistantHttpConfig`) and factory (
`AssistantOkHttpClientFactory`) to ensure consistent behavior across:

- Chat requests (text and multipart with images)
- Preflight health checks
- Warmup requests

## Timeout Configuration

### Production Chat Requests

Used for actual assistant chat interactions:

| Timeout | Value | Rationale                                                     |
|---------|-------|---------------------------------------------------------------|
| Connect | 15s   | Accommodates mobile network variability                       |
| Read    | 60s   | LLM responses can take time, especially with vision           |
| Write   | 30s   | Multipart uploads with images need time                       |
| Call    | 75s   | Overall request budget (connect + read + processing overhead) |
| Retries | 1     | One retry on transient errors                                 |

### Preflight Health Checks

Used for quick backend availability checks:

| Timeout | Value | Rationale                               |
|---------|-------|-----------------------------------------|
| Connect | 3s    | Fast fail for unavailable backends      |
| Read    | 3s    | Health checks should respond quickly    |
| Write   | 3s    | Minimal payload                         |
| Call    | 5s    | Total budget for health check           |
| Retries | 0     | No retries - just report current status |

### Warmup Requests

Used for priming connections and caches:

| Timeout | Value | Rationale                          |
|---------|-------|------------------------------------|
| Connect | 5s    | Moderate tolerance                 |
| Read    | 10s   | Not time-critical                  |
| Write   | 5s    | Empty or minimal payload           |
| Call    | 15s   | Generous for background operation  |
| Retries | 0     | No retries - warmup is best-effort |

### Test Configuration

Used in unit tests with MockWebServer:

| Timeout | Value | Rationale                                      |
|---------|-------|------------------------------------------------|
| Connect | 5s    | Fast enough for tests                          |
| Read    | 5s    | Fail fast on test issues                       |
| Write   | 5s    | Consistent                                     |
| Call    | 10s   | Overall test timeout                           |
| Retries | 0     | Tests should control retry behavior explicitly |

## Retry Policy

The `AssistantRetryInterceptor` implements a consistent retry-once policy for transient errors.

### Errors That ARE Retried

- **Socket timeouts** - Connection may succeed on retry
- **Network IOExceptions** - Transient network issues
- **HTTP 502 Bad Gateway** - Upstream server error
- **HTTP 503 Service Unavailable** - Server temporarily overloaded
- **HTTP 504 Gateway Timeout** - Upstream timeout

### Errors That Are NOT Retried

- **HTTP 400 Bad Request** - Client error, won't change on retry
- **HTTP 401 Unauthorized** - Authentication issue
- **HTTP 403 Forbidden** - Authorization issue
- **HTTP 404 Not Found** - Resource doesn't exist
- **HTTP 429 Rate Limited** - Respect rate limits, don't hammer
- **SSL/TLS errors** - Certificate issues shouldn't be retried

### Retry Delay

A 500ms delay is applied between retry attempts to avoid overwhelming the server.

## Error Mapping to User Messages

| Error Type              | HTTP Code(s)                  | User Message                       |
|-------------------------|-------------------------------|------------------------------------|
| NETWORK_TIMEOUT         | Socket timeout                | "Assistant request timed out"      |
| NETWORK_UNREACHABLE     | UnknownHost, ConnectException | "Unable to reach assistant server" |
| UNAUTHORIZED            | 401, 403                      | "Not authorized to use assistant"  |
| RATE_LIMITED            | 429                           | "Assistant rate limit exceeded"    |
| VALIDATION_ERROR        | 400                           | "Message could not be processed"   |
| PROVIDER_UNAVAILABLE    | 503                           | "Assistant provider unavailable"   |
| PROVIDER_NOT_CONFIGURED | -                             | "Assistant backend not configured" |

## Logging

### Startup Policy Log

On app startup, the assistant HTTP configuration is logged at INFO level:

```
AssistantHttp: Assistant HTTP Policy Initialized:
  Version: 1.2.3
  Timeouts: AssistantHttpConfig(connect=15s, read=60s, write=30s, call=75s, retries=1)
  Retry: 1x on transient errors (502/503/504, timeout, network)
  Non-retryable: 400/401/403/404/429
```

### Per-Request Logging

Each client type logs its configuration at DEBUG level:

- `AssistantHttp[chat]: AssistantHttpConfig(...)`
- `AssistantHttp[preflight]: AssistantHttpConfig(...)`

### Retry Logging

Retry attempts are logged at DEBUG level:

```
AssistantRetry: Transient error 503 on attempt 1/2, will retry in 500ms
AssistantRetry: Request succeeded on attempt 2/2
```

## Verifying on Device

### Testing Slow Responses

To verify timeouts work correctly:

1. **Use Developer Options** to simulate slow network
2. **Backend throttling**: If you control the backend, add artificial delays
3. **Charles Proxy**: Use throttling to slow responses

### Expected Behavior

1. **Chat request > 60s read time**: Should timeout and show "Assistant request timed out"
2. **Preflight > 5s**: Should timeout and return UNKNOWN status (allows chat attempt)
3. **502/503/504 from backend**: Should retry once, then show appropriate error

### Log Verification

Filter logcat by tag `AssistantHttp` or `AssistantRetry`:

```bash
adb logcat -s AssistantHttp:* AssistantRetry:*
```

## Implementation Files

| File                              | Purpose                                |
|-----------------------------------|----------------------------------------|
| `AssistantHttpConfig.kt`          | Timeout configuration data class       |
| `AssistantOkHttpClientFactory.kt` | Client factory with standardized setup |
| `AssistantRetryInterceptor.kt`    | Retry logic for transient errors       |
| `AssistantRepository.kt`          | Chat request handling                  |
| `AssistantPreflight.kt`           | Health check handling                  |

## Test Coverage

Unit tests verify:

- All predefined configurations have correct values
- Retry interceptor handles all error types correctly
- Factory creates clients with proper timeouts
- Tests use consistent TEST configuration

Test files:

- `AssistantHttpConfigTest.kt`
- `AssistantRetryInterceptorTest.kt`
- `AssistantOkHttpClientFactoryTest.kt`
