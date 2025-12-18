# Architecture Refactoring Plan

**Status:** Proposed
**Last Updated:** 2025-12-18
**Related ADRs:** ADR-001, ADR-002, ADR-003
**Goal:** Migrate from current single-module architecture to target modular, KMP-ready, cloud-classification-enabled architecture

---

## Overview

This document provides a step-by-step plan to refactor Scanium's architecture from the current state (Phase 0) to the target state defined in `docs/ARCHITECTURE.md`. The plan is designed for:

✅ **Android First** - Ship working APK immediately; iOS follows
✅ **Incremental** - Small, safe steps with build verification after each
✅ **Non-Blocking** - Existing development can continue during refactoring
✅ **Testable** - Each step has clear success criteria and validation commands

---

## Phases Summary

| Phase | Name | Duration | Parallelizable? | Risk Level | Build Breaking? |
|-------|------|----------|-----------------|------------|-----------------|
| **0** | Repo Discovery | ✅ COMPLETE | N/A | None | No |
| **1** | Architecture Design | ✅ COMPLETE | N/A | None | No |
| **2** | Foundation Setup | 2-3 days | **Yes** | Low | No |
| **3** | Shared Domain Creation | 3-4 days | Partial | Low | No |
| **4** | Use Case Extraction | 4-5 days | **Yes** | Medium | No |
| **5** | Cloud Classification Setup | 3-4 days | **Yes** | Medium | No |
| **6** | Config & Observability | 2-3 days | **Yes** | Low | No |
| **7** | Cleanup & Validation | 2-3 days | Partial | Low | No |

**Total Estimated Time:** 16-22 days (parallelizable to ~10-14 days with multiple developers)

---

## Phase 2: Foundation Setup

**Goal:** Create new modules and validation infrastructure without changing existing code.

**Duration:** 2-3 days
**Parallelizable:** Yes (can split into 3 tasks)
**Risk:** Low (additive only, no changes to existing code)

### Task 2.1: Create `shared:core-domain` Module

**Location:** `shared/core-domain/`

**Steps:**
1. Create directory structure:
   ```bash
   mkdir -p shared/core-domain/src/commonMain/kotlin/com/scanium/core/domain/{model,usecase,repository}
   mkdir -p shared/core-domain/src/commonTest/kotlin/com/scanium/core/domain
   mkdir -p shared/core-domain/src/androidMain/kotlin/com/scanium/core/domain
   mkdir -p shared/core-domain/src/iosMain/kotlin/com/scanium/core/domain
   ```

2. Create `shared/core-domain/build.gradle.kts`:
   ```kotlin
   plugins {
       kotlin("multiplatform")
   }

   kotlin {
       androidTarget {
           compilations.all {
               kotlinOptions {
                   jvmTarget = "17"
               }
           }
       }

       iosX64()
       iosArm64()
       iosSimulatorArm64()

       sourceSets {
           val commonMain by getting {
               dependencies {
                   api(project(":shared:core-models"))
                   api(project(":shared:core-tracking"))
               }
           }
           val commonTest by getting {
               dependencies {
                   implementation(kotlin("test"))
               }
           }
       }
   }

   android {
       namespace = "com.scanium.core.domain"
       compileSdk = 34
       defaultConfig {
           minSdk = 24
       }
       compileOptions {
           sourceCompatibility = JavaVersion.VERSION_17
           targetCompatibility = JavaVersion.VERSION_17
       }
   }
   ```

3. Update `settings.gradle.kts`:
   ```kotlin
   include(
       ":androidApp",
       ":core-models",
       ":core-tracking",
       ":core-domainpack",
       ":core-scan",
       ":core-contracts",
       ":android-ml-mlkit",
       ":android-camera-camerax",
       ":android-platform-adapters",
       ":shared:core-models",
       ":shared:core-tracking",
       ":shared:core-domain"  // NEW
   )

   project(":shared:core-domain").projectDir = file("shared/core-domain")
   ```

4. Add placeholder file to verify module works:
   ```kotlin
   // shared/core-domain/src/commonMain/kotlin/com/scanium/core/domain/Placeholder.kt
   package com.scanium.core.domain

   /**
    * Placeholder file to verify module compilation.
    * Will be replaced with actual use cases in Phase 3-4.
    */
   object Placeholder
   ```

**Verification:**
```bash
./gradlew :shared:core-domain:build
./gradlew :shared:core-domain:testDebugUnitTest
```

---

### Task 2.2: Create `shared:core-data` Module

**Location:** `shared/core-data/`

