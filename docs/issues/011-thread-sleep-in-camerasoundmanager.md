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

- [ ] Make `playTone()` a suspend function
- [ ] Replace `Thread.sleep()` with `delay()`
- [ ] Ensure all callers are in coroutine scope
- [ ] Verify sound playback still works correctly

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
