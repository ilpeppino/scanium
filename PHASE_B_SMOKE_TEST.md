# Phase B Smoke Test Guide

**Date**: January 13, 2026
**Prerequisites**: Phase B must be deployed to the backend

---

## Current Status

✅ **Phase B Implementation**: Complete
⚠️ **Backend Deployment**: Phase B not yet deployed to https://scanium.gtemp1.com
⚠️ **Database Migration**: Requires running `npx prisma migrate dev` on deployment

---

## Setup Required

### 1. Deploy Phase B to Backend

```bash
# On the backend server:
cd /path/to/scanium/backend

# Pull latest code with Phase B changes
git pull

# Install dependencies (if needed)
npm install

# Run Prisma migration to add User and UserSession tables
npx prisma migrate dev --name add_google_auth

# Restart backend service
# (Use your deployment method: docker-compose restart, systemctl restart, etc.)
```

### 2. Verify Environment Variables

Ensure these are set in backend `.env`:

```bash
# Phase A: Google OAuth
GOOGLE_OAUTH_CLIENT_ID=your_android_client_id.apps.googleusercontent.com
AUTH_SESSION_SECRET=base64_32_bytes_min
AUTH_SESSION_EXPIRY_SECONDS=2592000

# Phase B: Per-user rate limits (optional, has defaults)
ASSIST_USER_RATE_LIMIT_PER_MINUTE=20
ASSIST_USER_DAILY_QUOTA=100
```

---

## Smoke Test Procedures

### Test 1: Verify Backend is Running

```bash
curl -s https://scanium.gtemp1.com/health | jq
```

**Expected Output:**
```json
{
  "status": "ok",
  "ts": "2026-01-13T...",
  "version": "1.0.0",
  "assistant": {
    "providerConfigured": true,
    "providerReachable": true,
    "state": "ENABLED"
  }
}
```

---

### Test 2: AUTH_REQUIRED (No Authorization Header)

```bash
curl -s -X POST https://scanium.gtemp1.com/v1/assist/chat \
  -H "X-API-Key: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"items":[{"itemId":"123","title":"Test Item"}],"message":"Hello"}' \
  | jq
```

**Expected Output (Phase B):**
```json
{
  "error": {
    "code": "AUTH_REQUIRED",
    "message": "Sign in is required to use this feature.",
    "correlationId": "abc-123-..."
  }
}
```

**HTTP Status**: 401

---

### Test 3: AUTH_INVALID (Invalid Token)

```bash
curl -s -X POST https://scanium.gtemp1.com/v1/assist/chat \
  -H "X-API-Key: YOUR_API_KEY" \
  -H "Authorization: Bearer invalid-token-xyz-123" \
  -H "Content-Type: application/json" \
  -d '{"items":[{"itemId":"123","title":"Test Item"}],"message":"Hello"}' \
  | jq
```

**Expected Output (Phase B):**
```json
{
  "error": {
    "code": "AUTH_INVALID",
    "message": "Your session is invalid or expired. Please sign in again.",
    "correlationId": "abc-123-..."
  }
}
```

**HTTP Status**: 401

---

### Test 4: Successful Request (Valid Auth)

#### Step 4a: Sign In and Get Token

This requires using the Android app or simulating the Google sign-in flow. For testing, you can:

1. **Option A**: Use Android app to sign in, then extract token from logs
2. **Option B**: Create a test user directly in DB (for dev/test environments only)

**For testing, create a test user and session:**

```bash
# Connect to your Postgres DB
psql postgresql://user:pass@localhost:5432/scanium

# Create test user
INSERT INTO "User" (id, email, "displayName", "googleId", "createdAt", "updatedAt")
VALUES (
  gen_random_uuid(),
  'test@example.com',
  'Test User',
  'test-google-id-123',
  NOW(),
  NOW()
) RETURNING id;

# Note the returned user ID, then create a session
# Replace USER_ID_HERE with the actual UUID from above
INSERT INTO "UserSession" (id, "userId", "sessionToken", "expiresAt", "createdAt", "lastUsedAt")
VALUES (
  gen_random_uuid(),
  'USER_ID_HERE',
  'test-session-token-for-smoke-testing',
  NOW() + INTERVAL '1 day',
  NOW(),
  NOW()
);
```

