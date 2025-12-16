***REMOVED*** KMP Migration Target Map

***REMOVED******REMOVED*** Top 10 Files
- core-models/src/main/java/com/scanium/app/items/ScannedItem.kt
- core-models/src/main/java/com/scanium/app/ml/RawDetection.kt
- core-models/src/main/java/com/scanium/app/ml/DetectionResult.kt
- core-tracking/src/main/java/com/scanium/app/tracking/ObjectTracker.kt
- core-tracking/src/main/java/com/scanium/app/tracking/ObjectCandidate.kt
- androidApp/src/main/java/com/scanium/app/items/ItemsListScreen.kt
- androidApp/src/main/java/com/scanium/app/items/ItemDetailDialog.kt
- androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt
- androidApp/src/main/java/com/scanium/app/ml/BarcodeScannerClient.kt
- androidApp/src/main/java/com/scanium/app/ml/DocumentTextRecognitionClient.kt

***REMOVED******REMOVED*** Leak Inventory

***REMOVED******REMOVED******REMOVED*** ✅ KMP-Ready (No Android Dependencies)
- **core-models ML layer**: All detection models are KMP-ready
  - core-models/src/main/java/com/scanium/app/ml/RawDetection.kt — portable (NormalizedRect + ImageRef; no android.graphics types)
  - core-models/src/main/java/com/scanium/app/ml/DetectionResult.kt — portable (NormalizedRect only; no android.graphics types)

***REMOVED******REMOVED******REMOVED*** 🚧 Platform-Specific (Android Dependencies Remain)
- androidApp/src/main/java/com/scanium/app/items/ItemsListScreen.kt — renders thumbnails via `asImageBitmap()` assuming platform `Bitmap` instances.
- androidApp/src/main/java/com/scanium/app/items/ItemDetailDialog.kt — displays thumbnails with Compose `Image` backed by `Bitmap`.
- androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt — depends on `Bitmap`, `Rect`, and `RectF` for cropping/rotation and ML Kit input.
- androidApp/src/main/java/com/scanium/app/ml/BarcodeScannerClient.kt — depends on `Bitmap` and `Rect` for barcode crops and area math.
- androidApp/src/main/java/com/scanium/app/ml/DocumentTextRecognitionClient.kt — depends on `Bitmap` and `Rect` for text thumbnails and bounding boxes.
