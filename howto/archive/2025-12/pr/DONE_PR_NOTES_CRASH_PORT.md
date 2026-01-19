***REMOVED*** PR ***REMOVED***4: Android Sentry Integration behind CrashPort

***REMOVED******REMOVED*** Summary

This PR introduces a **vendor-neutral CrashPort interface** in the shared Kotlin Multiplatform code
and implements it using the Sentry Android SDK. This architecture keeps crash reporting abstracted
from the shared business logic while allowing platform-specific implementations.

***REMOVED******REMOVED******REMOVED*** Key Features

✅ **Vendor-neutral CrashPort interface** in `shared/telemetry`
✅ **Android Sentry adapter** implementing CrashPort
✅ **Automatic breadcrumb bridging** from Telemetry WARN/ERROR events
✅ **Comprehensive tagging** (platform, version, build, env, session, scan mode)
✅ **Crash test feature** in Developer settings (DEBUG builds only)
✅ **User consent** via existing "Share Diagnostics" setting

---

***REMOVED******REMOVED*** Architecture

***REMOVED******REMOVED******REMOVED*** Hexagonal Architecture (Ports & Adapters)

```
┌─────────────────────────────────────────────────────────┐
│                   Shared Code (KMP)                     │
│  ┌────────────────────────────────────────────────┐     │
│  │          Telemetry Facade                      │     │
│  │  - event(name, severity, attributes)           │     │
│  │  - warn() / error()                            │     │
│  │  - Auto-forwards WARN/ERROR to CrashPort       │     │
│  └────────────────┬───────────────────────────────┘     │
│                   │                                      │
│  ┌────────────────▼───────────────────────────────┐     │
│  │          CrashPort Interface                   │     │
│  │  - setTag(key, value)                          │     │
│  │  - addBreadcrumb(message, attributes)          │     │
│  │  - captureException(throwable, attributes)     │     │
│  └────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────┘
                    │
                    │ Platform boundary
                    │
┌───────────────────▼─────────────────────────────────────┐
│                Android App Layer                        │
│  ┌────────────────────────────────────────────────┐     │
│  │     AndroidCrashPortAdapter (Sentry)           │     │
│  │  - Wraps Sentry Android SDK                    │     │
│  │  - Translates CrashPort calls to Sentry API    │     │
│  │  - Thread-safe (Sentry SDK guarantees)         │     │
│  └────────────────┬───────────────────────────────┘     │
│                   │                                      │
│                   ▼                                      │
│            Sentry Android SDK                            │
│         (io.sentry:sentry-android:7.14.0)                │
└─────────────────────────────────────────────────────────┘
```

***REMOVED******REMOVED******REMOVED*** Breadcrumb Flow

1. Business logic calls `telemetry.warn("ml.classification_failed", attrs)`
2. Telemetry facade emits event to LogPort (existing behavior)
3. **NEW:** If CrashPort is configured and severity ≥ WARN, forward as breadcrumb
4. AndroidCrashPortAdapter translates to `Sentry.addBreadcrumb()`
5. Breadcrumb appears in Sentry when crashes occur

---

***REMOVED******REMOVED*** Files Changed

***REMOVED******REMOVED******REMOVED*** Shared Code (Kotlin Multiplatform)

***REMOVED******REMOVED******REMOVED******REMOVED*** New Files

- **`shared/telemetry/src/commonMain/kotlin/com/scanium/telemetry/ports/CrashPort.kt`**
  Vendor-neutral interface for crash reporting (setTag, addBreadcrumb, captureException)

***REMOVED******REMOVED******REMOVED******REMOVED*** Modified Files

- **`shared/telemetry/src/commonMain/kotlin/com/scanium/telemetry/ports/NoOpPorts.kt`**
  Added `NoOpCrashPort` for testing and disabled builds

- **`shared/telemetry/src/commonMain/kotlin/com/scanium/telemetry/facade/Telemetry.kt`**
    - Added optional `crashPort: CrashPort?` parameter
    - Auto-forwards WARN/ERROR events as breadcrumbs
    - Filters out redundant attributes (platform, app_version, etc.) from breadcrumbs

***REMOVED******REMOVED******REMOVED*** Android App

***REMOVED******REMOVED******REMOVED******REMOVED*** New Files

