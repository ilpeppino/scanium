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

***REMOVED******REMOVED*** HTTPS Enforcement & Cloudflare Calls

- Backend containers run with `NODE_ENV=production` by default and reject plain HTTP with `HTTPS_REQUIRED`. This keeps Cloudflare-facing endpoints TLS-only.
- Cloudflare adds `X-Forwarded-Proto: https`, so the backend accepts public `https://scanium.gtemp1.com/v1/assist/chat` requests:

```bash
curl -X POST https://scanium.gtemp1.com/v1/assist/chat \
  -H "x-api-key: $SCANIUM_API_KEY" \
  -H "content-type: application/json" \
  --data '{"items":[],"message":"ping"}'
```

- Mobile clients should first warm up the assistant to confirm that a provider is configured and reachable:

```bash
curl -X POST https://scanium.gtemp1.com/v1/assist/warmup \
  -H "x-api-key: $SCANIUM_API_KEY"
```

  Successful responses return `{"status":"ok","provider":"claude","model":"claude-sonnet-4-20250514","ts":"..."}`. Missing keys or disabled providers return an error payload and non-200 status so the app can stay in offline mode.

- For LAN smoke tests, set `ALLOW_INSECURE_HTTP=true` in the backend env file and call `http://localhost:8080/v1/assist/chat`. Only localhost/RFC1918 hosts are honored, and you still need `X-API-Key`.
- Missing or invalid API keys return `401 UNAUTHORIZED`; TLS violations return `403 HTTPS_REQUIRED`. Check logs for `reason=HTTPS_REQUIRED` if your curl fails.

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

***REMOVED******REMOVED*** Runtime URL Resolution (Debug Builds)

In debug builds, the base URL is resolved in this order:

1. **DevConfigOverride** (if explicitly set and not stale) - Stored in DataStore
2. **BuildConfig.SCANIUM_API_BASE_URL** - From `local.properties` at build time

***REMOVED******REMOVED******REMOVED*** Override Behavior

- **Stale overrides** are automatically cleared on app startup (after 30 days or app version change)
- **Release builds** ignore all overrides and always use BuildConfig
- **Developer Options** shows whether an override is active and provides a reset button

***REMOVED******REMOVED******REMOVED*** Viewing Override Status

In Developer Options → App Configuration:
- `Base URL` - Shows the current effective URL
- `Base URL (OVERRIDE)` - Shown when override is active (red text indicates potential issue)
- `BuildConfig URL` - Shows the original BuildConfig value when overridden

***REMOVED******REMOVED******REMOVED*** Clearing Overrides via ADB

```bash
***REMOVED*** View override (debug builds)
adb shell "run-as com.scanium.app.dev cat /data/data/com.scanium.app.dev/files/datastore/dev_config_override.preferences_pb"

***REMOVED*** Clear override
adb shell "run-as com.scanium.app.dev rm -rf /data/data/com.scanium.app.dev/files/datastore/dev_config_override.preferences_pb"

***REMOVED*** Force app restart
adb shell am force-stop com.scanium.app.dev
```

***REMOVED******REMOVED******REMOVED*** Reset via Developer Options

1. Open Settings → Developer Options
2. Find "App Configuration" section
3. If "Base URL (OVERRIDE)" is shown, click "Reset to BuildConfig default"

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

***REMOVED******REMOVED*** Diagnostics Status States

The Developer Options → Assistant / AI Diagnostics section shows detailed backend status that distinguishes between different failure modes.

***REMOVED******REMOVED******REMOVED*** Status Classifications

| Status | Color | Meaning | Action Required |
|--------|-------|---------|-----------------|
| **Assistant Ready** | 🟢 Green | Backend healthy, all prerequisites met | None |
| **Backend Unreachable** | 🔴 Red | Network error (DNS, timeout, SSL, connection refused) | Check network, firewall, DNS, tunnel status |
| **Backend Reachable — Invalid API Key** | 🟠 Orange | HTTP 401/403 - Backend IS reachable, but auth failed | Check/update API key |
| **Backend Reachable — Server Error** | 🟠 Orange | HTTP 5xx - Backend IS reachable, but server having issues | Check backend logs, container health |
| **Backend Reachable — Endpoint Not Found** | 🟠 Orange | HTTP 404 - Wrong URL or endpoint not deployed | Verify base URL and backend version |
| **Backend Not Configured** | ⚪ Gray | No URL or API key configured | Configure in `local.properties` |
| **No Network Connection** | 🔴 Red | Device not connected to internet | Enable WiFi/cellular |

***REMOVED******REMOVED******REMOVED*** Debug Details

When there's an error, the diagnostics display shows:
- The HTTP method and endpoint tested (e.g., `GET /health`)
- The HTTP status code if available (e.g., `-> 401`)

Example debug strings:
- `GET /health -> 200` - Success
- `GET /health -> 401` - Unauthorized
- `GET /health -> 502` - Cloudflare/proxy error
- `GET /health` (no status) - Network error before HTTP response

***REMOVED******REMOVED******REMOVED*** Endpoints Used by Scanium

