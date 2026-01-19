***REMOVED*** GitHub Issue Templates for Review Findings

**Note:** GitHub CLI authentication expired. Create these issues manually or run:

```bash
gh auth login -h github.com
```

Then execute the commands below.

---

***REMOVED******REMOVED*** P0 - Critical Issues

***REMOVED******REMOVED******REMOVED*** Issue 1: SEC-001 - Hardcoded Secrets in Git History

```bash
gh issue create \
  --title "[SEC-001] P0: Hardcoded secrets committed to git - CRITICAL" \
  --label "security,P0-critical,backend" \
  --body "***REMOVED******REMOVED*** Summary
Active credentials have been committed to git in \`backend/.env\`, \`.env.backup\`, and \`.env.last\` files.

***REMOVED******REMOVED*** Evidence
- \`backend/.env.backup\` and \`backend/.env.last\` are tracked by git
- Files contain: API keys, eBay credentials, Cloudflare tunnel token, session secrets

***REMOVED******REMOVED*** Impact
Any attacker with repository access can extract production credentials.

***REMOVED******REMOVED*** Remediation
1. **Immediately** revoke all exposed credentials
2. Add \`.env.backup\` and \`.env.last\` to \`.gitignore\`
3. Use \`git filter-repo\` to scrub git history
4. Rotate all secrets

***REMOVED******REMOVED*** References
- [REPO_REVIEW_REPORT.md](docs/REPO_REVIEW_REPORT.md***REMOVED***sec-001-hardcoded-secrets-in-git-history)"
```

***REMOVED******REMOVED******REMOVED*** Issue 2: SEC-002 - Database Default Credentials

```bash
gh issue create \
  --title "[SEC-002] P0: Database uses default credentials - CRITICAL" \
  --label "security,P0-critical,backend,infrastructure" \
  --body "***REMOVED******REMOVED*** Summary
PostgreSQL configuration defaults to weak credentials \`scanium:scanium\`.

***REMOVED******REMOVED*** Evidence
\`backend/docker-compose.yml\` lines 10-12:
\`\`\`yaml
POSTGRES_USER: \${POSTGRES_USER:-scanium}
POSTGRES_PASSWORD: \${POSTGRES_PASSWORD:-scanium}
\`\`\`

***REMOVED******REMOVED*** Impact
Default credentials used if environment variables not set.

***REMOVED******REMOVED*** Remediation
1. Remove defaults - require explicit password: \`\${POSTGRES_PASSWORD:?POSTGRES_PASSWORD required}\`
2. Generate strong password: \`openssl rand -base64 32\`
3. Enable TLS: \`sslmode=require\`

***REMOVED******REMOVED*** References
- [REPO_REVIEW_REPORT.md](docs/REPO_REVIEW_REPORT.md***REMOVED***sec-002-database-default-credentials)"
```

---

***REMOVED******REMOVED*** P1 - High Priority Issues

***REMOVED******REMOVED******REMOVED*** Issue 3: SEC-003 - Grafana Anonymous Admin Access

```bash
gh issue create \
  --title "[SEC-003] P1: Grafana allows anonymous admin access" \
  --label "security,P1-high,monitoring" \
  --body "***REMOVED******REMOVED*** Summary
Local dev Grafana config enables anonymous admin access without authentication.

***REMOVED******REMOVED*** Evidence
\`monitoring/docker-compose.yml\` lines 103-108:
\`\`\`yaml
GF_AUTH_ANONYMOUS_ENABLED=true
GF_AUTH_ANONYMOUS_ORG_ROLE=Admin
GF_AUTH_DISABLE_LOGIN_FORM=true
\`\`\`

***REMOVED******REMOVED*** Impact
Anyone accessing port 3000 has full admin access to dashboards and data.

***REMOVED******REMOVED*** Remediation
1. Set \`GF_AUTH_ANONYMOUS_ENABLED=false\`
2. Set \`GF_AUTH_DISABLE_LOGIN_FORM=false\`
3. Configure admin password

***REMOVED******REMOVED*** References
- [REPO_REVIEW_REPORT.md](docs/REPO_REVIEW_REPORT.md***REMOVED***sec-003-grafana-anonymous-admin-access)"
```

***REMOVED******REMOVED******REMOVED*** Issue 4: SEC-004 - OTLP Endpoints Exposed Without Auth

```bash
gh issue create \
  --title "[SEC-004] P1: OTLP ports exposed without authentication" \
  --label "security,P1-high,monitoring,infrastructure" \
  --body "***REMOVED******REMOVED*** Summary
Telemetry ingestion endpoints (4317/4318) are publicly accessible without authentication.

***REMOVED******REMOVED*** Evidence
\`monitoring/docker-compose.yml\`:
\`\`\`yaml
ports:
  - \"4317:4317\"  ***REMOVED*** Bound to 0.0.0.0
  - \"4318:4318\"
\`\`\`

***REMOVED******REMOVED*** Impact
- Telemetry injection attacks
- DoS via log flooding
- Data exfiltration via log inspection

***REMOVED******REMOVED*** Remediation
1. Bind to localhost: \`\"127.0.0.1:4317:4317\"\`
2. Or add API key authentication to Alloy
3. Or use firewall to restrict to known IPs

***REMOVED******REMOVED*** References
- [REPO_REVIEW_REPORT.md](docs/REPO_REVIEW_REPORT.md***REMOVED***sec-004-otlp-endpoints-exposed-without-auth)"
```

