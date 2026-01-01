***REMOVED*** Developer Backend Connectivity Guide

This guide explains how to configure the Scanium Android app to connect to your backend server during development.

***REMOVED******REMOVED*** Connect App to NAS Backend via Cloudflare

Quick checklist for connecting to the NAS-hosted backend at `https://scanium.gtemp1.com`:

```bash
***REMOVED*** Step 1: Set your API key
export SCANIUM_API_KEY="your-api-key-here"

***REMOVED*** Step 2: Configure local.properties for Cloudflare backend
./scripts/android/set-backend-cloudflare-dev.sh

***REMOVED*** Step 3: Build, install, and test connectivity
./scripts/android/build-install-devdebug.sh

***REMOVED*** Step 4: Verify from device (optional)
adb shell curl -s https://scanium.gtemp1.com/health
```

The scripts will:
- Write `scanium.api.base.url.debug=https://scanium.gtemp1.com` to `local.properties`
- Write `scanium.api.key` if `SCANIUM_API_KEY` is set
- Build and install the devDebug APK
- Run a connectivity smoke test from the device

***REMOVED******REMOVED*** Quick Start (Generic)

```bash
***REMOVED*** 1. Configure backend URL and API key
./scripts/android-configure-backend-dev.sh \
    --url https://your-backend.example.com \
    --key your-api-key

***REMOVED*** 2. Build and install
./scripts/android-build-install-dev.sh

***REMOVED*** 3. Verify connectivity
./scripts/dev/verify-backend-config.sh devDebug
```

***REMOVED******REMOVED*** Configuration Methods

***REMOVED******REMOVED******REMOVED*** Method 1: Configuration Script (Recommended)

The configuration script writes settings to `local.properties` (never committed to git):

```bash
***REMOVED*** Basic configuration with Cloudflare URL
./scripts/android-configure-backend-dev.sh \
    --url https://scanium-api.your-domain.com \
    --key your-api-key

***REMOVED*** With separate LAN URL for faster debug builds
./scripts/android-configure-backend-dev.sh \
    --url https://scanium-api.your-domain.com \
    --debug-url http://192.168.1.100:3000 \
    --key your-api-key
```

***REMOVED******REMOVED******REMOVED*** Method 2: Manual local.properties

Edit `local.properties` in the repository root:

```properties
***REMOVED*** Backend URL for release builds (and debug if debug URL not set)
scanium.api.base.url=https://scanium-api.your-domain.com

***REMOVED*** Optional: Debug-specific URL (for LAN development)
scanium.api.base.url.debug=http://192.168.1.100:3000

***REMOVED*** API key for authentication
scanium.api.key=your-api-key-here
```

***REMOVED******REMOVED******REMOVED*** Method 3: Environment Variables

Set environment variables before building:

```bash
export SCANIUM_API_BASE_URL=https://scanium-api.your-domain.com
export SCANIUM_API_BASE_URL_DEBUG=http://192.168.1.100:3000
export SCANIUM_API_KEY=your-api-key

./gradlew :androidApp:installDevDebug
```

***REMOVED******REMOVED*** Cloudflare Tunnel Setup

If your backend runs on a NAS or local server, you can expose it via Cloudflare Tunnel:

***REMOVED******REMOVED******REMOVED*** 1. Install cloudflared on your NAS/server

```bash
***REMOVED*** Linux (amd64)
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -o cloudflared
chmod +x cloudflared
sudo mv cloudflared /usr/local/bin/

***REMOVED*** macOS
brew install cloudflare/cloudflare/cloudflared
```

***REMOVED******REMOVED******REMOVED*** 2. Create a tunnel

```bash
***REMOVED*** Authenticate with Cloudflare
cloudflared tunnel login

***REMOVED*** Create tunnel
cloudflared tunnel create scanium-backend

***REMOVED*** Configure tunnel (creates ~/.cloudflared/config.yml)
cat > ~/.cloudflared/config.yml << EOF
tunnel: scanium-backend
credentials-file: ~/.cloudflared/<tunnel-id>.json

ingress:
  - hostname: scanium-api.your-domain.com
    service: http://localhost:3000
  - service: http_status:404
EOF

***REMOVED*** Add DNS record
cloudflared tunnel route dns scanium-backend scanium-api.your-domain.com

***REMOVED*** Run tunnel
cloudflared tunnel run scanium-backend
```

***REMOVED******REMOVED******REMOVED*** 3. Configure Android app

```bash
./scripts/android-configure-backend-dev.sh \
    --url https://scanium-api.your-domain.com \
    --key your-api-key
```

