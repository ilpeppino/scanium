***REMOVED*** Vertical Threshold Slider - Real-Time Detection Tuning

***REMOVED******REMOVED*** Overview

The **Vertical Threshold Slider** is a UI control on the Camera screen that allows real-time adjustment of the similarity threshold used in the item aggregation pipeline. This feature serves both as a **debugging tool** for developers and as a potential **advanced feature** for users.

***REMOVED******REMOVED*** Visual Design

The slider appears on the **left side** of the Camera screen, vertically centered:

```
┌─────────────────────────────────────┐
│  SCANIUM              Items (5)      │
│                                      │
│  ┌──────┐                            │
│  │THRESH│                            │
│  │ 55% │◄─ Current value            │
│  │      │                            │
│  │  ●   │◄─ Draggable thumb          │
│  │  █   │                            │
│  │  █   │                            │
│  │  █   │                            │
│  │ HIGH │                            │
│  │  ↕   │                            │
│  │ LOW  │                            │
│  └──────┘                            │
│                                      │
│                                      │
│     Tap to capture • Long-press      │
│          to scan                     │
└─────────────────────────────────────┘
```

***REMOVED******REMOVED******REMOVED*** Color Scheme

- **Track (background)**: Deep Navy (`***REMOVED***050B18`)
- **Active Track**: Scanium Blue (`***REMOVED***1F7BFF`)
- **Thumb**: Scanium Blue (normal), Cyan Glow (`***REMOVED***00D4FF`) when dragging
- **Background**: Black with 70% opacity
- **Text**: White with varying opacity

***REMOVED******REMOVED******REMOVED*** Interaction

- **Drag up**: Increase threshold (stricter matching)
- **Drag down**: Decrease threshold (looser matching)
- **Visual feedback**: Thumb grows and glows cyan when dragging
- **Smooth animations**: 150ms transitions for value changes

***REMOVED******REMOVED*** Functionality

***REMOVED******REMOVED******REMOVED*** Threshold Control

The slider controls the **similarity threshold** in the aggregation system:

- **Range**: 0.0 (0%) to 1.0 (100%)
- **Default**: 0.55 (55%) - from REALTIME preset
- **Display**: Percentage (e.g., "55%")

***REMOVED******REMOVED******REMOVED*** Effect on Detection

**Higher threshold (↑):**
- Stricter similarity matching
- Fewer items detected/aggregated
- More confident matches only
- Less duplicate merging

**Lower threshold (↓):**
- Looser similarity matching
- More items detected/aggregated
- Accept less confident matches
- More aggressive merging

***REMOVED******REMOVED******REMOVED*** Real-Time Updates

Changes to the slider take effect **immediately**:

1. User drags slider
2. `ItemsViewModel.updateSimilarityThreshold()` called
3. `ItemAggregator.updateSimilarityThreshold()` updates internal threshold
4. Next detection uses new threshold
5. No camera restart required

***REMOVED******REMOVED*** Architecture

***REMOVED******REMOVED******REMOVED*** Component Flow

```
┌─────────────────────────────────────────────────────────┐
│  CameraScreen (Compose)                                  │
│  ├─ VerticalThresholdSlider                             │
│  │  └─ onValueChange(newThreshold)                      │
│  │      ↓                                                │
│  └─ ItemsViewModel                                       │
│     └─ updateSimilarityThreshold(threshold)             │
│         ↓                                                │
│        ItemAggregator                                    │
│        └─ updateSimilarityThreshold(threshold)          │
│            ↓                                             │
│           processDetection() uses current threshold      │
│            ↓                                             │
│           Merge or Create decision                       │
└─────────────────────────────────────────────────────────┘
```

***REMOVED******REMOVED******REMOVED*** Key Components

***REMOVED******REMOVED******REMOVED******REMOVED*** 1. VerticalThresholdSlider.kt

**Location**: `app/src/main/java/com/scanium/app/camera/VerticalThresholdSlider.kt`