- **`androidApp/src/main/java/com/scanium/app/crash/AndroidCrashPortAdapter.kt`**
  Sentry Android SDK implementation of CrashPort

***REMOVED******REMOVED******REMOVED******REMOVED*** Modified Files

- **`androidApp/src/main/java/com/scanium/app/ScaniumApplication.kt`**
    - Refactored to use CrashPort adapter
    - Sets required tags: `platform`, `app_version`, `build`, `env`
    - Sets session tags: `session_id`, `scan_mode`, `build_type`
    - Placeholder for `domain_pack_version` (future feature)
    - Falls back to NoOpCrashPort when Sentry DSN not configured

- **`androidApp/src/main/java/com/scanium/app/ui/settings/SettingsViewModel.kt`**
    - Added `Application` parameter to access CrashPort
    - Added `triggerCrashTest(throwCrash: Boolean)` for manual testing

- **`androidApp/src/main/java/com/scanium/app/ui/settings/SettingsScreen.kt`**
    - Added "Test Crash Reporting" button in Developer section (DEBUG only)

- **`androidApp/src/main/java/com/scanium/app/ScaniumApp.kt`**
    - Updated SettingsViewModel.Factory to pass Application instance

- **`androidApp/src/main/java/com/scanium/app/navigation/NavGraph.kt`**
    - Updated SettingsViewModel.Factory to pass Application instance

- **`androidApp/build.gradle.kts`**
    - Added dependencies: `implementation(project(":shared:telemetry"))`
    - Added dependencies: `implementation(project(":shared:telemetry-contract"))`

---

***REMOVED******REMOVED*** Sentry DSN Configuration

***REMOVED******REMOVED******REMOVED*** Method 1: local.properties (Recommended for Development)

Create or edit `local.properties` in the project root:

```properties
***REMOVED*** Sentry DSN for crash reporting
sentry.dsn=https://your-sentry-dsn@sentry.io/project-id
```

***REMOVED******REMOVED******REMOVED*** Method 2: Environment Variable (Recommended for CI/CD)

Set the environment variable before building:

```bash
export SENTRY_DSN="https://your-sentry-dsn@sentry.io/project-id"
./gradlew assembleDebug
```

***REMOVED******REMOVED******REMOVED*** Build Configuration

The Sentry DSN is loaded in `androidApp/build.gradle.kts`:

```kotlin
val sentryDsn = localPropertyOrEnv("sentry.dsn", "SENTRY_DSN", "")
buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
```

If no DSN is configured, the app will use `NoOpCrashPort` (no crash reporting).

---

***REMOVED******REMOVED*** User Consent

Crash reporting respects the **"Share Diagnostics"** setting in the app:

1. User enables "Share Diagnostics" in Settings → Privacy & Data
2. Setting is stored in DataStore (`SettingsRepository.shareDiagnosticsFlow`)
3. `ScaniumApplication` observes this flow and updates `crashReportingEnabled` flag
4. Sentry's `beforeSend` callback filters events based on user consent

**Privacy-first:** Events are discarded locally if the user hasn't opted in.

---

***REMOVED******REMOVED*** Testing Crash Reporting

***REMOVED******REMOVED******REMOVED*** Manual Test (DEBUG builds only)

1. **Build and install the debug APK:**
   ```bash
   ./gradlew :androidApp:assembleDebug
   adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
   ```

2. **Enable crash reporting:**
    - Open the app
    - Navigate to: **Settings → Privacy & Data**
    - Enable **"Share Diagnostics"**

3. **Trigger a test crash:**
    - Navigate to: **Settings → Developer**
    - Enable **"Developer Mode"** (if not already enabled)
    - Tap **"Test Crash Reporting"**

4. **Verify in Sentry:**
    - Go to your Sentry project dashboard
    - Navigate to **Issues**
    - Look for: `RuntimeException: 🧪 Test crash from developer settings - this is intentional!`
    - Verify tags: `platform=android`, `app_version`, `build`, `env=dev`, `crash_test=true`
    - Check breadcrumbs: Should include "User triggered crash test"

***REMOVED******REMOVED******REMOVED*** Expected Tags in Sentry

