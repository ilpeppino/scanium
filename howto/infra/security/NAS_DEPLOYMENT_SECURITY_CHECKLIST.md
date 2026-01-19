***REMOVED*** NAS Deployment Security Checklist

**Target:** Synology DS418play (Intel Celeron J3455, 2 cores, 6GB RAM)
**Stack:** Docker containers (Backend API, PostgreSQL, LGTM monitoring stack)
**Network:** LAN + Cloudflare Tunnel for external access

---

***REMOVED******REMOVED*** Pre-Deployment Checklist

***REMOVED******REMOVED******REMOVED*** 1. Secrets Management

- [ ] **Generate strong passwords** (minimum 32 characters)
  ```bash
  ***REMOVED*** PostgreSQL password
  openssl rand -base64 32

  ***REMOVED*** Grafana admin password
  openssl rand -base64 32

  ***REMOVED*** Session signing secret (64 bytes)
  openssl rand -base64 64

  ***REMOVED*** API keys
  openssl rand -hex 32
  ```

- [ ] **Create env files from examples**
  ```bash
  cp deploy/nas/env/backend.env.example deploy/nas/env/backend.env
  cp deploy/nas/env/monitoring.env.example deploy/nas/env/monitoring.env
  ```

- [ ] **Verify env files are gitignored**
  ```bash
  git check-ignore deploy/nas/env/backend.env
  ***REMOVED*** Should output the path (ignored)
  ```

- [ ] **Never commit actual secrets**
    - `backend.env` contains real credentials
    - `monitoring.env` contains Grafana password
    - Check with `git status` before every commit

- [ ] **Store secrets securely** (outside Docker volumes)
    - Google Vision SA key: `/volume1/docker/scanium/secrets/vision-sa.json`
    - Set restrictive permissions: `chmod 600 secrets/*`

---

***REMOVED******REMOVED******REMOVED*** 2. Firewall Configuration

- [ ] **Block external access to internal services**

  | Port | Service | Should Be | Action |
    |------|---------|-----------|--------|
  | 3000 | Grafana | LAN only OR Cloudflare Tunnel | Block from WAN |
  | 3100 | Loki | Internal only | Block all external |
  | 3200 | Tempo | Internal only | Block all external |
  | 4317 | Alloy gRPC | LAN only | Block from WAN |
  | 4318 | Alloy HTTP | LAN only | Block from WAN |
  | 5432 | PostgreSQL | Internal only | Block all external |
  | 8080 | Backend API | Cloudflare Tunnel only | Block direct WAN |
  | 9009 | Mimir | Internal only | Block all external |
  | 12345 | Alloy UI | Internal only | Block all external |

- [ ] **Configure Synology Firewall**
    1. Control Panel → Security → Firewall
    2. Create profile "Scanium"
    3. Rules:
        - Allow LAN (192.168.x.x) to ports 3000, 4317, 4318, 8080
        - Deny all other sources to these ports
        - Allow all to Cloudflare Tunnel port (if used)

- [ ] **Verify port bindings in docker-compose**
  ```yaml
  ***REMOVED*** CORRECT: Internal only
  ports:
    - "127.0.0.1:3100:3100"

  ***REMOVED*** WRONG: Publicly accessible
  ports:
    - "3100:3100"  ***REMOVED*** This binds to 0.0.0.0
  ```

---

***REMOVED******REMOVED******REMOVED*** 3. Docker Container Security

- [ ] **No privileged containers**
  ```bash
  ***REMOVED*** Check for privileged mode (should return nothing)
  grep -r "privileged: true" deploy/nas/compose/
  ```

- [ ] **No host networking**
  ```bash
  ***REMOVED*** Check for host network (should return nothing)
  grep -r "network_mode: host" deploy/nas/compose/
  ```

- [ ] **Root user only where necessary**
    - Monitoring containers use `user: "0"` for NAS volume permissions (acceptable)
    - Backend API should NOT run as root
    - Verify: `docker exec scanium-api whoami` (should not be root)

- [ ] **Read-only config mounts**
  ```yaml
  volumes:
    - /path/to/config.yaml:/etc/app/config.yaml:ro  ***REMOVED*** :ro = read-only
  ```

- [ ] **Resource limits set**
  ```yaml
  deploy:
    resources:
      limits:
        memory: 256M
        cpus: '0.5'
  ```

---

***REMOVED******REMOVED******REMOVED*** 4. Database Security

- [ ] **Strong PostgreSQL password**
    - NOT the default `scanium`
    - At least 32 random characters
    - Stored only in `backend.env`

