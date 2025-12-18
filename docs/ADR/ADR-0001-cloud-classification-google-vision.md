# ADR-0001: Cloud Classification via Google Vision (Backend Proxy)

**Status:** Accepted  
**Date:** 2025-01-12  
**Deciders:** Architecture group  
**Context:** Android-first shipping with cloud-primary classification

---

## Context
- ML Kit gives only 5 coarse labels; resale needs 20+ fine-grained categories and attributes.
- The camera/overlay must stay responsive; classification cannot block detection/tracking.
- Secrets must not ship in mobile binaries; API usage needs rate limiting and observability.

---

## Decision
Use Google Vision through our **backend proxy** as the primary classifier. Only **stable aggregated items** (confirmed + thumbnail) are uploaded. BuildConfig-driven config (from `local.properties`/env) feeds a typed `CloudClassifierConfig`; `CloudConfigProvider` supplies it per platform.

---

## Consequences
**Positive**
- Higher accuracy and attribute coverage versus on-device labels.
- Secrets stay server-side; backend enforces auth/rate limits and logs usage.
- Async path with bounded concurrency keeps the pipeline responsive.

**Negative**
- Requires backend availability; adds a network hop and cost controls.

---

## Implementation Notes
- Contracts: `shared/core-models/classification/ClassifierContracts.kt`, `shared/core-models/config/CloudClassifierConfig.kt`.
- Android client: `CloudClassifier` uses OkHttp (10s/10s timeouts), multipart JPEG, retries on 408/429/5xx, strips EXIF.
- Config: `scanium.api.base.url`, `scanium.api.key` from `local.properties`/env → BuildConfig → `CloudConfigProvider`.
- Fallback: On-device coarse labels when cloud unavailable/unconfigured; results flagged as fallback.
- Testing: Mock classifier for JVM; cloud E2E guarded by env vars.
