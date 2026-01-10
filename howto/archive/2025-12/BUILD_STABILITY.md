> Archived on 2025-12-20: superseded by docs/INDEX.md.
***REMOVED*** Build Stability Rules and Guardrails

**Status:** Phase 4 - Build Stability + iOS Prep
**Last Updated:** 2025-12-18
**Goal:** Ensure consistent, stable builds that never break on main branch

---

***REMOVED******REMOVED*** Build Configuration Standards

***REMOVED******REMOVED******REMOVED*** Java Toolchain: Java 17 (LTS)

**Requirement:** All Kotlin modules MUST use Java 17 toolchain.

**Verification:**
```bash
***REMOVED*** Check Java version
java -version
***REMOVED*** Should show: openjdk version "17.x.x"

***REMOVED*** Verify Gradle uses Java 17
./gradlew -version
***REMOVED*** Should show: JVM: 17.x.x
```

**Configuration (enforced):**

```kotlin
// androidApp/build.gradle.kts
kotlin {
    jvmToolchain(17)
}

android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

**Rationale:**
- Android Gradle Plugin 8.5.0 requires Java 17
- Kotlin 2.0.0 fully supports Java 17
- Long-term support (LTS) version with stability guarantees
- Consistent across CI and local development

---

***REMOVED******REMOVED******REMOVED*** Version Lock Table

**Critical:** These versions MUST remain in sync across all modules.

| Dependency | Version | Upgrade Policy |
|------------|---------|----------------|
| **Android Gradle Plugin (AGP)** | 8.5.0 | Minor updates only (8.5.x), review breaking changes |
| **Kotlin** | 2.0.0 | Patch updates only (2.0.x), major updates require ADR |
| **Compose Compiler** | 2.0.0 | Must match Kotlin version exactly |
| **Kotlin Multiplatform** | 2.0.0 | Must match Kotlin version exactly |
| **Java Toolchain** | 17 (LTS) | No upgrades until Android requires Java 21 |
| **compileSdk** | 34 | Update with new Android releases (annual) |
| **minSdk** | 24 (Android 7.0) | No changes (wide device coverage) |
| **targetSdk** | 34 | Update annually with new Android releases |

**Verification Command:**
```bash
./gradlew dependencies --configuration releaseRuntimeClasspath | grep -E "(kotlin|agp|compose)"
```

---

***REMOVED******REMOVED******REMOVED*** Gradle Plugin Stability Rules

***REMOVED******REMOVED******REMOVED******REMOVED*** ✅ Approved Plugins (Stable)

```kotlin
// build.gradle.kts (root)
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("com.android.library") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
}

