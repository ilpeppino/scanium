# Missing Unit Tests - Analysis & Recommendations

**Generated**: January 31, 2026  
**Branch**: main  
**Scope**: Android App, Shared Modules, Backend

---

## Executive Summary

This document identifies gaps in unit test coverage across the Scanium codebase and provides prioritized recommendations for improvement.

### Overall Test Coverage

| Module | Source Files | Test Files | Coverage Ratio |
|--------|-------------|------------|----------------|
| **Android App** | 411 | 130 | 31.6% |
| **Shared Modules** | 79 | 35 | 44.3% |
| **Backend** | 143 | 60 | 42.0% |
| **Total** | 633 | 225 | 35.5% |

### Key Findings

- **Critical Gap**: Billing module has **zero tests** despite handling revenue-critical logic
- **High Risk**: Camera detection pipeline (44 files, only 15 tests) - core scanning functionality
- **FTUE Untested**: First-time user experience (22 files, only 1 test) - onboarding critical path
- **Telemetry Missing**: 11 telemetry files with **no tests** - observability data quality at risk
- **ViewModel Gaps**: 23 ViewModels exist, only 6 have dedicated tests

---

## Android App - Critical Missing Tests

### 1. Billing Module (Priority: P0 - Critical)

**Status**: 6 source files, **0 tests**  
**Risk**: Revenue loss, entitlement bypass, subscription bugs

| File | Risk | Recommended Test Cases |
|------|------|------------------------|
| `BillingRepository.kt` | High | Entitlement state persistence, update/clear operations, DataStore error handling |
| `AndroidBillingProvider.kt` | High | Billing client connection, purchase flow, query purchases, consume purchases |
| `FakeBillingProvider.kt` | Medium | Fake provider contract, test mode behavior |
| `PaywallViewModel.kt` | High | Paywall display logic, purchase flow state, error handling |
| `PaywallScreen.kt` | Medium | UI composition, button states, purchase trigger |
| `BillingSkus.kt` | Low | SKU validation, in-app product mapping |

**Why Critical**: Billing bugs directly impact revenue. The BillingRepository has complex DataStore operations that could corrupt entitlement state.

**Example Test Priority**:
```kotlin
// P0: BillingRepository.kt
class BillingRepositoryTest {
    @Test fun `updateEntitlement persists correct state`()
    @Test fun `clearEntitlement resets to FREE tier`()
    @Test fun `purchase tokens are deduplicated`()
    @Test fun `corrupted preferences fall back to FREE`()
}
```

---

### 2. Camera Detection Pipeline (Priority: P0 - Critical)

**Status**: 44 source files, 15 tests  
**Coverage**: 34%  
**Risk**: Scanning failures, performance degradation, user experience issues

#### Missing Tests - High Priority

| File | Complexity | Missing Coverage |
|------|-----------|------------------|
| `DetectionRouter.kt` | High (538 lines) | Session lifecycle, adaptive throttling, detector routing, barcode deduplication |
| `CameraXManager.kt` | High | Camera lifecycle, resolution switching, ViewPort alignment |
| `AdaptiveThrottlePolicy.kt` | Medium | Processing time tracking, throttling activation |
| `DocumentCandidateDetector.kt` | Medium | Document candidate detection logic |
| `RoiDetectionFilter.kt` | Medium | ROI-based filtering |
| `LiveScanDiagnostics.kt` | Medium | Diagnostics collection |
| `ScanPipelineDiagnostics.kt` | Medium | Pipeline health tracking |

**DetectionRouter** is particularly concerning - it's the core orchestrator for detection routing with 538 lines of complex logic.

**Recommended Test Cases for DetectionRouter**:
```kotlin
class DetectionRouterTest {
    @Test fun `startSession resets all counters`()
    @Test fun `routeDetection returns correct detector for scan mode`()
    @Test fun `tryInvokeObjectDetection respects throttle interval`()
    @Test fun `processBarcodeResults deduplicates by value and format`()
    @Test fun `adaptive throttling multiplies base interval`()
    @Test fun `getStats calculates correct FPS`()
}
```

---

### 3. Telemetry & Observability (Priority: P1 - High)

**Status**: 11 source files, **0 tests**  
**Risk**: Data loss, incorrect metrics, silent failures

