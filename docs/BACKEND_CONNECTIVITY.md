***REMOVED*** Backend Connectivity Guide

**Purpose**: Configure the Scanium Android app to reliably reach the backend running on your NAS (Synology) in both LAN and remote modes.

**Last Updated**: 2026-01-01

---

***REMOVED******REMOVED*** Table of Contents

1. [Overview](***REMOVED***overview)
2. [Configuration Modes](***REMOVED***configuration-modes)
3. [Quick Start](***REMOVED***quick-start)
4. [Configuration Details](***REMOVED***configuration-details)
5. [Build & Install](***REMOVED***build--install)
6. [Verification](***REMOVED***verification)
7. [Troubleshooting](***REMOVED***troubleshooting)
8. [Advanced Topics](***REMOVED***advanced-topics)

---

***REMOVED******REMOVED*** Overview

The Scanium Android app supports two connectivity modes:

- **LAN Mode** (Debug): Connect directly to your NAS over HTTP on the local network
  - Example: `http://192.168.1.100:3000`
  - Use this for local development and testing
  - Requires physical device on the same network as your NAS

- **Remote Mode** (Release/Beta): Connect via HTTPS through a public domain
  - Example: `https://scanium.yourdomain.com`
  - Use this for production, beta testing, or remote access
  - Works from anywhere with internet connection
  - Typically uses Cloudflare Tunnel or similar reverse proxy

The app automatically selects the appropriate configuration based on the build type:
- **Debug builds**: Use LAN URL if configured, otherwise fall back to remote URL
- **Release builds**: Always use remote URL (HTTPS required)

---

***REMOVED******REMOVED*** Configuration Modes

***REMOVED******REMOVED******REMOVED*** Build Variants

The app has 4 build variants (combinations of flavor + build type):

| Variant | Flavor | Build Type | Use Case | Default URL |
|---------|--------|------------|----------|-------------|
| `devDebug` | dev | debug | Local development with LAN backend | LAN URL or Remote URL |
| `devRelease` | dev | release | Internal testing with remote backend | Remote URL (HTTPS) |
| `betaDebug` | beta | debug | Beta testing with LAN backend | LAN URL or Remote URL |
| `betaRelease` | beta | release | Beta distribution with remote backend | Remote URL (HTTPS) |

***REMOVED******REMOVED******REMOVED*** Network Security

- **Debug builds**: Allow cleartext HTTP traffic (for LAN access)
  - Config: `androidApp/src/debug/res/xml/network_security_config_debug.xml`
  - Allows HTTP to any host (e.g., `http://192.168.1.100:3000`)

- **Release builds**: Enforce HTTPS only (security)
  - Config: `androidApp/src/main/res/xml/network_security_config.xml`
  - Blocks HTTP except for localhost/emulator
  - Use this for production/beta with Cloudflare Tunnel or similar

---

***REMOVED******REMOVED*** Quick Start

***REMOVED******REMOVED******REMOVED*** 1. Find Your NAS LAN IP Address

On your NAS (Synology), find the local IP address:

```bash
***REMOVED*** SSH into your NAS
ssh admin@your-nas

***REMOVED*** Check IP address
ifconfig | grep "inet " | grep -v 127.0.0.1
```

You should see something like `inet 192.168.1.100 netmask ...`

Your backend port is typically `3000` (check your backend configuration).

***REMOVED******REMOVED******REMOVED*** 2. Configure Base URLs

Edit `local.properties` in the **repository root**:

```properties
***REMOVED*** For LAN mode (debug builds) - use your NAS LAN IP
scanium.api.base.url.debug=http://192.168.1.100:3000

***REMOVED*** For Remote mode (release builds) - use your public domain
scanium.api.base.url=https://scanium.yourdomain.com

***REMOVED*** API Key (same for both modes)
scanium.api.key=your-api-key-here
```

**Important**: The `local.properties` file is gitignored and safe to edit.

***REMOVED******REMOVED******REMOVED*** 3. Build & Install

For LAN mode (debug):
```bash
***REMOVED*** Build debug APK
./gradlew :androidApp:assembleDevDebug

***REMOVED*** Install to connected device
./gradlew :androidApp:installDevDebug

***REMOVED*** Or combined
./gradlew :androidApp:installDevDebug
```

