***REMOVED*** Manual Golden Instrumented Test Runbook

***REMOVED******REMOVED*** Purpose & Usage
This runbook describes the canonical “golden” manual regression that every physical-device build must pass before release. Each test case is structured so it can later be mirrored 1:1 in automated instrumentation (Compose UI + Espresso). Mark each checkbox when complete and capture evidence (screenshots/logs) as needed.

***REMOVED******REMOVED*** How to Use
1. Install the signed APK on a clean device (Pixel 7+ on Android 13 recommended).
2. Unless stated, begin each test from the `Routes.CAMERA` start destination.
3. Collect device logs with `adb logcat -s ScaniumApplication CameraScreen AssistantVoice DiagnosticsRepo` while executing.
4. Update `Failure Notes / Diagnostics` with any deviations.

---

***REMOVED******REMOVED*** Smoke Suite (critical acceptance)
***REMOVED******REMOVED******REMOVED*** SCN-SMOKE-001 – Clean Install & Launch
- [ ] Status
- Test ID: SCN-SMOKE-001
- Title: Clean install boots to camera with permission prompt
- Area: Permissions
- Preconditions: Device reset to clear Scanium data; Wi‑Fi enabled; backend reachable.
- Test Data / Setup: APK sideloaded via `adb install`.
- Steps:
  1. Uninstall any previous build.
  2. Install current APK and launch Scanium.
  3. When prompted for camera permission, tap “Allow”.
  4. Observe initial FTUE overlay banner.
- Expected Results:
  1. Splash transitions directly to CameraScreen with live preview placeholder.
  2. Android camera permission dialog appears exactly once.
  3. Granting permission binds CameraX preview; viewfinder displays real camera feed.
  4. No crashes, and system nav remains responsive.
- Failure Notes / Diagnostics: If preview fails, capture `adb logcat` + Developer → System Health backend status. Confirm `Settings > Apps > Scanium > Permissions`.
- Automation Hints: Use `Routes.CAMERA` start; wait for `testTag("cameraPreview")`; instrumentation tag `@smoke`.

***REMOVED******REMOVED******REMOVED*** SCN-SMOKE-002 – FTUE Spotlight Path
- [ ] Status
- Test ID: SCN-SMOKE-002
- Title: First-time tour highlights shutter and nav buttons
- Area: FTUE
- Preconditions: Fresh install; tour not forced or marked complete.
- Test Data / Setup: Device brightness medium; record screen for overlay alignment.
- Steps:
  1. From CameraScreen, observe the tour overlay.
  2. Tap “Next” (or CTA) to move through each spotlight (shutter, items button, settings).
  3. Dismiss tour at the final step.
- Expected Results:
  1. Overlay cutout aligns precisely over highlighted control with translucent dim beyond.
  2. Target text references the correct control each step.
  3. After dismissal, overlay is removed and interactions resume.
- Failure Notes / Diagnostics: Verify `FtueRepository` flags via Developer → Force First-Time Tour; capture screenshots if misaligned.
- Automation Hints: Advocate `testTag("tour-overlay-step-{index}")`; use Compose semantics to assert bounds.

***REMOVED******REMOVED******REMOVED*** SCN-SMOKE-003 – Camera Preview & Capture
- [ ] Status
- Test ID: SCN-SMOKE-003
- Title: Capture single item and see overlay animation
- Area: Camera
- Preconditions: Camera permission granted; stable lighting.
- Test Data / Setup: Place a book or box on desk.
- Steps:
  1. Aim at the object and tap the shutter once.
  2. Observe detection overlay and toast/snackbar.
  3. Tap the Items icon (list) to navigate to items list.
- Expected Results:
  1. Preview remains active without dimming.
  2. Single tap triggers capture animation and overlay rectangle appears.
  3. Items list shows the captured item at top with thumbnail.
- Failure Notes / Diagnostics: Use Developer → System Health to confirm `Camera` permission; review `CameraScreen` logs.
- Automation Hints: Tag shutter as `testTag("shutterButton")`; overlay via `testTag("detection-overlay")`; `@smoke`.

***REMOVED******REMOVED******REMOVED*** SCN-SMOKE-004 – Item Selection Toggle
- [ ] Status
- Test ID: SCN-SMOKE-004
- Title: Select/unselect item from list
- Area: Items
- Preconditions: At least one item captured.
- Test Data / Setup: N/A.
- Steps:
  1. From ItemsListScreen, tap an item card to select.
  2. Verify selection indicator (check or badge).
  3. Tap again to deselect.
