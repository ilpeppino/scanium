***REMOVED*** Privacy Technical Notes

This document describes the technical implementation of privacy controls in Scanium, including where toggles are checked in code, where uploads occur, and what redaction is applied to logs.

***REMOVED******REMOVED*** Table of Contents

1. [Toggle Check Locations](***REMOVED***toggle-check-locations)
2. [Upload Endpoints](***REMOVED***upload-endpoints)
3. [Log Redaction](***REMOVED***log-redaction)
4. [Voice Privacy Safeguards](***REMOVED***voice-privacy-safeguards)
5. [Assistant Privacy Safeguards](***REMOVED***assistant-privacy-safeguards)
6. [Privacy Safe Mode](***REMOVED***privacy-safe-mode)

---

***REMOVED******REMOVED*** Toggle Check Locations

***REMOVED******REMOVED******REMOVED*** Cloud Classification Toggle

**User preference storage:**
- `androidApp/.../data/SettingsRepository.kt:27` - `ALLOW_CLOUD_CLASSIFICATION_KEY`
- Default: `true`

**Where checked:**
- `androidApp/.../data/AndroidFeatureFlagRepository.kt` - Combines user pref + remote config + entitlements
- `androidApp/.../ml/classification/ClassificationOrchestrator.kt` - Decides cloud vs on-device
- `androidApp/.../settings/ClassificationModeViewModel.kt` - UI state binding

**Flow:**
```
User toggle → SettingsRepository → FeatureFlagRepository → ClassificationOrchestrator → CloudClassifier
```

---

***REMOVED******REMOVED******REMOVED*** Assistant Toggle

**User preference storage:**
- `androidApp/.../data/SettingsRepository.kt:28` - `ALLOW_ASSISTANT_KEY`
- Default: `false`

**Where checked:**
- `androidApp/.../data/AndroidFeatureFlagRepository.kt` - Combines with entitlements (Pro+ only)
- `androidApp/.../ui/settings/SettingsScreen.kt:208-210` - Gated by `UserEdition`
- `androidApp/.../selling/assistant/AssistantScreen.kt` - ViewModel instantiation

---

***REMOVED******REMOVED******REMOVED*** Assistant Images Toggle

**User preference storage:**
- `androidApp/.../data/SettingsRepository.kt:34` - `ALLOW_ASSISTANT_IMAGES_KEY`
- Default: `false` (privacy-first)

**Where checked:**
- `androidApp/.../selling/assistant/AssistantViewModel.kt` - When building prompt request
- `androidApp/.../ui/settings/SettingsScreen.kt:238-245` - UI toggle (requires assistant enabled)

**Key code path:**
```kotlin
// In AssistantViewModel - images only sent if toggle is ON
val allowImages = settingsRepository.allowAssistantImagesFlow.first()
if (allowImages) {
    // Include thumbnails in request
}
```

---

***REMOVED******REMOVED******REMOVED*** Voice Mode Toggle

**User preference storage:**
- `androidApp/.../data/SettingsRepository.kt:44` - `VOICE_MODE_ENABLED_KEY`
- Default: `false`

**Where checked:**
- `androidApp/.../selling/assistant/AssistantScreen.kt:123` - Controls mic button visibility
- Mic button only rendered when `voiceModeEnabled == true`

---

***REMOVED******REMOVED******REMOVED*** Speak Answers Toggle

**User preference storage:**
- `androidApp/.../data/SettingsRepository.kt:45` - `SPEAK_ANSWERS_KEY`
- Default: `false`

**Where checked:**
- `androidApp/.../selling/assistant/AssistantScreen.kt:124` - Controls TTS initialization
- `androidApp/.../selling/assistant/AssistantScreen.kt:159-167` - Auto-speak logic

---

***REMOVED******REMOVED******REMOVED*** Share Diagnostics Toggle

**User preference storage:**
- `androidApp/.../data/SettingsRepository.kt:29` - `SHARE_DIAGNOSTICS_KEY`
- Default: `false`

**Where checked:**
- `androidApp/.../ScaniumApplication.kt` - Sentry initialization
- Crash reports only sent when this is `true`

---

***REMOVED******REMOVED*** Upload Endpoints

***REMOVED******REMOVED******REMOVED*** Cloud Classification

**Endpoint:** `POST {SCANIUM_API_BASE_URL}/v1/classify`

