> Archived on 2025-12-20: backend notes kept for reference; see docs/ARCHITECTURE.md for current
> state.

***REMOVED*** Scanium Backend API Reference

Quick reference for all available endpoints.

**Base URL:** `https://api.yourdomain.com`

---

***REMOVED******REMOVED*** 🏥 Health & Status

***REMOVED******REMOVED******REMOVED*** GET /healthz

Liveness probe - returns 200 if process is running.

**Response 200:**

```json
{
  "status": "ok",
  "timestamp": "2024-12-12T10:30:00.000Z"
}
```

---

***REMOVED******REMOVED******REMOVED*** GET /readyz

Readiness probe - returns 200 only if database is reachable.

**Response 200 (Ready):**

```json
{
  "status": "ok",
  "database": "connected",
  "timestamp": "2024-12-12T10:30:00.000Z"
}
```

**Response 503 (Not Ready):**

```json
{
  "status": "error",
  "message": "Database not reachable",
  "timestamp": "2024-12-12T10:30:00.000Z"
}
```

---

***REMOVED******REMOVED******REMOVED*** GET /

API information and endpoint listing.

**Response 200:**

```json
{
  "name": "Scanium Backend API",
  "version": "1.0.0",
  "environment": "sandbox",
  "endpoints": {
    "health": "/healthz",
    "readiness": "/readyz",
    "ebayAuth": {
      "start": "POST /auth/ebay/start",
      "callback": "GET /auth/ebay/callback",
      "status": "GET /auth/ebay/status"
    }
  }
}
```

---

***REMOVED******REMOVED*** 🔐 eBay OAuth

***REMOVED******REMOVED******REMOVED*** POST /auth/ebay/start

Initiates eBay OAuth flow. Returns authorization URL for user to visit.

**Request:**

```http
POST /auth/ebay/start HTTP/1.1
Host: api.yourdomain.com
Content-Type: application/json
```

**Response 200:**

```json
{
  "authorizeUrl": "https://auth.sandbox.ebay.com/oauth2/authorize?client_id=...&state=..."
}
```

**Usage:**

1. Call this endpoint
2. Open `authorizeUrl` in browser/Custom Tab
3. User authorizes on eBay
4. User is redirected to callback (handled by backend)

**Sets Cookies:**

- `oauth_state` (signed, HttpOnly)
- `oauth_nonce` (signed, HttpOnly)

---

***REMOVED******REMOVED******REMOVED*** GET /auth/ebay/callback

OAuth callback endpoint - receives authorization code from eBay.

**⚠️ Not called directly by app** - eBay redirects here after authorization.

**Request (from eBay):**

```http
GET /auth/ebay/callback?code=v2-abc123&state=xyz789 HTTP/1.1
Host: api.yourdomain.com
Cookie: oauth_state=...; oauth_nonce=...
```

**Response 200 (HTML):**

User-friendly success page:

```html
<!DOCTYPE html>
<html>
<body>
  <h1>✅ eBay Connected!</h1>
  <p>You can now return to the Scanium app.</p>
</body>
</html>
```

**Response 200 (JSON - if Accept: application/json):**

```json
{
  "success": true,
  "message": "eBay account connected successfully",
  "environment": "sandbox"
}
```

**Response 400 (Validation Error):**

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Missing code or state parameter"
  }
}
```

**Response 400 (State Mismatch):**

```json
{
  "error": {
    "code": "OAUTH_STATE_MISMATCH",
    "message": "OAuth state mismatch - possible CSRF attack"
  }
}
```

**Response 502 (Token Exchange Failed):**

```json
{
  "error": {
    "code": "OAUTH_TOKEN_EXCHANGE_FAILED",
    "message": "Failed to exchange authorization code for tokens",
    "details": {
      "status": 400,
      "body": "..."
    }
  }
}
```

**Behavior:**

1. Validates state parameter against signed cookie
2. Exchanges authorization code for access/refresh tokens
3. Stores tokens in database
4. Clears OAuth cookies
5. Returns success page

---

***REMOVED******REMOVED******REMOVED*** GET /auth/ebay/status

Returns current eBay connection status.

**Request:**

```http
GET /auth/ebay/status HTTP/1.1
Host: api.yourdomain.com
```

**Response 200 (Connected):**

```json
{
  "connected": true,
  "environment": "sandbox",
  "scopes": "https://api.ebay.com/oauth/api_scope https://api.ebay.com/oauth/api_scope/sell.inventory",
  "expiresAt": "2024-12-31T23:59:59.000Z"
}
```

**Response 200 (Not Connected):**

```json
{
  "connected": false,
  "environment": null,
  "scopes": null,
  "expiresAt": null
}
```

**Usage:**

- Call before showing "Connect eBay" button to check if already connected
- Poll after OAuth callback to confirm successful authorization

---

***REMOVED******REMOVED*** 🛒 eBay Listings (Future)

Placeholder endpoints - not yet implemented.

***REMOVED******REMOVED******REMOVED*** POST /listings

Create a new eBay listing.

**Request:**

```json
{
  "title": "Vintage Camera",
  "description": "...",
  "price": 150.00,
  "condition": "USED_EXCELLENT",
  "images": ["https://..."]
}
```

**Response 201:**

```json
{
  "listingId": "uuid",
  "externalId": "123456789",
  "externalUrl": "https://www.ebay.com/itm/123456789",
  "status": "active"
}
```

---

***REMOVED******REMOVED*** 📷 Media Upload (Future)

Placeholder endpoints - not yet implemented.

***REMOVED******REMOVED******REMOVED*** POST /media/upload

Upload image for listing.

**Request (multipart/form-data):**

```
POST /media/upload
Content-Type: multipart/form-data

