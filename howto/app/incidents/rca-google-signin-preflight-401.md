# Root Cause Analysis: Google Sign-In DEVELOPER_ERROR & Preflight 401

**Date:** 2026-01-25
**Severity:** Critical
**Status:** Identified - Requires Fix
**Affected Components:** Android Authentication, Backend CORS, Google OAuth Configuration

---

## Executive Summary

Google Sign-In fails with `DEVELOPER_ERROR` across all build flavors (prod, dev, beta) and preflight health checks return `401 Unauthorized`. Analysis reveals two distinct but related configuration issues:

1. **Google OAuth Client IDs are placeholder values** in `build.gradle.kts` causing Google to reject authentication attempts
2. **CORS origins configuration** may be incomplete for production backend access

---

## Symptoms

### 1. Google Sign-In Failure
- **Error:** `DEVELOPER_ERROR` from Google Sign-In / One Tap
- **User Impact:** Cannot sign in with Google account
- **Affected Flavors:** All (prod, dev, beta)
- **Location:** Settings → General → Sign in with Google

### 2. Preflight Failure
- **Error:** HTTP 401 Unauthorized
- **User Impact:** Assistant preflight check fails
- **Endpoint:** `/v1/assist/chat` (used by preflight)
- **Related:** May indicate broader authentication/CORS issues

---

## Root Cause Analysis

### Issue 1: Invalid Google OAuth Client IDs

**File:** `androidApp/build.gradle.kts` (lines 256-310)

**Current Configuration:**
```kotlin
// Production flavor (line 256-261)
buildConfigField(
    "String",
    "GOOGLE_SERVER_CLIENT_ID",
    "\"REDACTED_GOOGLE_OAUTH_CLIENT_ID\"",  // ❌ PLACEHOLDER VALUE
)

// Dev flavor (line 280-285)
buildConfigField(
    "String",
    "GOOGLE_SERVER_CLIENT_ID",
    "\"REDACTED_GOOGLE_OAUTH_CLIENT_ID\"",  // ❌ PLACEHOLDER VALUE
)

// Beta flavor (line 304-309)
buildConfigField(
    "String",
    "GOOGLE_SERVER_CLIENT_ID",
    "\"REDACTED_GOOGLE_OAUTH_CLIENT_ID\"",  // ❌ PLACEHOLDER VALUE
)
```

**Why This Causes DEVELOPER_ERROR:**

The Android Credential Manager API (`CredentialManagerAuthLauncher.kt:31`) uses this Client ID to request a Google ID token:

```kotlin
val googleIdOption =
    GetGoogleIdOption
        .Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(BuildConfig.GOOGLE_SERVER_CLIENT_ID)  // ← Using placeholder
        .build()
```

When Google receives a request with:
- Package name: `com.scanium.app.dev` / `com.scanium.app` / `com.scanium.app.beta`
- SHA-1 fingerprint: `03:C5:1E:2F:FA:EA:B6:F9:AA:F9:C6:25:D4:14:08:14:57:C1:FC:A2`
- Client ID: `"REDACTED_GOOGLE_OAUTH_CLIENT_ID"` (invalid placeholder)

Google cannot find a matching OAuth client configuration and returns `DEVELOPER_ERROR`.

**Expected Configuration:**

Each flavor should have a **Web Application** OAuth Client ID from Google Cloud Console:

```kotlin
// Example (DO NOT USE THESE VALUES - create your own)
buildConfigField(
    "String",
    "GOOGLE_SERVER_CLIENT_ID",
    "\"480326569434-example123.apps.googleusercontent.com\""
)
```

**Backend Verification:**

The backend is correctly configured:
- File: `backend/.env`
- Setting: `GOOGLE_OAUTH_CLIENT_ID=480326569434-nhp9a4ups5sb3i5ualtuc67h2865qhdo.apps.googleusercontent.com`
- This is a valid Client ID but it must match what's sent from Android

---

### Issue 2: CORS Configuration

**File:** `backend/.env` (line 196)

**Current Configuration:**
```env
CORS_ORIGINS=scanium://,http://localhost:3000
```

**Analysis:**

