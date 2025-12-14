***REMOVED*** No User Feedback When Switching Classification Modes

**Labels:** `ux`, `priority:p3`, `area:ui`, `area:settings`
**Type:** UX Issue
**Severity:** Low

***REMOVED******REMOVED*** Problem

Users can toggle between "On-Device" and "Cloud" classification modes, but there's **no visual or haptic feedback** indicating:
- Which mode is currently active
- That the mode switch was successful
- What the difference between modes is

***REMOVED******REMOVED*** Location

**ClassificationModeViewModel.kt** and wherever the toggle is displayed (likely in advanced camera controls)

***REMOVED******REMOVED*** Current Behavior

User taps classification mode toggle:
- Mode changes in ViewModel
- No toast, snackbar, or visual confirmation
- User doesn't know if tap registered
- No explanation of what changed

***REMOVED******REMOVED*** Expected Behavior

***REMOVED******REMOVED******REMOVED*** When User Toggles Mode:

1. **Visual Feedback**:
   - Toggle animates to new position
   - Brief highlight/ripple effect
   - Icon changes (cloud icon vs phone icon)

2. **Confirmation Message** (optional):
   - Toast: "Switched to Cloud classification"
   - Or Snackbar: "Using on-device classification"

3. **Persistent Indicator**:
   - Badge showing current mode
   - Different color for cloud vs on-device
   - Icon in camera controls

***REMOVED******REMOVED******REMOVED*** First Time Toggle (Educational):

Show explanation dialog:

```
┌─────────────────────────────────────┐
│   Classification Mode               │
│                                     │
│  On-Device (Default)                │
│  ✓ Fast                             │
│  ✓ Privacy-friendly                 │
│  ✗ Less accurate                    │
│                                     │
│  Cloud (Requires Internet)          │
│  ✓ More accurate                    │
│  ✗ Slower                           │
│  ✗ Sends images to server           │
│                                     │
│  [Use On-Device]  [Try Cloud]       │
└─────────────────────────────────────┘
```

***REMOVED******REMOVED*** Impact

**Severity**: Low
- Feature works, just poor UX
- Users might not know the feature exists
- No understanding of trade-offs

**Affected Users**: Anyone who finds the advanced controls

***REMOVED******REMOVED*** Acceptance Criteria

- [x] Add visual state indicator to classification mode toggle
- [x] Show confirmation when mode changes
- [x] Add tooltip/help text explaining modes
- [x] Persist mode selection across app restarts (already done via DataStore)
- [ ] Consider showing explanatory dialog on first toggle (deferred)
- [ ] Update CLAUDE.md to document the UX

***REMOVED******REMOVED*** Resolution

- Added mode-specific iconography and brief descriptions to the Processing settings card so users can see whether they are on-device or cloud along with trade-offs.
- Added a toast in `CameraScreen` that confirms the classification mode after the user switches it (initial value is skipped).
- Persistence continues via `ClassificationPreferences` DataStore; no change needed.

***REMOVED******REMOVED*** Verification

- Manual: Open the Camera settings panel → toggle Processing mode; observe icon/color/text change and toast confirmation. Reopen to confirm state persisted.
- Automated: `./gradlew test` (fails in container: Android SDK not installed; unable to fetch via apt due to unsigned/blocked repositories). `./gradlew assembleDebug` (fails: Java 17 toolchain not available and repository download blocked).

***REMOVED******REMOVED*** Suggested Implementation

***REMOVED******REMOVED******REMOVED*** 1. Visual State Indicator

```kotlin
// ClassificationModeToggle.kt (or wherever it's displayed)
Row(
    modifier = Modifier
        .background(
            color = if (currentMode == ClassificationMode.CLOUD)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp)
        )
        .padding(8.dp)
) {
    Icon(
        imageVector = if (currentMode == ClassificationMode.CLOUD)
            Icons.Default.Cloud
        else Icons.Default.Phone,
        contentDescription = null,
        tint = if (currentMode == ClassificationMode.CLOUD)
            MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = currentMode.displayName,
        style = MaterialTheme.typography.labelSmall
    )
}
```

***REMOVED******REMOVED******REMOVED*** 2. Confirmation Toast

```kotlin
// CameraScreen.kt or wherever toggle is
val classificationMode by classificationModeViewModel.classificationMode.collectAsState()

LaunchedEffect(classificationMode) {
    // Show toast on mode change (skip initial value)
    if (classificationMode != initialMode) {
        Toast.makeText(
            context,
            "Using ${classificationMode.displayName} classification",
            Toast.LENGTH_SHORT
        ).show()
    }
}
```

***REMOVED******REMOVED******REMOVED*** 3. Help Dialog (First Time)

```kotlin
var showClassificationExplanation by remember {
    mutableStateOf(preferences.isFirstClassificationToggle())
}

if (showClassificationExplanation) {
    AlertDialog(
        onDismissRequest = { showClassificationExplanation = false },
        title = { Text("Classification Modes") },
        text = {
            Column {
                Text(
                    "Scanium can classify objects in two ways:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                ClassificationModeOption(
                    title = "On-Device",
                    icon = Icons.Default.Phone,
                    pros = listOf("Fast", "Privacy-friendly", "Works offline"),
                    cons = listOf("Less accurate")
                )

                Spacer(modifier = Modifier.height(16.dp))

                ClassificationModeOption(
                    title = "Cloud",
                    icon = Icons.Default.Cloud,
                    pros = listOf("More accurate"),
                    cons = listOf("Slower", "Requires internet", "Sends images to server")
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                showClassificationExplanation = false
                preferences.setFirstClassificationToggleSeen()
            }) {
                Text("Got it")
            }
        }
    )
}
```

***REMOVED******REMOVED*** Related Issues

- Issue ***REMOVED***014 (Classification system placeholders) - explains why modes might not show difference
