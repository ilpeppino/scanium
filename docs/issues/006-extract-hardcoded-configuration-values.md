# Extract Hardcoded Configuration Values to Centralized Config

**Labels:** `tech-debt`, `priority:p2`, `maintainability`, `area:camera`, `area:ml`
**Type:** Code Maintainability
**Severity:** Medium

## Problem

Configuration values are hardcoded throughout the codebase, making it difficult to:
- Tune performance without recompiling
- A/B test different thresholds
- Understand what's configurable
- Debug why certain values were chosen

## Hardcoded Values Found

### 1. CameraXManager.kt

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

### 2. ObjectDetectorClient.kt

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

### 3. CloudClassifier.kt

**Lines 43-44**: Network timeouts
```kotlin
connectTimeout = 5_000
readTimeout = 8_000
```

### 4. SessionDeduplicator.kt (if not deleted per Issue #003)

**Lines 30-32**: Similarity thresholds
```kotlin
private const val MAX_CENTER_DISTANCE_RATIO = 0.15f
private const val MAX_SIZE_RATIO_DIFF = 0.4f
private const val MIN_LABEL_SIMILARITY = 0.7f
```

## Impact

- **Maintenance**: Changing thresholds requires code changes
- **Testing**: Can't A/B test different configurations
- **Documentation**: Hard to see all tunable parameters
- **Production**: Can't hotfix threshold issues without app update

## Acceptance Criteria

- [ ] Create `ScaniumConfig` object or class
- [ ] Move all hardcoded values to centralized config
- [ ] Add inline documentation for each config value
- [ ] Consider loading from BuildConfig for release/debug variants
- [ ] Update CLAUDE.md with configuration section

## Suggested Approach

### 1. Create Config Object

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

### 2. Update Usages

Replace hardcoded values with `ScaniumConfig.Camera.targetResolution` etc.

### 3. Document in CLAUDE.md

Add "Configuration & Tuning" section documenting all values and their impact.

## Alternative: BuildConfig Approach

For more advanced control:

```kotlin
// build.gradle.kts
buildConfigField("Int", "CAMERA_WIDTH", "1280")
buildConfigField("Int", "CAMERA_HEIGHT", "720")
buildConfigField("Float", "MIN_CONFIDENCE", "0.2f")

// Usage
val resolution = Size(BuildConfig.CAMERA_WIDTH, BuildConfig.CAMERA_HEIGHT)
```

## Related Issues

- Issue #009 (Tracker config mismatch between code and docs)

---

## Assessment

**Status:** ❌ NOT RELEVANT (Premature Abstraction)

**Decision:** Do not implement centralized configuration object at this time.

### Why This Is Not Relevant

**1. YAGNI Principle Violation**
- No evidence of frequent tuning needed
- No A/B testing requirements for PoC/demo app
- No production hotfix requirements
- "You Aren't Gonna Need It" - don't add abstraction until proven necessary

**2. Current Approach Is Appropriate**
The hardcoded values are **tuning parameters**, not user configuration:
- ✅ Well-documented with inline comments
- ✅ Co-located near usage (easier to understand)
- ✅ Discoverable via simple grep (`grep "private const val"`)
- ✅ Named constants are self-documenting

**3. Proposed Solution Adds Complexity Without Benefit**
```kotlin
// Current (clear and simple):
private const val CONFIDENCE_THRESHOLD = 0.3f // Category assignment threshold

// Proposed (unnecessary indirection):
ScaniumConfig.Detection.confidenceThreshold
```
- Requires jumping between files to understand values
- Adds layer of abstraction with no proven ROI
- Makes code harder to read, not easier

**4. Android Best Practices**
- **Co-location**: Keep config near usage for clarity
- **KISS**: Inline constants are simpler than config objects
- **Documentation**: Comments explain "why" better than config files
- **Maintainability**: Fewer files/abstractions = easier maintenance

### The Real Problem

The issue correctly identifies a documentation problem, but proposes the wrong solution:

**Real Issue:** CLAUDE.md is **out of sync** with actual code

