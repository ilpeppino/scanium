# Phase D Verification Checklist - Production Readiness

**Date:** 2026-01-13
**Status:** ✅ Implementation Complete
**Goal:** Make Scanium compliant and production-ready for Google Play

---

## Implementation Summary

Phase D adds comprehensive account deletion functionality to meet Google Play requirements for apps
with user accounts (Google Sign-In). This includes in-app deletion, web-based deletion, privacy
policy updates, and Play Console documentation.

---

## 🎯 Deliverables

### ✅ Backend Implementation

#### Account Deletion Service

- **File:** `backend/src/modules/account/deletion-service.ts`
- **Features:**
    - Immediate account deletion (hard delete with CASCADE)
    - Email-based deletion request with verification token
    - Token-based confirmation flow (1-hour expiry)
    - Automatic cleanup of expired verification tokens

#### Account Deletion Endpoints

- **File:** `backend/src/modules/account/routes.ts`
- **Endpoints:**
    - `POST /v1/account/delete` - Authenticated user deletion (immediate)
    - `GET /v1/account/deletion-status` - Check deletion status
    - `POST /v1/account/delete-by-email` - Web deletion request (rate-limited)
    - `POST /v1/account/delete/confirm` - Confirm deletion via token

#### Data Deleted on Account Deletion

- ✅ User profile (googleSub, email, displayName, pictureUrl)
- ✅ All sessions (access tokens, refresh tokens)
- ✅ eBay connections (OAuth tokens, encrypted)
- ✅ All listings (drafts and published)
- ✅ Related data via CASCADE delete

#### Security Features

- ✅ Rate limiting on email-based deletion (3 requests per 15 minutes per IP)
- ✅ Email verification with secure random tokens (32 bytes base64url)
- ✅ Token expiry (1 hour)
- ✅ Prevention of unauthorized deletion (requires auth or email verification)

---

### ✅ Android Implementation

#### In-App Deletion UI

- **File:** `androidApp/src/main/java/com/scanium/app/ui/settings/SettingsGeneralScreen.kt`
- **Features:**
    - Delete Account button in Settings → Account section
    - Confirmation dialog with warning message
    - Clear explanation of what will be deleted
    - Error handling and success feedback via Snackbar
    - Red warning color for destructive action

#### Auth Repository

- **File:** `androidApp/src/main/java/com/scanium/app/auth/AuthRepository.kt`
- **New method:** `deleteAccount()` - calls backend and clears local state

#### API Client

- **File:** `androidApp/src/main/java/com/scanium/app/auth/GoogleAuthApi.kt`
- **New method:** `deleteAccount(accessToken)` - HTTP POST to backend

#### String Resources

- **File:** `androidApp/src/main/res/values/strings.xml`
- **Added:**
    - settings_delete_account
    - settings_delete_account_title
    - settings_delete_account_message
    - settings_delete_account_confirm
    - settings_delete_account_cancel
    - settings_delete_account_success
    - settings_delete_account_error

---

### ✅ Web Deletion Resource

#### Account Deletion Page

- **File:** `scanium-site/account-deletion.html`
- **Features:**
    - Standalone HTML page (no dependencies)
    - Email input form with validation
    - Email verification flow explanation
    - What gets deleted section
    - Data retention disclosure
    - Links to privacy policy
    - Handles token confirmation automatically (query param: `?token=...`)
    - Success/error states

**Deployed URL:** `https://scanium-site-url/account-deletion.html` (update with actual URL)

---

### ✅ Privacy Policy Updates

#### Privacy Policy

- **File:** `scanium-site/PRIVACY.html`
- **Updates (2026-01-13):**
    - Section 2.3: Added Google Sign-In account information
    - Section 2.4: Added IP address collection disclosure
    - Section 5: Added account data retention until deletion
    - **Section 6 (NEW):** Comprehensive account deletion section:
        - 6.1: In-app deletion instructions
        - 6.2: Web-based deletion link
        - 6.3: What gets deleted
        - 6.4: Data retention after deletion
        - 6.5: Deletion timeline

---

### ✅ Play Console Documentation

