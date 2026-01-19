***REMOVED*** Build and Install Verification

This document describes the deterministic build+install+verify system for Scanium Android development.

***REMOVED******REMOVED*** Problem

Previously, the installed APK could differ from the current git HEAD by 2+ commits due to:
- Using wildcard paths that picked stale APK outputs
- Building multiple ABI-specific APKs without selecting the correct one
- Gradle configuration cache using stale git SHA information
- Installing from wrong build output directories

***REMOVED******REMOVED*** Solution

We now provide deterministic build scripts that:
1. Build the exact variant (devDebug or betaDebug)
2. Detect device ABI and install the correct APK
3. Verify the installed app's git SHA matches the current HEAD
4. Fail loudly with diagnostics if there's a mismatch

***REMOVED******REMOVED*** Quick Start

***REMOVED******REMOVED******REMOVED*** Dev Builds

```bash
***REMOVED*** Build, install, and verify devDebug variant
./scripts/dev/install_dev_debug.sh

***REMOVED*** Optionally uninstall existing app first
./scripts/dev/install_dev_debug.sh --uninstall
```

***REMOVED******REMOVED******REMOVED*** Beta Builds

```bash
***REMOVED*** Build, install, and verify betaDebug variant
./scripts/dev/install_beta_debug.sh

***REMOVED*** Optionally uninstall existing app first
./scripts/dev/install_beta_debug.sh --uninstall
```

***REMOVED******REMOVED*** How It Works

***REMOVED******REMOVED******REMOVED*** 1. Build Fingerprinting

Git SHA and build time are embedded in `BuildConfig` at build time:

```kotlin
// androidApp/build.gradle.kts
val gitSha = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.get().trim()

buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
buildConfigField("String", "BUILD_TIME_UTC", "\"$buildTimeUtc\"")
```

***REMOVED******REMOVED******REMOVED*** 2. Device-Readable Build Info (Dev Flavor Only)

The `BuildInfoReceiver` (dev flavor only) allows scripts to query installed build info:

```bash
***REMOVED*** Query installed build info
adb shell am broadcast -n com.scanium.app.dev/com.scanium.app.debug.BuildInfoReceiver \
    -a com.scanium.app.DEBUG_BUILD_INFO

***REMOVED*** Read from logcat
adb logcat -d -s BuildInfoReceiver:I | grep "BUILD_INFO|"
***REMOVED*** Output: BUILD_INFO|com.scanium.app.dev|1.3.1-dev|13|dev|debug|a46ffeca|2026-01-19T22:41:27Z
```

**Location**: `androidApp/src/dev/java/com/scanium/app/debug/BuildInfoReceiver.kt`

This receiver is only included in dev builds via the `src/dev` source set and is registered in
`androidApp/src/dev/AndroidManifest.xml`.

***REMOVED******REMOVED******REMOVED*** 3. Startup Logging (All Flavors)

All flavors log build info at app startup for manual verification:

```bash
adb logcat -s APP_BUILD:I
***REMOVED*** Output: versionName=1.3.1-dev versionCode=13 flavor=dev buildType=debug git=a46ffeca time=2026-01-19T22:41:27Z
```

**Location**: `androidApp/src/main/java/com/scanium/app/ScaniumApplication.kt:76-83`

***REMOVED******REMOVED******REMOVED*** 4. Deterministic APK Selection

Scripts automatically:
- Detect device ABI (`adb shell getprop ro.product.cpu.abi`)
- Locate the correct APK: `androidApp/build/outputs/apk/{flavor}/{buildType}/androidApp-{flavor}-{abi}-{buildType}.apk`
- Fail if the expected APK doesn't exist

***REMOVED******REMOVED******REMOVED*** 5. Verification

After installation:
- **Dev builds**: Query `BuildInfoReceiver` via broadcast
- **Beta builds**: Launch app and read `APP_BUILD` from logcat
- Compare installed SHA with `git rev-parse --short HEAD`
- Exit 0 on match, exit 1 with diagnostics on mismatch

***REMOVED******REMOVED*** Script Output

***REMOVED******REMOVED******REMOVED*** Success

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Scanium Dev Build + Install + Verify
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[1/7] Computing expected git SHA...
Expected SHA: a46ffeca

[2/7] Checking for connected device...
Device: 100.103.213.58:42081

[3/7] Detecting device ABI...
Device ABI: arm64-v8a

[4/7] Building devDebug variant...
BUILD SUCCESSFUL in 13s

[5/7] Locating APK...
APK: /Users/family/dev/scanium/androidApp/build/outputs/apk/dev/debug/androidApp-dev-arm64-v8a-debug.apk (57M)

[6/7] Installing APK (upgrade)...
Success

[7/7] Verifying installed build SHA...
Received build info from device

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Verification Results
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Package:      com.scanium.app.dev
Version:      1.3.1-dev (13)
Flavor:       dev
Build Type:   debug
Build Time:   2026-01-19T22:41:27Z
Expected SHA: a46ffeca
Installed SHA: a46ffeca

✓ SUCCESS: Installed SHA matches expected SHA
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

***REMOVED******REMOVED******REMOVED*** Failure

```
✗ FAILURE: SHA mismatch!
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Diagnostics:
  APK installed from: /path/to/apk
  Device package path: /data/app/.../base.apk

Possible causes:
  1. The build used stale git information (Gradle config cache issue)
  2. Wrong APK was installed (check APK_FILE path above)
  3. Installed package was from a different commit

Recommended actions:
  1. Run: ./gradlew --stop
  2. Clean build outputs: ./gradlew clean
  3. Re-run this script with --uninstall flag
```

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** SHA Mismatch After Clean Checkout

