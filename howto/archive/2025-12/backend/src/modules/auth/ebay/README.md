> Archived on 2025-12-20: backend notes kept for reference; see docs/ARCHITECTURE.md for current
> state.

# eBay OAuth Module

Server-side eBay OAuth 2.0 implementation for Scanium backend.

## Endpoints

### POST /auth/ebay/start

Initiates the eBay OAuth flow.

**Request:**

```http
POST /auth/ebay/start
Content-Type: application/json
```

**Response:**

```json
{
  "authorizeUrl": "https://auth.sandbox.ebay.com/oauth2/authorize?client_id=...&state=..."
}
```

**Behavior:**

- Generates cryptographically secure state and nonce
- Stores them in signed HTTP-only cookies
- Returns eBay authorization URL
- Mobile app should open this URL in a browser/custom tab

---

### GET /auth/ebay/callback

OAuth callback endpoint - receives authorization code from eBay.

**Request:**

```http
GET /auth/ebay/callback?code=AUTHORIZATION_CODE&state=STATE_VALUE
```

**Query Parameters:**

- `code` (required): Authorization code from eBay
- `state` (required): State parameter for CSRF protection

**Response (HTML):**
Returns a user-friendly HTML page confirming successful connection.

**Response (JSON):**
If `Accept: application/json` header is present:

```json
{
  "success": true,
  "message": "eBay account connected successfully",
  "environment": "sandbox"
}
```

**Behavior:**

1. Validates state parameter against signed cookie
2. Exchanges authorization code for access/refresh tokens
3. Stores tokens in database linked to user
4. Clears OAuth cookies
5. Returns success page

**Errors:**

- 400: Missing code or state
- 400: State mismatch (CSRF protection)
- 502: Token exchange failed

---

### GET /auth/ebay/status

Returns current eBay connection status.

**Request:**

```http
GET /auth/ebay/status
```

**Response (Connected):**

```json
{
  "connected": true,
  "environment": "sandbox",
  "scopes": "https://api.ebay.com/oauth/api_scope https://api.ebay.com/oauth/api_scope/sell.inventory",
  "expiresAt": "2024-12-31T23:59:59.000Z"
}
```

**Response (Not Connected):**

```json
{
  "connected": false,
  "environment": null,
  "scopes": null,
  "expiresAt": null
}
```

**Behavior:**

- Checks database for existing eBay connection
- Returns connection metadata if exists
- Mobile app polls this after OAuth callback to confirm connection

---

## Security Features

1. **CSRF Protection**: State parameter validated against signed cookie
2. **Secure Cookies**: HTTP-only, signed, SameSite=Lax
3. **Token Storage**: Access and refresh tokens stored in database
4. **No Token Logging**: Tokens are never logged
5. **Environment Isolation**: Sandbox and production tokens stored separately

---

## Mobile App Integration Flow

```
┌─────────────┐                  ┌─────────────┐                  ┌─────────────┐
│ Scanium App │                  │   Backend   │                  │    eBay     │
└──────┬──────┘                  └──────┬──────┘                  └──────┬──────┘
       │                                │                                │
       │  POST /auth/ebay/start         │                                │
       │───────────────────────────────>│                                │
       │                                │                                │
       │  { authorizeUrl }              │                                │
       │<───────────────────────────────│                                │
       │                                │                                │
       │  Open Custom Tab               │                                │
       │────────────────────────────────────────────────────────────────>│
       │                                │                                │
       │                                │  User logs in & authorizes     │
       │                                │<───────────────────────────────│
       │                                │                                │
       │                                │  GET /callback?code=...&state=...
       │                                │<───────────────────────────────│
       │                                │                                │
       │                                │  Exchange code for tokens      │
       │                                │───────────────────────────────>│
       │                                │                                │
       │                                │  { access_token, refresh_token }
       │                                │<───────────────────────────────│
       │                                │                                │
       │                                │  Store tokens in DB            │
       │                                │────┐                           │
       │                                │    │                           │
       │                                │<───┘                           │
       │                                │                                │
       │  Custom Tab closes             │  Return success HTML           │
       │<────────────────────────────────────────────────────────────────│
       │                                │                                │
       │  Poll GET /auth/ebay/status    │                                │
       │───────────────────────────────>│                                │
       │                                │                                │
       │  { connected: true, ... }      │                                │
       │<───────────────────────────────│                                │
       │                                │                                │
```

---

## Environment Variables

See `backend/.env.example` for required configuration:

- `EBAY_ENV`: "sandbox" or "production"
- `EBAY_CLIENT_ID`: eBay application client ID
- `EBAY_CLIENT_SECRET`: eBay application client secret
- `EBAY_TOKEN_ENCRYPTION_KEY`: 32+ character secret used to encrypt stored OAuth tokens
- `EBAY_REDIRECT_PATH`: OAuth callback path (default: /auth/ebay/callback)
- `EBAY_SCOPES`: Space-delimited API scopes
- `PUBLIC_BASE_URL`: Public URL of backend (used to build redirect URI)
- `SESSION_SIGNING_SECRET`: Secret for signing cookies (min 32 chars)

---

## Future Enhancements

- [ ] Token refresh logic (automatic renewal before expiry)
- [ ] Multi-user support (replace default user with real authentication)
- [x] Token encryption at rest (AES-256-GCM via application key)
- [ ] Revoke token endpoint
- [ ] Webhook for token revocation events