- Expected Results:
  1. Selection state toggles deterministically.
  2. Floating action button updates count (if applicable).
  3. No duplicate entries created.
- Failure Notes / Diagnostics: Check `ItemsViewModel` selection logs; inspect DB via `adb shell run-as com.scanium.app cat databases/scanned_items.db`.
- Automation Hints: Add `testTag("item-card-{id}")`; check semantics `selected = true`.

***REMOVED******REMOVED******REMOVED*** SCN-SMOKE-005 – Swipe Delete & Undo
- [ ] Status
- Test ID: SCN-SMOKE-005
- Title: Swipe right deletes item with undo
- Area: Items
- Preconditions: Two or more items exist.
- Test Data / Setup: None.
- Steps:
  1. Swipe an item card to the right until delete action completes.
  2. Tap “Undo” on snackbar.
- Expected Results:
  1. Item temporarily disappears and snackbar shows “Item deleted”.
  2. Undo restores item in previous order.
  3. Database count unchanged after undo.
- Failure Notes / Diagnostics: Check `ItemsViewModel` `itemDeletedEvents`; confirm `Undo` uses the same ID.
- Automation Hints: Tag swipe row `testTag("item-swipe-row-{id}")`; snackbar `testTag("undo-snackbar")`.

***REMOVED******REMOVED******REMOVED*** SCN-SMOKE-006 – Assistant Entry via Items FAB
- [ ] Status
- Test ID: SCN-SMOKE-006
- Title: Open assistant for selected items
- Area: Assistant
- Preconditions: At least one item selected; assistant allowed in Settings.
- Test Data / Setup: Enable `Allow Assistant` toggle if needed.
- Steps:
  1. Select an item.
  2. Tap assistant FAB (AI icon).
  3. Confirm navigation to AssistantScreen showing context chips.
- Expected Results:
  1. Navigation occurs without crash.
  2. Context chip text matches item title/category.
  3. Chat list initially empty aside from system hints.
- Failure Notes / Diagnostics: Check `Routes.ASSISTANT` arguments; look at `logcat` for `AssistantViewModel`.
- Automation Hints: FAB `testTag("fab-assistant")`; context chips `testTag("assistant-context-chip-{itemId}")`.

***REMOVED******REMOVED******REMOVED*** SCN-SMOKE-007 – Assistant Text Chat Basics
- [ ] Status
- Test ID: SCN-SMOKE-007
- Title: Send text question and receive reply
- Area: Assistant
- Preconditions: Backend reachable; assistant allowed; network online.
- Test Data / Setup: Use question “What should I write in the description?”.
- Steps:
  1. Type the question and press Send or IME “Send”.
  2. Observe typing indicator and final assistant reply.
- Expected Results:
  1. Input field expands to show full text without covering previous messages.
  2. List auto-scrolls to show pending and final message.
  3. Reply arrives with suggested actions if provided.
- Failure Notes / Diagnostics: If backend fails, capture `AssistantViewModel` loading stage; check Developer → System Health -> Backend status.
- Automation Hints: Input `testTag("assistant-input")`, Send icon `testTag("assistant-send")`, chat list `testTag("assistant-chat-list")`.

***REMOVED******REMOVED******REMOVED*** SCN-SMOKE-008 – Voice Input & TTS Happy Path
- [ ] Status
- Test ID: SCN-SMOKE-008
- Title: Use mic button to dictate and auto-play TTS
- Area: Assistant
- Preconditions: Device supports `SpeechRecognizer` and TTS; Settings toggles enabled for voice input + speak replies; RECORD_AUDIO permission not yet granted.
- Test Data / Setup: Enable toggles in Settings → Voice Mode.
- Steps:
  1. In AssistantScreen, tap mic button; grant microphone permission when prompted.
  2. Speak “What color is it?” and stop speaking.
  3. Observe transcript inserted; manually send.
  4. Wait for assistant reply and ensure speech playback occurs with stop chip.
- Expected Results:
  1. Permission prompt only appears once.
  2. Listening indicator (card) shows partial transcript.
  3. Transcript populates input box; reply triggers “Speaking...” chip and audio.
  4. Stop button halts playback immediately.