***REMOVED******REMOVED*** URL Configuration Behavior

| Build Type | URL Source | Fallback |
|------------|------------|----------|
| devDebug   | `scanium.api.base.url.debug` | `scanium.api.base.url` |
| devRelease | `scanium.api.base.url` | - |
| betaDebug  | `scanium.api.base.url.debug` | `scanium.api.base.url` |
| betaRelease| `scanium.api.base.url` | - |

**Note:** Debug builds allow HTTP (cleartext) for LAN development. Release builds require HTTPS.

***REMOVED******REMOVED*** Build Validation

The build system validates backend configuration:

- **devDebug builds** fail if no backend URL is configured
- Run `./gradlew :androidApp:validateBackendConfig` to check configuration

***REMOVED******REMOVED******REMOVED*** Validation Task Output

```
┌─────────────────────────────────────────────────────────────┐
│  Scanium Backend Configuration                             │
├─────────────────────────────────────────────────────────────┤
│  Debug URL (effective):  https://scanium-api.example.com   │
│  Release URL:            https://scanium-api.example.com   │
│  API Key:                Cr3UnvP9...                       │
└─────────────────────────────────────────────────────────────┘
```

***REMOVED******REMOVED*** Verifying Connectivity from Device

***REMOVED******REMOVED******REMOVED*** Using the verification script

```bash
./scripts/dev/verify-backend-config.sh devDebug
```

This checks:
- Configuration values in `local.properties`
- BuildConfig values in the compiled app
- Network connectivity from the device
- Installed app versions

***REMOVED******REMOVED******REMOVED*** Manual verification via adb

```bash
***REMOVED*** Test /health endpoint from device
adb shell curl -s https://scanium-api.your-domain.com/health

***REMOVED*** Check app logs
adb logcat | grep -i scanium

***REMOVED*** Check network connectivity
adb shell ping -c 3 scanium-api.your-domain.com
```

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** Build fails with "Backend URL not configured"

1. Verify `local.properties` exists and contains the URL:
   ```bash
   cat local.properties | grep scanium.api
   ```

2. Re-run configuration:
   ```bash
   ./scripts/android-configure-backend-dev.sh --url YOUR_URL --key YOUR_KEY
   ```

***REMOVED******REMOVED******REMOVED*** App can't reach backend

1. **Check device network**: Ensure the device is on a network that can reach the backend.

2. **For LAN URLs**: Both device and server must be on the same network.

3. **For Cloudflare URLs**: Verify the tunnel is running:
   ```bash
   cloudflared tunnel info scanium-backend
   ```

4. **Check certificate pinning**: If using certificate pinning, ensure the pin is correct:
   ```bash
   ***REMOVED*** Get certificate pin
   openssl s_client -servername YOUR_HOST -connect YOUR_HOST:443 \
       | openssl x509 -pubkey -noout \
       | openssl pkey -pubin -outform der \
       | openssl dgst -sha256 -binary \
       | openssl enc -base64
   ```

***REMOVED******REMOVED******REMOVED*** HTTP not working on device

Android blocks cleartext (HTTP) traffic by default. For debug builds:

1. Verify `network_security_config_debug.xml` allows cleartext for your domain
2. For LAN IPs, add them to the cleartext allowlist

***REMOVED******REMOVED******REMOVED*** Environment variables not being read

Gradle may cache configuration. Try:
```bash
./gradlew --stop
./gradlew :androidApp:assembleDevDebug
```

***REMOVED******REMOVED*** Security Notes

- **Never commit** `local.properties` to git (it's in `.gitignore`)
- **API keys** are masked in script output (only first 8 characters shown)
- **Release builds** enforce HTTPS and don't allow cleartext traffic
- **Certificate pinning** is optional but recommended for production

***REMOVED******REMOVED*** Scripts Reference

| Script | Purpose |
|--------|---------|
| `scripts/android/set-backend-cloudflare-dev.sh` | Configure for Cloudflare NAS backend |
| `scripts/android/build-install-devdebug.sh` | Build, install, and smoke test |
| `scripts/android-configure-backend-dev.sh` | Configure backend URL and API key (generic) |
| `scripts/android-build-install-dev.sh` | Build and install devDebug |
| `scripts/dev/verify-backend-config.sh` | Verify configuration and connectivity |

***REMOVED******REMOVED*** See Also

- [Backend Deployment Guide](./BACKEND_DEPLOYMENT.md)
- [Security Configuration](./SECURITY.md)
- [Network Security Config](../androidApp/src/debug/res/xml/network_security_config_debug.xml)
