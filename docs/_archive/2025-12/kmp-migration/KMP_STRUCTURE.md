***REMOVED*** KMP Structure

This document describes the target Kotlin Multiplatform (KMP) module layout for Scanium after the extraction of shared business logic from the Android-specific codebase.

***REMOVED******REMOVED*** Overview

The `/shared` directory will contain all platform-independent Kotlin Multiplatform modules. These modules will use the `commonMain` / `androidMain` / `iosMain` source set structure to share core business logic while allowing platform-specific implementations where needed.

***REMOVED******REMOVED*** Directory Structure

```
scanium/
├── shared/                          ***REMOVED*** KMP modules root
│   ├── core-models/                 ***REMOVED*** Platform-independent data models
│   ├── core-tracking/               ***REMOVED*** Tracking & aggregation logic
│   ├── core-domainpack/             ***REMOVED*** Domain Pack system (categories)
│   ├── core-scan/                   ***REMOVED*** Scan contracts & interfaces
│   └── core-contracts/              ***REMOVED*** Shared contracts & interfaces
│
├── androidApp/                      ***REMOVED*** Android app (Compose UI, navigation)
├── android-ml-mlkit/                ***REMOVED*** Android ML Kit wrappers
├── android-camera-camerax/          ***REMOVED*** Android CameraX wrappers
└── android-platform-adapters/       ***REMOVED*** Android type conversions
```

***REMOVED******REMOVED*** Module Descriptions

***REMOVED******REMOVED******REMOVED*** `/shared/core-models`
**Type**: KMP Library (`multiplatform` plugin)
**Purpose**: Platform-independent data models and types

**Contains**:
- `ImageRef` - Platform-agnostic image reference (sealed class)
- `NormalizedRect` - Normalized bounding box (0-1 coordinates)
- `ItemCategory` - Enum mapping detection categories
- `ScanMode` - Enum for OBJECT_DETECTION | BARCODE | DOCUMENT_TEXT
- `ScannedItem` - Immutable item model (will remove Android `Uri` dependency)
- `DetectionResult` - Real-time detection result
- `RawDetection` - Raw ML detection data
- `ClassificationMode` - ON_DEVICE | CLOUD enum

**Dependencies**: None (pure Kotlin stdlib + Kotlinx Serialization)

**Source Sets**:
- `commonMain` - All models and types
- `androidMain` - Android-specific extensions if needed
- `iosMain` - iOS-specific extensions if needed

---

***REMOVED******REMOVED******REMOVED*** `/shared/core-tracking`
**Type**: KMP Library (`multiplatform` plugin)
**Purpose**: Object tracking and item aggregation logic

**Contains**:
- `ObjectTracker` - Multi-frame tracking with spatial matching
- `ObjectCandidate` - Candidate state during tracking
- `TrackerConfig` - Tunable thresholds
- `ItemAggregator` - Session-level similarity-based deduplication
- `AggregationPresets` - Configuration presets (REALTIME, etc.)
- `AggregatedItem` - Merged detection with metadata
- `Logger` - Platform-agnostic logging interface
- `DetectionInfo` - Input to tracker

**Dependencies**:
- `core-models`
- Kotlin Coroutines
- Kotlinx Serialization

**Source Sets**:
- `commonMain` - All tracking and aggregation logic
- `androidMain` - `AndroidLogger` implementation (wraps `android.util.Log`)
- `iosMain` - `IOSLogger` implementation (wraps `NSLog`/`os_log`)

**expect/actual**:
- `expect class Logger` (platform-specific logging)

---

***REMOVED******REMOVED******REMOVED*** `/shared/core-domainpack`
**Type**: KMP Library (`multiplatform` plugin)
**Purpose**: Domain Pack system for category taxonomy

**Contains**:
- `DomainPack` - Schema for 23 categories + 10 attributes
- `DomainCategory`, `DomainAttribute` - Category and attribute models
- `DomainPackRepository` - Repository interface
- `LocalDomainPackRepository` - JSON loader implementation
- `BasicCategoryEngine` - ML Kit label → DomainCategory matching
- `DomainPackProvider` - Singleton accessor

**Dependencies**:
- `core-models`
- Kotlinx Serialization

