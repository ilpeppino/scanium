***REMOVED*** ML Kit Detection Improvements

***REMOVED******REMOVED*** Changes Made to Fix "No Objects Detected" Issue

***REMOVED******REMOVED******REMOVED*** 1. **Dual Detector Modes** (ObjectDetectorClient.kt:27-48)
Created two separate ML Kit detectors optimized for different use cases:
- **SINGLE_IMAGE_MODE**: Used for tap-to-capture (more accurate)
- **STREAM_MODE**: Used for continuous scanning (faster)

**Why**: Single image mode performs more thorough analysis, improving detection rates for static captures.

***REMOVED******REMOVED******REMOVED*** 2. **Increased Image Resolution** (CameraXManager.kt:77)
- Changed target resolution from default to **1280x720**
- Higher resolution = more detail for ML Kit to analyze

**Why**: Low resolution images may not have enough detail for ML Kit to identify objects.

***REMOVED******REMOVED******REMOVED*** 3. **ML Kit Model Auto-Download** (AndroidManifest.xml:20-23)
Added metadata to ensure ML Kit models are downloaded at app install:
```xml
<meta-data
    android:name="com.google.mlkit.vision.DEPENDENCIES"
    android:value="ocr,object_custom" />
```

**Why**: Critical! If the model isn't downloaded, detection will fail silently.

***REMOVED******REMOVED******REMOVED*** 4. **Enhanced Logging** (Throughout)
Added comprehensive debug logging to track:
- Detector mode being used
- Image dimensions and rotation
- Number of objects detected
- Object labels and confidence scores
- Conversion to ScannedItems

**Why**: Essential for diagnosing where detection is failing.

***REMOVED******REMOVED******REMOVED*** 5. **User Feedback** (CameraScreen.kt:95-108)
Added Toast notifications:
- Success: "Detected X object(s)"
- Failure: "No objects detected. Try pointing at prominent items."

**Why**: Immediate feedback helps users understand if the issue is with their technique or the system.

***REMOVED******REMOVED******REMOVED*** 6. **Better Error Handling** (CameraXManager.kt:184-219)
- Check for null mediaImage
- Catch bitmap conversion failures
- Log all errors with context

***REMOVED******REMOVED*** Testing Instructions

***REMOVED******REMOVED******REMOVED*** 1. Fresh Install
```bash
***REMOVED*** Uninstall old version first (to trigger model download)
adb uninstall com.example.objecta

***REMOVED*** Install new version
./gradlew installDebug
```

**IMPORTANT**: Uninstalling ensures the ML Kit model is freshly downloaded.

***REMOVED******REMOVED******REMOVED*** 2. Check Model Download
After installing, wait 10-20 seconds for the ML Kit model to download in the background. Check logcat:
```bash
adb logcat | grep -i "mlkit\|tflite\|object"
```

Look for messages about model download completion.

***REMOVED******REMOVED******REMOVED*** 3. Test Detection with Known Objects
ML Kit Object Detection works best with these categories:
- **Fashion**: Clothing, shoes, bags
- **Food**: Fruits, vegetables, packaged foods
- **Home Goods**: Furniture, appliances, household items
- **Places**: Buildings, landmarks (may not work well indoors)
- **Plants**: Flowers, trees

**Best practices**:
- Point camera at **prominent, well-lit objects**
- Use objects that fill 20-40% of the frame
- Avoid small or partially occluded items
- Ensure good lighting

***REMOVED******REMOVED******REMOVED*** 4. Monitor Logs
```bash
adb logcat -s CameraXManager:D ObjectDetectorClient:D
```

**What to look for**:
```
ObjectDetectorClient: Creating SINGLE_IMAGE_MODE detector
CameraXManager: captureSingleFrame: Starting single frame capture
CameraXManager: captureSingleFrame: Received image proxy 1280x720
ObjectDetectorClient: Starting object detection (SINGLE_IMAGE) on image 1280x720
ObjectDetectorClient: Detected 2 objects
ObjectDetectorClient: Object 0: labels=[Fashion:0.75], box=Rect(100, 200 - 400, 600)
ObjectDetectorClient: Converted to 2 scanned items
```

***REMOVED******REMOVED******REMOVED*** 5. Troubleshooting

***REMOVED******REMOVED******REMOVED******REMOVED*** If you see "Detected 0 objects":
- **Model not downloaded**: Wait longer or check Play Services
- **Object not in ML Kit categories**: Try items from the list above
- **Object too small**: Move closer or use larger items
- **Poor lighting**: Move to better lit area
- **Object not prominent**: Ensure object is clear and unobstructed

***REMOVED******REMOVED******REMOVED******REMOVED*** If you see no logs at all:
- Tap isn't being detected → Check gesture handling
- Camera not starting → Check permissions

***REMOVED******REMOVED******REMOVED******REMOVED*** If you see "Error detecting objects":
- ML Kit crash → Check full exception in logcat
- Memory issue → Restart app

***REMOVED******REMOVED*** ML Kit Limitations

**Important**: Google's ML Kit Object Detection has inherent limitations:

