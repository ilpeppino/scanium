# Android 15 Cloud Regression Test Suite

This document describes the cloud regression test suite for Scanium Android, designed to validate camera lifecycle, cloud classification, and share/export functionality on Android 15+ devices.

## Overview

### What These Tests Cover

The regression test suite validates critical paths that involve cloud backend integration:

| Test | Focus Area | Key Validations |
|------|-----------|-----------------|
| **BackendHealthRegressionTest** | Backend connectivity | Health endpoint, cloud mode config, latency |
| **CameraLifecycleRegressionTest** | Camera pipeline | Frame reception, navigation transitions, no freeze |
| **CloudClassificationRegressionTest** | Classification API | End-to-end cloud requests, result parsing |
| **ShareExportRegressionTest** | Export functionality | CSV/ZIP creation, intent configuration |
| **BackgroundForegroundRegressionTest** | App lifecycle | Background/foreground transitions, camera recovery |

### Regression vs Unit Tests

| Aspect | Unit Tests | Regression Tests |
|--------|-----------|------------------|
| **Scope** | Single class/function | End-to-end feature flows |
| **Dependencies** | Mocked/faked | Real backend required |
| **Speed** | Fast (ms) | Slower (seconds) |
| **Environment** | JVM/Robolectric | Physical device recommended |
| **Data** | Static fixtures | Generated at runtime |

---

## Running the Tests

### Prerequisites

1. **Cloud Backend**: Tests require a running backend with the `/health` endpoint
2. **Physical Device**: Android 15 device recommended (emulator may work but less reliable for camera tests)
3. **Permissions**: Camera permission must be grantable

### Method 1: Instrumentation Arguments (Recommended)

```bash
./gradlew :androidApp:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.SCANIUM_BASE_URL=https://your-backend.com \
    -Pandroid.testInstrumentationRunnerArguments.SCANIUM_API_KEY=your-api-key
```

### Method 2: Environment Variables

Set before running tests:

```bash
export SCANIUM_BASE_URL=https://your-backend.com
export SCANIUM_API_KEY=your-api-key

./gradlew :androidApp:connectedDebugAndroidTest
```

### Method 3: local.properties

Add to `local.properties` (not committed to git):

```properties
scanium.api.base.url=https://your-backend.com
scanium.api.key=your-api-key
```

Then run:

```bash
./gradlew :androidApp:connectedDebugAndroidTest
```

### Run Specific Tests

```bash
# Run only backend health test
./gradlew :androidApp:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.scanium.app.regression.BackendHealthRegressionTest

# Run only camera lifecycle test
./gradlew :androidApp:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.scanium.app.regression.CameraLifecycleRegressionTest

# Run the full suite
./gradlew :androidApp:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.scanium.app.regression.CloudRegressionTestSuite
```

---

## Device Requirements

### Recommended Configuration

- **OS**: Android 15 (API 35) physical device
- **Camera**: Back-facing camera required for camera tests
- **Network**: WiFi connection to reach cloud backend
- **Storage**: Sufficient space for test artifacts

### Compatibility Notes

| API Level | Support Level | Notes |
|-----------|--------------|-------|
| 35+ (Android 15) | ✅ Full | Primary target, all features tested |
| 30-34 (Android 11-14) | ✅ Full | Fully compatible |
| 24-29 (Android 7-10) | ⚠️ Partial | Camera tests may be flaky on older devices |

### Camera Requirements

- Tests detect camera availability and skip if no camera is present
- Emulator cameras may work but provide less reliable results
- Physical device strongly recommended for camera lifecycle tests

---

## Test Details

### TEST 1: Backend Health (BackendHealthRegressionTest)

**Purpose**: Validate cloud backend is reachable and properly configured.

**What it tests**:
- Backend `/health` endpoint returns HTTP 200
- Response latency is within acceptable range (<3 seconds)
- Cloud mode configuration is active
- Diagnostics state contains valid configuration

**Skip conditions**:
- Backend URL not configured
- Backend not reachable (network error, timeout)

### TEST 2: Camera Lifecycle (CameraLifecycleRegressionTest)

**Purpose**: Ensure camera pipeline doesn't freeze after navigation or lifecycle transitions.

**What it tests**:
- Camera receives frames after app launch
- Navigation to Items list and back doesn't freeze camera
- Multiple navigation cycles don't cause resource leaks
- Debug overlay (if enabled) shows valid state

**Skip conditions**:
- No camera available on device

**Key metrics observed**:
- `lastFrame` timestamp updates
- `fps` > 0 when camera active
- Pipeline status is "OK" (not stalled)

### TEST 3: Cloud Classification (CloudClassificationRegressionTest)

**Purpose**: Validate cloud classification API works end-to-end without real camera captures.

**What it tests**:
- Classification request reaches backend successfully
- Backend returns valid classification result
- Multiple requests complete without error
- Large bitmaps are handled correctly

**Key approach**:
- Uses generated solid-color bitmaps (no binary test assets)
- Deterministic inputs for reproducible results
- Tests HTTP path, not ML accuracy

### TEST 4: Share/Export (ShareExportRegressionTest)

**Purpose**: Verify CSV and ZIP exports are created correctly with proper attachments.

**What it tests**:
- CSV export creates valid file with correct columns
- ZIP export creates archive with `images/` folder and `items.csv`
- Special characters in labels are properly escaped
- Share intent has correct action, type, and permissions
- Exported files are readable

