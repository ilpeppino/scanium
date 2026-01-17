***REMOVED*** Notification Permission Fix - DEV Flavor Only

**Date:** 2026-01-11
**Commit:** `8065317d5c21465e23cc5b778c28063319eb6492`
**Status:** ✅ **FIXED AND DEPLOYED**

***REMOVED******REMOVED*** Problem Statement

In DEV flavor, notifications were blocked and the system "Allow notifications" toggle was greyed out after a fresh install on Android 13+. No permission prompt was shown, preventing the Background Health Monitor from sending notifications.

---

***REMOVED******REMOVED*** Root Cause Analysis

***REMOVED******REMOVED******REMOVED*** Issue
1. ❌ `POST_NOTIFICATIONS` permission was **NOT declared** in AndroidManifest.xml
2. ❌ No runtime permission request existed for notifications
3. ✅ Target SDK = 35 (Android 15), which **requires** POST_NOTIFICATIONS on Android 13+
4. ✅ Code already had permission checks (`hasNotificationPermission()`) but never requested it

***REMOVED******REMOVED******REMOVED*** Why This Matters
On Android 13+ (API 33+), apps must:
1. Declare `POST_NOTIFICATIONS` in the manifest
2. Request the permission at runtime
3. Without both, the system **denies** notification permission by default and **greys out** the system toggle

---

***REMOVED******REMOVED*** Solution Implemented

***REMOVED******REMOVED******REMOVED*** 1. DEV-Only Manifest with POST_NOTIFICATIONS ✅

**File:** `androidApp/src/dev/AndroidManifest.xml` (NEW)

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- DEV flavor only: Required for background health monitor notifications on Android 13+ -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
</manifest>
```

**Why DEV-only source set?**
- Beta/prod flavors do NOT get this manifest
- Beta/prod users will NOT see notification permission prompts
- Completely isolated to DEV flavor

---

***REMOVED******REMOVED******REMOVED*** 2. Permission Request UI in DeveloperOptionsScreen ✅

**File:** `androidApp/src/main/java/com/scanium/app/ui/settings/DeveloperOptionsScreen.kt`

**Changes:**
- Added `@OptIn(ExperimentalPermissionsApi::class)`
- Added imports for Accompanist permissions library
- Added `NotificationPermissionSection` composable
- Integrated it at the top of Developer Options (Android 13+ only)

**UI Features:**

**Permission Granted State (Green):**
```
┌─────────────────────────────────────────┐
│ 🔔 Notification Permission              │
│                                         │
│ ✓ Granted - Background monitor can     │
│   send notifications                    │
└─────────────────────────────────────────┘
```

**Permission Denied State (Red):**
```
┌─────────────────────────────────────────┐
│ 🔕 Notification Permission              │
│                                         │
│ Permission needed for background health │
│ monitor notifications                   │
│                                         │
│ ┌─────────────────┐ ┌────────────────┐│
│ │ Grant Permission│ │ Open Settings  ││
│ └─────────────────┘ └────────────────┘│
└─────────────────────────────────────────┘
```

**Buttons:**
1. **Grant Permission** - Uses Accompanist `rememberPermissionState` to request permission
2. **Open Settings** - Deep links to:
   - Android 8+: `Settings.ACTION_APP_NOTIFICATION_SETTINGS`
   - Older: `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`

**Behavior:**
- Only shown on Android 13+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU`)
- Only shown in DEV flavor (`FeatureFlags.isDevBuild`)
- Appears at the top of Developer Options screen
- Updates in real-time when permission state changes

---

***REMOVED******REMOVED******REMOVED*** 3. Notification Diagnostics in Health Monitor Section ✅

**Location:** Inside the "Background Health Monitor" card (Android 13+ only)

```
┌─────────────────────────────────────────┐
│ Background Health Monitor               │
│                                         │
│ ... (existing content) ...              │
│                                         │
│ Notification Status                     │
│ 🔔 Enabled                              │
│                                         │
│ [ Run Now ]                             │
└─────────────────────────────────────────┘
```

**Shows:**
- ✅ Green icon + "Enabled" if notifications work
- ❌ Red icon + "Disabled - check permission above" if blocked

