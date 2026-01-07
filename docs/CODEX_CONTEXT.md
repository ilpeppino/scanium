***REMOVED*** Scanium – Codex Context (agent quickmap)
- Update rule: when adding a module, append a row to the module table + dependency graph; when adding a feature area, add one bullet to TL;DR + Feature-routing.

***REMOVED******REMOVED*** A) TL;DR Map (feature → touchpoints)

***REMOVED******REMOVED******REMOVED*** Android Application
- App shell/nav: `androidApp/src/main/java/com/scanium/app/MainActivity.kt`, `navigation/NavGraph.kt`, `ScaniumApp.kt`.
- Camera UI/preview/gestures/overlays: `androidApp/src/main/java/com/scanium/app/camera/` (CameraScreen, CameraXManager, DetectionOverlay).
- ML analyzers (objects/barcodes/OCR/pricing): `androidApp/src/main/java/com/scanium/app/ml/` (ObjectDetectorClient, BarcodeScannerClient, PricingEngine).
- AI Assistant (Claude/OpenAI): `androidApp/src/main/java/com/scanium/app/assistant/` (AssistantScreen, AssistantViewModel, AssistantRepository).
- Classification providers: `androidApp/src/main/java/com/scanium/app/classification/` (ClassifierProvider, MockClassifier, NoOpClassifier).
- Voice control: `androidApp/src/main/java/com/scanium/app/voice/` (VoiceController, VoiceStateMachine, VoiceState).
- Audio feedback: `androidApp/src/main/java/com/scanium/app/audio/` (sound effects, audio feedback).
- FTUE & Onboarding: `androidApp/src/main/java/com/scanium/app/ftue/` (SpotlightTourOverlay, WelcomeOverlay, TourViewModel).
- Telemetry export: `androidApp/src/main/java/com/scanium/app/telemetry/` (OpenTelemetry OTLP export - logs, traces, metrics).
- Diagnostics & Health: `androidApp/src/main/java/com/scanium/app/diagnostics/` (DiagnosticsRepository, system health checks).
- Settings UI: `androidApp/src/main/java/com/scanium/app/settings/` (SettingsScreen, DeveloperOptionsScreen).
- Tracking/aggregation logic: `shared/core-tracking/src/commonMain` (ObjectTracker, ItemAggregator) via Android wrappers in `core-tracking`.
- Portable models + enums: `shared/core-models/src/commonMain` (ImageRef, NormalizedRect, ScanMode, ScannedItem); Android aliases in `core-models`.
- Export models: `shared/core-export/src/commonMain` (ExportItem, ExportPayload, CSV/ZIP mappers).
- Domain pack loading/category mapping: `core-domainpack/src/main/java/com/scanium/app/domain/` (+ `core-domainpack/src/main/res/raw/home_resale_domain_pack.json`).
- Item state + orchestration: `androidApp/src/main/java/com/scanium/app/items/` (ItemsViewModel, ItemsListScreen, EditItemsScreen).
- Selling/eBay integration (flavor-gated): `androidApp/src/main/java/com/scanium/app/selling/`.
- Dependency injection: `androidApp/src/main/java/com/scanium/app/di/` (Hilt modules).
- Camera/ML platform adapters: `android-platform-adapters/src/main/java/com/scanium/android/platform/adapters/` (Bitmap/ImageRef, Rect/NormalizedRect).
- Platform ML/camera shells (namespaces only): `android-ml-mlkit`, `android-camera-camerax` (no sources expected).
- Shared test utilities: `shared/test-utils` used by shared modules; Android tests in `androidApp/src/test` and `src/androidTest`.

***REMOVED******REMOVED******REMOVED*** Backend Services
- Backend entry point: `backend/src/main.ts` (Fastify server setup, middleware, OpenTelemetry).
- API routes: `backend/src/routes/` (items.ts, auth/, health.ts).
- Business logic: `backend/src/services/` (item management, auth, external integrations).
- Feature modules: `backend/src/modules/` (classifier domain, AI assistant, vision insights).
- Database schema: `backend/prisma/schema.prisma` (Prisma models, relations, migrations).
- Database container: `backend/docker-compose.yml` (PostgreSQL 16).
- Environment config: `backend/.env` (API keys, database URL, ngrok settings - gitignored).
- External integrations: Anthropic Claude SDK, OpenAI SDK, Google Cloud Vision.

