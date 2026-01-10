***REMOVED*** Startup Crash-Loop Root Cause Analysis

**Date:** 2026-01-08
**Status:** Fixed
**Severity:** High (intermittent app launch failure)

***REMOVED******REMOVED*** Symptom

The app occasionally crash-loops immediately after install:
- Splash screen shows for ~0.5s then returns to home screen
- Re-opening triggers Android warning "app keeps stopping"
- Intermittent (not every install) - depends on DataStore state

***REMOVED******REMOVED*** How to Reproduce

1. Install fresh APK (any flavor: dev/beta/prod)
2. If DataStore preferences file becomes corrupted (e.g., due to process kill during write, disk issues, or app update edge cases)
3. App crashes on launch before Sentry is initialized
4. No crash report is captured, making diagnosis difficult

**Reproduction helper script:**
```bash
./scripts/dev/capture_startup_crash.sh --clear --loop 10 --stop-on-crash
```

***REMOVED******REMOVED*** Root Cause

**Location:** `ScaniumApplication.onCreate()` (line 55-62)

```kotlin
// PROBLEMATIC: No error handling for DataStore failures
val initialLanguage = runBlocking {
    settingsRepository.appLanguageFlow.first()
}
```

**Two issues combined to cause the crash:**

***REMOVED******REMOVED******REMOVED*** 1. Missing DataStore Corruption Handler

`SettingsRepository.kt` used `preferencesDataStore` without a `corruptionHandler`:

```kotlin
// BEFORE (no corruption handler)
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings_preferences",
)
```

If the preferences file becomes corrupted (possible after process kill, disk issues, or app updates), `dataStore.data.first()` throws an `IOException`.

***REMOVED******REMOVED******REMOVED*** 2. Unprotected runBlocking in Application.onCreate()

The locale loading code blocked the main thread with `runBlocking` and had no error handling:

```kotlin
// BEFORE (no error handling)
val initialLanguage = runBlocking {
    settingsRepository.appLanguageFlow.first()
}
```

If DataStore threw, this crashed the app **before Sentry was initialized**, so:
- No crash report was captured
- Android showed "app keeps stopping" on subsequent launches
- The crash loop continued until the user cleared app data

***REMOVED******REMOVED*** Fix

***REMOVED******REMOVED******REMOVED*** 1. Added DataStore Corruption Handler

`SettingsRepository.kt` now includes a corruption handler that resets to defaults:

```kotlin
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { exception ->
        Log.e(SETTINGS_DATASTORE_TAG, "DataStore corrupted, resetting to defaults", exception)
        emptyPreferences()
    },
)
```

***REMOVED******REMOVED******REMOVED*** 2. Added Safe Flow Helper

All startup-critical flows now use a `.safeMap()` helper that catches IO exceptions:

```kotlin
private fun <T> Flow<Preferences>.safeMap(
    default: T,
    transform: (Preferences) -> T,
): Flow<T> =
    this
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "DataStore IO error, using default", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            runCatching { transform(preferences) }.getOrElse { default }
        }
```

***REMOVED******REMOVED******REMOVED*** 3. Added Defensive Error Handling in Application

`ScaniumApplication.onCreate()` now wraps the locale loading in `runCatching`:

```kotlin
val initialLanguage = runCatching {
    runBlocking { settingsRepository.appLanguageFlow.first() }
}.getOrElse { exception ->
    Log.e("ScaniumApplication", "Failed to load initial language preference", exception)
    AppLanguage.SYSTEM  // Fallback to system default
}
```

***REMOVED******REMOVED******REMOVED*** 4. Added Crash-Loop Detection (StartupGuard)

New `StartupGuard` class detects crash loops and enables safe mode:

- Records startup attempt timestamps using SharedPreferences (not DataStore)
- Detects if app crashed within 30 seconds of last launch
- After 3 consecutive quick crashes, enables "safe mode"
- In safe mode, optional initialization is skipped
- Successful startup (first composition rendered) resets the counter

**Files:**
- `app/startup/StartupGuard.kt` - Crash-loop detection
- `app/startup/StartupOrchestrator.kt` - Phased initialization framework

***REMOVED******REMOVED*** Regression Guards

***REMOVED******REMOVED******REMOVED*** 1. Instrumented Tests

`StartupReliabilityRegressionTest.kt` validates:
- App starts and reaches RESUMED state
- StartupGuard correctly records success
- Application components are initialized
- Multiple startups don't trigger safe mode
- Safe mode activates after crash threshold

**Run:**
```bash
./gradlew :androidApp:connectedDevDebugAndroidTest \
    --tests "com.scanium.app.regression.StartupReliabilityRegressionTest"
```

***REMOVED******REMOVED******REMOVED*** 2. Unit Tests

`StartupGuardTest.kt` validates crash-loop detection logic:
- Initial state has no crashes
- Startup timestamps are recorded
- Crash counter resets on success
- Safe mode triggers after threshold
- Slow restarts don't count as crashes

**Run:**
```bash
./gradlew :androidApp:testDevDebugUnitTest \
    --tests "com.scanium.app.startup.StartupGuardTest"
```

***REMOVED******REMOVED******REMOVED*** 3. Capture Script

`scripts/dev/capture_startup_crash.sh` provides:
- Automated logcat capture on startup
- Loop mode for catching intermittent crashes
- FATAL EXCEPTION extraction
- Package info and process state capture

**Usage:**
```bash
***REMOVED*** Single capture
./scripts/dev/capture_startup_crash.sh

***REMOVED*** Loop mode (catch intermittent)
./scripts/dev/capture_startup_crash.sh --clear --loop 10 --stop-on-crash

***REMOVED*** With APK install
./scripts/dev/capture_startup_crash.sh --flavor beta --install app.apk
```

***REMOVED******REMOVED*** Remaining Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Other DataStore instances without corruption handlers | Audit other DataStore usages; use safeMap pattern |
| Native library loading failures | DomainPackProvider.initialize() is already wrapped in try-catch |
| Hilt DI initialization failures | Compile-time safety from KSP; no known issues |
| Network security config issues | Build-time validation by AGP |

***REMOVED******REMOVED*** Files Changed

| File | Change |
|------|--------|
| `data/SettingsRepository.kt` | Added corruption handler and safeMap helper |
| `ScaniumApplication.kt` | Added runCatching for locale loading, integrated StartupGuard |
| `MainActivity.kt` | Added StartupGuard.recordStartupSuccess() call |
| `startup/StartupGuard.kt` | New crash-loop detection class |
| `startup/StartupOrchestrator.kt` | New phased initialization framework |
| `scripts/dev/capture_startup_crash.sh` | New capture script |
| `regression/StartupReliabilityRegressionTest.kt` | New instrumented tests |
| `startup/StartupGuardTest.kt` | New unit tests |

***REMOVED******REMOVED*** Verification

1. Build and install fresh:
   ```bash
   ./scripts/android-build-install-dev.sh --clean
   ```

2. Run regression tests:
   ```bash
   ./gradlew :androidApp:connectedDevDebugAndroidTest \
       --tests "*StartupReliabilityRegressionTest"
   ```

3. Run capture script to verify no crashes:
   ```bash
   ./scripts/dev/capture_startup_crash.sh --clear --loop 5
   ```

***REMOVED******REMOVED*** Future Improvements

1. **Add Phase 2/3 initialization** to StartupOrchestrator for ML warmup, caches
2. **Add safe mode UI** to show diagnostic info in dev builds
3. **Add telemetry** for startup timing and crash-loop detection events
4. **Audit all DataStore usages** across the codebase for corruption handlers
