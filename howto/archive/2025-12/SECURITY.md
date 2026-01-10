> Archived on 2025-12-20: superseded by docs/INDEX.md.
# Security

## Current posture (Updated 2025-12-20)
- Core scanning, tracking, and selling flows run fully on-device; mock marketplace avoids real network calls.
- Optional cloud classification client exists but is disabled until credentials/endpoints are provided; ensure secrets are injected via config, not source.
- Dependency security is covered by the `Security - CVE Scanning` workflow (OWASP Dependency-Check with SARIF upload).
  - **CRITICAL**: This workflow must remain enabled at all times.
  - Plugin configured in `androidApp/build.gradle.kts` (line 16).
  - When updating Gradle/AGP versions, verify plugin compatibility and test with `./gradlew dependencyCheckAnalyze`.
- **Backend OAuth tokens stored in plaintext** in PostgreSQL (SEC-001 - CRITICAL)
- **Session secrets loaded from environment variables** without validation (SEC-002 - CRITICAL)
- **API keys embedded in Android BuildConfig** extractable from APK (SEC-007 - HIGH)

## Security Strengths ✅
- Network security config enforces HTTPS-only (cleartext blocked except localhost)
- ProGuard/R8 code obfuscation and logging removal in release builds
- SBOM generation (CycloneDX) for supply chain security
- Automated CVE scanning with GitHub Security integration
- Rate limiting and concurrency controls on backend API
- EXIF metadata stripping from uploaded images
- No image persistence by default (privacy-first)

## Critical Security Findings (From Comprehensive Audit 2025-12-20)

### CRITICAL Priority (Fix Immediately)
1. **SEC-001:** Database tokens stored unencrypted (`backend/prisma/schema.prisma:40-41`)
   - OAuth tokens in plaintext expose user accounts if database compromised
   - Action: Implement encryption at rest (pgcrypto or application-level)

2. **SEC-002:** Session secret in environment variable (`backend/.env.example:59`)
   - Weak default value enables session forgery
   - Action: Generate strong secrets, add validation, use secrets manager

### HIGH Priority (Fix This Week)
3. **SEC-003:** API keys in headers without explicit TLS verification
4. **SEC-004:** File upload size validated after buffer read (DoS risk)
5. **SEC-005:** Insufficient rate limiting granularity
6. **SEC-006:** Missing CORS origin validation
7. **SEC-007:** Android BuildConfig exposes API keys in APK

See [COMPREHENSIVE_AUDIT_REPORT](./COMPREHENSIVE_AUDIT_REPORT.md) for complete details and remediation steps.

## Prioritized follow-ups
- **URGENT:** Fix SEC-001 and SEC-002 before production deployment
- **This Week:** Address all HIGH priority security findings (SEC-003 to SEC-007)
- **This Month:** Implement comprehensive security headers, CSRF protection, request logging
- Review archived security assessment findings in `docs/_archive/2025-12/security/` and open issues for still-relevant items.
- Document the expected configuration surface (API base URL/keys) before enabling `CloudClassifier`.
- Ensure no secrets are committed; prefer GitHub Actions secrets or local gradle properties for credentials.
- Add security testing to CI/CD (SAST, DAST, penetration testing)

## Security Checklist for Production

### Authentication & Authorization
- [ ] Encrypt OAuth tokens at rest (SEC-001)
- [ ] Implement strong session secret generation (SEC-002)
- [ ] Add token refresh mechanism
- [ ] Implement CSRF protection
- [ ] Add request signing for API calls

### Network Security
- [ ] Move API keys from BuildConfig to encrypted storage (SEC-007)
- [ ] Implement certificate pinning
- [ ] Enforce SSL/TLS for database connections
- [ ] Validate CORS origins strictly (SEC-006)
- [ ] Add security headers (CSP, X-Frame-Options, etc.)

### Input Validation
- [ ] Validate file size before buffering (SEC-004)
- [ ] Implement comprehensive input sanitization
- [ ] Add SQL injection prevention tests
- [ ] Validate all user inputs client and server-side

### Rate Limiting & DDoS Protection
- [ ] Add per-IP rate limiting (SEC-005)
- [ ] Implement exponential backoff
- [ ] Add distributed rate limiting with Redis
- [ ] Monitor and alert on suspicious patterns

### Monitoring & Logging
- [ ] Implement security event logging (SEC-013)
- [ ] Set up centralized log aggregation
- [ ] Add alerting for security events
- [ ] Create security incident runbooks

### Code Security
- [ ] Refine ProGuard rules for better obfuscation (SEC-008)
- [ ] Remove all debug logging in production
- [ ] Implement runtime application self-protection (RASP)
- [ ] Add code signing verification

## References
- [COMPREHENSIVE_AUDIT_REPORT](./COMPREHENSIVE_AUDIT_REPORT.md) - Complete security audit with 19 security findings
- `.github/workflows/security-cve-scan.yml` for automated dependency scanning (CRITICAL - do not disable)
- `androidApp/build.gradle.kts` for OWASP Dependency-Check plugin configuration
- `build.gradle.kts` for AGP version (must remain compatible with Dependency-Check plugin)
- `androidApp/src/main/res/xml/network_security_config.xml` for Android network security
- `androidApp/proguard-rules.pro` for code obfuscation configuration
- `backend/src/app.ts` for backend security middleware

## Security Contact
[TODO: Add security contact email for vulnerability reports]

## Vulnerability Disclosure Policy
[TODO: Add responsible disclosure policy]