***REMOVED******REMOVED******REMOVED*** Issue 5: SEC-005 - Database TLS Disabled

```bash
gh issue create \
  --title "[SEC-005] P1: Database SSL/TLS disabled" \
  --label "security,P1-high,backend,infrastructure" \
  --body "***REMOVED******REMOVED*** Summary
Database connections use \`sslmode=disable\` allowing unencrypted traffic.

***REMOVED******REMOVED*** Evidence
\`backend/.env.backup\` line 13: \`sslmode=disable\`

***REMOVED******REMOVED*** Impact
Database traffic can be intercepted on network.

***REMOVED******REMOVED*** Remediation
1. Set \`sslmode=require\` in all DATABASE_URL values
2. Configure PostgreSQL for TLS
3. Verify with: \`psql \"sslmode=require ...\"\`

***REMOVED******REMOVED*** References
- [REPO_REVIEW_REPORT.md](docs/REPO_REVIEW_REPORT.md***REMOVED***sec-005-database-ssltls-disabled)"
```

***REMOVED******REMOVED******REMOVED*** Issue 6: PERF-001 - Loki Retention Workers Overwhelm NAS

```bash
gh issue create \
  --title "[PERF-001] P1: Loki retention workers (150) will starve NAS CPU" \
  --label "performance,P1-high,monitoring,NAS" \
  --body "***REMOVED******REMOVED*** Summary
Loki configured with 150 parallel retention workers on 2-core NAS.

***REMOVED******REMOVED*** Evidence
\`monitoring/loki/loki.yaml\` line 48:
\`\`\`yaml
retention_delete_worker_count: 150
\`\`\`

***REMOVED******REMOVED*** Impact
CPU exhaustion on DS418play (2 cores), affecting all services.

***REMOVED******REMOVED*** Remediation
Set to 2-4 workers: \`retention_delete_worker_count: 2\`

***REMOVED******REMOVED*** Target Environment
Synology DS418play (Intel Celeron J3455, 2 cores, 6GB RAM)

***REMOVED******REMOVED*** References
- [REPO_REVIEW_REPORT.md](docs/REPO_REVIEW_REPORT.md***REMOVED***perf-001-loki-retention-workers-overwhelm-cpu)"
```

***REMOVED******REMOVED******REMOVED*** Issue 7: PERF-002 - Log Retention Will Fill NAS Storage

```bash
gh issue create \
  --title "[PERF-002] P1: 14-day log retention will fill NAS storage" \
  --label "performance,P1-high,monitoring,NAS" \
  --body "***REMOVED******REMOVED*** Summary
Combined retention periods project to 40-50GB+ storage usage.

***REMOVED******REMOVED*** Evidence
- Loki: 336h (14 days) - \`loki.yaml:52\`
- Tempo: 168h (7 days) - \`tempo.yaml:23\`
- Mimir: 360h (15 days) - \`mimir.yaml:28\`

***REMOVED******REMOVED*** Impact
Storage exhaustion on NAS within weeks of heavy usage.

***REMOVED******REMOVED*** Remediation
Reduce all retention to 72h (3 days):
- \`limits_config.retention_period: 72h\`
- \`compactor.compaction.block_retention: 72h\`
- \`limits.compactor_blocks_retention_period: 3d\`

***REMOVED******REMOVED*** References
- [REPO_REVIEW_REPORT.md](docs/REPO_REVIEW_REPORT.md***REMOVED***perf-002-log-retention-will-fill-nas-storage)"
```

---

***REMOVED******REMOVED*** P2 - Medium Priority Issues

***REMOVED******REMOVED******REMOVED*** Issue 8: Backend Tests Failing

```bash
gh issue create \
  --title "[MAINT-001] P2: Backend tests failing - missing unified-cache module" \
  --label "bug,P2-medium,backend,testing" \
  --body "***REMOVED******REMOVED*** Summary
4 test suites fail due to missing \`infra/cache/unified-cache.js\` module.

***REMOVED******REMOVED*** Evidence
\`\`\`
npm test output:
Error: Failed to load url ../../infra/cache/unified-cache.js
FAIL  src/modules/assistant/routes.test.ts
FAIL  src/modules/classifier/routes.test.ts
FAIL  src/infra/http/plugins/cors.test.ts
FAIL  src/infra/http/plugins/security.test.ts
\`\`\`

***REMOVED******REMOVED*** Impact
CI pipeline unreliable, code changes may break undetected.

***REMOVED******REMOVED*** Remediation
Create the missing unified-cache module or fix import paths.

***REMOVED******REMOVED*** References
- [REPO_REVIEW_REPORT.md](docs/REPO_REVIEW_REPORT.md***REMOVED***test-coverage-issues)"
```

