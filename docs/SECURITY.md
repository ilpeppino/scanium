# Security

## Current posture
- Core scanning, tracking, and selling flows run fully on-device; mock marketplace avoids real network calls.
- Optional cloud classification client exists but is disabled until credentials/endpoints are provided; ensure secrets are injected via config, not source.
- Dependency security is covered by the `Security - CVE Scanning` workflow (OWASP Dependency-Check with SARIF upload).

## Prioritized follow-ups
- TODO/VERIFY: review archived security assessment findings in `docs/_archive/2025-12/security/` and open issues for still-relevant items.
- TODO/VERIFY: document the expected configuration surface (API base URL/keys) before enabling `CloudClassifier`.
- Ensure no secrets are committed; prefer GitHub Actions secrets or local gradle properties for credentials.

## References
- `.github/workflows/security-cve-scan.yml` for automated dependency scanning.
- `androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifier.kt` for cloud classification entry point.
