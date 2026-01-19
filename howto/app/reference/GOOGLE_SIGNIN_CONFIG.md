***REMOVED*** Google Sign-In Configuration

This document provides comprehensive instructions for configuring Google Sign-In across all Scanium build flavors (dev, beta, prod) and redeploying the application with proper OAuth credentials.

***REMOVED******REMOVED*** Overview

Scanium uses Google Sign-In via Android Credential Manager for user authentication. Due to different package names across build flavors, each flavor requires separate OAuth client registration in Google Cloud Console.

**Package Names:**
- `com.scanium.app` (prod)
- `com.scanium.app.dev` (dev)
- `com.scanium.app.beta` (beta)

***REMOVED******REMOVED*** Problem Statement

**Symptom:** Google Sign-In works in dev flavor but fails in beta/prod with "credentials not available" error.

**Root Cause:** The Android app uses a single hardcoded Google Server Client ID for all flavors, but Google OAuth requires separate client registrations for each package name. When a flavor attempts sign-in with an unregistered package name, Google rejects the credential request.

**Files Affected:**
- `androidApp/src/main/java/com/scanium/app/auth/CredentialManagerAuthLauncher.kt:63` - Hardcoded Client ID
- `backend/src/config/index.ts:447` - Backend OAuth configuration
- `backend/src/modules/auth/google/token-verifier.ts:25` - Token verification

***REMOVED******REMOVED*** Authentication Flow

***REMOVED******REMOVED******REMOVED*** High-Level Flow

```
User Taps Sign In
    ↓
Android Credential Manager (GetGoogleIdOption)
    ↓ (with serverClientId)
Google Sign-In UI
    ↓ (user authenticates)
Google Issues ID Token (audience = serverClientId)
    ↓
Android sends ID token to backend /v1/auth/google
    ↓
Backend verifies token with Google OAuth2Client
    ↓ (audience must match)
Session created, user authenticated
```

***REMOVED******REMOVED******REMOVED*** Code Components

1. **Android Client** (`CredentialManagerAuthLauncher.kt`)
   - Initiates sign-in with `GetGoogleIdOption`
   - Passes `GOOGLE_SERVER_CLIENT_ID` to identify backend
   - Receives ID token from Google

2. **Backend Verification** (`token-verifier.ts`)
   - Verifies ID token with `OAuth2Client.verifyIdToken()`
   - Checks `audience` matches `GOOGLE_OAUTH_CLIENT_ID`
   - Extracts user email, name, profile picture

3. **Backend Configuration** (`config/index.ts`)
   - Reads `GOOGLE_OAUTH_CLIENT_ID` from environment
   - Used across all auth flows

***REMOVED******REMOVED*** Google Cloud Console Setup

***REMOVED******REMOVED******REMOVED*** Step 1: Access Google Cloud Console

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your Scanium project (or create one if needed)
3. Navigate to **APIs & Services** > **Credentials**

***REMOVED******REMOVED******REMOVED*** Step 2: Create OAuth Client IDs for Each Flavor

You need to create **3 separate Web application OAuth clients** (one for each flavor).

***REMOVED******REMOVED******REMOVED******REMOVED*** Create OAuth Client for PROD (`com.scanium.app`)

1. Click **+ CREATE CREDENTIALS** > **OAuth client ID**
2. Select **Application type**: **Web application**
3. **Name**: `Scanium Production Web Client`
4. **Authorized JavaScript origins**: Add your production backend URL
   ```
   https://scanium.gtemp1.com
   ```
5. **Authorized redirect URIs**: (if needed for web flows)
   ```
   https://scanium.gtemp1.com/auth/google/callback
   ```
6. Click **CREATE**
7. **Copy the Client ID** - you'll need this for configuration
   - Format: `XXXXXXXXXX-YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY.apps.googleusercontent.com`
8. Store this as `GOOGLE_OAUTH_CLIENT_ID_PROD`

***REMOVED******REMOVED******REMOVED******REMOVED*** Create OAuth Client for BETA (`com.scanium.app.beta`)

1. Repeat the same process with:
   - **Name**: `Scanium Beta Web Client`
   - **Origins**: Same backend URL (or separate beta backend if you have one)
   - **Redirect URIs**: Same as prod (or beta-specific)
2. **Copy the Client ID** and store as `GOOGLE_OAUTH_CLIENT_ID_BETA`

***REMOVED******REMOVED******REMOVED******REMOVED*** Create OAuth Client for DEV (`com.scanium.app.dev`)

