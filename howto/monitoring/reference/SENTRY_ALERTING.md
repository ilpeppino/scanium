# Sentry Alerting Configuration

This document covers Sentry project configuration for crash and error monitoring in Scanium.

## Overview

Sentry handles crash reporting and error tracking separately from the LGTM observability stack. This
separation is intentional:

- **Sentry excels at:** Crash symbolication, stack grouping, issue deduplication, release tracking
- **LGTM excels at:** Metrics, logs, traces, dashboards, custom business alerts

## Release Naming Scheme

Scanium uses a consistent release identifier format across all environments:

```
com.scanium.app@{VERSION_NAME}+{VERSION_CODE}
```

**Examples:**

- `com.scanium.app@1.0.42+42` (production build 42)
- `com.scanium.app@1.0.0+1` (local development)

This format
follows [Sentry's recommended release naming](https://docs.sentry.io/platforms/android/configuration/releases/)
and is set automatically in the app initialization.

### Version Components

| Component      | Source                                               | Example  |
|----------------|------------------------------------------------------|----------|
| `VERSION_NAME` | `SCANIUM_VERSION_NAME` env var or `local.properties` | `1.0.42` |
| `VERSION_CODE` | `SCANIUM_VERSION_CODE` env var or `local.properties` | `42`     |

In CI, versions are set by GitHub Actions (see `.github/workflows/android-release-bundle.yml`):

```yaml
SCANIUM_VERSION_CODE: ${{ github.run_number }}
SCANIUM_VERSION_NAME: "1.0.${{ github.run_number }}"
```

## Environment Configuration

Two environments are used:

| Environment | When                        | Characteristics            |
|-------------|-----------------------------|----------------------------|
| `dev`       | `BuildConfig.DEBUG = true`  | Local builds, debug APKs   |
| `prod`      | `BuildConfig.DEBUG = false` | Release builds, Play Store |

Environment is set automatically based on build type.

## Tags Reference

The app sets these Sentry tags at initialization for filtering and grouping:

| Tag                   | Description                  | Example Values             |
|-----------------------|------------------------------|----------------------------|
| `platform`            | Platform identifier          | `android`                  |
| `app_version`         | Version name                 | `1.0.42`                   |
| `build`               | Version code                 | `42`                       |
| `env`                 | Environment                  | `dev`, `prod`              |
| `build_type`          | Build variant                | `debug`, `release`         |
| `session_id`          | Classification session ID    | `cls-550e8400-...`         |
| `scan_mode`           | Current classification mode  | `LOCAL`, `CLOUD`, `HYBRID` |
| `cloud_allowed`       | Cloud classification enabled | `true`, `false`            |
| `domain_pack_version` | Domain model version         | `unknown` (placeholder)    |

### Dynamic Tag Updates

These tags are updated in real-time as user preferences change:

- `scan_mode` / `cloud_mode` - Updated when classification mode changes
- `cloud_allowed` - Updated when cloud classification permission changes
- `session_id` - Set at startup; new sessions create new IDs

## DSN Security (SEC-002)

### Understanding DSN Exposure

Sentry DSNs are intentionally "semi-public" by design. The DSN is embedded in the client
application (APK) and can be extracted, but this is expected behavior:

- **DSN identifies the project** - It tells Sentry where to send events
- **DSN does not grant read access** - Attackers cannot view existing crash data
- **Risk: Event pollution** - Attackers can send fake crash reports to pollute analytics

Reference: [Sentry DSN Explainer](https://docs.sentry.io/concepts/key-concepts/dsn-explainer/)

### Required Mitigations

Configure these protections in Sentry to prevent abuse:

#### 1. Rate Limiting (Quota Management)

Limit events per period to prevent spam attacks:

1. Go to **Settings → Subscription → Quota**
2. Configure rate limits:
    - **Per-project limit:** 10,000 events/hour (adjust based on user base)
    - **Spike protection:** Enable to prevent sudden event floods
    - **Per-key limit:** Consider separate DSNs for debug/release with different quotas

**Recommended thresholds:**

| User Base  | Events/Hour | Events/Day |
|------------|-------------|------------|
| < 1K DAU   | 1,000       | 10,000     |
| 1K-10K DAU | 10,000      | 100,000    |
| > 10K DAU  | 50,000      | 500,000    |

#### 2. Inbound Filters

Block known bad actors and suspicious traffic:

1. Go to **Settings → Processing → Inbound Filters**
2. Enable:
    - [ ] **Filter localhost events** (blocks development pollution)
    - [ ] **Filter known bots/crawlers**
    - [ ] **Filter by release** (block events from unknown versions)

3. Add custom IP filters if abuse patterns detected:
    - Go to **Settings → Processing → Inbound Data Filters**
    - Add suspicious IP ranges to blocklist

#### 3. DSN Rotation Schedule

Rotate the DSN monthly to invalidate any leaked or abused keys:

**Rotation Procedure:**

1. **Generate new DSN:**
    - Go to **Settings → Client Keys (DSN)**
    - Click "Generate New Key"

2. **Update configuration:**
    - Update `SCANIUM_SENTRY_DSN` in CI/CD secrets
    - Update `local.properties` for local development

3. **Deploy new app version:**
    - Release app update with new DSN
    - Allow 30-day grace period for user updates

4. **Disable old DSN:**
    - After grace period, disable old key in Sentry
    - Monitor for any legitimate traffic on old key

**Rotation Calendar:**

| Month | Action                              |
|-------|-------------------------------------|
| 1st   | Generate new DSN, deploy app update |
| 15th  | Monitor adoption of new version     |
| 30th  | Disable old DSN if adoption > 90%   |

#### 4. Monitoring for Abuse

Set up alerts to detect DSN abuse:

```yaml
Name: Suspicious Event Volume
Environment: prod

Metric: count()
Trigger: When count() increases by 500% in 1 hour

Actions:
  - Slack notification to #security-alerts
  - Review event sources and consider DSN rotation
```

**Signs of abuse:**

- Sudden spike in events from unknown releases
- Events with invalid/garbage data
- Events from unexpected geographic regions
- Duplicate events with identical timestamps

### Security Audit Checklist

Review monthly:

- [ ] Rate limits are configured and effective
- [ ] Inbound filters are blocking unwanted traffic
- [ ] DSN rotation is on schedule
- [ ] No unusual event patterns in dashboard
- [ ] PII scrubbing is enabled and tested

---

## Sentry Project Settings Checklist

Configure these settings in Sentry UI (Project Settings):

### General Settings

- [ ] **Project Name:** `scanium-android`
- [ ] **Platform:** Android
- [ ] **Default Environment:** `prod`
- [ ] **DSN:** Stored in `SCANIUM_SENTRY_DSN` (never commit to repo)

### Issue Grouping

- [ ] Enable **Fingerprint Rules** if needed for custom grouping
- [ ] Review **Stack Trace Rules** for obfuscated code (ProGuard/R8)
- [ ] Upload **ProGuard mapping files** for each release (see Release Workflow)

### Data Scrubbing

- [ ] Enable **Data Scrubbing** for PII protection
- [ ] Add custom scrubbing rules for any sensitive fields
- [ ] Review default scrubbing (IP addresses, credentials)

### Inbound Filters

Recommended filters to reduce noise:

- [ ] **Filter localhost events** (optional, for dev)
- [ ] **Filter legacy browsers** (N/A for mobile)
- [ ] **Filter known web crawlers** (N/A for mobile)

## Alert Rules Configuration

### Alert Rule 1: Crash Spike

Detect sudden increases in crash rate.

**Create in Sentry UI:** Alerts → Create Alert Rule → Issue Alert

```yaml
Name: Crash Spike Alert
Environment: prod

Conditions (When):
  - Number of events is more than 10 in 10 minutes
  - Issue category is Error

Filters (If):
  - Event level is fatal OR error

Actions (Then):
  - Send notification to: #scanium-alerts (Slack)
  - Send email to: scanium@gtemp1.com

Frequency: Alert once per 30 minutes per issue
```

**Threshold guidance:**

| User Base    | Threshold                 | Time Window |
|--------------|---------------------------|-------------|
| < 1K DAU     | > 5 crashes               | 10 min      |
| 1K-10K DAU   | > 10 crashes              | 10 min      |
| 10K-100K DAU | > 50 crashes              | 10 min      |
| > 100K DAU   | > 0.1% session crash rate | 10 min      |

### Alert Rule 2: New Issue (Regressions)

Catch new errors introduced in a release.

```yaml
Name: New Issue Alert
Environment: prod

Conditions (When):
  - A new issue is created

Filters (If):
  - Issue is unassigned
  - Issue level is error or fatal
  - Issue has happened at least 3 times

Actions (Then):
  - Send notification to: #scanium-alerts (Slack)

Frequency: Alert immediately
```

### Alert Rule 3: Regression Detected

Alert when a resolved issue reappears.

```yaml
Name: Regression Alert
Environment: prod

Conditions (When):
  - An issue changes state from resolved to unresolved

Filters (If):
  - Issue category is Error

Actions (Then):
  - Send notification to: #scanium-alerts (Slack)
  - Assign to: original resolver (if available)

Frequency: Alert immediately
```

### Alert Rule 4: High Error Rate (Metric Alert)

Use Sentry's metric alerts for rate-based detection.

**Create in Sentry UI:** Alerts → Create Alert Rule → Metric Alert

```yaml
Name: High Error Rate
Environment: prod

Metric: count()
Grouped by: None
Filter: event.type:error

Trigger:
  - Critical: When count() is above 100 in 10 minutes
  - Warning: When count() is above 50 in 10 minutes

Actions:
  - Critical: Page on-call + Slack
  - Warning: Slack only
```

## Issue Ownership & Routing

### Code Owners (Optional)

Define ownership rules in Sentry to auto-assign issues:

```
# Sentry Ownership Rules (Project Settings → Issue Owners)

# ML/Classification issues
path:**/classification/* team:ml
path:**/inference/* team:ml
tags.scan_mode:CLOUD team:ml

# Settings/Preferences
path:**/settings/* team:android
path:**/preferences/* team:android

# Networking
path:**/network/* team:backend
path:**/api/* team:backend
```

### Routing by Tags

Use tag-based routing for targeted alerts:

- `env:prod` → High priority, immediate notification
- `env:dev` → Low priority, daily digest only
- `scan_mode:CLOUD` → Include ML team
- `platform:android` → Android team

## Integration Setup

### Slack Integration

1. Go to Settings → Integrations → Slack
2. Install Sentry app to workspace
3. Configure default channel: `#scanium-alerts`
4. Enable issue link unfurling

### GitHub Integration

1. Go to Settings → Integrations → GitHub
2. Connect repository: `ilpeppino/scanium`
3. Enable:
    - [ ] Stack trace linking
    - [ ] Suspect commits
    - [ ] Issue sync

### PagerDuty (Production)

1. Go to Settings → Integrations → PagerDuty
2. Create service for Scanium
3. Use in Critical alert actions

## Release Workflow

### Automatic Release Creation

Sentry automatically creates releases when the app reports crashes with a release version.

### ProGuard/R8 Mapping Upload

For readable stack traces in release builds, upload mapping files:

**In CI/CD (GitHub Actions):**

```yaml
# Add to android-release-bundle.yml
- name: Upload Sentry mapping
  env:
    SENTRY_AUTH_TOKEN: ${{ secrets.SENTRY_AUTH_TOKEN }}
  run: |
    sentry-cli releases new com.scanium.app@${{ env.VERSION }}+${{ github.run_number }}
    sentry-cli releases files com.scanium.app@${{ env.VERSION }}+${{ github.run_number }} \
      upload-mappings --android-manifest androidApp/src/main/AndroidManifest.xml \
      androidApp/build/outputs/mapping/release/mapping.txt
    sentry-cli releases finalize com.scanium.app@${{ env.VERSION }}+${{ github.run_number }}
```

**Manually:**

```bash
# Install sentry-cli
npm install -g @sentry/cli

# Authenticate
sentry-cli login

# Upload mapping
sentry-cli releases --org=your-org --project=scanium-android \
  files "com.scanium.app@1.0.42+42" \
  upload-mappings --android-manifest androidApp/src/main/AndroidManifest.xml \
  androidApp/build/outputs/mapping/release/mapping.txt
```

## Testing Crash Reporting

### Debug Build Test

In debug builds, use Developer Settings → "Test Crash Reporting":

1. Enable **Share Diagnostics** in Settings → Privacy & Data
2. Go to Settings → Developer → Test Crash Reporting
3. Tap "Crash Now" to trigger test crash
4. Verify crash appears in Sentry with tag `crash_test: true`

### Programmatic Test

```kotlin
// Only in debug builds
if (BuildConfig.DEBUG) {
    Sentry.captureMessage("Test message from Scanium")
    // Or trigger exception
    throw RuntimeException("Test crash - intentional")
}
```

## Diagnostics Bundle Attachment

Every crash includes a `diagnostics.json` attachment with:

```json
{
  "generatedAt": "2025-12-24T10:30:00Z",
  "context": {
    "platform": "android",
    "app_version": "1.0.42",
    "build": "42",
    "env": "prod",
    "session_id": "cls-550e8400-..."
  },
  "events": [
    {
      "name": "scan.started",
      "severity": "INFO",
      "timestamp": "2025-12-24T10:29:55Z",
      "attributes": { "scan_mode": "LOCAL" }
    }
  ]
}
```

This provides crash context even when breadcrumbs are insufficient.

## User Consent

Crash reporting respects user consent:

- Default: **Disabled** (no crashes sent)
- Enable: Settings → Privacy & Data → Share Diagnostics

The app filters events in `beforeSend` based on user preference, ensuring GDPR/privacy compliance.

## See Also

- [TRIAGE_RUNBOOK.md](./TRIAGE_RUNBOOK.md) - How to investigate issues using Sentry + Grafana
- [monitoring/README.md](../../monitoring/README.md) - LGTM stack setup and Grafana alerts
- [docs/telemetry/DIAGNOSTICS.md](../telemetry/DIAGNOSTICS.md) - Diagnostics bundle architecture
