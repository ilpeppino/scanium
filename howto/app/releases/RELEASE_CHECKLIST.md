***REMOVED*** Release Checklist

This guide documents the process for building and releasing Scanium for distribution.

***REMOVED******REMOVED*** Prerequisites

- Java 17 installed (`java -version`).
- Android SDK installed.
- Keystore file (e.g., `release.jks`) generated.

***REMOVED******REMOVED*** 1. Keystore Setup

**NEVER commit the keystore file or passwords to the repository.**

1. Generate a keystore if you don't have one:
   ```bash
   keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias scanium-release
   ```
2. Place `release.jks` in a secure location (outside the project root, or git-ignored).
3. Configure `local.properties` (do NOT commit this file):
   ```properties
   scanium.keystore.file=/absolute/path/to/release.jks
   scanium.keystore.password=YOUR_STORE_PASSWORD
   scanium.key.alias=scanium-release
   scanium.key.password=YOUR_KEY_PASSWORD
   ```
   Alternatively, set environment variables:
    * `SCANIUM_KEYSTORE_FILE`
    * `SCANIUM_KEYSTORE_PASSWORD`
    * `SCANIUM_KEY_ALIAS`
    * `SCANIUM_KEY_PASSWORD`

***REMOVED******REMOVED*** 2. Versioning

1. Open `androidApp/build.gradle.kts`.
2. Increment `versionCode`.
3. Update `versionName` (Semantic Versioning: Major.Minor.Patch).

***REMOVED******REMOVED*** 3. Build Release Artifacts

Scanium uses three product flavors: **prod** (production), **dev** (development), **beta** (external
testing).

**Production builds** (for Play Store or public release):

```bash
***REMOVED*** APK
./scripts/build.sh assembleProdRelease
***REMOVED*** Or: ./gradlew :androidApp:assembleProdRelease

***REMOVED*** AAB (for Play Store)
./gradlew :androidApp:bundleProdRelease
```

**Dev builds** (internal testing with Developer Options):

```bash
./gradlew :androidApp:assembleDevRelease
***REMOVED*** Or AAB: ./gradlew :androidApp:bundleDevRelease
```

**Beta builds** (external testers, no Developer Options):

```bash
./gradlew :androidApp:assembleBetaRelease
***REMOVED*** Or AAB: ./gradlew :androidApp:bundleBetaRelease
```

**Outputs:**

* APK: `androidApp/build/outputs/apk/{prod|dev|beta}/release/*.apk`
* AAB: `androidApp/build/outputs/bundle/{prod|dev|beta}Release/*.aab`

***REMOVED******REMOVED*** 4. Verification

1. **Install Release APK:**
   ```bash
   ***REMOVED*** Prod release
   adb install androidApp/build/outputs/apk/prod/release/*.apk

   ***REMOVED*** Or Dev/Beta
   adb install androidApp/build/outputs/apk/dev/release/*.apk
   adb install androidApp/build/outputs/apk/beta/release/*.apk
   ```
2. **Smoke Test:**
    * Launch app and verify correct flavor (check app name: "Scanium" for prod, "Scanium Dev" for
      dev, "Scanium Beta" for beta).
    * Verify Cloud Classification works (if configured).
    * Verify "About" screen shows correct version.
    * Verify no crashes on basic flows.
    * **Dev flavor only:** Verify Developer Options is accessible in Settings.
    * **Beta flavor:** Verify Developer Options is NOT accessible.

***REMOVED******REMOVED*** 5. Distribution

* **Play Store:** Upload the `.aab` file to the Play Console.
* **Direct:** Share the `.apk` file.

***REMOVED******REMOVED*** 6. ProGuard/R8 Checks

If the release build crashes but debug works, it's likely a ProGuard issue.
Check `androidApp/proguard-rules.pro` and add keep rules for any reflection-based libraries (e.g.,
GSON, Retrofit, some ML Kit parts if not already covered).
