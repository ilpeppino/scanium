***REMOVED*** Extract Hardcoded Configuration Values to Centralized Config

**Labels:** `tech-debt`, `priority:p2`, `maintainability`, `area:camera`, `area:ml`
**Type:** Code Maintainability
**Severity:** Medium

***REMOVED******REMOVED*** Problem

Configuration values are hardcoded throughout the codebase, making it difficult to:
- Tune performance without recompiling
- A/B test different thresholds
- Understand what's configurable
- Debug why certain values were chosen

***REMOVED******REMOVED*** Hardcoded Values Found

***REMOVED******REMOVED******REMOVED*** 1. CameraXManager.kt

**Line 131**: Camera resolution
```kotlin
.setTargetResolution(android.util.Size(1280, 720)) // Higher resolution for better detection
```

**Line 218**: Analysis interval
```kotlin
val analysisIntervalMs = 800L // Analyze every 800ms for better tracking
```

**Lines 57-62**: TrackerConfig inline
```kotlin
private val objectTracker = ObjectTracker(
    config = TrackerConfig(
        minFramesToConfirm = 1,
        minConfidence = 0.2f,
        minBoxArea = 0.0005f,
        maxFrameGap = 8,
        minMatchScore = 0.2f,
        expiryFrames = 15
    )
)
```

***REMOVED******REMOVED******REMOVED*** 2. ObjectDetectorClient.kt

**Line 34**: Confidence threshold
```kotlin
private const val CONFIDENCE_THRESHOLD = 0.3f
```

**Line 209**: Blank image detection
```kotlin
val isLikelyBlank = totalVariance < 30
```

**Line 587**: Thumbnail max dimension
```kotlin
val maxDimension = 200
```

***REMOVED******REMOVED******REMOVED*** 3. CloudClassifier.kt

**Lines 43-44**: Network timeouts
```kotlin
connectTimeout = 5_000
readTimeout = 8_000
```

***REMOVED******REMOVED******REMOVED*** 4. SessionDeduplicator.kt (if not deleted per Issue ***REMOVED***003)

**Lines 30-32**: Similarity thresholds
```kotlin
private const val MAX_CENTER_DISTANCE_RATIO = 0.15f
private const val MAX_SIZE_RATIO_DIFF = 0.4f
private const val MIN_LABEL_SIMILARITY = 0.7f
```

***REMOVED******REMOVED*** Impact

- **Maintenance**: Changing thresholds requires code changes
- **Testing**: Can't A/B test different configurations
- **Documentation**: Hard to see all tunable parameters
- **Production**: Can't hotfix threshold issues without app update

***REMOVED******REMOVED*** Acceptance Criteria

- [ ] Create `ScaniumConfig` object or class
- [ ] Move all hardcoded values to centralized config
- [ ] Add inline documentation for each config value
- [ ] Consider loading from BuildConfig for release/debug variants
- [ ] Update CLAUDE.md with configuration section

***REMOVED******REMOVED*** Suggested Approach

***REMOVED******REMOVED******REMOVED*** 1. Create Config Object

File: `/app/src/main/java/com/scanium/app/config/ScaniumConfig.kt`

```kotlin
object ScaniumConfig {
    object Camera {
        val targetResolution = Size(1280, 720)
        const val analysisIntervalMs = 800L
    }

    object Tracking {
        const val minFramesToConfirm = 1
        const val minConfidence = 0.2f
        const val minBoxArea = 0.0005f
        const val maxFrameGap = 8
        const val minMatchScore = 0.2f
        const val expiryFrames = 15
    }

    object Detection {
        const val confidenceThreshold = 0.3f
        const val blankImageVarianceThreshold = 30
        const val thumbnailMaxDimension = 200
    }

    object Network {
        const val connectTimeoutMs = 5_000L
        const val readTimeoutMs = 8_000L
    }
}
```

***REMOVED******REMOVED******REMOVED*** 2. Update Usages

Replace hardcoded values with `ScaniumConfig.Camera.targetResolution` etc.

***REMOVED******REMOVED******REMOVED*** 3. Document in CLAUDE.md

Add "Configuration & Tuning" section documenting all values and their impact.

***REMOVED******REMOVED*** Alternative: BuildConfig Approach

For more advanced control:

```kotlin
// build.gradle.kts
buildConfigField("Int", "CAMERA_WIDTH", "1280")
buildConfigField("Int", "CAMERA_HEIGHT", "720")
buildConfigField("Float", "MIN_CONFIDENCE", "0.2f")

// Usage
val resolution = Size(BuildConfig.CAMERA_WIDTH, BuildConfig.CAMERA_HEIGHT)
```

***REMOVED******REMOVED*** Related Issues

- Issue ***REMOVED***009 (Tracker config mismatch between code and docs)
