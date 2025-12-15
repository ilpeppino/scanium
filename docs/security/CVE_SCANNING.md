***REMOVED*** CVE Scanning & Vulnerability Management (SEC-003)

**Security Finding:** SEC-003 - No Automated CVE Scanning
**Priority:** P1 (High)
**OWASP Mapping:** M2 (Inadequate Supply Chain Security)
**MASVS Control:** MASVS-CODE-3
**Status:** ✅ IMPLEMENTED (2025-12-15)

---

***REMOVED******REMOVED*** Overview

This document describes the automated CVE (Common Vulnerabilities and Exposures) scanning system implemented in Scanium to detect and prevent vulnerable dependencies from being deployed.

***REMOVED******REMOVED******REMOVED*** Security Mechanisms

1. **OWASP Dependency-Check** - Gradle plugin scanning against NVD database
2. **GitHub Actions CI/CD** - Automated scanning on every PR and weekly
3. **SARIF Integration** - Results uploaded to GitHub Security tab
4. **Build Failure Gates** - Automatic rejection of HIGH/CRITICAL vulnerabilities

---

***REMOVED******REMOVED*** 1. OWASP Dependency-Check

***REMOVED******REMOVED******REMOVED*** What It Does

OWASP Dependency-Check scans project dependencies against the National Vulnerability Database (NVD) to identify known security vulnerabilities. It:

- Downloads CVE data from NVD (updated continuously)
- Analyzes all dependencies (direct + transitive)
- Assigns CVSS scores to vulnerabilities
- Generates HTML, JSON, and SARIF reports
- Can fail builds if vulnerabilities exceed threshold

***REMOVED******REMOVED******REMOVED*** Configuration

Added to `app/build.gradle.kts`:

```kotlin
plugins {
    id("org.owasp.dependencycheck") version "10.0.4"
}

dependencyCheck {
    // Report formats
    formats = listOf("HTML", "JSON", "SARIF")

    // Fail build on CVSS >= 7.0 (HIGH severity)
    failBuildOnCVSS = 7.0f

    // Suppress false positives
    suppressionFile = file("dependency-check-suppressions.xml")
        .takeIf { it.exists() }?.absolutePath

    // Analyzer settings
    analyzers.apply {
        experimentalEnabled = false
        archiveEnabled = false  // Not needed for Android
        assemblyEnabled = false
        nuspecEnabled = false
    }

    // NVD API key (optional, faster downloads)
    nvd.apiKey = System.getenv("DEPENDENCY_CHECK_NVD_API_KEY") ?: ""

    // Cache directory
    data.directory = "${project.buildDir}/dependency-check-data"
}
```

---

***REMOVED******REMOVED*** 2. Running CVE Scans

***REMOVED******REMOVED******REMOVED*** Local Scans

***REMOVED******REMOVED******REMOVED******REMOVED*** Basic Scan

```bash
***REMOVED*** Run dependency check
./gradlew dependencyCheckAnalyze

***REMOVED*** View report
open app/build/reports/dependency-check-report.html
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Advanced Options

```bash
***REMOVED*** Scan with verbose output
./gradlew dependencyCheckAnalyze --info

***REMOVED*** Update NVD data only (no scan)
./gradlew dependencyCheckUpdate

***REMOVED*** Purge cached NVD data
./gradlew dependencyCheckPurge

