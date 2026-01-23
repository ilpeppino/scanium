# DEV Background Health Monitor

A DEV-flavor-only background health monitoring system that periodically checks backend endpoints and
sends local notifications when disruptions occur.

## Overview

The health monitor runs every 15 minutes in the background using Android WorkManager. It checks the
same endpoints used by ops smoke checks and notifies developers via local notifications when issues
are detected.

**Important:** This feature is only available in DEV builds. Beta and Prod builds do not include the
feature, UI, worker scheduling, or notification channel.

## Endpoints Checked

| Endpoint            | Pass Conditions (with API key) | Pass Conditions (no API key) |
|---------------------|--------------------------------|------------------------------|
| `/health`           | HTTP 200                       | HTTP 200                     |
| `/v1/config`        | HTTP 200                       | HTTP 200 or 401              |
| `/v1/preflight`     | HTTP 200                       | HTTP 200 or 401              |
| `/v1/assist/status` | HTTP 200 or 403                | HTTP 200 or 403              |

## Enabling the Monitor

### Via UI

1. Open the Scanium DEV app
2. Go to **Settings > Developer Options**
3. Find the **Background Health Monitor** section
4. Toggle **Enable monitoring** ON

### Configuration Options

- **Enable monitoring**: Master toggle for background checks
- **Notify on recovery**: Send notification when backend recovers (default: ON)
- **Base URL Override**: Custom backend URL (leave empty for default)
- **Run Now**: Trigger immediate one-off check

## Notification Behavior

### Failure Notifications

- **Title**: "Scanium backend issue"
- **Text**: Describes the failure (e.g., "health timeout", "config unauthorized (401)")

Notifications are sent:

- Immediately on OK → FAIL transition
- On signature change while failing (different endpoint or status code)
- As reminder every 6 hours while continuously failing

### Recovery Notifications (if enabled)

- **Title**: "Scanium backend recovered"
- **Text**: "All checks passing"

Sent when transitioning from FAIL → OK (if "Notify on recovery" is enabled).

## ADB Commands

### Run a One-Off Check

```bash
# Enqueue an immediate one-time check
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -n com.scanium.app.dev/.monitoring.DevHealthMonitorWorker
```

Or use the "Run Now" button in Developer Options.

### Inspect WorkManager State

```bash
# List all scheduled work
adb shell dumpsys jobscheduler | grep -A 20 "com.scanium.app.dev"

# Alternative: Use WorkManager inspector in Android Studio
```

### Clear Monitor State

```bash
# Clear all health monitor state
adb shell "run-as com.scanium.app.dev rm -rf /data/data/com.scanium.app.dev/files/datastore/dev_health_monitor.preferences_pb"
```

### View Logs

```bash
# Filter for health monitor logs
adb logcat -s DevHealthMonitor HealthCheck
```

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    DevHealthMonitorScheduler                     │
│  • enable() / disable() / reschedule() / runNow()               │
│  • Manages PeriodicWorkRequest (15min, CONNECTED constraint)    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    DevHealthMonitorWorker                        │
│  • Runs health checks via HealthCheckRepository                 │
│  • Decides notifications via NotificationDecision               │
│  • Updates state via DevHealthMonitorStateStore                 │
└─────────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌───────────────┐   ┌───────────────────┐   ┌───────────────────┐
│HealthCheck    │   │DevHealthMonitor   │   │Notification       │
│Repository     │   │StateStore         │   │Decision           │
│• OkHttp calls │   │• DataStore prefs  │   │• Pure functions   │
│• 10s timeouts │   │• Last status      │   │• Rate limiting    │
│• 4 endpoints  │   │• Config           │   │• State transitions│
└───────────────┘   └───────────────────┘   └───────────────────┘
```

## Files

| File                                       | Purpose                                                      |
|--------------------------------------------|--------------------------------------------------------------|
| `monitoring/HealthCheckModels.kt`          | Domain models (MonitorHealthStatus, HealthCheckResult, etc.) |
| `monitoring/HealthCheckRepository.kt`      | HTTP client for endpoint checks                              |
| `monitoring/DevHealthMonitorStateStore.kt` | DataStore for persistent state                               |
| `monitoring/NotificationDecision.kt`       | Pure functions for notification logic                        |
| `monitoring/DevHealthMonitorWorker.kt`     | WorkManager CoroutineWorker                                  |
| `monitoring/DevHealthMonitorScheduler.kt`  | Work scheduling management                                   |
| `ui/settings/DeveloperOptionsScreen.kt`    | UI section (HealthMonitorSection)                            |
| `ui/settings/DeveloperOptionsViewModel.kt` | ViewModel health monitor controls                            |

## Testing

Unit tests are located in `src/test/java/com/scanium/app/monitoring/`:

- `NotificationDecisionTest.kt` - Tests for notification state transitions and rate limiting
- `HealthCheckRepositoryTest.kt` - Tests for endpoint validation rules using MockWebServer

Run tests:

```bash
./gradlew :androidApp:testDevDebugUnitTest --tests "com.scanium.app.monitoring.*"
```

## Flavor Isolation

The feature uses runtime guards via `FeatureFlags.isDevBuild`:

- Worker checks `FeatureFlags.isDevBuild` before executing
- Scheduler checks before scheduling work
- UI is only visible in Developer Options (already DEV-only screen)

Beta/Prod builds:

- Include the code (to avoid complex source set management)
- Worker immediately returns `Result.success()` without executing
- No work is ever scheduled
- Notification channel is never created

This approach ensures:

1. Simple codebase with single source set
2. R8 can potentially strip unused code paths
3. No runtime overhead in Beta/Prod
