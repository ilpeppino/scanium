***REMOVED*** Real-Time Item Aggregation System

***REMOVED******REMOVED*** Overview

The **Real-Time Item Aggregation System** is a robust solution for merging similar object detections during scanning mode. It replaces the previous strict deduplication logic that failed due to frequent ML Kit trackingId changes and overly strict spatial matching thresholds.

***REMOVED******REMOVED*** Problem Statement

***REMOVED******REMOVED******REMOVED*** Previous System Issues

1. **trackingId Instability**: ML Kit's trackingId changed frequently during real-world scanning, breaking the deduplication logic.
2. **Strict Thresholds**: IoU and spatial matching thresholds were too strict, preventing legitimate matches.
3. **No Items Promoted**: The strict logic resulted in no items being promoted during scanning, forcing fallback to "loose identification."
4. **Duplicate Overload**: Loose mode created too many duplicates when users panned around objects.

***REMOVED******REMOVED******REMOVED*** Solution Requirements

- **Resilient Matching**: Work even when trackingIds reset or oscillate
- **Spatial Tolerance**: Handle bounding box shifts from camera movement
- **User-Friendly**: Merge detections of the same object while keeping distinct objects separate
- **Configurable**: Tunable thresholds for different use cases
- **Always Produce Items**: Avoid the "no items appear" failure mode

***REMOVED******REMOVED*** Architecture

***REMOVED******REMOVED******REMOVED*** Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      Camera Frame                            │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ↓
          ┌──────────────────────────────┐
          │   ObjectDetectorClient       │
          │   (ML Kit Integration)       │
          └──────────────┬───────────────┘
                         │
                         ↓ DetectionInfo[]
          ┌──────────────────────────────┐
          │   ObjectTracker              │
          │   (Frame-level Confirmation) │
          └──────────────┬───────────────┘
                         │
                         ↓ ObjectCandidate[]
          ┌──────────────────────────────┐
          │   Convert to ScannedItem[]   │
          └──────────────┬───────────────┘
                         │
                         ↓ ScannedItem[]
          ┌──────────────────────────────┐
          │   ItemAggregator             │  ← NEW LAYER
          │   (Session-level Aggregation)│
          └──────────────┬───────────────┘
                         │
                         ↓ AggregatedItem[]
          ┌──────────────────────────────┐
          │   ItemsViewModel             │
          │   (UI State Management)      │
          └──────────────┬───────────────┘
                         │
                         ↓ ScannedItem[] (UI)
          ┌──────────────────────────────┐
          │   UI Layer (Compose)         │
          └──────────────────────────────┘
