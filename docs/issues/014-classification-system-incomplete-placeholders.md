***REMOVED*** Classification System Has Placeholder Implementations

**Labels:** `enhancement`, `priority:p3`, `area:ml`, `feature-incomplete`
**Type:** Feature Completeness
**Severity:** Low (expected for PoC, but should be documented)

***REMOVED******REMOVED*** Problem

The classification system architecture is implemented but uses **placeholder/stub classifiers** instead of real ML models. This is likely intentional for a PoC, but should be explicitly documented.

***REMOVED******REMOVED*** Current State

***REMOVED******REMOVED******REMOVED*** OnDeviceClassifier - Fake Implementation

File: `/app/src/main/java/com/scanium/app/ml/classification/OnDeviceClassifier.kt`

**Lines 17-72**: Uses brightness/contrast heuristics instead of actual CLIP/TFLite model

**Evidence:**
```kotlin
// Lightweight on-device classifier **placeholder**
// In production, this would use:
// - CLIP (Contrastive Language-Image Pre-training) model
// - Custom TensorFlow Lite model
// - Fine-tuned MobileNet
```

Fake logic:
```kotlin
val avgBrightness = // calculate from pixels
val contrast = // calculate from pixels

// Fake classification based on visual features
return when {
    avgBrightness > 180 && contrast < 40 -> ClassificationResult("Unknown", 0.3f)
    avgBrightness < 80 -> ClassificationResult(originalCategory.name, 0.4f)
    // ... more fake heuristics
}
```

***REMOVED******REMOVED******REMOVED*** CloudClassifier - Requires External Configuration

File: `/app/src/main/java/com/scanium/app/ml/classification/CloudClassifier.kt`

**Issue**: Requires `BuildConfig.CLOUD_CLASSIFIER_URL` which defaults to empty string

```kotlin
private val apiUrl = BuildConfig.CLOUD_CLASSIFIER_URL.ifEmpty {
    Log.w(TAG, "Cloud classifier URL not configured")
    return@lazy null
}
```

**Impact**: Silently skips cloud classification if not configured.

***REMOVED******REMOVED******REMOVED*** ClassificationOrchestrator - Works Correctly

File: `/app/src/main/java/com/scanium/app/ml/classification/ClassificationOrchestrator.kt`

**Status**: ✅ Implementation is correct - properly orchestrates classifiers, just depends on placeholder implementations above.

***REMOVED******REMOVED*** Impact

**Current**: System appears to work but provides low-quality classifications

**User Experience**:
- OnDeviceClassifier returns random/fake categories
- CloudClassifier is disabled by default
- Users get poor category refinement

**Expectations**: This is likely **intentional for PoC scope**

***REMOVED******REMOVED*** Decision Required

Choose ONE:

***REMOVED******REMOVED******REMOVED*** Option 1: Document as Intentional (Recommended)

Add prominent documentation that classification is placeholder-only:

**In CLAUDE.md:**
```markdown
***REMOVED******REMOVED*** Classification System (Placeholder Implementation)

⚠️ **Current Status**: Classification system architecture is complete but uses placeholder implementations suitable for PoC/demo only.

***REMOVED******REMOVED******REMOVED*** OnDeviceClassifier
- **Current**: Brightness/contrast heuristics
- **Production TODO**: Integrate CLIP or custom TFLite model
- **Effort**: 2-3 sprints for model training + integration

***REMOVED******REMOVED******REMOVED*** CloudClassifier
- **Current**: Disabled (no API configured)
- **Production TODO**: Implement backend classification API
- **Effort**: Backend API development + integration
```

***REMOVED******REMOVED******REMOVED*** Option 2: Implement Real Classifiers

Significant effort required:
- Research and select CLIP model variant
- Convert to TensorFlow Lite
- Integrate with Android ML inference
- Build/integrate cloud API
- Performance testing

***REMOVED******REMOVED*** Acceptance Criteria (Option 1 - Document)

- [ ] Add "Known Limitations" section to CLAUDE.md documenting placeholder classifiers
- [ ] Add TODO comments in OnDeviceClassifier pointing to model integration guide
- [ ] Add configuration guide for CloudClassifier in SETUP.md
- [ ] Document expected classification quality ("placeholder only")
- [ ] Add future roadmap section mentioning real ML integration

***REMOVED******REMOVED*** Suggested Documentation

***REMOVED******REMOVED******REMOVED*** In OnDeviceClassifier.kt:

```kotlin
/**
 * PLACEHOLDER on-device classifier using simple heuristics.
 *
 * For production use, replace with:
 * - CLIP (Contrastive Language-Image Pre-training) model
 * - Custom TensorFlow Lite model trained on product images
 * - Fine-tuned MobileNetV3 or EfficientNet
 *
 * Current implementation uses brightness/contrast for demo purposes only.
 * Classification quality is intentionally low.
 *
 * See: docs/roadmap/ML_MODEL_INTEGRATION.md for implementation guide
 */
class OnDeviceClassifier : ItemClassifier {
    // ... current implementation
}
```

***REMOVED******REMOVED******REMOVED*** In build.gradle.kts:

```kotlin
// Cloud Classifier Configuration (Optional)
// To enable cloud classification, set these in local.properties:
// cloud.classifier.url=https://your-api.com/classify
// cloud.classifier.api.key=your-api-key
buildConfigField("String", "CLOUD_CLASSIFIER_URL",
    "\"${project.findProperty("cloud.classifier.url") ?: ""}\"")
buildConfigField("String", "CLOUD_CLASSIFIER_API_KEY",
    "\"${project.findProperty("cloud.classifier.api.key") ?: ""}\"")
```

***REMOVED******REMOVED*** Related Issues

None (this is expected PoC limitation)