**File:** `androidApp/.../ml/classification/CloudClassifier.kt:115`

**Request format:**
```
Content-Type: multipart/form-data
- image: JPEG file (cropped item thumbnail)
- domainPackId: string

Headers:
- X-API-Key: (from BuildConfig)
- X-Scanium-Correlation-Id: (session tracking)
- X-Client: Scanium-Android
- X-App-Version: (BuildConfig.VERSION_NAME)
- X-Signature: HMAC-SHA256 (SEC-004)
- X-Timestamp: Unix timestamp
```

**Privacy in upload:**
- Only cropped thumbnail, not full frame
- JPEG re-compression strips EXIF metadata
- No location data included

---

***REMOVED******REMOVED******REMOVED*** AI Assistant Chat

**Endpoint:** `POST {SCANIUM_API_BASE_URL}/v1/assist/chat`

**File:** `androidApp/.../assistant/AssistantRepository.kt:116`

**Request format:**
```json
{
  "question": "user question text",
  "context": {
    "items": [...],
    "draft": {...}
  },
  "prefs": {
    "language": "EN",
    "tone": "NEUTRAL",
    ...
  },
  "images": [...] // Only if allowAssistantImages is true
}

Headers:
- X-API-Key: (from BuildConfig)
- X-Scanium-Correlation-Id: (session tracking)
- X-Scanium-Device-Id: SHA-256(deviceId)
- X-Client: Scanium-Android
- X-App-Version: (BuildConfig.VERSION_NAME)
- X-Signature: HMAC-SHA256
- X-Timestamp: Unix timestamp
```

**Privacy in upload:**
- Device ID is hashed (SHA-256) before sending
- Images only included if explicitly enabled
- Raw prompts NOT logged on server

---

***REMOVED******REMOVED******REMOVED*** Crash Reports (Sentry)

**Endpoint:** Sentry SDK handles automatically

**File:** `androidApp/.../crash/AndroidCrashPortAdapter.kt`

**What is sent:**
- Stack trace
- Device info (model, OS)
- App version
- Breadcrumbs (recent actions)
- Diagnostics bundle (if available)

**Privacy in upload:**
- PII redaction via Sentry SDK
- No raw prompts/OCR/images attached
- Diagnostics bundle capped at 128KB

---

***REMOVED******REMOVED*** Log Redaction

***REMOVED******REMOVED******REMOVED*** ScaniumLog Wrapper

**File:** `androidApp/.../logging/ScaniumLog.kt`

All logging goes through `ScaniumLog` which:
- Respects release/debug build differences
- Does NOT log:
  - Raw user prompts
  - OCR text content
  - Audio transcripts
  - API keys or tokens
  - Full image data

***REMOVED******REMOVED******REMOVED*** What IS Logged (safely)

- Correlation IDs (for request tracing)
- Classification results (category, confidence)
- Error codes and messages
- State transitions (e.g., "listening started")
- Performance metrics (timing)

***REMOVED******REMOVED******REMOVED*** What is NOT Logged

- `AssistantPromptRequest.question` - Never logged
- `VoiceResult.transcript` - Only logged as "[transcript received]"
- Image bytes - Never logged
- API keys - Masked if accidentally included

***REMOVED******REMOVED******REMOVED*** Log Filtering for Verification

```bash
***REMOVED*** Verify assistant image toggle behavior
adb logcat -s AssistantViewModel | grep -i image

***REMOVED*** Verify cloud classification toggle behavior
adb logcat -s CloudClassifier | grep -i "classifying\|disabled\|not configured"

***REMOVED*** Verify voice listening start/stop
adb logcat -s AssistantVoice | grep -i "listening\|stopped\|shutdown"

***REMOVED*** Verify crash opt-in
adb logcat -s AndroidCrashPortAdapter | grep -i "diagnostics\|attached"
```

---

***REMOVED******REMOVED*** Voice Privacy Safeguards

***REMOVED******REMOVED******REMOVED*** No Always-On Listening

**Implementation:** `androidApp/.../selling/assistant/AssistantVoiceController.kt:150-166`

```kotlin
fun startListening(onResult: (VoiceResult) -> Unit) {
    // Only starts when explicitly called (from mic button press)
    // Never auto-invoked or always-on
}
```