1. **Coarse Classification**: Only 5 categories (Fashion, Food, Home goods, Places, Plants)
2. **Prominent Objects Only**: Designed for clear, well-defined objects
3. **Not All Objects Detected**: Many everyday items may not trigger detection
4. **On-Device Model**: Limited compared to cloud-based alternatives
5. **No Custom Training**: Cannot add new object categories

***REMOVED******REMOVED*** Next Steps If Detection Still Fails

If objects are still not being detected after these improvements:

***REMOVED******REMOVED******REMOVED*** Option 1: Switch to ML Kit Image Labeling
- More general purpose than Object Detection
- Detects concepts/scenes, not just objects
- Higher detection rate but no bounding boxes

***REMOVED******REMOVED******REMOVED*** Option 2: Use Custom TensorFlow Lite Model
- Train your own object detection model
- Can detect specific items relevant to your use case
- More setup required

***REMOVED******REMOVED******REMOVED*** Option 3: Use Cloud-Based Detection
- Google Cloud Vision API
- Much more powerful but requires internet
- Costs money per API call

***REMOVED******REMOVED*** Build Output

Location: `/Users/family/dev/objecta/app/build/outputs/apk/debug/app-debug.apk`

Install command:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or use Android Studio's Run button.

***REMOVED******REMOVED*** Summary

The main issues were:
1. **Missing ML Kit model metadata** → Model wasn't auto-downloading
2. **Wrong detector mode** → STREAM_MODE less accurate than SINGLE_IMAGE_MODE
3. **Low image resolution** → Not enough detail for detection
4. **ML Kit inherent limitations** → Only detects prominent objects in 5 categories

***REMOVED******REMOVED*** Latest Update: Fixed Scanning Mode

**Issue**: Tap-to-capture worked, but long-press scanning didn't detect objects.

**Root Cause**: Scanning was using STREAM_MODE (optimized for speed) while tap used SINGLE_IMAGE_MODE (optimized for accuracy). STREAM_MODE is less accurate and was missing objects.

**Fix**: Changed scanning to use SINGLE_IMAGE_MODE as well (CameraXManager.kt:122-177)
- Both tap and scan now use the same accurate detector
- Increased scan interval from 600ms to 1000ms (1 second between detections)
- Added `isProcessing` flag to prevent overlapping processing
- Same detection quality as tap, just continuous

**Trade-off**: Scanning is now slightly slower (1 detection per second instead of ~1.6), but it actually detects objects now.

These changes should significantly improve detection rates for both tap and scan. However, if you're testing with items outside ML Kit's supported categories, detection may still be limited.

**Test with**: shoes, shirts, bottles, cups, fruits, potted plants for best results.

***REMOVED******REMOVED*** Latest Update: Object Tracking and De-Duplication System

**Issue**: In continuous scanning mode, the same physical object was being detected multiple times, creating duplicate entries in the items list.

**Root Cause**: Each frame's detections were immediately converted to ScannedItems without tracking whether they represented objects already seen. ML Kit's `trackingId` was being used for item IDs, but:
- `trackingId` is often null in SINGLE_IMAGE_MODE
- No multi-frame confirmation logic existed
- No spatial matching for detections without trackingId
- Same object at slightly different positions created new items

**Solution**: Implemented a comprehensive tracking and de-duplication system (see [TRACKING_IMPLEMENTATION.md](./TRACKING_IMPLEMENTATION.md) for details).

***REMOVED******REMOVED******REMOVED*** New Components Added

1. **ObjectCandidate** (`tracking/ObjectCandidate.kt`)
   - Intermediate representation for objects being tracked across frames
   - Stores: stable ID, bounding box, frame counts, confidence, category, thumbnail
   - Implements spatial matching helpers: IoU calculation, center distance

2. **ObjectTracker** (`tracking/ObjectTracker.kt`)
   - Core tracking engine with configurable thresholds
   - Maintains in-memory collection of candidates
   - Implements dual matching strategy:
     - Primary: ML Kit trackingId matching
     - Fallback: Spatial matching using IoU + center distance
   - Automatic expiry of stale candidates

3. **DetectionInfo** (data class in ObjectTracker.kt)
   - Raw detection metadata from ML Kit
   - Bridges ML Kit DetectedObject and ObjectTracker

***REMOVED******REMOVED******REMOVED*** How It Works

**Data Flow:**
```
ImageProxy → ML Kit (STREAM_MODE) → DetectionInfo[]
  ↓
ObjectTracker.processFrame()
  ├─ Match to existing candidates (trackingId or spatial)
  ├─ Update or create candidates
  ├─ Track frame counts, confidence, stability
  └─ Return newly confirmed candidates
  ↓
Convert confirmed candidates to ScannedItems
  ↓
ItemsViewModel (ID-based de-duplication)
```

**Confirmation Logic:**
Objects must meet ALL criteria before being added to the items list:
- Seen in ≥3 frames (`minFramesToConfirm`)
- Confidence ≥0.4 (`minConfidence`)
- Bounding box ≥0.1% of frame (`minBoxArea`)