file: <binary image data>
```

**Response 200:**

```json
{
  "url": "https://...",
  "thumbnailUrl": "https://...",
  "width": 1600,
  "height": 1200,
  "size": 245678
}
```

---

***REMOVED******REMOVED*** 🚨 Error Responses

All errors follow this format:

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable error message",
    "details": {
      // Optional additional context
    }
  }
}
```

***REMOVED******REMOVED******REMOVED*** Common Error Codes

| Code                          | HTTP Status | Description                           |
|-------------------------------|-------------|---------------------------------------|
| `INTERNAL_ERROR`              | 500         | Unexpected server error               |
| `VALIDATION_ERROR`            | 400         | Request validation failed             |
| `NOT_FOUND`                   | 404         | Resource not found                    |
| `UNAUTHORIZED`                | 401         | Authentication required               |
| `OAUTH_STATE_MISMATCH`        | 400         | Invalid OAuth state (CSRF protection) |
| `OAUTH_TOKEN_EXCHANGE_FAILED` | 502         | eBay token exchange failed            |
| `DATABASE_ERROR`              | 500         | Database operation failed             |
| `EBAY_API_ERROR`              | 502         | eBay API returned error               |

---

***REMOVED******REMOVED*** 🔒 Security Headers

All responses include:

```
Access-Control-Allow-Origin: scanium:// (or configured origins)
Access-Control-Allow-Credentials: true
Set-Cookie: ...; HttpOnly; Secure; SameSite=Lax
```

---

***REMOVED******REMOVED*** 📝 Request Examples (curl)

***REMOVED******REMOVED******REMOVED*** Health Check

```bash
curl https://api.yourdomain.com/healthz
```

***REMOVED******REMOVED******REMOVED*** Start OAuth

```bash
curl -X POST https://api.yourdomain.com/auth/ebay/start
```

***REMOVED******REMOVED******REMOVED*** Check Connection Status

```bash
curl https://api.yourdomain.com/auth/ebay/status
```

***REMOVED******REMOVED******REMOVED*** Test with Accept: application/json

```bash
curl -H "Accept: application/json" \
     'https://api.yourdomain.com/auth/ebay/callback?code=test&state=test'
```

---

***REMOVED******REMOVED*** 📱 Mobile App Integration

***REMOVED******REMOVED******REMOVED*** Kotlin Example

```kotlin
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class OAuthStartResponse(val authorizeUrl: String)

@Serializable
data class ConnectionStatus(
    val connected: Boolean,
    val environment: String? = null
)

class ScaniumApi(private val baseUrl: String) {
    private val client = OkHttpClient()

    suspend fun startOAuth(): OAuthStartResponse {
        val request = Request.Builder()
            .url("$baseUrl/auth/ebay/start")
            .post("".toByteArray().toRequestBody())
            .build()

        client.newCall(request).execute().use { response ->
            return Json.decodeFromString(response.body!!.string())
        }
    }

    suspend fun getConnectionStatus(): ConnectionStatus {
        val request = Request.Builder()
            .url("$baseUrl/auth/ebay/status")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            return Json.decodeFromString(response.body!!.string())
        }
    }
}

// Usage
val api = ScaniumApi("https://api.yourdomain.com")
val oauth = api.startOAuth()
openBrowser(oauth.authorizeUrl)

// After browser returns
delay(2000)
val status = api.getConnectionStatus()
if (status.connected) {
    println("✅ eBay connected!")
}
```

---

***REMOVED******REMOVED*** 🔄 Rate Limiting (Future)

Rate limiting not yet implemented. Future plans:

- 100 requests/minute per IP
- 1000 requests/hour per user
- Burst allowance: 20 requests

Headers will include:

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1702345678
```

---

***REMOVED******REMOVED*** 📊 Monitoring

***REMOVED******REMOVED******REMOVED*** Metrics (Future)

Planned Prometheus metrics:

- `http_requests_total`
- `http_request_duration_seconds`
- `http_requests_in_flight`
- `db_connections_active`
- `oauth_flows_started_total`
- `oauth_flows_completed_total`
- `oauth_flows_failed_total`

---

***REMOVED******REMOVED*** 🧪 Testing

***REMOVED******REMOVED******REMOVED*** Local Testing

```bash
***REMOVED*** Start local server
cd backend
npm run dev

***REMOVED*** Test endpoints
curl http://localhost:8080/healthz
curl -X POST http://localhost:8080/auth/ebay/start
```

***REMOVED******REMOVED******REMOVED*** Production Testing

```bash
***REMOVED*** Use actual Cloudflare Tunnel URL
curl https://api.yourdomain.com/healthz
curl https://api.yourdomain.com/auth/ebay/status
```

---

***REMOVED******REMOVED*** 📚 Related Documentation

- [Setup Guide](SETUP_GUIDE.md) - Deployment instructions
- [Backend README](README.md) - Development guide
- [Mobile Integration](../md/backend/MOBILE_APP_INTEGRATION.md) - Android implementation
- [eBay OAuth Module](src/modules/auth/ebay/README.md) - Detailed OAuth docs

---

**Last Updated:** 2024-12-12
**API Version:** 1.0.0
**Environment:** Sandbox/Production