| File | Risk | Recommended Test Cases |
|------|------|------------------------|
| `MobileTelemetryClient.kt` | High | Event queuing, batching, retry logic, singleton initialization |
| `OtlpHttpExporter.kt` | High | HTTP export, exponential backoff, retry on 5xx, no retry on 4xx |
| `TraceContextInterceptor.kt` | Medium | Trace context propagation, header injection |
| `AndroidDefaultAttributesProvider.kt` | Medium | Attribute collection, PII filtering |
| `OtlpConfiguration.kt` | Low | Configuration parsing, validation |

**Why High Priority**: Telemetry is the production debugging backbone. If it silently fails, critical issues become uninvestigable.

**Example Test Priority**:
```kotlin
// P0: MobileTelemetryClient.kt
class MobileTelemetryClientTest {
    @Test fun `events are queued and batched correctly`()
    @Test fun `disabled flag prevents all exports`()
    @Test fun `retry logic respects exponential backoff`()
    @Test fun `singleton initialization is thread-safe`()
}
```

---

### 4. First-Time User Experience (FTUE) (Priority: P1 - High)

**Status**: 22 source files, 1 test  
**Coverage**: 4.5%  
**Risk**: Onboarding drop-off, confusing UX, feature discovery issues

#### Missing Tests - High Priority

| File | Complexity | Missing Coverage |
|------|-----------|------------------|
| `TourViewModel.kt` | Medium | Tour progression, step navigation, completion |
| `FtueRepository.kt` | Medium | FTU status persistence, completion tracking |
| `CameraFtueViewModel.kt` | Medium | Camera-specific FTUE flow |
| `CameraUiFtueViewModel.kt` | Medium | UI overlay targeting, anchor registry |
| `EditItemFtueViewModel.kt` | Medium | Edit screen FTUE flow |
| `ItemsListFtueViewModel.kt` | Medium | List screen FTUE flow |
| `SettingsFtueViewModel.kt` | Medium | Settings discovery flow |

**Why High Priority**: FTUE is the first interaction. Bugs here cause user churn and negative reviews.

---

### 5. Voice & Audio (Priority: P2 - Medium)

**Status**: 7 source files, **0 tests**  
**Risk**: Voice command failures, audio bugs, TTS errors

| File | Risk | Recommended Test Cases |
|------|------|------------------------|
| `VoiceController.kt` | Medium | Voice command recognition, state transitions |
| `VoiceStateMachine.kt` | Medium | State machine transitions, error handling |
| `TtsController.kt` | Medium | TTS playback, queueing, error recovery |
| `TtsManager.kt` | Low | TTS initialization, resource cleanup |

---

### 6. Networking & Security (Priority: P2 - Medium)

**Status**: 3 source files, 1 test  
**Coverage**: 33%

| File | Complexity | Missing Coverage |
|------|-----------|------------------|
| `AuthTokenInterceptor.kt` | Medium | Token refresh, header injection, auth errors |
| `DeviceIdProvider.kt` | Low | Device ID generation, persistence |
| `RequestSigner.kt` | ✓ Tested | - |

---

### 7. Platform & Adapters (Priority: P2 - Medium)

**Status**: 2 source files, **0 tests**  
**Coverage**: 0%

| File | Complexity | Missing Coverage |
|------|-----------|------------------|
| `PortableAdapters.kt` | Medium | Bitmap ↔ ImageRef, Rect ↔ NormalizedRect conversions |
| `ConnectivityObserver.kt` | Low | Network state detection, flow emission |

**Why Medium Priority**: Platform adapters are critical for shared module portability. Bugs here break the KMP contract.

---

### 8. Monitoring & Health Checks (Priority: P2 - Medium)

**Status**: 5 source files, 3 tests  
**Coverage**: 60%

| File | Complexity | Missing Coverage |
|------|-----------|------------------|
| `DevHealthMonitorWorker.kt` | High | Worker scheduling, health check execution |
| `DevHealthMonitorScheduler.kt` | Medium | WorkManager integration, scheduling logic |
| `DevHealthMonitorStateStore.kt` | Medium | State persistence, cleanup |

---

## Android ViewModels - Coverage Gap

### ViewModels Without Tests (17 missing)

| ViewModel | Priority | Reason |
|-----------|----------|--------|
| `CameraViewModel` | P1 | Core scanning flow state management |
| `ClassificationModeViewModel` | P2 | Classification settings |
| `DeveloperOptionsViewModel` | P2 | Dev tools, feature flags |
| `EditItemFtueViewModel` | P1 | Onboarding flow |
| `ItemsListFtueViewModel` | P1 | Onboarding flow |
| `CameraFtueViewModel` | P1 | Onboarding flow |
| `CameraUiFtueViewModel` | P1 | Onboarding flow |
| `SettingsFtueViewModel` | P1 | Onboarding flow |
| `TourViewModel` | P1 | Onboarding flow |
| `ListingGenerationViewModel` | P2 | Listing creation flow |
| `ListingViewModel` | P2 | Listing management |
| `PaywallViewModel` | P0 | Revenue-critical |
| `PostingAssistViewModel` | P2 | Marketplace posting |

