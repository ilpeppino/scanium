***REMOVED*** Data Safety Mapping for Google Play

This document maps Scanium's features to the data types collected, shared, and processed, for the Google Play Console Data Safety section.

***REMOVED******REMOVED*** Overview

Scanium is a camera-first app that uses machine learning to detect and identify objects. Most processing happens on-device. Cloud features are opt-in and clearly disclosed in the app's Data Usage screen.

---

***REMOVED******REMOVED*** Data Safety Matrix

| Feature | Data Type | Collected? | Shared? | Purpose | Storage/Retention | User Control |
|---------|-----------|------------|---------|---------|-------------------|--------------|
| **On-Device Detection** | Camera frames | No (processed in memory only) | No | Object detection | Not stored | N/A |
| **On-Device OCR** | Text from barcodes | No (processed in memory only) | No | Item identification | Not stored | N/A |
| **Cloud Classification** | Cropped item thumbnails | Yes (opt-in) | Yes (Google Vision API) | Enhanced item classification | Transient (not stored permanently) | Settings → Cloud Classification |
| **AI Assistant (text)** | User questions + item context | Yes (opt-in) | Yes (AI provider via backend) | Listing assistance | Not stored server-side | Settings → Assistant Features |
| **AI Assistant (images)** | Item thumbnails | Yes (opt-in, off by default) | Yes (AI provider via backend) | Visual context for assistant | Not stored server-side | Settings → Send Images to Assistant |
| **Voice Mode (STT)** | Audio recording | No (processed on-device) | No | Hands-free input | Not stored | Settings → Enable Voice Mode |
| **Voice Mode (TTS)** | Text responses | No (processed on-device) | No | Spoken responses | Not stored | Settings → Speak Answers Aloud |
| **Crash Reports** | Device info, stack traces | Yes (opt-in, off by default) | Yes (Sentry) | App stability | 90 days retention (Sentry default) | Settings → Share Diagnostics |
| **Diagnostics Bundle** | App state, feature flags | Yes (opt-in) | Yes (with crash reports) | Bug diagnosis | Attached to crash reports | Settings → Share Diagnostics |

---

***REMOVED******REMOVED*** Detailed Data Descriptions

***REMOVED******REMOVED******REMOVED*** 1. Cloud Classification

**What is sent:**
- Cropped JPEG thumbnail of the detected item (not full camera frame)
- Domain pack ID (e.g., "home_resale")
- Correlation ID for request tracing (not linked to user identity)

**Privacy measures:**
- EXIF metadata stripped via JPEG re-compression
- No location data included
- Device ID is hashed (SHA-256) before transmission
- Images not stored permanently on server

**Endpoint:** `POST {SCANIUM_API_BASE_URL}/v1/classify`

**Third-party sharing:** Google Cloud Vision API (for image analysis)

**User control:** Toggle in Settings → Cloud Classification

---

***REMOVED******REMOVED******REMOVED*** 2. AI Assistant

**What is sent (text-only mode, default):**
- User's question text
- Item context: category, attributes, condition, draft fields
- Export profile metadata
- Device ID (hashed)
- Correlation ID

**What is sent (with images enabled):**
- All of the above, plus:
- Item thumbnail images (JPEG)

**Privacy measures:**
- Raw prompts are NOT logged on server
- Device ID hashed before transmission
- HMAC request signing for replay protection
- Client-side throttling (1 second minimum between requests)

**Endpoint:** `POST {SCANIUM_API_BASE_URL}/v1/assist/chat`

**Third-party sharing:** AI provider (provider-agnostic; currently routed via Scanium backend)

**User controls:**
- Settings → Assistant Features (master toggle)
- Settings → Send Images to Assistant (off by default)

---

***REMOVED******REMOVED******REMOVED*** 3. Voice Mode

**Speech-to-Text (STT):**
- Uses Android's built-in `SpeechRecognizer` API
- Audio processed on-device where available
- Some devices may use Google's cloud STT (determined by Android system)
- Audio is NOT stored or uploaded by Scanium

**Text-to-Speech (TTS):**
- Uses Android's built-in `TextToSpeech` API
- Fully on-device processing
- No data transmitted

**Privacy measures:**
- Recording only starts on explicit user gesture (mic button press)
- Recording stops immediately when:
  - User presses stop
  - Screen/app goes to background
  - End of speech detected
- No always-on listening
- Visual indicator shown during recording

**User controls:**
- Settings → Enable Voice Mode
- Settings → Speak Answers Aloud
- Settings → Auto-send After Transcription

---

***REMOVED******REMOVED******REMOVED*** 4. Crash Reports & Diagnostics

**What is collected (when opted in):**
- Device model, OS version
- App version, build number
- Stack traces for crashes
- Breadcrumb events (recent actions)
- Diagnostics bundle (feature flags, session ID)

**What is NOT collected:**
- Raw user prompts or questions
- OCR text or barcode data
- Audio recordings
- Images
- API keys or tokens
- Personal identifiable information (PII is redacted)

**Privacy measures:**
- Opt-in only (default OFF)
- PII automatically redacted
- Diagnostics bundle capped at 128KB
- No raw content included

**Third-party sharing:** Sentry (crash reporting service)

**Retention:** 90 days (Sentry default)

**User control:** Settings → Share Diagnostics

---

***REMOVED******REMOVED*** Privacy Safe Mode

Users can enable "Privacy Safe Mode" with one tap, which disables:
- Cloud Classification
- Assistant image sending
- Diagnostics sharing

This ensures no data leaves the device.

**Location:** Settings → Privacy Safe Mode

---

***REMOVED******REMOVED*** Google Play Data Safety Form Responses

Based on the above, here are the recommended responses for the Play Console:

***REMOVED******REMOVED******REMOVED*** Data Collection

| Data Type | Collected? | Required? | Purpose |
|-----------|------------|-----------|---------|
| Photos/Videos | Yes (optional) | No | App functionality (classification) |
| Audio | No | N/A | N/A |
| Personal info | No | N/A | N/A |
| Financial info | No | N/A | N/A |
| Health info | No | N/A | N/A |
| Messages | No | N/A | N/A |
| Files and docs | No | N/A | N/A |
| Calendar | No | N/A | N/A |
| Contacts | No | N/A | N/A |
| App activity | Yes (optional) | No | Analytics, crash reporting |
| Web browsing | No | N/A | N/A |
| App info and performance | Yes (optional) | No | Crash reporting |
| Device or other IDs | Yes (optional) | No | Rate limiting (hashed) |

***REMOVED******REMOVED******REMOVED*** Data Sharing

| Data Type | Shared? | Purpose | Recipient |
|-----------|---------|---------|-----------|
| Photos/Videos | Yes (optional) | App functionality | Google Vision API |
| App info and performance | Yes (optional) | Crash reporting | Sentry |
| Device IDs | Yes (optional) | Rate limiting | Scanium backend |

***REMOVED******REMOVED******REMOVED*** Security Practices

- Data encrypted in transit: Yes (HTTPS/TLS)
- Data encrypted at rest: Yes (Sentry infrastructure)
- Users can request data deletion: Yes (contact support)
- Independent security review: No

---

***REMOVED******REMOVED*** Changelog

| Date | Change |
|------|--------|
| 2025-12-25 | Initial version for PR7 pre-ship hardening |
