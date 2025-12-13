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

- [ ] Add "Aggregation System" section to CLAUDE.md
- [ ] Document ItemAggregator, AggregatedItem, AggregationPresets
- [ ] Explain similarity scoring formula
- [ ] Document all 6 presets with use cases
- [ ] Update architecture diagrams to include aggregation layer
- [ ] Remove references to SessionDeduplicator
- [ ] Cross-reference with tracking system documentation

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