***REMOVED*** With NVD API key (faster)
DEPENDENCY_CHECK_NVD_API_KEY=your-key-here ./gradlew dependencyCheckAnalyze
```

***REMOVED******REMOVED******REMOVED*** CI/CD Scans (Automated)

**GitHub Actions Workflow:** `.github/workflows/security-cve-scan.yml`

**Triggers:**
- ✅ Every pull request (when dependencies change)
- ✅ Push to main/master branch
- ✅ Weekly schedule (Monday 2am UTC)
- ✅ Manual trigger via GitHub UI

**What It Does:**
1. Runs OWASP Dependency-Check
2. Uploads SARIF results to GitHub Security tab
3. Uploads HTML/JSON reports as artifacts
4. Comments on PR with vulnerability summary
5. **Fails build** if HIGH/CRITICAL vulnerabilities found

---

***REMOVED******REMOVED*** 3. Understanding CVE Reports

***REMOVED******REMOVED******REMOVED*** Report Locations

After running `./gradlew dependencyCheckAnalyze`:

```
app/build/reports/
├── dependency-check-report.html    ***REMOVED*** Human-readable report
├── dependency-check-report.json    ***REMOVED*** Machine-readable (for automation)
└── dependency-check-report.sarif   ***REMOVED*** GitHub Security integration
```

***REMOVED******REMOVED******REMOVED*** Report Structure

**HTML Report Sections:**
1. **Summary:** Total dependencies scanned, vulnerabilities found
2. **Dependency Details:** Each dependency with CVE list
3. **Vulnerability Details:** CVE ID, CVSS score, description, references

**Example Vulnerability Entry:**

```
Dependency: com.squareup.okhttp3:okhttp:4.12.0
CVE: CVE-2024-XXXXX
CVSS Score: 7.5 (HIGH)
Description: HTTP Request Smuggling vulnerability allows...
References: https://nvd.nist.gov/vuln/detail/CVE-2024-XXXXX
Fix: Upgrade to okhttp:4.12.1 or later
```

***REMOVED******REMOVED******REMOVED*** CVSS Severity Levels

| Score Range | Severity | Build Action |
|-------------|----------|--------------|
| 9.0 - 10.0 | **CRITICAL** | ❌ FAIL BUILD |
| 7.0 - 8.9 | **HIGH** | ❌ FAIL BUILD |
| 4.0 - 6.9 | **MEDIUM** | ⚠️ WARN (allow) |
| 0.1 - 3.9 | **LOW** | ✅ PASS |

**Current Threshold:** `failBuildOnCVSS = 7.0f` (HIGH or above fails build)

---

***REMOVED******REMOVED*** 4. Handling Vulnerabilities

***REMOVED******REMOVED******REMOVED*** Workflow

1. **CVE Detected** → Build fails or PR comment appears
2. **Assess Impact** → Read CVE description and determine if exploitable
3. **Fix or Suppress** → Upgrade dependency OR suppress false positive
4. **Verify** → Re-run scan to confirm resolution

***REMOVED******REMOVED******REMOVED*** Fixing Vulnerabilities

***REMOVED******REMOVED******REMOVED******REMOVED*** Step 1: Identify Vulnerable Dependency

```bash
***REMOVED*** Run scan
./gradlew dependencyCheckAnalyze

***REMOVED*** Open report
open app/build/reports/dependency-check-report.html

***REMOVED*** Find: Dependency name, current version, fixed version
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Step 2: Upgrade Dependency

```kotlin
// Before (vulnerable)
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// After (fixed)
implementation("com.squareup.okhttp3:okhttp:4.12.1")
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Step 3: Update Verification Metadata

```bash
***REMOVED*** Regenerate checksums for new version
./gradlew --write-verification-metadata sha256 help

***REMOVED*** Commit updated metadata
git add gradle/verification-metadata.xml
git commit -m "security: upgrade okhttp to fix CVE-2024-XXXXX"
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Step 4: Verify Fix

```bash
***REMOVED*** Re-run scan
./gradlew dependencyCheckAnalyze

***REMOVED*** Confirm CVE is gone
open app/build/reports/dependency-check-report.html
```

***REMOVED******REMOVED******REMOVED*** Suppressing False Positives

Sometimes Dependency-Check reports CVEs that don't apply to your usage. You can suppress these.

***REMOVED******REMOVED******REMOVED******REMOVED*** Create Suppression File

Create `app/dependency-check-suppressions.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
    <!--
        Suppress CVE-2024-XXXXX for okhttp:4.12.0
        Rationale: We only use OkHttp for internal API calls over HTTPS with certificate pinning.
                   The vulnerability only affects HTTP (cleartext) connections which we never use.
        Reviewed: 2025-12-15
        Expires: 2026-01-15 (re-evaluate in 1 month)
    -->
    <suppress>
        <notes>HTTP Request Smuggling - not applicable (HTTPS only)</notes>
        <packageUrl regex="true">^pkg:maven/com\.squareup\.okhttp3/okhttp@4\.12\.0.*$</packageUrl>
        <cve>CVE-2024-XXXXX</cve>
    </suppress>
</suppressions>
```

