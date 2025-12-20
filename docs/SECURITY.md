***REMOVED*** Security

***REMOVED******REMOVED*** Current posture
- Core scanning, tracking, and selling flows run fully on-device; mock marketplace avoids real network calls.
- Optional cloud classification client exists but is disabled until credentials/endpoints are provided; ensure secrets are injected via config, not source.
- Dependency security is covered by the `Security - CVE Scanning` workflow (OWASP Dependency-Check with SARIF upload).
  - **CRITICAL**: This workflow must remain enabled at all times.
  - Plugin configured in `androidApp/build.gradle.kts` (line 16).
  - When updating Gradle/AGP versions, verify plugin compatibility and test with `./gradlew dependencyCheckAnalyze`.

***REMOVED******REMOVED*** Prioritized follow-ups
- TODO/VERIFY: review archived security assessment findings in `docs/_archive/2025-12/security/` and open issues for still-relevant items.
- TODO/VERIFY: document the expected configuration surface (API base URL/keys) before enabling `CloudClassifier`.
- Ensure no secrets are committed; prefer GitHub Actions secrets or local gradle properties for credentials.

***REMOVED******REMOVED*** References
- `.github/workflows/security-cve-scan.yml` for automated dependency scanning (CRITICAL - do not disable).
- `androidApp/build.gradle.kts` for OWASP Dependency-Check plugin configuration.
- `build.gradle.kts` for AGP version (must remain compatible with Dependency-Check plugin).
- `androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifier.kt` for cloud classification entry point.