The CORS plugin (`backend/src/infra/http/plugins/cors.ts`) implements origin validation:

```typescript
origin(origin, callback) {
  if (!origin) {
    return callback(null, true);  // ✅ Native apps have no origin
  }

  if (allowedOrigins.includes(origin)) {
    return callback(null, origin);  // ✅ Explicit allowlist
  }

  fastify.log.warn({ origin }, 'CORS origin rejected');
  return callback(null, false);  // ❌ Reject unknown origins
}
```

**For Native Android Apps:**

Android native HTTP clients (OkHttp) **do not send an `Origin` header** in most cases. When `origin` is undefined/null, the CORS plugin allows the request.

**However:**

1. If Android WebView or any web-based component makes requests, it may send an origin
2. The preflight 401 error is likely **NOT CORS-related** but rather:
   - Missing/invalid API key (`X-API-Key` header)
   - Missing/invalid authentication token (`Authorization: Bearer` header)
   - The auth middleware rejecting requests

**Preflight Request Flow:**

```
Android App → OkHttp GET /v1/assist/chat
  Headers:
    X-API-Key: <from SecureApiKeyStore>
    Authorization: Bearer <session token>  ← May be missing/invalid
    X-Scanium-Device-Id: <device ID>

Backend → Auth Middleware (onRequest hook)
  - Checks Authorization header
  - Calls verifySession(token)
  - Sets request.userId if valid

Backend → Route Handler
  - May call requireAuth(request)
  - Throws AuthRequiredError (401) if no userId
```

**Likely Cause of 401:**

The preflight endpoint (`/v1/assist/chat`) is being called but:
- User is not signed in → no session token
- Or session token is expired/invalid
- Backend auth middleware correctly returns 401

This is **correct behavior** - the preflight fails when not authenticated.

---

## Evidence

### 1. Build Configuration
- **File:** `androidApp/build.gradle.kts:260,284,308`
- **Status:** Contains placeholder `"REDACTED_GOOGLE_OAUTH_CLIENT_ID"`
- **Impact:** All flavors fail Google Sign-In

### 2. Debug Keystore Verification
```bash
$ keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey
SHA1: 03:C5:1E:2F:FA:EA:B6:F9:AA:F9:C6:25:D4:14:08:14:57:C1:FC:A2
```
✅ Matches expected SHA-1 in `setup-google-oauth.sh:11`

### 3. Backend Configuration
```bash
$ grep GOOGLE_OAUTH_CLIENT_ID backend/.env
GOOGLE_OAUTH_CLIENT_ID=480326569434-nhp9a4ups5sb3i5ualtuc67h2865qhdo.apps.googleusercontent.com
```
✅ Backend has valid Client ID

### 4. Backend Running
```bash
$ ps aux | grep tsx
family  60088  node backend/node_modules/.bin/tsx watch src/main.ts
```
✅ Backend is running locally