Custom Compose component that renders the vertical slider UI.

**Props**:
- `value: Float` - Current threshold (0-1)
- `onValueChange: (Float) -> Unit` - Callback when slider changes
- `modifier: Modifier` - Positioning and styling

**Features**:
- Vertical drag gesture detection
- Smooth animations
- Visual feedback (glow on drag)
- Percentage display

***REMOVED******REMOVED******REMOVED******REMOVED*** 2. ItemsViewModel.kt

**Location**: `app/src/main/java/com/scanium/app/items/ItemsViewModel.kt`

**New Properties**:
```kotlin
private val _similarityThreshold = MutableStateFlow(0.55f)
val similarityThreshold: StateFlow<Float>
```

**New Methods**:
```kotlin
fun updateSimilarityThreshold(threshold: Float)
fun getCurrentSimilarityThreshold(): Float
```

***REMOVED******REMOVED******REMOVED******REMOVED*** 3. ItemAggregator.kt

**Location**: `app/src/main/java/com/scanium/app/aggregation/ItemAggregator.kt`

**New Properties**:
```kotlin
private var dynamicThreshold: Float? = null
```

**New Methods**:
```kotlin
fun updateSimilarityThreshold(threshold: Float?)
fun getCurrentSimilarityThreshold(): Float
```

**Updated Logic**:
- `processDetection()` now uses `getCurrentSimilarityThreshold()` instead of hardcoded config value
- Logs include current threshold for debugging

***REMOVED******REMOVED******REMOVED******REMOVED*** 4. CameraScreen.kt

**Location**: `app/src/main/java/com/scanium/app/camera/CameraScreen.kt`

**Integration**:
```kotlin
val currentThreshold by itemsViewModel.similarityThreshold.collectAsState()
VerticalThresholdSlider(
    value = currentThreshold,
    onValueChange = { newValue ->
        itemsViewModel.updateSimilarityThreshold(newValue)
    },
    modifier = Modifier
        .align(Alignment.CenterStart)
        .padding(start = 16.dp)
)
```

***REMOVED******REMOVED*** Usage Examples

***REMOVED******REMOVED******REMOVED*** Scenario 1: Too Many Duplicates

**Problem**: Same object appearing multiple times when panning camera

**Solution**: Increase threshold
- Drag slider upward
- Try 70-80% (0.7-0.8)
- Stricter matching reduces false merges

***REMOVED******REMOVED******REMOVED*** Scenario 2: Items Not Appearing

**Problem**: Objects not being detected/aggregated during scanning

**Solution**: Decrease threshold
- Drag slider downward
- Try 30-40% (0.3-0.4)
- Looser matching increases detections

***REMOVED******REMOVED******REMOVED*** Scenario 3: Debug Pipeline

**Problem**: Understanding why items merge or stay separate

**Solution**: Experiment with threshold
- Start at 50%
- Move slider while scanning
- Check logs for similarity scores vs threshold
- Find optimal value for your use case

***REMOVED******REMOVED*** Persistence (Optional)

***REMOVED******REMOVED******REMOVED*** Current State

The threshold is **in-memory only** - resets to default (55%) when app restarts.

***REMOVED******REMOVED******REMOVED*** Adding Persistence

A `ThresholdPreferences` utility is provided for DataStore-based persistence:

**Location**: `app/src/main/java/com/scanium/app/data/ThresholdPreferences.kt`

**To Enable**:

1. Convert `ItemsViewModel` to `AndroidViewModel`:
   ```kotlin
   class ItemsViewModel(application: Application) : AndroidViewModel(application)
   ```

2. Add ThresholdPreferences:
   ```kotlin
   private val thresholdPreferences = ThresholdPreferences(application)
   ```

3. Load on init:
   ```kotlin
   init {
       viewModelScope.launch {
           thresholdPreferences.similarityThreshold.collect { savedThreshold ->
               _similarityThreshold.value = savedThreshold
               itemAggregator.updateSimilarityThreshold(savedThreshold)
           }
       }
   }
   ```

