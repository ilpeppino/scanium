***REMOVED*** CI-First Testing for Scanium

***REMOVED******REMOVED*** Why CI-First?

**Local Gradle builds in Codex container are NOT supported** due to:
- Missing Android SDK (cannot be installed in container)
- Java 17 toolchain unavailable
- Blocked dependency downloads (network restrictions)

***REMOVED******REMOVED*** How to Test on Your Phone

***REMOVED******REMOVED******REMOVED*** Step 1: Push to Main
```bash
git push origin main
```

***REMOVED******REMOVED******REMOVED*** Step 2: Download APK from GitHub Actions
1. Go to **GitHub → Actions** tab
2. Find the latest **"Android Debug APK"** workflow run
3. Download the artifact named **`scanium-app-debug-apk`** (zip file)

***REMOVED******REMOVED******REMOVED*** Step 3: Install on Android Device
1. Unzip the downloaded artifact
2. Transfer `app-debug.apk` to your Android device
3. Enable **"Install unknown apps"** for your file manager (one-time setup)
4. Tap the APK to install
5. Launch Scanium and test your changes

***REMOVED******REMOVED*** Workflow Details

The CI workflow automatically:
- ✅ Builds debug APK on every push to `main`
- ✅ Uses Java 17 (required)
- ✅ Uploads APK as downloadable artifact
- ✅ Fails fast if APK build fails

**Workflow file:** `.github/workflows/android-debug-apk.yml`

***REMOVED******REMOVED*** Local Development

You CAN still run Gradle locally if you have:
- Android SDK installed
- Java 17 configured
- Working network for dependencies

```bash
./gradlew clean assembleDebug
./gradlew test
```

But for Codex container development, **always use CI to build APKs**.