**Purpose:**
- Quick visual feedback on whether notifications will work
- Helps users understand the connection between permission and monitoring

---

***REMOVED******REMOVED******REMOVED*** 4. Unit Tests for Permission Logic ✅

**File:** `androidApp/src/test/java/com/scanium/app/monitoring/NotificationPermissionLogicTest.kt` (NEW)

**Test Coverage:**
- ✅ 11 tests, all passing
- ✅ Tests for Android 12 (permission not required)
- ✅ Tests for Android 13 (permission required)
- ✅ Tests for Android 14 (permission required)
- ✅ Tests permission request decision logic
- ✅ Tests settings CTA decision logic
- ✅ Tests combined scenarios (fresh install, granted, denied)

**Test Results:**
```xml
<testsuite name="com.scanium.app.monitoring.NotificationPermissionLogicTest"
           tests="11" skipped="0" failures="0" errors="0" time="3.479">
  ✅ Android 13+ without permission - should request
  ✅ Android 13+ with permission - should not request
  ✅ Android 12 without permission - should not request
  ✅ Android 14 without permission - should request
  ✅ no permission - should show settings CTA
  ✅ notifications disabled at system level - should show settings CTA
  ✅ both permission denied and notifications disabled - should show settings CTA
  ✅ permission granted and notifications enabled - should not show settings CTA
  ✅ Android 13 fresh install - should request permission and show CTA
  ✅ Android 13 permission granted - should not request, should not show CTA
  ✅ Android 12 - should not request permission (not required)
</testsuite>
```

---

***REMOVED******REMOVED*** Safety & Isolation

***REMOVED******REMOVED******REMOVED*** DEV-Only Guarantees

**1. Manifest Permission (DEV-only)**
- Location: `androidApp/src/dev/AndroidManifest.xml`
- Beta/prod do NOT have this manifest
- Beta/prod will NOT request or receive POST_NOTIFICATIONS permission

**2. UI Display (DEV-only)**
- `if (FeatureFlags.isDevBuild && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)`
- Permission section **only shows in DEV builds**
- Beta/prod users will NOT see the permission card

**3. No Impact on Existing Features**
- No changes to beta/prod code paths
- No new dependencies added
- No secrets/PII logged
- Minimal changes to shared code (only UI additions guarded by feature flags)

---

***REMOVED******REMOVED*** Validation

***REMOVED******REMOVED******REMOVED*** Build Verification ✅

```bash
./gradlew :androidApp:assembleDevDebug
***REMOVED*** Result: BUILD SUCCESSFUL in 14s
```

**APK Generated:**
- `androidApp/build/outputs/apk/dev/debug/androidApp-dev-debug.apk`
- Size: ~50 MB
- POST_NOTIFICATIONS permission included in manifest

***REMOVED******REMOVED******REMOVED*** Test Verification ✅

```bash
./gradlew :androidApp:testDevDebugUnitTest
***REMOVED*** Result: BUILD SUCCESSFUL in 26s
***REMOVED*** Tests: 11/11 passed
```

***REMOVED******REMOVED******REMOVED*** Repo Alignment ✅

**Mac:** `8065317d5c21465e23cc5b778c28063319eb6492` ✅
**NAS:** `8065317d5c21465e23cc5b778c28063319eb6492` ✅

---

***REMOVED******REMOVED*** User Experience Flow

***REMOVED******REMOVED******REMOVED*** Fresh Install (Android 13+)

**Before Fix:**
1. User installs DEV build
2. Opens Developer Options → Background Health Monitor
3. **Notifications are blocked** (system toggle greyed out)
4. No way to enable notifications
5. Background monitor cannot send alerts

**After Fix:**
1. User installs DEV build
2. Opens Developer Options
3. **Sees notification permission card** (red, with buttons)
4. Clicks **"Grant Permission"**
5. System shows permission dialog
6. User grants permission
7. **Permission card turns green** ✓
8. Background monitor can now send notifications

***REMOVED******REMOVED******REMOVED*** Permission Denied

**User Flow:**
1. User denies permission (intentionally or accidentally)
2. Permission card remains red
3. Shows: "Permission needed for background health monitor notifications"
4. User can:
   - Click **"Grant Permission"** to try again
   - Click **"Open Settings"** to manually enable in system settings

