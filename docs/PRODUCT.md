***REMOVED*** Product Overview

***REMOVED******REMOVED*** What the app does now
- Live camera scanning with on-device ML Kit for object, barcode/QR, and document OCR modes (API 24+).
- Shows detection overlays (boxes + labels) and aggregates stable items with confidence and thumbnail snapshots.
- Maintains an item list; users can tap through scan sessions without losing prior detections.
- Mock selling flow: select items and list via fake marketplace service; no real network calls for selling.
- Domain pack config provides finer-grained categories beyond ML Kit defaults.

***REMOVED******REMOVED*** User flows & screens
- **Home/Camera**: `CameraScreen` hosts preview, overlays, and scan mode switching.
- **Items list**: `ItemsListScreen` displays aggregated items from `ItemsViewModel`.
- **Selling**: `SellScreen` lets users pick items and run the mocked listing flow.

***REMOVED******REMOVED*** Feature flags / modes
- Scan modes: object, barcode/QR, document (switchable in camera UI).
- Cloud classification pipeline exists in `ml/classification/CloudClassifier.kt` but requires backend credentials; TODO/VERIFY before enabling in builds.
- No other runtime feature-flag system is present; changes are code-driven.