If the installed SHA doesn't match after a clean checkout:

```bash
***REMOVED*** Stop Gradle daemon to clear config cache
./gradlew --stop

***REMOVED*** Clean build outputs
./gradlew clean

***REMOVED*** Uninstall and reinstall
./scripts/dev/install_dev_debug.sh --uninstall
```

***REMOVED******REMOVED******REMOVED*** BuildInfoReceiver Not Found (Dev Builds)

If the broadcast receiver fails:

1. Verify you built the **dev** flavor (not prod/beta)
2. Check the APK includes the receiver:
   ```bash
   unzip -l androidApp/build/outputs/apk/dev/debug/*.apk | grep BuildInfoReceiver
   ```
3. Verify the manifest merged correctly:
   ```bash
   adb shell pm dump com.scanium.app.dev | grep BuildInfoReceiver
   ```

***REMOVED******REMOVED******REMOVED*** Wrong ABI Selected

The script auto-detects device ABI. To manually verify:

```bash
adb shell getprop ro.product.cpu.abi
***REMOVED*** Output: arm64-v8a, armeabi-v7a, x86, or x86_64
```

Available ABIs per variant:
- `arm64-v8a` - Modern 64-bit ARM devices
- `armeabi-v7a` - Older 32-bit ARM devices
- `x86_64` - 64-bit emulators
- `x86` - 32-bit emulators

***REMOVED******REMOVED******REMOVED*** APK Not Found

If the expected APK doesn't exist:

```bash
***REMOVED*** List all built APKs
find androidApp/build/outputs/apk -name "*.apk"

***REMOVED*** Verify the variant was built
./gradlew :androidApp:assembleDevDebug --console=plain
```

***REMOVED******REMOVED*** Manual Verification

***REMOVED******REMOVED******REMOVED*** Check Installed SHA (Dev Builds)

```bash
adb shell am broadcast -n com.scanium.app.dev/com.scanium.app.debug.BuildInfoReceiver \
    -a com.scanium.app.DEBUG_BUILD_INFO

adb logcat -d -s BuildInfoReceiver:I | grep "BUILD_INFO" | tail -1
```

***REMOVED******REMOVED******REMOVED*** Check Installed SHA (All Flavors)

```bash
***REMOVED*** Start the app
adb shell am start -n com.scanium.app.dev/com.scanium.app.MainActivity

***REMOVED*** Read startup log
adb logcat -d -s APP_BUILD:I | tail -1
***REMOVED*** Output: versionName=1.3.1-dev versionCode=13 flavor=dev buildType=debug git=a46ffeca time=2026-01-19T22:41:27Z
```

***REMOVED******REMOVED******REMOVED*** Compare with Local HEAD

```bash
git rev-parse --short HEAD
```

***REMOVED******REMOVED*** Architecture Notes

***REMOVED******REMOVED******REMOVED*** Why Explicit Component Name for Broadcasts?

Android 8.0+ (API 26) restricts implicit broadcasts for security. The broadcast must specify the component explicitly:

```bash
***REMOVED*** ✓ Works (explicit component)
adb shell am broadcast -n com.scanium.app.dev/com.scanium.app.debug.BuildInfoReceiver -a ACTION

***REMOVED*** ✗ Fails on Android 8+ (implicit broadcast)
adb shell am broadcast -a ACTION
```

***REMOVED******REMOVED******REMOVED*** Why ABI-Specific APKs?

Scanium uses ABI splits for smaller download sizes:

```kotlin
// androidApp/build.gradle.kts
splits {
    abi {
        isEnable = true
        include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        isUniversalApk = false  // No universal APK
    }
}
```

This reduces APK size by ~40% but requires selecting the correct ABI at install time.

***REMOVED******REMOVED******REMOVED*** Git SHA Computation

The git SHA is computed at Gradle configuration time using the `providers.exec` API:

```kotlin
val gitSha = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.get().trim()
```

This ensures:
- Configuration cache compatibility (task outputs are cached separately)
- Reproducible builds (SHA is stable for a given git state)
- No runtime overhead (computed once during configuration)

**Important**: Gradle daemon caching can cause stale SHAs. Always stop the daemon (`./gradlew --stop`) after switching branches or pulling updates.

***REMOVED******REMOVED*** Related Files

***REMOVED******REMOVED******REMOVED*** Scripts
- `scripts/dev/install_dev_debug.sh` - Dev variant install script
- `scripts/dev/install_beta_debug.sh` - Beta variant install script

***REMOVED******REMOVED******REMOVED*** Source
- `androidApp/build.gradle.kts` - Git SHA and build time computation (lines 88-107, 194-195)
- `androidApp/src/dev/java/com/scanium/app/debug/BuildInfoReceiver.kt` - Debug broadcast receiver (dev only)
- `androidApp/src/dev/AndroidManifest.xml` - Receiver registration (dev only)
- `androidApp/src/main/java/com/scanium/app/ScaniumApplication.kt` - Startup logging (lines 74-83)

***REMOVED******REMOVED******REMOVED*** Build Configuration
- `androidApp/build.gradle.kts:341-346` - ABI splits configuration
- `androidApp/build.gradle.kts:114` - Base applicationId
- `androidApp/build.gradle.kts:241-289` - Flavor configuration (applicationIdSuffix)

***REMOVED******REMOVED*** See Also

- `howto/app/reference/DEV_GUIDE.md` - General development workflow
- `howto/app/reference/FLAVORS.md` - Build flavors and variants
- `CLAUDE.md` - Project overview and build commands
