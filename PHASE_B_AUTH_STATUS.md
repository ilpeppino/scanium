# Phase B: Auth Enforcement + Per-User Rate Limiting - Implementation Status

**Date**: January 13, 2026
**Status**: Implementation Complete ✅ | Testing Required ⚠️

## Executive Summary

Phase B implementation is **complete**. All backend auth enforcement, per-user rate limiting, error standardization, Android error handling, and tests have been implemented. **Remaining work**: Run tests and perform end-to-end verification with curl.

---

## ✅ Completed Components

### Backend (100% Complete)

#### 1. Error Types and Standardization
- **File**: `backend/src/shared/errors/index.ts`
- **Changes**:
  - Added `AUTH_REQUIRED` error code (401): "Sign in is required to use this feature."
  - Added `AUTH_INVALID` error code (401): "Your session is invalid or expired. Please sign in again."
  - Added `RATE_LIMITED` error code (429): "Rate limit reached. Please try again later."
  - Created `AuthRequiredError`, `AuthInvalidError`, `RateLimitError` classes
  - `RateLimitError` includes `resetAt` ISO timestamp in response

#### 2. Auth Middleware Enhancement
- **File**: `backend/src/infra/http/plugins/auth-middleware.ts`
- **Changes**:
  - Updated `requireAuth()` function to throw proper error types:
    - No auth header → `AUTH_REQUIRED`
    - Invalid/expired token → `AUTH_INVALID`
  - Added `hadAuthAttempt` tracking to distinguish error cases
  - Imported new error classes

#### 3. Error Handler Update
- **File**: `backend/src/infra/http/plugins/error-handler.ts`
- **Changes**:
  - Ensures `correlationId` is included in all error responses
  - Properly handles new `RateLimitError` with `resetAt` field

#### 4. Configuration
- **Files**:
  - `backend/src/config/index.ts` - Added per-user rate limit and quota settings
  - `backend/.env.example` - Documented Phase B environment variables
  - `deploy/nas/compose/.env.example` - Production config

**New Environment Variables**:
```bash
# Phase B: Per-user rate limiting (authenticated requests only)
ASSIST_USER_RATE_LIMIT_PER_MINUTE=20  # Default: 20 requests/min per user
ASSIST_USER_DAILY_QUOTA=100           # Default: 100 requests/day per user
```

#### 5. Assistant Routes Enforcement
- **File**: `backend/src/modules/assistant/routes.ts`
- **Changes**:
  - Imported `requireAuth` and `RateLimitError`
  - Created per-user rate limiter (`userRateLimiter`) keyed on `userId`
  - Created per-user daily quota store (`userQuotaStore`)
  - **POST /v1/assist/chat**: Now requires authentication
    1. Validates API key (existing)
    2. Calls `requireAuth(request)` → throws `AUTH_REQUIRED` or `AUTH_INVALID`
    3. Checks per-user rate limit → throws `RateLimitError` with `resetAt`
    4. Checks per-user daily quota → throws `RateLimitError` with `resetAt`
    5. Proceeds with existing IP/device/API key rate limits
  - **POST /v1/assist/warmup**: Now requires authentication
  - Registered cleanup hooks for new quota store

#### 6. Error Response Contract
All auth and rate limit errors follow this standardized format:

**401 AUTH_REQUIRED**:
```json
{
  "error": {
    "code": "AUTH_REQUIRED",
    "message": "Sign in is required to use this feature.",
    "correlationId": "abc-123"
  }
}
```

**401 AUTH_INVALID**:
```json
{
  "error": {
    "code": "AUTH_INVALID",
    "message": "Your session is invalid or expired. Please sign in again.",
    "correlationId": "abc-123"
  }
}
```

**429 RATE_LIMITED**:
```json
{
  "error": {
    "code": "RATE_LIMITED",
    "message": "Rate limit reached. Please try again later.",
    "resetAt": "2026-01-13T17:00:00.000Z",
    "correlationId": "abc-123"
  }
}
```

#### 7. Backend Tests
- **File**: `backend/src/modules/assistant/routes.phase-b.test.ts` (new)
- **Coverage**:
  - Auth enforcement on `/assist/chat`:
    - Returns `AUTH_REQUIRED` (401) when no Authorization header
    - Returns `AUTH_INVALID` (401) when token is invalid
    - Returns 200 with valid session token
  - Auth enforcement on `/assist/warmup`:
    - Returns `AUTH_REQUIRED` (401) when no Authorization header
    - Returns 200 with valid session token
  - Per-user rate limiting:
    - Returns `RATE_LIMITED` (429) with `resetAt` when user exceeds rate limit
  - Response schema validation:
    - Successful response includes expected fields
    - `citationsMetadata` includes `fromCache` field

