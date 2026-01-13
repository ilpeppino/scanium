***REMOVED*** OAuth Production Readiness - Scanium

**Last Updated:** 2026-01-13
**Purpose:** Ensure Google OAuth is correctly configured for production release to Google Play

---

***REMOVED******REMOVED*** Overview

Scanium uses Google OAuth 2.0 for user authentication. To release to production, you must ensure that OAuth clients are properly configured and not using test/development credentials.

---

***REMOVED******REMOVED*** Current OAuth Setup

***REMOVED******REMOVED******REMOVED*** Backend OAuth Client
- **Environment Variable:** `GOOGLE_OAUTH_CLIENT_ID`
- **Configuration File:** `backend/.env`
- **Used for:** Server-side token verification

***REMOVED******REMOVED******REMOVED*** Android OAuth Client
- **Hardcoded in:** `androidApp/src/main/java/com/scanium/app/auth/AuthRepository.kt`
- **Constant:** `GOOGLE_SERVER_CLIENT_ID`
- **Used for:** Credential Manager Google Sign-In

---

***REMOVED******REMOVED*** Pre-Release Checklist

***REMOVED******REMOVED******REMOVED*** 1. Create Production OAuth Clients (if not done)

***REMOVED******REMOVED******REMOVED******REMOVED*** Backend OAuth Client (Web Application)
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your project (or create new for production)
3. Navigate to **APIs & Services → Credentials**
4. Click **Create Credentials → OAuth 2.0 Client ID**
5. Select **Web application**
6. Configure:
   - **Name:** `Scanium Backend (Production)`
   - **Authorized JavaScript origins:** `https://scanium.gtemp1.com` (your backend URL)
   - **Authorized redirect URIs:** Not required for backend token verification
7. Save and copy the **Client ID**

***REMOVED******REMOVED******REMOVED******REMOVED*** Android OAuth Client
1. In the same Google Cloud project
2. Click **Create Credentials → OAuth 2.0 Client ID**
3. Select **Android**
4. Configure:
   - **Name:** `Scanium Android (Production)`
   - **Package name:** `com.scanium.app` (from `androidApp/build.gradle.kts`)
   - **SHA-1 certificate fingerprint:** Your production signing key SHA-1 (see below)
5. Save and copy the **Client ID**

---

***REMOVED******REMOVED*** Getting Production SHA-1 Fingerprint