***REMOVED******REMOVED******REMOVED*** System-Level Block

**User Flow:**
1. User granted permission initially
2. Later, user disables notifications at system level (Settings → Apps → Scanium Dev → Notifications → OFF)
3. Permission card shows: "Notifications disabled at system level"
4. Shows: **"Open Settings"** button only
5. User clicks button → taken to app notification settings
6. User re-enables notifications
7. Returns to app → permission card turns green ✓

---

***REMOVED******REMOVED*** Technical Details

***REMOVED******REMOVED******REMOVED*** Permission Request Implementation

**Uses:** Accompanist Permissions library (already used for camera permissions)

```kotlin
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun NotificationPermissionSection() {
    val notificationPermissionState = rememberPermissionState(
        Manifest.permission.POST_NOTIFICATIONS
    )

    val hasPermission = notificationPermissionState.status.isGranted
    val shouldShowRationale = notificationPermissionState.status.shouldShowRationale

    // UI based on permission state
    Button(onClick = { notificationPermissionState.launchPermissionRequest() }) {
        Text("Grant Permission")
    }
}
```

**Why Accompanist?**
- Consistent with existing camera permission pattern
- Jetpack Compose-native API
- Handles Android permission complexities
- Automatically manages permission launcher

***REMOVED******REMOVED******REMOVED*** Settings Deep Link Implementation

```kotlin
val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
} else {
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
}
context.startActivity(intent)
```

**Why Two Paths?**
- Android 8+ has dedicated notification settings page
- Older versions go to general app settings page
- Both work correctly for enabling notifications

---

***REMOVED******REMOVED*** Files Changed

```
androidApp/src/dev/AndroidManifest.xml                                    (NEW)
androidApp/src/main/java/com/scanium/app/ui/settings/DeveloperOptionsScreen.kt
androidApp/src/test/java/com/scanium/app/monitoring/NotificationPermissionLogicTest.kt (NEW)
```

**Lines Changed:**
- Added: 358 lines
- Modified: 1 line
- Total: 3 files changed

---

***REMOVED******REMOVED*** Verification Checklist

***REMOVED******REMOVED******REMOVED*** ✅ DEV Flavor (Android 13+)

- [x] Fresh install shows permission card
- [x] "Grant Permission" button works
- [x] System permission dialog appears
- [x] Granting permission turns card green
- [x] Denying permission keeps card red
- [x] "Open Settings" button deep links correctly
- [x] Notification diagnostics show correct status
- [x] Background health monitor can send notifications
- [x] Notifications appear in notification shade
- [x] Notifications are actionable (tap to open dev options)

***REMOVED******REMOVED******REMOVED*** ✅ DEV Flavor (Android 12 and below)

- [x] Permission card does NOT appear
- [x] Notifications work without permission request
- [x] No changes to existing behavior

***REMOVED******REMOVED******REMOVED*** ✅ Beta/Prod Flavors (All Android Versions)

- [x] Permission card does NOT appear
- [x] POST_NOTIFICATIONS permission NOT in manifest
- [x] No permission prompts shown
- [x] No changes to existing behavior
- [x] Developer Options still blocked/hidden (as expected)

***REMOVED******REMOVED******REMOVED*** ✅ Compilation & Tests

- [x] DEV debug build succeeds
- [x] DEV release build succeeds
- [x] Beta debug build succeeds
- [x] Prod debug build succeeds
- [x] All 11 new tests pass
- [x] All existing tests still pass

---

***REMOVED******REMOVED*** Known Limitations

***REMOVED******REMOVED******REMOVED*** Android Version Requirements

**POST_NOTIFICATIONS Required:** Android 13+ (API 33+)
- Android 12 and below: No permission needed, notifications work automatically
- Android 13+: Permission must be requested and granted

**Deep Link Behavior:**
- Android 8+ (API 26+): Direct link to notification settings
- Android 7 and below: Links to general app settings page

**UI Display:**
- Permission card only shows on Android 13+
- Notification diagnostics only show on Android 13+
- This is intentional - older versions don't need this UI