***REMOVED******REMOVED******REMOVED*** Observability Stack
- Monitoring stack: `monitoring/docker-compose.yml` (Grafana, Loki, Tempo, Mimir, Alloy).
- Grafana dashboards: `monitoring/grafana/dashboards/` (pre-provisioned visualizations).
- Grafana datasources: `monitoring/grafana/provisioning/datasources/` (Loki, Tempo, Mimir).
- Alloy config: `monitoring/alloy/alloy.hcl` (OTLP routing rules).
- Backend configs: `monitoring/{loki,tempo,mimir}/*.yaml` (retention, storage).

***REMOVED******REMOVED******REMOVED*** Development Scripts
- Integrated startup: `scripts/backend/start-dev.sh` (backend + PostgreSQL + ngrok + monitoring).
- Integrated shutdown: `scripts/backend/stop-dev.sh` (with optional --with-monitoring).
- Monitoring management: `scripts/monitoring/{start,stop,print-urls}.sh`.
- Android build: `scripts/build.sh` (Java 17 auto-detection).

***REMOVED******REMOVED******REMOVED*** Architecture Documentation
- [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) - Full system architecture (Android + backend + observability).
- [`docs/DEV_GUIDE.md`](DEV_GUIDE.md) - Development workflow and setup.
- Domain pack architecture: see `docs/_archive/2025-12/md/architecture/DOMAIN_PACK_ARCHITECTURE.md` for historical reference.
- [`README.md`](../README.md) - Project overview and quick start.
- [`monitoring/README.md`](../monitoring/README.md) - Observability stack guide.

***REMOVED******REMOVED*** B) Module Table

**Dependency Injection:** Dagger Hilt is used throughout `androidApp`. ViewModels use `@HiltViewModel` + `@Inject constructor()`; `MainActivity` is annotated with `@AndroidEntryPoint`. Test modules in `androidApp/src/test/java/com/scanium/app/di/FakeModules.kt`.

| Module | Responsibility | Inputs / Outputs | Do / Don't | Key files |
| --- | --- | --- | --- | --- |
| androidApp | Compose UI, navigation, camera UX, ML wrappers, selling, persistence, cloud API, diagnostics, build config, Hilt DI | Inputs: CameraX frames, ML Kit results, domain packs; Outputs: UI state, network calls | Do: keep platform logic here; Don't: import from `android.*` into shared packages | `src/main/java/com/scanium/app/MainActivity.kt`, `navigation/NavGraph.kt`, `camera/CameraXManager.kt`, `ml/*`, `items/ItemsViewModel.kt`, `selling/*`, `diagnostics/*`, `ui/settings/*`, `data`, `di/` |
| core-models | Android wrapper for shared models/typealiases | Inputs: shared models; Outputs: Android-friendly types | Do: stay Android-free aside from namespace; Don’t: add platform imports (portability check) | `src/main/java/com/scanium/app/model/*`, `src/main/java/com/scanium/app/items/ScannedItem.kt` |
| core-tracking | Android wrapper exposing shared tracking | Inputs: shared tracking; Outputs: Android-consumable tracker/aggregator | Do: delegate to shared; Don’t: add android.* (portability check applies) | `src/main/java/com/scanium/app/tracking/*`, `src/main/java/com/scanium/app/aggregation/*` |
| core-domainpack | Domain pack IO, repository, category engine | Inputs: JSON packs, labels/prompts; Outputs: ItemCategory, domain config | Do: keep Android IO minimal; Don’t: depend on camera/ML classes | `domain/DomainPackProvider.kt`, `domain/repository/LocalDomainPackRepository.kt`, `domain/category/*`, `src/main/res/raw/home_resale_domain_pack.json` |
| core-scan | Namespace holder for legacy scan contracts | Inputs/Outputs: n/a | Do: keep empty until migrated; Don’t: add UI/platform deps | `build.gradle.kts` (no sources) |
| core-contracts | Namespace holder for shared contracts | Inputs/Outputs: n/a | Do: keep slim; Don’t: leak platform types | `build.gradle.kts` (no sources) |
| android-platform-adapters | Bitmap/Rect ↔ portable model adapters | Inputs: Bitmap, Rect/RectF; Outputs: ImageRef/NormalizedRect | Do: convert at platform boundary; Don’t: push android.* into shared | `src/main/java/com/scanium/android/platform/adapters/*` |
| android-ml-mlkit | ML Kit namespace shell | Inputs/Outputs: n/a | Do: keep placeholder until KMP adapters land; Don’t: add manifests with package attr | `build.gradle.kts` |
| android-camera-camerax | CameraX namespace shell | Inputs/Outputs: n/a | Do: keep placeholder; Don’t: add manifests with package attr | `build.gradle.kts` |
| shared:core-models | KMP portable primitives (ImageRef, NormalizedRect, ScanMode, ScannedItem, pricing/domain models) | Inputs: none; Outputs: serializable models | Do: stay platform-free; Don’t: import android.*, ML Kit, CameraX | `src/commonMain/kotlin/com/scanium/core/model/*`, `src/commonMain/kotlin/com/scanium/core/items/ScannedItem.kt` |
| shared:core-export | KMP export models + mappers for selected items | Inputs: ScannedItem; Outputs: ExportItem/ExportPayload | Do: stay platform-free; Don’t: depend on android.* | `src/commonMain/kotlin/com/scanium/core/export/*` |
| shared:core-tracking | KMP tracking/aggregation logic | Inputs: Raw detections (NormalizedRect, trackingIds); Outputs: tracked items/events | Do: pure Kotlin; Don’t: platform imports; respect golden tests | `src/commonMain/kotlin/com/scanium/core/tracking/*`, `src/commonMain/kotlin/com/scanium/core/aggregation/*` |
| shared:test-utils | Test helpers for shared modules | Inputs: shared models/tracking; Outputs: fixtures/assert helpers | Do: keep deterministic; Don’t: add platform deps | `src/commonMain/kotlin/com/scanium/test/*` |

