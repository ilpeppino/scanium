# Fix Tracker Configuration Documentation Mismatch

**Labels:** `documentation`, `priority:p1`, `area:tracking`, `area:camera`
**Type:** Documentation Bug
**Severity:** High

## Problem

CLAUDE.md documents **incorrect TrackerConfig values**. The documented defaults don't match the actual values used in CameraXManager.kt.

## Documented vs Actual Values

### CLAUDE.md (Configuration & Tuning section) - WRONG ❌

```kotlin
TrackerConfig(
    minFramesToConfirm = 1,      // ✅ CORRECT
    minConfidence = 0.25f,       // ❌ ACTUAL IS 0.2f
    minBoxArea = 0.0f,           // ❌ ACTUAL IS 0.0005f
    maxFrameGap = 5,             // ❌ ACTUAL IS 8
    minMatchScore = 0.3f,        // ❌ ACTUAL IS 0.2f
    expiryFrames = 30,           // ❌ ACTUAL IS 15
    candidateTimeoutMs = 3000L   // ❌ NOT USED (ObjectTracker uses frame-based expiry)
)
```

### CameraXManager.kt lines 57-62 - ACTUAL ✅

```kotlin
private val objectTracker = ObjectTracker(
    config = TrackerConfig(
        minFramesToConfirm = 1,      // Confirm immediately
        minConfidence = 0.2f,         // Very low (20%) ❌ DOCS SAY 0.25f
        minBoxArea = 0.0005f,         // Very small (0.05%) ❌ DOCS SAY 0.0f
        maxFrameGap = 8,              // More forgiving ❌ DOCS SAY 5
        minMatchScore = 0.2f,         // Lower threshold ❌ DOCS SAY 0.3f
        expiryFrames = 15             // Keep longer ❌ DOCS SAY 30
    )
)
```

## Impact

- **Developer Confusion**: Docs don't match reality
- **Tuning Errors**: Developers might tune wrong values
- **Lost Context**: Comments explain "why" but docs are outdated
- **Trust Issues**: Users can't trust documentation

## Root Cause

Documentation was written for earlier tracker config and never updated when values were tuned.

## Acceptance Criteria

- [x] Update CLAUDE.md with correct TrackerConfig values
- [x] Remove `candidateTimeoutMs` from docs (ObjectTracker doesn't use it)
- [x] Add comments explaining why each value was chosen
- [x] Document the trade-offs of the aggressive thresholds
- [x] Cross-reference with TRACKING_IMPLEMENTATION.md

## Suggested Fix

Update CLAUDE.md section "Configuration & Tuning":

```markdown
### Tracker Configuration
Located in `CameraXManager.kt`:

```kotlin
TrackerConfig(
    minFramesToConfirm = 1,      // Instant confirmation (relies on aggregator for quality)
    minConfidence = 0.2f,         // Very low (20%) - aggressive detection
    minBoxArea = 0.0005f,         // Accept tiny objects (0.05% of frame)
    maxFrameGap = 8,              // Forgiving matching (allow 8 frames gap)
    minMatchScore = 0.2f,         // Low spatial matching threshold
    expiryFrames = 15             // Keep candidates 15 frames (~12 seconds at 800ms)
)
```

**Rationale for Aggressive Thresholds:**
- **Session-level deduplication**: ItemAggregator handles quality filtering
- **Responsive UX**: Instant confirmation feels more natural
- **Inclusive detection**: Low confidence catches edge cases
- **Forgiving tracking**: maxFrameGap=8 handles occlusion/movement

**Trade-offs:**
- More false positives at tracker level (filtered by aggregator)
- Potentially more noise (mitigated by 0.2f confidence minimum)
- Higher memory usage (more candidates stay active longer)

**Tuning Guidelines:**
- Increase `minFramesToConfirm` to 3 for more stable confirmations
- Increase `minConfidence` to 0.4f to reduce false positives
- Decrease `expiryFrames` to 10 to reduce memory usage
```

## Additional Documentation Needed

Add rationale section explaining:
- Why these values differ from TrackerConfig defaults
- How tracker and aggregator work together
- When to tune which parameter
- Expected behavior with current config

## Related Issues

- Issue #006 (Extract hardcoded configuration values)

---

## Resolution

**Status:** ✅ RESOLVED

**Changes Made:**

Updated CLAUDE.md (lines 417-446) to reflect actual TrackerConfig values:

**Corrected Values:**
| Parameter | Was (Wrong) | Now (Correct) | Change |
|-----------|-------------|---------------|--------|
| `minFramesToConfirm` | 1 | 1 | ✅ No change |
| `minConfidence` | 0.25f | **0.2f** | ❌ Fixed |
| `minBoxArea` | 0.0f | **0.0005f** | ❌ Fixed |
| `maxFrameGap` | 5 | **8** | ❌ Fixed |
| `minMatchScore` | 0.3f | **0.2f** | ❌ Fixed |
| `expiryFrames` | 30 | **15** | ❌ Fixed |
| `candidateTimeoutMs` | 3000L | **REMOVED** | ❌ Not used |

**5 out of 6 parameters were wrong, plus 1 non-existent parameter documented.**

**Enhanced Documentation:**

Added three new sections to CLAUDE.md:

1. **Rationale for Aggressive Thresholds:**
   - Explains why values are permissive (ItemAggregator handles quality)
   - Describes UX benefits (responsive, instant confirmation)
   - Notes forgiving tracking for camera movement

2. **Trade-offs:**
   - Documents false positive risk (mitigated by aggregator)
   - Notes potential noise (0.2f minimum confidence helps)
   - Mentions memory usage (15 frames vs 30)

3. **Enhanced Tuning Guidelines:**
   - Specific examples with numeric values
   - Explains tracker vs aggregator balance
   - Provides concrete threshold recommendations

**Verification:**

```bash
# Confirmed actual values in code
cat app/src/main/java/com/scanium/app/camera/CameraXManager.kt | grep -A 10 "TrackerConfig"

# Output matches new documentation
minFramesToConfirm = 1
minConfidence = 0.2f
minBoxArea = 0.0005f
maxFrameGap = 8
minMatchScore = 0.2f
expiryFrames = 15
```

**Impact:**
- ✅ Documentation now matches code exactly
- ✅ Developers can trust CLAUDE.md for tuning
- ✅ Added context explains "why" behind aggressive values
- ✅ Removed confusion about non-existent candidateTimeoutMs

**Benefits:**
- Accurate reference for developers
- Clear rationale for threshold choices
- Better understanding of tracker/aggregator relationship
- Actionable tuning guidance with specific values
