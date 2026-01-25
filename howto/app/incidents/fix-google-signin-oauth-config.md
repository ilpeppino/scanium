# Fix: Google Sign-In OAuth Configuration

**Related RCA:** `rca-google-signin-preflight-401.md`
**Priority:** P0 - Critical
**Estimated Time:** 30 minutes

---

## Problem Summary

Google Sign-In fails with `DEVELOPER_ERROR` because `androidApp/build.gradle.kts` contains placeholder OAuth Client IDs (`"REDACTED_GOOGLE_OAUTH_CLIENT_ID"`) instead of real Google Cloud Console Client IDs.

---

## Quick Fix (Step-by-Step)

### 1. Create OAuth Clients (15 minutes)

Go to [Google Cloud Console → Credentials](https://console.cloud.google.com/apis/credentials)

Create **3 Web Application OAuth Clients:**

| Flavor | Name | Client Type | Authorized Origins |
|--------|------|-------------|-------------------|
| prod | `Scanium Production Web` | Web application | `https://scanium.gtemp1.com` |
| dev | `Scanium Dev Web` | Web application | `http://localhost:3000`<br>`https://scanium.gtemp1.com` |
| beta | `Scanium Beta Web` | Web application | `https://scanium.gtemp1.com` |

**Important:** Use **Web application** type, NOT Android type.

Copy each Client ID (format: `123456-abc.apps.googleusercontent.com`)

---

### 2. Update Android Build Config (5 minutes)

Edit `androidApp/build.gradle.kts`:

```kotlin
productFlavors {
    create("prod") {
        // ... existing config ...
        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            "\"<PASTE_PROD_CLIENT_ID_HERE>\""  // Replace this
        )
    }

    create("dev") {
        // ... existing config ...
        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            "\"<PASTE_DEV_CLIENT_ID_HERE>\""  // Replace this
        )
    }

    create("beta") {
        // ... existing config ...
        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            "\"<PASTE_BETA_CLIENT_ID_HERE>\""  // Replace this
        )
    }
}
```

**Example (DO NOT use these IDs):**
```kotlin
"\"480326569434-nhp9a4ups5sb3i5ualtuc67h2865qhdo.apps.googleusercontent.com\""
```

---

### 3. Update Backend Config (2 minutes)

Edit `backend/.env`:

```env
# Use the PRODUCTION Client ID
GOOGLE_OAUTH_CLIENT_ID=<PASTE_PROD_CLIENT_ID_HERE>
```

**Restart backend:**
```bash
cd backend
npm run dev
```

---

### 4. Rebuild and Test (8 minutes)

```bash
# Clean and rebuild dev flavor
./gradlew clean :androidApp:assembleDevDebug

# Install
adb install androidApp/build/outputs/apk/dev/debug/androidApp-dev-debug.apk

# Test
1. Open app
2. Settings → General → Sign in with Google
3. ✅ Google account picker appears (no DEVELOPER_ERROR)
4. Select account
5. ✅ Sign-in succeeds, profile shows
```

---

## Verification Checklist

- [ ] Created 3 Web OAuth clients in Google Cloud Console
- [ ] Updated `build.gradle.kts` with real Client IDs (all 3 flavors)
- [ ] Updated `backend/.env` with production Client ID
- [ ] Restarted backend
- [ ] Rebuilt Android app
- [ ] Tested sign-in on dev flavor - SUCCESS
- [ ] Verified backend logs show `auth_login_success`
- [ ] Tested sign-in on beta flavor (optional)
- [ ] Tested sign-in on prod flavor (optional)

---

## Expected Outcome

**Before:**
```
User taps "Sign in with Google"
→ DEVELOPER_ERROR
→ No account picker
→ Sign-in fails
```

**After:**
```
User taps "Sign in with Google"
→ Google account picker appears
→ User selects account
→ Sign-in succeeds
→ Profile displayed
→ Session token stored
→ Preflight succeeds (if signed in)
```

---

## Common Pitfalls

### ❌ Wrong Client Type
Using **Android** OAuth client instead of **Web application**
- **Fix:** Create **Web application** clients

### ❌ Mismatched Client IDs
Android and backend using different Client IDs
- **Fix:** Backend should use **production** Client ID, all 3 flavors should have their own

### ❌ Missing Authorized Origins
Web clients missing `https://scanium.gtemp1.com` in authorized origins
- **Fix:** Add backend URL to each client's authorized origins

### ❌ Forgot to Rebuild
Changing `build.gradle.kts` but not rebuilding
- **Fix:** Always run `./gradlew clean :androidApp:assembleDevDebug` after config changes

---

## Rollback Plan

If sign-in fails after applying fix:

1. Check backend logs for token verification errors:
   ```bash
   cd backend && npm run logs | grep -i "auth"
   ```

2. Verify Client IDs match:
   ```bash
   # Android (should print the Client ID)
   grep -A2 "GOOGLE_SERVER_CLIENT_ID" androidApp/build.gradle.kts

   # Backend
   grep GOOGLE_OAUTH_CLIENT_ID backend/.env
   ```

3. Check Google Cloud Console:
   - OAuth consent screen is published (not in testing mode, or user added as test user)
   - Web clients have correct authorized origins
   - SHA-1 fingerprint matches (for Android OAuth client if created)

4. Revert `build.gradle.kts` to placeholder and investigate

---

## Testing Matrix

| Flavor | Package | OAuth Client | Test Status |
|--------|---------|--------------|-------------|
| dev | `com.scanium.app.dev` | `DEV_CLIENT_ID` | ⏳ Pending |
| beta | `com.scanium.app.beta` | `BETA_CLIENT_ID` | ⏳ Pending |
| prod | `com.scanium.app` | `PROD_CLIENT_ID` | ⏳ Pending |

---

## Security Notes

**Client IDs are NOT secrets** - they identify your app to Google but don't grant access. Safe to commit to version control.

**DO NOT commit:**
- Client Secrets (if you have Web OAuth with secrets)
- Session signing secrets
- API keys

**Already .gitignored:**
- `backend/.env` (contains secrets)
- `local.properties` (contains API keys)

---

## Next Steps After Fix

1. Update documentation:
   - Mark `howto/app/reference/GOOGLE_SIGNIN_CONFIG.md` as verified
   - Update `howto/GOOGLE_OAUTH_SETUP.md` with real Client IDs (redacted in public repos)

2. Add build validation:
   - Gradle task to check for placeholder Client IDs
   - CI check to warn about placeholders

3. Test preflight:
   - Sign in with Google
   - Open Assistant
   - Verify preflight status is AVAILABLE

4. Production readiness:
   - Get production signing SHA-1 from Play Console
   - Create production Android OAuth client with SHA-1
   - Test production release build before Play Store upload

---

## Support

If issues persist after applying fix:

1. Check RCA document: `howto/project/rca-google-signin-preflight-401.md`
2. Review Google Sign-In docs: `howto/app/reference/GOOGLE_SIGNIN_CONFIG.md`
3. Check backend auth logs for detailed error messages
4. Verify Android logcat for CredentialManager errors:
   ```bash
   adb logcat -s CredentialManagerAuthLauncher:* AuthRepository:*
   ```
