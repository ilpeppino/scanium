# Play Console Review Access - Scanium

**Last Updated:** 2026-01-13
**Purpose:** Provide guidance for Play Console review access requirements

---

## Overview

Google Play requires that reviewers can access and test all features of your app. For Scanium, most features are accessible without login, making review straightforward.

---

## App Access Strategy

### Public Features (No Login Required) ✅
Scanium's core features are available without sign-in:
- ✅ Camera scanning
- ✅ Object detection
- ✅ Item classification
- ✅ Basic item management
- ✅ Settings (most features)

**Result:** Reviewers can test primary functionality immediately without credentials.

### Optional Sign-In Features (Google OAuth) ⚠️
These features require Google Sign-In:
- Data synchronization across devices
- AI assistant (if enabled)
- eBay marketplace integration (if user connects)
- Account preferences

**Access Method:** Reviewers can use any personal Google account.

---

## Play Console Configuration

### App Access Section

When filling out **App access** in Play Console:

#### Question: "Do all features in your app require login?"
**Answer:** ❌ **No**

#### Question: "Does your app allow users to create an account?"
**Answer:** ✅ **Yes** (via Google Sign-In)

#### Provide Instructions for Review
**Sample text:**
```
Scanium's primary features (camera scanning, object detection, item classification)
are fully accessible without login.

Optional features require Google Sign-In:
- User account: Use any Google account (no special credentials needed)
- Sign-in path: Open app → Settings → "Continue with Google"
- Features unlocked: Data sync, AI assistant, marketplace integration

No test account needed - any Gmail account will work.
```

---

## Do You Need to Provide Test Credentials?

### No Test Credentials Needed ✅

Reasons:
1. **OAuth with Google** - Reviewers can use their own Google accounts
2. **No custom authentication** - No username/password system
3. **No restricted access** - No approval process, roles, or membership levels
4. **No payment required** - All features work in free tier

### If Play Requires Credentials Anyway

Some reviewers may still request access details. Provide this:

**Account Type:** Google Sign-In (OAuth)
**Test Account:** Not required - use any Google account
**Sign-In Steps:**
1. Open Scanium app
2. Navigate to Settings
3. Tap "Continue with Google"
4. Sign in with any Google account
5. Grant permissions when prompted

**Alternative:** If Play Console form requires an email, provide:
- **Email:** `scanium.reviewer@gmail.com` (create a dedicated test account if needed)
- **Password:** Not applicable (OAuth flow)

---

## Review Scenarios

### Scenario 1: Reviewer Tests Without Sign-In ✅
- **Expected:** Full access to scanning, detection, classification
- **Features tested:** 90% of app functionality
- **Outcome:** Should approve if these work correctly

### Scenario 2: Reviewer Tests With Sign-In ✅
- **Expected:** Additional features like sync, AI assistant
- **Reviewer uses:** Own Google account
- **Outcome:** Full feature coverage

### Scenario 3: Reviewer Can't Sign In ❌
- **Likely cause:**
  - OAuth consent screen in "Testing" mode (not "In production")
  - Reviewer not added to test users list
  - Production OAuth client not configured
- **Fix:** See `oauth-production-readiness.md`

---

## OAuth Consent Screen Configuration for Review

### Publishing Status
**Set to:** ✅ **In production**

If kept in "Testing" mode:
- Only users added to "Test users" list can sign in
- Reviewers will be blocked unless added to test user list
- Google may reject app for restricted access

### Required Fields
- ✅ App name: "Scanium"
- ✅ User support email: `contact@scanium.app`
- ✅ Developer contact email: `your-dev-email@example.com`
- ✅ App logo (120x120px)
- ✅ Privacy Policy URL: `https://scanium-site-url/PRIVACY.html`

---

## Testing Before Submission

### Pre-Submit Checklist

- [ ] OAuth consent screen set to "In production" (or all reviewers added to test users)
- [ ] Sign-in works with fresh Google account (not previously used for testing)
- [ ] Core features (scanning, detection) work without login
- [ ] Optional features (sync, assistant) work after Google Sign-In
- [ ] No errors or crashes during sign-in flow
- [ ] Privacy policy link is live and accessible
- [ ] Account deletion works (in-app + web)

### Test With Fresh Account
1. Create a new Google account (or use a friend's with permission)
2. Install production build
3. Test core features without sign-in
4. Sign in with Google
5. Test signed-in features
6. Sign out and delete account (verify it works)

---

## Common Review Issues

| Issue | Cause | Fix |
|-------|-------|-----|
| "Can't access features" | OAuth in testing mode | Publish OAuth consent screen |
| "Login required but no credentials" | Instructions unclear | Update App Access description in Play Console |
| "Sign-in doesn't work" | Production OAuth not configured | See `oauth-production-readiness.md` |
| "App crashes on sign-in" | Release build signing mismatch | Verify SHA-1 in Google Cloud Console |

---

## Play Console App Access Form Template

Copy this into Play Console → **App access**:

```
App Access Type:
☑️ All or some functionality is restricted

Explain restrictions:
Scanium offers optional Google Sign-In for data synchronization and personalized
features. Core functionality (camera scanning, object detection, item classification)
is available without login.

Instructions for testing sign-in features:
1. Open Scanium app
2. Navigate to: Settings → Account section
3. Tap "Continue with Google"
4. Sign in using any Google account (no test account needed)
5. Optional features will be unlocked: data sync, AI assistant, marketplace integration

Alternative access method:
Not applicable - any Google account works

Test credentials:
Not required - reviewers can use their own Google accounts
```

---

## App Content Rating & Target Audience

### Content Rating
**Recommended:** Everyone (all ages)
- No mature content
- No violence, profanity, or adult themes
- Primary use: item scanning for resale/organization

### Target Audience
**Age group:** 18+ (due to marketplace features like eBay integration)
- Declare: "App designed for adults involved in online resale"

---

## Final Recommendations

1. **Keep core features public** - Don't require login for main functionality
2. **Make OAuth "In production"** - Critical for smooth review process
3. **Test with fresh account** - Ensures reviewers won't be blocked
4. **Provide clear instructions** - Even though no credentials needed, explain the flow
5. **Monitor review status** - Respond quickly to reviewer questions if app is rejected

---

## References

- **Play Console App Access:** https://support.google.com/googleplay/android-developer/answer/9859455
- **OAuth Consent Screen:** https://support.google.com/cloud/answer/10311615
- **Scanium OAuth Setup:** `oauth-production-readiness.md`

**Last verified:** Phase D (2026-01-13)
