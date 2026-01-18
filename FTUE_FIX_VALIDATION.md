***REMOVED*** FTUE Fix - Validation Plan

***REMOVED******REMOVED*** Summary
Fixed critical initialization bugs preventing FTUE from ever showing on any screen.

***REMOVED******REMOVED*** Root Causes Fixed

***REMOVED******REMOVED******REMOVED*** Bug ***REMOVED***1 (Primary): Initial value mismatch blocks FTUE
- **Files**: CameraScreen.kt, ItemsListScreen.kt, EditItemScreenV3.kt, SettingsGeneralScreen.kt
- **Issue**: All screens used `collectAsState(initial = true)` for completion flags
- **Impact**: LaunchedEffect condition `if (!ftueCompleted)` evaluated to `!true = false`, never triggering
- **Fix**: Changed all to `initial = false` to match DataStore defaults

***REMOVED******REMOVED******REMOVED*** Bug ***REMOVED***2 (Secondary): Replay guide incomplete
- **File**: SettingsViewModel.kt
- **Issue**: resetFtueTour() called `reset()` instead of `resetAll()`
- **Impact**: Only reset old tour flag, not screen-specific FTUE flags
- **Fix**: Changed to call `resetAll()` to reset all FTUE state

***REMOVED******REMOVED*** Changes Made

***REMOVED******REMOVED******REMOVED*** Files Modified
1. `androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt`
   - Line 237: `initial = true` → `initial = false`
   - Lines 444-452: Added dev-only FTUE debug logging

2. `androidApp/src/main/java/com/scanium/app/items/ItemsListScreen.kt`
   - Line 106: `initial = true` → `initial = false`
   - Lines 290-295: Added dev-only FTUE debug logging

3. `androidApp/src/main/java/com/scanium/app/items/edit/EditItemScreenV3.kt`
   - Line 105: `initial = true` → `initial = false`
   - Lines 140-145: Added dev-only FTUE debug logging

4. `androidApp/src/main/java/com/scanium/app/ui/settings/SettingsGeneralScreen.kt`
   - Line 84: `initial = true` → `initial = false`
   - Lines 92-97: Added dev-only FTUE debug logging

5. `androidApp/src/main/java/com/scanium/app/ui/settings/SettingsViewModel.kt`
   - Line 354: `reset()` → `resetAll()`
   - Added dev-only debug log for replay action

***REMOVED******REMOVED*** Validation Steps (Manual Testing)

***REMOVED******REMOVED******REMOVED*** Prerequisites
- Dev build flavor (for debug logs)
- Fresh install OR reset FTUE via "Replay first-time guide"
- adb logcat filtered to tag "FTUE"

***REMOVED******REMOVED******REMOVED*** Test 1: Camera FTUE (First Run)
1. Fresh install or clear app data
2. Launch app → grant camera permission
3. **Expected**: After 1 second delay, see ROI pulse overlay
4. **Logs**: Should see:
   ```
   FTUE: Camera: hasCameraPermission=true, cameraFtueCompleted=false
   FTUE: Camera: Initializing FTUE (first time)
   ```
5. Wait for first detection
6. **Expected**: BBox hint appears around first detected object
7. Capture item
8. **Expected**: Shutter pulse hint appears

***REMOVED******REMOVED******REMOVED*** Test 2: Items List FTUE
1. With items from Test 1, navigate to items list
2. **Expected**: Tap edit hint appears on first item
3. **Logs**: Should see:
   ```
   FTUE: ItemsList: items.size=1, listFtueCompleted=false
   FTUE: ItemsList: Initializing FTUE (first time)
   ```
4. Follow hints through swipe, long-press, and share hints

***REMOVED******REMOVED******REMOVED*** Test 3: Edit Item FTUE
1. Tap item to open edit screen
2. **Expected**: Details field hint appears
3. **Logs**: Should see:
   ```
   FTUE: EditItem: editFtueCompleted=false
   FTUE: EditItem: Initializing FTUE (first time)
   ```
4. Edit field → see condition/price hint

***REMOVED******REMOVED******REMOVED*** Test 4: Settings FTUE
1. Navigate to Settings → General
2. **Expected**: Language hint appears
3. **Logs**: Should see:
   ```
   FTUE: Settings: settingsFtueCompleted=false
   FTUE: Settings: Initializing FTUE (first time)
   ```
4. Change language → see replay guide hint

***REMOVED******REMOVED******REMOVED*** Test 5: Replay FTUE
1. In Settings → General, tap "Replay first-time guide"
2. **Logs**: Should see:
   ```
   FTUE: Replay: Resetting all FTUE flags via resetAll()
   ```
3. Return to camera screen
4. **Expected**: All FTUE sequences replay as if fresh install

***REMOVED******REMOVED*** Verification Without Device

***REMOVED******REMOVED******REMOVED*** Code Review Checklist
- [x] All `cameraFtueCompletedFlow` uses `initial = false`
- [x] All `listFtueCompletedFlow` uses `initial = false`
- [x] All `editFtueCompletedFlow` uses `initial = false`
- [x] All `settingsFtueCompletedFlow` uses `initial = false`
- [x] resetFtueTour() calls `resetAll()` not `reset()`
- [x] Debug logs only in dev flavor (BuildConfig.FLAVOR == "dev")
- [x] No changes to camera/scanning logic
- [x] No changes to overlay rendering logic

***REMOVED******REMOVED******REMOVED*** Log Commands
```bash
***REMOVED*** Monitor FTUE logs in real-time
adb logcat -s FTUE:D

***REMOVED*** Clear logs and start fresh
adb logcat -c && adb logcat -s FTUE:D

***REMOVED*** Full debug log with timestamps
adb logcat -v time -s FTUE:D
```

***REMOVED******REMOVED*** Expected Outcomes

***REMOVED******REMOVED******REMOVED*** Before Fix
- ✗ No FTUE ever shows (flags always true at initialization)
- ✗ Replay button doesn't reset screen-specific FTUE
- ✗ No debug visibility into FTUE state

***REMOVED******REMOVED******REMOVED*** After Fix
- ✓ FTUE initializes on first run (flags start as false)
- ✓ Replay button resets all FTUE flags correctly
- ✓ Dev logs show FTUE state transitions
- ✓ All 4 FTUE sequences work as designed

***REMOVED******REMOVED*** Rollback Plan
If issues occur, revert commits:
```bash
git diff HEAD -- androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt \
                 androidApp/src/main/java/com/scanium/app/items/ItemsListScreen.kt \
                 androidApp/src/main/java/com/scanium/app/items/edit/EditItemScreenV3.kt \
                 androidApp/src/main/java/com/scanium/app/ui/settings/SettingsGeneralScreen.kt \
                 androidApp/src/main/java/com/scanium/app/ui/settings/SettingsViewModel.kt
```

***REMOVED******REMOVED*** Notes
- Build issues exist on main branch (Italian strings, launcher icons) - unrelated to FTUE fix
- Changes are minimal and surgical - only fix verified bugs
- No behavioral changes to camera, detection, or scanning logic
- Debug logs only active in dev builds