1. Repeat the process with:
   - **Name**: `Scanium Dev Web Client`
   - **Origins**: Your local/dev backend URL
     ```
     http://localhost:3000
     https://scanium.gtemp1.com
     ```
   - **Redirect URIs**: Local and remote dev endpoints
2. **Copy the Client ID** and store as `GOOGLE_OAUTH_CLIENT_ID_DEV`

***REMOVED******REMOVED******REMOVED*** Step 3: Enable Google+ API

1. In Google Cloud Console, go to **APIs & Services** > **Library**
2. Search for **Google+ API**
3. Click **ENABLE** if not already enabled

***REMOVED******REMOVED******REMOVED*** Step 4: Configure OAuth Consent Screen

1. Go to **APIs & Services** > **OAuth consent screen**
2. Select **External** user type (or Internal if G Workspace)
3. Fill in required fields:
   - **App name**: Scanium
   - **User support email**: Your email
   - **Developer contact**: Your email
4. Add scopes:
   - `.../auth/userinfo.email`
   - `.../auth/userinfo.profile`
   - `openid`
5. Save and continue
6. Add test users if app is in Testing mode

***REMOVED******REMOVED*** Android Configuration

***REMOVED******REMOVED******REMOVED*** Option 1: BuildConfig Fields (Recommended)

This approach uses flavor-specific BuildConfig fields to inject the correct Client ID at build time.

***REMOVED******REMOVED******REMOVED******REMOVED*** 1. Update `androidApp/build.gradle.kts`

Add a new BuildConfig field for each flavor:

```kotlin
productFlavors {
    create("prod") {
        dimension = "distribution"
        buildConfigField("boolean", "DEV_MODE_ENABLED", "false")
        resValue("string", "app_name", "Scanium")

        // Feature flags
        buildConfigField("boolean", "FEATURE_DEV_MODE", "false")
        buildConfigField("boolean", "FEATURE_SCREENSHOTS", "true")
        buildConfigField("boolean", "FEATURE_AI_ASSISTANT", "true")
        buildConfigField("String", "MAX_IMAGE_RESOLUTION", "\"HIGH\"")
        buildConfigField("boolean", "FEATURE_ITEM_DIAGNOSTICS", "true")

        // OAuth Client ID for prod flavor
        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            "\"PROD_CLIENT_ID_HERE.apps.googleusercontent.com\""
        )
    }

    create("dev") {
        dimension = "distribution"
        applicationIdSuffix = ".dev"
        versionNameSuffix = "-dev"
        buildConfigField("boolean", "DEV_MODE_ENABLED", "true")
        resValue("string", "app_name", "Scanium Dev")

        // Feature flags
        buildConfigField("boolean", "FEATURE_DEV_MODE", "true")
        buildConfigField("boolean", "FEATURE_SCREENSHOTS", "true")
        buildConfigField("boolean", "FEATURE_AI_ASSISTANT", "true")
        buildConfigField("String", "MAX_IMAGE_RESOLUTION", "\"HIGH\"")
        buildConfigField("boolean", "FEATURE_ITEM_DIAGNOSTICS", "true")

        // OAuth Client ID for dev flavor
        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            "\"DEV_CLIENT_ID_HERE.apps.googleusercontent.com\""
        )
    }

    create("beta") {
        dimension = "distribution"
        applicationIdSuffix = ".beta"
        versionNameSuffix = "-beta"
        buildConfigField("boolean", "DEV_MODE_ENABLED", "false")
        resValue("string", "app_name", "Scanium Beta")

        // Feature flags
        buildConfigField("boolean", "FEATURE_DEV_MODE", "false")
        buildConfigField("boolean", "FEATURE_SCREENSHOTS", "true")
        buildConfigField("boolean", "FEATURE_AI_ASSISTANT", "true")
        buildConfigField("String", "MAX_IMAGE_RESOLUTION", "\"HIGH\"")
        buildConfigField("boolean", "FEATURE_ITEM_DIAGNOSTICS", "true")

        // OAuth Client ID for beta flavor
        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            "\"BETA_CLIENT_ID_HERE.apps.googleusercontent.com\""
        )
    }
}
```

***REMOVED******REMOVED******REMOVED******REMOVED*** 2. Update `CredentialManagerAuthLauncher.kt`

Replace the hardcoded constant with the BuildConfig value:

**Before:**
```kotlin
companion object {
    private const val TAG = "CredentialManagerAuthLauncher"

    // This must match GOOGLE_OAUTH_CLIENT_ID in backend .env
    private const val GOOGLE_SERVER_CLIENT_ID = "480326569434-9cje4dkffu16ol5126q7pt6oihshtn5k.apps.googleusercontent.com"
}
```

