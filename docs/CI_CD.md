# CI / CD

## Workflows
- **Android Debug APK** (`.github/workflows/android-debug-apk.yml`)
  - Triggers: push to `main`, manual dispatch.
  - Steps: checkout, JDK 17 + Gradle setup, `./gradlew clean assembleDebug`, upload `scanium-app-debug-apk` artifact from `*/build/outputs/apk/**`.
  - Use this for device testing when local SDKs are unavailable.
- **Security - CVE Scanning** (`.github/workflows/security-cve-scan.yml`)
  - Triggers: PRs touching Gradle files, pushes to `main/master`, weekly cron, manual dispatch.
  - Runs OWASP Dependency-Check with SARIF upload and HTML artifact; continues on error.
- **(Planned) Coverage**: add `./gradlew koverVerify` and publish `*/build/reports/kover/html` + `androidApp/build/reports/jacoco/testDebugUnitTest/html` artifacts once coverage execution is enabled on CI runners.

## Mobile testing via artifact
1. Push your branch or use workflow_dispatch on **Android Debug APK**.
2. Download `scanium-app-debug-apk` from the workflow run artifacts.
3. Install on device/emulator (enable unknown sources if needed) and verify the scenario.

## Runners / environments
- Current workflows run on `ubuntu-latest` GitHub-hosted runners; no self-hosted runners configured.
- Keep CI green by ensuring Gradle wrapper is executable and Java 17-compatible.