#### Step 4b: Test with Valid Token

```bash
curl -s -X POST https://scanium.gtemp1.com/v1/assist/chat \
  -H "X-API-Key: YOUR_API_KEY" \
  -H "Authorization: Bearer test-session-token-for-smoke-testing" \
  -H "Content-Type: application/json" \
  -d '{"items":[{"itemId":"123","title":"Vintage Camera"}],"message":"What is this?"}' \
  | jq
```

**Expected Output (Phase B - Success):**
```json
{
  "reply": "This appears to be a vintage camera...",
  "actions": [],
  "citationsMetadata": {
    "fromCache": false
  },
  "correlationId": "abc-123-...",
  "safety": {
    "blocked": false,
    "reasonCode": null,
    "requestId": "..."
  }
}
```

**HTTP Status**: 200

---

### Test 5: Rate Limit Exceeded (429)

Make 25+ rapid requests with the same valid token:

```bash
TOKEN="test-session-token-for-smoke-testing"
API_KEY="YOUR_API_KEY"

# Make 25 requests rapidly
for i in {1..25}; do
  echo "Request $i:"
  curl -s -X POST https://scanium.gtemp1.com/v1/assist/chat \
    -H "X-API-Key: $API_KEY" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"items\":[{\"itemId\":\"123\",\"title\":\"Test\"}],\"message\":\"Message $i\"}" \
    | jq -r '.error.code // .reply[:20]'
  sleep 0.1
done
```

**Expected Output:**
- First 20 requests: Success responses (200)
- Requests 21+: Rate limit error (429)

**Rate Limit Response (Phase B):**
```json
{
  "error": {
    "code": "RATE_LIMITED",
    "message": "Rate limit reached. Please try again later.",
    "resetAt": "2026-01-13T15:30:00.000Z",
    "correlationId": "abc-123-..."
  }
}
```

**HTTP Status**: 429

**Validation:**
- `resetAt` is a valid ISO timestamp
- `resetAt` is in the future
- Request with same token after `resetAt` succeeds

---

### Test 6: Warmup Endpoint (Also Protected)

```bash
# Without auth - should return AUTH_REQUIRED
curl -s -X POST https://scanium.gtemp1.com/v1/assist/warmup \
  -H "X-API-Key: YOUR_API_KEY" \
  | jq

# With valid auth - should return 200
curl -s -X POST https://scanium.gtemp1.com/v1/assist/warmup \
  -H "X-API-Key: YOUR_API_KEY" \
  -H "Authorization: Bearer $TOKEN" \
  | jq
```

**Expected Output (with auth):**
```json
{
  "status": "ok",
  "provider": "openai",
  "ready": true,
  "ts": "2026-01-13T..."
}
```

---

## Android App Testing

### 1. Sign In Flow

1. Open Scanium app
2. Go to Settings > General
3. Tap "Continue with Google"
4. Complete Google sign-in
5. Verify "Signed in as [email]" appears

### 2. Assistant Usage

1. Scan or select an item
2. Open Assistant (chat icon)
3. Send a message
4. **Expected**: Assistant responds normally (using authenticated session)

### 3. Rate Limit UX

1. Make 20+ assistant requests rapidly
2. **Expected**: After 20 requests, see message:
   - "Switched to Local Helper: Rate Limited (wait XXs)."
   - Assistant continues working in local mode

### 4. Session Expiry UX

1. Sign out from Settings
2. Try to use Assistant
3. **Expected**: Message appears:
   - "Switched to Local Helper: Sign in required. Tap Settings to sign in."

---

## Verification Checklist

