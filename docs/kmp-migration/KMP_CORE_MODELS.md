# KMP Core Models Module

This document describes the `shared/core-models` Kotlin Multiplatform module and the migration plan for moving platform-independent data models from the existing Android library `core-models` module.

## Overview

The `shared/core-models` module will contain all platform-independent data models and types used throughout Scanium. This is the foundation layer with **no dependencies** on other modules, making it safe to depend on from all other shared and platform-specific modules.

## Module Configuration

**Type**: Kotlin Multiplatform Library
**Plugin**: `kotlin("multiplatform")`
**Package**: `com.scanium.core.models`
**Namespace (Android)**: `com.scanium.core.models`

**Source Sets**:
- `commonMain` - Shared Kotlin code (all models)
- `commonTest` - Shared tests
- `androidMain` - Android-specific extensions
- `androidUnitTest` - Android unit tests
- `iosMain` - iOS-specific extensions
- `iosTest` - iOS tests

**Dependencies**:
- Kotlinx Serialization JSON (for data serialization)
- Kotlin Test (for testing)

## Files to Migrate

The following files from `core-models/src/main/java/` will be migrated to `shared/core-models/src/commonMain/kotlin/`:

### Core Geometry & Image Types

#### `ImageRef.kt`
**Source**: `core-models/src/main/java/com/scanium/app/model/ImageRef.kt`
**Target**: `shared/core-models/src/commonMain/kotlin/com/scanium/core/models/image/ImageRef.kt`

Platform-agnostic image reference (sealed class).

```kotlin
sealed class ImageRef {
    data class Bytes(
        val data: ByteArray,
        val format: String = "JPEG"
    ) : ImageRef()
}
```

**Migration Notes**:
- Already Android-free ✅
- Uses `ByteArray` (Kotlin stdlib) instead of `Bitmap`
- Ready for `commonMain`

**Tests**: `core-models/src/test/java/com/scanium/app/model/ImageRefTest.kt`

---

#### `NormalizedRect.kt`
**Source**: `core-models/src/main/java/com/scanium/app/model/NormalizedRect.kt`
**Target**: `shared/core-models/src/commonMain/kotlin/com/scanium/core/models/geometry/NormalizedRect.kt`

Normalized bounding box with 0-1 coordinates.

```kotlin
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)
```

**Migration Notes**:
- Already Android-free ✅
- No Android types (no `Rect`, `RectF`)
- Ready for `commonMain`

**Tests**: `core-models/src/test/java/com/scanium/app/model/NormalizedRectTest.kt`

---

### ML Detection Types

#### `ItemCategory.kt`
**Source**: `core-models/src/main/java/com/scanium/app/ml/ItemCategory.kt`
**Target**: `shared/core-models/src/commonMain/kotlin/com/scanium/core/models/ml/ItemCategory.kt`

Enum mapping ML Kit's 5 coarse categories.

```kotlin
enum class ItemCategory {
    FASHION_GOOD,
    FOOD,
    HOME_GOOD,
    PLACE,
    PLANT,
    UNKNOWN
}
```

**Migration Notes**:
- Pure Kotlin enum ✅
- No Android dependencies
- Ready for `commonMain`

---

#### `ScanMode.kt`
**Source**: `core-models/src/main/java/com/scanium/app/camera/ScanMode.kt`
**Target**: `shared/core-models/src/commonMain/kotlin/com/scanium/core/models/camera/ScanMode.kt`

Scan mode enum: OBJECT_DETECTION | BARCODE | DOCUMENT_TEXT

```kotlin
enum class ScanMode {
    OBJECT_DETECTION,
    BARCODE,
    DOCUMENT_TEXT
}
```

**Migration Notes**:
- Pure Kotlin enum ✅
- No Android dependencies
- Ready for `commonMain`

---

#### `ClassificationMode.kt`
**Source**: `core-models/src/main/java/com/scanium/app/ml/classification/ClassificationMode.kt`
**Target**: `shared/core-models/src/commonMain/kotlin/com/scanium/core/models/ml/classification/ClassificationMode.kt`

Classification mode enum: ON_DEVICE | CLOUD