### 5. CORS Origins
```bash
$ grep CORS_ORIGINS backend/.env
CORS_ORIGINS=scanium://,http://localhost:3000
```
⚠️ Limited to app scheme and localhost (native apps don't need `https://scanium.gtemp1.com`)

---

## Impact Assessment

### Google Sign-In (Critical)
- **Severity:** P0 - Blocks user authentication
- **Scope:** All users, all flavors
- **Workaround:** None
- **User-Facing:** Users cannot sign in with Google

### Preflight 401 (Medium)
- **Severity:** P2 - Expected behavior when not signed in
- **Scope:** Assistant feature only
- **Workaround:** Users can still use app without assistant
- **User-Facing:** Assistant may show "unavailable" status

---

## Fix Recommendations

### Fix 1: Configure Google OAuth Client IDs (REQUIRED)

#### Step 1: Create OAuth Clients in Google Cloud Console

For each flavor, create a **Web Application** OAuth 2.0 Client:

1. Go to [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
2. Select your Scanium project
3. Click **Create Credentials** → **OAuth 2.0 Client ID**
4. Select **Web application**
5. Configure for each flavor:

**Production (com.scanium.app):**
- Name: `Scanium Production Web Client`
- Authorized JavaScript origins: `https://scanium.gtemp1.com`
- Create and copy Client ID

**Dev (com.scanium.app.dev):**
- Name: `Scanium Dev Web Client`
- Authorized JavaScript origins: `http://localhost:3000`, `https://scanium.gtemp1.com`
- Create and copy Client ID

**Beta (com.scanium.app.beta):**
- Name: `Scanium Beta Web Client`
- Authorized JavaScript origins: `https://scanium.gtemp1.com`
- Create and copy Client ID

#### Step 2: Update Android Build Configuration

Edit `androidApp/build.gradle.kts`:

```kotlin
productFlavors {
    create("prod") {
        // ... existing config ...
        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            "\"<PROD_CLIENT_ID>.apps.googleusercontent.com\""
        )
    }

    create("dev") {
        // ... existing config ...
        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            "\"<DEV_CLIENT_ID>.apps.googleusercontent.com\""
        )
    }

    create("beta") {
        // ... existing config ...
        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            "\"<BETA_CLIENT_ID>.apps.googleusercontent.com\""
        )
    }
}
```

**Important:** Replace `<PROD_CLIENT_ID>`, `<DEV_CLIENT_ID>`, `<BETA_CLIENT_ID>` with actual Client IDs from Step 1.

#### Step 3: Update Backend Configuration

The backend must recognize tokens issued by any of the three Android clients. Two options:

**Option A: Single Shared Client (Simpler)**

Use the **production** Client ID in backend and ensure all three OAuth clients have the same authorized origins:

```env
# backend/.env
GOOGLE_OAUTH_CLIENT_ID=<PROD_CLIENT_ID>.apps.googleusercontent.com
```

**Option B: Environment-Specific (Advanced)**

Deploy separate backends for dev/beta/prod with matching Client IDs. Not recommended unless you already have separate backend deployments.

#### Step 4: Rebuild and Test

```bash
# Clean build
./gradlew clean

# Build dev flavor
./gradlew :androidApp:assembleDevDebug

# Install and test
adb install androidApp/build/outputs/apk/dev/debug/androidApp-dev-debug.apk

# Test sign-in
# 1. Open app
# 2. Go to Settings → General
# 3. Tap "Sign in with Google"
# 4. Expected: Google account picker appears
# 5. Select account
# 6. Expected: Sign-in succeeds
```

---

### Fix 2: CORS Origins (OPTIONAL)

The current CORS configuration is correct for native Android apps. However, if you plan to support web-based components or need to allowlist the backend's own origin:

```env
# backend/.env
CORS_ORIGINS=scanium://,http://localhost:3000,https://scanium.gtemp1.com
```

**Note:** This is **NOT required** to fix the current issues and should only be added if you have web-based components making requests to the backend.

---

### Fix 3: Preflight 401 Context

The preflight 401 is **expected behavior** when the user is not signed in. The fix for Google Sign-In (Fix 1) will indirectly resolve preflight failures for signed-in users:

1. User signs in with Google → receives session token
2. Session token stored in `SecureApiKeyStore`
3. Preflight requests include `Authorization: Bearer <token>` header
4. Backend validates token and allows request
5. Preflight returns 200 OK

No separate fix needed for preflight - it's authentication-dependent.

---

## Testing Plan

### 1. Google Sign-In Verification

**For each flavor (prod, dev, beta):**

```bash
# Build
./gradlew :androidApp:assemble${Flavor}Debug

# Install
adb install androidApp/build/outputs/apk/${flavor}/debug/androidApp-${flavor}-debug.apk

# Test
1. Open app
2. Settings → General → Sign in with Google
3. ✓ Google account picker appears (no DEVELOPER_ERROR)
4. Select account
5. ✓ Sign-in succeeds
6. ✓ User email/name displayed
7. ✓ Backend logs show successful token verification
```

**Success Criteria:**
- ✅ No `DEVELOPER_ERROR` in logcat
- ✅ Google account picker appears
- ✅ Sign-in completes successfully
- ✅ User profile displayed in Settings
- ✅ Backend logs: `"event": "auth_login_success"`

### 2. Backend Token Verification

```bash
# Check backend logs for successful auth
cd backend
npm run logs | grep -i "auth_login_success"

# Expected output:
# {"event":"auth_login_success","userId":"...","email":"...","isNewUser":false}
```

### 3. Preflight Health Check

**After signing in:**

```bash
# Check preflight status in Developer Options (dev flavor only)
1. Sign in to Google
2. Developer Options → Diagnostics → Assistant Diagnostics
3. Preflight Status: ✓ AVAILABLE (not UNAUTHORIZED)
```

**Logcat verification:**

```bash
adb logcat -s AssistantPreflight:I

# Expected after sign-in:
# AssistantPreflight: Preflight: AVAILABLE latency=XXXms
```

### 4. End-to-End Flow

```bash
1. Fresh install (uninstall first)
2. Open app
3. Grant camera permissions
4. Scan an object
5. Go to Settings → Sign in with Google
6. ✓ Sign-in succeeds
7. Return to Items screen
8. Open item → Tap "Get selling advice"
9. ✓ Assistant loads (preflight succeeds)
10. Type a message
11. ✓ Chat succeeds (authenticated request)
```

---

## Prevention

### 1. Build-Time Validation

Add a Gradle task to validate OAuth Client IDs are not placeholders:

```kotlin
// androidApp/build.gradle.kts
tasks.register("validateOAuthConfig") {
    doLast {
        val clientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID
        if (clientId.contains("REDACTED") || clientId.contains("YOUR_")) {
            throw GradleException(
                "Google OAuth Client ID is not configured. " +
                "See howto/app/reference/GOOGLE_SIGNIN_CONFIG.md"
            )
        }
    }
}

tasks.matching { it.name.contains("assemble") }.configureEach {
    dependsOn("validateOAuthConfig")
}
```

### 2. Pre-Commit Hook

Add to `.git/hooks/pre-commit`:

```bash
#!/bin/bash
# Prevent committing real OAuth Client IDs
if git diff --cached | grep -E '[0-9]+-[a-z0-9]+\.apps\.googleusercontent\.com'; then
    echo "❌ Error: OAuth Client ID detected in commit"
    echo "Replace with placeholder before committing"
    exit 1
fi
```

### 3. Documentation Update

**Update:** `howto/app/reference/GOOGLE_SIGNIN_CONFIG.md`
- Add troubleshooting section for DEVELOPER_ERROR
- Add checklist for pre-release OAuth verification
- Add link to this RCA

### 4. CI/CD Check

Add to GitHub Actions workflow:

```yaml
- name: Validate OAuth Configuration
  run: |
    if grep -r "REDACTED_GOOGLE_OAUTH_CLIENT_ID" androidApp/build.gradle.kts; then
      echo "::warning::OAuth Client IDs are placeholders. Configure real IDs for release builds."
    fi
```

---

## Related Documentation

- **Google Sign-In Setup:** `howto/GOOGLE_OAUTH_SETUP.md`
- **Flavor Configuration:** `howto/app/reference/GOOGLE_SIGNIN_CONFIG.md`
- **OAuth Production Readiness:** `howto/project/oauth-production-readiness.md`
- **Backend Auth:** `backend/src/modules/auth/google/README.test.md`
- **Preflight Implementation:** `howto/backend/reference/assistant/PREFLIGHT_IMPLEMENTATION.md`

---

## Timeline

| Time | Event |
|------|-------|
| Unknown | OAuth Client IDs replaced with `REDACTED_GOOGLE_OAUTH_CLIENT_ID` placeholder |
| 2026-01-25 | User reports Google Sign-In failing with DEVELOPER_ERROR |
| 2026-01-25 | RCA completed, root cause identified |
| Pending | Fix implementation |
| Pending | Testing and verification |
| Pending | Deployment |

---

## Sign-Off

**Prepared by:** Claude Code
**Date:** 2026-01-25
**Next Actions:**
1. [ ] Create Google OAuth clients in Google Cloud Console
2. [ ] Update `androidApp/build.gradle.kts` with real Client IDs
3. [ ] Rebuild all flavors
4. [ ] Test sign-in on all flavors
5. [ ] Verify backend token validation
6. [ ] Update documentation
7. [ ] Add build-time validation
