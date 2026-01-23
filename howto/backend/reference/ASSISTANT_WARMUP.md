# Assistant Warmup Endpoint

The warmup endpoint allows the mobile app to pre-verify that the assistant is configured and
reachable before initiating a chat session.

## Endpoint

```
POST /v1/assist/warmup
```

## Request

Headers:

- `X-API-Key`: Required. Valid assistant API key.

Body: Empty (no payload required)

## Response

### Success (200)

```json
{
  "status": "ok",
  "provider": "claude",
  "model": "claude-sonnet-4-20250514",
  "ts": "2026-01-04T10:00:00.000Z",
  "correlationId": "abc123-..."
}
```

### Unauthorized (401)

```json
{
  "status": "error",
  "reason": "UNAUTHORIZED",
  "message": "Missing or invalid API key",
  "ts": "...",
  "correlationId": "..."
}
```

### Provider Not Configured (503)

```json
{
  "status": "error",
  "reason": "PROVIDER_NOT_CONFIGURED",
  "message": "Assistant provider is not configured",
  "ts": "...",
  "correlationId": "..."
}
```

### Provider Unavailable (503)

```json
{
  "status": "error",
  "reason": "PROVIDER_UNAVAILABLE",
  "message": "Assistant provider unavailable",
  "ts": "...",
  "correlationId": "..."
}
```

## Verification Commands

### Local Development

```bash
# Test against local dev server
curl -X POST http://localhost:8080/v1/assist/warmup \
  -H "X-API-Key: YOUR_API_KEY"
```

### Production (via Cloudflare Tunnel)

```bash
# Test against production
curl -X POST https://scanium.gtemp1.com/v1/assist/warmup \
  -H "X-API-Key: YOUR_PRODUCTION_API_KEY"
```

### Expected Output

On success, you should see:

```json
{"status":"ok","provider":"claude","model":"...","ts":"...","correlationId":"..."}
```

If you see `404 "Route not found"`, the container is running an old image.
See [REDEPLOY.md](./REDEPLOY.md) for instructions.

## Troubleshooting

### 404 Route not found

**Cause**: The Docker container is running an old image that doesn't have the warmup route.

**Fix**: Rebuild and restart the container:

```bash
cd /volume1/docker/scanium/backend
docker compose down api
docker compose build --no-cache api
docker compose up -d api
```

### 401 Unauthorized

**Cause**: Missing or invalid API key.

**Fix**: Ensure you're passing the correct API key in the `X-API-Key` header.

### 503 Provider Not Configured

**Cause**: The assistant provider environment variables are not set.

**Fix**: Check `.env` for:

- `ASSISTANT_PROVIDER=claude`
- `CLAUDE_API_KEY=...`

### Connection Refused

**Cause**: Container is not running or port is not exposed.

**Fix**: Check container status:

```bash
docker ps | grep scanium-api
docker logs scanium-api --tail 50
```

## Integration Tests

Run the E2E tests to verify route registration:

```bash
cd backend
npm test -- --run src/modules/assistant/routes.e2e.test.ts
```

This includes tests that specifically verify:

- Warmup endpoint returns 200 (not 404)
- Warmup endpoint is registered under `/v1` prefix
- Authentication is enforced