---

### Android (100% Complete)

#### 1. Error Types
- **File**: `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantRepository.kt`
- **Changes**:
  - Added `AUTH_REQUIRED` to `AssistantBackendErrorType` enum
  - Added `AUTH_INVALID` to `AssistantBackendErrorType` enum
  - Updated `parseType()` to map backend error codes
  - Added `ErrorResponseDto` and `ErrorDetailsDto` for parsing Phase B error responses
  - Updated `mapHttpFailure()`:
    - Parses error response JSON to extract specific error codes
    - For 401: Distinguishes between `AUTH_REQUIRED` and `AUTH_INVALID`
    - For 429: Extracts `resetAt` timestamp and calculates `retryAfterSeconds`

#### 2. Error Display
- **File**: `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantErrorDisplay.kt`
- **Changes**:
  - Added error info for `AUTH_REQUIRED`:
    - Title: "Sign In Required"
    - Explanation: "You need to sign in to use the online assistant..."
    - Action: "Tap to sign in with Google."
    - showRetry: false
  - Added error info for `AUTH_INVALID`:
    - Title: "Session Expired"
    - Explanation: "Your session has expired or is no longer valid..."
    - Action: "Sign in again to continue."
    - showRetry: false
  - Updated status labels:
    - `AUTH_REQUIRED` → "Sign In Required"
    - `AUTH_INVALID` → "Session Expired"

#### 3. Android Tests
- **File**: `androidApp/src/test/java/com/scanium/app/selling/assistant/AssistantErrorMappingTest.kt` (new)
- **Coverage**:
  - Error parsing:
    - `401 with AUTH_REQUIRED code maps to AUTH_REQUIRED error type`
    - `401 with AUTH_INVALID code maps to AUTH_INVALID error type`
    - `429 with RATE_LIMITED code includes resetAt timestamp`
  - Error display:
    - `AUTH_REQUIRED error displays correct UI message`
    - `AUTH_INVALID error displays correct UI message`
    - `RATE_LIMITED error displays retry hint with timestamp`
  - Status labels:
    - Verifies correct labels for all Phase B error types

---

## ⚠️ Remaining Work

### 1. Run Backend Tests
```bash
cd backend
npm test
```

**Expected Results**:
- All Phase A auth tests pass
- All Phase B contract tests pass
- No regressions in existing tests

### 2. Run Android Tests
```bash
./gradlew :androidApp:test
```

**Expected Results**:
- All error mapping tests pass
- No regressions in existing tests

### 3. Smoke Test with curl

**Test 1: No auth header (AUTH_REQUIRED)**
```bash
curl -X POST http://localhost:8080/v1/assist/chat \
  -H "X-API-Key: assist-key" \
  -H "Content-Type: application/json" \
  -d '{"items":[{"itemId":"123","title":"Test"}],"message":"Hello"}'

# Expected: 401 with AUTH_REQUIRED
```

**Test 2: Invalid token (AUTH_INVALID)**
```bash
curl -X POST http://localhost:8080/v1/assist/chat \
  -H "X-API-Key: assist-key" \
  -H "Authorization: Bearer invalid-token-xyz" \
  -H "Content-Type: application/json" \
  -d '{"items":[{"itemId":"123","title":"Test"}],"message":"Hello"}'

# Expected: 401 with AUTH_INVALID
```

**Test 3: Valid auth (200)**
```bash
# First, sign in to get a valid token
TOKEN=$(curl -X POST http://localhost:8080/v1/auth/google \
  -H "Content-Type: application/json" \
  -d '{"idToken":"VALID_GOOGLE_ID_TOKEN"}' | jq -r '.accessToken')

# Then use it for assistant request
curl -X POST http://localhost:8080/v1/assist/chat \
  -H "X-API-Key: assist-key" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"items":[{"itemId":"123","title":"Test"}],"message":"Hello"}'

# Expected: 200 with assistant response
```