4. Save on update:
   ```kotlin
   fun updateSimilarityThreshold(threshold: Float) {
       val clampedThreshold = threshold.coerceIn(0f, 1f)
       _similarityThreshold.value = clampedThreshold
       itemAggregator.updateSimilarityThreshold(clampedThreshold)

       viewModelScope.launch {
           thresholdPreferences.saveSimilarityThreshold(clampedThreshold)
       }
   }
   ```

5. Update CameraScreen viewModel creation:
   ```kotlin
   itemsViewModel: ItemsViewModel = viewModel(
       factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
           LocalContext.current.applicationContext as Application
       )
   )
   ```

***REMOVED******REMOVED*** Debugging

***REMOVED******REMOVED******REMOVED*** Log Output

When threshold changes:
```
ItemsViewModel: Similarity threshold updated to: 0.65
ItemAggregator: Similarity threshold updated to: 0.65
```

When processing detections:
```
ItemAggregator: >>> processDetection: id=det_1, category=FASHION,
                    confidence=0.8, threshold=0.65
ItemAggregator:     ✓ MERGE: detection det_2 → aggregated agg_123
                    (similarity=0.72 >= 0.65)
```

Or:
```
ItemAggregator:     ✗ CREATE NEW: similarity too low (0.58 < 0.65)
ItemAggregator:     Similarity breakdown:
ItemAggregator:       - Category match: true (FASHION vs FASHION)
ItemAggregator:       - Label similarity: 0.65
ItemAggregator:       - Size similarity: 0.58
ItemAggregator:       - Distance similarity: 0.52
ItemAggregator:       - Final weighted score: 0.58
```

***REMOVED******REMOVED******REMOVED*** Log Tags

- `VerticalThresholdSlider` - Slider interactions (if logging added)
- `ItemsViewModel` - Threshold updates
- `ItemAggregator` - Aggregation decisions with threshold

***REMOVED******REMOVED*** Testing

***REMOVED******REMOVED******REMOVED*** Manual Testing

1. **Build and run** the app
2. **Navigate** to Camera screen
3. **Verify slider appears** on left side
4. **Tap and drag** slider up/down
5. **Check percentage** updates smoothly
6. **Start scanning** (long-press)
7. **Adjust threshold** while scanning
8. **Observe** item count changes in real-time
9. **Check logs** for threshold values

***REMOVED******REMOVED******REMOVED*** Expected Behavior

✅ Slider visible on Camera screen
✅ Slider positioned on left, vertically centered
✅ Dragging changes percentage display
✅ Dragging changes detection behavior immediately
✅ No lag or stuttering in camera preview
✅ Slider works during active scanning
✅ Default value is 55%
✅ Value bounded between 0% and 100%

***REMOVED******REMOVED******REMOVED*** Edge Cases

- **Rapid dragging**: Should handle smoothly without crashes
- **Extreme values** (0% or 100%): Should still work, just very loose/strict
- **During scanning**: Should update without interrupting scan
- **Camera rotation**: Slider should remain visible and functional

***REMOVED******REMOVED*** UI Placement Considerations

***REMOVED******REMOVED******REMOVED*** Why Left Side?

- **Right-handed users**: Most users hold phone with right hand, interact with right thumb
- **Left side is less crowded**: Critical controls (Items button, mode switcher) on right/center
- **Easy reach**: Left edge accessible with left hand or by shifting grip
- **No conflicts**: Doesn't block camera controls or detected objects

***REMOVED******REMOVED******REMOVED*** Padding & Spacing

- **Left padding**: 16dp from screen edge
- **Vertical centering**: Aligned to center of screen
- **Component height**: ~320dp total (slider + labels + padding)
- **Touch target**: 40dp width (thumb area)
- **Safe area**: Avoids top bar and bottom controls

