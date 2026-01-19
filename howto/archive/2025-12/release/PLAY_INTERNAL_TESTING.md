***REMOVED*** Google Play Internal Testing Guide

This guide describes how to prepare and deploy Scanium to the Google Play Store for internal
testing.

***REMOVED******REMOVED*** 1. Prerequisites

- Google Play Console account with developer access to the Scanium project.
- Release signing key (kept secure and NOT in version control).
- `local.properties` configured with signing secrets (for local release builds).

***REMOVED******REMOVED*** 2. Signing Configuration

To build a signed release locally, add the following to your `local.properties`:

```properties
scanium.keystore.file=/path/to/your/release.keystore
scanium.keystore.password=your_keystore_password
scanium.key.alias=your_key_alias
scanium.key.password=your_key_password
```

**WARNING:** Never commit your keystore file or passwords to the repository.

For CI/CD, use environment variables:

- `SCANIUM_KEYSTORE_FILE`
- `SCANIUM_KEYSTORE_PASSWORD`
- `SCANIUM_KEY_ALIAS`
- `SCANIUM_KEY_PASSWORD`

***REMOVED******REMOVED*** 3. Build Commands

***REMOVED******REMOVED******REMOVED*** Run Tests First

```bash
./gradlew test
```

***REMOVED******REMOVED******REMOVED*** Debug Build (for local testing)

```bash
./gradlew assembleDebug
```

Output: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`

***REMOVED******REMOVED******REMOVED*** Release Bundle (for Play Store)

```bash
./gradlew bundleRelease
```

Output: `androidApp/build/outputs/bundle/release/androidApp-release.aab`

***REMOVED******REMOVED*** 4. Versioning

Before building, ensure the version code and name are bumped if necessary.
See [VERSIONING.md](VERSIONING.md) for the strategy.

You can set versions via environment variables for CI:

- `SCANIUM_VERSION_CODE`
- `SCANIUM_VERSION_NAME`

***REMOVED******REMOVED*** 5. Deployment to Play Console

1. Log in to [Google Play Console](https://play.google.com/console).
2. Select **Scanium**.
3. Go to **Testing > Internal testing**.
4. Create a new release.
5. Upload the `.aab` file.
6. Provide release notes.
7. Review and roll out to internal testing.

***REMOVED******REMOVED*** 6. Managing Testers

1. In the Internal testing section, go to the **Testers** tab.
2. Create or select a mailing list of testers.
3. Share the **Join on the web** or **Join on Android** link with your testers.

***REMOVED******REMOVED*** 7. Smoke Test Checklist

After installing from the Play Store, verify the following:

***REMOVED******REMOVED******REMOVED*** Core Functionality

- [ ] **Scan Mode:** Continuous scanning works smoothly
- [ ] **Object Detection:** Items are detected and aggregated
- [ ] **Cloud Classification:** If enabled, enhanced labels appear after stabilization
- [ ] **Drafting:** Can create a listing draft from a scanned item
- [ ] **Copy/Share:** Posting Assist works and copies data to clipboard

***REMOVED******REMOVED******REMOVED*** Assistant & Voice (PR6+)

- [ ] **Assistant Toggle:** Can enable/disable in Settings → Features
- [ ] **Assistant Chat:** Can send messages and receive responses
- [ ] **Assistant Images Toggle:** Can enable/disable image sending
- [ ] **Voice Mode Toggle:** Can enable/disable in Settings
- [ ] **Mic Button:** Appears only when Voice Mode enabled
- [ ] **Voice Recording:** Mic indicator visible during listening
- [ ] **Voice Stop:** Recording stops when pressing stop or backgrounding app
- [ ] **TTS Playback:** Responses spoken aloud when Speak Answers enabled
- [ ] **No Background Mic:** Verify mic doesn't record when app backgrounded

***REMOVED******REMOVED******REMOVED*** Privacy & Data

- [ ] **Privacy Policy:** Accessible via Settings → Legal
- [ ] **Terms of Service:** Accessible via Settings → Legal
- [ ] **Data Usage:** Accessible via Settings → Privacy & Data
- [ ] **Privacy Safe Mode:** Can enable/disable all cloud sharing with one tap
- [ ] **Reset Privacy:** Can restore privacy settings to defaults
- [ ] **Crash Reporting:** Opt-in toggle works (default OFF)

***REMOVED******REMOVED******REMOVED*** Network Failure Handling

- [ ] **Airplane Mode + Cloud:** Shows clear error, falls back gracefully
- [ ] **Airplane Mode + Assistant:** Shows clear error message
- [ ] **API Timeout:** Shows retry option, no infinite spinner

***REMOVED******REMOVED*** 8. Assistant + Voice Specific Tests

***REMOVED******REMOVED******REMOVED*** Toggle Behavior

| Test                                   | Expected Result                       | Pass |
|----------------------------------------|---------------------------------------|------|
| Assistant OFF, send message            | Should not work or show upsell        | [ ]  |
| Assistant ON, Images OFF, send message | Message sent, no images in request    | [ ]  |
| Assistant ON, Images ON, send message  | Message sent with thumbnails          | [ ]  |
| Voice Mode OFF                         | Mic button not visible                | [ ]  |
| Voice Mode ON                          | Mic button visible in assistant input | [ ]  |

***REMOVED******REMOVED******REMOVED*** Microphone Indicator

| Test                            | Expected Result                                   | Pass |
|---------------------------------|---------------------------------------------------|------|
| Tap mic button                  | Indicator shows "Listening..."                    | [ ]  |
| Speak, wait for result          | Indicator shows "Transcribing..." then disappears | [ ]  |
| Tap stop during listening       | Recording stops immediately                       | [ ]  |
| Background app during listening | Recording stops, no crash                         | [ ]  |

***REMOVED******REMOVED******REMOVED*** No Background Mic

1. Enable Voice Mode
2. Open Assistant screen
3. Start listening (tap mic)
4. While still listening, press Home
5. **Expected:** Recording stops, no continued mic access
6. Return to app
7. **Expected:** State is IDLE, not LISTENING

***REMOVED******REMOVED******REMOVED*** TTS Playback

| Test                                | Expected Result         | Pass |
|-------------------------------------|-------------------------|------|
| Speak Answers OFF, receive response | No audio playback       | [ ]  |
| Speak Answers ON, receive response  | Response spoken aloud   | [ ]  |
| Tap stop during speaking            | Audio stops immediately | [ ]  |

***REMOVED******REMOVED*** 9. Logcat Filters for Verification

```bash
***REMOVED*** Cloud classification on/off
adb logcat -s CloudClassifier FeatureFlagRepository | grep -iE "cloud|enabled|disabled"

***REMOVED*** Assistant image sending on/off
adb logcat -s AssistantViewModel AssistantRepository | grep -iE "image|thumbnail"

***REMOVED*** Voice listening start/stop
adb logcat -s AssistantVoice | grep -iE "listening|stopped|shutdown|dispose"

***REMOVED*** Crash reporting opt-in
adb logcat -s AndroidCrashPortAdapter Sentry | grep -iE "capture|diagnostics"

***REMOVED*** Privacy safe mode
adb logcat | grep -iE "privacy.?safe|enablePrivacySafeMode"
```

***REMOVED******REMOVED*** 10. Pre-Release Checklist

Complete the full [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) before publishing.