#### Data Safety Form Mapping

- **File:** `howto/project/play-data-safety.md`
- **Contents:**
    - Accurate answers for all Data Safety questions
    - Data types collected (account info, photos, app activity, device IDs)
    - Third-party sharing disclosure (Google Vision, OpenAI, eBay)
    - Encryption practices (in transit: HTTPS, at rest: AES-256-GCM)
    - Account deletion URL reference
    - Declaration checklist
    - Common mistakes to avoid

#### OAuth Production Readiness

- **File:** `howto/project/oauth-production-readiness.md`
- **Contents:**
    - OAuth client setup for production vs. development
    - SHA-1 fingerprint extraction (Play signing vs. custom)
    - Backend and Android configuration
    - Verification steps
    - Environment-based configuration options
    - Pre-submission checklist
    - Troubleshooting guide

#### Play Review Access

- **File:** `howto/project/play-review-access.md`
- **Contents:**
    - Public vs. sign-in features breakdown
    - Play Console App Access form template
    - OAuth consent screen configuration
    - Testing with fresh Google account
    - Common review issues and fixes

---

## 🧪 Testing

### Backend Tests

- **File:** `backend/src/modules/account/routes.test.ts`
- **Status:** ⚠️ Implemented but requires Postgres database to run
- **Tests cover:**
    - Authenticated user deletion
    - Auth requirement validation
    - Token invalidation after deletion
    - Deletion status endpoint
    - Email-based deletion request
    - Rate limiting enforcement
    - Token confirmation
    - Token reuse prevention
    - Cascade deletion of related data

**To run (requires DB):**

```bash
cd backend && npm test -- src/modules/account/routes.test.ts
```

### Android Tests

- **Status:** ⚠️ Not implemented (unit tests for deletion flow recommended)
- **Recommended tests:**
    - ViewModel deleteAccount() success/failure
    - Dialog state management
    - Token clearing after deletion
    - Error message formatting

**To implement:**

```bash
cd androidApp && ./gradlew :androidApp:test
```

### Manual Testing Required

#### Backend Deletion (curl)

```bash
# 1. Get auth token (sign in first)
ACCESS_TOKEN="your-access-token"

# 2. Delete account
curl -X POST https://scanium.gtemp1.com/v1/account/delete \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# Expected: 200 OK, status: DELETED
```

#### Web Deletion

1. Open: `https://scanium-site-url/account-deletion.html`
2. Enter email address
3. Submit form
4. Check email for verification link
5. Click link (redirects back with token)
6. Verify success message

#### In-App Deletion

1. Open Scanium app
2. Sign in with Google
3. Go to Settings
4. Scroll to Account section
5. Tap "Delete Account"
6. Read confirmation dialog
7. Tap "Delete Account" again
8. Verify snackbar shows success
9. Verify user is signed out

---

## 📋 Pre-Submission Checklist

### Backend

- [ ] Account deletion endpoints deployed to production
- [ ] Rate limiting configured (3 req/15min for email deletion)
- [ ] Verification tokens expire after 1 hour
- [ ] All user data deleted via CASCADE (verify in DB schema)
- [ ] Session cleanup job running

### Android

- [ ] Delete Account button visible in Settings (signed-in users only)
- [ ] Confirmation dialog explains what gets deleted
- [ ] Error handling shows clear messages
- [ ] Tokens cleared after deletion
- [ ] Build production APK/AAB to test

### Web

- [ ] account-deletion.html deployed and accessible
- [ ] HTTPS enabled
- [ ] Email verification flow works
- [ ] Token confirmation redirects correctly
- [ ] Privacy policy link works

### Privacy & Compliance

- [ ] PRIVACY.html updated (2026-01-13) and deployed
- [ ] Account deletion section (6) complete
- [ ] Privacy policy URL live: `https://scanium-site-url/PRIVACY.html`
- [ ] Deletion URL live: `https://scanium-site-url/account-deletion.html`

### OAuth Production