**After:**
```kotlin
companion object {
    private const val TAG = "CredentialManagerAuthLauncher"

    // Flavor-specific OAuth Client ID injected at build time
    // Must match GOOGLE_OAUTH_CLIENT_ID in backend .env for respective environment
    private val GOOGLE_SERVER_CLIENT_ID = BuildConfig.GOOGLE_SERVER_CLIENT_ID
}
```

**File location:** `androidApp/src/main/java/com/scanium/app/auth/CredentialManagerAuthLauncher.kt:59-64`

***REMOVED******REMOVED******REMOVED*** Option 2: Flavor-Specific Source Sets (Alternative)

If you prefer not to expose Client IDs in BuildConfig, you can create flavor-specific constant files.

***REMOVED******REMOVED******REMOVED******REMOVED*** 1. Create flavor directories

```bash
mkdir -p androidApp/src/prod/java/com/scanium/app/auth
mkdir -p androidApp/src/dev/java/com/scanium/app/auth
mkdir -p androidApp/src/beta/java/com/scanium/app/auth
```

***REMOVED******REMOVED******REMOVED******REMOVED*** 2. Create `AuthConfig.kt` for each flavor

**`androidApp/src/prod/java/com/scanium/app/auth/AuthConfig.kt`:**
```kotlin
package com.scanium.app.auth

internal object AuthConfig {
    const val GOOGLE_SERVER_CLIENT_ID = "PROD_CLIENT_ID_HERE.apps.googleusercontent.com"
}
```

**`androidApp/src/dev/java/com/scanium/app/auth/AuthConfig.kt`:**
```kotlin
package com.scanium.app.auth

internal object AuthConfig {
    const val GOOGLE_SERVER_CLIENT_ID = "DEV_CLIENT_ID_HERE.apps.googleusercontent.com"
}
```

**`androidApp/src/beta/java/com/scanium/app/auth/AuthConfig.kt`:**
```kotlin
package com.scanium.app.auth

internal object AuthConfig {
    const val GOOGLE_SERVER_CLIENT_ID = "BETA_CLIENT_ID_HERE.apps.googleusercontent.com"
}
```

***REMOVED******REMOVED******REMOVED******REMOVED*** 3. Update `CredentialManagerAuthLauncher.kt`

```kotlin
companion object {
    private const val TAG = "CredentialManagerAuthLauncher"

    // Flavor-specific OAuth Client ID from AuthConfig
    private val GOOGLE_SERVER_CLIENT_ID = AuthConfig.GOOGLE_SERVER_CLIENT_ID
}
```

***REMOVED******REMOVED*** Backend Configuration

The backend needs to know which OAuth Client ID to use for token verification. Since the backend typically serves all flavors, you have two options:

***REMOVED******REMOVED******REMOVED*** Option 1: Single Backend for All Flavors (Simpler)

Use the **production Client ID** in backend configuration. Configure all three Google OAuth clients (prod, dev, beta) to authorize the same backend origin.

**`.env` configuration:**
```bash
GOOGLE_OAUTH_CLIENT_ID=PROD_CLIENT_ID_HERE.apps.googleusercontent.com
```

**Important:** In Google Cloud Console, ensure **all three OAuth clients** have:
- **Authorized JavaScript origins**: `https://scanium.gtemp1.com`
- **Authorized redirect URIs**: `https://scanium.gtemp1.com/auth/google/callback`

This way, tokens issued by any flavor can be verified by the backend.

***REMOVED******REMOVED******REMOVED*** Option 2: Environment-Specific Backends (Advanced)

If you have separate backend deployments for dev/beta/prod:

**Dev backend `.env`:**
```bash
GOOGLE_OAUTH_CLIENT_ID=DEV_CLIENT_ID_HERE.apps.googleusercontent.com
```

**Beta backend `.env`:**
```bash
GOOGLE_OAUTH_CLIENT_ID=BETA_CLIENT_ID_HERE.apps.googleusercontent.com
```

**Prod backend `.env`:**
```bash
GOOGLE_OAUTH_CLIENT_ID=PROD_CLIENT_ID_HERE.apps.googleusercontent.com
```

Update Android flavors to point to respective backend URLs in `local.properties`:
```properties
***REMOVED*** Dev flavor
scanium.api.base.url.debug=http://localhost:3000
scanium.api.base.url.release=https://dev.scanium.gtemp1.com

***REMOVED*** Beta flavor
scanium.api.base.url.beta.debug=https://beta.scanium.gtemp1.com
scanium.api.base.url.beta.release=https://beta.scanium.gtemp1.com

***REMOVED*** Prod flavor
scanium.api.base.url.prod.debug=https://scanium.gtemp1.com
scanium.api.base.url.prod.release=https://scanium.gtemp1.com
```