// androidApp/build.gradle.kts
plugins {
    id("com.google.devtools.ksp") version "2.0.0-1.0.24"  // Kotlin Symbol Processing
    kotlin("plugin.serialization") version "2.0.0"        // JSON serialization
    id("org.cyclonedx.bom") version "1.8.2"               // SBOM generation (SEC-002)
    id("org.owasp.dependencycheck") version "10.0.4"      // CVE scanning (SEC-003)
}
```

***REMOVED******REMOVED******REMOVED******REMOVED*** ❌ Forbidden Plugins (Experimental/Unstable)

- ❌ `kotlin("plugin.parcelize")` - Use kotlinx.serialization instead
- ❌ Experimental Compose plugins (alpha/beta versions)
- ❌ Unstable KMP plugins (preview versions)
- ❌ Any plugin marked as "incubating" in Gradle

**Rationale:** Experimental plugins break CI frequently and may be removed in future versions.

---

***REMOVED******REMOVED*** Build Guardrails (Enforced)

***REMOVED******REMOVED******REMOVED*** 1. Module Dependency Guard

**Rule:** No module can depend on `:androidApp` (prevents circular dependencies)

**Enforcement:**
```kotlin
// build.gradle.kts (root)
subprojects {
    if (path != ":androidApp") {
        configurations.configureEach {
            withDependencies {
                filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                    .firstOrNull { it.dependencyProject.path == ":androidApp" }
                    ?.let {
                        throw GradleException("$path must not depend on :androidApp")
                    }
            }
        }
    }
}
```

**Why:** Presentation layer (androidApp) is the integration point. Other modules provide building blocks.

---

***REMOVED******REMOVED******REMOVED*** 2. Portable Modules Check

**Rule:** `core-models` and `core-tracking` must have NO Android imports

**Enforcement:**
```bash
./gradlew checkPortableModules
```

**What it checks:**
- No `import android.graphics.*`
- No `import android.util.*`
- No `import androidx.*`

**Fails build if violations found.**

**Extend to domain package:**
```kotlin
// build.gradle.kts (root)
tasks.register("checkDomainPackage") {
    description = "Validates domain package has no Android imports"
    group = "verification"

    doLast {
        val domainDir = file("androidApp/src/main/java/com/scanium/domain")
        if (!domainDir.exists()) {
            println("⚠ Domain package not yet created, skipping check")
            return@doLast
        }

        val forbiddenImports = listOf("import android.", "import androidx.")
        val violations = mutableListOf<String>()

        fileTree(domainDir) {
            include("**/*.kt")
        }.forEach { file ->
            file.readLines().forEachIndexed { lineNum, line ->
                forbiddenImports.forEach { forbidden ->
                    if (line.trim().startsWith(forbidden)) {
                        violations.add("${file.name}:${lineNum + 1}: $line")
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Domain package must be platform-agnostic.\n" +
                "Found Android imports:\n  ${violations.joinToString("\n  ")}\n" +
                "See docs/PACKAGE_BOUNDARIES.md for rules."
            )
        }

        println("✓ Domain package is platform-agnostic")
    }
}
```

**Add to check task:**
```kotlin
tasks.named("check") {
    dependsOn("checkPortableModules")
    dependsOn("checkDomainPackage")
}
```

---

***REMOVED******REMOVED******REMOVED*** 3. Build Reproducibility

**Rule:** Builds must be reproducible (same inputs → same outputs)

**Requirements:**
- No timestamp injection in BuildConfig
- No random values in generated code
- Deterministic ProGuard/R8 mappings
- Locked dependency versions (no dynamic `+` versions)

**Verification:**
```bash
***REMOVED*** Build twice, compare APKs
./gradlew clean assembleDebug
cp androidApp/build/outputs/apk/debug/androidApp-debug.apk build1.apk

./gradlew clean assembleDebug
cp androidApp/build/outputs/apk/debug/androidApp-debug.apk build2.apk

***REMOVED*** Should be byte-identical (excluding signatures)
diff <(unzip -p build1.apk classes.dex | md5) \
     <(unzip -p build2.apk classes.dex | md5)
```

---

***REMOVED******REMOVED*** Build Verification Commands

***REMOVED******REMOVED******REMOVED*** Environment Requirements

**⚠️ Container/Docker Limitation:**
Commands marked with 🏗️ require the **Android SDK** and will **fail in container environments** (e.g., Claude Code, Docker without Android SDK). Use these alternatives:

- **Container-friendly:** `./gradlew prePushJvmCheck` (runs JVM-only tests for shared modules)
- **Full validation:** Run on **workstation** with Android Studio or **CI runners** (GitHub Actions)

See `hooks/README.md` for JVM-only pre-push validation setup.

---

***REMOVED******REMOVED******REMOVED*** Essential Checks (Must Pass)

```bash
***REMOVED*** 1. Clean build 🏗️ (requires Android SDK)
./gradlew clean assembleDebug
***REMOVED*** ✅ Must complete without errors
***REMOVED*** ⚠️ Container: Use GitHub Actions artifact instead (see docs/DEV_GUIDE.md)

***REMOVED*** 2. Unit tests 🏗️ (requires Android SDK for androidApp module)
./gradlew test
***REMOVED*** ✅ All tests must pass (no ignored tests on main branch)
***REMOVED*** ✅ Container-friendly alternative: ./gradlew prePushJvmCheck (shared modules only)

***REMOVED*** 3. Portability checks ✅ (container-friendly)
./gradlew checkPortableModules checkDomainPackage
***REMOVED*** ✅ No Android imports in portable code
***REMOVED*** ✅ Works in containers (no Android SDK required)

***REMOVED*** 4. Lint checks 🏗️ (requires Android SDK)
./gradlew lint
***REMOVED*** ⚠️ Warnings allowed, but no critical/fatal errors
***REMOVED*** ⚠️ Container: Skip or run in CI

***REMOVED*** 5. Security scans (slow, run in CI) ✅ (container-friendly)
./gradlew dependencyCheckAnalyze
***REMOVED*** ⚠️ May report vulnerabilities, review and accept/fix
```

***REMOVED******REMOVED******REMOVED*** Full Verification Suite

**🏗️ Requires Android SDK** (run on workstation or CI, not in containers):

```bash
***REMOVED***!/bin/bash
***REMOVED*** scripts/verify-build.sh

set -e

echo "🔍 Running full build verification..."

echo "1. Clean build..."
./gradlew clean assembleDebug

echo "2. Unit tests..."
./gradlew test

echo "3. Portability checks..."
./gradlew checkPortableModules checkDomainPackage

echo "4. Lint..."
./gradlew lint

echo "5. ProGuard check (release build)..."
./gradlew assembleRelease

echo "✅ All checks passed!"
```

**Make executable:**
```bash
chmod +x scripts/verify-build.sh
```

**Run before every PR (workstation only):**
```bash
./scripts/verify-build.sh
```

**Container alternative (JVM-only):**
```bash
./gradlew prePushJvmCheck
```

---

***REMOVED******REMOVED*** Testing Strategy

***REMOVED******REMOVED******REMOVED*** Philosophy: Tests Must Not Block Builds

**Core Principle:** Tests validate correctness but must not prevent developers from building and running the app locally or in CI.

**Requirements:**
- ✅ All tests must be **optional** for `assembleDebug` and `assembleRelease`
- ✅ Tests run separately via `./gradlew test` (can fail without blocking APK generation)
- ✅ Mock implementations available for offline/hermetic builds
- ✅ No network dependencies in unit tests (use mocks for cloud services)

---

***REMOVED******REMOVED******REMOVED*** Test Types and Scope

***REMOVED******REMOVED******REMOVED******REMOVED*** 1. JVM Unit Tests (Primary Testing Layer)

**What:** Pure Kotlin logic tests running on JVM (no Android emulator/device needed)

**Coverage:**
- Domain logic (use cases, business rules)
- Data transformations (aggregation, mapping)
- Repository implementations (with mocked dependencies)
- Price estimation logic
- Category mapping logic

**Location:**
```
androidApp/src/test/java/com/scanium/
core-tracking/src/test/java/com/scanium/
```

**Example:**
```kotlin
// androidApp/src/test/java/com/scanium/domain/repository/MockClassifierTest.kt
class MockClassifierTest {

    @Test
    fun `mock classifier returns deterministic results`() = runTest {
        val classifier = MockClassifier()
        val image = ImageRef.fromTestResource("sofa.jpg")

        val result = classifier.classifyItem(
            thumbnail = image,
            hint = "furniture"
        )

        assertTrue(result.isSuccess)
        assertEquals("furniture_sofa", result.getOrNull()?.domainCategoryId)
        assertEquals(0.85f, result.getOrNull()?.confidence)
    }

    @Test
    fun `noop classifier always returns UNKNOWN`() = runTest {
        val classifier = NoopClassifier()
        val result = classifier.classifyItem(ImageRef.empty())

        assertTrue(result.isSuccess)
        assertEquals("unknown", result.getOrNull()?.domainCategoryId)
        assertEquals(ClassificationSource.FALLBACK, result.getOrNull()?.source)
    }
}
```

**Run:**
```bash
./gradlew test
***REMOVED*** Or specific module:
./gradlew :androidApp:testDebugUnitTest
```

**Benefits:**
- Fast execution (no emulator startup)
- Run in CI without Android SDK
- Easy to parallelize
- Hermetic (no network, no file system dependencies)

---

***REMOVED******REMOVED******REMOVED******REMOVED*** 2. Instrumented Tests (Optional, Not Required for CI)

**What:** Tests running on Android emulator or physical device

**Coverage:**
- UI integration tests (Compose UI, navigation)
- CameraX integration
- Android-specific platform code
- End-to-end flows (scan → classify → display)

**Location:**
```
androidApp/src/androidTest/java/com/scanium/
```

**Run:**
```bash
***REMOVED*** Requires emulator or connected device
./gradlew connectedAndroidTest
```

**When to Use:**
- Manual QA before release
- Nightly builds (not on every PR)
- Debugging platform-specific issues
- Performance profiling

**Not Required For:**
- `assembleDebug` to succeed
- CI checks on PRs (too slow, flaky)
- Local development (optional)

---

***REMOVED******REMOVED******REMOVED*** Mock Implementations for Offline Builds

***REMOVED******REMOVED******REMOVED******REMOVED*** NoopClassifier (Always Available)

**Purpose:** Fallback classifier that always returns UNKNOWN category

**Use Cases:**
- Offline builds (no backend available)
- CI environments without network access
- Testing aggregation logic without classification
- Graceful degradation when cloud service unavailable

**Implementation:**
```kotlin
// androidApp/src/main/java/com/scanium/domain/repository/impl/NoopClassifier.kt
package com.scanium.domain.repository.impl

import com.scanium.domain.repository.ClassificationResult
import com.scanium.domain.repository.ClassificationSource
import com.scanium.domain.repository.ItemClassifier
import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.model.ImageRef

/**
 * No-op classifier for offline/testing scenarios.
 * Always returns UNKNOWN category with low confidence.
 */
class NoopClassifier : ItemClassifier {

    override suspend fun classifyItem(
        thumbnail: ImageRef,
        hint: String?,
        domainPackId: String
    ): Result<ClassificationResult> {
        return Result.success(
            ClassificationResult(
                domainCategoryId = "unknown",
                attributes = emptyMap(),
                confidence = 0.0f,
                source = ClassificationSource.FALLBACK,
                label = "Unknown Item",
                itemCategory = ItemCategory.UNKNOWN,
                latencyMs = 0
            )
        )
    }

    override suspend fun isAvailable(): Boolean = true
}
```

---

***REMOVED******REMOVED******REMOVED******REMOVED*** MockClassifier (Deterministic Test Results)

**Purpose:** Returns predictable results based on hints for testing

**Use Cases:**
- Unit tests for domain logic
- UI development (consistent test data)
- Demo mode (show different categories)

**Implementation:**
```kotlin
// androidApp/src/test/java/com/scanium/domain/repository/impl/MockClassifier.kt
package com.scanium.domain.repository.impl

import com.scanium.domain.repository.ClassificationResult
import com.scanium.domain.repository.ClassificationSource
import com.scanium.domain.repository.ItemClassifier
import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.model.ImageRef

/**
 * Mock classifier with deterministic results for testing.
 * Returns predefined categories based on hint parameter.
 */
class MockClassifier : ItemClassifier {

    private val mockResults = mapOf(
        "furniture" to ClassificationResult(
            domainCategoryId = "furniture_sofa",
            attributes = mapOf("color" to "brown", "material" to "leather"),
            confidence = 0.85f,
            source = ClassificationSource.CLOUD,
            label = "Sofa",
            itemCategory = ItemCategory.HOME_GOOD,
            latencyMs = 50
        ),
        "electronics" to ClassificationResult(
            domainCategoryId = "electronics_laptop",
            attributes = mapOf("brand" to "Apple", "condition" to "good"),
            confidence = 0.92f,
            source = ClassificationSource.CLOUD,
            label = "Laptop",
            itemCategory = ItemCategory.ELECTRONIC,
            latencyMs = 45
        ),
        "clothing" to ClassificationResult(
            domainCategoryId = "clothing_jacket",
            attributes = mapOf("size" to "M", "material" to "leather"),
            confidence = 0.78f,
            source = ClassificationSource.CLOUD,
            label = "Jacket",
            itemCategory = ItemCategory.CLOTHING,
            latencyMs = 60
        )
    )

    override suspend fun classifyItem(
        thumbnail: ImageRef,
        hint: String?,
        domainPackId: String
    ): Result<ClassificationResult> {
        // Match hint to mock result, default to furniture
        val result = hint?.let { h ->
            mockResults.entries.firstOrNull { (key, _) ->
                h.contains(key, ignoreCase = true)
            }?.value
        } ?: mockResults["furniture"]!!

        return Result.success(result)
    }

    override suspend fun isAvailable(): Boolean = true
}
```

---

***REMOVED******REMOVED******REMOVED******REMOVED*** InMemoryCategoryEngine (Portable Test Data)

**Purpose:** Test category mapping without domain pack files

**Implementation:**
```kotlin
// androidApp/src/test/java/com/scanium/domain/repository/impl/InMemoryCategoryEngine.kt
package com.scanium.domain.repository.impl

import com.scanium.domain.repository.CategoryEngine
import com.scanium.domain.repository.CategoryMapping
import com.scanium.shared.core.models.ml.ItemCategory

/**
 * In-memory category engine with hardcoded mappings for testing.
 */
class InMemoryCategoryEngine : CategoryEngine {

    private val mappings = mapOf(
        "furniture_sofa" to CategoryMapping(
            domainCategoryId = "furniture_sofa",
            itemCategory = ItemCategory.HOME_GOOD,
            displayName = "Sofa",
            tags = listOf("couch", "seating", "living room"),
            priority = 10
        ),
        "electronics_laptop" to CategoryMapping(
            domainCategoryId = "electronics_laptop",
            itemCategory = ItemCategory.ELECTRONIC,
            displayName = "Laptop",
            tags = listOf("computer", "notebook", "pc"),
            priority = 20
        ),
        "unknown" to CategoryMapping(
            domainCategoryId = "unknown",
            itemCategory = ItemCategory.UNKNOWN,
            displayName = "Unknown Item",
            priority = 0
        )
    )

    override suspend fun mapCategory(domainCategoryId: String): CategoryMapping {
        return mappings[domainCategoryId]
            ?: mappings["unknown"]!!
    }

    override suspend fun getAllCategories(): List<CategoryMapping> {
        return mappings.values.sortedByDescending { it.priority }
    }

    override suspend fun searchCategories(query: String): List<CategoryMapping> {
        return mappings.values
            .filter { it.matches(query) }
            .sortedByDescending { it.priority }
    }
}
```

---

***REMOVED******REMOVED******REMOVED*** Test Configuration in Gradle

**Keep tests decoupled from build:**

```kotlin
// androidApp/build.gradle.kts

android {
    // Tests are NOT required for assembleDebug/assembleRelease
    testOptions {
        unitTests {
            isIncludeAndroidResources = false
            isReturnDefaultValues = true
        }
    }
}

// Run tests separately
tasks.named("check") {
    // Check includes test, but doesn't block assembleDebug
    dependsOn("test")
    dependsOn("checkPortableModules")
    dependsOn("checkDomainPackage")
}
```

**Test dependencies:**
```kotlin
dependencies {
    // Unit testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("app.cash.turbine:turbine:1.0.0") // Flow testing

    // Instrumented testing (optional)
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

---

***REMOVED******REMOVED******REMOVED*** Running Tests Locally

**Essential (fast, run frequently):**
```bash
***REMOVED*** Unit tests only (< 30 seconds)
./gradlew test

***REMOVED*** With portable checks
./gradlew test checkPortableModules checkDomainPackage
```

**Optional (slow, run before release):**
```bash
***REMOVED*** Instrumented tests (requires emulator, 5-10 minutes)
./gradlew connectedAndroidTest

***REMOVED*** Full verification suite
./scripts/verify-build.sh
```

---

***REMOVED******REMOVED******REMOVED*** CI Test Strategy

**On every PR (fast checks):**
```yaml
***REMOVED*** .github/workflows/pr-check.yml
***REMOVED*** Note: CI runners have Android SDK, so full test suite works
- name: Run unit tests
  run: ./gradlew test --no-daemon

- name: Check portability
  run: ./gradlew checkPortableModules checkDomainPackage

***REMOVED*** Upload test reports if failed
- name: Upload test results
  if: failure()
  uses: actions/upload-artifact@v3
  with:
    name: test-results
    path: |
      **/build/test-results/
      **/build/reports/tests/
```

**Container environments (JVM-only validation):**
```bash
***REMOVED*** For developers in Claude Code or Docker without Android SDK
./gradlew prePushJvmCheck

***REMOVED*** Or install git pre-push hook (see hooks/README.md)
./hooks/install-hooks.sh
```

**Nightly (comprehensive checks):**
```yaml
***REMOVED*** .github/workflows/nightly.yml
- name: Run instrumented tests
  uses: reactivecircus/android-emulator-runner@v2
  with:
    api-level: 34
    script: ./gradlew connectedAndroidTest

- name: Security scan
  run: ./gradlew dependencyCheckAnalyze
```

---

***REMOVED******REMOVED******REMOVED*** Success Metrics

**Unit Test Coverage Goals:**
- Domain logic: 80%+ coverage
- Repository implementations: 70%+ coverage
- Aggregation logic: 90%+ coverage (critical path)

**Test Execution Time Targets:**
| Test Type | Target | Maximum |
|-----------|--------|---------|
| Unit tests (all modules) | < 30 sec | 1 min |
| Single module tests | < 10 sec | 20 sec |
| Instrumented tests | < 5 min | 10 min |

**Measure coverage:**
```bash
./gradlew testDebugUnitTestCoverage
***REMOVED*** Report: androidApp/build/reports/coverage/test/debug/index.html
```

---

***REMOVED******REMOVED*** CI/CD Requirements

***REMOVED******REMOVED******REMOVED*** GitHub Actions Workflow

**Minimum checks on every PR:**

```yaml
name: Build Verification

on:
  pull_request:
    branches: [ main ]
  push:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Validate Gradle wrapper
        uses: gradle/wrapper-validation-action@v1

      - name: Build with Gradle
        run: ./gradlew assembleDebug --no-daemon

      - name: Run tests
        run: ./gradlew test --no-daemon

      - name: Check portability
        run: ./gradlew checkPortableModules checkDomainPackage

      - name: Upload build artifacts
        if: failure()
        uses: actions/upload-artifact@v3
        with:
          name: build-reports
          path: |
            **/build/reports/
            **/build/test-results/
```

**Security scans (weekly):**
```yaml
name: Security Scan

on:
  schedule:
    - cron: '0 2 * * 1'  ***REMOVED*** Monday 2 AM UTC
  workflow_dispatch:

jobs:
  security:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: OWASP Dependency Check
        run: ./gradlew dependencyCheckAnalyze

      - name: Upload SARIF
        uses: github/codeql-action/upload-sarif@v2
        with:
          sarif_file: build/reports/dependency-check-report.sarif
```

---

***REMOVED******REMOVED*** Dependency Management

***REMOVED******REMOVED******REMOVED*** Version Catalog (Recommended)

**Create `gradle/libs.versions.toml`:**

```toml
[versions]
agp = "8.5.0"
kotlin = "2.0.0"
compose-bom = "2023.10.01"
camerax = "1.3.1"
mlkit = "17.0.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version = "1.12.0" }
androidx-lifecycle-runtime = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version = "2.7.0" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
camerax-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
mlkit-object-detection = { group = "com.google.mlkit", name = "object-detection", version.ref = "mlkit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

**Usage:**
```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.camerax.core)
}
```

**Benefits:**
- Single source of truth for versions
- Easier to update dependencies
- Type-safe accessors
- IDE autocomplete support

---

***REMOVED******REMOVED*** Build Performance Optimization

***REMOVED******REMOVED******REMOVED*** 1. Gradle Daemon

**Enable in `gradle.properties`:**
```properties
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configureondemand=true
```

***REMOVED******REMOVED******REMOVED*** 2. Build Cache

**Local cache (developer machines):**
```properties
org.gradle.caching=true
```

**Remote cache (CI, optional):**
```properties
***REMOVED*** gradle.properties (CI only)
org.gradle.caching=true
org.gradle.cache.remote.url=https://your-build-cache.com
org.gradle.cache.remote.push=true
```

***REMOVED******REMOVED******REMOVED*** 3. Kotlin Incremental Compilation

**Enabled by default in Kotlin 2.0**

Verify in `gradle.properties`:
```properties
kotlin.incremental=true
kotlin.incremental.java=true
```

***REMOVED******REMOVED******REMOVED*** 4. Compose Compiler Metrics (Debug only)

```kotlin
// androidApp/build.gradle.kts
kotlin {
    composeCompiler {
        if (project.hasProperty("enableComposeCompilerReports")) {
            metricsDestination = file("${layout.buildDirectory.get()}/compose_metrics")
            reportsDestination = file("${layout.buildDirectory.get()}/compose_reports")
        }
    }
}
```

**Run with:**
```bash
./gradlew assembleDebug -PenableComposeCompilerReports=true
```

---

***REMOVED******REMOVED*** Troubleshooting Build Issues

***REMOVED******REMOVED******REMOVED*** Common Problems

***REMOVED******REMOVED******REMOVED******REMOVED*** 1. "Compilation error: Argument type mismatch"

**Cause:** Kotlin/Java version mismatch
**Fix:**
```bash
***REMOVED*** Verify Java 17
java -version

***REMOVED*** Update toolchain in build.gradle.kts
kotlin {
    jvmToolchain(17)
}
```

***REMOVED******REMOVED******REMOVED******REMOVED*** 2. "Android resource linking failed"

**Cause:** Resource conflicts or missing resources
**Fix:**
```bash
***REMOVED*** Clean and rebuild
./gradlew clean assembleDebug

***REMOVED*** Check for duplicate resources
find androidApp/src/main/res -type f -name "*.xml" | sort | uniq -d
```

***REMOVED******REMOVED******REMOVED******REMOVED*** 3. "Execution failed for task ':checkPortableModules'"

**Cause:** Android imports in portable code
**Fix:**
```bash
***REMOVED*** Find violations
grep -r "import android" core-models/ core-tracking/

***REMOVED*** Replace with KMP-compatible types
***REMOVED*** android.graphics.Bitmap → ImageRef
***REMOVED*** android.util.Log → Logger interface
```

***REMOVED******REMOVED******REMOVED******REMOVED*** 4. "KSP: Symbol processing failed"

**Cause:** KSP version incompatible with Kotlin version
**Fix:**
```kotlin
// Ensure KSP version matches Kotlin
// Kotlin 2.0.0 → KSP 2.0.0-1.0.24
id("com.google.devtools.ksp") version "2.0.0-1.0.24"
```

---

***REMOVED******REMOVED*** Build Failure Policy

***REMOVED******REMOVED******REMOVED*** On Main Branch

**Zero tolerance:** Main branch must ALWAYS build successfully.

**If build breaks:**
1. **Immediate revert:** Revert the breaking commit
2. **Fix forward:** Open PR with fix, ensure all checks pass
3. **Root cause analysis:** Document why break happened
4. **Prevention:** Add check to prevent similar breaks

**Never acceptable:**
- "Builds fine on my machine" (use clean build in CI environment)
- "Will fix later" (fix immediately or revert)
- Ignoring failing tests (fix or remove test)

***REMOVED******REMOVED******REMOVED*** On Feature Branches

**Pre-merge requirements:**
- ✅ `./gradlew assembleDebug` succeeds
- ✅ `./gradlew test` all pass
- ✅ `./gradlew checkPortableModules` passes
- ✅ No new lint warnings (critical/fatal)
- ✅ Code review approved
- ✅ CI checks green

**PR blocked if any check fails.**

---

***REMOVED******REMOVED*** Build Time Targets

**Goals (measured on CI):**

| Build Type | Target | Maximum |
|------------|--------|---------|
| Clean build | < 3 min | 5 min |
| Incremental build | < 30 sec | 1 min |
| Unit tests | < 2 min | 3 min |
| Instrumented tests | < 5 min | 10 min |
| Full verification | < 10 min | 15 min |

**Monitor via CI:**
```yaml
- name: Build with timing
  run: |
    START=$(date +%s)
    ./gradlew assembleDebug
    END=$(date +%s)
    echo "Build time: $((END - START)) seconds"
```

---

***REMOVED******REMOVED*** Version Bump Policy

***REMOVED******REMOVED******REMOVED*** Patch Updates (Auto-approve)

**Safe to update without review:**
- Kotlin 2.0.0 → 2.0.1 (bug fixes only)
- AGP 8.5.0 → 8.5.1 (bug fixes only)
- Library patches (1.2.3 → 1.2.4)

**Requirements:**
- Run full verification suite
- Check release notes for breaking changes
- Update version lock table in this document

***REMOVED******REMOVED******REMOVED*** Minor Updates (Review required)

**Require ADR or review:**
- Kotlin 2.0.x → 2.1.0 (new features)
- AGP 8.5.x → 8.6.0 (new features)
- Library minor (1.2.x → 1.3.0)

**Requirements:**
- Read migration guide
- Test all affected features
- Update documentation
- Run full test suite

***REMOVED******REMOVED******REMOVED*** Major Updates (ADR required)

**Must create ADR:**
- Kotlin 2.x → 3.0
- AGP 8.x → 9.0
- Java 17 → 21
- Library major (1.x → 2.0)

**Requirements:**
- Impact analysis
- Migration plan
- Compatibility testing
- Team approval

---

***REMOVED******REMOVED*** Success Criteria

***REMOVED******REMOVED******REMOVED*** Phase 4 Complete When:

- [x] Java 17 toolchain verified
- [x] Version lock table documented
- [x] Build guardrails enforced (module dependency guard, portability checks)
- [x] CI/CD requirements documented
- [ ] Verification script created
- [ ] Build time targets measured
- [ ] iOS readiness documented (next section)

***REMOVED******REMOVED******REMOVED*** Ongoing:

- Monitor build times weekly
- Review dependency updates monthly
- Security scans weekly (automated)
- Version lock table kept current

---

***REMOVED******REMOVED*** References

- [Gradle Build Performance](https://docs.gradle.org/current/userguide/performance.html)
- [Android Gradle Plugin Release Notes](https://developer.android.com/build/releases/gradle-plugin)
- [Kotlin Release Notes](https://kotlinlang.org/docs/releases.html)
- [OWASP Dependency-Check](https://owasp.org/www-project-dependency-check/)