### Backend
- [ ] Prisma migration applied successfully
- [ ] `User` and `UserSession` tables exist in database
- [ ] Backend starts without errors
- [ ] Health endpoint returns `status: ok`
- [ ] Test 2 (No auth) returns `AUTH_REQUIRED` (401)
- [ ] Test 3 (Invalid token) returns `AUTH_INVALID` (401)
- [ ] Test 4 (Valid auth) returns success (200)
- [ ] Test 5 (Rate limit) returns `RATE_LIMITED` (429) with `resetAt`
- [ ] Test 6 (Warmup) requires auth

### Android
- [ ] App builds successfully (`./gradlew :androidApp:assembleDevDebug`)
- [ ] Sign-in flow works (Google Credential Manager)
- [ ] Assistant works when signed in
- [ ] Rate limit message shows after 20 requests
- [ ] "Sign in required" message shows when not signed in
- [ ] No "You're offline" shown for auth/rate limit errors

### Error Contract
- [ ] All errors include `correlationId`
- [ ] `AUTH_REQUIRED` has code `"AUTH_REQUIRED"`
- [ ] `AUTH_INVALID` has code `"AUTH_INVALID"`
- [ ] `RATE_LIMITED` has code `"RATE_LIMITED"` and `resetAt` timestamp
- [ ] `resetAt` is valid ISO 8601 format
- [ ] `resetAt` is in the future

---

## Troubleshooting

### "AUTH_REQUIRED but I'm signed in on Android"

1. Check Android logs for `X-Scanium-Auth` or `Authorization` header
2. Verify token is stored: Settings > Developer Options > View stored data
3. Check backend logs for token validation errors
4. Verify `AUTH_SESSION_SECRET` matches between deployments

### "Rate limited immediately"

1. Check `ASSIST_USER_RATE_LIMIT_PER_MINUTE` (default: 20)
2. Verify user ID is correct (check backend logs)
3. If using Redis, verify it's running and accessible
4. Check for duplicate requests (retry logic)

### "Migration failed"

1. Check database is accessible: `psql $DATABASE_URL`
2. Verify no conflicting tables exist
3. Run `npx prisma migrate reset` (DEV ONLY - deletes all data)
4. Or manually apply migration SQL from `prisma/migrations/`

---

## Success Criteria

✅ All 6 smoke tests pass with expected responses
✅ Android app successfully signs in via Google
✅ Assistant works with authenticated session
✅ Rate limit enforced after 20 requests per user per minute
✅ Clear error messages (no "You're offline" confusion)
✅ `correlationId` present in all error responses

---

## Next Steps After Smoke Test

1. **Monitor Logs**: Watch for auth errors in production
2. **Adjust Limits**: Tune `USER_RATE_LIMIT` based on usage patterns
3. **User Feedback**: Collect feedback on error messages
4. **Analytics**: Track auth success/failure rates
5. **Phase C Planning**: Token refresh, session management UI

---

## Quick Reference

### Default Rate Limits (Phase B)

| Limit Type | Default | Config Variable |
|------------|---------|-----------------|
| Per-user rate limit | 20/min | `ASSIST_USER_RATE_LIMIT_PER_MINUTE` |
| Per-user daily quota | 100/day | `ASSIST_USER_DAILY_QUOTA` |
| Per-IP rate limit | 60/min | `ASSIST_IP_RATE_LIMIT_PER_MINUTE` |
| Per-device rate limit | 30/min | `ASSIST_DEVICE_RATE_LIMIT_PER_MINUTE` |

### Error Codes (Phase B)

| Code | HTTP Status | Meaning | Retryable |
|------|-------------|---------|-----------|
| `AUTH_REQUIRED` | 401 | No auth provided | No (sign in first) |
| `AUTH_INVALID` | 401 | Token expired/invalid | No (sign in again) |
| `RATE_LIMITED` | 429 | Rate limit exceeded | Yes (wait for `resetAt`) |

---

**Phase B Implementation Status**: ✅ Complete
**Deployment Status**: ⚠️ Pending deployment to production
**Testing Status**: 🧪 Awaiting smoke test after deployment