**Why ViewModel Testing Matters**: ViewModels contain business logic, state management, and orchestration. Untested ViewModels are the most common source of production bugs.

---

## Shared Modules - Test Coverage

### Core Tracking (Priority: P0 - Critical)

**Status**: 46 source files, 16 tests  
**Coverage**: 35%

| Module | Files | Tests | Coverage |
|--------|-------|-------|----------|
| `ObjectTracker.kt` | - | ✓ Tested | Tracking logic |
| `ItemAggregator.kt` | - | ✓ Tested | Deduplication |
| `ScanGuidanceManager.kt` | - | ✓ Tested | Guidance |
| `DetectionInfo.kt` | - | ✓ Tested | Models |
| `SpatialTemporalMergePolicy.kt` | - | ✓ Tested | Merge policy |
| `TrackingPipelineIntegrationTest.kt` | - | ✓ Tested | Integration |

**Status**: Good coverage on core algorithms. Focus on edge cases and golden test expansion.

---

### Core Models (Priority: P1 - High)

**Status**: 9 source files, 9 tests  
**Coverage**: 100%

Excellent coverage. All models have dedicated tests.

---

### Core Export (Priority: P2 - Medium)

**Status**: 4 source files, 1 test  
**Coverage**: 25%

| File | Missing Coverage |
|------|------------------|
| Export models | CSV formatting, ZIP bundling |

---

### Diagnostics (Priority: P2 - Medium)

**Status**: 5 source files, 3 tests  
**Coverage**: 60%

| File | Missing Coverage |
|------|------------------|
| `DiagnosticsBuffer.kt` | Buffer overflow, rotation |
| `DefaultDiagnosticsPort.kt` | Port contract |

---

### Telemetry (Priority: P1 - High)

**Status**: 7 source files, 1 test  
**Coverage**: 14%

| File | Missing Coverage |
|------|------------------|
| `TelemetryEvent.kt` | Event serialization |
| `AttributeSanitizer.kt` | PII filtering |
| `TelemetryEventNaming.kt` | Naming validation |

---

### Android Platform Adapters (Priority: P2 - Medium)

**Status**: 2 source files, **0 tests**  
**Coverage**: 0%

| File | Missing Coverage |
|------|------------------|
| `BitmapToImageRef.kt` | JPEG compression, quality settings |
| `RectToNormalizedRect.kt` | Coordinate transformation |

---

## Backend - Test Coverage

**Status**: 143 source files, 60 tests  
**Coverage**: 42%  
**Test Cases**: 807 `it()` blocks

### Well-Tested Modules

| Module | Tests | Coverage Notes |
|--------|-------|----------------|
| Assistant | 15 tests | Claude/OpenAI providers, safety, shaping |
| Classifier | 8 tests | Enrichment, domain pack, mapper |
| Pricing | 11 tests | Estimator v2/v4, normalization |
| Vision | 5 tests | Golden tests, routes, quota |
| Auth | 6 tests | Google session, cleanup, eBay tokens |

### Missing Tests - Backend

| Module | Priority | Missing Coverage |
|--------|----------|------------------|
| Mobile Telemetry | P0 | Controller, event ingestion |
| Catalog | P1 | Search, filtering, pagination |
| Account | P2 | Routes |
| Admin | P2 | Routes |
| Infrastructure | P1 | Cache, observability, HTTP plugins |

---

## Priority Test Recommendations

### P0 - Critical (Immediate Action Required)

1. **Billing Module** - Zero tests, revenue-critical
   - `BillingRepositoryTest.kt`
   - `PaywallViewModelTest.kt`

2. **Camera Detection Router** - Core scanning orchestration
   - `DetectionRouterTest.kt`

3. **Telemetry** - Production debugging backbone
   - `MobileTelemetryClientTest.kt`
   - `OtlpHttpExporterTest.kt`

### P1 - High (Sprint Planning)

4. **FTUE ViewModels** - User onboarding
   - `TourViewModelTest.kt`
   - `FtueRepositoryTest.kt`
   - All FTUE ViewModels

