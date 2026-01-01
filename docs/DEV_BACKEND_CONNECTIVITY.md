# Developer Backend Connectivity Guide

This guide explains how to configure the Scanium Android app to connect to your backend server during development.

## Connect App to NAS Backend via Cloudflare

Quick checklist for connecting to the NAS-hosted backend at `https://scanium.gtemp1.com`:

```bash
# Step 1: Set your API key
export SCANIUM_API_KEY="your-api-key-here"

# Step 2: Configure local.properties for Cloudflare backend
./scripts/android/set-backend-cloudflare-dev.sh

# Step 3: Build, install, and test connectivity
./scripts/android/build-install-devdebug.sh

# Step 4: Verify from device (optional)
adb shell curl -s https://scanium.gtemp1.com/health
```

The scripts will:
- Write `scanium.api.base.url.debug=https://scanium.gtemp1.com` to `local.properties`
- Write `scanium.api.key` if `SCANIUM_API_KEY` is set
- Build and install the devDebug APK
- Run a connectivity smoke test from the device

## Quick Start (Generic)

```bash
# 1. Configure backend URL and API key
./scripts/android-configure-backend-dev.sh \
    --url https://your-backend.example.com \
    --key your-api-key

# 2. Build and install
./scripts/android-build-install-dev.sh

# 3. Verify connectivity
./scripts/dev/verify-backend-config.sh devDebug
```

## Configuration Methods

### Method 1: Configuration Script (Recommended)

The configuration script writes settings to `local.properties` (never committed to git):

```bash
# Basic configuration with Cloudflare URL
./scripts/android-configure-backend-dev.sh \
    --url https://scanium-api.your-domain.com \
    --key your-api-key

# With separate LAN URL for faster debug builds
./scripts/android-configure-backend-dev.sh \
    --url https://scanium-api.your-domain.com \
    --debug-url http://192.168.1.100:3000 \
    --key your-api-key
```

### Method 2: Manual local.properties

Edit `local.properties` in the repository root:

```properties
# Backend URL for release builds (and debug if debug URL not set)
scanium.api.base.url=https://scanium-api.your-domain.com

# Optional: Debug-specific URL (for LAN development)
scanium.api.base.url.debug=http://192.168.1.100:3000

# API key for authentication
scanium.api.key=your-api-key-here
```

### Method 3: Environment Variables

Set environment variables before building:

```bash
export SCANIUM_API_BASE_URL=https://scanium-api.your-domain.com
export SCANIUM_API_BASE_URL_DEBUG=http://192.168.1.100:3000
export SCANIUM_API_KEY=your-api-key

./gradlew :androidApp:installDevDebug
```

## Cloudflare Tunnel Setup

If your backend runs on a NAS or local server, you can expose it via Cloudflare Tunnel:

### 1. Install cloudflared on your NAS/server

```bash
# Linux (amd64)
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -o cloudflared
chmod +x cloudflared
sudo mv cloudflared /usr/local/bin/

# macOS
brew install cloudflare/cloudflare/cloudflared
```

### 2. Create a tunnel

```bash
# Authenticate with Cloudflare
cloudflared tunnel login

# Create tunnel
cloudflared tunnel create scanium-backend

# Configure tunnel (creates ~/.cloudflared/config.yml)
cat > ~/.cloudflared/config.yml << EOF
tunnel: scanium-backend
credentials-file: ~/.cloudflared/<tunnel-id>.json

ingress:
  - hostname: scanium-api.your-domain.com
    service: http://localhost:3000
  - service: http_status:404
EOF

# Add DNS record
cloudflared tunnel route dns scanium-backend scanium-api.your-domain.com

# Run tunnel
cloudflared tunnel run scanium-backend
```

### 3. Configure Android app

```bash
./scripts/android-configure-backend-dev.sh \
    --url https://scanium-api.your-domain.com \
    --key your-api-key
```

## URL Configuration Behavior

| Build Type | URL Source | Fallback |
|------------|------------|----------|
| devDebug   | `scanium.api.base.url.debug` | `scanium.api.base.url` |
| devRelease | `scanium.api.base.url` | - |
| betaDebug  | `scanium.api.base.url.debug` | `scanium.api.base.url` |
| betaRelease| `scanium.api.base.url` | - |

**Note:** Debug builds allow HTTP (cleartext) for LAN development. Release builds require HTTPS.

## Runtime URL Resolution (Debug Builds)

In debug builds, the base URL is resolved in this order:

1. **DevConfigOverride** (if explicitly set and not stale) - Stored in DataStore
2. **BuildConfig.SCANIUM_API_BASE_URL** - From `local.properties` at build time

### Override Behavior

- **Stale overrides** are automatically cleared on app startup (after 30 days or app version change)
- **Release builds** ignore all overrides and always use BuildConfig
- **Developer Options** shows whether an override is active and provides a reset button

### Viewing Override Status

In Developer Options → App Configuration:
- `Base URL` - Shows the current effective URL
- `Base URL (OVERRIDE)` - Shown when override is active (red text indicates potential issue)
- `BuildConfig URL` - Shows the original BuildConfig value when overridden

