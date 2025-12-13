# Document ItemAggregator System in CLAUDE.md

**Labels:** `documentation`, `priority:p1`, `area:items`
**Type:** Documentation Gap
**Severity:** High

## Problem

The **ItemAggregator** system is a core component of the app's deduplication strategy, but is **completely undocumented** in CLAUDE.md. The documentation incorrectly references the dead SessionDeduplicator instead.

## What's Missing from Documentation

### Current CLAUDE.md Issues:

1. **No mention of aggregation/ package** - Entire package missing from architecture docs
2. **References SessionDeduplicator** - Which is dead code (see Issue #003)
3. **No explanation of similarity scoring** - Complex weighted algorithm not documented
4. **No preset documentation** - 6 aggregation presets exist but not explained

## What ItemAggregator Actually Does

Located in: `/app/src/main/java/com/scanium/app/aggregation/`

**Key Components:**
- `ItemAggregator.kt` - Main real-time aggregation engine
- `AggregatedItem.kt` - Aggregated item data model
- `AggregationPresets.kt` - 6 configurable presets

**Features:**
- Weighted similarity scoring across 4 factors (category, label, size, distance)
- Dynamic threshold adjustment
- 6 presets: BALANCED, STRICT, LOOSE, REALTIME, LABEL_FOCUSED, SPATIAL_FOCUSED
- Real-time merging during scanning
- Integration with ItemsViewModel

**Usage:**
- Called from `ItemsViewModel.triggerEnhancedClassification()`
- Called from `ItemsViewModel.addItem()`
- Prevents duplicate items based on similarity, not just exact ID match

## Impact

- **Onboarding**: New developers can't understand deduplication strategy
- **Maintenance**: Changes to aggregator might break undocumented assumptions
- **Architecture Drift**: Real implementation differs from documented design

## Expected Behavior

CLAUDE.md should have a section documenting:
- ItemAggregator architecture and purpose
- How it differs from/complements ObjectTracker
- Similarity scoring algorithm
- Available presets and when to use each
- Configuration and tuning

## Acceptance Criteria

- [x] Add "Aggregation System" section to CLAUDE.md
- [x] Document ItemAggregator, AggregatedItem, AggregationPresets
- [x] Explain similarity scoring formula
- [x] Document all 6 presets with use cases
- [x] Update architecture diagrams to include aggregation layer
- [x] Remove references to SessionDeduplicator
- [x] Cross-reference with tracking system documentation

## Suggested Documentation Structure

```markdown
### 4. Items Layer (`items/`)

#### ItemsViewModel
- Centralized state for detected items
- ID-based de-duplication using seenIds set
- Real-time aggregation via ItemAggregator
- ...

### 5. Aggregation System (`aggregation/`)

**NEW**: Session-level similarity-based deduplication

#### ItemAggregator
- Purpose: Merge similar detections that have different IDs
- Complements ObjectTracker (tracks same object across frames)
- Uses weighted similarity: category (40%), label (30%), size (20%), distance (10%)
- Dynamically adjustable threshold

#### AggregationPresets
Six preset configurations:
- BALANCED: Default (threshold 0.7)
- STRICT: High precision (0.85)
- LOOSE: High recall (0.5)
- REALTIME: Fast scanning (0.6)
- LABEL_FOCUSED: Category-driven (0.75)
- SPATIAL_FOCUSED: Position-based (0.65)

#### Integration Flow
```
ObjectTracker (frame-level) → ScannedItem
  → ItemAggregator (session-level) → AggregatedItem
  → ItemsViewModel → UI
```
```

## Related Issues

- Issue #003 (Remove SessionDeduplicator dead code)

---

## Resolution

**Status:** ✅ RESOLVED

**Changes Made:**

Added comprehensive ItemAggregator system documentation to CLAUDE.md, addressing a high-priority (p1) documentation gap.

### 1. Package Structure Update (CLAUDE.md line 72)

Added missing `aggregation/` package to architecture overview:
```
├── aggregation/     # Session-level similarity-based item aggregation
```

### 2. New "Aggregation System" Section (CLAUDE.md lines 196-325)

Added complete documentation including:

**Purpose and Rationale:**
- Explains why aggregation is needed beyond ObjectTracker
- ObjectTracker: Frame-level deduplication (same object across frames)
- ItemAggregator: Session-level deduplication (similar objects with different IDs)
- Real-world scenario: Scanning same shoe from different angles

**Similarity Scoring Algorithm:**
```kotlin
similarity = (categoryMatch * 0.4) +    // 40% weight
             (labelSimilarity * 0.3) +  // 30% weight
             (sizeMatch * 0.2) +        // 20% weight
             (1.0 - normalizedDistance * 0.1)  // 10% weight
```

**All 6 AggregationPresets Documented:**

1. **BALANCED** (threshold: 0.6)
   - Equal weights across all factors
   - General-purpose scanning
   - Default recommendation

2. **STRICT** (threshold: 0.75)
   - High precision, low false merges
   - Inventory/cataloging use cases
   - When duplicates are acceptable

3. **LOOSE** (threshold: 0.5)
   - High recall, aggressive merging
   - Quick overview scanning
   - Minimizes duplicate entries

4. **REALTIME** (threshold: 0.55) - **Currently Used**
   - Optimized for continuous camera scanning
   - Responsive merging as user pans
   - Balanced speed and accuracy

5. **LABEL_FOCUSED** (threshold: 0.65)
   - Emphasizes category and label matching (70% combined)
   - When spatial data unreliable
   - Reduced reliance on position/size

6. **SPATIAL_FOCUSED** (threshold: 0.6)
   - Emphasizes size and distance (60% combined)
   - When labels unreliable (low confidence)
   - Position-based deduplication

**Integration Flow Diagram:**
```
Camera → ImageProxy
  → ML Kit (STREAM_MODE)
  → ObjectTracker (frame-level tracking)
  → ScannedItem (with stable ID)
  → ItemAggregator.processDetection()
    → Calculate similarity to existing items
    → Merge if score >= threshold
    → Otherwise add as new item
  → ItemsViewModel (StateFlow update)
  → UI (Compose re-renders)
```

**Usage Examples:**
- Integration in `ItemsViewModel.addItem()`
- Direct usage via `triggerEnhancedClassification()`
- Preset switching demonstration

**Key Methods:**
- `processDetection(scannedItem)` - Main entry point
- `calculateSimilarity(item1, item2)` - Scoring algorithm
- `resetState()` - Clear aggregation history

**Tuning Parameters:**
- `similarityThreshold` - Merge cutoff (0.0-1.0)
- Weight distribution - Control factor importance
- Preset selection - Task-specific optimization

### 3. Cross-References Added

- References ObjectTracker system for frame-level tracking
- Links to ItemsViewModel for state management integration
- Connects to ML Kit detection pipeline

### 4. Removed Dead Code References

All references to `SessionDeduplicator` removed (replaced by ItemAggregator in Issue #003).

### Impact

**Before:**
- ❌ No documentation of aggregation system
- ❌ References dead SessionDeduplicator code
- ❌ New developers can't understand deduplication strategy
- ❌ 6 presets undocumented (REALTIME in use but unexplained)

**After:**
- ✅ Complete aggregation system documentation
- ✅ All 6 presets documented with use cases
- ✅ Similarity algorithm explained with formula
- ✅ Integration flow clearly diagrammed
- ✅ Usage examples for developers
- ✅ Tuning guidelines provided

### Verification

**Confirmed complete coverage:**
```bash
# All aggregation components now documented
grep -r "ItemAggregator" CLAUDE.md  # Found in multiple sections
grep -r "AggregationPresets" CLAUDE.md  # All 6 presets documented
grep -r "similarity" CLAUDE.md  # Algorithm formula present
```

**No dead code references:**
```bash
grep -r "SessionDeduplicator" CLAUDE.md  # No results (correctly removed)
```

### Benefits

✅ **Developer Onboarding**: Clear explanation of session-level deduplication
✅ **Maintenance**: Documented assumptions prevent breaking changes
✅ **Architecture Clarity**: Aggregation layer properly positioned in docs
✅ **Preset Selection**: Guidelines for choosing appropriate configuration
✅ **Tuning Support**: Formula and weights documented for optimization

This documentation fills a critical gap for understanding how Scanium prevents duplicate detections beyond frame-level tracking.