For Remote mode (release):
```bash
***REMOVED*** Build release APK (requires signing config)
./gradlew :androidApp:assembleDevRelease

***REMOVED*** Install to connected device
./gradlew :androidApp:installDevRelease
```

***REMOVED******REMOVED******REMOVED*** 4. Verify Connectivity

Open the Scanium app and check:
1. Go to **Settings** → **Developer Options** (in dev builds)
2. Look for the base URL displayed
3. Try a backend-dependent feature (e.g., cloud classification, feature flags)

Or use the verification script (see [Verification](***REMOVED***verification) below).

---

***REMOVED******REMOVED*** Configuration Details

***REMOVED******REMOVED******REMOVED*** Configuration Files

***REMOVED******REMOVED******REMOVED******REMOVED*** 1. `local.properties` (Repository Root)

This is the **primary configuration file** for local development:

```properties
***REMOVED*** Android SDK location (auto-generated)
sdk.dir=/Users/family/Library/Android/sdk

***REMOVED*** === Backend Configuration ===

***REMOVED*** Debug builds: LAN URL (HTTP allowed in debug builds)
scanium.api.base.url.debug=http://192.168.1.100:3000

***REMOVED*** Release builds: Remote URL (HTTPS required)
scanium.api.base.url=https://scanium.yourdomain.com

***REMOVED*** API Key (shared between debug and release)
scanium.api.key=your-dev-api-key

***REMOVED*** Optional: Certificate pinning for HTTPS (production)
***REMOVED*** scanium.api.certificate.pin=sha256/AAAAAAAAAA...

***REMOVED*** === Other Configuration ===

***REMOVED*** Sentry DSN (optional)
***REMOVED*** scanium.sentry.dsn=https://...

***REMOVED*** OTLP endpoint for telemetry (optional)
***REMOVED*** scanium.otlp.endpoint=http://192.168.1.100:4318
***REMOVED*** scanium.otlp.enabled=true

***REMOVED*** Telemetry data region (US/EU)
***REMOVED*** scanium.telemetry.data_region=US
```

**Important**:
- This file is gitignored - it's safe to commit changes to it
- Changes take effect after rebuilding the app
- Environment variables override local.properties (useful for CI/CD)

***REMOVED******REMOVED******REMOVED******REMOVED*** 2. Environment Variables (CI/CD)

For automated builds, you can set environment variables instead of using `local.properties`:

```bash
export SCANIUM_API_BASE_URL_DEBUG=http://192.168.1.100:3000
export SCANIUM_API_BASE_URL=https://scanium.yourdomain.com
export SCANIUM_API_KEY=your-api-key
```

Priority: `local.properties` → `environment variables` → `default value`

***REMOVED******REMOVED******REMOVED*** How URLs Are Resolved

The `build.gradle.kts` uses the following logic:

***REMOVED******REMOVED******REMOVED******REMOVED*** Debug Builds:
```kotlin
val debugUrl = local.properties["scanium.api.base.url.debug"] ?: env["SCANIUM_API_BASE_URL_DEBUG"] ?: ""
val effectiveUrl = debugUrl.ifEmpty {
    local.properties["scanium.api.base.url"] ?: env["SCANIUM_API_BASE_URL"]
}
```

**Translation**: Debug builds prefer the `.debug` URL, but fall back to the main URL if not set.

***REMOVED******REMOVED******REMOVED******REMOVED*** Release Builds:
```kotlin
val releaseUrl = local.properties["scanium.api.base.url"] ?: env["SCANIUM_API_BASE_URL"]
```

**Translation**: Release builds always use the main (remote) URL.

***REMOVED******REMOVED******REMOVED*** BuildConfig Fields

The resolved URLs are injected as `BuildConfig` constants:

```kotlin
BuildConfig.SCANIUM_API_BASE_URL  // e.g., "http://192.168.1.100:3000" (debug) or "https://scanium.yourdomain.com" (release)
BuildConfig.CLOUD_CLASSIFIER_URL  // e.g., "${SCANIUM_API_BASE_URL}/classify"
BuildConfig.SCANIUM_API_KEY       // e.g., "your-api-key"
```