***REMOVED******REMOVED*** Build and Deployment

***REMOVED******REMOVED******REMOVED*** 1. Configure Credentials

Update `androidApp/build.gradle.kts` with the three Client IDs you obtained from Google Cloud Console.

**Example:**
```kotlin
// In prod flavor:
buildConfigField(
    "String",
    "GOOGLE_SERVER_CLIENT_ID",
    "\"123456789-abc123def456ghi789jkl012mno345pq.apps.googleusercontent.com\""
)

// In dev flavor:
buildConfigField(
    "String",
    "GOOGLE_SERVER_CLIENT_ID",
    "\"987654321-xyz987wvu654tsr321pqo098nml876ihg.apps.googleusercontent.com\""
)

// In beta flavor:
buildConfigField(
    "String",
    "GOOGLE_SERVER_CLIENT_ID",
    "\"555666777-aaa111bbb222ccc333ddd444eee555fff.apps.googleusercontent.com\""
)
```

***REMOVED******REMOVED******REMOVED*** 2. Update Backend Configuration

Update `backend/.env` with the appropriate Client ID:

```bash
***REMOVED*** If using single backend for all flavors (Option 1):
GOOGLE_OAUTH_CLIENT_ID=123456789-abc123def456ghi789jkl012mno345pq.apps.googleusercontent.com

***REMOVED*** Ensure all other auth variables are set:
AUTH_SESSION_SECRET=your-session-secret-here
AUTH_SESSION_EXPIRY_SECONDS=86400
AUTH_REFRESH_TOKEN_EXPIRY_SECONDS=2592000
SESSION_SIGNING_SECRET=your-signing-secret-here
```

***REMOVED******REMOVED******REMOVED*** 3. Build Android App

```bash
***REMOVED*** Build dev flavor
./gradlew :androidApp:assembleDevDebug

***REMOVED*** Build beta flavor
./gradlew :androidApp:assembleBetaDebug

***REMOVED*** Build prod flavor (release)
./gradlew :androidApp:assembleProdRelease
```

***REMOVED******REMOVED******REMOVED*** 4. Deploy Backend

```bash
cd backend
npm run build
npm run start
```

Or using the dev script:
```bash
scripts/backend/start-dev.sh
```

***REMOVED******REMOVED******REMOVED*** 5. Install and Test

```bash
***REMOVED*** Install dev build
adb install androidApp/build/outputs/apk/dev/debug/androidApp-dev-debug.apk

***REMOVED*** Install beta build
adb install androidApp/build/outputs/apk/beta/debug/androidApp-beta-debug.apk

***REMOVED*** Install prod build
adb install androidApp/build/outputs/apk/prod/release/androidApp-prod-release.apk
```

***REMOVED******REMOVED*** Testing and Validation

***REMOVED******REMOVED******REMOVED*** Manual Testing Checklist

For **each flavor** (dev, beta, prod):

1. **Launch app**
   - Verify correct app name appears (Scanium / Scanium Dev / Scanium Beta)
   - Verify correct package name in Settings > About

2. **Initiate Google Sign-In**
   - Tap "Sign in with Google" button
   - Google account picker should appear
   - Select your Google account

3. **Verify Sign-In Success**
   - Should see "Signed in as [email]" or similar success indicator
   - User should be navigated to main app screen
   - Profile picture should load (if implemented)

4. **Check Session Persistence**
   - Close app completely
   - Reopen app
   - Should remain signed in

5. **Test Sign-Out**
   - Navigate to Settings
   - Tap "Sign Out"
   - Should return to sign-in screen

***REMOVED******REMOVED******REMOVED*** Backend Verification

Check backend logs for successful token verification:

```bash
***REMOVED*** View backend logs
cd backend
npm run logs

***REMOVED*** Look for:
***REMOVED*** - "Google ID token verified successfully"
***REMOVED*** - "User authenticated: [email]"
```

***REMOVED******REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED******REMOVED*** Error: "Credentials not available"

**Cause:** OAuth Client ID not registered for the flavor's package name.

**Solution:**
1. Verify you created an OAuth client in Google Cloud Console
2. Verify the Client ID in `build.gradle.kts` matches the one from Google Cloud Console
3. Verify the OAuth client has correct authorized origins
4. Clean and rebuild:
   ```bash
   ./gradlew clean
   ./gradlew :androidApp:assembleDevDebug
   ```

***REMOVED******REMOVED******REMOVED******REMOVED*** Error: "Invalid ID token"

