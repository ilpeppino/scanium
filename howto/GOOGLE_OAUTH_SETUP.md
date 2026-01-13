***REMOVED*** Google OAuth Setup for Scanium Android

***REMOVED******REMOVED*** Prerequisites
- Google Cloud Console access: https://console.cloud.google.com
- Package name: `com.scanium.app.dev` (dev) or `com.scanium.app` (prod)
- SHA-1 fingerprint: `03:C5:1E:2F:FA:EA:B6:F9:AA:F9:C6:25:D4:14:08:14:57:C1:FC:A2`

---

***REMOVED******REMOVED*** Step 1: Create OAuth 2.0 Credentials

1. **Go to Google Cloud Console:**
   - Navigate to: https://console.cloud.google.com/apis/credentials
   - Select your project (or create one if needed)

2. **Create Android OAuth Client ID:**
   - Click "Create Credentials" → "OAuth client ID"
   - Application type: **Android**
   - Name: `Scanium Android (Dev)`
   - Package name: `com.scanium.app.dev`
   - SHA-1 fingerprint: `03:C5:1E:2F:FA:EA:B6:F9:AA:F9:C6:25:D4:14:08:14:57:C1:FC:A2`
   - Click "Create"

3. **Create Web OAuth Client ID (for backend):**
   - Click "Create Credentials" → "OAuth client ID"
   - Application type: **Web application**
   - Name: `Scanium Backend`
   - Authorized redirect URIs: (leave empty for now, backend uses token exchange)
   - Click "Create"
   - **Copy the Client ID** - you'll need this for the backend

4. **Get the Server Client ID:**
   - From the Web OAuth Client you just created
   - Copy the full Client ID (format: `xxxxx.apps.googleusercontent.com`)

---

***REMOVED******REMOVED*** Step 2: Configure Android App

**File:** `androidApp/src/main/java/com/scanium/app/auth/CredentialManagerAuthLauncher.kt`

Replace line 63:
```kotlin
private const val GOOGLE_SERVER_CLIENT_ID = "YOUR_ANDROID_CLIENT_ID.apps.googleusercontent.com"
```

With your Web OAuth Client ID:
```kotlin
private const val GOOGLE_SERVER_CLIENT_ID = "123456789-abcdefg.apps.googleusercontent.com"
```

---

***REMOVED******REMOVED*** Step 3: Configure Backend

**File (on NAS):** `/volume1/docker/scanium/repo/backend/.env`

Add this line:
```bash
GOOGLE_OAUTH_CLIENT_ID=123456789-abcdefg.apps.googleusercontent.com
```

---

***REMOVED******REMOVED*** Step 4: Restart Services

***REMOVED******REMOVED******REMOVED*** On Mac:
```bash
cd /Users/family/dev/scanium
git add -A
git commit -m "feat(auth): configure Google OAuth credentials"
git push origin main
```

***REMOVED******REMOVED******REMOVED*** On NAS:
```bash
ssh nas
cd /volume1/docker/scanium/repo
git pull origin main
cd /volume1/docker/scanium
docker-compose restart api
```

---

***REMOVED******REMOVED*** Step 5: Test

1. **Rebuild Android app:**
   ```bash
   ./gradlew :androidApp:assembleDevDebug
   adb install androidApp/build/outputs/apk/dev/debug/androidApp-dev-debug.apk
   ```

2. **Test sign-in:**
   - Open app
   - Settings → General
   - Tap "Sign in to Google"
   - **Expected:** Google account picker appears
   - Select account
   - **Expected:** Sign-in succeeds, shows your profile

---

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** "No credentials available"
- Verify SHA-1 fingerprint matches in Google Cloud Console
- Verify package name matches (`com.scanium.app.dev`)
- Wait 5-10 minutes after creating credentials (Google propagation delay)

***REMOVED******REMOVED******REMOVED*** "Invalid client"
- Verify GOOGLE_SERVER_CLIENT_ID matches Web OAuth Client ID
- Verify backend has GOOGLE_OAUTH_CLIENT_ID in .env
- Restart backend: `docker-compose restart api`

***REMOVED******REMOVED******REMOVED*** Check logs:
```bash
***REMOVED*** Android logs
adb logcat -s CredentialManagerAuthLauncher AuthRepository

***REMOVED*** Backend logs
ssh nas "cd /volume1/docker/scanium && docker-compose logs -f api | grep -i auth"
```

---

***REMOVED******REMOVED*** Production Setup (Later)

When ready for production, repeat for `com.scanium.app`:

1. Get production SHA-1:
   ```bash
   keytool -list -v -keystore /path/to/release.keystore -alias release
   ```

2. Create new OAuth Client ID for package `com.scanium.app`

3. Update `CredentialManagerAuthLauncher.kt` to use production client ID

---

***REMOVED******REMOVED*** Security Notes

- **Never commit OAuth secrets to git** (add to .gitignore)
- **Use environment variables** for credentials
- **Rotate credentials** if exposed
- **Restrict API keys** to specific package names and SHA-1 fingerprints
