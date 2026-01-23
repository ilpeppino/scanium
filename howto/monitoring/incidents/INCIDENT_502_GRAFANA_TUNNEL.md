# Incident Report: Grafana 502 Bad Gateway via Cloudflare Tunnel

**Date**: 2026-01-09
**Status**: Resolved
**Duration**: ~2 hours
**Severity**: P2 (High) - Monitoring dashboard inaccessible

## Summary

Grafana monitoring dashboard at https://grafana.gtemp1.com returned Cloudflare 502 Bad Gateway
errors. Cloudflare indicated the host was unreachable, preventing access to production monitoring.

## Root Cause

**Network isolation between Docker containers**

The `cloudflared` tunnel container was not connected to the `scanium-observability` Docker network
where Grafana resides. This prevented cloudflared from resolving and reaching the
`scanium-grafana:3000` service endpoint configured in the tunnel ingress rules.

### Network Configuration Analysis

- **Grafana**: Connected to `scanium-observability` network only
- **Backend**: Connected to both `backend_scanium-network` AND `scanium-observability`
- **Cloudflared**: Connected to `backend_scanium-network`, `compose_scanium_net`, `scanium_net` -
  but NOT `scanium-observability`

This explains why:

- scanium.gtemp1.com worked (cloudflared could reach backend via shared `backend_scanium-network`)
- grafana.gtemp1.com failed (cloudflared could not reach grafana - no shared network)

## Timeline

- **18:00** - Issue detected: grafana.gtemp1.com returning 502
- **18:05** - PHASE 0: Verified Grafana healthy locally (HTTP 200 on 127.0.0.1:3000)
- **18:07** - PHASE 1: Confirmed tunnel config correct (grafana.gtemp1.com → scanium-grafana:3000)
- **18:10** - PHASE 2: Identified network isolation via `docker inspect`
- **18:12** - PHASE 3: Root cause confirmed - cloudflared not on scanium-observability network
- **18:13** - PHASE 4: Added network to docker-compose.yml and connected container
- **18:14** - PHASE 5: Verified fix successful (HTTP 200, no more 502)
- **18:15** - PHASE 6: Documented and committed fix

## Fix Applied

Added `scanium-observability` network to cloudflared container configuration:

```yaml
# deploy/nas/cloudflared/docker-compose.yml
services:
  cloudflared:
    networks:
      - backend_scanium-network
      - compose_scanium_net
      - scanium_net
      - scanium-observability  # Added

networks:
  scanium-observability:
    external: true  # Added
```

Applied to running container:

```bash
docker network connect scanium-observability scanium-cloudflared
```

## Verification

### Local verification (from NAS)

```bash
$ curl -sS -I http://127.0.0.1:3000
HTTP/1.1 200 OK
```

### External verification

```bash
$ curl -sS -I https://grafana.gtemp1.com
HTTP/2 200
date: Fri, 09 Jan 2026 18:14:58 GMT
content-type: text/html; charset=UTF-8
```

### Cloudflared logs

```
2026-01-09T18:14:57Z DBG GET https://grafana.gtemp1.com/...
  ingressRule=2
  originService=http://scanium-grafana:3000
  path=/...
```

### Backend regression check

```bash
$ curl -sS -I https://scanium.gtemp1.com/health
HTTP/2 403  # Expected (WARP-protected), not 502
```

## Prevention

1. Document all required Docker networks for each service in README
2. Add docker-compose healthcheck that validates cross-service connectivity
3. Consider using docker-compose depends_on with conditions to enforce network topology
4. Add monitoring alert for 502 errors from Cloudflare Tunnel

## Related Changes

- Commit: fix(tunnel): restore grafana.gtemp1.com origin connectivity
- Files modified:
    - `deploy/nas/cloudflared/docker-compose.yml`
    - `monitoring/INCIDENT_502_GRAFANA_TUNNEL.md` (this file)

## Lessons Learned

- Docker service name resolution only works within shared networks
- Cloudflare 502 with "Host Error" strongly indicates origin unreachability
- Always verify network topology when debugging container connectivity issues
- The backend worked because it was on multiple networks; this masked the cloudflared network
  isolation issue
