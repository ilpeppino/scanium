***REMOVED***!/bin/bash
***REMOVED*** Create Phase 2 GitHub issues for Scanium enterprise readiness
***REMOVED*** Run after Phase 1 (20 issues) are complete
***REMOVED*** Prerequisites: Labels already created from CREATE_LABELS.sh
***REMOVED*** Usage: bash docs/go-live/CREATE_ISSUES_PHASE2.sh

set -e

echo "🚀 Creating Phase 2 (Enterprise-Ready) issues..."
echo ""

***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** P2 HIGH - Security Hardening
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

echo "Creating P2 severity:high security issues..."

gh issue create \
  --title "[ENTERPRISE][HIGH] Penetration testing and vulnerability assessment" \
  --label "severity:high,epic:security,area:backend,area:android,priority:p2" \
  --body "***REMOVED******REMOVED*** Epic
epic:security

***REMOVED******REMOVED*** Priority
**P2** – enterprise readiness / 10K+ users

***REMOVED******REMOVED*** Problem Statement
No external penetration testing has been performed. Application has not been audited by security professionals for:
- OWASP Top 10 vulnerabilities (injection, XSS, CSRF, etc.)
- Mobile-specific attacks (SSL pinning bypass, root detection bypass)
- API security (broken authentication, rate limit bypass)
- Infrastructure security (container escape, privilege escalation)

**Why it matters:** Unknown vulnerabilities may exist. Enterprise customers and compliance frameworks (SOC 2, ISO 27001) require regular pen testing.

