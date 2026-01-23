# Shared API Surface (pre-compile lock)

## shared/core-models

- Exported portable models under `com.scanium.shared.core.models`:
    - `NormalizedRect` – normalized bounding-box geometry helper (`model/NormalizedRect.kt`).
    - `ImageRef` – sealed portable image reference (`model/ImageRef.kt`).
    - `ScannedItem<FullImageUri>` plus `ConfidenceLevel` and `ItemListingStatus` – promoted
      detection with display helpers (`items/ScannedItem.kt`).
    - `RawDetection` and `LabelWithConfidence` – raw ML detection payload kept Android-free (
      `ml/RawDetection.kt`).
    - `DetectionResult` – overlay-friendly detection result with formatting helpers (
      `ml/DetectionResult.kt`).
- Legacy `core-models` module re-exports these via typealiases so existing imports under
  `com.scanium.app.*` continue compiling (e.g., ImageRef, NormalizedRect, ScannedItem, RawDetection,
  DetectionResult).

## shared/core-tracking

- Public API under `com.scanium.core.tracking`:
    - `Logger` interface with `NONE` no-op instance.
    - `ObjectCandidate` (plus `FloatRect`) – candidate state and geometry helpers.
    - `DetectionInfo` – tracker input record using shared models.
    - `ObjectTracker` – orchestrator with confirmation/expiry, exposing `TrackerConfig` and
      `TrackerStats`.
- Legacy `core-tracking` module provides typealias wrappers that re-export the shared classes for
  compatibility with older packages.

## Android namespace audit

- `shared/**/src/commonMain` contains no `android.*` or `androidx.*` imports; only guard comments
  reference these namespaces.
