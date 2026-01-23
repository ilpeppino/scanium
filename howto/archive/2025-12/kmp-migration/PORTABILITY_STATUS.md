# KMP Portability Readiness Status

**Last Updated**: 2025-12-16
**Phase**: Portability Cleanup (Phase 1)

## 5-Point Completion Checklist

Based on the roadmap in `CLAUDE.md`, Phase 1 (Portability Cleanup) consists of the following
criteria:

### 1. Multi-module Gradle structure established (9 modules)

**Status**: ✅ **DONE**

**Evidence**:

- Verified in `settings.gradle.kts:26-36`: all 9 modules present
    - `:androidApp` - Main Android app module
    - `:core-models` - Platform-independent data models
    - `:core-tracking` - Platform-independent tracking/aggregation logic
    - `:core-domainpack` - Domain Pack system (categories, attributes)
    - `:core-scan` - Placeholder for future scan contracts
    - `:core-contracts` - Placeholder for platform-independent contracts
    - `:android-ml-mlkit` - ML Kit Android wrappers (placeholder)
    - `:android-camera-camerax` - CameraX Android wrappers (placeholder)
    - `:android-platform-adapters` - Conversion layer between Android and portable types

**Remaining Work**: None

---

### 2. Portable types fully implemented and integrated

**Status**: ✅ **DONE**

**Evidence**:

- **ImageRef**: Platform-agnostic image reference
    - Location: `core-models/src/main/java/com/scanium/app/model/ImageRef.kt`
    - Sealed class with `Bytes` implementation
    - No Android dependencies
    - Backwards-compatible alias in
      `core-models/src/main/java/com/scanium/app/core/image/ImageRef.kt`

- **NormalizedRect**: Portable bounding box with 0-1 coordinates
    - Location: `core-models/src/main/java/com/scanium/app/model/NormalizedRect.kt`
    - Pure Kotlin data class with geometry helpers (`clampToUnit()`, `area`, etc.)
    - No Android dependencies
    - Backwards-compatible alias in
      `core-models/src/main/java/com/scanium/app/core/geometry/NormalizedRect.kt`

- **Logger**: Platform-agnostic logging interface
    - Location: `core-tracking/src/main/java/com/scanium/app/tracking/Logger.kt`
    - Interface with debug/info/warn/error methods
    - No Android dependencies
    - Includes no-op implementation for platforms without logging

**Remaining Work**: None

---

### 3. Core data models migrated to portable types

**Status**: ⚠️ **IN_PROGRESS** (95% complete)

**Evidence**:

- ✅ **DetectionResult** (`core-models/src/main/java/com/scanium/app/ml/DetectionResult.kt:16`)
    - Uses `NormalizedRect` for bounding boxes
    - No Android dependencies

- ✅ **RawDetection** (`core-models/src/main/java/com/scanium/app/ml/RawDetection.kt:17`)
    - Uses `NormalizedRect` for bounding boxes
    - Uses `ImageRef` for thumbnails
    - No Android dependencies

- ✅ **ObjectCandidate** (
  `core-tracking/src/main/java/com/scanium/app/tracking/ObjectCandidate.kt:27`)
    - Uses `NormalizedRect` for bounding boxes
    - Uses `ImageRef` for thumbnails
    - No Android dependencies

- ⚠️ **ScannedItem** (`core-models/src/main/java/com/scanium/app/items/ScannedItem.kt:3`)
    - Uses `ImageRef` for thumbnails ✅
    - Uses `NormalizedRect` for bounding boxes ✅
    - **BUT** still imports `android.net.Uri` at line 3 ❌
    - Fields `fullImageUri: Uri?` at line 45 is the remaining Android dependency

**Remaining Work**:

- Remove `android.net.Uri` dependency from `ScannedItem`
- Replace `fullImageUri: Uri?` with `fullImagePath: String?` (already present as fallback)
- Update callers to use path-based storage instead of Uri

**Blocker**: None (can be addressed independently)

---

### 4. Platform adapter layer established

**Status**: ✅ **DONE**

**Evidence**:

- **ImageAdapters** (
  `android-platform-adapters/src/main/java/com/scanium/android/platform/adapters/ImageAdapters.kt`)
    - `Bitmap.toImageRefJpeg(quality)` - Android Bitmap → portable ImageRef
    - `ImageRef.Bytes.toBitmap()` - portable ImageRef → Android Bitmap
    - Used at module boundaries when interfacing with ML Kit and Compose UI

- **RectAdapters** (
  `android-platform-adapters/src/main/java/com/scanium/android/platform/adapters/RectAdapters.kt`)
    - `RectF.toNormalizedRect(frameWidth, frameHeight)` - Android RectF → portable NormalizedRect
    - `Rect.toNormalizedRect(frameWidth, frameHeight)` - Android Rect → portable NormalizedRect
    - `NormalizedRect.toRectF(frameWidth, frameHeight)` - portable NormalizedRect → Android RectF
    - `NormalizedRect.toRect(frameWidth, frameHeight)` - portable NormalizedRect → Android Rect
    - Includes dimension validation and clamping

**Remaining Work**: None

---

### 5. Core modules are Android-free

**Status**: ⚠️ **IN_PROGRESS** (98% complete)

**Evidence**:

- ✅ **core-tracking module**
    - Zero `android.*` imports (verified via grep)
    - Zero `androidx.*` imports (verified via grep)
    - Uses Android library plugin (pending KMP conversion to `commonMain`)
    - Build file has no Android dependencies beyond stdlib

- ⚠️ **core-models module**
    - **One** `android.*` import: `android.net.Uri` in `ScannedItem.kt:3`
    - Zero `androidx.*` imports (verified via grep)
    - Uses Android library plugin (pending KMP conversion to `commonMain`)
    - Build file has no Android dependencies beyond stdlib

**Remaining Work**:

- Remove `android.net.Uri` import from
  `core-models/src/main/java/com/scanium/app/items/ScannedItem.kt`
- This is the **same blocker** as criterion #3 above

**Blocker**: None (can be addressed independently)

---

## Overall Phase 1 Status

**Phase Status**: ⚠️ **IN_PROGRESS** (95% complete)

### Summary

- **3 of 5 criteria**: ✅ DONE (Multi-module structure, Portable types, Platform adapters)
- **2 of 5 criteria**: ⚠️ IN_PROGRESS (Core data models, Android-free modules)
- **Blockers**: None

### Single Remaining Issue

Both incomplete criteria share the **same root cause**:

- `android.net.Uri` dependency in
  `core-models/src/main/java/com/scanium/app/items/ScannedItem.kt:3,45`

### Next Action

To achieve **Phase 1 complete (all 5 criteria DONE)**:

1. Remove `android.net.Uri` import from `ScannedItem.kt`
2. Remove `fullImageUri: Uri?` field (line 45)
3. Migrate all callers to use `fullImagePath: String?` instead
4. Re-verify with grep that `core-models` has zero Android imports

### Phase 2 Preview

Once Phase 1 is complete, Phase 2 (KMP Module Conversion) involves:

1. Convert `:core-models` to KMP with `commonMain/androidMain`
2. Convert `:core-tracking` to KMP with `commonMain/androidMain`
3. Convert `:core-domainpack` to KMP with `commonMain/androidMain`
4. Implement platform actuals (AndroidLogger, IOSLogger)
5. Create iOS app target with SwiftUI

---

## Verification Commands

```bash
# Verify module structure
grep "^include" settings.gradle.kts

# Check for Android imports in core modules
rg "^import android\." core-models/
rg "^import android\." core-tracking/
rg "^import androidx\." core-models/
rg "^import androidx\." core-tracking/

# Verify portable types exist
ls -1 core-models/src/main/java/com/scanium/app/model/
ls -1 core-tracking/src/main/java/com/scanium/app/tracking/Logger.kt

# Verify adapter layer exists
ls -1 android-platform-adapters/src/main/java/com/scanium/android/platform/adapters/
```

---

## Notes

- **CI Status**: GitHub Actions builds APK on every push to main (
  `.github/workflows/android-debug-apk.yml`)
- **Test Coverage**: 175+ tests passing (110 tracking, 61 domain pack, 4+ eBay)
- **Build Validation**: `./gradlew assembleDebug` succeeds with all modules
- **Documentation**: See `CLAUDE.md`, `docs/kmp-migration/PLAN.md`, `docs/kmp-migration/TARGETS.md`