***REMOVED******REMOVED*** Future Enhancements

***REMOVED******REMOVED******REMOVED*** Potential Improvements

1. **Toggle Visibility**
   - Show/hide slider based on debug mode or settings
   - Gesture to reveal (e.g., swipe from left edge)

2. **Preset Buttons**
   - Quick buttons for: STRICT (80%), BALANCED (55%), LOOSE (30%)
   - Snap to preset values

3. **Haptic Feedback**
   - Vibrate when reaching 0% or 100%
   - Subtle feedback during drag

4. **Visual Indicators**
   - Show recommended range (40-70%)
   - Color-code zones (red=too low, green=optimal, orange=too high)

5. **Session Statistics**
   - Track detection/merge rates at different thresholds
   - Suggest optimal threshold based on scene

6. **Multiple Thresholds**
   - Separate sliders for different factors (category, label, spatial)
   - Advanced mode with full control

***REMOVED******REMOVED*** Accessibility

***REMOVED******REMOVED******REMOVED*** Current Features

- **Large touch target**: 40dp width for easy interaction
- **Visual feedback**: Clear color changes when dragging
- **Numeric display**: Percentage shown in legible font size

***REMOVED******REMOVED******REMOVED*** Future Improvements

- **Content descriptions**: Add semantic labels for screen readers
- **Haptic feedback**: Vibration for blind/low-vision users
- **Voice control**: "Set threshold to 70 percent"
- **High contrast mode**: Enhanced visibility in bright sunlight

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** Slider Not Appearing

**Causes**:
- Build error
- Missing import in CameraScreen
- ViewModel not properly instantiated

**Solutions**:
- Check Logcat for errors
- Verify `VerticalThresholdSlider` is imported
- Ensure `itemsViewModel` is available in CameraScreen scope

***REMOVED******REMOVED******REMOVED*** Slider Not Responding

**Causes**:
- Gesture conflicts with camera preview
- ViewModel method not wired correctly

**Solutions**:
- Verify `onValueChange` callback is connected
- Check that slider is above preview in Z-order
- Review pointer input handling in slider code

***REMOVED******REMOVED******REMOVED*** Threshold Not Affecting Detections

**Causes**:
- ItemAggregator not using dynamic threshold
- ViewModel not calling aggregator update method

**Solutions**:
- Check `ItemAggregator.getCurrentSimilarityThreshold()` in logs
- Verify `updateSimilarityThreshold()` chain is complete
- Ensure `processDetection()` uses `getCurrentSimilarityThreshold()`

***REMOVED******REMOVED******REMOVED*** Values Reset After App Restart

**Causes**:
- Persistence not implemented (current state)

**Solutions**:
- This is expected behavior without persistence
- Follow "Adding Persistence" section to enable DataStore
- Or accept default value on app start (by design for debugging)

***REMOVED******REMOVED*** References

***REMOVED******REMOVED******REMOVED*** Source Files

- `app/src/main/java/com/scanium/app/camera/VerticalThresholdSlider.kt` - Slider UI component
- `app/src/main/java/com/scanium/app/camera/CameraScreen.kt` - Integration point
- `app/src/main/java/com/scanium/app/items/ItemsViewModel.kt` - State management
- `app/src/main/java/com/scanium/app/aggregation/ItemAggregator.kt` - Detection logic
- `app/src/main/java/com/scanium/app/data/ThresholdPreferences.kt` - Persistence utility
- `app/src/main/java/com/scanium/app/ui/theme/Color.kt` - Brand colors

***REMOVED******REMOVED******REMOVED*** Related Documentation

- `docs/AGGREGATION_SYSTEM.md` - Aggregation system overview
- `docs/TRACKING.md` - ObjectTracker frame-level logic
- `docs/ML_KIT_INTEGRATION.md` - ML Kit detection pipeline

---

**Last Updated**: 2025-12-10
**Version**: 1.0.0
**Status**: Production Ready