- Failure Notes / Diagnostics: Developer → System Health should mark Microphone permission granted; `AssistantVoice` logcat shows state transitions.
- Automation Hints: Tag mic icon `testTag("voice-button")`, speaking chip `testTag("tts-speaking-chip")`; annotate instrumentation `@smoke @voice`.

***REMOVED******REMOVED******REMOVED*** SCN-SMOKE-009 – Selling Flow Launch
- [ ] Status
- Test ID: SCN-SMOKE-009
- Title: Start Sell on eBay for selected items
- Area: Items
- Preconditions: Item selected; ListingDraftRepository accessible.
- Test Data / Setup: Use previously captured item.
- Steps:
  1. Select one item and tap overflow / Sell action.
  2. Choose “Sell on eBay”.
  3. Verify listing composer appears with prefilled fields.
- Expected Results:
  1. Navigation uses `Routes.SELL_ON_EBAY`.
  2. Title/description fields show assistant-derived text or placeholders.
  3. Back navigation returns to items list without duplicates.
- Failure Notes / Diagnostics: If crash occurs, inspect `ListingDraftRepository` logs; confirm `MockEbayApi` config in logcat.
- Automation Hints: Button `testTag("action-sell")`, composer root `testTag("sell-dialog")`.

***REMOVED******REMOVED******REMOVED*** SCN-SMOKE-010 – Theme Toggle
- [ ] Status
- Test ID: SCN-SMOKE-010
- Title: Switching theme updates UI immediately
- Area: Settings
- Preconditions: Access to SettingsScreen.
- Test Data / Setup: None.
- Steps:
  1. Navigate to Settings → Appearance (Theme mode).
  2. Toggle between System, Light, Dark sequentially.
  3. Return to CameraScreen.
- Expected Results:
  1. Changes apply instantaneously (no restart).
  2. Compose surfaces update colors (e.g., top bar).
  3. Setting persists after app relaunch.
- Failure Notes / Diagnostics: Validate `theme_mode` entry in DataStore using `adb shell run-as com.scanium.app cat files/datastore/settings_preferences.preferences_pb`.
- Automation Hints: Add `testTag("theme-toggle")`; instrumentation should assert `MaterialTheme.colorScheme.isLight`.

***REMOVED******REMOVED******REMOVED*** SCN-SMOKE-011 – Developer Options Health Snapshot
- [ ] Status
- Test ID: SCN-SMOKE-011
- Title: Developer screen shows healthy backend & copy works
- Area: Developer
- Preconditions: Debug build; developer mode toggle accessible.
- Test Data / Setup: Ensure backend reachable.
- Steps:
  1. From Settings, open Developer Options.
  2. Enable Developer Mode if off.
  3. Tap Refresh; after data loads, tap Copy diagnostics.
- Expected Results:
  1. Backend health row shows “Healthy” with latency.
  2. Permissions list marks Camera granted, Mic (per prior test) state correct.
  3. Snackbar confirms diagnostics copied; clipboard contains JSON snippet (no secrets).
- Failure Notes / Diagnostics: Compare base URL shown with `BuildConfig.SCANIUM_API_BASE_URL`; ensure copy uses sanitized config.
- Automation Hints: Tag `testTag("diagnostics-refresh")`, `testTag("copy-diagnostics")`; `@smoke`.

---

***REMOVED******REMOVED*** Full Regression Suite
***REMOVED******REMOVED******REMOVED*** SCN-REG-001 – FTUE Spotlight Layout Audit
- [ ] Status
- Test ID: SCN-REG-001
- Title: Verify spotlight cutouts align with controls on multiple densities
- Area: FTUE
- Preconditions: Device with different DPI (phone + tablet if possible); FTUE enabled.
- Test Data / Setup: Force `Force First-Time Tour` toggle off (normal) then on for repeated runs.
- Steps:
  1. Reset FTUE flags (Developer → Reset Tour Progress).
  2. Relaunch and measure overlay bounds for each step using developer inspector.
  3. Compare on secondary device.
- Expected Results:
  1. Cutout centers align within ±4 dp of control center.
  2. Tooltip arrow points to correct element.
  3. No overlay clipping on curved screens.
- Failure Notes / Diagnostics: Capture screenshot + overlay bounding boxes from `TourViewModel.targetBounds`.
- Automation Hints: `testTag("tour-overlay-step-{index}")` to assert coordinates; `@regression @ftue`.