**Example - TrackerConfig mismatch:**

CLAUDE.md documents:
```kotlin
minConfidence = 0.25f
minBoxArea = 0.0f
maxFrameGap = 5
minMatchScore = 0.3f
expiryFrames = 30
```

Actual code (CameraXManager.kt:56-63):
```kotlin
minConfidence = 0.2f      // DIFFERENT!
minBoxArea = 0.0005f      // DIFFERENT!
maxFrameGap = 8           // DIFFERENT!
minMatchScore = 0.2f      // DIFFERENT!
expiryFrames = 15         // DIFFERENT!
```

**Solution:** Fix the documentation, not the code structure (see Issue #009).

### Current State Verification

**✅ All values are well-documented:**

1. **CameraXManager.kt:131**
   ```kotlin
   .setTargetResolution(android.util.Size(1280, 720)) // Higher resolution for better detection
   ```

2. **CameraXManager.kt:218**
   ```kotlin
   val analysisIntervalMs = 800L // Analyze every 800ms for better tracking
   ```

3. **CameraXManager.kt:56-63** - TrackerConfig with 7 documented parameters

4. **ObjectDetectorClient.kt:34**
   ```kotlin
   private const val CONFIDENCE_THRESHOLD = 0.3f // Category assignment threshold
   ```

5. **ObjectDetectorClient.kt:209**
   ```kotlin
   val isLikelyBlank = totalVariance < 30 // Very low variance suggests blank image
   ```

6. **ObjectDetectorClient.kt:587** (+ BarcodeScannerClient, DocumentTextRecognitionClient)
   ```kotlin
   // CRITICAL: Limit thumbnail size to save memory
   val maxDimension = 200
   ```

7. **CloudClassifier.kt:43-44**
   ```kotlin
   connectTimeout = 5_000
   readTimeout = 8_000
   ```

**✅ Values are discoverable:**
```bash
# Find all tuning constants
grep -r "private const val" app/src/main/java/
grep -r "val.*= [0-9]" app/src/main/java/com/scanium/app/camera/
```

**✅ CLAUDE.md has "Configuration & Tuning" section** (though out of date - see Issue #009)

### When to Revisit

Consider centralized configuration **only if**:

1. **Production deployment** with need for:
   - A/B testing different thresholds
   - Feature flags for gradual rollout
   - Remote config updates

2. **Multiple build variants** requiring different values:
   - Debug vs Release
   - Free vs Premium
   - Per-customer configurations

3. **Frequent tuning** evidenced by:
   - Multiple PRs changing same constants
   - User-reported issues requiring threshold adjustments
   - Performance testing requiring parameter sweeps

4. **Dynamic configuration** requirements:
   - Runtime adjustment via settings UI
   - Server-driven configuration
   - ML-based auto-tuning

**None of these apply to current PoC/demo scope.**

### Recommended Actions

**Instead of creating ScaniumConfig:**

1. ✅ **Keep inline constants** with descriptive comments
2. ✅ **Fix CLAUDE.md** to match actual code (Issue #009)
3. ✅ **Add "Why" documentation** for non-obvious values
4. ✅ **Follow YAGNI** - defer abstraction until needed

**Example of good inline documentation:**
```kotlin
// Analyze every 800ms to balance:
// - Responsiveness: Catch objects quickly as user pans
// - Battery life: Avoid excessive processing
// - Tracking stability: Allow tracker to correlate frames
val analysisIntervalMs = 800L
```

### Benefits of Current Approach

✅ **Simplicity**: Fewer files, less indirection
✅ **Clarity**: Values near usage, easier to understand
✅ **Maintainability**: No config layer to keep in sync
✅ **Performance**: No runtime config lookup overhead
✅ **Discoverability**: grep works perfectly
✅ **Flexibility**: Easy to change when actually needed

### Conclusion

This issue represents **premature optimization**. The current approach is appropriate for a PoC/demo app. Creating a centralized config object would add complexity without solving any real problem.

**Close this issue and focus on Issue #009** (fixing CLAUDE.md documentation mismatch) instead.
