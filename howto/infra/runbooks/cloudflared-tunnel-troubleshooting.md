***REMOVED*** Cloudflare Tunnel Troubleshooting

***REMOVED******REMOVED*** 502 Error Fix (2026-01-11)

***REMOVED******REMOVED******REMOVED*** Problem

Cloudflare tunnel was returning 502 errors when trying to access the backend
through https://scanium.gtemp1.com

***REMOVED******REMOVED******REMOVED*** Root Cause

The `scanium-cloudflared` container was not connected to the `scanium_net` Docker network where the
`scanium-backend` container resides. This caused DNS resolution failures:

```
dial tcp: lookup scanium-backend on 127.0.0.11:53: no such host
```

***REMOVED******REMOVED******REMOVED*** Solution

Connected the cloudflared container to the scanium_net network:

```bash
docker network connect scanium_net scanium-cloudflared
```

***REMOVED******REMOVED******REMOVED*** Verification

- Health endpoint: https://scanium.gtemp1.com/health returns 200 OK
- Pricing API works through tunnel with real web search results
- No more "no such host" errors in cloudflared logs

***REMOVED******REMOVED******REMOVED*** Prevention

The docker-compose.yml already includes scanium_net in the networks list. If redeploying:

```bash
cd /volume1/docker/scanium/repo/deploy/nas/cloudflared
docker-compose down
docker-compose up -d
```

This will ensure the container starts with all required networks.

***REMOVED******REMOVED*** Diagnostics

***REMOVED******REMOVED******REMOVED*** Check if cloudflared can reach backend

```bash
***REMOVED*** Check cloudflared networks
docker inspect scanium-cloudflared --format '{{json .NetworkSettings.Networks}}' | python3 -m json.tool

***REMOVED*** Check backend networks
docker inspect scanium-backend --format '{{json .NetworkSettings.Networks}}' | python3 -m json.tool

***REMOVED*** Both should be on scanium_net
```

***REMOVED******REMOVED******REMOVED*** Check cloudflared logs

```bash
docker logs scanium-cloudflared --tail 50 | grep -E 'ERR|no such host'
```

***REMOVED******REMOVED******REMOVED*** Test tunnel connectivity

```bash
***REMOVED*** Test health endpoint
curl https://scanium.gtemp1.com/health

***REMOVED*** Test API endpoint
curl -X POST https://scanium.gtemp1.com/v1/assist/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: YOUR_KEY" \
  -d '{"items":[{"itemId":"test","title":"Test"}],"message":"test"}'
```