***REMOVED******REMOVED******REMOVED*** Explicit User Gesture Required

**Implementation:** `androidApp/.../selling/assistant/AssistantScreen.kt:403-419`

```kotlin
IconButton(onClick = {
    // Recording only starts on explicit button press
    if (hasPermission) {
        voiceController.startListening(handleVoiceResult)
    } else {
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
})
```

***REMOVED******REMOVED******REMOVED*** Recording Stops on Lifecycle Events

**Implementation:** `androidApp/.../selling/assistant/AssistantScreen.kt:146-148`

```kotlin
DisposableEffect(Unit) {
    onDispose { voiceController.shutdown() }
}
```

The `shutdown()` method:
- Stops any active listening
- Stops any TTS playback
- Destroys the SpeechRecognizer
- Releases TTS resources

***REMOVED******REMOVED******REMOVED*** Visual Indicator During Recording

**Implementation:** `androidApp/.../selling/assistant/AssistantScreen.kt:346-352` and `759-842`

```kotlin
// Indicator shown when recording
if (voiceState == VoiceState.LISTENING || voiceState == VoiceState.TRANSCRIBING) {
    VoiceListeningIndicator(
        state = voiceState,
        partialTranscript = partialTranscript,
        onStop = { voiceController.stopListening() }
    )
}
```

***REMOVED******REMOVED******REMOVED*** No Audio Storage

**Implementation:** `androidApp/.../selling/assistant/AssistantVoiceController.kt:184-185`

```kotlin
override fun onBufferReceived(buffer: ByteArray?) {
    // Raw audio buffer - we don't store this (privacy)
}
```

---

***REMOVED******REMOVED*** Assistant Privacy Safeguards

***REMOVED******REMOVED******REMOVED*** Images Only Sent When Enabled

The assistant must check `allowAssistantImagesFlow` before including thumbnails in requests.

**Toggle location:** Settings → Send Images to Assistant

***REMOVED******REMOVED******REMOVED*** Cloud Misconfiguration Handling

**Implementation:** `androidApp/.../assistant/AssistantRepository.kt:86-101`

```kotlin
val baseUrl = BuildConfig.SCANIUM_API_BASE_URL.takeIf { it.isNotBlank() }
    ?: return@withContext Result.failure(
        AssistantException(
            errorCode = "CONFIG_ERROR",
            userMessage = "Assistant is not configured. Please check app settings."
        )
    )
```

Clear error messages are shown in UI instead of silent failures.

---

***REMOVED******REMOVED*** Privacy Safe Mode

***REMOVED******REMOVED******REMOVED*** Implementation

**File:** `androidApp/.../data/SettingsRepository.kt:302-309`

```kotlin
suspend fun enablePrivacySafeMode() {
    context.settingsDataStore.edit { preferences ->
        preferences[ALLOW_CLOUD_CLASSIFICATION_KEY] = false
        preferences[ALLOW_ASSISTANT_IMAGES_KEY] = false
        preferences[SHARE_DIAGNOSTICS_KEY] = false
    }
}
```

***REMOVED******REMOVED******REMOVED*** What It Disables

| Feature | Disabled by Privacy Safe Mode |
|---------|-------------------------------|
| Cloud Classification | Yes |
| Assistant Images | Yes |
| Share Diagnostics | Yes |
| Voice Mode | No (uses on-device STT) |
| Assistant Text | No (but entitlements still apply) |

***REMOVED******REMOVED******REMOVED*** UI Location

Settings → Privacy & Data → Privacy Safe Mode

---

***REMOVED******REMOVED*** Verification Commands

```bash
***REMOVED*** Check that assistant doesn't send images when disabled
adb logcat -s AssistantViewModel | grep -E "image|thumbnail"

***REMOVED*** Check cloud classification is respecting toggle
adb logcat -s CloudClassifier FeatureFlagRepository | grep -E "cloud|enabled|disabled"

***REMOVED*** Check voice never records in background
adb logcat -s AssistantVoice | grep -E "listening|shutdown|dispose"

***REMOVED*** Check crash reporting opt-in
adb logcat -s AndroidCrashPortAdapter Sentry | grep -E "diagnostics|opt"
```

---

***REMOVED******REMOVED*** Changelog

| Date | Change |
|------|--------|
| 2025-12-25 | Initial version for PR7 pre-ship hardening |