***REMOVED******REMOVED******REMOVED*** SCN-REG-002 – Force FTUE Toggle Behavior
- [ ] Status
- Test ID: SCN-REG-002
- Title: Developer toggle forces tour on every launch
- Area: Developer
- Preconditions: Developer options available.
- Test Data / Setup: None.
- Steps:
  1. In Developer Options, enable “Force First-Time Tour”.
  2. Relaunch app twice.
- Expected Results:
  1. Tour overlay appears on every launch regardless of completion.
  2. Disabling the toggle returns to normal once tour manually completed.
- Failure Notes / Diagnostics: Inspect DataStore flag `force_ftue`.
- Automation Hints: Hook to `DeveloperOptionsViewModel.forceFtueTour`; tag `testTag("force-ftue-switch")`.

***REMOVED******REMOVED******REMOVED*** SCN-REG-003 – Tour Reset Button
- [ ] Status
- Test ID: SCN-REG-003
- Title: Reset clears completion state
- Area: FTUE
- Preconditions: Tour previously completed.
- Test Data / Setup: None.
- Steps:
  1. In Developer Options, tap “Reset Tour Progress”.
  2. Relaunch Scanium.
- Expected Results:
  1. Tour reappears once.
  2. `FtueRepository` value flips to false then true after completion.
- Failure Notes / Diagnostics: Monitor logcat for `TourViewModel` reset.
- Automation Hints: Add `testTag("reset-tour-button")`; instrumentation ensures DataStore update.

***REMOVED******REMOVED******REMOVED*** SCN-REG-004 – Keep Screen Awake During Scanning
- [ ] Status
- Test ID: SCN-REG-004
- Title: Camera preview prevents screen dim/off while scanning
- Area: Camera
- Preconditions: Device display timeout set to 30s; camera permission granted.
- Test Data / Setup: Begin scanning session for >2 minutes.
- Steps:
  1. Hold device with CameraScreen active for 2 minutes without touching screen.
  2. Observe display state.
- Expected Results:
  1. Screen stays awake (flag `keepScreenOn` active).
  2. No Android “Screen dimmed” message.
- Failure Notes / Diagnostics: Check `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON`; log entry in `CameraScreen`.
- Automation Hints: Compose instrumentation can assert `LocalView.keepScreenOn == true`.

***REMOVED******REMOVED******REMOVED*** SCN-REG-005 – Camera Lens Flip
- [ ] Status
- Test ID: SCN-REG-005
- Title: Front/back switch binds successfully
- Area: Camera
- Preconditions: Device has front camera.
- Test Data / Setup: None.
- Steps:
  1. Tap camera switch icon repeatedly (10x).
  2. Observe preview orientation each time.
- Expected Results:
  1. Each tap rebinds preview without crash.
  2. Lens icon reflects current state.
- Failure Notes / Diagnostics: Inspect `CameraXManager` logs for binding errors; rebind attempts counter should reset.
- Automation Hints: Tag button `testTag("lens-switch")`; instrumentation loops toggles; `@regression`.

***REMOVED******REMOVED******REMOVED*** SCN-REG-006 – Scan Modes & Gestures
- [ ] Status
- Test ID: SCN-REG-006
- Title: Tap vs long-press capture across all scan modes
- Area: Camera
- Preconditions: Items available; classification toggles default.
- Test Data / Setup: Use objects, barcode, document.
- Steps:
  1. Cycle through Items / Barcode / Document icons.
  2. In each mode, tap shutter, then long-press for continuous scanning.
  3. Ensure highlight states update.
- Expected Results:
  1. Mode icons show selected semantics for current mode.
  2. Tap captures once; long-press enters continuous scanning until release or tap.
  3. Document mode uses portrait overlay.
- Failure Notes / Diagnostics: Check `currentScanMode` state in logs.
- Automation Hints: Tag icons `testTag("mode-{modeName}")`; use semantics `selected=true`.

***REMOVED******REMOVED******REMOVED*** SCN-REG-007 – Resolution Selector
- [ ] Status
- Test ID: SCN-REG-007
- Title: Resolution setting affects file size/orientation
- Area: Camera
- Preconditions: Storage permission granted if saving.
- Test Data / Setup: Use Camera settings overlay to set Low/Normal/High; capture sample assets and inspect output via `adb pull`.
- Steps:
  1. Open Camera settings overlay.
  2. Switch to Low, capture photo; repeat for Normal and High.
  3. Export logs of resulting file dimensions.