These are used throughout the app:
- `CloudClassifier.kt`: `val baseUrl = BuildConfig.SCANIUM_API_BASE_URL`
- `AndroidFeatureFlagRepository.kt`: `val baseUrl = BuildConfig.SCANIUM_API_BASE_URL`
- `AssistantRepository.kt`: `val baseUrl = BuildConfig.SCANIUM_API_BASE_URL`

---

***REMOVED******REMOVED*** Build & Install

***REMOVED******REMOVED******REMOVED*** Prerequisites

1. **Android SDK**: Install via Android Studio or `sdkmanager`
2. **Java 17**: Required for Gradle
   ```bash
   java -version  ***REMOVED*** Should show version 17+
   ```
3. **Physical Android Device**:
   - For LAN mode, device must be on the same network as your NAS
   - Enable USB debugging in Developer Options
   - Connect via USB

***REMOVED******REMOVED******REMOVED*** Build Commands

All commands should be run from the **repository root**.

***REMOVED******REMOVED******REMOVED******REMOVED*** 1. Debug Build (LAN Mode)

```bash
***REMOVED*** Clean previous builds (optional)
./gradlew clean

***REMOVED*** Build and install dev debug variant
./gradlew :androidApp:installDevDebug

***REMOVED*** Verify installation
adb shell pm list packages | grep scanium
***REMOVED*** Should show: package:com.scanium.app.dev
```

**What happens**:
- Uses `scanium.api.base.url.debug` (LAN URL) if set
- Falls back to `scanium.api.base.url` (remote URL) if debug URL not set
- Allows HTTP traffic (cleartext) via debug network security config
- Enables developer mode (Developer Options visible in Settings)

***REMOVED******REMOVED******REMOVED******REMOVED*** 2. Release Build (Remote Mode)

```bash
***REMOVED*** Build and install dev release variant
./gradlew :androidApp:installDevRelease

***REMOVED*** Verify installation
adb shell pm list packages | grep scanium
***REMOVED*** Should show: package:com.scanium.app.dev
```

**What happens**:
- Uses `scanium.api.base.url` (remote URL)
- Enforces HTTPS only (no cleartext HTTP except localhost)
- Requires signing config (see [Signing Configuration](***REMOVED***signing-configuration) below)
- Developer mode enabled (dev flavor)

***REMOVED******REMOVED******REMOVED******REMOVED*** 3. Beta Build (Release)

```bash
***REMOVED*** Build and install beta release variant
./gradlew :androidApp:installBetaRelease

***REMOVED*** Verify installation
adb shell pm list packages | grep scanium
***REMOVED*** Should show: package:com.scanium.app.beta
```

**What happens**:
- Uses `scanium.api.base.url` (remote URL)
- Enforces HTTPS only
- Requires signing config
- Developer mode **disabled** (beta flavor)

***REMOVED******REMOVED******REMOVED*** Signing Configuration

Release builds require a signing configuration. Add to `local.properties`:

```properties
***REMOVED*** Keystore file path (absolute or relative to androidApp/)
scanium.keystore.file=/path/to/keystore.jks

***REMOVED*** Keystore password
scanium.keystore.password=your-keystore-password

***REMOVED*** Key alias
scanium.key.alias=scanium-release

***REMOVED*** Key password
scanium.key.password=your-key-password
```