**Steps:**
1. Create directory structure:
   ```bash
   mkdir -p shared/core-data/src/commonMain/kotlin/com/scanium/core/data/{repository,mapper,model}
   mkdir -p shared/core-data/src/commonTest/kotlin/com/scanium/core/data
   mkdir -p shared/core-data/src/androidMain/kotlin/com/scanium/core/data
   mkdir -p shared/core-data/src/iosMain/kotlin/com/scanium/core/data
   ```

2. Create `shared/core-data/build.gradle.kts`:
   ```kotlin
   plugins {
       kotlin("multiplatform")
       kotlin("plugin.serialization")
   }

   kotlin {
       androidTarget {
           compilations.all {
               kotlinOptions {
                   jvmTarget = "17"
               }
           }
       }

       iosX64()
       iosArm64()
       iosSimulatorArm64()

       sourceSets {
           val commonMain by getting {
               dependencies {
                   api(project(":shared:core-models"))
                   api(project(":shared:core-domain"))
                   implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
               }
           }
           val commonTest by getting {
               dependencies {
                   implementation(kotlin("test"))
                   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
               }
           }
       }
   }

   android {
       namespace = "com.scanium.core.data"
       compileSdk = 34
       defaultConfig {
           minSdk = 24
       }
       compileOptions {
           sourceCompatibility = JavaVersion.VERSION_17
           targetCompatibility = JavaVersion.VERSION_17
       }
   }
   ```

3. Update `settings.gradle.kts`:
   ```kotlin
   include(
       // ... existing modules
       ":shared:core-data"  // NEW
   )

   project(":shared:core-data").projectDir = file("shared/core-data")
   ```

4. Add placeholder:
   ```kotlin
   // shared/core-data/src/commonMain/kotlin/com/scanium/core/data/Placeholder.kt
   package com.scanium.core.data

   object Placeholder
   ```

**Verification:**
```bash
./gradlew :shared:core-data:build
```

---

### Task 2.3: Implement Module Dependency Validation

**Location:** `build.gradle.kts` (root)

**Steps:**
1. Add `checkModuleDependencies` task to root `build.gradle.kts` (see ADR-003 for full implementation)

2. Add to existing verification tasks:
   ```kotlin
   tasks.named("check") {
       dependsOn("checkPortableModules")
       dependsOn("checkModuleDependencies")
   }
   ```

3. Update GitHub Actions workflow:
   ```yaml
   # .github/workflows/android-debug-apk.yml

   - name: Validate Architecture
     run: |
       ./gradlew checkPortableModules
       ./gradlew checkModuleDependencies
   ```

**Verification:**
```bash
./gradlew checkModuleDependencies
# Should pass (no violations in current state)

./gradlew assembleDebug
# Should still work
```

---

### Phase 2 Checklist

- [ ] `shared:core-domain` module created and compiles
- [ ] `shared:core-data` module created and compiles
- [ ] `checkModuleDependencies` task implemented
- [ ] All existing tests still pass: `./gradlew test`
- [ ] Build still works: `./gradlew assembleDebug`
- [ ] No new warnings or errors

**Rollback Plan:** If Phase 2 breaks anything, simply remove new modules from `settings.gradle.kts` and delete directories.

---

## Phase 3: Shared Domain Creation

**Goal:** Define core domain models and repository interfaces in shared modules.