- [ ] **TLS enabled for database connections**
  ```
  DATABASE_URL=postgresql://user:pass@postgres:5432/db?sslmode=require
  ```

- [ ] **Connection pool limited**
  ```
  DATABASE_URL=postgresql://user:pass@postgres:5432/db?connection_limit=5
  ```

- [ ] **Database not exposed externally**
    - Port 5432 bound to Docker network only
    - No port mapping to host

- [ ] **Regular backups configured**
  ```bash
  ***REMOVED*** Daily backup cron job
  0 2 * * * docker exec scanium-postgres pg_dump -U scanium scanium > /volume1/backups/scanium-$(date +%Y%m%d).sql
  ```

---

***REMOVED******REMOVED******REMOVED*** 5. Grafana Security

- [ ] **Anonymous access DISABLED**
  ```env
  GF_AUTH_ANONYMOUS_ENABLED=false
  ```

- [ ] **Login form ENABLED**
  ```env
  GF_AUTH_DISABLE_LOGIN_FORM=false
  ```

- [ ] **Strong admin password set**
  ```env
  GF_SECURITY_ADMIN_PASSWORD=<32+ char random password>
  ```

- [ ] **Default admin password changed**
    - First login: Change from env password
    - Or: `docker exec scanium-grafana grafana-cli admin reset-admin-password <newpassword>`

- [ ] **Viewers cannot edit dashboards**
  ```env
  GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer  ***REMOVED*** Not Admin
  ```

---

***REMOVED******REMOVED******REMOVED*** 6. Cloudflare Tunnel Security

- [ ] **Tunnel token stored securely**
    - In `backend.env` only
    - NOT in docker-compose.yml
    - NOT in git history

- [ ] **Origin services not publicly exposed**
    - Backend API accessible ONLY via tunnel
    - No direct port forwarding to WAN

- [ ] **Cloudflare Access configured** (if needed)
    - Require authentication for Grafana access
    - IP allowlist for admin endpoints

- [ ] **Tunnel routes configured correctly**
    - Public hostname → `http://localhost:8080` (backend)
    - NOT exposing monitoring stack publicly

---

***REMOVED******REMOVED******REMOVED*** 7. Monitoring Stack Security

- [ ] **Loki not exposed externally**
    - Port 3100 bound to 127.0.0.1 only
    - Contains application logs (potentially sensitive)

- [ ] **Tempo not exposed externally**
    - Port 3200 bound to 127.0.0.1 only
    - Contains trace data

- [ ] **Mimir not exposed externally**
    - Port 9009 bound to 127.0.0.1 only
    - Contains metrics data

- [ ] **OTLP endpoints access controlled**
    - Consider API key authentication for Alloy
    - Or restrict to known source IPs

- [ ] **Log retention appropriate**
    - Reduce if logs contain sensitive data
    - Default 14 days may be excessive

- [ ] **PII not logged**
    - User data redacted from logs
    - API keys masked in logs
    - No image data in logs

---

***REMOVED******REMOVED******REMOVED*** 8. System Updates

- [ ] **DSM up to date**
    - Control Panel → Update & Restore
    - Enable auto-update for security patches

- [ ] **Docker images pinned to specific versions**
  ```yaml
  image: grafana/grafana:10.3.1  ***REMOVED*** GOOD: pinned
  image: grafana/grafana:latest   ***REMOVED*** BAD: unpredictable
  ```

- [ ] **Periodic image updates scheduled**
  ```bash
  ***REMOVED*** Monthly update script
  docker compose -f docker-compose.nas.monitoring.yml pull
  docker compose -f docker-compose.nas.monitoring.yml up -d
  ```

- [ ] **Security advisories monitored**
    - Subscribe to Grafana security advisories
    - Subscribe to Node.js security advisories
    - Subscribe to PostgreSQL security advisories

---

***REMOVED******REMOVED******REMOVED*** 9. Backup & Recovery

- [ ] **Database backups automated**
  ```bash
  ***REMOVED*** Create backup
  docker exec scanium-postgres pg_dump -U scanium scanium | gzip > backup.sql.gz

  ***REMOVED*** Restore backup
  gunzip -c backup.sql.gz | docker exec -i scanium-postgres psql -U scanium scanium
  ```

- [ ] **Configuration backed up**
    - `deploy/nas/env/*.env` (encrypted!)
    - `monitoring/` directory
    - Docker compose files