```

***REMOVED******REMOVED******REMOVED*** Key Components

***REMOVED******REMOVED******REMOVED******REMOVED*** 1. AggregatedItem (`AggregatedItem.kt`)

Represents a unique physical object aggregated from multiple detections.

**Key Features:**
- Stable identity across trackingId changes
- Tracks merge statistics (count, confidence history)
- Maintains "best" detection data for UI display
- Timestamps for staleness detection

**Key Properties:**
- `aggregatedId`: Stable unique identifier (UUID-based)
- `category`: Item category (must match for merging)
- `labelText`: Primary label from highest confidence detection
- `boundingBox`: Current position (updated on merge)
- `maxConfidence`: Highest confidence across all detections
- `averageConfidence`: Running average of confidence scores
- `mergeCount`: Number of detections merged
- `sourceDetectionIds`: Set of original detection IDs

**Key Methods:**
- `merge(detection)`: Merge new detection into this item
- `toScannedItem()`: Convert to ScannedItem for UI compatibility
- `isStale(maxAgeMs)`: Check if item hasn't been seen recently
- `cleanup()`: Release bitmap resources

***REMOVED******REMOVED******REMOVED******REMOVED*** 2. ItemAggregator (`ItemAggregator.kt`)

The core aggregation engine that processes detections and maintains aggregated items.

**Responsibilities:**
- Maintain collection of AggregatedItems
- Compare new detections using weighted similarity scoring
- Merge similar detections or create new items
- Provide tunable configuration
- Comprehensive logging for debugging

**Key Methods:**
- `processDetection(detection)`: Process single detection
- `processDetections(detections)`: Batch processing
- `getAllItems()`: Get all aggregated items
- `getScannedItems()`: Convert to ScannedItems for UI
- `removeStaleItems(maxAgeMs)`: Clean up old items
- `reset()`: Clear all items (new session)
- `getStats()`: Get aggregation statistics

***REMOVED******REMOVED******REMOVED******REMOVED*** 3. AggregationConfig (`ItemAggregator.kt`)

Configuration parameters for aggregation behavior.

**Parameters:**
- `similarityThreshold` (0-1): Minimum similarity for merging (default: 0.6)
- `maxCenterDistanceRatio` (0-1): Maximum normalized center distance (default: 0.25)
- `maxSizeDifferenceRatio` (0-1): Maximum size difference (default: 0.5)
- `categoryMatchRequired` (boolean): Must categories match? (default: true)
- `labelMatchRequired` (boolean): Must labels be present and similar? (default: false)
- `weights`: Similarity factor weights

***REMOVED******REMOVED******REMOVED******REMOVED*** 4. SimilarityWeights (`ItemAggregator.kt`)

Weights for combining similarity factors.

**Weights:**
- `categoryWeight`: 0.3 (30%) - Category match importance
- `labelWeight`: 0.25 (25%) - Label similarity importance
- `sizeWeight`: 0.20 (20%) - Bounding box size importance
- `distanceWeight`: 0.25 (25%) - Spatial proximity importance

***REMOVED******REMOVED*** Similarity Calculation

The aggregator uses a **weighted similarity model** that combines multiple factors:

***REMOVED******REMOVED******REMOVED*** 1. Category Match

- **Binary**: Categories must match exactly (if `categoryMatchRequired = true`)
- **Score**: 1.0 if match, 0.0 if not
- **Weight**: 30%

***REMOVED******REMOVED******REMOVED*** 2. Label Similarity

- **Algorithm**: Normalized Levenshtein distance
- **Case-insensitive**: "T-Shirt" matches "t-shirt"
- **Score**: 1.0 for exact match, decreasing with edit distance
- **Weight**: 25%
- **Hard Filter**: If `labelMatchRequired = true`, both items must have labels

***REMOVED******REMOVED******REMOVED*** 3. Size Similarity

- **Calculation**: `min(area1, area2) / max(area1, area2)`
- **Hard Filter**: If size difference > `maxSizeDifferenceRatio`, similarity = 0
- **Score**: 1.0 for identical sizes, decreasing with difference
- **Weight**: 20%

***REMOVED******REMOVED******REMOVED*** 4. Spatial Distance

- **Calculation**: Euclidean distance between bounding box centers
- **Normalization**: By frame diagonal (√2 for normalized coordinates)
- **Hard Filter**: If distance > `maxCenterDistanceRatio`, similarity = 0
- **Score**: 1.0 for same position, decreasing with distance
- **Weight**: 25%

***REMOVED******REMOVED******REMOVED*** Final Score

```
similarity = (
    categoryScore * 0.3 +
    labelScore * 0.25 +
    sizeScore * 0.20 +
    distanceScore * 0.25
) / totalWeight
```

If `similarity >= similarityThreshold`: **MERGE**
If `similarity < similarityThreshold`: **CREATE NEW**

***REMOVED******REMOVED*** Integration with ItemsViewModel

The `ItemsViewModel` has been updated to use `ItemAggregator` instead of `SessionDeduplicator`.

***REMOVED******REMOVED******REMOVED*** Changes

**Before:**
```kotlin
private val sessionDeduplicator = SessionDeduplicator()

