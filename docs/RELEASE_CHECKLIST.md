***REMOVED*** Release Checklist

This guide documents the process for building and releasing Scanium for distribution.

***REMOVED******REMOVED*** Prerequisites

-   Java 17 installed (`java -version`).
-   Android SDK installed.
-   Keystore file (e.g., `release.jks`) generated.

***REMOVED******REMOVED*** 1. Keystore Setup

**NEVER commit the keystore file or passwords to the repository.**

1.  Generate a keystore if you don't have one:
    ```bash
    keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias scanium-release
    ```
2.  Place `release.jks` in a secure location (outside the project root, or git-ignored).
3.  Configure `local.properties` (do NOT commit this file):
    ```properties
    scanium.keystore.file=/absolute/path/to/release.jks
    scanium.keystore.password=YOUR_STORE_PASSWORD
    scanium.key.alias=scanium-release
    scanium.key.password=YOUR_KEY_PASSWORD
    ```
    Alternatively, set environment variables:
    *   `SCANIUM_KEYSTORE_FILE`
    *   `SCANIUM_KEYSTORE_PASSWORD`
    *   `SCANIUM_KEY_ALIAS`
    *   `SCANIUM_KEY_PASSWORD`

***REMOVED******REMOVED*** 2. Versioning

1.  Open `androidApp/build.gradle.kts`.
2.  Increment `versionCode`.
3.  Update `versionName` (Semantic Versioning: Major.Minor.Patch).

***REMOVED******REMOVED*** 3. Build Release Artifacts

Run the build script:

```bash
./scripts/build.sh assembleRelease
```

Or using Gradle directly:

```bash
./gradlew assembleRelease
```

This will produce:
*   APK: `androidApp/build/outputs/apk/release/androidApp-release.apk`
*   AAB (for Play Store): `./gradlew bundleRelease` -> `androidApp/build/outputs/bundle/release/androidApp-release.aab`

***REMOVED******REMOVED*** 4. Verification

1.  **Install Release APK:**
    ```bash
    adb install androidApp/build/outputs/apk/release/androidApp-release.apk
    ```
2.  **Smoke Test:**
    *   Launch app.
    *   Verify Cloud Classification works (if configured).
    *   Verify "About" screen shows correct version.
    *   Verify no crashes on basic flows.

***REMOVED******REMOVED*** 5. Distribution

*   **Play Store:** Upload the `.aab` file to the Play Console.
*   **Direct:** Share the `.apk` file.

***REMOVED******REMOVED*** 6. ProGuard/R8 Checks

If the release build crashes but debug works, it's likely a ProGuard issue.
Check `androidApp/proguard-rules.pro` and add keep rules for any reflection-based libraries (e.g., GSON, Retrofit, some ML Kit parts if not already covered).
