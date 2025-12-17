***REMOVED*** Mobile App Backend Integration Guide

This guide explains how to integrate the Scanium Android app with the backend API for eBay OAuth and future listing features.

***REMOVED******REMOVED*** 📡 Backend Endpoints

Base URL: `https://api.yourdomain.com` (replace with your actual Cloudflare Tunnel URL)

***REMOVED******REMOVED******REMOVED*** Health & Status

- `GET /healthz` - Basic health check
- `GET /readyz` - Database connectivity check

***REMOVED******REMOVED******REMOVED*** eBay OAuth

- `POST /auth/ebay/start` - Initiate OAuth flow
- `GET /auth/ebay/callback` - OAuth callback (handled by browser)
- `GET /auth/ebay/status` - Check connection status

***REMOVED******REMOVED*** 🔐 eBay OAuth Flow (Android)

***REMOVED******REMOVED******REMOVED*** Step 1: Add Network Permission

In `app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />

<application
    android:usesCleartextTraffic="false">
    <!-- Your existing config -->
</application>
```

***REMOVED******REMOVED******REMOVED*** Step 2: Create API Client

Create `app/src/main/java/com/scanium/app/api/ScaniumApi.kt`:

```kotlin
package com.scanium.app.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
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

    suspend fun startOAuth(): Result<OAuthStartResponse> = runCatching {
        val request = Request.Builder()
            .url("$baseUrl/auth/ebay/start")
            .post("".toByteArray().toRequestBody())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected response: ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Empty response")
            json.decodeFromString<OAuthStartResponse>(body)
        }
    }

    suspend fun getConnectionStatus(): Result<ConnectionStatusResponse> = runCatching {
        val request = Request.Builder()
            .url("$baseUrl/auth/ebay/status")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected response: ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Empty response")
            json.decodeFromString<ConnectionStatusResponse>(body)
        }
    }
}
```

***REMOVED******REMOVED******REMOVED*** Step 3: Add Dependency

In `app/build.gradle.kts`:

```kotlin
dependencies {
    // Add OkHttp for HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Add Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}
```

In `build.gradle.kts` (project level):

```kotlin
plugins {
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.0"
}
```

***REMOVED******REMOVED******REMOVED*** Step 4: Create OAuth UI Screen

Create `app/src/main/java/com/scanium/app/settings/EbayOAuthScreen.kt`:

```kotlin
package com.scanium.app.settings

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.scanium.app.api.ScaniumApi
import kotlinx.coroutines.launch

@Composable
fun EbayOAuthScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { ScaniumApi("https://api.yourdomain.com") }

    var isConnected by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var environment by remember { mutableStateOf<String?>(null) }

    // Check connection status on launch
    LaunchedEffect(Unit) {
        scope.launch {
            api.getConnectionStatus().onSuccess { status ->
                isConnected = status.connected
                environment = status.environment
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isConnected) {
            Text("✅ eBay Connected", style = MaterialTheme.typography.headlineSmall)
            environment?.let {
                Text("Environment: ${it.uppercase()}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        // TODO: Call disconnect endpoint (not yet implemented)
                    }
                }
            ) {
                Text("Disconnect")
            }
        } else {
            Text("Connect your eBay account", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        api.startOAuth().onSuccess { response ->
                            openCustomTab(context, response.authorizeUrl)
                            isLoading = false
                        }.onFailure {
                            isLoading = false
                            // Handle error
                        }
                    }
                },
                enabled = !isLoading
            ) {
                Text(if (isLoading) "Loading..." else "Connect eBay")
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

***REMOVED******REMOVED******REMOVED*** Step 5: Add Custom Tabs Dependency

In `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("androidx.browser:browser:1.7.0")
}
```

***REMOVED******REMOVED******REMOVED*** Step 6: Poll for Connection After OAuth

After the Custom Tab closes, poll the status endpoint:

```kotlin
// In EbayOAuthScreen.kt, add this effect
LaunchedEffect(Unit) {
    // Poll every 2 seconds to check if OAuth completed
    while (true) {
        delay(2000)
        api.getConnectionStatus().onSuccess { status ->
            if (status.connected && !isConnected) {
                isConnected = true
                environment = status.environment
                // Show success message
            }
        }
    }
}
```

***REMOVED******REMOVED*** 🧪 Testing OAuth Flow

***REMOVED******REMOVED******REMOVED*** Local Testing (Development)

1. Start backend locally:
   ```bash
   cd backend
   npm run dev
   ```

2. Use ngrok to expose localhost:
   ```bash
   ngrok http 8080
   ```

3. Update `.env`:
   ```bash
   PUBLIC_BASE_URL=https://your-ngrok-url.ngrok.io
   ```

4. Update Android app API base URL to ngrok URL

5. Test OAuth flow from app

***REMOVED******REMOVED******REMOVED*** Production Testing

1. Deploy backend to NAS with Cloudflare Tunnel

2. Update Android app API base URL to production URL:
   ```kotlin
   val api = ScaniumApi("https://api.yourdomain.com")
   ```

3. Test OAuth flow from app

***REMOVED******REMOVED*** 🔒 Security Considerations

***REMOVED******REMOVED******REMOVED*** 1. Certificate Pinning (Optional but Recommended)

Add OkHttp certificate pinning:

```kotlin
val certificatePinner = CertificatePinner.Builder()
    .add("api.yourdomain.com", "sha256/YOUR_CERTIFICATE_HASH")
    .build()