```yaml
platform: android
app_version: 1.0  ***REMOVED*** From BuildConfig.VERSION_NAME
build: 1          ***REMOVED*** From BuildConfig.VERSION_CODE
env: dev          ***REMOVED*** "dev" in DEBUG builds, "prod" in RELEASE
build_type: debug ***REMOVED*** "debug" or "release"
session_id: <uuid>
scan_mode: <LOCAL|CLOUD|HYBRID>
cloud_allowed: true
domain_pack_version: unknown  ***REMOVED*** Placeholder for future
crash_test: true  ***REMOVED*** Only on manual test crashes
```

---

***REMOVED******REMOVED*** Breadcrumb Volume Control

To keep breadcrumb volume low (as per requirements):

1. **Severity filtering:** Only WARN and ERROR events are forwarded (not INFO or DEBUG)
2. **Attribute filtering:** Common attributes already set as tags are excluded from breadcrumbs
3. **Sentry SDK limit:** Default max 100 breadcrumbs (FIFO eviction)

Future optimization options:

- Sampling (e.g., forward 10% of WARN events)
- Rate limiting (e.g., max 10 breadcrumbs per minute)
- Grouping (e.g., deduplicate identical events)

---

***REMOVED******REMOVED*** Future Work (Out of Scope for This PR)

- [ ] **OTLP export:** Add OpenTelemetry export alongside Sentry
- [ ] **iOS implementation:** Create `IOSCrashPortAdapter` using Sentry iOS SDK
- [ ] **Domain pack version tag:** Wire up actual version once domain pack system is implemented
- [ ] **Telemetry → DiagnosticsPort integration:** Auto-forward events to DiagnosticsPort for crash
  attachments
- [ ] **Crash report enrichment:** Attach diagnostic bundles from DiagnosticsPort to Sentry events

---

***REMOVED******REMOVED*** Rollback Plan

If this integration causes issues:

1. **Disable crash reporting:**
    - Remove `SENTRY_DSN` from `local.properties` or environment
    - App will fall back to `NoOpCrashPort`

2. **Revert PR:**
   ```bash
   git revert <commit-hash>
   ```

3. **Feature flag (future):** Add remote config flag to disable crash reporting server-side

---

***REMOVED******REMOVED*** Security Considerations

✅ **PII protection:** Telemetry events are already sanitized by `AttributeSanitizer` before reaching
CrashPort
✅ **User consent:** Crash reporting only active when user opts in via "Share Diagnostics"
✅ **Vendor isolation:** Shared code has zero dependencies on Sentry (only on CrashPort interface)
✅ **Secrets management:** Sentry DSN stored in `local.properties` (gitignored)

---

***REMOVED******REMOVED*** Build Verification

```bash
***REMOVED*** Clean build from scratch
./gradlew clean

***REMOVED*** Build debug APK
./gradlew :androidApp:assembleDebug

***REMOVED*** Verify no compiler errors (✅ Build successful)
```

---

***REMOVED******REMOVED*** PR Review Checklist

- [x] CrashPort interface is vendor-neutral (no Sentry imports in shared code)
- [x] AndroidCrashPortAdapter properly wraps Sentry SDK
- [x] User consent is respected (beforeSend callback)
- [x] Tags include platform, app_version, build, env, session_id, scan_mode
- [x] Breadcrumbs auto-forward from Telemetry WARN/ERROR events
- [x] Crash test feature works in DEBUG builds
- [x] Build passes: `./gradlew :androidApp:assembleDebug` ✅
- [x] No unrelated refactoring (focused on crash reporting only)
- [x] Documentation includes Sentry DSN configuration steps

---

***REMOVED******REMOVED*** Questions?

**Q: Why not use Sentry SDK directly in shared code?**
A: Keeping shared code vendor-neutral allows us to:

- Swap crash reporting vendors without changing business logic
- Support multiple backends simultaneously (e.g., Sentry + OpenTelemetry)
- Test with NoOpCrashPort in unit tests

**Q: How are breadcrumbs different from telemetry events?**
A: Breadcrumbs are lightweight context markers attached to crash reports. Telemetry events are full
observability data sent to analytics backends. CrashPort bridges the two.

**Q: What happens if I don't configure a Sentry DSN?**
A: The app uses `NoOpCrashPort`, which discards all crash data. No errors, just silent no-op.

---

**Ready for review!** 🚀