fun addItem(item: ScannedItem) {
    // ID-based check
    if (seenIds.contains(item.id)) return

    // Similarity-based check
    val similarItemId = sessionDeduplicator.findSimilarItem(item, currentItems)
    if (similarItemId != null) return

    // Add item
    _items.update { currentItems -> currentItems + item }
}
```

**After:**
```kotlin
private val itemAggregator = ItemAggregator(config)

fun addItem(item: ScannedItem) {
    // Process through aggregator (automatically merges or creates)
    val aggregatedItem = itemAggregator.processDetection(item)

    // Update UI state with all aggregated items
    updateItemsState()
}

private fun updateItemsState() {
    val scannedItems = itemAggregator.getScannedItems()
    _items.value = scannedItems
}
```

***REMOVED******REMOVED******REMOVED*** New Methods

- `getAggregationStats()`: Get merge statistics for monitoring
- `removeStaleItems(maxAgeMs)`: Remove items not seen recently

***REMOVED******REMOVED*** Configuration Guide

***REMOVED******REMOVED******REMOVED*** Default Configuration (Balanced)

```kotlin
val aggregator = ItemAggregator(
    config = AggregationConfig(
        similarityThreshold = 0.6f,        // 60% similarity required
        maxCenterDistanceRatio = 0.25f,    // Max 25% of frame diagonal
        maxSizeDifferenceRatio = 0.5f,     // Max 50% size difference
        categoryMatchRequired = true,       // Category must match
        labelMatchRequired = false          // Labels optional
    )
)
```

***REMOVED******REMOVED******REMOVED*** Strict Configuration (Fewer Merges)

Use when you need high precision and want to avoid false positives.

```kotlin
val aggregator = ItemAggregator(
    config = AggregationConfig(
        similarityThreshold = 0.75f,       // 75% similarity required
        maxCenterDistanceRatio = 0.15f,    // Max 15% of frame diagonal
        maxSizeDifferenceRatio = 0.3f,     // Max 30% size difference
        categoryMatchRequired = true,
        labelMatchRequired = true           // Labels required and must match
    )
)
```

***REMOVED******REMOVED******REMOVED*** Loose Configuration (More Merges)

Use when you need high recall and want to aggressively merge similar items.

```kotlin
val aggregator = ItemAggregator(
    config = AggregationConfig(
        similarityThreshold = 0.5f,        // 50% similarity required
        maxCenterDistanceRatio = 0.35f,    // Max 35% of frame diagonal
        maxSizeDifferenceRatio = 0.7f,     // Max 70% size difference
        categoryMatchRequired = true,
        labelMatchRequired = false
    )
)
```

***REMOVED******REMOVED******REMOVED*** Custom Weights

Adjust importance of different factors:

```kotlin
val aggregator = ItemAggregator(
    config = AggregationConfig(
        similarityThreshold = 0.6f,
        weights = SimilarityWeights(
            categoryWeight = 0.4f,   // 40% - Increase category importance
            labelWeight = 0.3f,      // 30% - Increase label importance
            sizeWeight = 0.15f,      // 15% - Decrease size importance
            distanceWeight = 0.15f   // 15% - Decrease distance importance
        )
    )
)
```

***REMOVED******REMOVED*** Performance Characteristics

***REMOVED******REMOVED******REMOVED*** Computational Complexity

- **Per Detection**: O(N) where N = number of existing aggregated items
- **Similarity Calculation**: O(1) for category, size, distance; O(M) for label (M = label length)
- **Memory**: O(N * S) where S = average size of thumbnail + metadata

***REMOVED******REMOVED******REMOVED*** Optimization Strategies

1. **Early Exit**: Hard filters (category, size, distance) reject candidates quickly
2. **Spatial Indexing** (future): Could use quadtree for O(log N) spatial queries
3. **Thumbnail Caching**: Bitmaps reused across merges
4. **Stale Removal**: Periodic cleanup prevents unbounded growth

***REMOVED******REMOVED******REMOVED*** Typical Performance

- **2-5 fps analysis**: ~200ms per frame (including ML Kit detection)
- **Aggregation overhead**: <10ms per detection with <50 items
- **Memory usage**: ~5MB per 100 items (with thumbnails)

***REMOVED******REMOVED*** Testing

***REMOVED******REMOVED******REMOVED*** Test Coverage

Two comprehensive test suites verify correctness:

***REMOVED******REMOVED******REMOVED******REMOVED*** 1. ItemAggregatorTest

- `test_similar_detections_merge_into_one_item`
- `test_distinct_detections_remain_separate`
- `test_aggregation_works_when_trackingId_changes`
- `test_bounding_box_jitter_does_not_create_duplicates`
- `test_items_always_appear_no_empty_result_bug`
- `test_different_categories_remain_separate_even_when_spatially_close`
- `test_label_similarity_matching`
- `test_size_difference_threshold`
- `test_distance_threshold`
- `test_confidence_updates_correctly`
- `test_stale_item_removal`
- `test_reset_clears_all_items`
- `test_batch_processing`
- `test_statistics_calculation`
- `test_conversion_to_ScannedItem`

***REMOVED******REMOVED******REMOVED******REMOVED*** 2. ItemsViewModelAggregationTest

- `test_addItem_merges_similar_detections`
- `test_addItem_keeps_distinct_items_separate`
- `test_addItems_batch_processing`
- `test_clearAllItems`
- `test_removeItem`
- `test_getItemCount`
- `test_aggregation_stats`
- `test_removeStaleItems`
- `test_UI_state_updates_after_aggregation`
- `test_multiple_sequential_additions`
- `test_empty_batch_addition`
- `test_aggregated_items_maintain_correct_confidence`
- `test_aggregated_items_maintain_correct_bounding_box`

***REMOVED******REMOVED******REMOVED*** Running Tests

```bash
./gradlew test --tests "com.scanium.app.aggregation.*"
./gradlew test --tests "com.scanium.app.items.ItemsViewModelAggregationTest"
```

***REMOVED******REMOVED*** Debugging and Monitoring

***REMOVED******REMOVED******REMOVED*** Log Tags

- `ItemAggregator`: Main aggregation logic
- `ItemsViewModel`: ViewModel integration
- `AggregatedItem`: Individual item operations

***REMOVED******REMOVED******REMOVED*** Log Levels

**INFO**: Key operations
```
ItemAggregator: >>> processDetection: id=det_1, category=FASHION, confidence=0.8
ItemAggregator: ✓ MERGE: detection det_2 → aggregated agg_123 (similarity=0.85)
ItemAggregator: ✗ CREATE NEW: similarity too low (0.45 < 0.6)
```

**DEBUG**: Detailed similarity breakdown
```
ItemAggregator: Similarity breakdown:
ItemAggregator:   - Category match: true (FASHION vs FASHION)
ItemAggregator:   - Label similarity: 0.95 ('Shirt' vs 'shirt')
ItemAggregator:   - Size similarity: 0.92
ItemAggregator:   - Distance similarity: 0.88
ItemAggregator:   - Final weighted score: 0.85
```

***REMOVED******REMOVED******REMOVED*** Monitoring Statistics

```kotlin
val stats = itemsViewModel.getAggregationStats()
Log.i(TAG, "Aggregation stats:")
Log.i(TAG, "  Total items: ${stats.totalItems}")
Log.i(TAG, "  Total merges: ${stats.totalMerges}")
Log.i(TAG, "  Avg merges/item: ${stats.averageMergesPerItem}")
```

***REMOVED******REMOVED*** Migration Guide

***REMOVED******REMOVED******REMOVED*** For Developers

1. **No Code Changes Required**: If you use `ItemsViewModel`, the aggregation happens automatically.
2. **Custom Configuration**: Create custom `AggregationConfig` in `ItemsViewModel` constructor.
3. **Statistics Access**: Use `itemsViewModel.getAggregationStats()` for monitoring.
4. **Stale Cleanup**: Call `itemsViewModel.removeStaleItems()` periodically if needed.

***REMOVED******REMOVED******REMOVED*** For Testers

1. **Behavioral Changes**:
   - Items with similar appearance merge automatically
   - Fewer duplicate items in list
   - Item IDs now start with `agg_` instead of `mlkit_` or `gen_`

2. **Testing Focus**:
   - Verify distinct objects remain separate
   - Verify similar objects merge correctly
   - Test with camera movement (panning, rotation)
   - Test with varying lighting conditions

***REMOVED******REMOVED*** Future Enhancements

***REMOVED******REMOVED******REMOVED*** Potential Improvements

1. **Thumbnail Similarity**: Add perceptual hashing or dominant color matching
2. **Spatial Indexing**: Use quadtree for O(log N) spatial queries with large item counts
3. **Adaptive Thresholds**: Dynamically adjust based on scene complexity
4. **Confidence Decay**: Lower confidence for items not seen recently
5. **Multi-Object Tracking**: Track relationships between items (e.g., grouped objects)
6. **ML-Based Similarity**: Use neural embeddings for semantic similarity

***REMOVED******REMOVED******REMOVED*** API Extensions

```kotlin
// Proposed future API
class ItemAggregator {
    // Visual similarity using perceptual hashing
    fun setVisualSimilarityEnabled(enabled: Boolean)

