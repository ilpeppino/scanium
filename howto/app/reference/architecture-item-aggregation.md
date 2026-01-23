# Item Aggregation Architecture Notes

## Purpose

Document aggregation logic boundaries, scoring, and invariants so changes to merge behavior are
safe.

## Scope

- Shared aggregation engine:
  `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ItemAggregator.kt`.
- Android wrapper usage:
  `androidApp/src/main/java/com/scanium/app/items/state/ItemsStateManager.kt`.

## High-level flow

```
Detection (ScannedItem)
  -> ItemAggregator.processDetection
    -> calculateSimilarity
      -> category + label + size + distance
    -> merge or create AggregatedItem
    -> update caches / stats
  -> ItemsStateManager persistence
```

## Responsibilities (current)

- ItemAggregator
    - Similarity scoring and merge decisions.
    - Aggregated item lifecycle and stats.
    - Optional spatial-temporal fallback merge policy.

- ItemsStateManager
    - Stores and persists aggregated items.
    - Emits UI state and telemetry.

## Similarity inputs

- Category match (optional hard requirement).
- Label similarity (Levenshtein distance).
- Size ratio (area match).
- Center distance (normalized by frame diagonal).

## Invariants to preserve

- Similarity threshold is applied as `>=` for merge decisions.
- Hard limits reject matches that exceed size or distance constraints.
- Aggregation must be deterministic for a given sequence of detections.
- Aggregated IDs should remain stable for a session.

## Safety checks before changes

- Add or update tests for threshold edge cases.
- Validate size and distance cutoffs with existing presets.
- Verify spatial-temporal merge policy does not override category constraints unless configured.

## Suggested boundary split (target)

- SimilarityScorers: label/size/distance scoring functions.
- AggregationPolicies: decision rules and thresholds.
- AggregationStateStore: item lifecycle + stats.