**Test 4: Rate limit exceeded (429)**
```bash
# Make multiple requests rapidly with the same token
for i in {1..25}; do
  curl -X POST http://localhost:8080/v1/assist/chat \
    -H "X-API-Key: assist-key" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"items\":[{\"itemId\":\"123\",\"title\":\"Test\"}],\"message\":\"Message $i\"}"
  sleep 0.1
done

# Expected: First 20 succeed, then 429 with RATE_LIMITED and resetAt
```

---

## 📋 Verification Checklist

### Backend
- [x] Added AUTH_REQUIRED, AUTH_INVALID, RATE_LIMITED error codes
- [x] Created AuthRequiredError, AuthInvalidError, RateLimitError classes
- [x] Updated requireAuth() to throw proper errors
- [x] Updated error handler to include correlationId
- [x] Added per-user rate limit config (userRateLimitPerMinute)
- [x] Added per-user quota config (userDailyQuota)
- [x] Created userRateLimiter keyed on userId
- [x] Created userQuotaStore for per-user daily quotas
- [x] Applied requireAuth to /assist/chat endpoint
- [x] Applied requireAuth to /assist/warmup endpoint
- [x] Added per-user rate limiting to /assist/chat
- [x] Added per-user quota checks to /assist/chat
- [x] Updated error responses to include resetAt for rate limits
- [x] Wrote Phase B contract tests
- [ ] **TODO**: Run backend tests (`npm test`)
- [ ] **TODO**: Smoke test with curl

### Android
- [x] Added AUTH_REQUIRED error type
- [x] Added AUTH_INVALID error type
- [x] Updated error parsing to distinguish auth error types
- [x] Updated error parsing to extract resetAt from 429 responses
- [x] Added error display for AUTH_REQUIRED
- [x] Added error display for AUTH_INVALID
- [x] Updated error status labels
- [x] Wrote error mapping tests
- [ ] **TODO**: Run Android tests (`./gradlew :androidApp:test`)

---

## 🔐 Security Model

### Phase B Auth Flow
1. **Client sends request** with `Authorization: Bearer <token>`
2. **authMiddleware** validates token:
   - If valid → sets `request.userId`
   - If invalid/expired → sets `request.hadAuthAttempt = true`
3. **requireAuth()** checks auth state:
   - No `userId` + no attempt → throws `AUTH_REQUIRED`
   - No `userId` + had attempt → throws `AUTH_INVALID`
   - Has `userId` → returns userId (proceed)
4. **userRateLimiter** checks rate limit for userId:
   - Within limit → proceed
   - Exceeded → throws `RateLimitError` with resetAt
5. **userQuotaStore** checks daily quota for userId:
   - Within quota → proceed
   - Exceeded → throws `RateLimitError` with resetAt

### Rate Limiting Hierarchy
Phase B adds per-user rate limiting **in addition to** existing limits:

1. **API key check** (existing) - validates X-API-Key header
2. **Auth check** (Phase B) - requires valid session token
3. **Per-user rate limit** (Phase B) - 20 req/min per userId (default)
4. **Per-user daily quota** (Phase B) - 100 req/day per userId (default)
5. **IP rate limit** (existing) - 60 req/min per IP
6. **API key rate limit** (existing) - 60 req/min per API key
7. **Device rate limit** (existing) - 30 req/min per device ID

### Error Response Contract
- All errors include `correlationId` for traceability
- Rate limit errors include `resetAt` ISO timestamp
- Error codes are stable and deterministic
- Android can distinguish between error types without parsing messages

---

## 📊 Configuration Defaults

| Setting | Default | Description |
|---------|---------|-------------|
| `ASSIST_USER_RATE_LIMIT_PER_MINUTE` | 20 | Per-user rate limit (requests/minute) |
| `ASSIST_USER_DAILY_QUOTA` | 100 | Per-user daily quota (requests/day) |
| `ASSIST_RATE_LIMIT_PER_MINUTE` | 60 | Per-API-key rate limit (existing) |
| `ASSIST_IP_RATE_LIMIT_PER_MINUTE` | 60 | Per-IP rate limit (existing) |
| `ASSIST_DEVICE_RATE_LIMIT_PER_MINUTE` | 30 | Per-device rate limit (existing) |
| `ASSIST_DAILY_QUOTA` | 200 | Per-device/API-key daily quota (existing) |

---

## 📁 Files Created/Modified