***REMOVED******REMOVED******REMOVED*** If using Google Play App Signing (Recommended)
1. Go to [Play Console](https://play.google.com/console/)
2. Select your app
3. Navigate to **Release → Setup → App signing**
4. Copy the **SHA-1 certificate fingerprint** from **App signing key certificate**

***REMOVED******REMOVED******REMOVED*** If using your own signing key
```bash
keytool -list -v -keystore /path/to/your/production-keystore.jks -alias your-key-alias
```

---

***REMOVED******REMOVED*** Configuration

***REMOVED******REMOVED******REMOVED*** Backend Configuration
Update `backend/.env` or `deploy/nas/compose/.env` (for production deployment):

```env
***REMOVED*** Production OAuth Client ID (Web Application type)
GOOGLE_OAUTH_CLIENT_ID=YOUR_PRODUCTION_BACKEND_CLIENT_ID.apps.googleusercontent.com
```

***REMOVED******REMOVED******REMOVED*** Android Configuration
Update `androidApp/src/main/java/com/scanium/app/auth/AuthRepository.kt`:

```kotlin
companion object {
    // Production Android OAuth Client ID
    private const val GOOGLE_SERVER_CLIENT_ID = "YOUR_PRODUCTION_ANDROID_CLIENT_ID.apps.googleusercontent.com"
}
```

**⚠️ CRITICAL:** Do NOT commit this change with actual production credentials if repo is public. Consider using BuildConfig or environment-based configuration.

---

***REMOVED******REMOVED*** Verification

***REMOVED******REMOVED******REMOVED*** 1. Backend Verification
Test token exchange in production:
```bash
curl -X POST https://scanium.gtemp1.com/v1/auth/google \
  -H "Content-Type: application/json" \
  -d '{"idToken": "VALID_GOOGLE_ID_TOKEN"}'
```

Expected: 200 OK with session tokens

***REMOVED******REMOVED******REMOVED*** 2. Android Verification
1. Build production release APK/AAB with production OAuth client
2. Install on test device
3. Go to Settings → Sign in with Google
4. Verify sign-in succeeds and creates backend session

***REMOVED******REMOVED******REMOVED*** 3. Common Errors

| Error | Cause | Fix |
|-------|-------|-----|
| `DEVELOPER_ERROR` | SHA-1 mismatch or wrong package name | Verify SHA-1 and package name in Google Cloud Console |
| `INVALID_TOKEN` | Backend using wrong Client ID | Update `GOOGLE_OAUTH_CLIENT_ID` in backend `.env` |
| `Auth failed: 401` | Client ID mismatch between Android and backend | Ensure both Android app and backend use compatible OAuth clients |

---

***REMOVED******REMOVED*** Test vs. Production Separation (Recommended)

For safety, maintain separate OAuth projects for development and production:

***REMOVED******REMOVED******REMOVED*** Development Environment
- **Project:** `Scanium Dev`
- **Backend Client ID:** `dev-backend-client-id.apps.googleusercontent.com`
- **Android Client ID:** `dev-android-client-id.apps.googleusercontent.com`
- **SHA-1:** Debug keystore SHA-1
- **Backend URL:** `http://localhost:8080` or `https://dev.scanium.example.com`

***REMOVED******REMOVED******REMOVED*** Production Environment
- **Project:** `Scanium Production`
- **Backend Client ID:** `prod-backend-client-id.apps.googleusercontent.com`
- **Android Client ID:** `prod-android-client-id.apps.googleusercontent.com`
- **SHA-1:** Production signing key SHA-1 (from Play Console or your keystore)
- **Backend URL:** `https://scanium.gtemp1.com`

---

***REMOVED******REMOVED*** Environment-Based Configuration (Advanced)

To avoid hardcoding credentials, use build variants:

***REMOVED******REMOVED******REMOVED*** AndroidManifest.xml
```xml
<meta-data
    android:name="com.google.android.gms.auth.google.client_id"
    android:value="@string/google_oauth_client_id" />
```

***REMOVED******REMOVED******REMOVED*** strings.xml (flavor-specific)
```
androidApp/src/dev/res/values/strings.xml:
<string name="google_oauth_client_id">DEV_CLIENT_ID</string>

androidApp/src/prod/res/values/strings.xml:
<string name="google_oauth_client_id">PROD_CLIENT_ID</string>
```

Then use `context.getString(R.string.google_oauth_client_id)` in `AuthRepository`.

---

***REMOVED******REMOVED*** OAuth Consent Screen Configuration

***REMOVED******REMOVED******REMOVED*** Internal Testing vs. Production

***REMOVED******REMOVED******REMOVED******REMOVED*** During Development (Internal Testing)
- **Publishing status:** Testing
- **User type:** Internal (limited to test users you add)
- **Test users:** Add your Gmail accounts in **OAuth consent screen → Test users**

***REMOVED******REMOVED******REMOVED******REMOVED*** For Production Release
- **Publishing status:** In production
- **User type:** External
- **Verification:** May require Google verification if requesting sensitive scopes (not required for basic profile/email)
- **Scopes required:**
  - `openid`
  - `email`
  - `profile`

---

***REMOVED******REMOVED*** Pre-Submission Checklist

Before submitting to Google Play:

- [ ] Production OAuth clients created in Google Cloud Console
- [ ] Backend `GOOGLE_OAUTH_CLIENT_ID` updated in production `.env`
- [ ] Android `GOOGLE_SERVER_CLIENT_ID` updated with production client ID
- [ ] Production SHA-1 added to Android OAuth client in Google Cloud Console
- [ ] OAuth consent screen set to "In production" (if going live)
- [ ] Test sign-in flow with production build on real device
- [ ] Verify backend token verification works with production client
- [ ] No test/dev client IDs in production code
- [ ] Privacy policy URL added to OAuth consent screen

---

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** Sign-in works in debug but fails in release
- **Cause:** Debug SHA-1 is different from release SHA-1
- **Fix:** Add production SHA-1 to Android OAuth client

***REMOVED******REMOVED******REMOVED*** Backend returns 401 "Invalid Google ID token"
- **Cause:** Backend `GOOGLE_OAUTH_CLIENT_ID` doesn't match Android client's audience
- **Fix:** Both clients must be in the same Google Cloud project, or configure backend to accept Android client ID

***REMOVED******REMOVED******REMOVED*** "This app is blocked" error during sign-in
- **Cause:** OAuth consent screen is in testing mode but user not added as test user
- **Fix:** Publish OAuth consent screen to production, or add user to test users list

---

***REMOVED******REMOVED*** References

- **Google Sign-In for Android:** https://developers.google.com/identity/sign-in/android/start
- **OAuth 2.0 for Mobile & Desktop Apps:** https://developers.google.com/identity/protocols/oauth2/native-app
- **Play Console OAuth Requirements:** https://support.google.com/googleplay/android-developer/answer/9844679

---

***REMOVED******REMOVED*** Maintenance

Review OAuth configuration whenever:
- Changing package name or signing key
- Migrating between Google Cloud projects
- Adding new backend environments (staging, production)
- Updating Play App Signing certificate

**Last verified:** Phase D (2026-01-13)
