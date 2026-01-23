# 🔐 Google OAuth Setup - Resume Instructions

## ✅ What's Been Done

### 1. Auth Bug Fix (COMPLETED)

- ✅ Fixed silent sign-in failure (commit 5c0c022)
- ✅ Added Activity context requirement
- ✅ Added UI loading states and error messages
- ✅ Added regression tests
- ✅ Merged to main and synced with NAS

### 2. OAuth Documentation (COMPLETED)

- ✅ Created comprehensive setup guide: `howto/GOOGLE_OAUTH_SETUP.md`
- ✅ Created automated setup script: `setup-google-oauth.sh`
- ✅ Committed and pushed (commit 9b7690a)
- ✅ Synced with NAS

---

## 📊 Current Status

**What works:**

- ✅ Tap "Sign in to Google" triggers the flow (no longer silent)
- ✅ Shows "Signing in..." loading state
- ✅ Shows error message: "Sign-in failed: no credentials available"

**What's needed:**

- ⏳ Google OAuth Client ID configuration

**Current error:** `"no credentials available"`
**Reason:** OAuth credentials not yet configured in Google Cloud Console

---

## 🚀 Next Steps to Complete OAuth Setup

### Step 1: Create OAuth Credentials (15 minutes)

**Open Google Cloud Console:**

```
https://console.cloud.google.com/apis/credentials
```

**Actions:**

1. Select your Google Cloud project
2. Click "CREATE CREDENTIALS" → "OAuth client ID"
3. Configure OAuth consent screen if prompted:
    - User Type: External
    - App name: Scanium
    - Add your email as test user
4. Create **TWO** OAuth clients:

#### a) Web OAuth Client (MOST IMPORTANT - you need this one!)

- Application type: **Web application**
- Name: `Scanium Backend`
- Authorized redirect URIs: (leave empty)
- Click CREATE
- **📋 COPY THE CLIENT ID** (format: `123456789-abc.apps.googleusercontent.com`)

#### b) Android OAuth Client

- Application type: **Android**
- Name: `Scanium Android Dev`
- Package name: `com.scanium.app.dev`
- SHA-1 fingerprint: `03:C5:1E:2F:FA:EA:B6:F9:AA:F9:C6:25:D4:14:08:14:57:C1:FC:A2`
- Click CREATE

---

### Step 2: Configure Credentials (Automated - 2 minutes)

**Option A: Use the automated script (RECOMMENDED)**

Once you have the Web OAuth Client ID:

```bash
cd /Users/family/dev/scanium
./setup-google-oauth.sh
# Paste your Client ID when prompted
```

This will automatically:

- Update Android app code
- Update backend .env on NAS
- Restart backend API
- Commit and push changes

**Option B: Manual configuration**

If you prefer manual setup, see: `howto/GOOGLE_OAUTH_SETUP.md`

---

### Step 3: Rebuild and Test (5 minutes)

```bash
# Rebuild Android app
./gradlew :androidApp:assembleDevDebug

# Install on device/emulator
adb install androidApp/build/outputs/apk/dev/debug/androidApp-dev-debug.apk

# Test sign-in
# 1. Open Scanium app
# 2. Tap Settings (⚙️)
# 3. Tap General
# 4. Tap "Sign in to Google"
# 5. Expected: Google account picker appears
# 6. Select your account
# 7. Expected: Shows your name/email with "Sign Out" button
```

---

## 📁 Key Files

**Documentation:**

- `howto/GOOGLE_OAUTH_SETUP.md` - Full setup guide
- `OAUTH_RESUME_STEPS.md` - This file

**Code files that need OAuth Client ID:**

- `androidApp/src/main/java/com/scanium/app/auth/CredentialManagerAuthLauncher.kt`
    - Line 63: Replace `YOUR_ANDROID_CLIENT_ID.apps.googleusercontent.com`
    - With: Your Web OAuth Client ID

**Backend config:**

- On NAS: `/volume1/docker/scanium/repo/backend/.env`
    - Add: `GOOGLE_OAUTH_CLIENT_ID=your-client-id.apps.googleusercontent.com`

---

## 🔍 Verification Checklist

Before testing:

- [ ] Web OAuth Client ID created in Google Cloud Console
- [ ] Android OAuth Client ID created with correct package + SHA-1
- [ ] Your Google account added as test user in OAuth consent screen
- [ ] `CredentialManagerAuthLauncher.kt` updated with Client ID
- [ ] Backend `.env` file updated with Client ID
- [ ] Backend API restarted
- [ ] Android app rebuilt and installed

---

## 🐛 Troubleshooting

### Still seeing "no credentials available"

1. **Wait 5-10 minutes** - Google needs time to propagate credentials
2. Verify package name: `com.scanium.app.dev` (check build.gradle.kts)
3. Verify SHA-1 matches in Google Cloud Console
4. Check Android logs: `adb logcat -s CredentialManagerAuthLauncher`

### "Invalid client" error

1. Verify `GOOGLE_SERVER_CLIENT_ID` matches Web OAuth Client ID
2. Check backend .env has `GOOGLE_OAUTH_CLIENT_ID`
3. Restart backend: `ssh nas "cd /volume1/docker/scanium && docker-compose restart api"`

### Backend not receiving token

1. Check backend logs:
   `ssh nas "cd /volume1/docker/scanium && docker-compose logs -f api | grep -i auth"`
2. Verify backend can reach `https://oauth2.googleapis.com/tokeninfo`

---

## 📞 Quick Reference

**Your Android App Info:**

- Dev Package: `com.scanium.app.dev`
- SHA-1: `03:C5:1E:2F:FA:EA:B6:F9:AA:F9:C6:25:D4:14:08:14:57:C1:FC:A2`

**Useful Commands:**

```bash
# View setup guide
open howto/GOOGLE_OAUTH_SETUP.md

# Run automated setup
./setup-google-oauth.sh

# Rebuild app
./gradlew :androidApp:assembleDevDebug

# Check logs
adb logcat -s CredentialManagerAuthLauncher AuthRepository

# Backend logs
ssh nas "cd /volume1/docker/scanium && docker-compose logs -f api"
```

---

## ✅ When You're Done

After successful sign-in:

1. You should see your Google profile in Settings → General
2. "Sign Out" button should be visible
3. Backend should have stored session token
4. You can test sign-out and re-sign-in

**Then mark this task complete and move on!**

---

## 🔒 Security Note

Your OAuth credentials are sensitive:

- Never commit Client IDs or secrets to public repos
- The automated script updates local files only
- Backend `.env` file is in `.gitignore`
- NAS backend files are not in git

---

Last updated: 2026-01-13 (commit 9b7690a)