**Duration:** 3-4 days
**Parallelizable:** Partial (models first, then use cases)
**Risk:** Low (creates new code, doesn't modify existing)

### Task 3.1: Define Domain Models

**Location:** `shared/core-domain/src/commonMain/kotlin/com/scanium/core/domain/model/`

**New Models to Create:**

1. **DomainCategoryId.kt**
   ```kotlin
   package com.scanium.core.domain.model

   /**
    * Fine-grained category ID for items.
    * Examples: "furniture_sofa", "electronics_laptop", "fashion_sneakers"
    */
   data class DomainCategoryId(val value: String) {
       init {
           require(value.isNotBlank()) { "Category ID cannot be blank" }
           require(value.matches(Regex("[a-z_]+"))) {
               "Category ID must be lowercase with underscores only"
           }
       }

       companion object {
           val UNKNOWN = DomainCategoryId("unknown")
       }
   }
   ```

2. **ItemAttributes.kt**
   ```kotlin
   package com.scanium.core.domain.model

   /**
    * Extracted attributes for an item (from cloud classification).
    */
   data class ItemAttributes(
       val brand: String? = null,
       val color: String? = null,
       val material: String? = null,
       val condition: String? = null,  // "new", "used_like_new", "used_good", etc.
       val size: String? = null,
       val model: String? = null,
       val year: Int? = null
   ) {
       fun isEmpty(): Boolean = listOfNotNull(
           brand, color, material, condition, size, model, year
       ).isEmpty()
   }
   ```

3. **PriceEstimate.kt**
   ```kotlin
   package com.scanium.core.domain.model

   /**
    * Price range estimate with confidence.
    */
   data class PriceEstimate(
       val minPrice: Double,
       val maxPrice: Double,
       val currency: String,
       val confidence: Float  // 0.0 to 1.0
   ) {
       init {
           require(minPrice >= 0) { "Min price cannot be negative" }
           require(maxPrice >= minPrice) { "Max price must be >= min price" }
           require(confidence in 0f..1f) { "Confidence must be 0-1" }
       }

       val averagePrice: Double get() = (minPrice + maxPrice) / 2.0
   }
   ```

4. **ClassificationResult.kt**
   ```kotlin
   package com.scanium.core.domain.model

   enum class ClassificationSource {
       CLOUD,          // Google Vision API
       ON_DEVICE,      // ML Kit (fallback)
       FALLBACK        // Default/unknown
   }

   data class ClassificationResult(
       val domainCategoryId: DomainCategoryId,
       val attributes: ItemAttributes,
       val confidence: Float,
       val source: ClassificationSource,
       val latencyMs: Long? = null
   )
   ```

**Add Tests:**
```kotlin
// shared/core-domain/src/commonTest/kotlin/

class DomainCategoryIdTest {
    @Test
    fun `should create valid category ID`() {
        val categoryId = DomainCategoryId("furniture_sofa")
        assertEquals("furniture_sofa", categoryId.value)
    }

    @Test
    fun `should reject invalid category ID`() {
        assertFailsWith<IllegalArgumentException> {
            DomainCategoryId("Invalid-ID")
        }
    }
}

class ItemAttributesTest {
    @Test
    fun `isEmpty should return true when no attributes set`() {
        val attributes = ItemAttributes()
        assertTrue(attributes.isEmpty())
    }

    @Test
    fun `isEmpty should return false when attributes set`() {
        val attributes = ItemAttributes(brand = "IKEA", color = "brown")
        assertFalse(attributes.isEmpty())
    }
}

class PriceEstimateTest {
    @Test
    fun `should calculate average price`() {
        val estimate = PriceEstimate(
            minPrice = 100.0,
            maxPrice = 200.0,
            currency = "EUR",
            confidence = 0.8f
        )
        assertEquals(150.0, estimate.averagePrice, 0.01)
    }

    @Test
    fun `should reject invalid price range`() {
        assertFailsWith<IllegalArgumentException> {
            PriceEstimate(
                minPrice = 200.0,
                maxPrice = 100.0,  // Invalid: max < min
                currency = "EUR",
                confidence = 0.8f
            )
        }
    }
}
```

**Verification:**
```bash
./gradlew :shared:core-domain:testDebugUnitTest
```

---

### Task 3.2: Define Repository Interfaces

**Location:** `shared/core-domain/src/commonMain/kotlin/com/scanium/core/domain/repository/`

1. **CloudClassificationRepository.kt**
   ```kotlin
   package com.scanium.core.domain.repository

   import com.scanium.core.domain.model.ClassificationResult
   import com.scanium.core.models.image.ImageRef

   /**
    * Repository for cloud-based item classification.
    * Platform-specific implementations will call backend API.
    */
   interface CloudClassificationRepository {
       /**
        * Classify an item using cloud vision API.
        *
        * @param thumbnail Cropped item image (max 200x200 JPEG)
        * @param coarseLabel Optional hint from on-device detector (e.g., "Home good")
        * @param domainPackId Target domain pack ID (e.g., "home_resale")
        * @return Result with ClassificationResult or error
        */
       suspend fun classifyItem(
           thumbnail: ImageRef,
           coarseLabel: String?,
           domainPackId: String
       ): Result<ClassificationResult>

       /**
        * Check if cloud classification is currently available.
        * Returns false if no network, backend down, etc.
        */
       suspend fun isAvailable(): Boolean
   }
   ```

2. **PricingRepository.kt**
   ```kotlin
   package com.scanium.core.domain.repository

   import com.scanium.core.domain.model.DomainCategoryId
   import com.scanium.core.domain.model.ItemAttributes
   import com.scanium.core.domain.model.PriceEstimate

   /**
    * Repository for price estimation.
    * Initial implementation uses local rules; future can call pricing API.
    */
   interface PricingRepository {
       /**
        * Estimate price range for an item based on category and attributes.
        */
       suspend fun estimatePrice(
           categoryId: DomainCategoryId,
           attributes: ItemAttributes
       ): PriceEstimate
   }
   ```

**Verification:** Interfaces compile, no implementation needed yet.

---

### Phase 3 Checklist

- [ ] All domain models created with tests
- [ ] Repository interfaces defined
- [ ] Tests pass: `./gradlew :shared:core-domain:test`
- [ ] Build works: `./gradlew assembleDebug`
- [ ] No Android/iOS types in shared:core-domain

---

## Phase 4: Use Case Extraction

**Goal:** Extract business logic from ViewModels into shared use cases.

**Duration:** 4-5 days
**Parallelizable:** Yes (each use case independent)
**Risk:** Medium (modifies existing code)

### Task 4.1: Extract `EstimatePriceUseCase` (Simplest First)

**Location:** `shared/core-domain/src/commonMain/kotlin/com/scanium/core/domain/usecase/`

**Implementation:**
```kotlin
package com.scanium.core.domain.usecase

import com.scanium.core.domain.model.DomainCategoryId
import com.scanium.core.domain.model.ItemAttributes
import com.scanium.core.domain.model.PriceEstimate
import com.scanium.core.domain.repository.PricingRepository

/**
 * Use case for estimating item price based on category and attributes.
 */
class EstimatePriceUseCase(
    private val repository: PricingRepository
) {
    suspend operator fun invoke(
        categoryId: DomainCategoryId,
        attributes: ItemAttributes
    ): PriceEstimate {
        return repository.estimatePrice(categoryId, attributes)
    }
}
```

**Create Simple Repository Implementation:**
```kotlin
// androidApp/src/main/java/com/scanium/app/data/

class LocalPricingRepository : PricingRepository {
    override suspend fun estimatePrice(
        categoryId: DomainCategoryId,
        attributes: ItemAttributes
    ): PriceEstimate {
        // Port existing logic from PricingEngine.kt
        return when {
            categoryId.value.startsWith("furniture_") -> PriceEstimate(
                minPrice = 50.0,
                maxPrice = 500.0,
                currency = "EUR",
                confidence = 0.6f
            )
            categoryId.value.startsWith("electronics_") -> PriceEstimate(
                minPrice = 100.0,
                maxPrice = 1000.0,
                currency = "EUR",
                confidence = 0.7f
            )
            // ... other categories
            else -> PriceEstimate(
                minPrice = 10.0,
                maxPrice = 100.0,
                currency = "EUR",
                confidence = 0.3f
            )
        }
    }
}
```

**Update ItemsViewModel:**
```kotlin
// androidApp/src/main/java/com/scanium/app/items/ItemsViewModel.kt

class ItemsViewModel(
    // ... existing parameters
    private val estimatePriceUseCase: EstimatePriceUseCase  // NEW
) : ViewModel() {

    // Replace direct PricingEngine calls with use case
    suspend fun updateItemPrice(item: ScannedItem) {
        val domainCategoryId = DomainCategoryId(item.category.name.lowercase())
        val attributes = ItemAttributes()  // Extract from item if available

        val priceEstimate = estimatePriceUseCase(domainCategoryId, attributes)

        // Update item with new price
        val updatedItem = item.copy(
            priceRange = PriceRange(
                min = priceEstimate.minPrice,
                max = priceEstimate.maxPrice,
                currency = priceEstimate.currency
            )
        )
        // ... update state
    }
}
```

**Add Tests:**
```kotlin
// shared/core-domain/src/commonTest/kotlin/

class EstimatePriceUseCaseTest {
    @Test
    fun `should estimate price via repository`() = runTest {
        val mockRepository = object : PricingRepository {
            override suspend fun estimatePrice(
                categoryId: DomainCategoryId,
                attributes: ItemAttributes
            ): PriceEstimate {
                return PriceEstimate(100.0, 200.0, "EUR", 0.8f)
            }
        }

        val useCase = EstimatePriceUseCase(mockRepository)
        val result = useCase(
            DomainCategoryId("furniture_sofa"),
            ItemAttributes(color = "brown")
        )

        assertEquals(100.0, result.minPrice, 0.01)
        assertEquals(200.0, result.maxPrice, 0.01)
    }
}
```

**Verification:**
```bash
./gradlew :shared:core-domain:test
./gradlew assembleDebug
./gradlew test  # All tests including androidApp
```

---

### Task 4.2: Extract `AggregateDetectionsUseCase`

**Implementation:**
```kotlin
package com.scanium.core.domain.usecase

import com.scanium.core.models.ml.RawDetection
import com.scanium.core.tracking.ObjectTracker
import com.scanium.core.tracking.DetectionInfo

/**
 * Use case for aggregating raw detections into stable items using ObjectTracker.
 */
class AggregateDetectionsUseCase(
    private val tracker: ObjectTracker
) {
    /**
     * Process a frame of detections and return newly confirmed stable items.
     */
    fun processFrame(detections: List<DetectionInfo>): List<ObjectCandidate> {
        return tracker.processFrame(detections)
    }

    /**
     * Reset tracker state (e.g., when starting new scan session).
     */
    fun reset() {
        tracker.reset()
    }

    /**
     * Get current tracker statistics.
     */
    fun getStats(): TrackerStats {
        return tracker.getStats()
    }
}
```

**Update ItemsViewModel:**
```kotlin
class ItemsViewModel(
    private val aggregateUseCase: AggregateDetectionsUseCase  // NEW
) : ViewModel() {

    fun processDetections(detections: List<DetectionInfo>) {
        val confirmedCandidates = aggregateUseCase.processFrame(detections)

        // Convert candidates to ScannedItems
        confirmedCandidates.forEach { candidate ->
            val scannedItem = objectDetectorClient.candidateToScannedItem(candidate)
            addItem(scannedItem)
        }
    }
}
```

**Verification:**
```bash
./gradlew test
./gradlew assembleDebug
# Run app, verify detection still works
```

---

### Task 4.3: Extract `ClassifyStableItemUseCase`

**Implementation:**
```kotlin
package com.scanium.core.domain.usecase

import com.scanium.core.domain.model.ClassificationResult
import com.scanium.core.domain.model.ClassificationSource
import com.scanium.core.domain.repository.CloudClassificationRepository
import com.scanium.core.models.image.ImageRef

/**
 * Use case for classifying stable items using cloud or on-device classifiers.
 */
class ClassifyStableItemUseCase(
    private val cloudRepository: CloudClassificationRepository
) {
    /**
     * Classify an item, with fallback strategy:
     * 1. Try cloud classification
     * 2. If failed, return fallback result with coarse label
     */
    suspend operator fun invoke(
        thumbnail: ImageRef,
        coarseLabel: String?,
        domainPackId: String
    ): ClassificationResult {
        // Try cloud classification
        val cloudResult = cloudRepository.classifyItem(thumbnail, coarseLabel, domainPackId)

        return when {
            cloudResult.isSuccess -> cloudResult.getOrThrow()

            // Fallback: Use coarse label
            coarseLabel != null -> ClassificationResult(
                domainCategoryId = DomainCategoryId(coarseLabel.lowercase().replace(" ", "_")),
                attributes = ItemAttributes(),
                confidence = 0.3f,
                source = ClassificationSource.ON_DEVICE
            )

            // Last resort: Unknown
            else -> ClassificationResult(
                domainCategoryId = DomainCategoryId.UNKNOWN,
                attributes = ItemAttributes(),
                confidence = 0.1f,
                source = ClassificationSource.FALLBACK
            )
        }
    }
}
```

**Note:** Implementation of `CloudClassificationRepository` will be in Phase 5.

**Verification:**
```bash
./gradlew :shared:core-domain:test
```

---

### Phase 4 Checklist

- [ ] `EstimatePriceUseCase` extracted and tested
- [ ] `AggregateDetectionsUseCase` extracted and tested
- [ ] `ClassifyStableItemUseCase` extracted and tested
- [ ] ItemsViewModel refactored to use use cases
- [ ] All tests pass
- [ ] App still works (detection, aggregation, pricing)

---

## Phase 5: Cloud Classification Setup

**Goal:** Implement cloud classification via backend proxy.

**Duration:** 3-4 days
**Parallelizable:** Yes (can work on backend in parallel)
**Risk:** Medium (new feature, requires backend)

### Task 5.1: Define Backend API Contract

**Create API specification document:**

**Location:** `docs/api/CLASSIFICATION_API.md`

```markdown
# Classification API Specification

## Endpoint

`POST /api/v1/classify`

## Authentication

Bearer token in Authorization header:
```
Authorization: Bearer <JWT_TOKEN>
```

## Request

**Content-Type:** `multipart/form-data`

**Fields:**
- `image` (file): JPEG image, max 1MB
- `domainPackId` (string): Target domain pack (e.g., "home_resale")
- `hint` (string, optional): Coarse label from on-device detector

**Example:**
```bash
curl -X POST https://api.scanium.com/api/v1/classify \
  -H "Authorization: Bearer eyJhbGc..." \
  -F "image=@item.jpg" \
  -F "domainPackId=home_resale" \
  -F "hint=Home good"
```

## Response

**200 OK:**
```json
{
  "domainCategoryId": "furniture_sofa",
  "confidence": 0.87,
  "attributes": {
    "color": "brown",
    "material": "leather",
    "condition": "good"
  },
  "requestId": "req_abc123",
  "latencyMs": 234
}
```

**429 Too Many Requests:**
```json
{
  "error": "rate_limit_exceeded",
  "message": "Too many requests. Limit: 100/hour",
  "retryAfter": 3600
}
```

**503 Service Unavailable:**
```json
{
  "error": "service_unavailable",
  "message": "Classification service temporarily unavailable"
}
```
```

---

### Task 5.2: Implement Android Cloud Classification Client

**Location:** `androidApp/src/main/java/com/scanium/app/data/`

**Add Ktor HTTP client dependency:**
```kotlin
// androidApp/build.gradle.kts

dependencies {
    // ... existing dependencies

    // Ktor HTTP client for cloud classification
    implementation("io.ktor:ktor-client-android:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
}
```

**Implementation:**
```kotlin
package com.scanium.app.data

import android.util.Log
import com.scanium.core.domain.model.*
import com.scanium.core.domain.repository.CloudClassificationRepository
import com.scanium.core.models.image.ImageRef
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GoogleVisionClassifierAndroid(
    private val httpClient: HttpClient,
    private val apiBaseUrl: String,
    private val authTokenProvider: () -> String
) : CloudClassificationRepository {

    companion object {
        private const val TAG = "GoogleVisionClassifier"
    }

    override suspend fun classifyItem(
        thumbnail: ImageRef,
        coarseLabel: String?,
        domainPackId: String
    ): Result<ClassificationResult> {
        return try {
            Log.d(TAG, "Classifying item: domainPack=$domainPackId, hint=$coarseLabel")

            val response = httpClient.post("$apiBaseUrl/api/v1/classify") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${authTokenProvider()}")
                }
                setBody(MultiPartFormDataContent(
                    formData {
                        append("image", thumbnail.jpegBytes, Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=item.jpg")
                        })
                        append("domainPackId", domainPackId)
                        coarseLabel?.let { append("hint", it) }
                    }
                ))
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val apiResponse = response.body<ClassificationApiResponse>()
                    Result.success(apiResponse.toDomainModel())
                }
                HttpStatusCode.TooManyRequests -> {
                    Log.w(TAG, "Rate limit exceeded")
                    Result.failure(Exception("Rate limit exceeded"))
                }
                else -> {
                    Log.e(TAG, "Classification failed: ${response.status}")
                    Result.failure(Exception("Classification failed: ${response.status}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error classifying item", e)
            Result.failure(e)
        }
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            // TODO: Implement health check endpoint
            true
        } catch (e: Exception) {
            false
        }
    }
}

@Serializable
private data class ClassificationApiResponse(
    val domainCategoryId: String,
    val confidence: Float,
    val attributes: Map<String, String>,
    val requestId: String,
    val latencyMs: Long
) {
    fun toDomainModel(): ClassificationResult {
        return ClassificationResult(
            domainCategoryId = DomainCategoryId(domainCategoryId),
            attributes = ItemAttributes(
                brand = attributes["brand"],
                color = attributes["color"],
                material = attributes["material"],
                condition = attributes["condition"],
                size = attributes["size"]
            ),
            confidence = confidence,
            source = ClassificationSource.CLOUD,
            latencyMs = latencyMs
        )
    }
}
```

**Add Feature Flag:**
```kotlin
// androidApp/build.gradle.kts

android {
    buildTypes {
        debug {
            buildConfigField("Boolean", "CLOUD_CLASSIFICATION_ENABLED", "false")
        }
        release {
            buildConfigField("Boolean", "CLOUD_CLASSIFICATION_ENABLED", "true")
        }
    }
}
```

**Verification:**
```bash
./gradlew assembleDebug
# Cloud classification disabled in debug builds (no backend needed yet)
```

---

### Task 5.3: Wire Up Cloud Classification in ItemsViewModel

**Update DI setup:**
```kotlin
// androidApp/src/main/java/com/scanium/app/di/AppModule.kt

object AppModule {
    fun provideCloudClassificationRepository(
        context: Context,
        config: AppConfig
    ): CloudClassificationRepository {
        if (!BuildConfig.CLOUD_CLASSIFICATION_ENABLED) {
            return NoopCloudClassificationRepository()  // Returns fallback results
        }

        val httpClient = HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        return GoogleVisionClassifierAndroid(
            httpClient = httpClient,
            apiBaseUrl = BuildConfig.API_BASE_URL,
            authTokenProvider = { "mock_token" }  // TODO: Real auth in Phase 6
        )
    }

    fun provideClassifyUseCase(
        repository: CloudClassificationRepository
    ): ClassifyStableItemUseCase {
        return ClassifyStableItemUseCase(repository)
    }
}
```

**Update ItemsViewModel:**
```kotlin
class ItemsViewModel(
    // ... existing parameters
    private val classifyUseCase: ClassifyStableItemUseCase
) : ViewModel() {

    fun addItem(item: ScannedItem) {
        // Add item immediately (responsive UI)
        val itemId = item.id
        _items.update { it + item }

        // Classify asynchronously (doesn't block UI)
        viewModelScope.launch {
            item.thumbnailRef?.let { thumbnail ->
                val result = classifyUseCase(
                    thumbnail = thumbnail,
                    coarseLabel = item.labelText,
                    domainPackId = "home_resale"
                )

                // Update item with classification result
                _items.update { items ->
                    items.map { existingItem ->
                        if (existingItem.id == itemId) {
                            existingItem.copy(
                                domainCategoryId = result.domainCategoryId.value,
                                attributes = result.attributes,
                                classificationConfidence = result.confidence
                            )
                        } else {
                            existingItem
                        }
                    }
                }
            }
        }
    }
}
```

**Verification:**
```bash
./gradlew test
./gradlew assembleDebug
# Run app, verify items are added immediately (not blocked by classification)
```

---

### Phase 5 Checklist

- [ ] API specification documented
- [ ] Android cloud classification client implemented
- [ ] Feature flag added (disabled by default)
- [ ] ItemsViewModel updated for async classification
- [ ] Tests pass (cloud classification mocked)
- [ ] App works with cloud classification disabled

**Backend Development (Parallel Track):**
- [ ] Backend `/api/v1/classify` endpoint implemented (separate task)
- [ ] Google Vision API integration tested
- [ ] Rate limiting and auth implemented
- [ ] Deployed to staging environment

---

## Phase 6: Configuration & Observability

**Goal:** Add configuration layer and observability (logging, metrics).

**Duration:** 2-3 days
**Parallelizable:** Yes (config and logging independent)
**Risk:** Low (additive features)

### Task 6.1: Create `shared:core-config` Module

**Implementation:**
```kotlin
// shared/core-config/src/commonMain/kotlin/

data class AppConfig(
    val api: ApiConfig,
    val features: FeatureFlags,
    val logging: LoggingConfig
)

data class ApiConfig(
    val baseUrl: String,
    val classificationProvider: String,
    val timeoutMs: Long = 10_000,
    val retryAttempts: Int = 2
)

data class FeatureFlags(
    val cloudClassificationEnabled: Boolean,
    val domainPackEnabled: Boolean,
    val debugOverlaysEnabled: Boolean
)

data class LoggingConfig(
    val level: LogLevel,
    val enableAnalytics: Boolean,
    val enableCrashReporting: Boolean
)

enum class LogLevel {
    ERROR, WARNING, INFO, DEBUG, VERBOSE
}
```

**Platform Implementation (Android):**
```kotlin
// androidApp/src/main/java/com/scanium/app/di/ConfigProvider.kt

object ConfigProvider {
    fun provideAppConfig(): AppConfig {
        return AppConfig(
            api = ApiConfig(
                baseUrl = BuildConfig.API_BASE_URL,
                classificationProvider = "google_vision",
                timeoutMs = 10_000
            ),
            features = FeatureFlags(
                cloudClassificationEnabled = BuildConfig.CLOUD_CLASSIFICATION_ENABLED,
                domainPackEnabled = true,
                debugOverlaysEnabled = BuildConfig.DEBUG
            ),
            logging = LoggingConfig(
                level = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO,
                enableAnalytics = !BuildConfig.DEBUG,
                enableCrashReporting = true
            )
        )
    }
}
```

---

### Task 6.2: Add Observability Layer

**Create logging interface:**
```kotlin
// shared:core-observability/src/commonMain/kotlin/

interface AppLogger {
    fun logClassificationRequest(itemId: String, source: ClassificationSource, timestamp: Long)
    fun logClassificationResult(itemId: String, categoryId: String, confidence: Float, latencyMs: Long)
    fun logClassificationError(itemId: String, error: Throwable, retryAttempt: Int)
    fun logScanSession(sessionId: String, itemCount: Int, classifiedCount: Int, durationMs: Long)
}

data class ClassificationMetrics(
    val totalRequests: Int,
    val successCount: Int,
    val failureCount: Int,
    val averageLatencyMs: Long,
    val errorRate: Float
)
```

**Android implementation:**
```kotlin
// androidApp/src/main/java/com/scanium/app/observability/

class AndroidLogger(
    private val config: LoggingConfig
) : AppLogger {
    override fun logClassificationResult(
        itemId: String,
        categoryId: String,
        confidence: Float,
        latencyMs: Long
    ) {
        if (config.level >= LogLevel.INFO) {
            Log.i(TAG, "Classification: item=$itemId, category=$categoryId, " +
                      "confidence=$confidence, latency=${latencyMs}ms")
        }
    }

    // ... other methods
}
```

**Verification:**
```bash
./gradlew assembleDebug
# Run app in debug mode, verify logs appear
```

---

### Phase 6 Checklist

- [ ] `shared:core-config` module created
- [ ] `shared:core-observability` module created
- [ ] Android config provider implemented
- [ ] Android logger implemented
- [ ] Logging integrated into classification flow
- [ ] Debug overlays show classification metrics

---

## Phase 7: Cleanup & Validation

**Goal:** Remove deprecated code, validate architecture, prepare for production.

**Duration:** 2-3 days
**Parallelizable:** Partial
**Risk:** Low

### Task 7.1: Remove Deprecated Modules

**Steps:**
1. Update androidApp to depend directly on `shared:*` modules
2. Remove `:core-models` and `:core-tracking` Android wrappers
3. Update imports across codebase

**Verification:**
```bash
./gradlew assembleDebug
./gradlew test
```

---

### Task 7.2: Run Full Architecture Validation

**Checklist:**
```bash
# 1. Module dependency rules
./gradlew checkModuleDependencies

# 2. Portable modules check
./gradlew checkPortableModules

# 3. All tests pass
./gradlew test
./gradlew connectedAndroidTest  # Requires device

# 4. Build succeeds
./gradlew assembleDebug
./gradlew assembleRelease

# 5. No warnings
./gradlew build --warning-mode=all
```

---

### Task 7.3: Documentation Updates

**Update:**
- [ ] `docs/ARCHITECTURE.md` - Mark target architecture as current
- [ ] `README.md` - Update module descriptions
- [ ] `docs/DEV_GUIDE.md` - Add use case examples
- [ ] `docs/KMP_GUIDE.md` - Document KMP patterns
- [ ] `docs/TESTING.md` - Update test strategy

---

### Phase 7 Checklist

- [ ] Deprecated modules removed
- [ ] All validation tasks pass
- [ ] Documentation updated
- [ ] No build warnings
- [ ] APK size compared (should be similar or smaller)

---

## Rollback Strategy

**If any phase fails catastrophically:**

1. **Git:** `git revert` commits from failed phase
2. **Modules:** Remove new modules from `settings.gradle.kts`
3. **Build:** `./gradlew clean assembleDebug`
4. **Verify:** Ensure app works as before

**Checkpoints:** After each phase, create git tag: `phase-X-complete`

---

## Success Criteria

**Overall Success:**
- ✅ `./gradlew assembleDebug` succeeds
- ✅ All 171+ tests pass (and new tests added)
- ✅ App functions identically to Phase 0 (no regressions)
- ✅ Cloud classification works (when backend available)
- ✅ 70% of business logic in shared modules
- ✅ Architecture validation tasks pass
- ✅ No Android types in `shared:*` modules
- ✅ Build time increase < 20%

**Metrics to Track:**
- Test count: 171+ → 200+
- Shared code percentage: ~40% → 70%
- Module count: 11 → 15
- Build time: Baseline → +10-15%

---

## Parallel Work Streams

While architecture refactoring is happening, other work can proceed:

**Can Continue:**
- ✅ UI improvements (Compose screens)
- ✅ Bug fixes in existing code
- ✅ Performance optimizations
- ✅ Documentation updates

**Should Pause:**
- ⚠️ Major feature additions to ItemsViewModel (will need refactoring)
- ⚠️ Changes to domain models (moving to shared modules)

---

## Communication Plan

**Weekly:**
- Status update: Which phase complete, blockers, next steps
- Demo: Show working build after each phase

**Daily:**
- Sync on any build breaks
- Coordinate parallel work

**Documentation:**
- Update this plan with actual durations and learnings
- Document any deviations from plan

---

## Post-Refactoring

**Next Steps (Not in this plan):**
- iOS development (use shared modules)
- Backend deployment and integration
- Real authentication (replace mock tokens)
- Production monitoring and alerting
- Performance optimization (caching, batch classification)

---

## Summary

This plan provides a safe, incremental path from current architecture to target architecture. Each phase:
- Has clear goals and verification steps
- Can be rolled back if needed
- Doesn't break existing functionality
- Moves toward target architecture

**Key Principles:**
1. **Android First** - Ship working APK at every phase
2. **Incremental** - Small, testable changes
3. **Verified** - Build and test after each step
4. **Documented** - Track progress and learnings

**Timeline:** 16-22 days sequential, ~10-14 days with parallelization

**Ready to start Phase 2!** 🚀