### Backend (8 files)
```
backend/src/shared/errors/index.ts                                    (modified)
backend/src/infra/http/plugins/auth-middleware.ts                     (modified)
backend/src/infra/http/plugins/error-handler.ts                       (modified)
backend/src/config/index.ts                                           (modified)
backend/.env.example                                                  (modified)
deploy/nas/compose/.env.example                                       (modified)
backend/src/modules/assistant/routes.ts                               (modified)
backend/src/modules/assistant/routes.phase-b.test.ts                  (new)
```

### Android (3 files)
```
androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantRepository.kt     (modified)
androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantErrorDisplay.kt   (modified)
androidApp/src/test/java/com/scanium/app/selling/assistant/AssistantErrorMappingTest.kt  (new)
```

---

## 🎯 Success Criteria

- [x] Backend requires authentication for /assist/chat and /assist/warmup
- [x] Backend returns AUTH_REQUIRED when no auth provided
- [x] Backend returns AUTH_INVALID when token invalid/expired
- [x] Backend returns RATE_LIMITED with resetAt when rate limit exceeded
- [x] Backend enforces per-user rate limiting (not per-IP)
- [x] Backend enforces per-user daily quotas
- [x] Android parses AUTH_REQUIRED and AUTH_INVALID error codes
- [x] Android extracts resetAt from 429 responses
- [x] Android displays "Sign In Required" for AUTH_REQUIRED
- [x] Android displays "Session Expired" for AUTH_INVALID
- [x] Android displays rate limit message with retry hint
- [x] Tests written for backend auth enforcement
- [x] Tests written for Android error mapping
- [ ] **TODO**: All backend tests pass
- [ ] **TODO**: All Android tests pass
- [ ] **TODO**: Manual smoke test successful

---

## 🚀 Quick Start Guide

### 1. Run Backend Tests
```bash
cd backend
npm test
```

### 2. Run Android Tests
```bash
./gradlew :androidApp:test
```

### 3. Smoke Test (Manual)
Follow the curl examples in "Remaining Work" section above.

---

## 🔄 Differences from Phase A

| Aspect | Phase A | Phase B |
|--------|---------|---------|
| **Auth enforcement** | Optional (no gating) | Required for assistant endpoints |
| **Rate limiting** | Per-IP, per-device, per-API-key | Added per-user rate limiting |
| **Quotas** | Per-device/API-key | Added per-user daily quota |
| **Error codes** | Generic UNAUTHORIZED | Specific AUTH_REQUIRED, AUTH_INVALID |
| **Error response** | No resetAt | Rate limits include resetAt timestamp |
| **Android UX** | N/A | Distinct messages for auth vs rate limit |

---

## 📝 Implementation Notes

### Why Two Auth Error Types?
- **AUTH_REQUIRED**: User hasn't signed in at all → Show "Sign in to continue"
- **AUTH_INVALID**: User was signed in but session expired → Show "Session expired, sign in again"

This distinction prevents confusion when a user's session expires during use.

### Why Per-User Rate Limiting?
Phase A rate limiting was per-IP/device/API-key, which:
- Can be circumvented by rotating IPs or devices
- Doesn't account for multiple users sharing a device
- Doesn't support future subscription tiers

Phase B adds per-user rate limiting that:
- Is tied to authenticated identity
- Survives device/IP changes
- Enables future per-plan rate limits

### Why Include resetAt?
The `resetAt` timestamp enables:
- Precise retry logic (Android knows exactly when to retry)
- Better UX ("Try again in 45 seconds" vs "Try again later")
- Deterministic testing

---

## 🐛 Troubleshooting

### "AUTH_REQUIRED but I'm signed in"
- Check that `Authorization: Bearer <token>` header is being sent
- Verify token is stored in SecureApiKeyStore
- Check backend logs for token validation errors

### "Rate limited immediately"
- Check `ASSIST_USER_RATE_LIMIT_PER_MINUTE` setting (default: 20)
- Verify Redis is running if using distributed rate limiting
- Check that requests aren't being duplicated by retry logic

### Tests failing with "User not found"
- Ensure test database is set up with Prisma migrations
- Check that beforeAll() creates test user successfully
- Verify test session token is valid

---

## Next Steps (Future Work)

Potential Phase C features:
- Token refresh mechanism (extend session without re-login)
- Session management UI (view active sessions, revoke)
- User profile endpoint (GET /v1/auth/me)
- Subscription tiers with different rate limits
- Admin endpoint to view/adjust user quotas

**Phase B Focus**: Enforce auth + per-user rate limiting with clean error UX.