***REMOVED******REMOVED******REMOVED*** Issue 9: Healthcheck Uses Wrong Endpoint

```bash
gh issue create \
  --title "[PERF-005] P2: Healthcheck uses /health instead of /readyz" \
  --label "bug,P2-medium,infrastructure,NAS" \
  --body "***REMOVED******REMOVED*** Summary
Docker healthcheck calls \`/health\` which doesn't verify database connectivity.

***REMOVED******REMOVED*** Evidence
\`deploy/nas/compose/docker-compose.nas.backend.yml\` line 118:
\`\`\`yaml
test: ['CMD', 'node', '-e', \"require('http').get('http://localhost:8080/health', ...)\"]
\`\`\`

Should use \`/readyz\` which includes database check.

***REMOVED******REMOVED*** Impact
Database failures go undetected by container orchestration.

***REMOVED******REMOVED*** Remediation
Change healthcheck endpoint to \`/readyz\`.

***REMOVED******REMOVED*** References
- [REPO_REVIEW_REPORT.md](docs/REPO_REVIEW_REPORT.md***REMOVED***perf-005)"
```

---

***REMOVED******REMOVED*** Batch Creation Script

After authenticating with `gh auth login`, create all issues:

```bash
***REMOVED***!/bin/bash
***REMOVED*** Run from repo root after: gh auth login

***REMOVED*** P0 Issues
gh issue create --title "[SEC-001] P0: Hardcoded secrets in git - CRITICAL" --label "security,P0-critical" --body-file /dev/stdin << 'EOF'
Hardcoded secrets committed to git in backend/.env*, requiring immediate credential rotation and git history scrubbing.
See: docs/REPO_REVIEW_REPORT.md***REMOVED***sec-001
EOF

gh issue create --title "[SEC-002] P0: Database default credentials - CRITICAL" --label "security,P0-critical" --body "Default scanium:scanium credentials in docker-compose. See: docs/REPO_REVIEW_REPORT.md***REMOVED***sec-002"

***REMOVED*** P1 Issues
gh issue create --title "[SEC-003] P1: Grafana anonymous admin access" --label "security,P1-high" --body "See: docs/REPO_REVIEW_REPORT.md***REMOVED***sec-003"
gh issue create --title "[SEC-004] P1: OTLP ports exposed without auth" --label "security,P1-high" --body "See: docs/REPO_REVIEW_REPORT.md***REMOVED***sec-004"
gh issue create --title "[SEC-005] P1: Database TLS disabled" --label "security,P1-high" --body "See: docs/REPO_REVIEW_REPORT.md***REMOVED***sec-005"
gh issue create --title "[PERF-001] P1: Loki workers overwhelm NAS" --label "performance,P1-high" --body "See: docs/REPO_REVIEW_REPORT.md***REMOVED***perf-001"
gh issue create --title "[PERF-002] P1: Log retention fills NAS storage" --label "performance,P1-high" --body "See: docs/REPO_REVIEW_REPORT.md***REMOVED***perf-002"

***REMOVED*** P2 Issues
gh issue create --title "[SEC-006] P2: No rate limit on admin endpoints" --label "security,P2-medium" --body "See: docs/REPO_REVIEW_REPORT.md"
gh issue create --title "[SEC-007] P2: No JSON body size limit" --label "security,P2-medium" --body "See: docs/REPO_REVIEW_REPORT.md"
gh issue create --title "[PERF-003] P2: Scrape interval too aggressive" --label "performance,P2-medium" --body "See: docs/REPO_REVIEW_REPORT.md"
gh issue create --title "[PERF-004] P2: No DB connection pool limit" --label "performance,P2-medium" --body "See: docs/REPO_REVIEW_REPORT.md"
gh issue create --title "[PERF-005] P2: Healthcheck wrong endpoint" --label "bug,P2-medium" --body "See: docs/REPO_REVIEW_REPORT.md"
gh issue create --title "[MAINT-001] P2: Backend tests failing" --label "bug,P2-medium" --body "See: docs/REPO_REVIEW_REPORT.md"

echo "Created 13 issues. View at: https://github.com/ilpeppino/scanium/issues"
```

---

***REMOVED******REMOVED*** Required Labels

Before creating issues, ensure these labels exist:

```bash
gh label create "P0-critical" --color "B60205" --description "Critical priority - immediate action required"
gh label create "P1-high" --color "D93F0B" --description "High priority - fix within 24-48 hours"
gh label create "P2-medium" --color "FBCA04" --description "Medium priority - fix within 1 week"
gh label create "P3-low" --color "0E8A16" --description "Low priority - backlog"
gh label create "security" --color "D73A4A" --description "Security vulnerability or hardening"
gh label create "performance" --color "5319E7" --description "Performance optimization"
gh label create "NAS" --color "1D76DB" --description "NAS deployment specific"
gh label create "monitoring" --color "0052CC" --description "Observability stack"
gh label create "infrastructure" --color "006B75" --description "Infrastructure and deployment"
```

---

*Generated by repository review on 2026-01-01*