**Matching Strategy:**
1. **TrackingId Match**: If ML Kit provides trackingId (in STREAM_MODE), use it directly
2. **Spatial Match**: If no trackingId, match using:
   - Intersection over Union (IoU) - 70% weight
   - Center distance - 30% weight
   - Combined score must be ≥0.3

***REMOVED******REMOVED******REMOVED*** Configuration

Tracking behavior is controlled by `TrackerConfig`:
```kotlin
TrackerConfig(
    minFramesToConfirm = 3,      // Require 3 frames to confirm
    minConfidence = 0.4f,         // Minimum 0.4 confidence
    minBoxArea = 0.001f,          // Minimum 0.1% of frame
    maxFrameGap = 5,              // Allow 5 frames gap for matching
    minMatchScore = 0.3f,         // Spatial matching threshold
    expiryFrames = 10             // Expire after 10 frames without detection
)
```

***REMOVED******REMOVED******REMOVED*** Integration Points

**CameraXManager Changes:**
- Added `ObjectTracker` instance with configured thresholds
- Routes OBJECT_DETECTION in STREAM mode through tracking pipeline
- Resets tracker on mode changes and scan session boundaries
- Single-shot tap captures bypass tracking (backward compatible)

**ObjectDetectorClient Changes:**
- New `detectObjectsWithTracking()` method extracts DetectionInfo
- New `extractDetectionInfo()` converts DetectedObject to tracking metadata
- New `candidateToScannedItem()` converts confirmed candidates to items
- Uses STREAM_MODE for better trackingId availability

**ItemsViewModel:**
- No changes required! Existing ID-based de-duplication works seamlessly with stable tracking IDs

***REMOVED******REMOVED******REMOVED*** Testing

Created comprehensive test suite:
- **ObjectCandidateTest**: 13 unit tests for candidate data class and spatial helpers
- **ObjectTrackerTest**: 22 unit tests covering:
  - Candidate creation and matching
  - Confirmation thresholds
  - Expiry logic
  - Spatial matching fallback
  - Multi-frame tracking
- **TrackingPipelineIntegrationTest**: 9 integration tests for realistic scenarios:
  - Single object confirmed after movement
  - Multiple objects confirmed independently
  - Object lost and found
  - Object exits and expires
  - Spatial matching without trackingId
  - Category refinement over time
  - Noise filtering
  - Reset between scan sessions

***REMOVED******REMOVED******REMOVED*** Benefits

✅ **Eliminates Duplicates**: Each physical object appears only once per scan session
✅ **Stable Detection**: Multi-frame confirmation filters out noise and false detections
✅ **Robust Matching**: Works with or without ML Kit trackingId
✅ **Memory Efficient**: Automatic expiry prevents unbounded growth
✅ **Tunable**: All thresholds configurable for different use cases
✅ **Well-Tested**: 44 tests covering unit and integration scenarios
✅ **Backward Compatible**: Single-shot and other modes unchanged

***REMOVED******REMOVED******REMOVED*** Monitoring

**Logs to Watch:**
```bash
adb logcat | grep -E "ObjectTracker|CameraXManager.*tracking"
```

**Example Output:**
```
ObjectTracker: Created new candidate gen_abc123: FASHION (Shirt)
ObjectTracker: Updated candidate gen_abc123: seenCount=2, maxConfidence=0.7
ObjectTracker: ✓ CONFIRMED candidate gen_abc123: FASHION (Shirt) after 3 frames
ObjectTracker: Tracker stats: active=1, confirmed=1, frame=3
```

***REMOVED******REMOVED******REMOVED*** Trade-offs

**Pros:**
- Dramatically reduced duplicates
- Improved user experience
- Better detection quality (multi-frame confirmation)

**Cons:**
- 3-frame delay before objects appear (~3 seconds at 1 detection/sec)
- Slight processing overhead (IoU calculations)
- Memory usage for tracking state (mitigated by expiry)

***REMOVED******REMOVED******REMOVED*** Future Enhancements

- **Color Matching**: Extract dominant color for improved spatial matching
- **Adaptive Thresholds**: Adjust based on scene complexity and frame rate
- **Persistence**: Save tracker state across app restarts
- **Performance Metrics**: Track and log frame processing times

***REMOVED******REMOVED******REMOVED*** Debugging Tips

**If same object still appears multiple times:**
1. Check logs for "Created new candidate" - should see updates, not new candidates
2. Verify ML Kit is providing trackingId in STREAM_MODE
3. If trackingId is null, check spatial matching scores
4. Consider tightening `minMatchScore` threshold

**If objects take too long to appear:**
1. Reduce `minFramesToConfirm` (default 3)
2. Lower `minConfidence` (default 0.4)
3. Reduce `minBoxArea` (default 0.001)

**If objects disappear and reappear:**
1. Increase `maxFrameGap` (default 5 frames)
2. Increase `expiryFrames` (default 10 frames)

For detailed implementation documentation, see [TRACKING_IMPLEMENTATION.md](./TRACKING_IMPLEMENTATION.md).