- Expected Results:
  1. Low resolution < Normal < High in both width/height and byte size.
  2. Orientation metadata matches portrait when device upright.
- Failure Notes / Diagnostics: Check saved file EXIF; ensure `CameraViewModel.captureResolution`.
- Automation Hints: Tag resolution buttons `testTag("resolution-low")` etc; instrumentation can compare `ImageCapture` output metadata.

***REMOVED******REMOVED******REMOVED*** SCN-REG-008 – Shutter Sound Policy
- [ ] Status
- Test ID: SCN-REG-008
- Title: Respect system shutter sound
- Area: Camera
- Preconditions: Device volume on; “Camera shutter sound” OS toggle known.
- Test Data / Setup: Compare with OS camera to ensure baseline.
- Steps:
  1. With OS shutter sound enabled, capture photo.
  2. Disable OS shutter sound (where allowed) and capture again.
- Expected Results:
  1. App never plays custom audio; only system-level sound occurs when OS requires it.
  2. No double playback.
- Failure Notes / Diagnostics: Verify `CameraScreen` lacks media player logs; escalate if duplicates.
- Automation Hints: Hard to automate; instrumentation can assert absence of custom audio call; tag as `@regression @manual`.

***REMOVED******REMOVED******REMOVED*** SCN-REG-009 – Items Action Menu Integrity
- [ ] Status
- Test ID: SCN-REG-009
- Title: Overflow menu defaults to Sell on eBay; no Save-to-device entry
- Area: Items
- Preconditions: Items exist.
- Test Data / Setup: None.
- Steps:
  1. Open overflow/action menu for selected item(s).
  2. Record available options.
- Expected Results:
  1. “Sell on eBay” is default primary action.
  2. “Save to device” option absent if moved elsewhere per spec.
  3. Menu placement anchored to bottom sheet or top-right as implemented.
- Failure Notes / Diagnostics: Compare with product spec; screenshot menu.
- Automation Hints: Add `testTag("items-action-menu")`.

***REMOVED******REMOVED******REMOVED*** SCN-REG-010 – Long-Press Bubble Preview
- [ ] Status
- Test ID: SCN-REG-010
- Title: Bubble preview matches list thumbnail
- Area: Items
- Preconditions: High resolution thumbnail available.
- Test Data / Setup: Long press on item.
- Steps:
  1. Long-press and hold item card.
  2. Track preview bubble animation.
- Expected Results:
  1. Bubble animates from card bounds using `DraftPreviewOverlay`.
  2. Full-size preview image matches list crop (no scaling mismatch).
  3. Releasing finger dismisses overlay instantly.
- Failure Notes / Diagnostics: Use `previewBounds` logs; inspect `DraftPreviewOverlay`.
- Automation Hints: Provide `testTag("preview-overlay")`; instrumentation can check state change on pointer input.

***REMOVED******REMOVED******REMOVED*** SCN-REG-011 – Camera Stress Switching
- [ ] Status
- Test ID: SCN-REG-011
- Title: Rapid mode/resolution/lens switching remains stable
- Area: Performance
- Preconditions: Fully charged device.
- Test Data / Setup: Run for 5 minutes consecutively.
- Steps:
  1. Alternate between scan modes, lens switch, and resolution every 10 seconds while capturing frames.
  2. Monitor for dropped frames or binding errors.
- Expected Results:
  1. No crash or ANR.
  2. `CameraXManager` logs show successful rebind every time.
  3. Preview stays responsive, FPS stable.
- Failure Notes / Diagnostics: Capture logcat; collect `CameraXManager` stats.
- Automation Hints: Stress test via instrumentation loop; tag `@regression @performance`.

***REMOVED******REMOVED******REMOVED*** SCN-REG-012 – Items List Memory / Scroll
- [ ] Status
- Test ID: SCN-REG-012
- Title: List scroll with 30+ entries is smooth
- Area: Performance
- Preconditions: Populate DB with ≥30 items (reuse dataset or import fixtures).
- Test Data / Setup: Optionally run script to insert dummy rows using `ScannedItemRepository`.
- Steps:
  1. Scroll from top to bottom repeatedly for 60s.
  2. Open and close preview overlay for random items.