Portability guards: root `checkPortableModules` blocks android imports in `core-models` + `core-tracking`; shared modules already KMP-only. `checkNoLegacyImports` forbids legacy `com.scanium.app.*` imports inside `androidApp`.

***REMOVED******REMOVED*** C) Dependency Graph (modules)
```
androidApp -> core-models, core-tracking, core-domainpack, core-scan, core-contracts,
              android-ml-mlkit, android-camera-camerax, android-platform-adapters, shared:test-utils (tests)
core-models -> shared:core-models
core-tracking -> shared:core-tracking, shared:core-models, core-models
core-domainpack -> core-models
android-platform-adapters -> core-models
shared:core-tracking -> shared:core-models
shared:core-export -> shared:core-models
shared:test-utils -> shared:core-models, shared:core-tracking
```

***REMOVED******REMOVED*** D) Feature-routing Cheat Sheet

***REMOVED******REMOVED******REMOVED*** Android Application
- UI tweak/navigation: `androidApp` → `navigation/NavGraph.kt`, relevant screen under `camera/`, `items/`, `assistant/`, `selling/`, `settings/`, `ui/`.
- Camera behavior (preview, focus, analyzer thread): `androidApp/camera/CameraXManager.kt`, `CameraScreen.kt`, `DetectionOverlay.kt`.
- ML behavior (detectors/pricing): `androidApp/ml/ObjectDetectorClient.kt`, `BarcodeScannerClient.kt`, `DocumentTextRecognitionClient.kt`, `PricingEngine.kt`.
- AI Assistant (multimodal chat): `androidApp/assistant/AssistantScreen.kt`, `AssistantViewModel.kt`, `AssistantRepository.kt`.
- Classification providers: `androidApp/classification/ClassifierProvider.kt`, `MockClassifier.kt`, `NoOpClassifier.kt`.
- Voice control: `androidApp/voice/VoiceController.kt`, `VoiceStateMachine.kt`, `VoiceState.kt`.
- Audio feedback: `androidApp/audio/` (sound effects, haptic feedback).
- FTUE/Onboarding: `androidApp/ftue/SpotlightTourOverlay.kt`, `WelcomeOverlay.kt`, `TourViewModel.kt`.
- Telemetry export: `androidApp/telemetry/` (OpenTelemetry OTLP - AndroidLogPortOtlp, AndroidMetricPortOtlp, AndroidTracePortOtlp).
- Tracking/dedup logic: shared KMP in `shared/core-tracking`; Android entry/usage in `core-tracking` and `androidApp/items/ItemsViewModel.kt`.
- Domain pack/category update: `core-domainpack/domain/category/*`, `domain/repository/LocalDomainPackRepository.kt`, raw JSON under `core-domainpack/src/main/res/raw/`.
- Export logic: shared KMP in `shared/core-export`; Android usage in `androidApp/items/`.
- Logging/crash reporting: `androidApp/crash/`, `androidApp/logging/`.
- Developer Options/System Health: `androidApp/diagnostics/DiagnosticsRepository.kt` (health checks), `settings/DeveloperOptionsScreen.kt` (UI).
- Persistence (drafts/DataStore/Room): `androidApp/data/*`, Room entities/DAOs if added; selling cache in `selling/data/*`.
- Platform adapters (Bitmap/Rect ↔ shared): `android-platform-adapters` extension functions.
- Tests: fast shared logic → `shared:core-tracking`, `shared:core-models`, `shared:core-export` JVM tests; Android features → `androidApp/src/test` or `src/androidTest`.