| Feature | Endpoint | Method | Auth Required |
|---------|----------|--------|---------------|
| Health check | `/health` | GET | Optional (API key improves diagnostics) |
| Cloud classification | `/v1/classify` | POST | Yes |
| AI Assistant | `/v1/assist/chat` | POST | Yes |

***REMOVED******REMOVED******REMOVED*** Common Diagnostic Scenarios

***REMOVED******REMOVED******REMOVED******REMOVED*** ✅ Health 200, but Assistant shows "Invalid API Key"

**Symptoms:**
- Backend card shows "Healthy OK (200)"
- Banner shows "Backend Reachable — Invalid API Key"
- Debug: `GET /health -> 401`

**Cause:** The `/health` endpoint accepts requests without authentication, but the app sends the API key for better diagnostics. If the key is invalid or missing, you get 401.

**Fix:**
1. Check API key is set in `local.properties`:
   ```bash
   grep scanium.api.key local.properties
   ```
2. Verify key is valid with curl:
   ```bash
   curl -H "X-API-Key: YOUR_KEY" https://scanium.gtemp1.com/health
   ```
3. Regenerate key if needed and update `local.properties`

***REMOVED******REMOVED******REMOVED******REMOVED*** ✅ Health 200, but classifier returns 404

**Symptoms:**
- Health check works
- Classification fails with 404

**Cause:** Base URL points to wrong server or backend version missing `/v1/classify` endpoint.

**Fix:**
1. Verify base URL:
   ```bash
   grep scanium.api.base.url local.properties
   ```
2. Test classify endpoint:
   ```bash
   curl -X POST -H "X-API-Key: KEY" https://scanium.gtemp1.com/v1/classify
   ***REMOVED*** Should return 400 (missing image) not 404
   ```
3. Check backend version supports classification

***REMOVED******REMOVED******REMOVED******REMOVED*** ✅ Health returns 502/530 (Cloudflare errors)

**Symptoms:**
- Banner shows "Backend Reachable — Server Error"
- Debug: `GET /health -> 502` or `-> 530`

**Cause:** Cloudflare Tunnel is working but backend container is down or not responding.

**Fix:**
1. Check backend container:
   ```bash
   docker logs scanium-backend
   docker ps | grep scanium
   ```
2. Check cloudflared tunnel:
   ```bash
   docker logs scanium-cloudflared
   ```
3. Restart if needed:
   ```bash
   docker compose restart scanium-backend
   ```

***REMOVED******REMOVED******REMOVED******REMOVED*** ✅ Complete network failure

**Symptoms:**
- Banner shows "Backend Unreachable"
- Debug shows endpoint but no status code

**Cause:** DNS failure, SSL error, connection refused, or timeout.

**Fix:**
1. Test from device:
   ```bash
   adb shell curl -v https://scanium.gtemp1.com/health
   ```
2. Check DNS resolution:
   ```bash
   adb shell nslookup scanium.gtemp1.com
   ```
3. Check SSL certificate:
   ```bash
   openssl s_client -connect scanium.gtemp1.com:443 -servername scanium.gtemp1.com
   ```

***REMOVED******REMOVED*** Quick Verification Commands

***REMOVED******REMOVED******REMOVED*** From development machine

```bash
***REMOVED*** Health check (no auth)
curl -s https://scanium.gtemp1.com/health

***REMOVED*** Health check with API key
curl -s -H "X-API-Key: YOUR_KEY" https://scanium.gtemp1.com/health

***REMOVED*** Test classify endpoint
curl -s -X POST -H "X-API-Key: YOUR_KEY" \
     -H "Content-Type: multipart/form-data" \
     -F "image=@test.jpg" \
     https://scanium.gtemp1.com/v1/classify

***REMOVED*** Check backend container logs
docker logs scanium-backend --tail 50

***REMOVED*** Check cloudflared tunnel logs
docker logs scanium-cloudflared --tail 50
```

***REMOVED******REMOVED******REMOVED*** From Android device

```bash
***REMOVED*** Test from device via adb
adb shell curl -s https://scanium.gtemp1.com/health

***REMOVED*** View app networking logs
adb logcat | grep -E "(DiagnosticsRepo|CloudClassifier|AssistantRepo|OkHttp)"

***REMOVED*** View full app logs
adb logcat -s ScaniumApplication DiagnosticsRepository CloudClassifier AssistantRepository
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

***REMOVED******REMOVED******REMOVED*** Developer Options shows wrong/old URL

This can happen if a runtime override was set previously (e.g., old ngrok URL):

1. **Check if override is active**: Look for "Base URL (OVERRIDE)" in Developer Options

2. **Reset via UI**: Click "Reset to BuildConfig default" button

3. **Reset via ADB**:
   ```bash
   ***REMOVED*** Clear override DataStore
   adb shell "run-as com.scanium.app.dev rm -rf /data/data/com.scanium.app.dev/files/datastore/dev_config_override.preferences_pb"
   ***REMOVED*** Force restart
   adb shell am force-stop com.scanium.app.dev
   ```

4. **Full reinstall** (if still issues):
   ```bash
   adb uninstall com.scanium.app.dev
   ./gradlew :androidApp:installDevDebug
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
