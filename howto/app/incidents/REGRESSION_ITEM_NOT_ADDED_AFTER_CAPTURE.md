# Regression Checklist: Item Not Added After Capture

**Incident**: Item not persisted after capture while hypothesis/correction UI shown.

## <5 Minute Device Checklist

1. **Capture → item appears immediately**
   - Open camera, capture a single item.
   - Expected: Item appears in list within ~1s with a thumbnail and timestamp.

2. **Classification updates later (non-blocking)**
   - Wait for cloud classification to complete.
   - Expected: Item updates category/label if user confirms; item remains saved regardless of classification timing.

3. **Empty/unknown classification**
   - Capture an ambiguous item (or disable backend).
   - Expected: Item still saved with status **Needs review**.
   - Expected: No blocking modal is shown automatically.

4. **Correction dialog is optional**
   - From hypothesis sheet, tap **None of these**.
   - Tap **Cancel** in the correction dialog.
   - Expected: Item remains saved and visible in list.

5. **No silent override after confirmation**
   - Confirm a hypothesis.
   - Wait 10–15s for late enrichment results.
   - Expected: Confirmed category/label does not change without user action.

## Expected Screenshots

1. **Items list with newly captured item**
   - Shows thumbnail, timestamp, and provisional label.

2. **Needs review badge**
   - Visible on items where classification returned empty/unknown.

3. **Hypothesis sheet with “None of these”**
   - Confirms correction dialog only appears after explicit tap.

4. **Post-confirmation item remains unchanged**
   - Category/label matches confirmed choice after waiting for enrichment.
