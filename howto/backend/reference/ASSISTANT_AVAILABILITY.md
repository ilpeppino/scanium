***REMOVED*** Assistant Availability State Model

***REMOVED******REMOVED*** Overview

This document describes the explicit availability state model for the AI Assistant feature, ensuring
the UI never feels broken or confusing when the backend is unavailable.

***REMOVED******REMOVED*** Problem Statement

Previously, when the assistant backend was unavailable:

- The input field remained enabled and focusable
- Users could type messages that could never be sent
- No clear indication of what actions were possible
- Silent failures led to confusion

***REMOVED******REMOVED*** Solution: First-Class Availability State

***REMOVED******REMOVED******REMOVED*** AssistantAvailability Sealed Class

```kotlin
sealed class AssistantAvailability {
    data object Available : AssistantAvailability()
    data object Checking : AssistantAvailability()
    data class Unavailable(
        val reason: UnavailableReason,
        val canRetry: Boolean,
        val retryAfterSeconds: Int? = null,
    ) : AssistantAvailability()
}
```

***REMOVED******REMOVED******REMOVED*** UnavailableReason Enum

| Reason             | Description                 | Can Retry | User Action         |
|--------------------|-----------------------------|-----------|---------------------|
| `OFFLINE`          | Device has no network       | Yes       | Connect to internet |
| `BACKEND_ERROR`    | Server error (5xx, timeout) | Yes       | Retry or use local  |
| `RATE_LIMITED`     | Too many requests           | Yes       | Wait and retry      |
| `UNAUTHORIZED`     | Auth/subscription issue     | No        | Check account       |
| `NOT_CONFIGURED`   | Backend not set up          | No        | Contact support     |
| `VALIDATION_ERROR` | Bad request                 | No        | Rephrase question   |
| `LOADING`          | Request in progress         | No        | Wait                |

***REMOVED******REMOVED*** UI Behavior

***REMOVED******REMOVED******REMOVED*** When Available

- Text input enabled with normal placeholder
- Send button enabled when text present
- Smart suggestion chips clickable
- Full assistant functionality

***REMOVED******REMOVED******REMOVED*** When Unavailable

- Text input **disabled** with status-specific placeholder
- Send button disabled
- Smart suggestion chips disabled (grayed out)
- Unavailable banner shown with:
    - Clear title explaining the issue
    - Detail text with recovery hint
    - Retry button (if retryable)
    - "Use local" / "OK" dismiss button
- Local suggestions still visible for reference

***REMOVED******REMOVED******REMOVED*** Placeholder Text by Reason

| Reason             | Placeholder                                 |
|--------------------|---------------------------------------------|
| `OFFLINE`          | "You're offline. Connect to use assistant." |
| `RATE_LIMITED`     | "Rate limited. Please wait."                |
| `UNAUTHORIZED`     | "Authorization required."                   |
| `NOT_CONFIGURED`   | "Assistant not configured."                 |
| `BACKEND_ERROR`    | "Assistant temporarily unavailable."        |
| `VALIDATION_ERROR` | "Service error. Try again."                 |
| `LOADING`          | "Processing..."                             |

***REMOVED******REMOVED*** State Transitions

***REMOVED******REMOVED******REMOVED*** Compute Availability

```kotlin
fun computeAvailability(
    isOnline: Boolean,
    isLoading: Boolean,
    failure: AssistantBackendFailure?,
): AssistantAvailability
```

Evaluation order:

1. If `isLoading` -> `Unavailable(LOADING)`
2. If `!isOnline` -> `Unavailable(OFFLINE)`
3. If `failure != null` -> Map failure type to reason
4. Otherwise -> `Available`

***REMOVED******REMOVED******REMOVED*** Trigger Points

| Event               | Action                                   |
|---------------------|------------------------------------------|
| Screen resume       | `viewModel.refreshAvailability()`        |
| Connectivity change | Auto-update via `observeConnectivity()`  |
| Request starts      | Set `Unavailable(LOADING)`               |
| Request succeeds    | Set `Available`                          |
| Request fails       | Set `Unavailable(reason)`                |
| Retry button tap    | Clear failure, retry if has last message |
| Dismiss button tap  | Clear failure state                      |

***REMOVED******REMOVED******REMOVED*** Network Reconnection

When device comes back online, if the last failure was network-related:

- `NETWORK_TIMEOUT` or `NETWORK_UNREACHABLE` failures are auto-cleared
- Availability is recalculated to `Available`
- User can immediately retry

***REMOVED******REMOVED*** Local-Only Fallback

When assistant is unavailable:

1. Local suggestions remain visible in the chat
2. Banner messaging emphasizes "local suggestions available"
3. Previous local assistant responses can still be viewed
4. Items and metadata are still accessible

***REMOVED******REMOVED*** Developer Diagnostics

***REMOVED******REMOVED******REMOVED*** Logging (Tag: AssistantViewModel)

```
I/AssistantViewModel: Assistant availability changed: Unavailable(OFFLINE, canRetry=true)
I/AssistantViewModel: Assistant availability restored: Available (success)
I/AssistantViewModel: Assistant fallback mode=LIMITED availability=Unavailable(BACKEND_ERROR) [provider_unavailable/temporary/retryable]
```

***REMOVED******REMOVED******REMOVED*** Developer Options

The existing Assistant Diagnostics section in Developer Options shows:

- Backend reachability status
- Prerequisites state
- Network connectivity
- Last checked timestamp

***REMOVED******REMOVED*** Invariants

1. **Input follows availability**: Text input is ONLY enabled when `availability.canSendMessages`
2. **No silent failures**: User always knows what happened and what to do
3. **Graceful degradation**: Local features work when online features don't
4. **Clean recovery**: Coming back online auto-clears transient failures

***REMOVED******REMOVED*** Testing Scenarios

| Scenario                       | Expected Behavior                     |
|--------------------------------|---------------------------------------|
| Assistant available            | Normal chat works                     |
| Assistant unavailable at entry | Input disabled, local actions visible |
| Type while unavailable         | Impossible (input disabled)           |
| Retry succeeds                 | Input enabled, chat resumes           |
| Retry fails                    | Stay in local-only mode               |
| Background/foreground app      | State remains correct                 |
| Rotate device                  | No broken layout                      |
| Network drop mid-session       | Graceful fallback with banner         |

***REMOVED******REMOVED*** Files Changed

- `AssistantViewModel.kt`: Added `AssistantAvailability`, `UnavailableReason`,
  `computeAvailability()`, `refreshAvailability()`, `clearFailureState()`
- `AssistantScreen.kt`: Added `AssistantUnavailableBanner`, disabled input when unavailable,
  lifecycle handling
- `AssistantUiState`: Added `availability`, `isInputEnabled`, `inputPlaceholder`
