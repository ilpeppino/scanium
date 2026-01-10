# Assistant Progress UI States

This document describes the progress UI states shown during the assistant request lifecycle.

## Overview

The assistant request lifecycle now provides detailed progress feedback to users through a state machine. This replaces the generic spinner with context-aware progress labels that help users understand what's happening.

## State Machine

```
IDLE ─┬─> SENDING ─> THINKING ─┬─> DRAFTING ─> FINALIZING ─> DONE
      │                        │
      │                        └─> EXTRACTING_VISION ─> DRAFTING ─> ...
      │
      └─> ERROR_TEMPORARY / ERROR_AUTH / ERROR_VALIDATION
```

## Progress States

| State | Display Label | When Shown | Duration |
|-------|--------------|------------|----------|
| `Idle` | *(empty)* | No request in progress | - |
| `Sending` | "Sending..." | Request is being prepared and sent | ~100-500ms |
| `Thinking` | "Thinking..." | Request sent, waiting for backend | ~500ms-2s |
| `ExtractingVision` | "Analyzing images..." / "Analyzing N images..." | Backend processing images | ~1-3s per image |
| `Drafting` | "Drafting answer..." | Backend generating response | ~2-5s |
| `Finalizing` | "Finalizing..." | Post-processing (mapping suggestedDraftUpdates) | ~100-500ms |
| `Done` | *(empty)* | Request completed successfully | - |
| `ErrorTemporary` | "Temporarily unavailable" | Timeout, 5xx errors | Retryable |
| `ErrorAuth` | "Authentication required" | 401 errors | Not retryable |
| `ErrorValidation` | "Invalid request" | 400 errors | Not retryable |

## UI Behavior

### Progress Indicator
- Single-line progress label with small spinner
- Thin linear progress bar below
- Uses `Crossfade` animation (200ms) for smooth transitions
- Fixed height to prevent layout jumps

### Last Successful Response
- The last successful assistant response is preserved during new requests
- Never replaced with "temporarily unavailable" unless the *current* request fails
- Stored in `AssistantUiState.lastSuccessfulEntry`

### Error Handling
- `ErrorTemporary`: Shows retry banner with "Retry" button
- `ErrorAuth`: Shows error banner (no retry)
- `ErrorValidation`: Shows error banner (no retry)

## Timing Telemetry

Each request tracks timing for performance monitoring:

```kotlin
AssistantRequestTiming(
    correlationId: String,      // Request correlation ID
    sendingStartedAt: Long?,    // When request started sending
    thinkingStartedAt: Long?,   // When backend started processing
    extractingVisionStartedAt: Long?,  // When image analysis started (if applicable)
    draftingStartedAt: Long?,   // When response generation started
    finalizingStartedAt: Long?, // When post-processing started
    completedAt: Long?,         // When request completed
    hasImages: Boolean          // Whether images were attached
)
```

Logged via `ScaniumLog.i(TAG, "Assist timing: ${timing.toLogString()}")` on completion.

Example log output:
```
Assist timing: correlationId=assist-abc123 sending=150ms thinking=800ms vision=1200ms drafting=2500ms total=4650ms
```

## State Transitions

### Success Flow (No Images)
```
Idle -> Sending -> Thinking -> Drafting -> [Finalizing] -> Done
```

### Success Flow (With Images)
```
Idle -> Sending -> Thinking -> ExtractingVision -> Drafting -> [Finalizing] -> Done
```

### Error Flow
```
Idle -> Sending -> Thinking -> ErrorTemporary (on timeout/5xx)
                            -> ErrorAuth (on 401)
                            -> ErrorValidation (on 400)
```

### Retry Flow
```
ErrorTemporary -> Idle -> Sending -> ...
```

## Implementation Details

### Files Changed
- `AssistantViewModel.kt`: Added `AssistantRequestProgress` sealed class, `AssistantRequestTiming` data class
- `AssistantScreen.kt`: Added `ProgressIndicatorSection`, `ProgressStageRow`, `ProgressErrorBanner` composables

### Backwards Compatibility
- Legacy `LoadingStage` enum is deprecated but preserved
- `AssistantUiState.loadingStage` still updated for backwards compatibility
- `AssistantRequestProgress.toLegacyStage()` provides mapping

## Usage Example

```kotlin
// In ViewModel
_uiState.update { state ->
    state.copy(
        progress = AssistantRequestProgress.Thinking(
            startedAt = System.currentTimeMillis(),
            correlationId = correlationId,
        ),
    )
}

// In UI
ProgressIndicatorSection(
    progress = state.progress,
    onRetry = { viewModel.retryLastMessage() },
)
```
