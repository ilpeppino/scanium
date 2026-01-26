# Pricing V3 Rollout & Feature Flags

This document covers how Pricing V3 is gated and rolled out across backend + Android.

## Summary

Pricing V3 is a dedicated pricing endpoint and UI card gated by:

- **Backend env flags** (enable endpoint + limits)
- **Remote config flag** (enable UI exposure)
- **Flavor gating** (dev builds can surface the UI)

Feature remains **disabled by default** in production until the rollout gates are met.

## Android Feature Flags

### Flavor gating (BuildConfig)

Defined in `androidApp/build.gradle.kts` and surfaced in `FeatureFlags.kt`:

- `FEATURE_PRICING_V3` -> `FeatureFlags.allowPricingV3`

Recommended defaults:
- **dev**: true (developer testing)
- **beta/prod**: false

### Remote Config

Remote config is fetched from `/v1/config` (backed by `backend/config/remote-config.json`).

Add the following boolean flag under `featureFlags`:

- `enablePricingV3`: true/false

Android reads it via `RemoteConfig.featureFlags.enablePricingV3`.

## Backend Environment Flags

Configured via environment variables (see `backend/src/config/index.ts`):

- `PRICING_V3_ENABLED` (boolean) – enable v3 endpoint
- `PRICING_V3_TIMEOUT_MS` (number, default 15000)
- `PRICING_V3_CACHE_TTL_SECONDS` (number, default 86400)
- `PRICING_V3_DAILY_QUOTA` (number, default 1000)
- `PRICING_V3_PROMPT_VERSION` (string, default "1.0.0")

## Rollout Process

1. **Backend**: set `PRICING_V3_ENABLED=true` in dev/staging.
2. **Remote config**: set `featureFlags.enablePricingV3=true` for dev devices or a small rollout.
3. **Beta**: enable for internal users only, monitor metrics.
4. **Prod**: ramp up via rollouts; keep default false until metrics are healthy.

## Monitoring Gates

- Error rate < 5%
- P95 latency < 10s
- Cache hit rate > 30%
- Daily cost < $10

If any gate fails, disable `PRICING_V3_ENABLED` on backend and/or set `enablePricingV3=false` in remote config.
