***REMOVED*** NAS Compose Quickstart

Use this directory for fast local smoke tests of the NAS deployment files.

***REMOVED******REMOVED*** 1. Configure environment files

1. Edit `deploy/nas/compose/.env` – Docker Compose reads this automatically to set `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_DB`.
2. Copy `deploy/nas/env/backend.env.example` → `deploy/nas/env/backend.env` and fill in:
   - `SCANIUM_API_KEYS` – comma-separated keys for `X-API-Key`.
   - `ALLOW_INSECURE_HTTP=true` if you want to exercise HTTP on `localhost` or `192.168.x.x`.
   - `PUBLIC_BASE_URL` – the Cloudflare hostname (e.g., `https://scanium.gtemp1.com`).

***REMOVED******REMOVED*** 2. Launch the stack

```bash
cd deploy/nas/compose
docker compose -f docker-compose.nas.backend.yml \
  --env-file ../env/backend.env up
```

The backend container defaults to `NODE_ENV=production`, so HTTPS is enforced unless `ALLOW_INSECURE_HTTP=true`.

***REMOVED******REMOVED*** 3. Verify with curl

***REMOVED******REMOVED******REMOVED*** Local/LAN testing (requires `ALLOW_INSECURE_HTTP=true`)

```bash
curl -X POST http://localhost:8080/v1/assist/chat \
  -H "x-api-key: $SCANIUM_API_KEY" \
  -H "content-type: application/json" \
  --data '{"items":[],"message":"ping"}'
```

If you forget to set the flag, the backend responds with `403` / `HTTPS_REQUIRED`.

***REMOVED******REMOVED******REMOVED*** Public Cloudflare tunnel

```bash
curl -X POST https://scanium.gtemp1.com/v1/assist/chat \
  -H "x-api-key: $SCANIUM_API_KEY" \
  -H "content-type: application/json" \
  --data '{"items":[],"message":"ping"}'
```

Cloudflare automatically adds `X-Forwarded-Proto: https`, so the backend accepts the call even though the tunnel terminates TLS.