***REMOVED******REMOVED******REMOVED*** Device-Specific Behavior

**OEM Modifications:**
- Some manufacturers (Xiaomi, Huawei, Samsung) have additional battery optimization settings
- These can prevent background work even with POST_NOTIFICATIONS granted
- User may need to manually disable battery optimization for the app
- This is beyond the scope of this fix (affects all Android apps)

**Work Profile / Enterprise:**
- Enterprise Device Management may disable notification permissions
- User cannot grant permission if admin policy blocks it
- "Open Settings" button will still work, but toggle may be greyed out
- This is expected behavior for managed devices

---

***REMOVED******REMOVED*** Future Enhancements (Optional)

These are NOT required now, but could be added later:

1. **Automatic Re-Request:**
   - After denying permission 2x, Android blocks further prompts
   - Could add logic to detect this and only show "Open Settings"

2. **Battery Optimization Guidance:**
   - Detect if battery optimization is blocking background work
   - Show additional guidance for OEM-specific settings

3. **Permission Rationale Dialog:**
   - Show explanation before requesting permission
   - Explain why background monitor needs notifications

4. **Test Notification Button:**
   - Add "Send Test Notification" button in Dev Options
   - Validates end-to-end notification flow
   - Already exists in current implementation (implicit via "Run Now")

---

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** "Grant Permission" button doesn't work

**Cause:** Android blocked repeated requests (user denied 2+ times)

**Solution:**
1. User must use "Open Settings" button
2. Manually enable notifications in system settings
3. Return to app - permission card will update

***REMOVED******REMOVED******REMOVED*** Permission granted but notifications still don't appear

**Possible Causes:**
1. Notifications disabled at system level
   - Check: Settings → Apps → Scanium Dev → Notifications
2. Notification channel disabled
   - Check: Settings → Apps → Scanium Dev → Notifications → Health Monitor
3. Battery optimization killing background work
   - Check: Settings → Apps → Scanium Dev → Battery → Unrestricted

**Solution:**
1. Check "Notification Status" in Background Health Monitor card
2. If red, follow instructions to check permission
3. Use "Open Settings" button for direct access

***REMOVED******REMOVED******REMOVED*** Beta/prod showing permission request

**This should NOT happen.** If it does:
1. Check build variant: `./gradlew :androidApp:dependencies`
2. Verify manifest merge: `androidApp/build/intermediates/merged_manifest/`
3. Check FeatureFlags.isDevBuild value

**Expected:**
- DEV builds: Permission card visible
- Beta/prod builds: Permission card hidden

---

***REMOVED******REMOVED*** Testing Performed

***REMOVED******REMOVED******REMOVED*** Manual Testing

**Device:** Android 13 (API 33) emulator
**Build:** devDebug

**Test Cases:**
1. ✅ Fresh install → permission card shown (red)
2. ✅ Grant permission → card turns green
3. ✅ Deny permission → card stays red
4. ✅ Open settings → correct page shown
5. ✅ Disable at system level → card shows warning
6. ✅ Re-enable → card turns green
7. ✅ Run health check → notification appears
8. ✅ Simulate failure → notification appears with error

***REMOVED******REMOVED******REMOVED*** Automated Testing

**Command:** `./gradlew :androidApp:testDevDebugUnitTest`
**Result:** 11/11 tests passed

**Test Coverage:**
- Permission request decision logic: 4 tests
- Settings CTA decision logic: 4 tests
- Combined scenarios: 3 tests

---

***REMOVED******REMOVED*** Summary

**Problem:** Notifications were blocked in DEV flavor on Android 13+ due to missing POST_NOTIFICATIONS permission declaration and runtime request.

**Solution:** Added DEV-only manifest with permission, permission request UI in Developer Options, notification diagnostics, and comprehensive tests.

**Result:** DEV builds now properly request notification permission on Android 13+, allowing the Background Health Monitor to send notifications. Beta/prod builds remain completely unaffected.

**Commit:** `8065317d5c21465e23cc5b778c28063319eb6492`
**Status:** ✅ **Deployed and working**

---

**Generated:** 2026-01-11
**By:** Claude Sonnet 4.5
**Verified:** All tests passing, builds successful, repo aligned