- Expected Results:
  1. No dropped frames / Compose jank warnings.
  2. Memory usage stable (< 500MB).
  3. Scroll position maintained when returning from detail flows.
- Failure Notes / Diagnostics: Inspect `adb shell dumpsys gfxinfo com.scanium.app`.
- Automation Hints: Use `LazyColumn` semantics; instrumentation can count compose measure passes.

***REMOVED******REMOVED******REMOVED*** SCN-REG-013 – Assistant IME Handling
- [ ] Status
- Test ID: SCN-REG-013
- Title: Keyboard never covers latest messages
- Area: Assistant
- Preconditions: Multiple chat messages exist.
- Test Data / Setup: Send 3–4 messages to populate list.
- Steps:
  1. Focus input while keyboard up; type multi-line question.
  2. Send and observe auto-scroll.
- Expected Results:
  1. Last message always visible above keyboard.
  2. Chat auto-scrolls to new assistant reply without manual drag.
- Failure Notes / Diagnostics: Ensure `LazyColumn` `reverseLayout` not interfering; capture video.
- Automation Hints: Compose instrumentation can assert `lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.key == latestMessageId`.

***REMOVED******REMOVED******REMOVED*** SCN-REG-014 – Assistant Offline/Error Fallback
- [ ] Status
- Test ID: SCN-REG-014
- Title: Airplane mode triggers error banner & retry
- Area: Assistant
- Preconditions: At least one item context; backend reachable prior to test.
- Test Data / Setup: Enable Airplane mode during assistant send.
- Steps:
  1. Send question.
  2. While sending, enable Airplane mode.
  3. Observe error banner and Retry button.
- Expected Results:
  1. Assistant shows inline error (LoadingStage.ERROR) with retry CTA.
  2. Developer diagnostics reflect backend DOWN.
  3. Disabling Airplane mode + tapping Retry resends successfully.
- Failure Notes / Diagnostics: Check `AssistantViewModel.loadingStage`; gather logs.
- Automation Hints: Provide `testTag("assistant-error-banner")`; instrumentation toggles network via shell commands (if allowed).

***REMOVED******REMOVED******REMOVED*** SCN-REG-015 – Assistant Voice Error Banner
- [ ] Status
- Test ID: SCN-REG-015
- Title: Voice error displays actionable banner
- Area: Assistant
- Preconditions: Voice mode enabled; temporarily revoke mic permission via system settings.
- Test Data / Setup: None.
- Steps:
  1. Remove microphone permission in Android settings.
  2. Return to assistant and tap mic.
- Expected Results:
  1. Voice error banner shows “Microphone permission denied” with Retry disabled.
  2. Settings toggle automatically returns to off per policy.
- Failure Notes / Diagnostics: Developer diagnostics should mark microphone permission false.
- Automation Hints: Tag `testTag("voice-error-banner")`; instrumentation can call `UiAutomation.revokeRuntimePermission`.

***REMOVED******REMOVED******REMOVED*** SCN-REG-016 – Settings Toggles Consistency
- [ ] Status
- Test ID: SCN-REG-016
- Title: Cloud/assistant/voice toggles persist and propagate
- Area: Settings
- Preconditions: None.
- Test Data / Setup: Toggle each setting and monitor DataStore.
- Steps:
  1. Disable Cloud Classification; observe Camera settings banner.
  2. Enable “Allow assistant images”; check Assistant request payload logs.
  3. Toggle Share Diagnostics and verify Sentry enabling/disabling in logcat.
- Expected Results:
  1. Camera overlay shows on-device-only info when cloud disabled.
  2. `SettingsRepository` writes new booleans; state restored after restart.
  3. Crash reporting toggles respond at runtime.
- Failure Notes / Diagnostics: Inspect DataStore file; check `ScaniumApplication` logs.
- Automation Hints: Tag toggles `testTag("toggle-cloud-classification")` etc.; instrumentation can assert BuildConfig-driven banners.

***REMOVED******REMOVED******REMOVED*** SCN-REG-017 – Developer Gating & Diagnostics Copy Sanitization
- [ ] Status
- Test ID: SCN-REG-017
- Title: Developer menu hidden in release & diagnostics copy redacts secrets
- Area: Developer
- Preconditions: Have release-like build variant or use flag to simulate.
- Test Data / Setup: Build release variant and sideload.
- Steps:
  1. Launch release build; confirm Settings lacks Developer Options row.
  2. On debug build, open Developer Options and copy diagnostics.
  3. Inspect clipboard text for tokens.
