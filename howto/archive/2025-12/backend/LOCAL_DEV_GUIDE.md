> Archived on 2025-12-20: backend notes kept for reference; see docs/ARCHITECTURE.md for current
> state.

# Local Development & Mobile Testing Guide

**Complete, tested guide for running the backend locally and testing eBay OAuth with your Android
device.**

Last updated: 2025-12-12 (Tested on macOS with Colima)

---

## 🎯 Goal

1. Run backend on your Mac (localhost)
2. Expose it publicly via ngrok
3. Test eBay OAuth flow from Android device
4. Verify everything works before NAS deployment

---

## 📋 Prerequisites

✅ You need:

- **Node.js 20** installed (`node --version`)
- **Colima** or Docker Desktop (`colima version` or `docker version`)
- **eBay sandbox credentials** (Client ID + Client Secret
  from [developer.ebay.com](https://developer.ebay.com/my/keys))
- **Android device** or emulator
- **Mac** with ports 8080 and 5432 available

---

## Part 1: Backend Local Setup

### Step 1: Verify Prerequisites

```bash
# Check Node.js version (must be 20+)
node --version
# Expected: v20.x.x

# Check if using Colima (macOS Docker alternative)
colima version
# Expected: colima version 0.x.x

# Check Colima status
colima status
# Expected: colima is running

# If Colima is not running:
colima start --cpu 4 --memory 8

# Verify Docker context is set to Colima
docker context ls
# Expected: colima * (active)

# If not active, switch to it:
docker context use colima
```

### Step 2: Install Dependencies

```bash
cd /Users/family/dev/scanium/backend
npm install
```

**Expected output:**

```
added 271 packages, and audited 271 packages in 3s
```

### Step 3: Configure PostgreSQL with Port Mapping

The `docker-compose.yml` needs to expose PostgreSQL on localhost for migrations.

**Verify postgres section in `docker-compose.yml`:**

```yaml
postgres:
  image: postgres:16-alpine
  container_name: scanium-postgres
  ports:
    - '5432:5432'  # Must be present for local development
  environment:
    POSTGRES_USER: ${POSTGRES_USER:-scanium}
    POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-scanium}
    POSTGRES_DB: ${POSTGRES_DB:-scanium}
  volumes:
    - postgres_data:/var/lib/postgresql/data
  networks:
    - scanium-network
  healthcheck:
    test: ['CMD-SHELL', 'pg_isready -U ${POSTGRES_USER:-scanium}']
    interval: 10s
    timeout: 5s
    retries: 5
```

### Step 4: Start PostgreSQL

```bash
# Use 'docker compose' (with space, not hyphen) for Compose V2
docker compose up -d postgres
```

**Expected output:**

```
 Container scanium-postgres Created
 Container scanium-postgres Started
```

**Verify container is running:**

```bash
docker ps --filter name=scanium-postgres
```

**Expected:**

```
CONTAINER ID   IMAGE                PORTS                    NAMES
abc123def456   postgres:16-alpine   0.0.0.0:5432->5432/tcp   scanium-postgres
```

**Check logs:**

```bash
docker logs scanium-postgres
```

Look for: `database system is ready to accept connections`

### Step 5: Create and Configure `.env` File

```bash
# Create .env from example
cp .env.example .env

# Generate a secure session secret
openssl rand -base64 64
```

**Edit `.env` file:**

```bash
# Application
NODE_ENV=development
PORT=8080

# Public URL (will update after ngrok setup)
PUBLIC_BASE_URL=http://localhost:8080

# Database (localhost for local dev, postgres for Docker container)
DATABASE_URL=postgresql://scanium:scanium@localhost:5432/scanium

# eBay OAuth - YOUR SANDBOX CREDENTIALS
EBAY_ENV=sandbox
EBAY_CLIENT_ID=REDACTED_EBAY_CLIENT_ID
EBAY_CLIENT_SECRET=REDACTED_EBAY_CLIENT_SECRET
EBAY_TOKEN_ENCRYPTION_KEY=change_me_to_32+_char_secret_for_tokens
EBAY_REDIRECT_PATH=/auth/ebay/callback
EBAY_SCOPES=https://api.ebay.com/oauth/api_scope https://api.ebay.com/oauth/api_scope/sell.inventory https://api.ebay.com/oauth/api_scope/sell.fulfillment https://api.ebay.com/oauth/api_scope/sell.account

# Session Security (paste output from openssl command above)
SESSION_SIGNING_SECRET=REPLACE_WITH_BASE64_64B_SECRET

# CORS Origins
CORS_ORIGINS=scanium://,http://localhost:3000

# PostgreSQL credentials (must match docker-compose.yml)
POSTGRES_USER=scanium
POSTGRES_PASSWORD=scanium
POSTGRES_DB=scanium

# Cloudflare Tunnel (leave empty for local dev)
CLOUDFLARED_TOKEN=
```

**Critical Notes:**

- `DATABASE_URL` uses **`localhost`** (not `postgres`) because we're running migrations from host
  machine
- eBay credentials come
  from [eBay Developer Portal - Sandbox Keys](https://developer.ebay.com/my/keys)
- `SESSION_SIGNING_SECRET` must be at least 64 characters

### Step 6: Generate Prisma Client

```bash
npm run prisma:generate
```

**Expected output:**

```
✔ Generated Prisma Client (v5.22.0) to ./node_modules/@prisma/client in 25ms
```

### Step 7: Run Database Migrations

If migrations fail due to advisory locks, clear stale connections first:

```bash
# Clear any stale advisory locks (if needed)
docker exec scanium-postgres psql -U scanium -d scanium -c "SELECT pg_advisory_unlock_all();"

# Run migrations
npx prisma migrate dev --name init
```

**Expected output:**

```
Applying migration `20251212155540_init`

The following migration(s) have been created and applied from new schema changes:

migrations/
  └─ 20251212155540_init/
    └─ migration.sql

Your database is now in sync with your schema.
```

**If you encounter "advisory lock timeout":**

```bash
# Check for locks
docker exec scanium-postgres psql -U scanium -d scanium -c "SELECT * FROM pg_locks WHERE locktype = 'advisory';"

# Terminate stale connections
docker exec scanium-postgres psql -U scanium -d scanium -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'scanium' AND pid != pg_backend_pid();"

# Try migration again
npx prisma migrate dev --name init
```

---

## Part 2: Start Backend & Test Locally

### Step 8: Start Development Server

```bash
npm run dev
```

**Expected output:**

```
📝 Loading configuration...
✅ Configuration loaded (env: development)
🚀 Building application...
✅ Application built
✅ Server listening on http://0.0.0.0:8080
🌍 Public URL: http://localhost:8080
🏪 eBay environment: sandbox
[INFO]: Server listening at http://0.0.0.0:8080
```

**Keep this terminal running.**

### Step 9: Test Endpoints (New Terminal)

Open a **new terminal window** and test:

```bash
# Health check
curl http://localhost:8080/healthz

# Expected: {"status":"ok","timestamp":"2025-12-12T..."}

# Readiness check (database connection)
curl http://localhost:8080/readyz

# Expected: {"status":"ok","database":"connected","timestamp":"..."}

# API info
curl http://localhost:8080/

# Expected: Full API info with endpoints list

# eBay connection status (should be disconnected initially)
curl http://localhost:8080/auth/ebay/status

# Expected: {"connected":false}
```

✅ **If all return 200 OK, your backend is working!**

---

## Part 3: Expose Backend via ngrok

Your Android device can't access `localhost:8080` on your Mac. We need to expose it publicly using
ngrok.

### Step 10: Install and Configure ngrok

**Install via Homebrew:**

```bash
brew install ngrok/ngrok/ngrok
```

**Sign up for free ngrok account:**

1. Go to [ngrok.com/signup](https://ngrok.com/signup)
2. Create free account
3. Get your authtoken
   from [dashboard.ngrok.com/get-started/your-authtoken](https://dashboard.ngrok.com/get-started/your-authtoken)

**Authenticate ngrok:**

```bash
# Replace YOUR_AUTHTOKEN with your actual token from ngrok dashboard
ngrok config add-authtoken YOUR_AUTHTOKEN_HERE
```

**Expected output:**

```
Authtoken saved to configuration file: /Users/family/Library/Application Support/ngrok/ngrok.yml
```

### Step 11: Start ngrok Tunnel (New Terminal)

Open a **third terminal window**:

```bash
ngrok http 8080
```

**Expected output:**

```
ngrok

Session Status                online
Account                       your@email.com (Plan: Free)
Version                       3.34.1
Region                        United States (us)
Latency                       -
Web Interface                 http://127.0.0.1:4040
Forwarding                    https://brayan-vizarded-undomestically.ngrok-free.dev -> http://localhost:8080

Connections                   ttl     opn     rt1     rt5     p50     p90
                              0       0       0.00    0.00    0.00    0.00
```

**IMPORTANT: Copy the Forwarding URL!**

Example: `https://brayan-vizarded-undomestically.ngrok-free.dev`

**Keep this terminal running** - ngrok must stay active.

### Step 12: Update `.env` with ngrok URL

**Stop your dev server** (Ctrl+C in the terminal running `npm run dev`).

**Edit `.env`:**

```bash
# Change PUBLIC_BASE_URL to your ngrok URL
PUBLIC_BASE_URL=https://brayan-vizarded-undomestically.ngrok-free.dev
```

**Restart dev server:**

```bash
npm run dev
```

Wait for: `✅ Server listening on http://0.0.0.0:8080`

### Step 13: Test via ngrok

From any device with internet (even your phone):

```bash
# Replace with YOUR actual ngrok URL
curl https://brayan-vizarded-undomestically.ngrok-free.dev/healthz
```

**Expected:** `{"status":"ok","timestamp":"..."}`

**Test OAuth start endpoint:**

```bash
curl -X POST https://brayan-vizarded-undomestically.ngrok-free.dev/auth/ebay/start
```

**Expected response:**

```json
{
  "authorizeUrl": "https://auth.sandbox.ebay.com/oauth2/authorize?client_id=REDACTED_EBAY_CLIENT_ID&redirect_uri=https%3A%2F%2Fbrayan-vizarded-undomestically.ngrok-free.dev%2Fauth%2Febay%2Fcallback&response_type=code&scope=https%3A%2F%2Fapi.ebay.com%2Foauth%2Fapi_scope..."
}
```

✅ **If you see `authorizeUrl`, ngrok and backend are working together!**

---

## Part 4: Configure eBay Developer Portal

### Step 14: Add RuName in eBay Developer Portal

1. Go to [eBay Developer Portal - My Keys](https://developer.ebay.com/my/keys)
2. Under **Sandbox Keys**, find your application keyset
3. Scroll to **User Tokens** section
4. Click **Get a Token from eBay via Your Application**
5. Click **Add RuName** button

**Fill in RuName form:**

- **Your Privacy Policy URL**: `https://google.com/privacy` (any valid URL)
- **Your Auth Accepted URL**:
  `https://brayan-vizarded-undomestically.ngrok-free.dev/auth/ebay/callback`
    - ⚠️ **CRITICAL:** Use YOUR actual ngrok URL
    - Must end with `/auth/ebay/callback`
    - Must be HTTPS (ngrok provides this)
    - No trailing slash

Click **Save**

### Step 15: Activate RuName

1. Still on the same page, click **Get a Token from eBay via Your Application**
2. Select your newly created RuName from dropdown
3. Click **Sign in to Sandbox**
4. Log in with your eBay **sandbox test user** (not your developer account)
    - Don't have a sandbox user? Create one
      at [developer.ebay.com/sandbox](https://developer.ebay.com/sandbox)
5. Authorize the application
6. You'll receive a token (you can ignore it - we'll get tokens via OAuth)

**This activates the RuName for OAuth use.**

---

## Part 5: Test OAuth Flow from Browser

Before testing from mobile, verify OAuth works in your desktop browser.

### Step 16: Start OAuth Flow

```bash
# Get authorization URL
curl -X POST https://brayan-vizarded-undomestically.ngrok-free.dev/auth/ebay/start
```

**Copy the `authorizeUrl` from the response.**

### Step 17: Complete OAuth in Browser

1. **Paste the URL** into your browser (Chrome, Safari, etc.)
2. You'll see **eBay Sandbox Sign-In** page
3. **Log in** with your eBay sandbox test user credentials
4. **Authorize** the Scanium application
5. eBay redirects to: `https://your-ngrok-url/auth/ebay/callback?code=v2-...&state=...`
6. You should see a **beautiful success page:**

```
✅ eBay Connected!

Your eBay account has been successfully connected.
You can now return to the Scanium app.

[SANDBOX]
```

### Step 18: Verify Connection

```bash
curl https://brayan-vizarded-undomestically.ngrok-free.dev/auth/ebay/status
```

**Expected response:**

```json
{
  "connected": true,
  "environment": "sandbox",
  "scopes": "https://api.ebay.com/oauth/api_scope https://api.ebay.com/oauth/api_scope/sell.inventory https://api.ebay.com/oauth/api_scope/sell.fulfillment https://api.ebay.com/oauth/api_scope/sell.account",
  "expiresAt": "2026-12-12T16:00:00.000Z"
}
```

✅ **If `connected: true`, OAuth is working perfectly!**

### Step 19: Check Backend Logs

In your backend terminal, you should see:

```
[INFO]: OAuth flow initiated
[INFO]: eBay OAuth successful
```

### Step 20: View Tokens in Database

```bash
# Open Prisma Studio
npm run prisma:studio
```

Browser opens at `http://localhost:5555`

Navigate to:

- **User** table → should have 1 user (id: "default-user")
- **EbayConnection** table → should have 1 connection with:
    - `accessToken`
    - `refreshToken`
    - `environment`: "sandbox"
    - `scopes`
    - `expiresAt`

---

## Part 6: Mobile App Configuration (Android)

Now integrate your Android app with the local backend.

### Step 21: Add Dependencies

**In `app/build.gradle.kts`:**

```kotlin
dependencies {
    // ... existing dependencies

    // Backend integration
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.browser:browser:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}
```

**In project-level `build.gradle.kts`:**

```kotlin
plugins {
    // ... existing plugins
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.0" apply false
}
```

**In `app/build.gradle.kts`, add plugin:**

```kotlin
plugins {
    // ... existing plugins
    kotlin("plugin.serialization")
}
```

**Sync Gradle** in Android Studio.

### Step 22: Create API Client

Create `app/src/main/java/com/scanium/app/api/ScaniumApi.kt`:

```kotlin
package com.scanium.app.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Serializable
data class OAuthStartResponse(
    val authorizeUrl: String
)

@Serializable
data class ConnectionStatusResponse(
    val connected: Boolean,
    val environment: String? = null,
    val scopes: String? = null,
    val expiresAt: String? = null
)

class ScaniumApi(private val baseUrl: String) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun startOAuth(): Result<OAuthStartResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/auth/ebay/start")
                .post("".toByteArray().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body?.string() ?: throw IOException("Empty response")
                json.decodeFromString<OAuthStartResponse>(body)
            }
        }
    }

    suspend fun getConnectionStatus(): Result<ConnectionStatusResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/auth/ebay/status")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body?.string() ?: throw IOException("Empty response")
                json.decodeFromString<ConnectionStatusResponse>(body)
            }
        }
    }
}
```

### Step 23: Create Settings Screen

Create `app/src/main/java/com/scanium/app/settings/SettingsScreen.kt`:

```kotlin
package com.scanium.app.settings

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.scanium.app.api.ScaniumApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // IMPORTANT: Replace with YOUR actual ngrok URL
    val api = remember {
        ScaniumApi("https://brayan-vizarded-undomestically.ngrok-free.dev")
    }

    var isConnected by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var environment by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Check connection status on launch
    LaunchedEffect(Unit) {
        api.getConnectionStatus().onSuccess { status ->
            isConnected = status.connected
            environment = status.environment
        }
    }

    // Poll for connection status (after OAuth callback)
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000) // Poll every 2 seconds
            api.getConnectionStatus().onSuccess { status ->
                if (status.connected && !isConnected) {
                    isConnected = true
                    environment = status.environment
                    errorMessage = null
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "eBay Integration",
                style = MaterialTheme.typography.headlineSmall
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isConnected) {
                        Text(
                            text = "✅ eBay Connected",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        environment?.let {
                            Text(
                                text = "Environment: ${it.uppercase()}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Button(
                            onClick = {
                                // TODO: Implement disconnect
                                isConnected = false
                                environment = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disconnect eBay")
                        }
                    } else {
                        Text(
                            text = "Connect your eBay account to list items",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    api.startOAuth()
                                        .onSuccess { response ->
                                            openCustomTab(context, response.authorizeUrl)
                                            isLoading = false
                                        }
                                        .onFailure { error ->
                                            errorMessage = error.message ?: "Failed to start OAuth"
                                            isLoading = false
                                        }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isLoading) "Loading..." else "Connect eBay")
                        }
                    }

                    errorMessage?.let { error ->
                        Text(
                            text = "Error: $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

private fun openCustomTab(context: Context, url: String) {
    val intent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
    intent.launchUrl(context, Uri.parse(url))
}
```

**⚠️ IMPORTANT:** Update the ngrok URL in the code with YOUR actual URL!

### Step 24: Add Settings Route

In `app/src/main/java/com/scanium/app/navigation/NavGraph.kt`:

```kotlin
object Routes {
    const val CAMERA = "camera"
    const val ITEMS_LIST = "items_list"
    const val SELL_ON_EBAY = "sell_on_ebay"
    const val SETTINGS = "settings"  // Add this
}

// In NavHost, add:
composable(Routes.SETTINGS_HOME) {
    SettingsScreen(
        onNavigateBack = { navController.popBackStack() }
    )
}
```

### Step 25: Add Settings Button

In `ItemsListScreen.kt`, add settings icon:

```kotlin
import androidx.compose.material.icons.filled.Settings

TopAppBar(
    title = { Text("Detected Items ($itemCount)") },
    navigationIcon = {
        IconButton(onClick = onNavigateBack) {
            Icon(Icons.Default.ArrowBack, "Back")
        }
    },
    actions = {
        IconButton(onClick = { navController.navigate(Routes.SETTINGS_HOME) }) {
            Icon(Icons.Default.Settings, "Settings")
        }
    }
)
```

---

## Part 7: Test OAuth from Mobile Device

### Step 26: Build and Install App

```bash
cd /Users/family/dev/scanium
./gradlew installDebug
```

Or in Android Studio: **Run > Run 'app'**

### Step 27: Test OAuth Flow on Device

1. **Open Scanium app** on your Android device
2. **Navigate to Items List** (scan some items if list is empty)
3. **Tap Settings icon** (⚙️) in top-right corner
4. **Tap "Connect eBay"** button
5. **Custom Tab opens** with eBay Sandbox login
6. **Log in** with your eBay sandbox test user
7. **Authorize** Scanium application
8. **Success page appears**: "✅ eBay Connected!"
9. **Custom Tab closes automatically**
10. **Settings screen updates**: Shows "✅ eBay Connected" and "Environment: SANDBOX"

### Step 28: Verify in Backend Logs

Check your backend terminal:

```
[INFO]: OAuth flow initiated
[INFO]: Request completed (POST /auth/ebay/start)
[INFO]: eBay OAuth successful
[INFO]: Request completed (GET /auth/ebay/callback)
```

### Step 29: Check Database

```bash
npm run prisma:studio
```

Navigate to **EbayConnection** table:

- Should show 1 connection
- `environment`: "sandbox"
- `accessToken`: "v^1.1#i^1#..."
- `refreshToken`: "v^1.1#i^1#..."
- `expiresAt`: Future timestamp

---

## 🎉 Success Checklist

✅ PostgreSQL running in Docker
✅ Backend server running (`npm run dev`)
✅ ngrok tunnel active and accessible
✅ eBay RuName configured with ngrok callback URL
✅ OAuth flow works in browser
✅ Mobile app built and installed
✅ OAuth works from Android device
✅ Tokens stored in database
✅ Connection status persisted

---

## 🐛 Troubleshooting

### Port 8080 Already in Use

**Error:** `EADDRINUSE: address already in use 0.0.0.0:8080`

**Fix:**

```bash
# Find process using port 8080
lsof -i :8080

# Kill the process (replace PID)
kill <PID>

# Or kill all node processes
pkill node
```

### PostgreSQL Port Not Exposed

**Error:** `Can't reach database server at 'localhost:5432'`

**Fix:** Ensure `docker-compose.yml` has:

```yaml
postgres:
  ports:
    - '5432:5432'  # This line must be present
```

Then recreate container:

```bash
docker compose up -d postgres
```

### Database Advisory Lock Timeout

**Error:** `Timed out trying to acquire a postgres advisory lock`

**Fix:**

```bash
# Terminate stale connections
docker exec scanium-postgres psql -U scanium -d scanium -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'scanium' AND pid != pg_backend_pid();"

# Clear advisory locks
docker exec scanium-postgres psql -U scanium -d scanium -c "SELECT pg_advisory_unlock_all();"

# Retry migration
npx prisma migrate dev --name init
```

### ngrok "Endpoint is Offline" (ERR_NGROK_3200)

**Problem:** ngrok not running or not authenticated

**Fix:**

```bash
# Authenticate ngrok (first time only)
ngrok config add-authtoken YOUR_AUTHTOKEN

# Start ngrok
ngrok http 8080

# Keep terminal open
```

### Route Not Found (404)

**Error:** `Route POST:/auth/ebay/start not found`

**Problem:** Routes registered with prefix but handlers also include prefix (double prefix)

**Fix:** Routes in `src/modules/auth/ebay/routes.ts` should use:

- `/start` (not `/auth/ebay/start`)
- `/callback` (not `/auth/ebay/callback`)
- `/status` (not `/auth/ebay/status`)

Because they're registered with `prefix: '/auth/ebay'` in `app.ts`.

### Cookie Plugin Error

**Error:** `reply.setCookie is not a function`

**Fix:** In `src/app.ts`, register cookie plugin directly:

```typescript
import fastifyCookie from '@fastify/cookie';

// Register cookies
await app.register(fastifyCookie, {
  secret: config.sessionSigningSecret,
});
```

### ngrok URL Changed

**Problem:** Free ngrok URLs change on restart

**Solution:**

1. Get new ngrok URL: `ngrok http 8080`
2. Update `.env`: `PUBLIC_BASE_URL=https://new-url.ngrok-free.dev`
3. Update eBay RuName redirect URL in developer portal
4. Update mobile app `SettingsScreen.kt` with new URL
5. Restart backend: `npm run dev`
6. Rebuild mobile app: `./gradlew installDebug`

**Better solution:** Get ngrok paid plan for static domain ($8/month)

### "redirect_uri_mismatch" Error

**Problem:** eBay redirect URL doesn't match configured RuName

**Fix:**

1. Check `.env`: `PUBLIC_BASE_URL` must match ngrok URL exactly
2. Check eBay RuName: `{PUBLIC_BASE_URL}/auth/ebay/callback`
3. No trailing slashes
4. Must be HTTPS (ngrok provides this)
5. Restart backend after changing `.env`

### Mobile App Can't Connect

**Error:** Network error or timeout

**Fix:**

1. Test ngrok URL in phone's browser first: `https://your-url.ngrok-free.dev/healthz`
2. Ensure phone has internet (not just WiFi without internet)
3. Check backend logs for CORS errors
4. Verify ngrok is running: `curl https://your-url.ngrok-free.dev/healthz`
5. Check mobile app has correct URL (no typos)

### OAuth State Mismatch

**Error:** `OAuth state mismatch - possible CSRF attack`

**Causes:**

- Cookies blocked or cleared mid-flow
- Multiple OAuth attempts with stale cookies
- `SESSION_SIGNING_SECRET` changed after starting flow

**Fix:**

1. Clear browser cookies for ngrok domain
2. Start fresh OAuth flow from `/auth/ebay/start`
3. Verify `SESSION_SIGNING_SECRET` is set and hasn't changed
4. Don't change ngrok URL mid-flow

---

## 🔄 Daily Development Workflow

### Morning Startup (3 Terminals)

**Terminal 1: PostgreSQL**

```bash
cd /Users/family/dev/scanium/backend
docker compose up -d postgres
docker logs -f scanium-postgres
```

**Terminal 2: ngrok**

```bash
ngrok http 8080

# Note: Copy URL if it changed, update .env and RuName
```

**Terminal 3: Backend**

```bash
cd /Users/family/dev/scanium/backend
npm run dev
```

### Testing Changes

```bash
# Rebuild and install app
cd /Users/family/dev/scanium
./gradlew installDebug

# Test OAuth flow from device
```

### End of Day Shutdown

```bash
# Terminal 1: Stop backend (Ctrl+C)
# Terminal 2: Stop ngrok (Ctrl+C)
# Terminal 3: Stop PostgreSQL
docker compose down
```

---

## 📊 Architecture Summary

```
┌─────────────────┐
│  Android Device │
│   (Scanium App) │
└────────┬────────┘
         │ HTTPS
         ↓
┌─────────────────┐
│     ngrok       │
│  (Public URL)   │
└────────┬────────┘
         │ HTTP
         ↓
┌─────────────────┐
│  Backend Server │
│  (localhost:8080)│
└────────┬────────┘
         │ PostgreSQL
         ↓
┌─────────────────┐
│   PostgreSQL    │
│  (localhost:5432)│
└─────────────────┘
```

**OAuth Flow:**

```
1. App → POST /auth/ebay/start
2. Backend → Returns eBay authorization URL
3. App → Opens Custom Tab with URL
4. User → Logs into eBay and authorizes
5. eBay → Redirects to /auth/ebay/callback?code=...
6. Backend → Exchanges code for tokens
7. Backend → Stores tokens in PostgreSQL
8. Backend → Shows success page
9. Custom Tab → Closes
10. App → Polls /auth/ebay/status
11. Backend → Returns connected: true
12. App → Shows "eBay Connected"
```

---

## 📱 Next Steps

Once local testing is complete:

1. ✅ OAuth flow working from mobile device
2. 🔜 Deploy to Synology NAS (follow `SETUP_GUIDE.md`)
3. 🔜 Configure Cloudflare Tunnel (permanent URL, no ngrok needed)
4. 🔜 Update mobile app with production backend URL
5. 🔜 Switch to eBay production credentials
6. 🔜 Implement eBay Sell API (listing creation)
7. 🔜 Add image upload functionality
8. 🔜 Implement token refresh logic

---

## 📚 Related Documentation

- **[Backend README](README.md)** - Full development guide
- **[Setup Guide](SETUP_GUIDE.md)** - NAS deployment with Cloudflare Tunnel
- **[API Reference](API_REFERENCE.md)** - Complete endpoint documentation
- **[Colima Setup](COLIMA_SETUP.md)** - Docker Desktop alternative guide
- **[Mobile Integration](../md/backend/MOBILE_APP_INTEGRATION.md)** - Detailed Android integration

---

## ✅ You're Ready!

Follow Steps 1-29 and you'll have:

- ✅ Backend running locally
- ✅ PostgreSQL database configured
- ✅ ngrok tunnel exposing backend
- ✅ eBay OAuth working from browser
- ✅ Android app connected to backend
- ✅ Complete OAuth flow from mobile device

**All tested and working on macOS with Colima! 🚀**

---

Last updated: 2025-12-12
Tested on: macOS Sequoia 15.1, Colima 0.8.0, Node.js 20.x, PostgreSQL 16
