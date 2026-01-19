***REMOVED*** Cloudflare Tunnel Setup for Scanium Services

**Tunnel ID**: `REDACTED_CLOUDFLARE_TUNNEL_ID`

***REMOVED******REMOVED*** Configured Hostnames

| Hostname             | Service     | Container         | Port |
|----------------------|-------------|-------------------|------|
| `scanium.gtemp1.com` | Backend API | `scanium-backend` | 8080 |
| `grafana.gtemp1.com` | Grafana     | `scanium-grafana` | 3000 |
| `otlp.gtemp1.com`    | Alloy OTLP  | `scanium-alloy`   | 4318 |

---

***REMOVED******REMOVED*** Step 1: Add Grafana Hostname to Cloudflare Tunnel

1. **Go to Cloudflare Zero Trust Dashboard**:
    - Login to https://one.dash.cloudflare.com/
    - Navigate to **Networks** → **Tunnels**
    - Find tunnel ID: `REDACTED_CLOUDFLARE_TUNNEL_ID`
    - Click **Configure**

2. **Add Public Hostname**:
    - Go to **Public Hostnames** tab
    - Click **Add a public hostname**

3. **Configure the hostname**:
   ```
   Subdomain: grafana
   Domain: gtemp1.com
   Path: (leave empty)
   ```

4. **Configure the service**:
   ```
   Type: HTTP
   URL: scanium-grafana:3000
   ```

5. **Additional settings** (optional but recommended):
    - **TLS** → Verify TLS: OFF (Grafana uses HTTP internally)
    - **HTTP Host Header**: (leave empty)
    - **HTTP Settings** → Disable Chunked Encoding: OFF

6. **Save the hostname**

---

***REMOVED******REMOVED*** Step 2: Verify DNS

The DNS record should be automatically created by Cloudflare Tunnel.

Verify at: https://dash.cloudflare.com/

Look for:

```
Type: CNAME
Name: grafana
Target: <tunnel-id>.cfargotunnel.com
```

---

***REMOVED******REMOVED*** Step 3: Test Access

After saving the configuration (it takes ~30 seconds to propagate):

1. **Test from mobile browser**:
   ```
   https://grafana.gtemp1.com
   ```

2. **Test from command line**:
   ```bash
   curl -I https://grafana.gtemp1.com/api/health
   ```

   Expected response:
   ```
   HTTP/2 200
   content-type: application/json; charset=utf-8
   ```

3. **Full health check**:
   ```bash
   curl -s https://grafana.gtemp1.com/api/health | jq .
   ```

   Expected:
   ```json
   {
     "commit": "00a22ff8b28550d593ec369ba3da1b25780f0a4a",
     "database": "ok",
     "version": "10.3.1"
   }
   ```

---

***REMOVED******REMOVED*** OTLP Tunnel Setup (Mobile Telemetry)

The OTLP tunnel enables mobile apps to send telemetry data to the Grafana Alloy receiver over HTTPS.

***REMOVED******REMOVED******REMOVED*** Step 1: Add OTLP Hostname

1. **Go to Cloudflare Zero Trust Dashboard**:
    - Navigate to **Networks** → **Tunnels**
    - Find tunnel ID: `REDACTED_CLOUDFLARE_TUNNEL_ID`
    - Click **Configure**

2. **Add Public Hostname**:
   ```
   Subdomain: otlp
   Domain: gtemp1.com
   Path: (leave empty)
   ```

3. **Configure the service**:
   ```
   Type: HTTP
   URL: scanium-alloy:4318
   ```

4. **Additional settings**:
    - **TLS** → Verify TLS: OFF (Alloy uses HTTP internally)

5. **Save the hostname**

***REMOVED******REMOVED******REMOVED*** Step 2: Verify OTLP Tunnel

After configuration propagates (~30 seconds):

```bash
***REMOVED*** Test OTLP endpoint health
curl -s https://otlp.gtemp1.com/v1/logs -X POST \
  -H "Content-Type: application/json" \
  -d '{"resourceLogs":[]}' -w "%{http_code}\n" -o /dev/null
```

Expected: `200` (empty logs accepted)

***REMOVED******REMOVED******REMOVED*** Mobile App Configuration

The Android app defaults to `https://otlp.gtemp1.com` for OTLP telemetry.

For local development with direct NAS access, override in `local.properties`:

```properties
scanium.otlp.endpoint=http://192.168.x.x:4318
scanium.otlp.enabled=true
```

See: `androidApp/build.gradle.kts` for configuration options.

---

***REMOVED******REMOVED*** Current Network Configuration

***REMOVED******REMOVED******REMOVED*** Grafana Container Networks:

- `scanium-observability` (internal): For Loki, Tempo, Mimir access
- `backend_scanium-network` (shared): For cloudflared access

***REMOVED******REMOVED******REMOVED*** Cloudflared Container Network:

- `backend_scanium-network` (shared): Can reach both backend and grafana

***REMOVED******REMOVED******REMOVED*** Container Names for Ingress Rules:

- Backend API: `scanium-backend` (port 8080)
- Grafana: `scanium-grafana` (port 3000)

---

***REMOVED******REMOVED*** Tunnel Configuration Summary

| Hostname             | Service     | Container         | Port |
|----------------------|-------------|-------------------|------|
| `scanium.gtemp1.com` | Backend API | `scanium-backend` | 8080 |
| `grafana.gtemp1.com` | Grafana     | `scanium-grafana` | 3000 |
| `otlp.gtemp1.com`    | Alloy OTLP  | `scanium-alloy`   | 4318 |

---

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** "Bad Gateway" or "502"

**Problem**: Cloudflared can't reach Grafana container

**Solutions**:

1. Verify Grafana is running:
   ```bash
   ssh nas "/usr/local/bin/docker ps | grep grafana"
   ```

2. Check Grafana is on backend network:
   ```bash
   ssh nas "/usr/local/bin/docker inspect scanium-grafana -f '{{range \$k,\$v := .NetworkSettings.Networks}}{{\$k}} {{end}}'"
   ```

   Should show: `scanium-observability backend_scanium-network`

3. Test from cloudflared container:
   ```bash
   ssh nas "/usr/local/bin/docker exec scanium-cloudflared wget -O- http://scanium-grafana:3000/api/health"
   ```

***REMOVED******REMOVED******REMOVED*** "DNS resolution failed"

**Problem**: DNS record not created

**Solution**:

1. Check Cloudflare DNS dashboard
2. Manually create CNAME if needed:
   ```
   Type: CNAME
   Name: grafana
   Target: REDACTED_CLOUDFLARE_TUNNEL_ID.cfargotunnel.com
   Proxy status: Proxied (orange cloud)
   ```

***REMOVED******REMOVED******REMOVED*** Infinite redirect or "too many redirects"

**Problem**: Grafana ROOT_URL misconfigured

**Solution**: Grafana is already configured with `GF_SERVER_ROOT_URL=https://grafana.gtemp1.com`

If issues persist:

1. Check Grafana logs:
   ```bash
   ssh nas "/usr/local/bin/docker logs scanium-grafana | tail -50"
   ```

2. Verify environment variable:
   ```bash
   ssh nas "/usr/local/bin/docker exec scanium-grafana env | grep GF_SERVER"
   ```

---

***REMOVED******REMOVED*** Security Considerations

***REMOVED******REMOVED******REMOVED*** Current Setup:

- ✅ Grafana behind Cloudflare (DDoS protection)
- ✅ HTTPS enforced by Cloudflare
- ⚠️ **Anonymous access enabled** (Admin role)
- ⚠️ Login form disabled

***REMOVED******REMOVED******REMOVED*** For Production:

**Option 1: Enable Authentication**

```yaml
***REMOVED*** In monitoring/docker-compose.yml
environment:
  - GF_AUTH_ANONYMOUS_ENABLED=false  ***REMOVED*** Disable anonymous
  - GF_AUTH_DISABLE_LOGIN_FORM=false ***REMOVED*** Enable login
  ***REMOVED*** Add admin credentials:
  - GF_SECURITY_ADMIN_USER=admin
  - GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD}
```

**Option 2: Add Cloudflare Access (Recommended)**

1. Go to Cloudflare Zero Trust → Access → Applications
2. Create Self-hosted application
3. Add `grafana.gtemp1.com`
4. Configure authentication (Email OTP, Google, etc.)
5. Add access policies (e.g., allow specific emails)

This adds authentication before reaching Grafana.

---

***REMOVED******REMOVED*** Deployment

Changes deployed on: 2026-01-11

**Modified files**:

- `monitoring/docker-compose.yml`:
    - Added `backend_scanium-network` to Grafana container
    - Updated `GF_SERVER_ROOT_URL` to `https://grafana.gtemp1.com`

**Restart command**:

```bash
ssh nas "cd /volume1/docker/scanium/repo/monitoring && /usr/local/bin/docker-compose up -d grafana"
```

---

***REMOVED******REMOVED*** Quick Reference

**Access URLs**:

- Backend API: https://scanium.gtemp1.com
- Grafana: https://grafana.gtemp1.com
- OTLP Telemetry: https://otlp.gtemp1.com

**Container logs**:

```bash
***REMOVED*** Grafana logs
ssh nas "/usr/local/bin/docker logs -f scanium-grafana"

***REMOVED*** Cloudflared logs
ssh nas "/usr/local/bin/docker logs -f scanium-cloudflared"
```

**Restart services**:

```bash
***REMOVED*** Restart Grafana
ssh nas "cd /volume1/docker/scanium/repo/monitoring && /usr/local/bin/docker-compose restart grafana"

***REMOVED*** Restart cloudflared
ssh nas "cd /volume1/docker/scanium/repo/backend && /usr/local/bin/docker-compose restart cloudflared"
```

---

**Last Updated**: 2026-01-14T12:00:00Z