```kotlin
enum class ClassificationMode {
    ON_DEVICE,
    CLOUD
}
```

**Migration Notes**:
- Pure Kotlin enum ✅
- No Android dependencies
- Ready for `commonMain`

---

#### `DetectionResult.kt`
**Source**: `core-models/src/main/java/com/scanium/app/ml/DetectionResult.kt`
**Target**: `shared/core-models/src/commonMain/kotlin/com/scanium/core/models/ml/DetectionResult.kt`

Real-time detection result using portable types.

**Migration Notes**:
- Uses `NormalizedRect` ✅
- Already migrated away from Android `Rect`
- Ready for `commonMain`

---

#### `RawDetection.kt`
**Source**: `core-models/src/main/java/com/scanium/app/ml/RawDetection.kt`
**Target**: `shared/core-models/src/commonMain/kotlin/com/scanium/core/models/ml/RawDetection.kt`

Raw ML detection data.

**Migration Notes**:
- ⚠️ **Transitional state**: Currently has both legacy (`boundingBox: Rect?`, `thumbnail: Bitmap?`) and portable (`bboxNorm: NormalizedRect?`, `thumbnailRef: ImageRef?`) fields
- **Before migration**: Remove legacy `Rect` and `Bitmap` fields
- **After cleanup**: Will only use `NormalizedRect` and `ImageRef`
- Must update all call sites in `androidApp` to use portable fields

---

#### `ClassificationResult.kt`
**Source**: `core-models/src/main/java/com/scanium/app/ml/classification/ClassificationResult.kt`
**Target**: `shared/core-models/src/commonMain/kotlin/com/scanium/core/models/ml/classification/ClassificationResult.kt`

Cloud classification result with domain category and attributes.

**Migration Notes**:
- Check for Android dependencies (likely none)
- If Android-free, ready for `commonMain`

---

### Item Models

#### `ScannedItem.kt`
**Source**: `core-models/src/main/java/com/scanium/app/items/ScannedItem.kt`
**Target**: `shared/core-models/src/commonMain/kotlin/com/scanium/core/models/items/ScannedItem.kt`

Immutable scanned item model.

**Migration Notes**:
- ⚠️ **Blocker**: Currently uses Android `Uri` field
- **Before migration**: Replace `Uri` with platform-agnostic string path or remove entirely
- Uses `ImageRef` ✅
- Uses `NormalizedRect` ✅
- Must remove `Uri` dependency before moving to `commonMain`

**Action Required**:
1. Audit `ScannedItem.uri` usage across codebase
2. Replace with `String` path or remove field
3. Update all call sites

---

## Migration Checklist

### Phase 1: Pre-Migration Cleanup ✅ (This PR)
- [x] Create KMP module skeleton
- [x] Define source sets (commonMain, androidMain, iosMain)
- [x] Add minimal Gradle configuration
- [x] Add placeholder files
- [x] Document migration plan

### Phase 2: Remove Android Dependencies (Next PR)
- [ ] Remove legacy `Rect`/`Bitmap` fields from `RawDetection`
- [ ] Remove `Uri` from `ScannedItem`
- [ ] Update all call sites in `androidApp` to use portable types
- [ ] Verify no android.* imports remain in target files

### Phase 3: Move Code (Future PR)
- [ ] Move `ImageRef.kt` → `commonMain`
- [ ] Move `NormalizedRect.kt` → `commonMain`
- [ ] Move `ItemCategory.kt` → `commonMain`
- [ ] Move `ScanMode.kt` → `commonMain`
- [ ] Move `ClassificationMode.kt` → `commonMain`
- [ ] Move `DetectionResult.kt` → `commonMain`
- [ ] Move `RawDetection.kt` → `commonMain` (after cleanup)
- [ ] Move `ClassificationResult.kt` → `commonMain`
- [ ] Move `ScannedItem.kt` → `commonMain` (after Uri removal)
- [ ] Move tests → `commonTest`

### Phase 4: Update Dependencies (Future PR)
- [ ] Update `core-tracking` to depend on `shared:core-models`
- [ ] Update `core-domainpack` to depend on `shared:core-models`
- [ ] Update `androidApp` to depend on `shared:core-models`
- [ ] Update `android-platform-adapters` to depend on `shared:core-models`
- [ ] Remove old `core-models` Android library module