***REMOVED******REMOVED*** Evidence
- No pen test reports in repository
- No external security audit history
- No vulnerability disclosure policy
- \`docs/go-live/GO_LIVE_BACKLOG_SUMMARY.md\` - Identified as enterprise readiness gap

***REMOVED******REMOVED*** Acceptance Criteria
**Pre-Test Preparation:**
- [ ] Define pen test scope: Backend API, Android app, infrastructure (K8s, DB)
- [ ] Choose pen testing firm (HackerOne, Synack, local firm)
- [ ] Set up isolated pen test environment (duplicate of production)
- [ ] Create vulnerability disclosure policy (security@scanium.com)
- [ ] Define severity levels and SLAs for remediation

**Pen Test Execution:**
- [ ] Backend API testing: Authentication bypass, SQL injection, API abuse, rate limit bypass
- [ ] Android app testing: Certificate pinning bypass, root detection bypass, data extraction
- [ ] Infrastructure testing: Container security, secret exposure, network segmentation
- [ ] Receive pen test report with findings categorized by CVSS score
- [ ] No critical (CVSS 9.0+) or high (CVSS 7.0+) findings unresolved

**Remediation:**
- [ ] Fix all critical findings within 7 days
- [ ] Fix all high findings within 30 days
- [ ] Fix all medium findings within 90 days
- [ ] Re-test after fixes to verify remediation
- [ ] Document remediation in security log

**Ongoing:**
- [ ] Schedule annual pen tests
- [ ] Add pen test artifacts to compliance folder
- [ ] Update security.txt with contact info

***REMOVED******REMOVED*** Verification Steps
\`\`\`bash
***REMOVED*** Review pen test report
cat reports/security/pentest-2025-q1.pdf

***REMOVED*** Verify critical findings resolved
grep \"CRITICAL\" reports/security/pentest-2025-q1.pdf
***REMOVED*** Expected: 0 unresolved critical findings

***REMOVED*** Verify security.txt published
curl https://api.scanium.com/.well-known/security.txt
***REMOVED*** Expected: Contact, Expires, Preferred-Languages fields

***REMOVED*** Re-test critical finding (example: auth bypass)
curl -X GET https://api.scanium.com/auth/ebay/status
***REMOVED*** Expected: 401 Unauthorized (fixed)
\`\`\`"

gh issue create \
  --title "[ENTERPRISE][HIGH] Third-party security audit and certification" \
  --label "severity:high,epic:security,epic:docs,area:backend,area:android,priority:p2" \
  --body "***REMOVED******REMOVED*** Epic
epic:security, epic:docs

***REMOVED******REMOVED*** Priority
**P2** – enterprise readiness / compliance requirements

***REMOVED******REMOVED*** Problem Statement
No third-party security audit or compliance certification exists. Enterprise customers and regulated industries require:
- SOC 2 Type II (Security, Availability, Confidentiality)
- ISO 27001 (Information Security Management)
- GDPR compliance verification
- Mobile app security certification (OWASP MASVS Level 2)

**Why it matters:** Cannot sell to enterprise customers or enter regulated markets (healthcare, finance) without certifications. Builds trust and demonstrates security maturity.

***REMOVED******REMOVED*** Evidence
- No SOC 2, ISO 27001, or compliance certifications
- \`docs/_archive/2025-12/security/SECURITY_RISK_ASSESSMENT.md\` - MASVS assessment done internally, not certified
- \`docs/go-live/GO_LIVE_BACKLOG_SUMMARY.md\` - Compliance checklist shows gaps

***REMOVED******REMOVED*** Acceptance Criteria
**Choose Certification Path:**
- [ ] Determine required certifications:
  - SOC 2 Type II (most common for SaaS, 6-12 months, \$20-50K)
  - ISO 27001 (international standard, 6-12 months, \$15-40K)
  - OWASP MASVS Level 2 (mobile security, 1-2 months, \$5-10K)
  - GDPR compliance audit (EU users, 2-3 months, \$10-20K)
- [ ] Choose auditor/certification body (Big 4 accounting firm or specialized firm)

**SOC 2 Type II (if chosen):**
- [ ] Define trust service criteria (Security, Availability, Confidentiality)
- [ ] Implement required controls (access management, change management, monitoring)
- [ ] Run for 6+ months to demonstrate controls operate effectively
- [ ] Undergo audit by CPA firm
- [ ] Receive SOC 2 Type II report
- [ ] Publish SOC 2 report to enterprise customers (under NDA)

**ISO 27001 (if chosen):**
- [ ] Implement Information Security Management System (ISMS)
- [ ] Complete risk assessment and treatment plan
- [ ] Document policies, procedures, and controls
- [ ] Undergo Stage 1 audit (documentation review)
- [ ] Undergo Stage 2 audit (implementation verification)
- [ ] Receive ISO 27001 certificate
- [ ] Annual surveillance audits

**OWASP MASVS Level 2 (mobile):**
- [ ] Hire mobile security firm to assess against MASVS Level 2 requirements
- [ ] Verify all MASVS-L2 controls implemented (auth, crypto, storage, network)
- [ ] Receive MASVS Level 2 certification or attestation letter
- [ ] Display certification badge in app and marketing

**GDPR Compliance Audit:**
- [ ] Data protection impact assessment (DPIA)
- [ ] Privacy by design verification
- [ ] Data subject rights implementation (access, deletion, portability)
- [ ] Third-party data processing agreements (Google, OpenAI, eBay)
- [ ] Receive GDPR compliance attestation

***REMOVED******REMOVED*** Verification Steps
\`\`\`bash
***REMOVED*** Verify SOC 2 report published
ls -l compliance/soc2/Scanium-SOC2-TypeII-2025.pdf

***REMOVED*** Verify ISO 27001 certificate
curl https://scanium.com/certifications/iso27001.pdf

***REMOVED*** Verify MASVS badge in app
***REMOVED*** Settings → About → Security Certifications
***REMOVED*** Expected: \"OWASP MASVS Level 2 Certified\"

***REMOVED*** Verify GDPR compliance in privacy policy
curl https://scanium.com/privacy | grep GDPR
***REMOVED*** Expected: GDPR rights section
\`\`\`"

echo ""
echo "Creating P2 severity:medium operational excellence issues..."

gh issue create \
  --title "[ENTERPRISE][MEDIUM] Load testing and capacity planning" \
  --label "severity:medium,epic:backend,epic:observability,area:backend,priority:p2" \
  --body "***REMOVED******REMOVED*** Epic
epic:backend, epic:observability

***REMOVED******REMOVED*** Priority
**P2** – enterprise readiness / 10K+ users

***REMOVED******REMOVED*** Problem Statement
No load testing or capacity planning has been performed. Cannot answer:
- How many concurrent users can the system handle?
- What is the maximum requests per second (RPS) before degradation?
- When do we need to scale up (add pods, increase DB size)?
- What are the bottlenecks (CPU, memory, database, API rate limits)?

**Why it matters:** Risk of performance degradation or outages at scale. Cannot plan infrastructure costs. Enterprise customers require SLAs and capacity guarantees.

***REMOVED******REMOVED*** Evidence
- No load tests documented
- No capacity plan or resource sizing guidelines
- No performance benchmarks or SLAs documented
- Current deployment: 3 pods, db.t3.small - chosen arbitrarily

***REMOVED******REMOVED*** Acceptance Criteria
**Load Testing Setup:**
- [ ] Choose load testing tool: k6 (recommended), JMeter, Gatling, or Locust
- [ ] Create load test scenarios:
  - Authentication flow (login, JWT refresh)
  - Classification workflow (image upload, Google Vision call, response)
  - Assistant workflow (chat request, OpenAI call, response)
  - Read-heavy (GET /items, GET /config)
  - Write-heavy (POST /items, POST /listings)
- [ ] Define test profiles:
  - Baseline: 100 concurrent users, 10 RPS
  - Target: 1,000 concurrent users, 100 RPS
  - Peak: 5,000 concurrent users, 500 RPS
  - Stress: 10,000+ users until failure

**Execute Load Tests:**
- [ ] Run baseline test (100 users) → measure p50/p95/p99 latency, error rate
- [ ] Run target test (1,000 users) → identify first bottleneck
- [ ] Run peak test (5,000 users) → verify system can handle peak traffic
- [ ] Run stress test (10,000+ users) → find breaking point
- [ ] Measure resource usage: CPU, memory, database connections, API quotas

**Analyze Results:**
- [ ] Identify bottlenecks: Database queries, Google Vision rate limits, CPU-bound operations
- [ ] Calculate capacity: Max RPS before p95 latency > 500ms or error rate > 1%
- [ ] Determine scaling triggers: CPU > 70% = add pod, DB connections > 80% = upgrade DB
- [ ] Document resource requirements per 1,000 users

**Capacity Plan:**
- [ ] Create capacity plan document: \`docs/operations/CAPACITY_PLAN.md\`
- [ ] Define SLAs: p95 latency < 500ms, error rate < 1%, uptime 99.9%
- [ ] Set autoscaling policies: Min 3 pods, max 20 pods, target CPU 70%
- [ ] Estimate costs at scale: 1K, 10K, 100K users
- [ ] Create scaling runbook: When to scale up DB, when to add Redis cache

**Continuous Load Testing:**
- [ ] Add load tests to CI/CD (run weekly against staging)
- [ ] Set performance regression alerts (p95 latency increased by 20%+)
- [ ] Schedule quarterly load tests against production (during off-peak)

***REMOVED******REMOVED*** Verification Steps
\`\`\`bash
***REMOVED*** Run load test locally
cd backend/tests/load
k6 run --vus 100 --duration 5m classification-load-test.js

***REMOVED*** Output:
***REMOVED*** ✓ http_req_duration (p95) < 500ms
***REMOVED*** ✓ http_req_failed < 1%
***REMOVED*** Max RPS sustained: 150

***REMOVED*** Run load test against staging
k6 run --vus 1000 --duration 10m \\
  --env BASE_URL=https://staging-api.scanium.com \\
  classification-load-test.js

***REMOVED*** Review capacity plan
cat docs/operations/CAPACITY_PLAN.md

***REMOVED*** Example output:
***REMOVED*** Max capacity (current config): 500 RPS, 2,000 concurrent users
***REMOVED*** Bottleneck: PostgreSQL connections (max 100)
***REMOVED*** Scaling trigger: Add db.t3.medium when connections > 80
***REMOVED*** Cost at 10K users: \$800/month
\`\`\`"

gh issue create \
  --title "[ENTERPRISE][MEDIUM] Disaster recovery drills and testing" \
  --label "severity:medium,epic:observability,area:backend,priority:p2" \
  --body "***REMOVED******REMOVED*** Epic
epic:observability

***REMOVED******REMOVED*** Priority
**P2** – enterprise readiness / operational maturity

***REMOVED******REMOVED*** Problem Statement
Disaster recovery runbook exists (from Phase 1 issue ***REMOVED***254) but has never been tested. Critical scenarios not validated:
- Database restore from backup (is the backup valid? how long does it take?)
- Failover to standby database replica (does it work? data loss?)
- Complete infrastructure rebuild from scratch (K8s manifests, secrets)
- Backend pod failure (does autoscaling work?)

**Why it matters:** Disaster recovery plans fail when executed under pressure. Runbooks become outdated. Recovery Time Objective (RTO) and Recovery Point Objective (RPO) are guesses, not measurements.

***REMOVED******REMOVED*** Evidence
- Issue ***REMOVED***254 created incident response runbook but no drill schedule
- Issue ***REMOVED***240 implemented backups but no restore tests documented
- No RTO/RPO measurements from actual tests
- No quarterly drill schedule

***REMOVED******REMOVED*** Acceptance Criteria
**Quarterly DR Drill Schedule:**
- [ ] Schedule quarterly disaster recovery drills (Q1, Q2, Q3, Q4)
- [ ] Rotate drill scenarios each quarter
- [ ] Assign drill coordinator (SRE lead or on-call rotation)

**DR Drill Scenarios:**

**Q1 Drill: Database Restore**
- [ ] Simulate database corruption (drop tables in test DB)
- [ ] Restore from latest backup using runbook
- [ ] Measure RTO (time to restore) - Target: < 1 hour
- [ ] Measure RPO (data loss) - Target: < 15 minutes (last backup)
- [ ] Verify data integrity (row counts, foreign keys, recent data)
- [ ] Document actual RTO/RPO achieved

**Q2 Drill: Failover to Standby Replica**
- [ ] Simulate primary database failure (stop primary pod)
- [ ] Trigger failover to standby replica using runbook
- [ ] Measure failover time - Target: < 5 minutes
- [ ] Verify read/write operations work on new primary
- [ ] Verify no data loss (compare transaction logs)
- [ ] Promote standby to new primary, spin up new standby

**Q3 Drill: Complete Infrastructure Rebuild**
- [ ] Simulate catastrophic failure (delete K8s namespace)
- [ ] Rebuild from scratch using IaC (Terraform, K8s manifests)
- [ ] Restore database from backup
- [ ] Restore secrets from Secret Manager
- [ ] Verify all services healthy
- [ ] Measure total rebuild time - Target: < 2 hours

**Q4 Drill: Multi-Service Failure**
- [ ] Simulate cascading failure (backend down, database slow, monitoring down)
- [ ] Follow incident response runbook (triage, escalate, communicate)
- [ ] Test status page updates (notify users of outage)
- [ ] Test on-call rotation and escalation
- [ ] Measure time to resolution - Target: < 30 minutes

**Post-Drill Actions:**
- [ ] Write drill report: What worked, what failed, lessons learned
- [ ] Update runbook with corrections (steps that failed or were unclear)
- [ ] Create action items for gaps discovered
- [ ] Share report with team in postmortem format
- [ ] Update RTO/RPO targets based on actual measurements

**Continuous Improvement:**
- [ ] Track RTO/RPO trends over time (are we getting faster?)
- [ ] Add new drill scenarios as architecture changes
- [ ] Automate drill triggering (chaos engineering, next step)

***REMOVED******REMOVED*** Verification Steps
\`\`\`bash
***REMOVED*** Q1 Drill: Database Restore
***REMOVED*** 1. Simulate corruption
kubectl exec -it postgres-0 -n scanium-prod -- psql -U scanium -c \"DROP TABLE users;\"

***REMOVED*** 2. Start timer
START_TIME=\$(date +%s)

***REMOVED*** 3. Execute restore from backup
./scripts/restore-from-backup.sh --backup-file /backups/postgres/latest.dump

***REMOVED*** 4. Stop timer
END_TIME=\$(date +%s)
RTO=\$((END_TIME - START_TIME))
echo \"RTO: \${RTO} seconds\"  ***REMOVED*** Target: < 3600s (1 hour)

***REMOVED*** 5. Verify data
psql -d scanium -c \"SELECT COUNT(*) FROM users;\"
***REMOVED*** Expected: Match pre-drill count

***REMOVED*** 6. Document results
cat reports/dr-drills/2025-q1-database-restore.md
***REMOVED*** Expected: RTO: 35 minutes, RPO: 10 minutes, Status: Success
\`\`\`"

gh issue create \
  --title "[ENTERPRISE][MEDIUM] Chaos engineering and resilience testing" \
  --label "severity:medium,epic:backend,epic:observability,area:backend,priority:p2" \
  --body "***REMOVED******REMOVED*** Epic
epic:backend, epic:observability

***REMOVED******REMOVED*** Priority
**P2** – enterprise readiness / resilience validation

***REMOVED******REMOVED*** Problem Statement
System resilience is untested under failure conditions. Unknown how the system behaves when:
- Backend pod crashes randomly
- Database connections are slow or timing out
- Google Vision API returns 500 errors
- Network partitions occur between services
- CPU or memory is exhausted

**Why it matters:** Production failures reveal weaknesses. Chaos engineering proactively injects failures to validate resilience and improve recovery.

***REMOVED******REMOVED*** Evidence
- No chaos engineering tools configured (Chaos Mesh, Litmus, Gremlin)
- No automated failure injection tests
- Resilience features exist (circuit breakers, retries, timeouts) but not validated under failure
- \`backend/src/infra/resilience/circuit-breaker.ts\` - Circuit breaker implemented but not chaos-tested

***REMOVED******REMOVED*** Acceptance Criteria
**Choose Chaos Engineering Tool:**
- [ ] Choose tool: Chaos Mesh (K8s native, free), Litmus Chaos, or Gremlin (paid)
- [ ] Install in staging environment first
- [ ] Create chaos experiment templates

**Chaos Experiment Categories:**

**1. Pod Failure (Pod Kill)**
- [ ] Randomly kill 1 backend pod every 5 minutes
- [ ] Verify: Requests routed to healthy pods, no 5xx errors
- [ ] Verify: K8s restarts pod automatically
- [ ] Verify: Prometheus alerts fire for pod down (if configured)
- [ ] Verify: p95 latency stays < 1s during pod restart

**2. Network Latency**
- [ ] Inject 500ms latency between backend and database
- [ ] Verify: Requests timeout gracefully after 10s
- [ ] Verify: Circuit breaker opens after 5 failures
- [ ] Verify: Error messages returned to client (not 500 Internal Error)

**3. Resource Exhaustion (CPU Stress)**
- [ ] Inject CPU stress (consume 100% CPU on 1 pod)
- [ ] Verify: K8s autoscaler adds new pod
- [ ] Verify: Requests routed to healthy pods
- [ ] Verify: Stressed pod eventually killed and replaced

**4. Database Failure**
- [ ] Simulate database connection failures (reject connections for 30s)
- [ ] Verify: Backend circuit breaker opens
- [ ] Verify: Graceful degradation (503 Service Unavailable, not crashes)
- [ ] Verify: Circuit breaker closes when DB recovers
- [ ] Verify: No data corruption or inconsistent state

**5. External API Failure (Google Vision)**
- [ ] Mock Google Vision to return 500 errors
- [ ] Verify: Classification fails gracefully (fallback to on-device labels)
- [ ] Verify: Retries with exponential backoff (max 2 retries)
- [ ] Verify: Circuit breaker opens after threshold failures
- [ ] Verify: User sees \"Cloud classification unavailable\" message

**6. Memory Leak Simulation**
- [ ] Inject memory leak (allocate memory without freeing)
- [ ] Verify: OOMKilled (Out of Memory) when limit exceeded
- [ ] Verify: K8s restarts pod automatically
- [ ] Verify: No cascading failures to other pods

**Chaos Experiment Schedule:**
- [ ] Run chaos experiments in staging weekly (automated)
- [ ] Run controlled chaos in production monthly (off-peak hours)
- [ ] Document chaos results and resilience improvements

**Continuous Improvement:**
- [ ] Fix weaknesses discovered during chaos experiments
- [ ] Add new experiments as architecture evolves
- [ ] Gradually increase chaos intensity (more frequent, longer duration)

***REMOVED******REMOVED*** Verification Steps
\`\`\`bash
***REMOVED*** Install Chaos Mesh in staging
kubectl apply -f https://mirrors.chaos-mesh.org/latest/crd.yaml
kubectl apply -f https://mirrors.chaos-mesh.org/latest/chaos-mesh.yaml

***REMOVED*** Create pod kill experiment
cat <<EOF | kubectl apply -f -
apiVersion: chaos-mesh.org/v1alpha1
kind: PodChaos
metadata:
  name: backend-pod-kill
  namespace: scanium-staging
spec:
  action: pod-kill
  mode: one
  selector:
    namespaces:
      - scanium-staging
    labelSelectors:
      app: scanium-backend
  scheduler:
    cron: '*/5 * * * *'  ***REMOVED*** Every 5 minutes
EOF

***REMOVED*** Monitor during chaos
watch kubectl get pods -n scanium-staging

***REMOVED*** Verify requests still succeed
for i in {1..100}; do
  curl -s https://staging-api.scanium.com/healthz | jq .status
  sleep 1
done
***REMOVED*** Expected: 100 \"ok\" responses (no failures during pod kill)

***REMOVED*** Check circuit breaker metrics
curl http://localhost:8080/metrics | grep circuit_breaker
***REMOVED*** Expected: circuit_breaker_state{service=\"database\"} = 0 (closed)

***REMOVED*** Review chaos experiment results
kubectl describe podchaos backend-pod-kill -n scanium-staging
\`\`\`"

gh issue create \
  --title "[ENTERPRISE][MEDIUM] Blue-green and canary deployment strategy" \
  --label "severity:medium,epic:backend,area:backend,area:ci,priority:p2" \
  --body "***REMOVED******REMOVED*** Epic
epic:backend

***REMOVED******REMOVED*** Priority
**P2** – enterprise readiness / zero-downtime deployments

***REMOVED******REMOVED*** Problem Statement
Current deployment strategy is basic rolling update (K8s default). Risky for production:
- New version deployed to all users immediately
- No gradual rollout (0% → 100% instantly)
- Rollback requires full redeploy (slow)
- No traffic splitting to test new version with subset of users

**Why it matters:** Breaking changes reach all users immediately. No way to detect issues before full rollout. Enterprise customers require zero-downtime deployments and safe rollback.

***REMOVED******REMOVED*** Evidence
- \`.github/workflows/backend-deploy-production.yml\` (from Phase 1 ***REMOVED***249) uses basic K8s deployment
- No Argo Rollouts, Flagger, or Istio configured
- No canary or blue-green deployment documented
- \`docs/go-live/GO_LIVE_BACKLOG_SUMMARY.md\` - Identified as enterprise gap

***REMOVED******REMOVED*** Acceptance Criteria
**Choose Deployment Strategy:**
- [ ] Choose strategy: Blue-Green (recommended for DB migrations) or Canary (recommended for gradual rollout)
- [ ] Choose tool: Argo Rollouts (K8s native, free), Flagger (with Istio), or cloud provider (AWS CodeDeploy, GCP Cloud Deploy)

**Blue-Green Deployment:**
- [ ] Install Argo Rollouts or equivalent
- [ ] Create blue (production) and green (new version) environments
- [ ] Deploy new version to green environment
- [ ] Run smoke tests against green environment
- [ ] Switch traffic from blue to green (load balancer update)
- [ ] Monitor green for 10 minutes (error rate, latency)
- [ ] If healthy: Decommission blue. If errors: Switch traffic back to blue (instant rollback)
- [ ] Advantages: Instant rollback, full environment testing
- [ ] Disadvantages: 2x resources during deployment

**Canary Deployment (Alternative):**
- [ ] Install Argo Rollouts with analysis
- [ ] Deploy new version to canary pods (10% of traffic)
- [ ] Monitor canary for 5 minutes (error rate, latency, logs)
- [ ] If healthy: Increase to 25% → 50% → 100% over 30 minutes
- [ ] If errors: Automatic rollback to stable version
- [ ] Configure success metrics: error rate < 1%, p95 latency < 500ms
- [ ] Advantages: Gradual rollout, less resource usage
- [ ] Disadvantages: Slower rollout, version skew issues

**Rollback Strategy:**
- [ ] Automate rollback triggers (error rate > 5%, p95 latency > 1s, manual abort)
- [ ] Test rollback procedure (simulate failed deployment, verify rollback works)
- [ ] Document rollback SLA (< 5 minutes to revert)

**Database Migration Safety:**
- [ ] Use blue-green for deployments with DB migrations
- [ ] Ensure migrations are backward-compatible (new code works with old schema)
- [ ] Run migrations before traffic switch (on green environment)
- [ ] Test rollback with schema rollback (if migration fails)

**CI/CD Integration:**
- [ ] Update deployment workflow to use blue-green or canary
- [ ] Add deployment approval gates (manual approval before 100% rollout)
- [ ] Configure Slack notifications for deployment progress (25%, 50%, 75%, 100%)

***REMOVED******REMOVED*** Verification Steps
\`\`\`bash
***REMOVED*** Blue-Green Deployment Example

***REMOVED*** 1. Deploy new version to green
kubectl apply -f k8s/production/deployment-green.yaml

***REMOVED*** 2. Wait for green pods healthy
kubectl wait --for=condition=ready pod -l app=scanium-backend,env=green -n scanium-prod --timeout=300s

***REMOVED*** 3. Run smoke tests against green
curl https://green-api.scanium.com/healthz
***REMOVED*** Expected: 200 OK

***REMOVED*** 4. Switch traffic to green (update service selector)
kubectl patch service scanium-backend -n scanium-prod -p '{\"spec\":{\"selector\":{\"env\":\"green\"}}}'

***REMOVED*** 5. Monitor green for errors
kubectl logs -f -l app=scanium-backend,env=green -n scanium-prod | grep ERROR
***REMOVED*** Expected: No errors for 10 minutes

***REMOVED*** 6. If healthy, decommission blue
kubectl delete deployment scanium-backend-blue -n scanium-prod

***REMOVED*** 7. If errors, rollback to blue
kubectl patch service scanium-backend -n scanium-prod -p '{\"spec\":{\"selector\":{\"env\":\"blue\"}}}'
***REMOVED*** Traffic switched back in < 5 seconds

***REMOVED*** Canary Deployment Example (Argo Rollouts)

***REMOVED*** 1. Deploy with canary strategy
kubectl apply -f k8s/production/rollout-canary.yaml

***REMOVED*** 2. Watch canary rollout
kubectl argo rollouts get rollout scanium-backend -n scanium-prod --watch

***REMOVED*** Output:
***REMOVED*** Revision 2: 10% (1/10 pods) - Analyzing (5m)
***REMOVED*** Revision 2: 25% (2/10 pods) - Healthy
***REMOVED*** Revision 2: 50% (5/10 pods) - Healthy
***REMOVED*** Revision 2: 100% (10/10 pods) - Healthy

***REMOVED*** 3. Abort rollout if needed
kubectl argo rollouts abort scanium-backend -n scanium-prod
***REMOVED*** Automatic rollback to revision 1
\`\`\`"

gh issue create \
  --title "[ENTERPRISE][MEDIUM] Database query optimization and indexing" \
  --label "severity:medium,epic:backend,area:backend,priority:p2" \
  --body "***REMOVED******REMOVED*** Epic
epic:backend

***REMOVED******REMOVED*** Priority
**P2** – enterprise readiness / performance at scale

***REMOVED******REMOVED*** Problem Statement
Database queries are not optimized for scale. No query performance analysis or indexing strategy:
- No slow query logging or profiling
- No database indexes beyond primary keys
- No query plan analysis (EXPLAIN)
- No connection pooling tuning
- No query caching strategy

**Why it matters:** Database becomes bottleneck at scale (10K+ users). Slow queries increase p95 latency and reduce throughput. High connection count exhausts database.

***REMOVED******REMOVED*** Evidence
- \`backend/prisma/schema.prisma\` - Only basic indexes (@@index on listings)
- No slow query logs or pg_stat_statements enabled
- No query performance monitoring in Grafana
- Load testing (Phase 2 issue) will likely reveal database bottlenecks

***REMOVED******REMOVED*** Acceptance Criteria
**Query Performance Monitoring:**
- [ ] Enable PostgreSQL slow query logging (queries > 100ms)
- [ ] Enable pg_stat_statements extension (track query stats)
- [ ] Add query performance dashboard in Grafana
- [ ] Configure alerts for slow queries (p95 > 500ms)

**Index Strategy:**
- [ ] Analyze common queries using pg_stat_statements
- [ ] Identify missing indexes (queries with sequential scans)
- [ ] Add indexes for:
  - [ ] User lookups: \`users.email\` (already unique, verify index exists)
  - [ ] eBay connections: \`ebay_connections.userId\` + \`ebay_connections.environment\`
  - [ ] Listings: \`listings.userId\`, \`listings.status\`, \`listings.marketplace\`
  - [ ] Timestamps: \`listings.createdAt\`, \`listings.updatedAt\` (for range queries)
- [ ] Use composite indexes for multi-column WHERE clauses
- [ ] Avoid over-indexing (indexes slow down writes)

**Query Optimization:**
- [ ] Run EXPLAIN ANALYZE on slow queries
- [ ] Optimize N+1 query problems (use Prisma include/select efficiently)
- [ ] Add query result caching (Redis) for read-heavy endpoints
- [ ] Use pagination for large result sets (cursor-based, not offset)
- [ ] Avoid SELECT * (only fetch needed columns)

**Connection Pooling:**
- [ ] Review Prisma connection pool settings (default: 10 connections)
- [ ] Increase pool size for high load (e.g., 20-50 connections)
- [ ] Configure connection timeout (30s)
- [ ] Monitor connection usage (alert if > 80% of pool)
- [ ] Consider external connection pooler (PgBouncer) for 100+ pods

**Database Scaling:**
- [ ] Measure current query throughput (QPS)
- [ ] Determine when to scale vertically (upgrade DB instance size)
- [ ] Determine when to scale horizontally (read replicas)
- [ ] Document read replica strategy (route read-only queries to replica)

**Continuous Optimization:**
- [ ] Review slow query log weekly
- [ ] Analyze new queries after feature launches
- [ ] Vacuum and analyze tables monthly (prevents table bloat)

***REMOVED******REMOVED*** Verification Steps
\`\`\`bash
***REMOVED*** Enable slow query logging
psql -d scanium -c \"ALTER SYSTEM SET log_min_duration_statement = 100;\"  ***REMOVED*** 100ms
psql -d scanium -c \"SELECT pg_reload_conf();\"

***REMOVED*** Enable pg_stat_statements
psql -d scanium -c \"CREATE EXTENSION IF NOT EXISTS pg_stat_statements;\"

***REMOVED*** View slow queries
psql -d scanium -c \"
SELECT
  query,
  calls,
  total_exec_time,
  mean_exec_time,
  max_exec_time
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;
\"

***REMOVED*** Example output:
***REMOVED*** query                              | calls | total_exec_time | mean_exec_time | max_exec_time
***REMOVED*** SELECT * FROM listings WHERE ...   | 1523  | 45234.5         | 29.7           | 450.2

***REMOVED*** Analyze query plan
psql -d scanium -c \"EXPLAIN ANALYZE SELECT * FROM listings WHERE userId = '123' AND status = 'active';\"

***REMOVED*** Expected before optimization:
***REMOVED*** Seq Scan on listings (cost=0.00..1234.56)  ***REMOVED*** BAD: Sequential scan

***REMOVED*** Add index
psql -d scanium -c \"CREATE INDEX idx_listings_user_status ON listings(userId, status);\"

***REMOVED*** Verify index used
psql -d scanium -c \"EXPLAIN ANALYZE SELECT * FROM listings WHERE userId = '123' AND status = 'active';\"

***REMOVED*** Expected after optimization:
***REMOVED*** Index Scan using idx_listings_user_status (cost=0.00..8.56)  ***REMOVED*** GOOD: Index scan

***REMOVED*** Monitor query performance
curl http://localhost:3000/d/postgres-queries  ***REMOVED*** Grafana dashboard
***REMOVED*** Verify: p95 query time < 100ms
\`\`\`"

echo ""
echo "Creating P2 severity:medium observability issues..."

gh issue create \
  --title "[ENTERPRISE][MEDIUM] Mobile app telemetry and distributed tracing" \
  --label "severity:medium,epic:mobile,epic:observability,area:android,area:logging,priority:p2" \
  --body "***REMOVED******REMOVED*** Epic
epic:mobile, epic:observability

***REMOVED******REMOVED*** Priority
**P2** – enterprise readiness / end-to-end visibility

***REMOVED******REMOVED*** Problem Statement
Backend has OpenTelemetry instrumentation (logs, traces, metrics) but mobile app does not. Cannot trace requests end-to-end:
- User taps \"Classify\" → Image upload → Backend → Google Vision → Response
- No mobile performance metrics (network latency, API call duration)
- No distributed trace IDs (cannot correlate mobile logs with backend logs)
- No mobile error tracking in observability stack (Sentry exists, but not integrated with LGTM)

**Why it matters:** Cannot diagnose user-reported issues. \"App is slow\" → Which part? Network? Backend? Google Vision? Cannot tell.

***REMOVED******REMOVED*** Evidence
- \`backend/src/app.ts\` - Backend has OTel instrumentation
- \`monitoring/\` - LGTM stack configured for backend only
- \`androidApp/\` - No OTel SDK integration
- \`docs/ARCHITECTURE.md:472\` - \"End-to-end telemetry from Android app to Grafana\" in roadmap

***REMOVED******REMOVED*** Acceptance Criteria
**Choose Mobile Telemetry Solution:**
- [ ] Option A: OpenTelemetry Android SDK (OTLP → Alloy → Loki/Tempo)
- [ ] Option B: Firebase Performance Monitoring (simple, but separate from LGTM stack)
- [ ] Option C: Custom instrumentation (manual spans, send to Alloy via HTTP)

**OpenTelemetry Integration (Recommended):**
- [ ] Add OpenTelemetry Android SDK dependency
- [ ] Initialize OTel in \`ScaniumApp.onCreate()\`
- [ ] Configure OTLP exporter to send to Alloy (http://alloy:4318/v1/traces)
- [ ] Add trace context propagation (inject trace ID in HTTP headers)
- [ ] Create spans for key operations:
  - [ ] App start (measure cold start time)
  - [ ] Image classification (measure upload + backend + response time)
  - [ ] Assistant request (measure request + OpenAI + response time)
  - [ ] eBay OAuth flow (measure redirect + callback + token exchange)
  - [ ] Camera frame processing (measure ML Kit inference time)
- [ ] Add custom attributes to spans (user ID, device model, Android version)
- [ ] Configure sampling (100% in staging, 10% in production to reduce data)

**Distributed Tracing:**
- [ ] Inject trace ID in HTTP requests to backend (W3C Trace Context header)
- [ ] Backend propagates trace ID to Google Vision and OpenAI
- [ ] View full trace in Grafana Tempo: Mobile → Backend → Google Vision → Response
- [ ] Measure end-to-end latency (user tap → response displayed)

**Mobile Metrics:**
- [ ] Export metrics to Mimir via OTLP:
  - API call duration (histogram)
  - API call error rate (counter)
  - Network type (WiFi vs cellular) (gauge)
  - App version (label)
- [ ] Create mobile performance dashboard in Grafana
- [ ] Add alerts for mobile performance degradation (p95 latency > 2s)

**Privacy and Data Minimization:**
- [ ] Redact PII from spans (user IDs hashed, no email addresses)
- [ ] Do not log image data or classification results in telemetry
- [ ] Configure retention: 7 days traces, 30 days metrics (GDPR compliance)
- [ ] Add opt-out setting in app (Settings → Privacy → Analytics)

**Testing:**
- [ ] Test trace propagation: Tap classify → View trace in Tempo
- [ ] Verify trace includes mobile span + backend span + Google Vision span
- [ ] Verify sampling works (10% of production traces captured)
- [ ] Test opt-out (user disables analytics → no spans sent)

***REMOVED******REMOVED*** Verification Steps
\`\`\`bash
***REMOVED*** Build app with OTel integration
./gradlew assembleDebug

***REMOVED*** Install on device
adb install androidApp/build/outputs/apk/debug/androidApp-debug.apk

***REMOVED*** Trigger classification
***REMOVED*** App → Camera → Scan object → Classify

***REMOVED*** View trace in Grafana Tempo
***REMOVED*** Navigate to Grafana → Explore → Tempo
***REMOVED*** Search for recent traces
***REMOVED*** Click trace to view timeline:

***REMOVED*** Trace ID: abc123def456
***REMOVED*** Duration: 2.3s
***REMOVED*** ├─ mobile.classification.upload (500ms)  [Android app]
***REMOVED*** ├─ backend.classify.handler (1.5s)       [Backend API]
***REMOVED***    ├─ google.vision.annotate (1.2s)      [Google Vision]
***REMOVED***    └─ db.query (100ms)                   [PostgreSQL]
***REMOVED*** └─ mobile.classification.render (300ms)  [Android app]

***REMOVED*** Verify mobile metrics in Grafana
***REMOVED*** Navigate to Grafana → Explore → Mimir
***REMOVED*** Query: histogram_quantile(0.95, rate(mobile_api_duration_bucket[5m]))
***REMOVED*** Expected: p95 latency < 2s

***REMOVED*** Test opt-out
***REMOVED*** Settings → Privacy → Disable Analytics
***REMOVED*** Trigger classification
***REMOVED*** Verify: No trace appears in Tempo
\`\`\`"

gh issue create \
  --title "[ENTERPRISE][MEDIUM] Synthetic monitoring and uptime checks" \
  --label "severity:medium,epic:observability,area:backend,priority:p2" \
  --body "***REMOVED******REMOVED*** Epic
epic:observability

***REMOVED******REMOVED*** Priority
**P2** – enterprise readiness / external monitoring

***REMOVED******REMOVED*** Problem Statement
Monitoring stack (Grafana, Loki, Tempo) only monitors internal metrics (backend logs, traces). No external uptime monitoring:
- No checks from outside the infrastructure (what users see)
- No multi-region uptime checks (is API reachable from US, EU, Asia?)
- No synthetic transactions (can users actually complete workflows?)
- Cannot detect issues invisible to internal monitoring (DNS failures, firewall rules, SSL cert expiration)

**Why it matters:** Internal monitoring shows backend is healthy, but users cannot reach the API (DNS issue). False sense of availability.

***REMOVED******REMOVED*** Evidence
- \`monitoring/\` - Internal monitoring only (Grafana, Loki, Tempo, Mimir)
- No external uptime monitoring configured
- No status page for user-facing outages
- No multi-region health checks

***REMOVED******REMOVED*** Acceptance Criteria
**Choose Synthetic Monitoring Tool:**
- [ ] Option A: Grafana Synthetic Monitoring (free tier, integrates with Grafana Cloud)
- [ ] Option B: Pingdom, UptimeRobot, or StatusCake (paid, simple)
- [ ] Option C: Custom solution (cron job + curl + alerting)

**Uptime Checks:**
- [ ] Configure uptime checks from multiple regions:
  - [ ] US East (AWS us-east-1 or similar)
  - [ ] EU West (AWS eu-west-1 or similar)
  - [ ] Asia Pacific (AWS ap-southeast-1 or similar)
- [ ] Check critical endpoints every 1-5 minutes:
  - [ ] GET https://api.scanium.com/healthz (expect 200)
  - [ ] GET https://api.scanium.com/ (expect 200, validate response JSON)
  - [ ] POST https://api.scanium.com/v1/classify (with test image, expect 200)
- [ ] Configure alerts for uptime < 99.9% (fire after 3 consecutive failures)

**Synthetic Transactions:**
- [ ] Create synthetic user workflows:
  - [ ] Classification workflow: Upload test image → Classify → Verify response
  - [ ] eBay OAuth workflow: Start OAuth → Callback → Verify token stored
  - [ ] Assistant workflow: Send test request → Verify response
- [ ] Run synthetic transactions every 15 minutes
- [ ] Alert if transaction fails or takes > 5s

**SSL Certificate Monitoring:**
- [ ] Monitor SSL certificate expiration (alert 30 days before expiry)
- [ ] Verify certificate chain is valid (no expired intermediate certs)
- [ ] Check for weak TLS versions (alert if TLS < 1.2 detected)

**DNS Monitoring:**
- [ ] Monitor DNS resolution for api.scanium.com from multiple locations
- [ ] Alert if DNS resolution fails or returns wrong IP
- [ ] Check DNS propagation time (after DNS changes)

**Status Page:**
- [ ] Create public status page: https://status.scanium.com (or use statuspage.io)
- [ ] Display uptime for:
  - [ ] API (backend)
  - [ ] Google Vision integration
  - [ ] eBay API integration
  - [ ] OpenAI integration
- [ ] Auto-update status page from synthetic monitoring (API down → status page shows incident)
- [ ] Allow users to subscribe to status updates (email, SMS, RSS)
- [ ] Display uptime percentage (99.9% last 30 days)

**Integration with Alerting:**
- [ ] Send uptime alerts to same channels as internal monitoring (PagerDuty, Slack)
- [ ] Distinguish internal vs external failures (internal metric shows healthy, but external check fails → DNS/firewall issue)
- [ ] Escalate external failures faster (likely user-facing outage)

**Continuous Monitoring:**
- [ ] Review uptime reports monthly (identify patterns, improve SLAs)
- [ ] Analyze synthetic transaction failures (which workflows fail most?)
- [ ] Test status page updates during planned maintenance

***REMOVED******REMOVED*** Verification Steps
\`\`\`bash
***REMOVED*** Configure synthetic check (example: curl-based)
***REMOVED*** Add to cron (every 5 minutes)
*/5 * * * * curl -f https://api.scanium.com/healthz || echo \"API DOWN\" | mail -s \"Alert: API Down\" oncall@scanium.com

***REMOVED*** Test uptime check from multiple regions
***REMOVED*** US East
curl -I https://api.scanium.com/healthz
***REMOVED*** Expected: HTTP/2 200

***REMOVED*** EU West (use VPN or cloud VM)
ssh eu-west-vm
curl -I https://api.scanium.com/healthz
***REMOVED*** Expected: HTTP/2 200

***REMOVED*** Asia Pacific
ssh asia-vm
curl -I https://api.scanium.com/healthz
***REMOVED*** Expected: HTTP/2 200

***REMOVED*** Test synthetic transaction (classification)
curl -X POST https://api.scanium.com/v1/classify \\
  -H \"X-API-Key: synthetic-test-key\" \\
  -F \"image=@test-images/mug.jpg\" \\
  -F \"domainPackId=home_resale\"

***REMOVED*** Expected: 200 OK with domainCategoryId

***REMOVED*** Check SSL certificate expiration
echo | openssl s_client -connect api.scanium.com:443 2>/dev/null | openssl x509 -noout -dates
***REMOVED*** Expected: notAfter > 30 days from now

***REMOVED*** View status page
curl https://status.scanium.com
***REMOVED*** Expected: HTML with uptime percentages (99.95% last 30 days)

***REMOVED*** Test status page incident
***REMOVED*** Simulate API down → Verify status page shows \"Major Outage\"
\`\`\`"

echo ""
echo "Creating P2 severity:low optimization issues..."

gh issue create \
  --title "[ENTERPRISE][LOW] Error budget tracking and SLO enforcement" \
  --label "severity:low,epic:observability,area:backend,priority:p2" \
  --body "***REMOVED******REMOVED*** Epic
epic:observability

***REMOVED******REMOVED*** Priority
**P2** – enterprise readiness / SRE best practices

***REMOVED******REMOVED*** Problem Statement
SLOs defined (from Phase 1 issue ***REMOVED***239) but no error budget tracking:
- SLO: 99.9% uptime (43 minutes downtime/month allowed)
- SLO: p95 latency < 500ms
- SLO: Error rate < 1%

But no system to:
- Track remaining error budget (how much downtime left this month?)
- Alert when error budget exhausted (stop deployments, focus on reliability)
- Enforce budget policies (no new features when budget exhausted)

**Why it matters:** SLOs without error budgets are just guidelines. Error budgets create accountability and balance velocity vs reliability.

***REMOVED******REMOVED*** Evidence
- Issue ***REMOVED***239 created SLOs but no error budget automation
- \`monitoring/grafana/dashboards/\` - No error budget dashboard
- No policy for what happens when error budget exhausted

***REMOVED******REMOVED*** Acceptance Criteria
**Error Budget Calculation:**
- [ ] Define error budget formula:
  - Uptime budget: (1 - 0.999) × 30 days = 43 minutes/month
  - Latency budget: 0.1% of requests can exceed 500ms (1 in 1000)
  - Error budget: 1% of requests can fail (10 in 1000)
- [ ] Create error budget dashboard in Grafana showing:
  - Current uptime: 99.95% (budget remaining: 30 minutes)
  - Current p95 latency: 350ms (budget remaining: healthy)
  - Current error rate: 0.5% (budget remaining: 0.5%)
- [ ] Color-code budget: Green (>50% remaining), Yellow (25-50%), Red (<25%)

**Error Budget Alerts:**
- [ ] Alert when 75% of budget consumed (warning, still healthy)
- [ ] Alert when 100% of budget consumed (critical, stop deployments)
- [ ] Alert when budget exhausted with 1 week left in month (emergency, all hands)

**Error Budget Policy:**
- [ ] Document error budget policy (\`docs/operations/ERROR_BUDGET_POLICY.md\`)
- [ ] **Budget healthy (>50% remaining):**
  - Continue normal deployment velocity
  - Focus on new features
- [ ] **Budget warning (25-50% remaining):**
  - Reduce deployment frequency (weekly instead of daily)
  - Increase testing and canary duration
  - Focus on reliability improvements
- [ ] **Budget exhausted (0% remaining):**
  - **FREEZE deployments** (no new features, only critical bug fixes)
  - All-hands focus on reliability (fix bugs, add tests, reduce latency)
  - Daily incident reviews
  - Resume deployments when budget recovers to 25%

**Budget Reset:**
- [ ] Reset error budget monthly (first day of month)
- [ ] Review previous month's budget consumption in postmortem
- [ ] Adjust SLOs if budget consistently over/under-consumed

**Integration with CI/CD:**
- [ ] Add error budget check to deployment pipeline
- [ ] Block deployments when budget exhausted (override requires director approval)
- [ ] Notify team when deployment blocked due to budget

***REMOVED******REMOVED*** Verification Steps
\`\`\`bash
***REMOVED*** View error budget dashboard
curl http://localhost:3000/d/error-budget

***REMOVED*** Example output:
***REMOVED*** Uptime SLO: 99.9%
***REMOVED*** Current uptime: 99.87%
***REMOVED*** Downtime this month: 56 minutes (budget: 43 minutes)
***REMOVED*** **BUDGET EXHAUSTED** 🔴

***REMOVED*** Error Rate SLO: < 1%
***REMOVED*** Current error rate: 0.3%
***REMOVED*** Budget remaining: 0.7% ✅

***REMOVED*** p95 Latency SLO: < 500ms
***REMOVED*** Current p95: 420ms
***REMOVED*** Budget remaining: healthy ✅

***REMOVED*** Attempt deployment when budget exhausted
gh workflow run backend-deploy-production.yml

***REMOVED*** Expected: Workflow fails with error:
***REMOVED*** \"Deployment blocked: Uptime error budget exhausted (56/43 minutes used).
***REMOVED***  Focus on reliability improvements before deploying new features.
***REMOVED***  Override requires director approval.\"

***REMOVED*** Verify alert fired
***REMOVED*** Check Slack/PagerDuty for alert:
***REMOVED*** \"🔴 ERROR BUDGET EXHAUSTED: Uptime budget consumed (56/43 minutes).
***REMOVED***  Freeze deployments. Focus on reliability.\"

***REMOVED*** Review error budget policy
cat docs/operations/ERROR_BUDGET_POLICY.md
\`\`\`"

gh issue create \
  --title "[ENTERPRISE][LOW] CDN and static asset optimization" \
  --label "severity:low,epic:backend,area:backend,priority:p2" \
  --body "***REMOVED******REMOVED*** Epic
epic:backend

***REMOVED******REMOVED*** Priority
**P2** – enterprise readiness / global performance

***REMOVED******REMOVED*** Problem Statement
No CDN configured. All API requests go directly to origin server:
- Increased latency for users far from server (US users → EU server = 100ms+ latency)
- No caching for static assets (domain pack JSON, API documentation)
- No DDoS protection at edge
- Higher bandwidth costs (origin serves all traffic)

**Why it matters:** Global users experience high latency. Origin server handles all traffic (scaling costs). Vulnerable to DDoS attacks.

***REMOVED******REMOVED*** Evidence
- \`backend/src/app.ts\` - No CDN headers configured (Cache-Control, ETag)
- No Cloudflare, CloudFront, or Fastly configured
- Static assets served from origin (domain pack JSON, API docs)
- \`docs/ARCHITECTURE.md\` - No CDN mentioned in architecture

***REMOVED******REMOVED*** Acceptance Criteria
**Choose CDN Provider:**
- [ ] Option A: Cloudflare (free tier, simple setup, DDoS protection)
- [ ] Option B: AWS CloudFront (pay-as-you-go, integrates with AWS)
- [ ] Option C: Fastly or Akamai (enterprise, expensive)

**CDN Configuration:**
- [ ] Configure DNS to point api.scanium.com to CDN (not origin)
- [ ] Configure origin server as CDN backend
- [ ] Enable TLS/SSL at CDN (Let's Encrypt or managed cert)
- [ ] Configure cache rules:
  - [ ] Cache GET /v1/config (domain pack, TTL: 1 hour)
  - [ ] Cache GET /docs (API documentation, TTL: 1 day)
  - [ ] Do NOT cache POST/PUT/DELETE requests
  - [ ] Do NOT cache authenticated endpoints (bypass cache if Authorization header present)
- [ ] Set Cache-Control headers in backend responses:
  - [ ] Static assets: \`Cache-Control: public, max-age=3600\` (1 hour)
  - [ ] Dynamic endpoints: \`Cache-Control: no-store\`

**Cache Invalidation:**
- [ ] Configure cache invalidation trigger (purge cache when domain pack updated)
- [ ] Add cache purge to deployment workflow (clear cache after backend deploy)
- [ ] Document manual cache purge command (\`curl -X PURGE https://api.scanium.com/v1/config\`)

**DDoS Protection:**
- [ ] Enable CDN DDoS protection (rate limiting at edge)
- [ ] Configure WAF rules (block malicious IPs, SQL injection, XSS)
- [ ] Enable bot protection (block automated scrapers)

**Performance Optimization:**
- [ ] Enable Brotli/Gzip compression at CDN
- [ ] Enable HTTP/2 and HTTP/3 (QUIC)
- [ ] Configure edge locations (choose regions close to user base)

**Monitoring:**
- [ ] Add CDN metrics to Grafana (cache hit rate, bandwidth saved, latency)
- [ ] Monitor cache hit rate (target: >80% for cacheable endpoints)
- [ ] Alert if cache hit rate drops below 50% (cache config issue)

**Cost Optimization:**
- [ ] Review CDN bandwidth costs monthly
- [ ] Configure cache to reduce origin traffic (target: 50% reduction)
- [ ] Estimate savings: Bandwidth cost reduced from \$45/month to \$20/month

***REMOVED******REMOVED*** Verification Steps
\`\`\`bash
***REMOVED*** Configure DNS to use CDN
***REMOVED*** Update DNS A record: api.scanium.com → Cloudflare IP

***REMOVED*** Test CDN is serving requests
curl -I https://api.scanium.com/v1/config

***REMOVED*** Expected headers:
***REMOVED*** CF-Cache-Status: HIT (or X-Cache: Hit from cloudfront)
***REMOVED*** Cache-Control: public, max-age=3600

***REMOVED*** Test cache works
curl -I https://api.scanium.com/v1/config
***REMOVED*** First request: CF-Cache-Status: MISS
curl -I https://api.scanium.com/v1/config
***REMOVED*** Second request: CF-Cache-Status: HIT ✅

***REMOVED*** Test authenticated requests bypass cache
curl -I https://api.scanium.com/auth/ebay/status -H \"Authorization: Bearer token\"
***REMOVED*** Expected: CF-Cache-Status: BYPASS (not cached)

***REMOVED*** Test latency improvement
***REMOVED*** Before CDN (direct to origin in EU)
time curl -I https://origin-api.scanium.com/healthz
***REMOVED*** Expected: 150ms (US → EU latency)

***REMOVED*** After CDN (edge location in US)
time curl -I https://api.scanium.com/healthz
***REMOVED*** Expected: 20ms (US → US edge) ✅ 87% reduction

***REMOVED*** Purge cache after deploy
curl -X PURGE https://api.scanium.com/v1/config
***REMOVED*** Expected: CF-Cache-Status: PURGED

***REMOVED*** View CDN analytics
***REMOVED*** Cloudflare Dashboard → Analytics
***REMOVED*** Expected: Cache hit rate: 85%, Bandwidth saved: 60%
\`\`\`"

gh issue create \
  --title "[ENTERPRISE][LOW] Bug bounty program and responsible disclosure" \
  --label "severity:low,epic:security,area:backend,area:android,priority:p2" \
  --body "***REMOVED******REMOVED*** Epic
epic:security

***REMOVED******REMOVED*** Priority
**P2** – enterprise readiness / proactive security

***REMOVED******REMOVED*** Problem Statement
No bug bounty program or responsible disclosure process. Security researchers have no official channel to report vulnerabilities:
- No security.txt file (RFC 9116 standard)
- No vulnerability disclosure policy
- No reward structure for researchers
- Risk: Researchers sell vulnerabilities on black market instead of reporting

**Why it matters:** Bug bounty programs crowdsource security testing. Incentivize researchers to report vulnerabilities responsibly. Demonstrates security maturity to enterprise customers.

***REMOVED******REMOVED*** Evidence
- No \`https://api.scanium.com/.well-known/security.txt\`
- No bug bounty program documented
- No responsible disclosure policy
- Phase 2 penetration testing (issue created earlier) is one-time, bug bounty is continuous

***REMOVED******REMOVED*** Acceptance Criteria
**Create Responsible Disclosure Policy:**
- [ ] Write vulnerability disclosure policy (\`docs/security/VULNERABILITY_DISCLOSURE.md\`)
- [ ] Define scope: What is in scope (API, mobile app, infra), what is out of scope (social engineering, physical attacks)
- [ ] Define safe harbor: Researchers will not be prosecuted if they follow the policy
- [ ] Define response SLAs:
  - Critical: Acknowledge 24h, Fix 7 days, Disclose 90 days
  - High: Acknowledge 48h, Fix 30 days, Disclose 90 days
  - Medium: Acknowledge 1 week, Fix 90 days, Disclose 180 days
- [ ] Define disclosure timeline (coordinated disclosure, not full disclosure)

**Create security.txt File:**
- [ ] Create \`https://api.scanium.com/.well-known/security.txt\` (RFC 9116)
- [ ] Include fields:
  - Contact: security@scanium.com
  - Expires: 2026-12-31
  - Preferred-Languages: en
  - Canonical: https://api.scanium.com/.well-known/security.txt
  - Policy: https://scanium.com/security/disclosure
  - Acknowledgments: https://scanium.com/security/thanks
- [ ] Sign security.txt with PGP key (optional but recommended)
- [ ] Verify security.txt loads correctly and passes validation (https://securitytxt.org/)

**Launch Bug Bounty Program:**
- [ ] Choose platform: HackerOne (most popular), Bugcrowd, or Intigriti
- [ ] Define scope:
  - **In scope:**
    - Backend API: https://api.scanium.com
    - Android app: com.scanium.app (latest Play Store version)
    - Web assets: https://scanium.com
  - **Out of scope:**
    - Staging/dev environments (staging-api.scanium.com)
    - Social engineering, phishing
    - DDoS attacks
    - Third-party services (Google Vision, eBay API)
- [ ] Define reward structure (example):
  - Critical (RCE, Auth bypass, SQL injection): \$500-2000
  - High (XSS, CSRF, PII leak): \$200-500
  - Medium (Misc config issues): \$50-200
  - Low (Informational): Hall of Fame (no payout)
- [ ] Set program visibility: Private (invite-only, first 3 months) → Public (open to all)
- [ ] Invite trusted researchers to private program (seed with 10-20 researchers)

**Bug Bounty Operations:**
- [ ] Assign triage team (security engineer + backend engineer)
- [ ] Define triage SLA: Respond to submissions within 24-48h
- [ ] Create remediation workflow: Triage → Fix → Verify → Reward → Public disclosure (90 days)
- [ ] Track metrics: Submissions/month, valid bugs/month, average time to fix
- [ ] Budget: Allocate \$500-1000/month for rewards (starting budget)

**Security Hall of Fame:**
- [ ] Create public acknowledgments page: https://scanium.com/security/thanks
- [ ] List researchers who reported valid bugs (with permission)
- [ ] Thank researchers in release notes (coordinated disclosure)

**Continuous Improvement:**
- [ ] Review bug bounty submissions monthly (identify trends, systemic issues)
- [ ] Increase rewards for high-quality reports (encourage detailed PoCs)
- [ ] Expand scope as product evolves (add iOS app when launched)
- [ ] Promote program (blog posts, tweets, security conferences)

***REMOVED******REMOVED*** Verification Steps
\`\`\`bash
***REMOVED*** Verify security.txt exists
curl https://api.scanium.com/.well-known/security.txt

***REMOVED*** Expected output:
***REMOVED*** Contact: mailto:security@scanium.com
***REMOVED*** Expires: 2026-12-31T23:59:59z
***REMOVED*** Preferred-Languages: en
***REMOVED*** Canonical: https://api.scanium.com/.well-known/security.txt
***REMOVED*** Policy: https://scanium.com/security/disclosure
***REMOVED*** Acknowledgments: https://scanium.com/security/thanks

***REMOVED*** Validate security.txt
curl https://api.scanium.com/.well-known/security.txt | gpg --verify
***REMOVED*** Expected: Valid signature (if PGP signed)

***REMOVED*** Check HackerOne program
curl https://hackerone.com/scanium
***REMOVED*** Expected: Program page with scope, rewards, submissions

***REMOVED*** Test vulnerability submission flow
***REMOVED*** 1. Researcher submits critical bug (auth bypass)
***REMOVED*** 2. Triage team responds within 24h: \"Thank you, confirmed, investigating\"
***REMOVED*** 3. Engineering team fixes bug within 7 days
***REMOVED*** 4. Researcher verifies fix
***REMOVED*** 5. Reward issued: \$1000
***REMOVED*** 6. 90 days later: Public disclosure with credit to researcher

***REMOVED*** View security hall of fame
curl https://scanium.com/security/thanks
***REMOVED*** Expected: List of researchers who contributed (with permission)
\`\`\`"

echo ""
echo "✅ Phase 2 (Enterprise-Ready) issues created!"
echo ""
echo "Summary:"
echo "- P2 severity:high: 2 issues (penetration testing, security audit)"
echo "- P2 severity:medium: 6 issues (load testing, DR drills, chaos engineering, blue-green, DB optimization, mobile telemetry, synthetic monitoring)"
echo "- P2 severity:low: 3 issues (error budget, CDN, bug bounty)"
echo ""
echo "Total Phase 2: 11 issues"
echo "Grand Total (Phase 1 + Phase 2): 31 issues"
echo ""
echo "View Phase 2 issues: gh issue list --label priority:p2"
echo "View all issues: gh issue list"
