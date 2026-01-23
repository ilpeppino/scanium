# Logcat commands for Scanium testing

Use these filters to capture the app's functional and technical logs while exercising key flows.

## Quick start

- Full pipeline view (camera + ML + aggregation):
    -
    `adb logcat -v color CameraXManager:I ObjectDetectorClient:I BarcodeScannerClient:D DocumentTextRecognitionClient:D DetectionLogger:D ItemsViewModel:I *:S`
- Save a focused session to a file:
    -
    `adb logcat -v threadtime -f scanium-scan.log -s CameraXManager ObjectDetectorClient BarcodeScannerClient DocumentTextRecognitionClient DetectionLogger ItemsViewModel`

## Functional flow filters

- Camera bring-up, frame analysis, and tracker metrics (CameraXManager):
    - `adb logcat -v color CameraXManager:I *:S`
- Object detection lifecycle and edge filtering (ObjectDetectorClient):
    - `adb logcat -v color ObjectDetectorClient:I *:S`
- Barcode/QR detection (BarcodeScannerClient):
    - `adb logcat -v color BarcodeScannerClient:D *:S`
- Document OCR for receipts/forms (DocumentTextRecognitionClient):
    - `adb logcat -v color DocumentTextRecognitionClient:D *:S`
- Item aggregation and telemetry snapshots (ItemsViewModel):
    - `adb logcat -v color ItemsViewModel:I *:S`

## Classification detail

- On-device heuristics (OnDeviceClassifier) for quick feedback:
    - `adb logcat -v color OnDeviceClassifier:D *:S`
- Cloud classifier requests/retries (CloudClassifier):
    - `adb logcat -v color CloudClassifier:D *:S`

## Selling and media flows

- Listing lifecycle, mock API calls, and image prep (ListingViewModel, MockEbayApi,
  EbayMarketplaceService, ListingImagePreparer):
    -
    `adb logcat -v color ListingViewModel:I MockEbayApi:D EbayMarketplaceService:D ListingImagePreparer:D *:S`
- Saved photo paths and failures (MediaStoreSaver):
    - `adb logcat -v color MediaStoreSaver:D *:S`

## Audio cues

- Camera shutter/confirmation sounds (CameraSoundManager):
    - `adb logcat -v color CameraSoundManager:D *:S`

Adjust `D/I/W/E` levels per need; add `*:S` to suppress noisy system logs when focusing on Scanium.