***REMOVED******REMOVED******REMOVED*** Backend Services
- API endpoints: `backend/src/routes/` (add new routes here).
- Business logic: `backend/src/services/` (service layer, external API calls).
- Database schema changes: `backend/prisma/schema.prisma` → run `npx prisma migrate dev`.
- Database queries: Use Prisma Client (`backend/src/services/*`).
- Environment variables: `backend/.env` (never commit; use `.env.example` for templates).
- Server config: `backend/src/index.ts` (middleware, CORS, OpenTelemetry).
- Health checks: `backend/src/routes/health.ts`.

***REMOVED******REMOVED******REMOVED*** Observability & Monitoring
- Add dashboard: Create JSON in `monitoring/grafana/dashboards/`, restart Grafana.
- Modify retention: Edit `monitoring/{loki,tempo,mimir}/*.yaml`, recreate containers.
- OTLP routing changes: `monitoring/alloy/alloy.hcl`, restart Alloy container.
- View logs: `docker compose -p scanium-monitoring logs -f [service]`.
- Health checks: `scripts/monitoring/print-urls.sh`.

***REMOVED******REMOVED******REMOVED*** Development Workflow
- Start full dev environment: `scripts/backend/start-dev.sh` (backend + monitoring).
- Start backend only: `scripts/backend/start-dev.sh --no-monitoring`.
- Stop everything: `scripts/backend/stop-dev.sh --with-monitoring`.
- View monitoring URLs: `scripts/monitoring/print-urls.sh`.
- Backend logs: `tail -f backend/.dev-server.log`.
- Database migrations: `cd backend && npx prisma migrate dev`.
- Reset database: `cd backend && npx prisma migrate reset`.

***REMOVED******REMOVED*** E) Change Safety Checklist
- Run fast checks: `./gradlew prePushJvmCheck` (shared JVM tests + portability), `./gradlew test` (unit), `./gradlew assembleDebug` (app builds), `./gradlew lint` when touching UI/Android.
- Avoid: android.* imports in shared/KMP modules; adding heavy deps to shared; leaking API keys; bypassing domain pack contracts; breaking tracker reset/aggregation invariants.

***REMOVED******REMOVED*** F) Item Enrichment & Attribute Contract

***REMOVED******REMOVED******REMOVED*** Canonical Attribute Schema

| Key | Type | Source | Confidence | Description |
|-----|------|--------|------------|-------------|
| `brand` | String | VISION_LOGO, VISION_OCR | 0.0-1.0 | Brand name from logo detection or OCR |
| `itemType` | String | VISION_LABEL, VISION_OCR | 0.0-1.0 | Concrete sellable noun (e.g., "Lip Balm") |
| `color` | String | VISION_COLOR | 0.0-1.0 | Primary dominant color |
| `secondaryColor` | String | VISION_COLOR | 0.0-1.0 | Secondary dominant color |
| `ocrText` | String | VISION_OCR | 0.8 | Detected text snippets (filtered) |
| `model` | String | VISION_OCR | 0.0-1.0 | Product model number |
| `material` | String | VISION_LABEL | 0.0-1.0 | Material composition |

**Source values:** `VISION_LOGO`, `VISION_OCR`, `VISION_COLOR`, `VISION_LABEL`, `LLM_DERIVED`, `USER`

***REMOVED******REMOVED******REMOVED*** Attribute Lifecycle

**Created:**
- Layer A (Local): `LocalVisionExtractor.kt` → ocrText, colors
- Layer B (Cloud): `VisionInsightsRepository.kt` → logos, labels, itemType, colors
- Layer C (Enrichment): `backend/src/modules/enrich/pipeline.ts` → normalized attributes

**Merged:**
- `ItemsStateManager.applyVisionInsights()` merges with priority: Cloud > Local
- `VisionInsightsPrefiller.applyEnrichmentResults()` maps backend keys to Android keys
- Key normalization: `product_type` → `itemType`, `secondary_color` → `secondaryColor`

**Persisted:**
- `ScannedItemEntity.kt` serializes `visionAttributesJson` to Room database
- Includes: ocrText, itemType, colors, logos, labels, brandCandidates, modelCandidates

**Consumed by UI:**
- `ScannedItem.displayLabel` uses priority: brand + itemType + color
- `EditItemScreenV2.buildDisplayableAttributes()` renders attribute chips
- `ItemsListScreen.AttributeChipsRow()` shows top 3 attributes