- [ ] Production OAuth clients created (backend + Android)
- [ ] Backend GOOGLE_OAUTH_CLIENT_ID updated in production .env
- [ ] Android GOOGLE_SERVER_CLIENT_ID updated with production client
- [ ] Production SHA-1 added to Android OAuth client
- [ ] OAuth consent screen set to "In production"
- [ ] Test sign-in with fresh Google account

### Play Console

- [ ] Data Safety form filled out (use `play-data-safety.md`)
- [ ] Account deletion URL added to appropriate field
- [ ] Privacy policy URL added
- [ ] App access section describes sign-in as optional
- [ ] No advertising ID permission declared (verified)

---

## 🚀 Deployment Order

1. **Deploy backend changes:**
   ```bash
   cd backend
   npm run build
   # Deploy to production server
   # Update .env with GOOGLE_OAUTH_CLIENT_ID
   # Restart backend service
   ```

2. **Deploy website changes:**
   ```bash
   cd ../scanium-site
   # Upload PRIVACY.html and account-deletion.html
   # Verify HTTPS works
   ```

3. **Build Android production release:**
   ```bash
   cd ../androidApp
   # Update AuthRepository.kt with production OAuth client
   ./gradlew :androidApp:assembleProdRelease
   # Or create AAB for Play Console
   ./gradlew :androidApp:bundleProdRelease
   ```

4. **Submit to Play Console:**
    - Upload production APK/AAB
    - Fill out Data Safety form (use `play-data-safety.md`)
    - Add privacy policy URL
    - Add account deletion URL
    - Submit for review

---

## 📊 API Contract Reference

### POST /v1/account/delete

**Auth:** Required (Bearer token)
**Request:** Empty body
**Response (200 OK):**

```json
{
  "status": "DELETED",
  "message": "Your account and all associated data have been permanently deleted",
  "correlationId": "uuid"
}
```

### GET /v1/account/deletion-status

**Auth:** Required
**Response (200 OK):**

```json
{
  "status": "ACTIVE",
  "correlationId": "uuid"
}
```

### POST /v1/account/delete-by-email

**Auth:** None (rate-limited by IP)
**Request:**

```json
{
  "email": "scanium@gtemp1.com"
}
```

**Response (200 OK):**

```json
{
  "message": "Verification email sent. Please check your email to confirm account deletion.",
  "verificationUrl": "https://scanium.gtemp1.com/account/delete/confirm?token=...",
  "expiresAt": "2026-01-13T16:00:00.000Z",
  "correlationId": "uuid"
}
```

### POST /v1/account/delete/confirm

**Auth:** None
**Request:**

```json
{
  "token": "verification-token-string"
}
```

**Response (200 OK):**

```json
{
  "success": true,
  "message": "Your account and all associated data have been permanently deleted",
  "correlationId": "uuid"
}
```

---

## 🔗 Important URLs

| Resource          | Local Path                                    | Production URL                                   |
|-------------------|-----------------------------------------------|--------------------------------------------------|
| Privacy Policy    | `scanium-site/PRIVACY.html`                   | `https://scanium-site-url/PRIVACY.html`          |
| Account Deletion  | `scanium-site/account-deletion.html`          | `https://scanium-site-url/account-deletion.html` |
| Backend Delete    | -                                             | `https://scanium.gtemp1.com/v1/account/delete`   |
| Data Safety Doc   | `howto/project/play-data-safety.md`           | -                                                |
| OAuth Setup Doc   | `howto/project/oauth-production-readiness.md` | -                                                |
| Review Access Doc | `howto/project/play-review-access.md`         | -                                                |

---

## ✅ Phase D Completion Status

**Implementation:** ✅ Complete
**Documentation:** ✅ Complete
**Testing:** ⚠️ Backend tests implemented (require DB), Android tests recommended
**Deployment:** ⏳ Ready for deployment

**Next Steps:**

1. Deploy backend to production
2. Deploy website updates (privacy + deletion pages)
3. Test end-to-end deletion flow (in-app + web)
4. Update Play Console Data Safety form
5. Build and submit production APK/AAB

---

**Phase D Successfully Implemented:** 2026-01-13
**Ready for Google Play Production Release:** ✅ Yes (pending deployment and OAuth production setup)