**Source Sets**:
- `commonMain` - All domain logic and repository interface
- `androidMain` - Android JSON resource loading from `res/raw/`
- `iosMain` - iOS bundle resource loading

**expect/actual**:
- `expect fun loadDomainPackJson(): String` (platform-specific resource loading)

---

***REMOVED******REMOVED******REMOVED*** `/shared/core-scan`
**Type**: KMP Library (`multiplatform` plugin)
**Purpose**: Scan-related contracts and interfaces (placeholder)

**Contains**:
- Future: Platform-independent scan contracts
- Future: Scan session management interfaces

**Dependencies**:
- `core-models`

**Source Sets**:
- `commonMain` - Scan contracts
- `androidMain` - Android-specific scan extensions
- `iosMain` - iOS-specific scan extensions

**Status**: Currently a placeholder module for future expansion.

---

***REMOVED******REMOVED******REMOVED*** `/shared/core-contracts`
**Type**: KMP Library (`multiplatform` plugin)
**Purpose**: Shared contracts and interfaces (placeholder)

**Contains**:
- Future: Platform-independent interfaces for camera, ML, etc.
- Future: Common protocol definitions

**Dependencies**: None (pure Kotlin stdlib)

**Source Sets**:
- `commonMain` - All contracts and interfaces

**Status**: Currently a placeholder module for future expansion.

---

***REMOVED******REMOVED*** Dependency Graph

```
┌─────────────────────────────────────────────────────────────┐
│                         :androidApp                          │
│  (Android UI, Navigation, CameraX, ML Kit integration)      │
└──────────┬──────────────────────────────────────────────────┘
           │
           ├─────────────────────┬─────────────────────┬───────────┐
           │                     │                     │           │
           ▼                     ▼                     ▼           ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│ android-ml-mlkit │  │android-camera-   │  │android-platform- │  │
│                  │  │    camerax       │  │    adapters      │  │
└──────────────────┘  └──────────────────┘  └─────────┬────────┘  │
                                                       │           │
                                                       ▼           │
                                            ┌──────────────────┐  │
                                            │ shared/          │  │
                                            │ core-models      │◀─┤
                                            └─────────┬────────┘  │
                                                      │           │
           ┌──────────────────────────────────────────┼───────────┘
           │                                          │
           ▼                                          │
┌──────────────────┐                                 │
│ shared/          │                                 │
│ core-tracking    │─────────────────────────────────┘
└─────────┬────────┘
          │
          │
          ▼
┌──────────────────┐
│ shared/          │
│ core-domainpack  │─────────────────────────────────┐
└─────────┬────────┘                                 │
          │                                          │
          └──────────────────────────────────────────┘
          ▲
          │
┌─────────┴────────┐      ┌──────────────────┐
│ shared/          │      │ shared/          │
│ core-scan        │      │ core-contracts   │
└──────────────────┘      └──────────────────┘
```

**Dependency Rules**:
1. **No circular dependencies** between shared modules
2. **Bottom-up dependencies only**: Lower layers cannot depend on higher layers
3. **Platform modules** (`:androidApp`, `:android-*`) can depend on shared modules
4. **Shared modules** cannot depend on platform-specific code (enforced by Gradle)
5. **`core-models`** has no dependencies (foundation layer)
6. **`core-tracking`** depends only on `core-models`
7. **`core-domainpack`** depends only on `core-models`

---

***REMOVED******REMOVED*** Source Set Structure (KMP Modules)

Each shared module will follow the standard KMP source set layout:

```
shared/core-models/
├── build.gradle.kts           ***REMOVED*** KMP configuration
└── src/
    ├── commonMain/
    │   └── kotlin/            ***REMOVED*** Shared Kotlin code
    ├── commonTest/
    │   └── kotlin/            ***REMOVED*** Shared tests
    ├── androidMain/
    │   └── kotlin/            ***REMOVED*** Android-specific implementations
    ├── androidUnitTest/
    │   └── kotlin/            ***REMOVED*** Android unit tests
    ├── iosMain/
    │   └── kotlin/            ***REMOVED*** iOS-specific implementations
    └── iosTest/
        └── kotlin/            ***REMOVED*** iOS tests
```

---

***REMOVED******REMOVED*** Build Configuration

***REMOVED******REMOVED******REMOVED*** Gradle Setup (Module-Level)

