***REMOVED*** Missing Accessibility Features (TalkBack, Content Descriptions)

**Labels:** `accessibility`, `priority:p2`, `area:ui`, `a11y`
**Type:** Accessibility Gap
**Severity:** Medium

***REMOVED******REMOVED*** Problem

The app is missing critical accessibility features for users with visual impairments. Compose components need content descriptions for TalkBack screen reader support.

***REMOVED******REMOVED*** Missing Features

***REMOVED******REMOVED******REMOVED*** 1. Content Descriptions for Icon Buttons

**CameraScreen.kt** - Multiple icon buttons without semantic labels:

```kotlin
// Line ~200-210: Scan mode switcher
IconButton(onClick = { /* ... */ }) {
    Icon(imageVector = /* mode icon */, contentDescription = null)  // ❌
}

// Items button (top-right)
IconButton(onClick = onNavigateToItems) {
    Icon(imageVector = Icons.Default.List, contentDescription = null)  // ❌
}
```

**ItemsListScreen.kt** - Item actions:

```kotlin
IconButton(onClick = { /* remove */ }) {
    Icon(imageVector = Icons.Default.Delete, contentDescription = null)  // ❌
}
```

***REMOVED******REMOVED******REMOVED*** 2. Missing Semantic Properties

**ShutterButton.kt** - No role/label:

```kotlin
Box(
    modifier = Modifier
        .size(buttonSize)
        .clickable { onCapture() }  // ❌ No semantics
)
```

Should use:

```kotlin
Box(
    modifier = Modifier
        .size(buttonSize)
        .clickable { onCapture() }
        .semantics {
            role = Role.Button
            contentDescription = "Capture photo. Long press to start scanning"
        }
)
```

***REMOVED******REMOVED******REMOVED*** 3. Detection Overlay Not Announced

**DetectionOverlay.kt** - Bounding boxes drawn but not announced to TalkBack:

No way for visually impaired users to know when objects are detected.

***REMOVED******REMOVED******REMOVED*** 4. Dynamic Content Not Announced

**CameraScreen.kt** - Items count changes but no announcement:

```kotlin
Text("Items (${itemsCount.size})")  // Changes silently
```

Should trigger announcement when count updates.

***REMOVED******REMOVED******REMOVED*** 5. Scan State Not Announced

**CameraState changes** (IDLE → SCANNING → IDLE) - No audio/haptic feedback for screen reader users.

***REMOVED******REMOVED*** Impact

**Severity**: Medium - App is unusable for blind/low-vision users

**Affected Users**:
- ~15% of users have some visual impairment (WHO stats)
- TalkBack users can't navigate the app
- Violates accessibility guidelines (WCAG 2.1)

**Legal Risk**: Some jurisdictions require accessibility compliance

***REMOVED******REMOVED*** Expected Behavior

***REMOVED******REMOVED******REMOVED*** TalkBack Navigation Should Work:

1. User lands on camera screen
2. TalkBack announces: "Camera preview. Tap to capture photo. Long press to start scanning."
3. User swipes to shutter button
4. TalkBack announces: "Capture button. Double tap to take photo. Double tap and hold to scan continuously."
5. User takes photo
6. TalkBack announces: "1 item detected. Scanned fashion item with medium confidence."
7. User swipes to items button
8. TalkBack announces: "Items list. 5 items. Double tap to open."

***REMOVED******REMOVED*** Acceptance Criteria

- [ ] All IconButtons have contentDescription
- [ ] ShutterButton has semantic role and description
- [ ] Scan state changes announced via LiveRegion
- [ ] Item count changes announced
- [ ] Detection events announced (optional: "X objects detected")
- [ ] All interactive elements reachable via TalkBack
- [ ] Minimum touch target size 48dp (Material Design guideline)
- [ ] Test with TalkBack enabled on physical device

***REMOVED******REMOVED*** Suggested Fixes

***REMOVED******REMOVED******REMOVED*** 1. Icon Button Content Descriptions

```kotlin
// CameraScreen.kt
IconButton(
    onClick = onNavigateToItems,
    modifier = Modifier.semantics {
        contentDescription = "View scanned items. ${itemsCount.size} items"
    }
) {
    Icon(
        imageVector = Icons.Default.List,
        contentDescription = null  // Null because parent has description
    )
}
```

***REMOVED******REMOVED******REMOVED*** 2. Scan Mode Switcher

```kotlin
IconButton(
    onClick = { currentScanMode = mode },
    modifier = Modifier.semantics {
        contentDescription = "${mode.displayName} scan mode" +
            if (currentScanMode == mode) ". Selected" else ""
        role = Role.RadioButton
        selected = currentScanMode == mode
    }
) {
    Icon(imageVector = mode.icon, contentDescription = null)
}
```

***REMOVED******REMOVED******REMOVED*** 3. Shutter Button Semantics

```kotlin
Box(
    modifier = Modifier
        .size(72.dp)
        .semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = when (cameraState) {
                CameraState.IDLE -> "Capture. Tap to take photo. Long press to scan continuously."
                CameraState.SCANNING -> "Scanning. Release to stop."
                CameraState.PROCESSING -> "Processing..."
            }
        }
        .clickable { onCapture() }
        .pointerInput(Unit) { /* gestures */ }
) {
    // ... existing button UI
}
```

***REMOVED******REMOVED******REMOVED*** 4. Announce Detection Events

```kotlin
// CameraScreen.kt
LaunchedEffect(itemsCount.size) {
    if (itemsCount.isNotEmpty()) {
        // Trigger accessibility announcement
        // Note: Compose doesn't have direct announcement API yet
        // Workaround: Use AndroidView with AccessibilityManager
    }
}
```

Better approach with custom modifier:

```kotlin
fun Modifier.announce(message: String): Modifier = this.then(
    Modifier.semantics {
        liveRegion = LiveRegionMode.Polite
        contentDescription = message
    }
)

// Usage
Text(
    text = "Items (${itemsCount.size})",
    modifier = Modifier.announce("${itemsCount.size} items scanned")
)
```

***REMOVED******REMOVED******REMOVED*** 5. Minimum Touch Targets

Ensure all interactive elements are at least 48dp:

```kotlin
IconButton(
    onClick = { /* ... */ },
    modifier = Modifier
        .size(48.dp)  // ✅ Minimum touch target
        .semantics { contentDescription = "..." }
) {
    Icon(...)
}
```

***REMOVED******REMOVED*** Testing Checklist

Manual accessibility testing:

- [ ] Enable TalkBack (Settings → Accessibility → TalkBack)
- [ ] Navigate camera screen with swipe gestures
- [ ] Verify all buttons have clear labels
- [ ] Verify scan state changes are announced
- [ ] Verify item count changes are announced
- [ ] Take photo with TalkBack gestures (double tap)
- [ ] Navigate to items list
- [ ] Remove item with TalkBack gestures
- [ ] Verify all touch targets are large enough (no "too small" warnings)

***REMOVED******REMOVED*** Automated Testing

Add Compose accessibility tests:

```kotlin
@Test
fun shutterButtonHasContentDescription() {
    composeTestRule.setContent {
        ShutterButton(onCapture = {}, cameraState = CameraState.IDLE)
    }

    composeTestRule
        .onNode(hasClickAction())
        .assertHasClickAction()
        .assertIsDisplayed()
        .assertContentDescriptionContains("Capture")
}
```

***REMOVED******REMOVED*** Related Issues

None