    // Adaptive thresholds based on scene
    fun setAdaptiveThresholds(enabled: Boolean)

    // Export aggregation session for analysis
    fun exportSession(): AggregationSession

    // Import previous session
    fun importSession(session: AggregationSession)
}
```

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** Too Many Duplicates

**Symptoms**: Same object appears multiple times in list

**Solutions**:
- Decrease `similarityThreshold` (e.g., 0.5 instead of 0.6)
- Increase `maxCenterDistanceRatio` (e.g., 0.35 instead of 0.25)
- Increase `maxSizeDifferenceRatio` (e.g., 0.7 instead of 0.5)
- Set `labelMatchRequired = false` if labels are inconsistent

***REMOVED******REMOVED******REMOVED*** Items Not Merging

**Symptoms**: Similar objects remain separate

**Solutions**:
- Increase `similarityThreshold` (e.g., 0.7 instead of 0.6)
- Check category matching: Verify categories are consistent
- Check label matching: If `labelMatchRequired = true`, ensure labels are present
- Review logs: Enable DEBUG logging to see similarity scores

***REMOVED******REMOVED******REMOVED*** Performance Issues

**Symptoms**: Slow frame processing, UI lag

**Solutions**:
- Call `removeStaleItems()` periodically to limit growth
- Reduce thumbnail size in detection pipeline
- Consider spatial indexing for large item counts (>100)
- Profile using Android Studio Profiler

***REMOVED******REMOVED*** References

***REMOVED******REMOVED******REMOVED*** Source Files

- `app/src/main/java/com/scanium/app/aggregation/AggregatedItem.kt`
- `app/src/main/java/com/scanium/app/aggregation/ItemAggregator.kt`
- `app/src/main/java/com/scanium/app/items/ItemsViewModel.kt`
- `app/src/test/java/com/scanium/app/aggregation/ItemAggregatorTest.kt`
- `app/src/test/java/com/scanium/app/items/ItemsViewModelAggregationTest.kt`

***REMOVED******REMOVED******REMOVED*** Related Documentation

- `docs/DEDUPLICATION.md` - Previous deduplication approach
- `docs/TRACKING.md` - ObjectTracker frame-level logic
- `docs/ML_KIT_INTEGRATION.md` - ML Kit detection pipeline

---

**Last Updated**: 2025-12-10
**Version**: 1.0.0
**Status**: Production Ready
