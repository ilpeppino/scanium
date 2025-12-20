> Archived on 2025-12-20: superseded by docs/DECISIONS.md.
***REMOVED*** ADR-001: Cloud Classification via Google Vision (Backend Proxy)

**Status:** Accepted  
**Date:** 2025-01-12  
**Deciders:** Architecture group  
**Context:** Phase 4 build stability + iOS prep

---

***REMOVED******REMOVED*** Context
ML Kit only returns 5 coarse labels, which is insufficient for resale workflows (needs 20+ fine-grained categories and attributes). We also must keep the camera pipeline responsive and avoid shipping secrets in the app. Cloud classification is required for accuracy, but it cannot block on-device detection/tracking.

---

***REMOVED******REMOVED*** Decision
Use Google Vision (Image/Label Detection) through our backend proxy as the **primary** classifier.

- Mobile uploads **only cropped thumbnails of stable aggregated items** (confirmed by tracking/aggregation) to minimize latency and cost.
- Backend holds the Google credentials, enforces auth/rate limits, and maps responses to domain categories.
- App-side configuration is typed via `CloudClassifierConfig` + `CloudConfigProvider` (Android impl reads BuildConfig values sourced from `local.properties` or env).
- `ClassificationOrchestrator` keeps uploads async with bounded concurrency (2) and retries on 408/429/5xx; failures fall back to on-device labels.

---

***REMOVED******REMOVED*** Alternatives Considered
- **Direct Vision API from app** — Rejected (key leakage, no throttling).
- **On-device CLIP** — Deferred (size/perf risk; violates “no heavy ML runtime” for this phase).
- **Stay on ML Kit only** — Rejected (insufficient accuracy/attributes).

---

***REMOVED******REMOVED*** Consequences
**Positive**
- Higher category accuracy and attribute coverage using Vision.
- No secrets in APK/IPA; backend controls auth and usage.
- Asynchronous path keeps camera/overlay responsive.

**Negative**
- Requires backend availability; adds one network hop.
- Cost control needed (rate limits + stable-item filter).

**Implementation Notes**
- Contract: `shared/core-models/classification/ClassifierContracts.kt` (`Classifier`, `ClassificationResult`, `ClassificationMode`, `CloudClassifierConfig`).
- Android: `CloudClassifier` implements the contract using OkHttp and BuildConfig-driven config; mock classifier available for offline tests.
- iOS: Future client uses the same contract and backend endpoint; no API key shipped to iOS either.

**Testing**
- JVM tests rely on mock classifier; no network required.
- E2E cloud tests gated by env vars `SCANIUM_API_BASE_URL` / `SCANIUM_API_KEY`.
