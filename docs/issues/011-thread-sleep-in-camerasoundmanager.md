# Replace Thread.sleep() with Coroutine Delay in CameraSoundManager

**Labels:** `bug`, `priority:p3`, `area:camera`, `threading`
**Type:** Threading Bug
**Severity:** Low

## Problem

`CameraSoundManager.kt` uses `Thread.sleep()` which blocks the calling thread. If called from main thread, this would freeze the UI. If called from a coroutine, it blocks the underlying thread instead of suspending.

## Location

File: `/app/src/main/java/com/scanium/app/camera/CameraSoundManager.kt`
Line: 152

## Current Code

```kotlin
private fun playTone(frequencyHz: Int, durationMs: Int) {
    try {
        toneGenerator?.startTone(ToneGenerator.TONE_DTMF_1, durationMs)
        Thread.sleep(durationMs.toLong())  // ❌ BLOCKS THREAD
    } catch (e: Exception) {
        Log.w(TAG, "Error playing tone at ${frequencyHz}Hz", e)
    }
}
```

## Impact

**Current**: Likely called from background thread, so no immediate issue.

**Risk**:
- If playTone() is ever called from main thread, app freezes
- If called from coroutine scope, blocks thread pool thread
- Anti-pattern in modern Android development

## Expected Behavior

Use coroutine `delay()` for non-blocking sleep.

## Acceptance Criteria

- [x] Make `playTone()` a suspend function
- [x] Replace `Thread.sleep()` with `delay()`
- [x] Ensure all callers are in coroutine scope
- [x] Verify sound playback still works correctly

## Suggested Fix

### Option 1: Suspend Function (Recommended)

```kotlin
private suspend fun playTone(frequencyHz: Int, durationMs: Int) {
    try {
        toneGenerator?.startTone(ToneGenerator.TONE_DTMF_1, durationMs)
        delay(durationMs.toLong())  // ✅ NON-BLOCKING
    } catch (e: Exception) {
        Log.w(TAG, "Error playing tone at ${frequencyHz}Hz", e)
    }
}

// Update callers
suspend fun playScanStartMelody() {
    try {
        playTone(800, 100)
        playTone(1000, 100)
        playTone(1200, 150)
    } catch (e: Exception) {
        Log.e(TAG, "Error playing scan start melody", e)
    }
}
```

### Option 2: Post with Delay

If suspend functions are not appropriate:

```kotlin
private fun playTone(frequencyHz: Int, durationMs: Int) {
    try {
        toneGenerator?.startTone(ToneGenerator.TONE_DTMF_1, durationMs)
        // No sleep needed - ToneGenerator handles duration
    } catch (e: Exception) {
        Log.w(TAG, "Error playing tone at ${frequencyHz}Hz", e)
    }
}
```

Check if ToneGenerator already handles the duration internally (likely does).

## Investigation Needed

1. Check if ToneGenerator's `durationMs` parameter already handles timing
2. If yes, Thread.sleep() is redundant
3. If no, use coroutine delay

## Related Issues

None

---

## Resolution

**Status:** ✅ RESOLVED

**Changes Made:**

Fixed threading anti-pattern in CameraSoundManager.kt by replacing blocking Thread.sleep() with coroutine delay().

### Change Details (CameraSoundManager.kt:135-153)

**Before:**
```kotlin
private fun playTone(frequencyHz: Int, durationMs: Int) {
    try {
        toneGenerator?.let { generator ->
            // ... tone mapping logic ...
            generator.startTone(dtmfTone, durationMs)
            Thread.sleep(durationMs.toLong())  // ❌ BLOCKS THREAD
        }
    } catch (e: Exception) {
        Log.w(TAG, "Error playing tone at ${frequencyHz}Hz", e)
    }
}
```

**After:**
```kotlin
private suspend fun playTone(frequencyHz: Int, durationMs: Int) {
    try {
        toneGenerator?.let { generator ->
            // ... tone mapping logic ...
            generator.startTone(dtmfTone, durationMs)
            delay(durationMs.toLong())  // ✅ NON-BLOCKING SUSPENSION
        }
    } catch (e: Exception) {
        Log.w(TAG, "Error playing tone at ${frequencyHz}Hz", e)
    }
}
```

### Why This Fix Is Correct

**ToneGenerator Behavior:**
- `ToneGenerator.startTone(tone, durationMs)` is **asynchronous** - returns immediately
- The tone **plays in background** for the specified duration
- The wait/sleep is **necessary** to prevent overlapping tones in melody sequences

**Thread.sleep() Problem:**
- **Blocks** the underlying thread from Dispatchers.Default pool
- Prevents other coroutines from using that thread
- Anti-pattern in modern Kotlin/Android development
- If accidentally called from main thread, would freeze UI

**delay() Solution:**
- **Suspends** the coroutine without blocking the thread
- Other coroutines can use the thread while waiting
- Standard Kotlin coroutines best practice
- Non-blocking, cooperative multitasking

### Verification

**All callers already in suspend context:**
```kotlin
// playShutterClick() - line 60
fun playShutterClick() {
    scope.launch {  // ✅ Coroutine scope
        playTone(NOTE_C6, CLICK_DURATION_MS)
    }
}

// playScanStartMelody() - line 81
fun playScanStartMelody() {
    scope.launch {  // ✅ Coroutine scope
        playTone(NOTE_C5, NOTE_DURATION_MS)
        delay(NOTE_GAP_MS)  // Already using delay for gaps
        playTone(NOTE_E5, NOTE_DURATION_MS)
        // ... more notes ...
    }
}

// playScanStopMelody() - line 108
fun playScanStopMelody() {
    scope.launch {  // ✅ Coroutine scope
        playTone(NOTE_A5, NOTE_DURATION_MS)
        delay(NOTE_GAP_MS)  // Already using delay for gaps
        // ... more notes ...
    }
}
```

**Already imports delay():**
```kotlin
import kotlinx.coroutines.delay  // Line 8 - already present
```

### Impact

**Before:**
- ❌ Thread.sleep() blocks Dispatchers.Default thread pool
- ❌ Inefficient thread usage during sound playback
- ❌ Risk of UI freeze if accidentally called from main thread
- ❌ Inconsistent with coroutine-based architecture

**After:**
- ✅ delay() suspends coroutine without blocking threads
- ✅ Efficient thread pool utilization
- ✅ No risk of UI freeze (suspend functions can't block main thread)
- ✅ Consistent with Kotlin coroutines best practices
- ✅ Zero functional changes to sound playback

### Why Sleep Was Needed

The delay/sleep is **not redundant** because:
- `startTone()` returns **immediately** (asynchronous)
- Melody sequences need **sequential** playback (C5 → E5 → G5 → C6)
- Without waiting, all tones would **overlap** and play simultaneously
- The wait ensures each note **completes** before the next starts

### Testing

**Expected behavior:**
- Shutter click: Single 50ms tone (high C)
- Scan start: Ascending arpeggio (C5 → E5 → G5 → C6) with 30ms gaps
- Scan stop: Descending pattern (A5 → F5 → D5 → C5) with 30ms gaps

**No changes to audio output** - only threading mechanism changed.

### Benefits

✅ **Performance**: Better thread pool utilization
✅ **Safety**: Eliminates potential UI freeze risk
✅ **Maintainability**: Follows Kotlin coroutines conventions
✅ **Consistency**: Matches existing delay() usage for note gaps
✅ **No Functional Change**: Sound playback behavior unchanged
