> Archived on 2025-12-20: superseded by docs/INDEX.md.

# Camera & Classification Performance Profiling Guide

This guide explains how to profile camera analyzer latency, frame rate, and classification
turnaround using the instrumentation added to Scanium.

## Instrumented Metrics

### 1. Analyzer Latency (CameraXManager)

- **What**: Time from receiving a camera frame to completing ML analysis
- **Location**: `CameraXManager.startScanning()`
- **Logcat Tag**: `CameraXManager`
- **Log Format**: `[METRICS] Frame #N analyzer latency: XXXms`

### 2. Frame Rate (CameraXManager)

- **What**: Frames processed per second (current window & session average)
- **Location**: `CameraXManager.startScanning()`
- **Logcat Tag**: `CameraXManager`
- **Log Format**: `[METRICS] Frame rate: current=X.XX fps, session_avg=X.XX fps, total_frames=N`
- **Reporting Interval**: Every 5 seconds

### 3. Classification Turnaround (ClassificationOrchestrator)

- **What**: Time from classification request start to result
- **Location**: `ClassificationOrchestrator.classifyWithRetry()`
- **Logcat Tag**: `ClassificationOrchestrator`
- **Log Format**:
  `[METRICS] Classification turnaround for ITEM_ID: XXXms (mode=CLOUD/ON_DEVICE, status=SUCCESS/FAILED)`

## Quick Start: Logcat Monitoring

### Install and Launch App

```bash
./gradlew installDebug
adb shell am start -n com.scanium.app/.MainActivity
```

### Monitor All Metrics

```bash
adb logcat -s CameraXManager:I ClassificationOrchestrator:I
```

### Monitor Specific Metrics

**Analyzer Latency Only:**

```bash
adb logcat -s CameraXManager:I | grep "analyzer latency"
```

**Frame Rate Only:**

```bash
adb logcat -s CameraXManager:I | grep "Frame rate"
```

**Classification Turnaround Only:**

```bash
adb logcat -s ClassificationOrchestrator:I | grep "Classification turnaround"
```

## Advanced Profiling with Android Profiler

### Enable Trace Markers

The instrumentation includes `Trace.beginSection()`/`Trace.endSection()` calls for:

- `CameraXManager.analyzeFrame` - Full frame analysis pipeline
- `ClassificationOrchestrator.classify` - Classification request
- `ClassificationOrchestrator.retry` - Manual retry request

### Capture CPU Trace

1. **Start app and profiling:**
   ```bash
   ./gradlew installDebug
   adb shell am start -n com.scanium.app/.MainActivity

   # Start CPU profiling (record to file)
   adb shell am profile start com.scanium.app /data/local/tmp/scanium_profile.trace
   ```

2. **Use the app** (scan objects, trigger classifications)

3. **Stop profiling:**
   ```bash
   adb shell am profile stop com.scanium.app

   # Pull trace file
   adb pull /data/local/tmp/scanium_profile.trace ./scanium_profile.trace
   ```

4. **Analyze in Android Studio:**
    - Open Android Studio
    - File → Open → Select `scanium_profile.trace`
    - Look for trace sections:
        - `CameraXManager.analyzeFrame`
        - `ClassificationOrchestrator.classify`

### Using Android Studio Profiler (Interactive)

1. **Run app with profiler:**
    - Android Studio → Run → Profile 'androidApp'

2. **Navigate to CPU Profiler:**
    - Click "CPU" section
    - Click "Record" → Select "System Trace"
    - Interact with app (scan objects)
    - Click "Stop"

3. **Analyze trace sections:**
    - Expand "Main thread" and look for:
        - `CameraXManager.analyzeFrame` - Frame analysis latency
        - `ClassificationOrchestrator.classify` - Classification latency

## Profiling Scenarios

### Scenario 1: Camera Performance on Mid-Tier Device

**Goal**: Measure analyzer latency and frame rate on Pixel 4a or similar

1. Install and start app:
   ```bash
   ./gradlew installDebug
   adb shell am start -n com.scanium.app/.MainActivity
   ```