**Create a keystore** (if you don't have one):

```bash
keytool -genkey -v \
  -keystore scanium-release.jks \
  -alias scanium-release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass your-keystore-password \
  -keypass your-key-password
```

**Security**: Keep your keystore and passwords secure! Never commit them to git.

***REMOVED******REMOVED******REMOVED*** Unsigned Release Build

If you don't have signing configured, you can build an unsigned release APK:

```bash
***REMOVED*** Build unsigned release APK
./gradlew :androidApp:assembleDevRelease

***REMOVED*** Find the APK
ls androidApp/build/outputs/apk/dev/release/*.apk
```

You'll need to sign it manually before installing:

```bash
***REMOVED*** Sign with debug keystore (for testing only)
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore ~/.android/debug.keystore \
  -storepass android \
  app-dev-release-unsigned.apk androiddebugkey

***REMOVED*** Install
adb install app-dev-release-unsigned.apk
```

---

***REMOVED******REMOVED*** Verification

***REMOVED******REMOVED******REMOVED*** Automated Verification Script

Use the provided script to verify your configuration:

```bash
***REMOVED*** Run verification script
./scripts/verify-backend-config.sh
```

**What it checks**:
- Current build variant
- Resolved base URL from BuildConfig
- Network connectivity from device to backend
- Backend health endpoint

***REMOVED******REMOVED******REMOVED*** Manual Verification

***REMOVED******REMOVED******REMOVED******REMOVED*** 1. Check BuildConfig Value

Print the resolved base URL:

```bash
***REMOVED*** For debug build
./gradlew :androidApp:assembleDevDebug
grep -r "SCANIUM_API_BASE_URL" androidApp/build/generated/source/buildConfig/dev/debug/com/scanium/app/BuildConfig.java

***REMOVED*** For release build
./gradlew :androidApp:assembleDevRelease
grep -r "SCANIUM_API_BASE_URL" androidApp/build/generated/source/buildConfig/dev/release/com/scanium/app/BuildConfig.java
```

***REMOVED******REMOVED******REMOVED******REMOVED*** 2. Test Network Connectivity from Device

```bash
***REMOVED*** Check device can reach NAS (LAN mode)
adb shell ping -c 3 192.168.1.100

***REMOVED*** Test HTTP endpoint from device (LAN mode)
adb shell curl -v http://192.168.1.100:3000/health

***REMOVED*** Test HTTPS endpoint from device (remote mode)
adb shell curl -v https://scanium.yourdomain.com/health
```

**Expected responses**:
- `200 OK` for healthy backend
- `curl: (7) Failed to connect` if backend is unreachable
- `curl: (35) SSL connect error` for HTTPS/certificate issues

***REMOVED******REMOVED******REMOVED******REMOVED*** 3. Check App Logs

```bash
***REMOVED*** Monitor app logs
adb logcat | grep -i scanium

***REMOVED*** Look for these patterns:
***REMOVED*** - "Classifying endpoint=http://..." (shows actual URL being used)
***REMOVED*** - "Cloud classifier not configured" (base URL is empty)
***REMOVED*** - "Failed to connect" (network issue)
```

***REMOVED******REMOVED******REMOVED******REMOVED*** 4. In-App Verification

1. Open Scanium app
2. Go to **Settings** → **Developer Options** (dev builds only)
3. Scroll to **Diagnostics** section
4. Check **Backend Base URL**: Should show your configured URL
5. Try **Test Backend Connection** button (if available)

Or:
1. Take a photo of an item
2. If cloud classification is configured, it should attempt to classify
3. Check for network errors in the UI

---

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** Common Issues

***REMOVED******REMOVED******REMOVED******REMOVED*** 1. App Can't Reach Backend (Debug Build)

**Symptoms**:
- Error: "Failed to connect to /192.168.1.100:3000"
- Logcat: `java.net.ConnectException: Failed to connect`

**Possible Causes**:

**A. Device and NAS on different networks**
```bash
***REMOVED*** Check device IP
adb shell ip addr show wlan0 | grep inet

***REMOVED*** Should be in same subnet as NAS (e.g., 192.168.1.x)
```

**Solution**: Connect device to same WiFi as NAS

**B. Backend not running on NAS**
```bash
***REMOVED*** SSH into NAS
ssh admin@192.168.1.100

***REMOVED*** Check backend is running
docker ps | grep backend
***REMOVED*** Or
curl http://localhost:3000/health
```

**Solution**: Start backend on NAS

**C. NAS firewall blocking port 3000**
```bash
***REMOVED*** On NAS, check firewall rules
sudo iptables -L -n | grep 3000

***REMOVED*** Or check Synology firewall in Control Panel → Security → Firewall
```

**Solution**: Allow port 3000 in NAS firewall

**D. Wrong IP or port in configuration**
```bash
***REMOVED*** Check local.properties
cat local.properties | grep scanium.api.base.url.debug

***REMOVED*** Verify NAS IP
ping 192.168.1.100
```

**Solution**: Update `scanium.api.base.url.debug` in `local.properties`

***REMOVED******REMOVED******REMOVED******REMOVED*** 2. App Can't Reach Backend (Release Build)

**Symptoms**:
- Error: "Cleartext HTTP traffic to ... not permitted"
- Logcat: `java.net.UnknownServiceException: CLEARTEXT communication not permitted`

**Cause**: Release builds block HTTP traffic (security policy)

**Solution**:
- Use HTTPS URL in `scanium.api.base.url`
- Set up Cloudflare Tunnel or reverse proxy with SSL
- OR use debug build for LAN testing

***REMOVED******REMOVED******REMOVED******REMOVED*** 3. Certificate/SSL Errors (Release Build)

**Symptoms**:
- Error: "SSL handshake failed"
- Logcat: `javax.net.ssl.SSLHandshakeException`

**Possible Causes**:

**A. Self-signed certificate**
```bash
***REMOVED*** Check certificate
openssl s_client -servername scanium.yourdomain.com -connect scanium.yourdomain.com:443 < /dev/null
```

**Solution**: Use a valid SSL certificate (e.g., Let's Encrypt via Cloudflare)

**B. Certificate pinning mismatch**

If you configured `scanium.api.certificate.pin`, it must match your server's certificate.

```bash
***REMOVED*** Get current certificate pin
openssl s_client -servername scanium.yourdomain.com -connect scanium.yourdomain.com:443 \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary \
  | openssl enc -base64
```

**Solution**: Update `scanium.api.certificate.pin` in `local.properties`

***REMOVED******REMOVED******REMOVED******REMOVED*** 4. Base URL Not Set

**Symptoms**:
- UI shows: "Classification will use on-device processing until SCANIUM_API_BASE_URL is configured"
- Logcat: "Cloud classifier not configured (SCANIUM_API_BASE_URL is empty)"

**Cause**: `scanium.api.base.url` (and `.debug`) not set in `local.properties`

**Solution**: Add base URLs to `local.properties` and rebuild

***REMOVED******REMOVED******REMOVED******REMOVED*** 5. Wrong Build Variant Installed

**Symptoms**:
- Debug URL used when expecting release URL (or vice versa)

**Check installed variant**:
```bash
***REMOVED*** List installed packages
adb shell pm list packages -f | grep scanium

***REMOVED*** Should show:
***REMOVED*** - com.scanium.app.dev (dev flavor)
***REMOVED*** - com.scanium.app.beta (beta flavor)
```

**Solution**: Uninstall and reinstall correct variant
```bash
adb uninstall com.scanium.app.dev
./gradlew :androidApp:installDevDebug
```

***REMOVED******REMOVED******REMOVED******REMOVED*** 6. Changes Not Applied After Editing local.properties

**Cause**: BuildConfig is generated at build time, not runtime

**Solution**: Clean and rebuild
```bash
./gradlew clean
./gradlew :androidApp:installDevDebug
```

***REMOVED******REMOVED******REMOVED******REMOVED*** 7. Cloudflare Tunnel / Reverse Proxy Issues

**Symptoms**:
- Backend works on LAN but not via public domain
- Error: 502 Bad Gateway or 504 Gateway Timeout

**Check Cloudflare Tunnel**:
```bash
***REMOVED*** On NAS, check tunnel status
docker logs <cloudflare-tunnel-container>

***REMOVED*** Verify tunnel is connected
curl -v https://scanium.yourdomain.com/health
```

**Common Cloudflare Tunnel Issues**:
- Tunnel not running: Start tunnel container
- Wrong backend URL in tunnel config: Update `config.yml`
- Firewall blocking tunnel: Check NAS firewall rules

**Solution**: See `deploy/nas/compose/README.md` for tunnel setup

---

***REMOVED******REMOVED*** Advanced Topics

***REMOVED******REMOVED******REMOVED*** Multiple Devices / Environments

You can create environment-specific property files:

```bash
***REMOVED*** local.properties.home - Home LAN
scanium.api.base.url.debug=http://192.168.1.100:3000

***REMOVED*** local.properties.office - Office LAN
scanium.api.base.url.debug=http://10.0.1.50:3000

***REMOVED*** Switch configs
cp local.properties.home local.properties
./gradlew :androidApp:installDevDebug
```

***REMOVED******REMOVED******REMOVED*** Testing Backend Changes Without Rebuilding App

The app reads `BuildConfig` at compile time. To test backend changes without rebuilding:

1. Use the **instrumentation argument override** (for tests):
   ```bash
   ./gradlew :androidApp:connectedDevDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.SCANIUM_BASE_URL=http://192.168.1.200:3000
   ```

2. Or modify the backend to respond on multiple ports/IPs

***REMOVED******REMOVED******REMOVED*** Certificate Pinning (Production)

For enhanced security in production, enable certificate pinning:

1. Get your server's certificate pin:
   ```bash
   openssl s_client -servername scanium.yourdomain.com -connect scanium.yourdomain.com:443 \
     | openssl x509 -pubkey -noout \
     | openssl pkey -pubin -outform der \
     | openssl dgst -sha256 -binary \
     | openssl enc -base64
   ```

2. Add to `local.properties`:
   ```properties
   scanium.api.certificate.pin=sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
   ```

3. Rebuild release build

**Warning**: If you rotate certificates, you must update the pin and release a new app version.

***REMOVED******REMOVED******REMOVED*** Using ngrok for Testing

For temporary remote testing without setting up Cloudflare Tunnel:

```bash
***REMOVED*** On NAS, start ngrok
ngrok http 3000

***REMOVED*** Copy the HTTPS URL (e.g., https://abc123.ngrok.io)
***REMOVED*** Add to local.properties
scanium.api.base.url=https://abc123.ngrok.io

***REMOVED*** Rebuild and install
./gradlew :androidApp:installDevRelease
```

**Note**: ngrok URLs change on restart. This is for testing only.

***REMOVED******REMOVED******REMOVED*** CI/CD Integration

For automated builds (GitHub Actions, GitLab CI, etc.):

```yaml
***REMOVED*** Example GitHub Actions workflow
env:
  SCANIUM_API_BASE_URL: ${{ secrets.SCANIUM_API_BASE_URL }}
  SCANIUM_API_BASE_URL_DEBUG: ${{ secrets.SCANIUM_API_BASE_URL_DEBUG }}
  SCANIUM_API_KEY: ${{ secrets.SCANIUM_API_KEY }}

jobs:
  build:
    steps:
      - name: Build Debug APK
        run: ./gradlew :androidApp:assembleDevDebug

      - name: Build Release APK
        run: ./gradlew :androidApp:assembleDevRelease
```

---

***REMOVED******REMOVED*** NAS Backend Configuration

***REMOVED******REMOVED******REMOVED*** Finding Your NAS Configuration

1. **Backend URL**: Check your docker-compose file
   ```bash
   cat deploy/nas/compose/docker-compose.nas.backend.yml | grep -A 5 "ports:"
   ```

2. **Backend Health Endpoint**:
   ```bash
   curl http://localhost:3000/health
   ***REMOVED*** Or from another machine
   curl http://192.168.1.100:3000/health
   ```

3. **Cloudflare Tunnel Configuration**:
   ```bash
   cat deploy/nas/compose/docker-compose.nas.cloudflare.yml
   ***REMOVED*** Or check tunnel config
   cat deploy/cloudflare/config.yml
   ```

***REMOVED******REMOVED******REMOVED*** Typical NAS Setup

```
┌─────────────────────────────────────────────────────────────┐
│  Physical Device (Android Phone)                             │
│  ├─ Debug Build                                              │
│  │  └─ http://192.168.1.100:3000 (LAN)                       │
│  └─ Release Build                                            │
│     └─ https://scanium.yourdomain.com (Remote)               │
└─────────────────────────────────────────────────────────────┘
                          │                    │
                          │                    │
          ┌───────────────┘                    └──────────────┐
          │ LAN                                               │ Internet
          │                                                   │
          ▼                                                   ▼
┌─────────────────────┐                           ┌──────────────────┐
│  NAS (192.168.1.100)│                           │  Cloudflare      │
│  ├─ Backend :3000   │                           │  ├─ Tunnel       │
│  └─ Tunnel (docker) │◄──────────────────────────┤  └─ DNS          │
└─────────────────────┘                           └──────────────────┘
```

---

***REMOVED******REMOVED*** Verification Checklist

Before deploying to production:

- [ ] NAS backend is running and accessible on LAN
- [ ] Cloudflare Tunnel (or reverse proxy) is configured and working
- [ ] `local.properties` has both LAN and remote URLs configured
- [ ] Debug build connects to LAN backend successfully
- [ ] Release build connects to remote backend via HTTPS
- [ ] Certificate is valid (not self-signed) for release builds
- [ ] Certificate pinning configured (optional but recommended)
- [ ] API key is set and working
- [ ] Backend health endpoint returns 200 OK
- [ ] App can classify items (cloud classification works)
- [ ] App can fetch feature flags
- [ ] Network security configs are correct (HTTP for debug, HTTPS for release)
- [ ] Signing config is set up for release builds
- [ ] APK installs and runs on physical device

---

***REMOVED******REMOVED*** Quick Reference

***REMOVED******REMOVED******REMOVED*** Configuration Properties

| Property | Description | Example | Required |
|----------|-------------|---------|----------|
| `scanium.api.base.url` | Remote backend URL (HTTPS) | `https://scanium.yourdomain.com` | Yes (for release) |
| `scanium.api.base.url.debug` | LAN backend URL (HTTP) | `http://192.168.1.100:3000` | No (falls back to main URL) |
| `scanium.api.key` | API key | `your-api-key` | Yes |
| `scanium.api.certificate.pin` | Certificate pin (SHA-256) | `sha256/AAAA...` | No (recommended for prod) |
| `scanium.keystore.file` | Keystore path for signing | `/path/to/keystore.jks` | Yes (for release) |
| `scanium.keystore.password` | Keystore password | `secret` | Yes (for release) |
| `scanium.key.alias` | Key alias | `scanium-release` | Yes (for release) |
| `scanium.key.password` | Key password | `secret` | Yes (for release) |

***REMOVED******REMOVED******REMOVED*** Build Variants

| Command | Variant | URL Source | HTTP Allowed | Use Case |
|---------|---------|------------|--------------|----------|
| `installDevDebug` | devDebug | `.debug` → fallback | Yes | Local dev with LAN backend |
| `installDevRelease` | devRelease | `.base.url` | No (HTTPS only) | Internal testing with remote backend |
| `installBetaDebug` | betaDebug | `.debug` → fallback | Yes | Beta testing with LAN backend |
| `installBetaRelease` | betaRelease | `.base.url` | No (HTTPS only) | Public beta with remote backend |

***REMOVED******REMOVED******REMOVED*** Useful Commands

```bash
***REMOVED*** Build and install debug (LAN mode)
./gradlew :androidApp:installDevDebug

***REMOVED*** Build and install release (remote mode)
./gradlew :androidApp:installDevRelease

***REMOVED*** Clean build
./gradlew clean

***REMOVED*** Check device connectivity
adb shell ping -c 3 192.168.1.100

***REMOVED*** Test backend from device
adb shell curl -v http://192.168.1.100:3000/health

***REMOVED*** View app logs
adb logcat | grep -i scanium

***REMOVED*** List installed Scanium apps
adb shell pm list packages | grep scanium

***REMOVED*** Uninstall
adb uninstall com.scanium.app.dev

***REMOVED*** Run verification script
./scripts/verify-backend-config.sh
```

---

***REMOVED******REMOVED*** Related Documentation

- [NAS Deployment Guide](../deploy/nas/README.md) - Backend setup on Synology NAS
- [Cloudflare Tunnel Setup](../deploy/cloudflare/README.md) - Expose backend via HTTPS
- [Security Guide](SECURITY.md) - Certificate pinning and API key management
- [CI/CD Guide](CI_CD.md) - Automated builds and releases
- [Architecture](ARCHITECTURE.md) - Overall system architecture

---

***REMOVED******REMOVED*** Getting Help

If you encounter issues not covered in this guide:

1. Check the [Troubleshooting](***REMOVED***troubleshooting) section above
2. Search existing issues: https://github.com/ilpeppino/scanium/issues
3. Run the verification script: `./scripts/verify-backend-config.sh`
4. Collect logs: `adb logcat | grep -i scanium > scanium.log`
5. Create a new issue with:
   - Build variant used
   - Configuration (sanitized, no secrets)
   - Error messages from logcat
   - Output from verification script

---

**Last Updated**: 2026-01-01
**Maintainer**: Scanium Team