- Expected Results:
  1. Release build does not expose developer entry.
  2. Diagnostics JSON omits API keys/PII (base URL only, no tokens).
- Failure Notes / Diagnostics: Check `BuildConfig.DEBUG`; ensure gating works.
- Automation Hints: For automation, differentiate variants using BuildConfig; tag `testTag("developer-options-row")`.

***REMOVED******REMOVED******REMOVED*** SCN-REG-018 – Backend Degraded Handling
- [ ] Status
- Test ID: SCN-REG-018
- Title: Simulate backend down scenario and verify user messaging
- Area: Backend
- Preconditions: Ability to disable backend (stop server or edit hosts).
- Test Data / Setup: Stop backend service before test.
- Steps:
  1. Launch CameraScreen; note any banners (cloud offline).
  2. Attempt assistant ask; observe fallback message.
  3. Open Developer Options → System Health.
- Expected Results:
  1. Camera indicates “using on-device only” reminder.
  2. Assistant gracefully reports limited mode and logs failure.
  3. System Health backend status = DOWN with meaningful detail.
- Failure Notes / Diagnostics: Provide server logs; confirm `DiagnosticsRepository` detail text matches HTTP error.
- Automation Hints: Tag `testTag("cloud-warning-banner")` in Camera settings; instrumentation may stub backend via MockWebServer.

***REMOVED******REMOVED******REMOVED*** SCN-REG-019 – Selling Flow Error Handling
- [ ] Status
- Test ID: SCN-REG-019
- Title: Selling flow handles empty selection and backend errors
- Area: Selling
- Preconditions: Items exist.
- Test Data / Setup: Ensure backend or MockEbay can be toggled offline.
- Steps:
  1. Attempt to open Sell on eBay with zero items selected – expect validation message.
  2. Select items and open flow while backend offline.
  3. Observe error banners and ability to retry/cancel.
- Expected Results:
  1. Empty-selection attempt shows snackbar “Select at least one item”.
  2. Backend failure surfaces inline error and no crash.
  3. Returning to items leaves state intact.
- Failure Notes / Diagnostics: Check `SellOnEbayScreen` logs; capture error strings.
- Automation Hints: Add `testTag("sell-error-banner")`; instrumentation can stub data layer.

***REMOVED******REMOVED******REMOVED*** SCN-REG-020 – Release-Like Sanity Sweep
- [ ] Status
- Test ID: SCN-REG-020
- Title: No debug UI or sensitive data leaks in release build
- Area: Theme / Release
- Preconditions: Release variant installed; backend reachable.
- Test Data / Setup: None.
- Steps:
  1. Navigate all main screens (Camera, Items, Assistant, Settings).
  2. Trigger Diagnostics copy (if accessible) and share flows.
  3. Inspect logcat for accidental debug statements (no PII).
- Expected Results:
  1. No Developer menu, verbose logging, or placeholder text visible.
  2. Diagnostics copy (if accessible) only includes high-level info; no tokens, API keys, personal data.
  3. App icons and toasts use production branding.
- Failure Notes / Diagnostics: Attach screenshot evidence; highlight any stray debug components.
- Automation Hints: Instrumentation should guard by BuildConfig + UI snapshot tests; tag `@regression @release`.

---

***REMOVED******REMOVED*** Failure Escalation Checklist
- Gather `adb logcat` filtered by `ScaniumApplication`, `CameraScreen`, `AssistantVoice`, `DiagnosticsRepository`.
- Attach Developer → “Copy diagnostics” payload when backend or permissions fail.
- Record screen for visual bugs (overlays, animations).
- Note BuildConfig fields (API base URL, Sentry DSN) from Developer options snapshot.

***REMOVED******REMOVED*** Automation Conversion Notes
- Every test case already includes `Automation Hints` for `testTag` additions; update Compose components accordingly.
- Prefer Compose testing with `createAndroidComposeRule<MainActivity>()` and navigation to target route.
- Backend-dependent tests should inject MockWebServer to produce success/failure responses.
- Use `DataStoreTestHarness` to preseed SettingsRepository when possible.
