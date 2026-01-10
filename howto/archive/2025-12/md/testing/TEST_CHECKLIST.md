# ML Kit Fix - Testing Checklist

## Build & Deploy
- [x] Code changes implemented
- [x] Push to `main` branch (CI builds APK automatically)
- [ ] Download `scanium-app-debug-apk` artifact from GitHub Actions (see `docs/CI_TESTING.md`)
- [ ] Install APK on device (enable "Install unknown apps" if needed)

## Expected Log Output (Success Case)

When scanning objects, you should now see logs like:

```
I ObjectDetectorClient: ======================================
I ObjectDetectorClient: Creating STREAM_MODE detector
I ObjectDetectorClient: Config: mode=STREAM, multipleObjects=true, classification=FALSE
I ObjectDetectorClient: This should detect more objects (less conservative)
I ObjectDetectorClient: ======================================

I ObjectDetectorClient: >>> detectObjectsWithTracking START: mode=STREAM, image=1280x720, rotation=90, format=35
I ObjectDetectorClient: >>> Detector initialized: com.google.mlkit...
I ObjectDetectorClient: >>> ML Kit process() SUCCESS - returned 3 objects
I ObjectDetectorClient: >>> ML Kit returned 3 raw objects
I ObjectDetectorClient:     Object 0: trackingId=1, labels=[], box=Rect(100, 100 - 300, 300)
I ObjectDetectorClient:     Object 1: trackingId=2, labels=[], box=Rect(400, 200 - 600, 500)
I ObjectDetectorClient:     Object 2: trackingId=3, labels=[], box=Rect(50, 400 - 250, 700)
I ObjectDetectorClient: >>> Extracted 3 DetectionInfo objects
```

## Key Differences from Before

### Before (0 objects)
```
I ObjectDetectorClient: >>> ML Kit returned 0 raw objects
I ObjectDetectorClient: >>> Extracted 0 DetectionInfo objects
```

### After (should see objects)
```
I ObjectDetectorClient: >>> ML Kit returned N raw objects  (N > 0)
I ObjectDetectorClient:     Object 0: trackingId=X, labels=[], box=Rect(...)
```

## Testing Steps

### 1. Basic Detection Test
1. Launch app
2. Point camera at 2-3 distinct objects (cup, phone, book, etc.)
3. Long-press to scan
4. **Expected**:
   - See "SCANNING..." indicator
   - Logcat shows: `ML Kit returned N raw objects` where N > 0
   - After 3 frames, items appear in detected items list

### 2. Check Labels
- Open logcat filter for `ObjectDetectorClient`
- Look for `labels=[]` in detection logs
- **This is normal!** Classification is disabled, so labels will be empty
- Objects should still be detected based on shape

### 3. Verify Item List
1. Tap "Items (N)" button after scanning
2. **Expected**:
   - See thumbnails of detected objects
   - Category shows as "Unknown" (because labels are empty)
   - Price range shows EUR values (e.g., "€3 - €15")

### 4. Verify Tracking
1. Long-press and slowly move camera
2. **Expected**:
   - Same object gets same tracking ID across frames
   - Only new unique objects get added (not duplicates)
   - Logcat shows tracking IDs reused: `trackingId=123` appears in multiple frames

## Troubleshooting

### Still seeing 0 objects?

Check these logs:

1. **Model download failure**:
   ```
   E ObjectDetectorClient: >>> ML Kit process() FAILED
   E ObjectDetectorClient: >>> Exception type: MlKitException
   E ObjectDetectorClient: >>> Exception message: Model not available
   ```
   **Solution**: Ensure device has internet, restart app to retry download

2. **Image format issue**:
   ```
   W ObjectDetectorClient: >>> Image details: 0x0, rotation=0
   ```
   **Solution**: Camera not providing frames, check CameraX initialization

3. **Detection but mapping failed**:
   ```
   I ObjectDetectorClient: >>> ML Kit returned 3 raw objects
   I ObjectDetectorClient: >>> Extracted 0 DetectionInfo objects
   ```
   **Solution**: Check `extractDetectionInfo()` error logs

### False positives (too many objects)?

If detecting background/noise as objects:

- This is expected with classification disabled
- Tracking pipeline will filter out weak/transient detections
- Only objects seen in 3+ frames are confirmed
- Adjust `MIN_CONFIDENCE` threshold if needed (currently 0.3)

## Success Criteria

✅ **Fix is working if**:
1. Logcat shows `ML Kit returned N raw objects` with N > 0
2. Objects have `trackingId` values
3. Labels are empty `labels=[]` (expected)
4. Items appear in list with EUR pricing
5. Category shows "Unknown" (acceptable for PoC)

❌ **Fix NOT working if**:
1. Still seeing `ML Kit returned 0 raw objects`
2. Seeing ML Kit process() FAILED errors
3. No items ever appear in list after scanning

## Performance Notes

- Detection without classification is **faster** than with classification
- Expect 30-50ms per frame on modern devices
- Stream mode optimized for continuous scanning
- Tracking IDs help avoid redundant processing

## Next Steps After Verification

If fix works:
1. Test on multiple object types (household items, electronics, etc.)
2. Test in different lighting conditions
3. Test camera at different distances
4. Consider adding classification back as separate step (optional)

If fix doesn't work:
1. Share full logcat output (filter: `ObjectDetectorClient`)
2. Check for ML Kit download errors
3. Verify device compatibility (minSdk 24+)
4. Try single-shot detection (tap) vs scanning (long-press)

---

## TalkBack Accessibility Testing

### Pre-Test Setup
- [ ] Enable TalkBack: Settings → Accessibility → TalkBack → ON
- [ ] Enable "Explore by touch"
- [ ] Familiarize with gestures: single finger swipe (navigate), double tap (activate)

### CameraScreen Tests

#### Navigation Order
- [ ] Swipe through elements in logical order: Settings → Mode icons → Logo → Items button → Shutter → Flip camera
- [ ] Verify traversal order matches visual layout (top-to-bottom, left-to-right)

#### Content Descriptions
- [ ] Settings button: Announces "Open settings"
- [ ] Mode icons: Announces mode name + selected state (e.g., "Items mode. Selected")
- [ ] Shutter button: Announces capture instructions based on state
- [ ] Items button: Announces "View items"
- [ ] Flip camera: Announces "Flip camera"
- [ ] Scanium logo: Announces "Scanium logo"

#### Touch Targets
- [ ] All buttons respond to double-tap gesture
- [ ] No "Touch target too small" warnings in Accessibility Scanner

### ItemsListScreen Tests

#### Touch Targets
- [ ] All buttons respond to double-tap
- [ ] Retry button meets 48dp minimum
- [ ] View listing button meets 48dp minimum

#### Card Navigation
- [ ] Each item card announces: label, price, confidence, status
- [ ] Selected state announced when toggling

#### Action Buttons
- [ ] Retry button: Announces "Retry classification for this item"
- [ ] View listing button: Announces "View listing on marketplace"
- [ ] Export button: Announces with context

### Validation Tools
- [ ] Run Android Accessibility Scanner on both screens
- [ ] Verify no critical issues flagged
- [ ] Test with Switch Access (optional)
