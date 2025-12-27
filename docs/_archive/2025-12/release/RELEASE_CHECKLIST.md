***REMOVED*** Release Sanity Checklist

This checklist must be completed before publishing any release to Google Play.

***REMOVED******REMOVED*** Pre-Release Verification

***REMOVED******REMOVED******REMOVED*** 1. Feature Flag Defaults

| Flag | Expected Default | Verified |
|------|------------------|----------|
| Cloud Classification | ON (true) | [ ] |
| Assistant Features | OFF (false) | [ ] |
| Send Images to Assistant | OFF (false) | [ ] |
| Voice Mode | OFF (false) | [ ] |
| Speak Answers Aloud | OFF (false) | [ ] |
| Share Diagnostics | OFF (false) | [ ] |
| Privacy Safe Mode | OFF (false) - derived state | [ ] |

**Verification:**
```bash
***REMOVED*** Fresh install, check DataStore defaults
adb shell "run-as com.scanium.app cat /data/data/com.scanium.app/files/datastore/settings_preferences.preferences_pb" | xxd
***REMOVED*** Or check via Settings UI on fresh install
```

---

***REMOVED******REMOVED******REMOVED*** 2. Endpoints Configured

| Endpoint | Environment Variable | Verified |
|----------|----------------------|----------|
| API Base URL | `SCANIUM_API_BASE_URL` | [ ] |
| API Key | `SCANIUM_API_KEY` | [ ] |
| Sentry DSN | `SCANIUM_SENTRY_DSN` | [ ] |
| OTLP Endpoint | `SCANIUM_OTLP_ENDPOINT` (optional) | [ ] |

**Verification:**
```bash
***REMOVED*** Check BuildConfig values in debug build
adb shell "run-as com.scanium.app cat /data/data/com.scanium.app/shared_prefs/*.xml"
***REMOVED*** Or inspect APK
aapt dump strings app-release.apk | grep -i "scanium\|api\|sentry"
```

**Note:** API keys should NOT be in version control. Verify they come from:
- `local.properties` (local builds)
- CI/CD secrets (release builds)

---

***REMOVED******REMOVED******REMOVED*** 3. Privacy Screens Present

| Screen | Route | Accessible From | Verified |
|--------|-------|-----------------|----------|
| Privacy Policy | `privacy` | Settings → Legal → Privacy Policy | [ ] |
| Terms of Service | `terms` | Settings → Legal → Terms of Service | [ ] |
| Data Usage & Transparency | `data_usage` | Settings → Privacy & Data → Data Usage | [ ] |
| About Scanium | `about` | Settings → Legal → About Scanium | [ ] |

**Verification:**
1. Open Settings
2. Navigate to each screen
3. Confirm content loads correctly

---

***REMOVED******REMOVED******REMOVED*** 4. Crash Reporting Opt-in Verified

| Check | Expected | Verified |
|-------|----------|----------|
| Default state | OFF | [ ] |
| Toggle visible | Settings → Share Diagnostics | [ ] |
| Sentry silent when OFF | No events sent | [ ] |
| Sentry active when ON | Test event captured | [ ] |

**Verification:**
```bash
***REMOVED*** Toggle OFF, trigger test crash, verify nothing sent to Sentry
adb logcat -s Sentry AndroidCrashPortAdapter | grep -i "capture\|send"

***REMOVED*** Toggle ON, trigger test crash via Developer settings
adb logcat -s Sentry AndroidCrashPortAdapter | grep -i "capture\|attached"
```

---

***REMOVED******REMOVED******REMOVED*** 5. Voice Permissions and Indicators

| Check | Expected | Verified |
|-------|----------|----------|
| Mic permission | Requested only on user action | [ ] |
| Recording indicator | Visible during LISTENING state | [ ] |
| Recording stops on background | Immediate stop | [ ] |
| No always-on listening | Mic only active on button press | [ ] |

**Verification:**
1. Enable Voice Mode in Settings
2. Open Assistant screen
3. Tap mic button → Confirm permission dialog (first time)
4. While listening, press Home → Verify recording stops
5. Background the app → Verify no recording continues

```bash
***REMOVED*** Monitor voice state
adb logcat -s AssistantVoice | grep -E "listening|stopped|shutdown"
```

---

***REMOVED******REMOVED******REMOVED*** 6. Assistant Image Toggle

| Check | Expected | Verified |
|-------|----------|----------|
| Default state | OFF | [ ] |
| Images NOT sent when OFF | No thumbnails in request | [ ] |
| Images sent when ON | Thumbnails included | [ ] |
| Toggle gated by assistant | Only visible when assistant enabled | [ ] |

**Verification:**
```bash
***REMOVED*** With toggle OFF, send assistant message, verify no images
adb logcat -s AssistantViewModel | grep -i "image\|thumbnail"

***REMOVED*** With toggle ON, verify images included
adb logcat -s AssistantRepository | grep -i "image"
```

---

***REMOVED******REMOVED******REMOVED*** 7. Privacy Safe Mode

| Check | Expected | Verified |
|-------|----------|----------|
| One-tap activation | Disables cloud/images/diagnostics | [ ] |
| UI indicator | Shows "Active - no data leaves device" | [ ] |
| Reversal works | Can re-enable cloud features | [ ] |

**Verification:**
1. Enable Privacy Safe Mode
2. Verify Cloud Classification, Assistant Images, Share Diagnostics are all OFF
3. Turn off Privacy Safe Mode
4. Verify Cloud Classification turns back ON

---

***REMOVED******REMOVED******REMOVED*** 8. Build Commands Pass

```bash
***REMOVED*** Unit tests
./gradlew test

***REMOVED*** Debug build
./gradlew assembleDebug

***REMOVED*** Release bundle (unsigned OK if signing not configured)
./gradlew bundleRelease
```

| Command | Status | Notes |
|---------|--------|-------|
| `./gradlew test` | [ ] | All tests pass |
| `./gradlew assembleDebug` | [ ] | APK builds successfully |
| `./gradlew bundleRelease` | [ ] | AAB builds successfully |

---

***REMOVED******REMOVED******REMOVED*** 9. Manual App Walkthrough

| Scenario | Verified |
|----------|----------|
| Fresh install → Settings accessible | [ ] |
| Scan items → On-device detection works | [ ] |
| Enable Cloud → Enhanced classification works | [ ] |
| Enable Assistant → Can send messages | [ ] |
| Enable Voice → Can use mic input | [ ] |
| Disable all cloud features → App still functional | [ ] |
| Privacy Safe Mode → All data local | [ ] |

---

***REMOVED******REMOVED******REMOVED*** 10. Release Build Logging Minimal

| Check | Expected | Verified |
|-------|----------|----------|
| No raw prompts logged | Only metadata logged | [ ] |
| No OCR text logged | Only detection counts | [ ] |
| No audio logged | Only state transitions | [ ] |
| No images logged | Only size/format info | [ ] |
| API keys not logged | Keys masked or omitted | [ ] |

**Verification:**
```bash
***REMOVED*** Build release variant and check logcat
adb logcat | grep -iE "api.?key\|secret\|password\|token"
***REMOVED*** Should return nothing sensitive
```

---

***REMOVED******REMOVED*** Sign-Off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Developer | | | |
| QA | | | |
| Product Owner | | | |

---

***REMOVED******REMOVED*** Notes

_Add any release-specific notes here._

---

***REMOVED******REMOVED*** Changelog

| Date | Version | Notes |
|------|---------|-------|
| 2025-12-25 | 1.0 | Initial checklist for PR7 |
