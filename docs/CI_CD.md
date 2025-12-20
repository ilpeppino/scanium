***REMOVED*** CI / CD

***REMOVED******REMOVED*** Workflows
- **Android Debug APK** (`.github/workflows/android-debug-apk.yml`)
  - Triggers: push to `main`, manual dispatch.
  - Steps: checkout, JDK 17 + Gradle setup, `./gradlew clean assembleDebug`, upload `scanium-app-debug-apk` artifact from `*/build/outputs/apk/**`.
  - Use this for device testing when local SDKs are unavailable.
- **Security - CVE Scanning** (`.github/workflows/security-cve-scan.yml`) **[CRITICAL - DO NOT DISABLE]**
  - Triggers: PRs touching `androidApp/build.gradle.kts`, `build.gradle.kts` (AGP versions), `gradle/libs.versions.toml`, pushes to `main/master`, weekly cron, manual dispatch.
  - Runs OWASP Dependency-Check with SARIF upload and HTML artifact; continues on error.
  - **Maintenance**: When updating AGP (in `build.gradle.kts`), verify OWASP Dependency-Check plugin compatibility in `androidApp/build.gradle.kts`. Test with `./gradlew dependencyCheckAnalyze` before merging.
- **Code Coverage** (`.github/workflows/coverage.yml`)
  - Triggers: PRs to `main`, pushes to `main`, manual dispatch.
  - Steps: checkout, JDK 17 + Gradle setup, `./gradlew clean test koverVerify`, `./gradlew jacocoTestReport`.
  - Publishes coverage artifacts: `*/build/reports/kover/html` (shared modules) + `androidApp/build/reports/jacoco/testDebugUnitTest/html`.
  - Enforces thresholds: shared modules ≥85%, androidApp ≥75% (configured in respective `build.gradle.kts`).

***REMOVED******REMOVED*** Mobile testing via artifact
1. Push your branch or use workflow_dispatch on **Android Debug APK**.
2. Download `scanium-app-debug-apk` from the workflow run artifacts.
3. Install on device/emulator (enable unknown sources if needed) and verify the scenario.

***REMOVED******REMOVED*** Runners / environments
- Current workflows run on `ubuntu-latest` GitHub-hosted runners; no self-hosted runners configured.
- Keep CI green by ensuring Gradle wrapper is executable and Java 17-compatible.