Example `shared/core-models/build.gradle.kts`:

```kotlin
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    androidTarget()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val androidMain by getting {
            dependencies {
                // Android-specific dependencies if needed
            }
        }

        val iosMain by creating {
            dependsOn(commonMain)
        }

        val iosX64Main by getting { dependsOn(iosMain) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
    }
}
```

***REMOVED******REMOVED******REMOVED*** Settings Gradle

The root `settings.gradle.kts` will include:

```kotlin
include(
    ":androidApp",
    ":app",  // Legacy resources only
    ":shared:core-models",
    ":shared:core-tracking",
    ":shared:core-domainpack",
    ":shared:core-scan",
    ":shared:core-contracts",
    ":android-ml-mlkit",
    ":android-camera-camerax",
    ":android-platform-adapters"
)
```

---

***REMOVED******REMOVED*** Migration Strategy

***REMOVED******REMOVED******REMOVED*** Phase 1: Structure Setup (Current)
✅ Create `/shared` directory structure
✅ Add placeholder modules
✅ Document target architecture
🚧 No code moved yet

***REMOVED******REMOVED******REMOVED*** Phase 2: Module Conversion
1. Convert `core-models` to KMP
   - Apply `multiplatform` plugin
   - Move code to `commonMain`
   - Remove Android dependencies (`Uri`, etc.)
   - Add `androidMain`/`iosMain` stubs

2. Convert `core-tracking` to KMP
   - Apply `multiplatform` plugin
   - Move code to `commonMain`
   - Implement `expect class Logger` with Android/iOS actuals
   - Update tests to `commonTest`

3. Convert `core-domainpack` to KMP
   - Apply `multiplatform` plugin
   - Move code to `commonMain`
   - Implement resource loading via expect/actual
   - Platform-specific JSON resource access

4. Platform Adapters
   - Keep `android-platform-adapters` as Android library
   - Create `ios-platform-adapters` for iOS-specific conversions

***REMOVED******REMOVED******REMOVED*** Phase 3: iOS Integration
1. Create `:iosApp` module with SwiftUI
2. Implement iOS platform providers (Camera, ML)
3. Wire up shared logic with iOS UI
4. Testing and validation

---

***REMOVED******REMOVED*** Non-Negotiables

1. **Android Must Remain Functional**: Every migration step must keep Android builds working
2. **No Breaking Changes**: Existing Android features must continue working throughout migration
3. **Platform Optimizations Allowed**: Each platform can use native optimizations (ML Kit vs Core ML)
4. **Shared Code Must Be Android-Free**: No `android.*`, `androidx.*`, CameraX, or ML Kit in `shared/*`
5. **CI Validation**: GitHub Actions must validate Android builds on every push
6. **Incremental Migration**: Move code module-by-module, not all at once

---

***REMOVED******REMOVED*** Testing Strategy

***REMOVED******REMOVED******REMOVED*** Shared Modules
- **Common Tests** (`commonTest`): Platform-independent unit tests
- **Android Tests** (`androidUnitTest`): Android-specific test cases
- **iOS Tests** (`iosTest`): iOS-specific test cases

***REMOVED******REMOVED******REMOVED*** Platform Modules
- **Android**: Existing test suite in `androidApp/src/test/` continues
- **iOS**: New test suite in `iosApp/src/test/` (future)

***REMOVED******REMOVED******REMOVED*** CI Pipeline
- Build all KMP modules for Android + iOS targets
- Run common tests on JVM
- Run Android tests on Android emulator
- Run iOS tests on macOS simulator (future)

---

***REMOVED******REMOVED*** References

- **Migration Plan**: `docs/kmp-migration/PLAN.md`
- **Phase 2 Plan**: `docs/kmp-migration/PHASE_2_PLAN.md`
- **Portability Status**: `docs/kmp-migration/PORTABILITY_STATUS.md`
- **Target Files**: `docs/kmp-migration/TARGETS.md`
- **Architecture**: `md/architecture/ARCHITECTURE.md`

---

***REMOVED******REMOVED*** Status

**Current State**: Phase 1 (Structure Setup)
**Last Updated**: 2025-12-17
**Next Steps**: Begin Phase 2 (Module Conversion)