2. Monitor metrics in real-time:
   ```bash
   adb logcat -s CameraXManager:I | grep METRICS
   ```

3. **Expected metrics:**
    - Analyzer latency: 100-300ms (ML Kit inference)
    - Frame rate: 1.0-1.5 fps (throttled to 800ms interval)

### Scenario 2: Classification Performance (Cloud vs On-Device)

**Goal**: Compare turnaround time for cloud vs on-device classification

1. **Test Cloud Classification:**
    - Enable cloud mode in app settings
    - Scan objects
    - Monitor: `adb logcat -s ClassificationOrchestrator:I | grep turnaround`
    - Expected: 500-2000ms (network + server)

2. **Test On-Device Classification:**
    - Enable on-device mode in app settings
    - Scan objects
    - Monitor: `adb logcat -s ClassificationOrchestrator:I | grep turnaround`
    - Expected: 100-500ms (local inference)

### Scenario 3: End-to-End Pipeline (Frame → Classification → UI)

**Goal**: Measure total time from camera frame to classified result displayed

1. **Capture full trace:**
   ```bash
   adb shell am profile start com.scanium.app /data/local/tmp/scanium_e2e.trace
   # Scan 3-5 objects
   adb shell am profile stop com.scanium.app
   adb pull /data/local/tmp/scanium_e2e.trace ./scanium_e2e.trace
   ```

2. **Analyze in Android Studio:**
    - Look for sequence:
        - `CameraXManager.analyzeFrame` (detection)
        - `ClassificationOrchestrator.classify` (classification)
    - Measure wall time from frame start to classification complete

## Performance Baselines

### Target Metrics (Mid-Tier Device: Pixel 4a, Snapdragon 730)

| Metric                   | Target      | Acceptable  | Poor     |
|--------------------------|-------------|-------------|----------|
| Analyzer Latency         | <200ms      | <400ms      | >500ms   |
| Frame Rate               | 1.0-1.5 fps | 0.8-1.0 fps | <0.5 fps |
| Cloud Classification     | <1500ms     | <3000ms     | >5000ms  |
| On-Device Classification | <300ms      | <600ms      | >1000ms  |

## Troubleshooting

### High Analyzer Latency (>500ms)

- Check device CPU load: `adb shell top -m 5`
- Verify ML Kit model downloaded: Check logs for "model ready"
- Test on physical device (emulators are slow)

### Low Frame Rate (<0.5 fps)

- Check if backpressure strategy is working: Look for "close proxy immediately" logs
- Verify analysis interval: Should process every 800ms
- Check for frame drops: Look for skipped frames in logs

### Slow Classification (>5s)

- Check network connectivity for cloud mode
- Verify classifier initialization: Check logs for "Classifying with mode="
- Look for retry attempts: Check for "Retry attempt" logs

## Exporting Metrics for Analysis

### Export Logcat to CSV

```bash
# Capture 60 seconds of metrics
adb logcat -s CameraXManager:I ClassificationOrchestrator:I > metrics.log

# Parse metrics (example using grep)
grep "analyzer latency" metrics.log | awk '{print $NF}' | sed 's/ms//' > analyzer_latency.csv
grep "Classification turnaround" metrics.log | awk -F': ' '{print $2}' | awk '{print $1}' | sed 's/ms//' > classification_turnaround.csv
```

### Analyze in Python/R

```python
import pandas as pd
import matplotlib.pyplot as plt

# Load metrics
latency = pd.read_csv('analyzer_latency.csv', header=None, names=['latency_ms'])
turnaround = pd.read_csv('classification_turnaround.csv', header=None, names=['turnaround_ms'])

# Plot histograms
latency.hist(bins=20)
plt.xlabel('Analyzer Latency (ms)')
plt.ylabel('Frequency')
plt.title('Camera Analyzer Latency Distribution')
plt.show()
```

## Reference: DEV_GUIDE Commands

Per [docs/DEV_GUIDE.md](DEV_GUIDE.md):

- `./gradlew installDebug` - Install APK on device
- Logcat filters: `CameraXManager`, `ClassificationOrchestrator`
- CPU profiling: `adb shell am profile start/stop`