val client = OkHttpClient.Builder()
    .certificatePinner(certificatePinner)
    .build()
```

***REMOVED******REMOVED******REMOVED*** 2. Store API Base URL in BuildConfig

In `app/build.gradle.kts`:

```kotlin
android {
    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://api.yourdomain.com\"")
        }
    }
}
```

Then use:

```kotlin
val api = ScaniumApi(BuildConfig.API_BASE_URL)
```

***REMOVED******REMOVED******REMOVED*** 3. Network Security Config

Create `app/src/main/res/xml/network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.yourdomain.com</domain>
    </domain-config>

    <!-- Allow localhost for debugging -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">localhost</domain>
        <domain includeSubdomains="true">10.0.2.2</domain>
    </domain-config>
</network-security-config>
```

Reference in `AndroidManifest.xml`:

```xml
<application
    android:networkSecurityConfig="@xml/network_security_config">
```

***REMOVED******REMOVED*** 📱 Future: Listing Creation

Once backend listing endpoints are implemented, follow this pattern:

```kotlin
@Serializable
data class CreateListingRequest(
    val title: String,
    val description: String,
    val price: Double,
    val condition: String,
    val imageUrls: List<String>
)

@Serializable
data class CreateListingResponse(
    val listingId: String,
    val externalUrl: String
)

suspend fun ScaniumApi.createListing(
    request: CreateListingRequest
): Result<CreateListingResponse> = runCatching {
    // Implementation similar to startOAuth
}
```

***REMOVED******REMOVED*** 🐛 Troubleshooting

***REMOVED******REMOVED******REMOVED*** "Unable to resolve host" error

- Check that backend is running and accessible
- Verify `API_BASE_URL` is correct
- Check network permissions in manifest
- Test URL in browser first

***REMOVED******REMOVED******REMOVED*** OAuth callback doesn't work

- Verify `PUBLIC_BASE_URL` matches actual backend URL
- Check eBay app RuName configuration matches callback URL
- View backend logs: `docker logs scanium-api`

***REMOVED******REMOVED******REMOVED*** Custom Tab doesn't open

- Ensure Chrome is installed on device/emulator
- Check `androidx.browser` dependency is added
- Handle case where no browser is available

***REMOVED******REMOVED*** 📚 Additional Resources

- [Backend API Documentation](../backend/README.md)
- [eBay OAuth Module](../backend/src/modules/auth/ebay/README.md)
- [Android Custom Tabs Guide](https://developer.chrome.com/docs/android/custom-tabs/)
- [OkHttp Documentation](https://square.github.io/okhttp/)