---

## Strict Rules for commonMain

### ✅ ALLOWED
- Kotlin stdlib (`kotlin.*`)
- Kotlin Coroutines (`kotlinx.coroutines.*`)
- Kotlinx Serialization (`kotlinx.serialization.*`)
- Pure Kotlin data classes, enums, sealed classes
- Platform-agnostic types: `ByteArray`, `String`, `Float`, etc.

### ❌ FORBIDDEN
- `android.*` - Any Android SDK classes
- `androidx.*` - Any AndroidX library classes
- `android.graphics.Bitmap` - Use `ImageRef` instead
- `android.graphics.Rect` / `RectF` - Use `NormalizedRect` instead
- `android.net.Uri` - Use `String` path instead
- `java.io.File` - Platform-specific, use expect/actual if needed
- `android.util.Log` - Use `Logger` interface instead
- CameraX, ML Kit, Compose - Platform-specific, stays in `androidApp`

### Enforcement
- CI builds will verify no Android imports in `commonMain`
- Gradle configuration disallows Android dependencies
- Code review checklist includes Android-free verification

---

## Package Structure

Target package structure in `commonMain`:

```
com.scanium.core.models/
├── image/
│   └── ImageRef.kt                   # Platform-agnostic image
├── geometry/
│   └── NormalizedRect.kt             # Normalized bounding box
├── ml/
│   ├── ItemCategory.kt               # ML category enum
│   ├── DetectionResult.kt            # Detection result
│   ├── RawDetection.kt               # Raw ML detection
│   └── classification/
│       ├── ClassificationMode.kt     # ON_DEVICE | CLOUD
│       └── ClassificationResult.kt   # Domain classification result
├── camera/
│   └── ScanMode.kt                   # Scan mode enum
└── items/
    └── ScannedItem.kt                # Scanned item model
```

---

## Testing Strategy

### Unit Tests
- All tests from `core-models/src/test/` will move to `commonTest`
- Use `kotlin.test` framework (platform-agnostic)
- No Robolectric needed (pure Kotlin tests)

**Existing Tests**:
- `ImageRefTest.kt` - ImageRef serialization, equality
- `NormalizedRectTest.kt` - Rect validation, clamping, area

**New Tests Needed**:
- Tests for all migrated models
- Serialization tests (Kotlinx Serialization)
- Edge case validation

---

## Dependencies

### This Module Depends On
- **None** - Foundation layer, no internal dependencies

### Modules That Will Depend On This
- `shared:core-tracking` - Uses `ImageRef`, `NormalizedRect`, `ScannedItem`
- `shared:core-domainpack` - Uses `ItemCategory`
- `androidApp` - Uses all models
- `android-platform-adapters` - Converts between Android types and these models

---

## Build Verification

### Local Build (No Android SDK required for commonMain)
```bash
./gradlew :shared:core-models:compileKotlinCommonMain
./gradlew :shared:core-models:compileKotlinAndroidDebug
```

### Testing
```bash
./gradlew :shared:core-models:cleanAllTests :shared:core-models:allTests
```

### CI Pipeline
- Verify `commonMain` builds without Android SDK
- Run tests on JVM (commonTest)
- Build Android target
- Build iOS targets (future)

---

## Timeline

1. **This PR**: KMP skeleton only (no code moved)
2. **Next PR**: Remove Android dependencies from target files
3. **Future PR**: Move code to `shared/core-models`
4. **Future PR**: Update all module dependencies
5. **Future PR**: Remove old `core-models` Android library

---

## References

- **KMP Structure**: `docs/kmp-migration/KMP_STRUCTURE.md`
- **Migration Plan**: `docs/kmp-migration/PLAN.md`
- **Phase 2 Plan**: `docs/kmp-migration/PHASE_2_PLAN.md`
- **Portability Status**: `docs/kmp-migration/PORTABILITY_STATUS.md`

---

**Status**: Phase 1 (Skeleton) - In Progress
**Last Updated**: 2025-12-17