**Cause:** Backend Client ID doesn't match the Android Client ID.

**Solution:**
1. Verify `GOOGLE_OAUTH_CLIENT_ID` in backend `.env` matches the Client ID used in Android flavor
2. Restart backend after changing `.env`:
   ```bash
   scripts/backend/stop-dev.sh
   scripts/backend/start-dev.sh
   ```

***REMOVED******REMOVED******REMOVED******REMOVED*** Error: "Audience mismatch"

**Cause:** Token was issued for a different Client ID than backend expects.

**Solution:**
1. Ensure `serverClientId` in Android matches `GOOGLE_OAUTH_CLIENT_ID` in backend
2. Check that you didn't mix up dev/beta/prod Client IDs
3. Verify OAuth client authorized origins include your backend URL

***REMOVED******REMOVED******REMOVED******REMOVED*** Sign-In Works in Dev but Not Beta/Prod

**Cause:** You only registered a Client ID for dev flavor.

**Solution:**
1. Create separate OAuth clients for beta and prod in Google Cloud Console
2. Update `build.gradle.kts` with the new Client IDs
3. Rebuild affected flavors

***REMOVED******REMOVED******REMOVED******REMOVED*** Error: "API not enabled"

**Cause:** Google+ API not enabled in Google Cloud Console.

**Solution:**
1. Go to Google Cloud Console > APIs & Services > Library
2. Search "Google+ API"
3. Click ENABLE

***REMOVED******REMOVED*** Security Considerations

***REMOVED******REMOVED******REMOVED*** Client ID Storage

**BuildConfig Approach:**
- Client IDs are **not secrets** - they're meant to identify your app to Google
- Safe to commit to version control
- BuildConfig values are visible in decompiled APK

**Flavor Source Sets Approach:**
- Keeps Client IDs out of build.gradle
- Still visible in decompiled APK
- Slightly cleaner separation

**Recommendation:** Use BuildConfig approach for simplicity. Client IDs are not sensitive.

***REMOVED******REMOVED******REMOVED*** Backend Secret Management

**Critical:** Never commit backend secrets to version control.

Use environment variables for:
- `GOOGLE_OAUTH_CLIENT_ID` - Safe to commit (not a secret)
- `AUTH_SESSION_SECRET` - **Secret, never commit**
- `SESSION_SIGNING_SECRET` - **Secret, never commit**

Store secrets in:
- `.env` file (gitignored)
- Secret management service (AWS Secrets Manager, Google Secret Manager)
- Environment variables in deployment platform

***REMOVED******REMOVED******REMOVED*** Token Security

Google ID tokens contain:
- User email
- User name
- Profile picture URL
- Token expiration
- Issuer (Google)
- Audience (your Client ID)

**Backend must verify:**
1. Token signature (Google signed it)
2. Token expiration (not expired)
3. Audience matches your Client ID
4. Issuer is `accounts.google.com`

This is handled by `OAuth2Client.verifyIdToken()` in `token-verifier.ts`.

***REMOVED******REMOVED*** File Reference

***REMOVED******REMOVED******REMOVED*** Android Files Modified

- `androidApp/build.gradle.kts` - Add `GOOGLE_SERVER_CLIENT_ID` BuildConfig field per flavor
- `androidApp/src/main/java/com/scanium/app/auth/CredentialManagerAuthLauncher.kt` - Use BuildConfig value

***REMOVED******REMOVED******REMOVED*** Backend Files (No Changes Needed)

- `backend/src/config/index.ts` - Already reads `GOOGLE_OAUTH_CLIENT_ID`
- `backend/src/modules/auth/google/token-verifier.ts` - Already verifies tokens correctly
- `backend/src/modules/auth/google/routes.ts` - Already handles `/v1/auth/google` endpoint
- `backend/.env` - Update `GOOGLE_OAUTH_CLIENT_ID` value only

***REMOVED******REMOVED*** Additional Resources

- [Google Sign-In for Android](https://developers.google.com/identity/sign-in/android/start)
- [Credential Manager API](https://developer.android.com/training/sign-in/credential-manager)
- [Google OAuth 2.0 Documentation](https://developers.google.com/identity/protocols/oauth2)
- [Scanium FLAVOR_GATING.md](./FLAVOR_GATING.md) - Build flavor architecture
- [Scanium Backend README](../../infra/README.md) - Backend deployment guide

***REMOVED******REMOVED*** Revision History

| Date       | Author  | Changes                                      |
|------------|---------|----------------------------------------------|
| 2026-01-19 | Claude  | Initial documentation for flavor-based OAuth |