- [ ] **Backup location secure**
    - Encrypted volume or external encrypted drive
    - NOT on same NAS volume as data

- [ ] **Recovery tested**
    - Test restore procedure quarterly
    - Document recovery steps

---

***REMOVED******REMOVED******REMOVED*** 10. Monitoring & Alerting

- [ ] **Disk usage alerts configured**
    - Alert at 80% capacity
    - Synology: Control Panel → Notifications

- [ ] **Container health monitored**
    - All containers have healthchecks
    - Synology Container Manager shows health status

- [ ] **Failed login alerts**
    - Grafana: Configure alerting for failed logins
    - Backend: Log failed API key attempts

- [ ] **Resource exhaustion alerts**
    - CPU > 90% for 5 minutes
    - Memory > 85%
    - Disk I/O saturation

---

***REMOVED******REMOVED*** Post-Deployment Verification

***REMOVED******REMOVED******REMOVED*** Quick Security Scan

```bash
***REMOVED*** 1. Check no secrets in environment
docker exec scanium-api env | grep -i password
***REMOVED*** Should show masked values only

***REMOVED*** 2. Check port bindings
docker port scanium-loki
***REMOVED*** Should be empty (internal only)

docker port scanium-grafana
***REMOVED*** Should show 0.0.0.0:3000 (intentionally exposed)

***REMOVED*** 3. Check Grafana auth
curl -s http://NAS_IP:3000/api/dashboards/home
***REMOVED*** Should return 401 Unauthorized (auth required)

***REMOVED*** 4. Check backend auth
curl -s http://NAS_IP:8080/v1/classify
***REMOVED*** Should return 401 (API key required)

***REMOVED*** 5. Check database not exposed
nc -zv NAS_IP 5432
***REMOVED*** Should fail (connection refused)
```

***REMOVED******REMOVED******REMOVED*** Penetration Testing (Optional)

- [ ] **nmap scan from external network**
  ```bash
  nmap -sT -p 1-65535 NAS_EXTERNAL_IP
  ***REMOVED*** Only Cloudflare Tunnel port should be open
  ```

- [ ] **API fuzzing**
    - Test rate limiting works
    - Test input validation
    - Test authentication bypass attempts

---

***REMOVED******REMOVED*** Emergency Response

***REMOVED******REMOVED******REMOVED*** If Credentials Compromised

1. **Immediately** rotate affected credentials
2. **Revoke** API keys in affected services
3. **Change** database password and update env
4. **Restart** all containers
5. **Audit** logs for unauthorized access
6. **Report** to affected parties if data breach

***REMOVED******REMOVED******REMOVED*** If Container Compromised

1. **Stop** affected container immediately
2. **Preserve** container logs and filesystem for analysis
3. **Delete** and recreate container from clean image
4. **Audit** network traffic logs
5. **Patch** vulnerability before restarting

---

***REMOVED******REMOVED*** Checklist Summary

| Category              | Items | Critical |
|-----------------------|-------|----------|
| Secrets Management    | 5     | Yes      |
| Firewall              | 3     | Yes      |
| Docker Security       | 5     | Yes      |
| Database              | 5     | Yes      |
| Grafana               | 5     | Yes      |
| Cloudflare Tunnel     | 4     | Yes      |
| Monitoring Stack      | 6     | Medium   |
| System Updates        | 4     | Medium   |
| Backup & Recovery     | 4     | Medium   |
| Monitoring & Alerting | 4     | Medium   |

**Total: 45 items**

---

*Last updated: 2026-01-05*
*Review this checklist before each deployment and quarterly thereafter.*

---

***REMOVED******REMOVED*** Addendum: Issues Identified in 2026-01-05 Review

The following issues were identified and should be addressed:

| Issue | Priority | Description                                                                |
|-------|----------|----------------------------------------------------------------------------|
| ***REMOVED***361  | P1       | NAS monitoring containers use `restart: "no"` - change to `unless-stopped` |
| ***REMOVED***362  | P2       | Alloy admin UI port 12345 exposed - bind to localhost                      |
| ***REMOVED***363  | P2       | Tempo max_workers=100 too high - reduce to 4                               |
| ***REMOVED***364  | P2       | Prometheus scrape interval 15s - increase to 60s                           |
| ***REMOVED***367  | P2       | NAS Grafana auth not explicitly enforced                                   |
| ***REMOVED***368  | P3       | Create NAS-specific config overlays                                        |

See [REPO_REVIEW_ACTION_BACKLOG.md](REPO_REVIEW_ACTION_BACKLOG.md) for full details.
