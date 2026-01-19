***REMOVED*** Assistant Preflight Implementation

This document describes the assistant preflight/warmup mechanism and how it determines UI
availability state.

***REMOVED******REMOVED*** Overview

The assistant preflight check verifies backend connectivity and authentication before the user
attempts to send a chat message. It runs on screen entry and can be re-triggered on connectivity
changes.

***REMOVED******REMOVED*** Endpoint

**Endpoint**: `POST {BASE_URL}/v1/assist/chat`

The preflight now uses the actual chat endpoint instead of a separate health endpoint. This ensures
we test the exact same authentication and routing path that real chat requests use.

***REMOVED******REMOVED******REMOVED*** Request Payload

Minimal valid JSON payload:

```json
{
  "message": "ping",
  "items": [],
  "history": []
}
```

**CRITICAL: Schema Requirements**

The backend Zod schema requires:

- `message`: string with min length 1 (required)
- `items`: array (required, can be empty `[]`)
- `history`: array (optional, can be omitted or empty `[]`)

The Android client MUST include `items` in the request even when empty. This requires
`encodeDefaults = true` in the kotlinx.serialization Json configuration.

Without `encodeDefaults = true`, default values are omitted from serialization:

```json
// BAD: Missing items field causes HTTP 400
{"message": "ping"}
```

With `encodeDefaults = true`:

```json
// GOOD: All required fields present
{"message": "ping", "items": [], "history": []}
```

***REMOVED******REMOVED******REMOVED*** Headers

| Header                | Value                 | Purpose                              |
|-----------------------|-----------------------|--------------------------------------|
| `X-API-Key`           | `<assistant api key>` | Authentication                       |
| `X-Scanium-Device-Id` | `<hashed device id>`  | Rate limiting (backend hashes again) |
| `X-Scanium-Preflight` | `true`                | Identifies preflight vs real request |
| `X-Client`            | `Scanium-Android`     | Client identification                |
| `X-App-Version`       | `<version>`           | App version for debugging            |

The device ID is hashed locally using SHA-256 before sending to protect user privacy.

***REMOVED******REMOVED*** State Derivation

The UI availability state is derived from the preflight result as follows:

| Preflight Status          | UI Availability                  | Can Send Messages | Rationale                                  |
|---------------------------|----------------------------------|-------------------|--------------------------------------------|
| `AVAILABLE`               | Available                        | Yes               | Backend is reachable and auth succeeded    |
| `CHECKING`                | Checking                         | No                | Check in progress                          |
| `UNKNOWN`                 | Available                        | Yes               | Couldn't determine - allow chat attempt    |
| `OFFLINE`                 | Unavailable (OFFLINE)            | No                | Network unreachable                        |
| `TEMPORARILY_UNAVAILABLE` | Unavailable (BACKEND_ERROR)      | No                | Backend error (5xx, etc.)                  |
| `RATE_LIMITED`            | Unavailable (RATE_LIMITED)       | No                | Too many requests                          |
| `UNAUTHORIZED`            | Available                        | Yes               | Auth failed on preflight but chat may work |
| `NOT_CONFIGURED`          | Unavailable (NOT_CONFIGURED)     | No                | No backend URL configured                  |
| `ENDPOINT_NOT_FOUND`      | Unavailable (ENDPOINT_NOT_FOUND) | No                | Wrong URL or tunnel route                  |

***REMOVED******REMOVED******REMOVED*** Key Behavior: Preflight Failures Don't Block Chat

The implementation ensures preflight failures **cannot permanently mark the assistant as unavailable
** when chat might still work:

1. **Timeout** → Returns `UNKNOWN` status → UI shows "Available" → User can try chat
2. **401/403 Auth Error** → Returns `UNKNOWN` status → UI shows "Available" → User can try chat
3. **IO Error** → Returns `UNKNOWN` status → UI shows "Available" → User can try chat

The actual chat request has a longer timeout (10-15s vs 2s for preflight) and may succeed even when
preflight failed.

***REMOVED******REMOVED******REMOVED*** Chat Success Overrides Preflight State

When a chat request returns HTTP 200, the UI immediately marks the assistant as "Available"
regardless of the previous preflight state. This ensures that:

- A temporary preflight failure doesn't permanently block the user
- The assistant state reflects actual capability, not a cached failure

***REMOVED******REMOVED*** Error Mapping

| HTTP Status                                           | Preflight Status        | Reason Code          | Blocks UI? |
|-------------------------------------------------------|-------------------------|----------------------|------------|
| 200                                                   | AVAILABLE               | -                    | No         |
| 200 (with `assistantError.type=provider_unavailable`) | TEMPORARILY_UNAVAILABLE | provider_unavailable | Yes        |
| 401                                                   | UNKNOWN                 | preflight_auth_401   | No         |
| 403                                                   | UNKNOWN                 | preflight_auth_403   | No         |
| 404                                                   | ENDPOINT_NOT_FOUND      | endpoint_not_found   | Yes        |
| 429                                                   | RATE_LIMITED            | http_429             | Yes        |
| 5xx                                                   | TEMPORARILY_UNAVAILABLE | http_5xx             | Yes        |
| Timeout                                               | UNKNOWN                 | timeout              | No         |
| DNS failure                                           | OFFLINE                 | dns_failure          | Yes        |
| Connection refused                                    | OFFLINE                 | connection_refused   | Yes        |

***REMOVED******REMOVED*** Verification

***REMOVED******REMOVED******REMOVED*** Logcat Filters

To monitor preflight activity:

```bash
adb logcat -s AssistantPreflight:* ScaniumAuth:*
```

Example log output:

```
I/AssistantPreflight: Preflight: host=api.scanium.app path=/v1/assist/chat status=200 latencyMs=234
I/AssistantPreflight: Preflight: AVAILABLE latency=234ms reason=null
```

***REMOVED******REMOVED******REMOVED*** curl Command

Test preflight with curl:

```bash
curl -X POST https://<BASE_URL>/v1/assist/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <API_KEY>" \
  -H "X-Scanium-Device-Id: test-device-hash" \
  -H "X-Scanium-Preflight: true" \
  -H "X-Client: Scanium-Android" \
  -d '{"message":"ping","items":[],"history":[]}'
```

Expected response for success:

```json
{"content": "...assistant response..."}
```

***REMOVED******REMOVED*** Files Changed

- `androidApp/src/main/java/com/scanium/app/network/DeviceIdProvider.kt` - New utility for device ID
- `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantPreflight.kt` - Updated
  preflight implementation
- `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantRepository.kt` - Added device
  ID header
- `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantViewModel.kt` - Updated state
  mapping
- `androidApp/src/test/java/com/scanium/app/selling/assistant/AssistantPreflightHttpTest.kt` -
  Updated tests
- `androidApp/src/test/java/com/scanium/app/selling/assistant/AssistantViewModelTest.kt` - Added
  regression tests