### Clearing Overrides via ADB

```bash
# View override (debug builds)
adb shell "run-as com.scanium.app.dev cat /data/data/com.scanium.app.dev/files/datastore/dev_config_override.preferences_pb"

# Clear override
adb shell "run-as com.scanium.app.dev rm -rf /data/data/com.scanium.app.dev/files/datastore/dev_config_override.preferences_pb"

# Force app restart
adb shell am force-stop com.scanium.app.dev
```

### Reset via Developer Options

1. Open Settings → Developer Options
2. Find "App Configuration" section
3. If "Base URL (OVERRIDE)" is shown, click "Reset to BuildConfig default"

## Build Validation

The build system validates backend configuration:

- **devDebug builds** fail if no backend URL is configured
- Run `./gradlew :androidApp:validateBackendConfig` to check configuration

### Validation Task Output

```
┌─────────────────────────────────────────────────────────────┐
│  Scanium Backend Configuration                             │
├─────────────────────────────────────────────────────────────┤
│  Debug URL (effective):  https://scanium-api.example.com   │
│  Release URL:            https://scanium-api.example.com   │
│  API Key:                Cr3UnvP9...                       │
└─────────────────────────────────────────────────────────────┘
```

## Verifying Connectivity from Device

### Using the verification script

```bash
./scripts/dev/verify-backend-config.sh devDebug
```

This checks:
- Configuration values in `local.properties`
- BuildConfig values in the compiled app
- Network connectivity from the device
- Installed app versions

### Manual verification via adb

```bash
# Test /health endpoint from device
adb shell curl -s https://scanium-api.your-domain.com/health

# Check app logs
adb logcat | grep -i scanium

# Check network connectivity
adb shell ping -c 3 scanium-api.your-domain.com
```

## Troubleshooting

### Build fails with "Backend URL not configured"

1. Verify `local.properties` exists and contains the URL:
   ```bash
   cat local.properties | grep scanium.api
   ```

2. Re-run configuration:
   ```bash
   ./scripts/android-configure-backend-dev.sh --url YOUR_URL --key YOUR_KEY
   ```

### App can't reach backend

1. **Check device network**: Ensure the device is on a network that can reach the backend.

2. **For LAN URLs**: Both device and server must be on the same network.

3. **For Cloudflare URLs**: Verify the tunnel is running:
   ```bash
   cloudflared tunnel info scanium-backend
   ```

4. **Check certificate pinning**: If using certificate pinning, ensure the pin is correct:
   ```bash
   # Get certificate pin
   openssl s_client -servername YOUR_HOST -connect YOUR_HOST:443 \
       | openssl x509 -pubkey -noout \
       | openssl pkey -pubin -outform der \
       | openssl dgst -sha256 -binary \
       | openssl enc -base64
   ```

### HTTP not working on device

Android blocks cleartext (HTTP) traffic by default. For debug builds:

1. Verify `network_security_config_debug.xml` allows cleartext for your domain
2. For LAN IPs, add them to the cleartext allowlist

### Environment variables not being read

Gradle may cache configuration. Try:
```bash
./gradlew --stop
./gradlew :androidApp:assembleDevDebug
```

### Developer Options shows wrong/old URL

This can happen if a runtime override was set previously (e.g., old ngrok URL):

1. **Check if override is active**: Look for "Base URL (OVERRIDE)" in Developer Options

2. **Reset via UI**: Click "Reset to BuildConfig default" button

3. **Reset via ADB**:
   ```bash
   # Clear override DataStore
   adb shell "run-as com.scanium.app.dev rm -rf /data/data/com.scanium.app.dev/files/datastore/dev_config_override.preferences_pb"
   # Force restart
   adb shell am force-stop com.scanium.app.dev
   ```

4. **Full reinstall** (if still issues):
   ```bash
   adb uninstall com.scanium.app.dev
   ./gradlew :androidApp:installDevDebug
   ```

## Security Notes

- **Never commit** `local.properties` to git (it's in `.gitignore`)
- **API keys** are masked in script output (only first 8 characters shown)
- **Release builds** enforce HTTPS and don't allow cleartext traffic
- **Certificate pinning** is optional but recommended for production

## Scripts Reference

| Script | Purpose |
|--------|---------|
| `scripts/android/set-backend-cloudflare-dev.sh` | Configure for Cloudflare NAS backend |
| `scripts/android/build-install-devdebug.sh` | Build, install, and smoke test |
| `scripts/android-configure-backend-dev.sh` | Configure backend URL and API key (generic) |
| `scripts/android-build-install-dev.sh` | Build and install devDebug |
| `scripts/dev/verify-backend-config.sh` | Verify configuration and connectivity |

## See Also

- [Backend Deployment Guide](./BACKEND_DEPLOYMENT.md)
- [Security Configuration](./SECURITY.md)
- [Network Security Config](../androidApp/src/debug/res/xml/network_security_config_debug.xml)