**⚠️ Suppression Guidelines:**
- Always document **rationale** (why it's safe to suppress)
- Always document **reviewer** and **review date**
- Set **expiration** date (force re-review)
- Get security team approval for CRITICAL/HIGH suppressions

---

***REMOVED******REMOVED*** 5. GitHub Security Integration

***REMOVED******REMOVED******REMOVED*** Security Tab

CVE scan results are automatically uploaded to GitHub's Security tab via SARIF format.

**View Results:**
1. Go to repository → **Security** tab
2. Click **Dependabot alerts** or **Code scanning alerts**
3. View vulnerability details, affected files, remediation advice

**Benefits:**
- ✅ Centralized vulnerability tracking
- ✅ Automatic PRs from Dependabot (if enabled)
- ✅ Security advisories visible to team
- ✅ Integration with GitHub Projects

***REMOVED******REMOVED******REMOVED*** Dependabot (Optional Enhancement)

Enable Dependabot for automatic dependency updates:

**Create `.github/dependabot.yml`:**

```yaml
version: 2
updates:
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 5
    labels:
      - "dependencies"
      - "security"
```

Dependabot will:
- Automatically detect outdated/vulnerable dependencies
- Create PRs with version upgrades
- Include changelogs and vulnerability info

---

***REMOVED******REMOVED*** 6. CI/CD Integration Details

***REMOVED******REMOVED******REMOVED*** GitHub Actions Workflow

**File:** `.github/workflows/security-cve-scan.yml`

**Key Features:**

1. **Caching:** NVD data cached to speed up scans (30+ minute download → seconds)
2. **SARIF Upload:** Results visible in GitHub Security tab
3. **Artifact Storage:** HTML/JSON reports stored for 30 days
4. **PR Comments:** Automatic summary comment with vulnerability counts
5. **Build Gates:** Fails PR if HIGH/CRITICAL CVEs found

***REMOVED******REMOVED******REMOVED*** Workflow Triggers

```yaml
on:
  pull_request:  ***REMOVED*** On every PR touching dependencies
  push:          ***REMOVED*** On push to main branch
  schedule:      ***REMOVED*** Weekly (Monday 2am UTC)
  workflow_dispatch:  ***REMOVED*** Manual trigger
```

***REMOVED******REMOVED******REMOVED*** Workflow Steps

1. **Checkout** → Clone repository
2. **Setup Java 17** → Required for Gradle
3. **Cache NVD Data** → Speed up subsequent runs
4. **Run Dependency-Check** → Scan all dependencies
5. **Upload SARIF** → Send to GitHub Security
6. **Upload Artifacts** → Store reports
7. **Check Vulnerabilities** → Count HIGH/CRITICAL
8. **Comment on PR** → Summarize findings
9. **Fail Build** → Exit 1 if vulnerabilities found

---

***REMOVED******REMOVED*** 7. Performance Optimization

***REMOVED******REMOVED******REMOVED*** NVD API Key (Recommended)

The NVD database download can take 30+ minutes. Get a free API key to reduce this to seconds.

**Get API Key:**
1. Visit https://nvd.nist.gov/developers/request-an-api-key
2. Request API key (free, instant approval)
3. Add to GitHub Secrets: `NVD_API_KEY`

**Local Usage:**

```bash
***REMOVED*** Export API key
export DEPENDENCY_CHECK_NVD_API_KEY="your-key-here"

***REMOVED*** Run scan (much faster)
./gradlew dependencyCheckAnalyze
```

**GitHub Actions:**
Already configured in workflow - just add secret to repository.

***REMOVED******REMOVED******REMOVED*** Caching Strategy

**Local Cache:**
```
app/build/dependency-check-data/
├── odc.mv.db        ***REMOVED*** NVD database (cached)
├── nvdcve-1.1-*.json.gz  ***REMOVED*** CVE data files
```

**CI Cache:**
GitHub Actions caches `~/.gradle/dependency-check-data` across runs.

**Cache Invalidation:**
- Purge manually: `./gradlew dependencyCheckPurge`
- Auto-updates: Daily when running scans

---

***REMOVED******REMOVED*** 8. Best Practices

***REMOVED******REMOVED******REMOVED*** Development Workflow

1. **Before Adding Dependency:**
   - Check dependency age and maintenance status
   - Check for known vulnerabilities: https://snyk.io/vuln/
   - Prefer well-maintained, popular libraries

2. **After Adding Dependency:**
   ```bash
   ***REMOVED*** Run CVE scan immediately
   ./gradlew dependencyCheckAnalyze

   ***REMOVED*** Update verification metadata
   ./gradlew --write-verification-metadata sha256 help

   ***REMOVED*** Commit both changes together
   git add app/build.gradle.kts gradle/verification-metadata.xml
   git commit -m "deps: add library X (no CVEs)"
   ```

3. **On CVE Alert:**
   - Assess impact within 24 hours (HIGH/CRITICAL)
   - Fix or suppress within 1 week (HIGH/CRITICAL)
   - Fix or suppress within 1 month (MEDIUM)

***REMOVED******REMOVED******REMOVED*** Release Workflow

**Before Every Release:**

```bash
***REMOVED*** 1. Update NVD data
./gradlew dependencyCheckUpdate

***REMOVED*** 2. Run full scan
./gradlew dependencyCheckAnalyze

***REMOVED*** 3. Check report
open app/build/reports/dependency-check-report.html

***REMOVED*** 4. Fix any HIGH/CRITICAL vulnerabilities
***REMOVED*** 5. Generate SBOM
./gradlew cyclonedxBom

***REMOVED*** 6. Archive both reports with release
mkdir -p releases/v1.0/security/
cp app/build/reports/dependency-check-report.html releases/v1.0/security/
cp app/build/reports/scanium-bom.json releases/v1.0/security/
```

***REMOVED******REMOVED******REMOVED*** Team Practices

- **Security Champion:** Assign one team member to monitor CVE alerts
- **Weekly Review:** Review GitHub Security tab every Monday
- **Response SLA:**
  - CRITICAL: 24 hours
  - HIGH: 1 week
  - MEDIUM: 1 month
  - LOW: Next sprint
- **Documentation:** Document all suppressions with rationale

---

***REMOVED******REMOVED*** 9. Troubleshooting

***REMOVED******REMOVED******REMOVED*** Issue: "NVD data download takes forever"

**Cause:** No API key, slow network

**Solution:**
```bash
***REMOVED*** Get NVD API key (free)
***REMOVED*** https://nvd.nist.gov/developers/request-an-api-key

***REMOVED*** Use key
export DEPENDENCY_CHECK_NVD_API_KEY="your-key"
./gradlew dependencyCheckAnalyze
```

***REMOVED******REMOVED******REMOVED*** Issue: "False positive CVE reported"

**Cause:** Dependency-Check matches CVEs conservatively

**Solution:**
1. Verify it's actually a false positive (read CVE description)
2. Add suppression to `dependency-check-suppressions.xml`
3. Document rationale clearly
4. Set expiration date
5. Get security team approval

***REMOVED******REMOVED******REMOVED*** Issue: "Build fails on CI but passes locally"

**Cause:** Different NVD data versions, cached data

**Solution:**
```bash
***REMOVED*** Purge and re-download NVD data
./gradlew dependencyCheckPurge
./gradlew dependencyCheckAnalyze
```

***REMOVED******REMOVED******REMOVED*** Issue: "Scan is too slow (30+ minutes)"

**Solutions:**
1. **Use NVD API key** (reduces to ~2 minutes)
2. **Disable unused analyzers** (already done in config)
3. **Cache NVD data** (already configured)
4. **Run scans less frequently** (weekly instead of every PR)

---

***REMOVED******REMOVED*** 10. Alternative Tools

***REMOVED******REMOVED******REMOVED*** Snyk

**Pros:**
- Faster than OWASP Dependency-Check
- Better fix advice
- Automatic PRs

**Cons:**
- Requires account (free tier available)
- Uploads dependency data to Snyk servers

**Setup:**
1. Sign up at https://snyk.io/
2. Add `SNYK_TOKEN` to GitHub Secrets
3. Enable Snyk workflow in `.github/workflows/security-cve-scan.yml`

***REMOVED******REMOVED******REMOVED*** Grype (by Anchore)

**Pros:**
- Open source
- Works offline with SBOM
- Very fast

**Cons:**
- Requires SBOM (we have it from SEC-002!)
- Fewer features than Snyk/Dependency-Check

**Usage:**
```bash
***REMOVED*** Install Grype
brew install grype  ***REMOVED*** macOS
***REMOVED*** OR
curl -sSfL https://raw.githubusercontent.com/anchore/grype/main/install.sh | sh

***REMOVED*** Scan SBOM
./gradlew cyclonedxBom
grype sbom:app/build/reports/scanium-bom.json
```

---

***REMOVED******REMOVED*** 11. Metrics & Reporting

***REMOVED******REMOVED******REMOVED*** Key Metrics to Track

1. **Mean Time to Detect (MTTD):** Time from CVE disclosure to detection in your app
2. **Mean Time to Remediate (MTTR):** Time from detection to fix deployed
3. **Vulnerability Backlog:** Count of open HIGH/CRITICAL CVEs
4. **Dependency Age:** Average age of dependencies (older = riskier)

***REMOVED******REMOVED******REMOVED*** Monthly Security Report

Generate monthly reports:

```bash
***REMOVED*** Run scan
./gradlew dependencyCheckAnalyze

***REMOVED*** Extract metrics
jq '.dependencies | length' app/build/reports/dependency-check-report.json
***REMOVED*** → Total dependencies scanned

jq '[.dependencies[].vulnerabilities[]? | select(.severity == "HIGH" or .severity == "CRITICAL")] | length' app/build/reports/dependency-check-report.json
***REMOVED*** → High/Critical vulnerabilities

***REMOVED*** Archive report
cp app/build/reports/dependency-check-report.html reports/2025-12-cve-scan.html
```

---

***REMOVED******REMOVED*** 12. References

***REMOVED******REMOVED******REMOVED*** Documentation

- **OWASP Dependency-Check:** https://owasp.org/www-project-dependency-check/
- **NVD Database:** https://nvd.nist.gov/
- **CVSS Scoring:** https://www.first.org/cvss/
- **GitHub Security:** https://docs.github.com/en/code-security

***REMOVED******REMOVED******REMOVED*** Tools

- **Snyk:** https://snyk.io/
- **Grype:** https://github.com/anchore/grype
- **Dependabot:** https://github.com/dependabot

***REMOVED******REMOVED******REMOVED*** Scanium Security Docs

- **DEPENDENCY_SECURITY.md** - SBOM generation (SEC-002)
- **SECURITY_RISK_ASSESSMENT.md** - Comprehensive assessment
- **ASSESSMENT_SUMMARY.md** - Executive summary

---

***REMOVED******REMOVED*** 13. Implementation Summary

**Implemented:** 2025-12-15
**Implemented By:** Codex Security Team
**Branch:** `claude/fix-android-security-findings-TccHR`

***REMOVED******REMOVED******REMOVED*** Changes Made

1. ✅ Added OWASP Dependency-Check plugin v10.0.4 to `app/build.gradle.kts`
2. ✅ Configured CVE scanning (HTML/JSON/SARIF reports, CVSS threshold)
3. ✅ Created GitHub Actions workflow (`.github/workflows/security-cve-scan.yml`)
4. ✅ Configured automatic PR comments and GitHub Security integration
5. ✅ Created comprehensive documentation (this file)

***REMOVED******REMOVED******REMOVED*** Next Steps

1. **Generate NVD Data** (requires network - first run only):
   ```bash
   ./gradlew dependencyCheckAnalyze
   ```

2. **Get NVD API Key** (optional but recommended):
   - Request at: https://nvd.nist.gov/developers/request-an-api-key
   - Add to GitHub Secrets as `NVD_API_KEY`

3. **Test Workflow:**
   - Create test PR changing `app/build.gradle.kts`
   - Verify workflow runs and comments on PR
   - Check GitHub Security tab for SARIF results

4. **Establish Response Process:**
   - Assign security champion
   - Define response SLAs
   - Create escalation path

***REMOVED******REMOVED******REMOVED*** Security Impact

- ✅ Automatic detection of vulnerable dependencies
- ✅ Prevents HIGH/CRITICAL CVEs from reaching production
- ✅ GitHub Security integration for centralized tracking
- ✅ Weekly scans catch newly-disclosed vulnerabilities
- ✅ Meets OWASP MASVS MASVS-CODE-3 requirement
- ✅ Completes OWASP Mobile Top 10 M2 (Supply Chain Security)

**Risk Reduction:** HIGH → LOW for unknown/unpatched vulnerabilities

---

**Status:** ✅ IMPLEMENTED (pending first NVD data download)
**Finding:** SEC-003 - No Automated CVE Scanning
**Date:** 2025-12-15