**Passed to Assistant:**
- `ListingDraftBuilder.buildFields()` extracts to `DraftFieldKey` map
- `ItemContextSnapshotBuilder.fromDraft()` converts to `ItemAttributeSnapshot`
- `AssistantViewModel.mergeSnapshotAttributes()` adds vision attributes

***REMOVED******REMOVED******REMOVED*** Critical Files for Enrichment Logic

**Must touch (for enrichment changes):**
- `androidApp/.../ml/VisionInsightsPrefiller.kt` - Pipeline orchestration
- `androidApp/.../ml/LocalVisionExtractor.kt` - Local extraction
- `androidApp/.../ml/VisionInsightsRepository.kt` - Cloud Vision calls
- `androidApp/.../items/state/ItemsStateManager.kt` - State updates
- `shared/.../listing/ListingDraft.kt` - Draft field extraction
- `backend/src/modules/vision/routes.ts` - Backend Vision API
- `backend/src/modules/enrich/pipeline.ts` - Enrichment pipeline

**Must NOT modify without architectural review:**
- `shared/.../items/ScannedItem.kt` - Core model (affects all consumers)
- `shared/.../items/VisionAttributes.kt` - Vision data contract
- `shared/.../assistant/AssistantModels.kt` - Assistant context contract
- `androidApp/.../items/persistence/ScannedItemEntity.kt` - Database schema

---

***REMOVED******REMOVED*** G) Vision Golden Assets

***REMOVED******REMOVED******REMOVED*** Directory Structure

```
tests/
└── golden_images/
    ├── kleenex-small-box.jpg       ***REMOVED*** Household goods (tissue box)
    ├── labello-lip-balm.jpg        ***REMOVED*** Cosmetics (lip balm) [future]
    ├── nike-tshirt.jpg             ***REMOVED*** Apparel (branded t-shirt) [future]
    └── multi-object-scene.jpg      ***REMOVED*** Multi-item scene [future]

androidApp/src/test/resources/golden/
    ├── nike_shoe_attributes.json   ***REMOVED*** Expected attributes for Nike shoe
    └── iphone_partial_attributes.json  ***REMOVED*** Partial electronics item
```

***REMOVED******REMOVED******REMOVED*** Image Constraints

| Constraint | Value | Reason |
|------------|-------|--------|
| Max dimension | 800px (longest side) | Git storage efficiency |
| Max file size | 200KB | Avoid bloating repository |
| Format | JPEG | Universal Vision API support |
| Quality | 85% compression | Balance quality/size |
| Content | Clear, well-lit | Reliable extraction |

***REMOVED******REMOVED******REMOVED*** Manifest Format (Expected Attributes)

```json
{
  "imageFile": "kleenex-small-box.jpg",
  "expectedAttributes": {
    "brand": { "contains": "kleenex", "minConfidence": 0.7 },
    "itemType": { "oneOf": ["tissue box", "tissue", "box"], "minConfidence": 0.5 },
    "ocrText": { "contains": "kleenex" },
    "colors": { "minCount": 1 }
  },
  "testType": "golden",
  "category": "household"
}
```

***REMOVED******REMOVED******REMOVED*** Adding a New Golden Image

1. **Select image:** Clear, well-lit, brand/text visible
2. **Resize:** `convert input.jpg -resize 800x800\> -quality 85 output.jpg`
3. **Verify size:** Must be ≤200KB
4. **Store:** Add to `tests/golden_images/`
5. **Create fixture:** Add expected attributes JSON
6. **Update test:** Add assertions in `routes.golden.test.ts`
7. **Run locally:** Verify with `GOOGLE_APPLICATION_CREDENTIALS=... npm test`
8. **Commit:** Include image + fixture + test update in same PR

***REMOVED******REMOVED******REMOVED*** CI Behavior

- Golden tests **skip** when `GOOGLE_APPLICATION_CREDENTIALS` not set
- Use `describe.skip` / `describeIf` pattern for conditional execution
- Mock extractors available for offline testing
- Real Vision API only called in golden test runs with credentials

---

***REMOVED******REMOVED*** H) Where Config Lives
- Build configs: set via `local.properties` or env → `androidApp/build.gradle.kts` (`SCANIUM_API_BASE_URL`, `SCANIUM_API_KEY`, `SENTRY_DSN`, legacy `CLOUD_CLASSIFIER_*`, `CLASSIFIER_SAVE_CROPS`). Example defaults in `local.properties.example` (not committed).
- Signing/keystore paths also read from `local.properties` keys (`scanium.keystore.*`).
- Domain packs: JSON under `core-domainpack/src/main/res/raw/`. Never commit secrets or real API keys.