**Key validations**:
- `ACTION_SEND` for single item, `ACTION_SEND_MULTIPLE` for multiple
- `FLAG_GRANT_READ_URI_PERMISSION` is set
- MIME types are correct (`text/csv`, `application/zip`, `image/*`)

### TEST 5: Background/Foreground (BackgroundForegroundRegressionTest)

**Purpose**: Ensure camera pipeline recovers correctly after app backgrounding.

**What it tests**:
- Camera resumes after `RESUMED` → `CREATED` → `RESUMED` transition
- Home button press and return doesn't break camera
- Multiple rapid lifecycle changes don't cause resource leaks
- Configuration change (rotation) doesn't crash camera

**Methods used**:
- `ActivityScenario.moveToState()` for controlled transitions
- UiAutomator `pressHome()` for real home button simulation
- `scenario.recreate()` for configuration changes

---

## Troubleshooting

### Backend Unreachable

**Symptom**: Tests skip with message "Backend not reachable"

**Causes & Solutions**:

1. **Backend not running**
   ```bash
   # Start backend
   cd backend && npm run dev
   ```

2. **Wrong URL**
   - Verify URL is correct (include protocol: `https://`)
   - Check for trailing slashes

3. **Network issues**
   - Ensure device has network connectivity
   - Check firewall rules
   - Verify ngrok tunnel is active (for local dev)

4. **API key invalid**
   - Regenerate API key in backend
   - Verify key is correctly passed

### Permission Prompts

**Symptom**: Tests hang waiting for permission dialog

**Solutions**:

1. **Grant permissions before test**
   ```bash
   adb shell pm grant com.scanium.app.dev android.permission.CAMERA
   ```

2. **Use `GrantPermissionRule`** (already included in test setup)

3. **Clear app data between runs**
   ```bash
   adb shell pm clear com.scanium.app.dev
   ```

### Intent Interception on Android 15

**Symptom**: Espresso Intents tests fail on Android 15

**Solutions**:

1. **Use `Intents.init()` and `Intents.release()`** in `@Before`/`@After`

2. **Avoid actual intent dispatch** in tests - verify intent configuration instead

3. **Grant required permissions**:
   ```bash
   adb shell appops set com.scanium.app.dev SYSTEM_ALERT_WINDOW allow
   ```

### Camera Tests Flaky

**Symptom**: Camera tests pass sometimes, fail sometimes

**Solutions**:

1. **Use physical device** instead of emulator

2. **Increase timeouts** if device is slow

3. **Add stabilization delays** (already included)

4. **Check for other apps using camera**

### Test Takes Too Long

**Symptom**: Tests timeout before completing

**Causes**:
- Backend response too slow
- Device is slow
- Too many lifecycle transitions

**Solutions**:
- Increase timeout constants in test classes
- Use faster device
- Reduce repeat counts in stress tests

---

## Architecture Notes

### Test Configuration Flow

```
Instrumentation Arguments
         ↓
   RegressionTestConfig.initialize()
         ↓
   TestConfigOverride.initFromArguments()
         ↓
   BackendHealthGate.checkBackendOrSkip()
         ↓
       [Test runs or skips]
```

### Key Classes

| Class | Purpose |
|-------|---------|
| `TestConfigOverride` | Reads and stores test configuration |
| `BackendHealthGate` | Validates backend health before tests |
| `RegressionTestConfig` | Entry point for test configuration |
| `TestSemantics` | Centralized test tag constants |
| `TestBridge` | Debug-only test utilities (item creation) |

### Test Tag Locations

| Tag | Location | Purpose |
|-----|----------|---------|
| `cam_pipeline_debug` | CameraPipelineDebugOverlay | Root overlay container |
| `cam_status` | CameraPipelineDebugOverlay | Pipeline status text |
| `cam_last_frame` | CameraPipelineDebugOverlay | Frame timestamp |
| `cam_fps` | CameraPipelineDebugOverlay | Analysis FPS |

---

## CI/CD Integration

### GitHub Actions Example

```yaml
jobs:
  regression-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Run Regression Tests
        env:
          SCANIUM_BASE_URL: ${{ secrets.SCANIUM_BASE_URL }}
          SCANIUM_API_KEY: ${{ secrets.SCANIUM_API_KEY }}
        run: |
          ./gradlew :androidApp:connectedDebugAndroidTest \
            -Pandroid.testInstrumentationRunnerArguments.SCANIUM_BASE_URL=$SCANIUM_BASE_URL \
            -Pandroid.testInstrumentationRunnerArguments.SCANIUM_API_KEY=$SCANIUM_API_KEY
```

### Required Secrets

- `SCANIUM_BASE_URL`: Cloud backend URL
- `SCANIUM_API_KEY`: API key for authentication

---

## Extending the Suite

### Adding New Tests

1. Create test class in `androidApp/src/androidTest/java/com/scanium/app/regression/`
2. Extend pattern from existing tests
3. Add to `CloudRegressionTestSuite.kt`
4. Use `BackendHealthGate.checkBackendOrSkip()` if cloud required

### Adding New Test Tags

1. Add constant to `TestSemantics.kt`
2. Apply `.testTag()` modifier in UI component
3. Use in tests with `onNodeWithTag()`

### Best Practices

- Always skip (not fail) if prerequisites missing
- Use generated data instead of binary assets
- Keep timeouts reasonable but not too aggressive
- Log clear skip reasons for debugging
- Clean up any created files/resources