5. **CameraXManager** - Camera lifecycle
   - `CameraXManagerTest.kt`

6. **Telemetry Contract** - Shared telemetry models
   - `TelemetryEventTest.kt`
   - `AttributeSanitizerTest.kt`

### P2 - Medium (Backlog)

7. **Voice & Audio**
   - `VoiceControllerTest.kt`
   - `TtsControllerTest.kt`

8. **Platform Adapters**
   - `PortableAdaptersTest.kt`

9. **Remaining ViewModels**
   - `CameraViewModelTest.kt`
   - `ListingViewModelTest.kt`

---

## Testing Guidelines

### When to Write Tests

1. **Business Logic**: All non-trivial business logic must be tested
2. **State Management**: ViewModels with StateFlow/SharedFlow must be tested
3. **Data Persistence**: Repository classes must be tested
4. **Complex Algorithms**: Algorithms with >50 lines require tests
5. **Revenue Paths**: All billing/entitlement logic must be tested
6. **Observability**: All telemetry/metrics collection must be tested

### Test Quality Standards

1. **Test Coverage**: Aim for ≥80% line coverage for critical modules
2. **Test Independence**: No test should depend on another test's state
3. **Test Speed**: Unit tests should run <100ms each
4. **Test Naming**: Use descriptive names: `testBehavior_expectedResult`
5. **Edge Cases**: Test null inputs, empty lists, boundary conditions

### Test Framework

- **Android**: JUnit4 + Truth + MockK + Robolectric
- **Shared**: Kotlin Test
- **Backend**: Vitest

---

## Implementation Roadmap

### Sprint 1 (2 weeks) - P0 Critical

```bash
# Create test files
androidApp/src/test/java/com/scanium/app/billing/BillingRepositoryTest.kt
androidApp/src/test/java/com/scanium/app/billing/PaywallViewModelTest.kt
androidApp/src/test/java/com/scanium/app/camera/detection/DetectionRouterTest.kt
androidApp/src/test/java/com/scanium/app/telemetry/MobileTelemetryClientTest.kt
androidApp/src/test/java/com/scanium/app/telemetry/otlp/OtlpHttpExporterTest.kt
```

### Sprint 2 (2 weeks) - P1 High

```bash
# FTUE ViewModels
androidApp/src/test/java/com/scanium/app/ftue/TourViewModelTest.kt
androidApp/src/test/java/com/scanium/app/ftue/FtueRepositoryTest.kt
androidApp/src/test/java/com/scanium/app/ftue/CameraFtueViewModelTest.kt
androidApp/src/test/java/com/scanium/app/ftue/CameraUiFtueViewModelTest.kt

# Camera Core
androidApp/src/test/java/com/scanium/app/camera/CameraXManagerTest.kt
androidApp/src/test/java/com/scanium/app/camera/CameraViewModelTest.kt
```

### Sprint 3 (2 weeks) - P1 High

```bash
# Telemetry Contract
shared/telemetry-contract/src/commonTest/.../TelemetryEventTest.kt
shared/telemetry-contract/src/commonTest/.../AttributeSanitizerTest.kt
shared/telemetry-contract/src/commonTest/.../TelemetryEventNamingTest.kt

# Backend Mobile Telemetry
backend/src/modules/mobile-telemetry/controller.test.ts
```

---

## Run Test Coverage

```bash
# Android unit tests
./gradlew test

# Android unit tests (JVM only, no Android SDK)
./gradlew prePushJvmCheck

# Shared module tests
./gradlew :shared:core-tracking:test
./gradlew :shared:core-models:test

# Code coverage
./gradlew koverVerify
# Thresholds: shared ≥85%, androidApp ≥75%

# Backend tests
cd backend && npm test
```

---

## Conclusion

The Scanium codebase has a solid foundation with **35.5% overall test coverage**, but there are critical gaps in revenue-critical paths (billing), core functionality (camera detection), and user onboarding (FTUE).

### Immediate Actions

1. **P0**: Add tests for Billing module (revenue protection)
2. **P0**: Add tests for DetectionRouter (core scanning)
3. **P0**: Add tests for Telemetry (observability)

### Long-term Goals

1. Achieve **≥80% coverage** for all critical modules
2. Add tests for all ViewModels (current: 6/23)
3. Expand golden test suite for vision/classification pipelines
4. Add integration tests for end-to-end flows

---

**Next Steps**: Review this document with the team, prioritize based on product risk, and create JIRA tickets for P0 items.
