***REMOVED*** Timeout Policy & Progress States Verification Guide

This guide explains the unified timeout policy and progressive UI states implemented to prevent "assistant temporarily unavailable" errors and improve stability UX.

***REMOVED******REMOVED*** What Changed

***REMOVED******REMOVED******REMOVED*** 1. Unified Timeout Policy (AssistantHttpConfig.kt)

**Problem:** Different OkHttpClient instances had inconsistent timeouts, causing premature "unavailable" errors.

**Solution:** Created a centralized timeout policy with different profiles:

| Profile | Connect | Read | Write | Call | Retry | Use Case |
|---------|---------|------|-------|------|-------|----------|
| **DEFAULT** | 15s | 60s | 30s | 75s | 1 | AI chat/export (aligned with backend ASSIST_PROVIDER_TIMEOUT_MS=30s) |
| **VISION** | 10s | 30s | 30s | 40s | 1 | Vision extraction (aligned with backend VISION_TIMEOUT_MS=10s) |
| **PREFLIGHT** | 5s | 8s | 5s | 10s | 0 | Quick health checks |
| **WARMUP** | 5s | 10s | 5s | 15s | 0 | Moderate warmup calls |

**Key Principles:**
- Client timeout > Backend timeout + buffer (prevents false "unavailable")
- Backend ASSIST_PROVIDER_TIMEOUT_MS = 30s → Client read = 60s
- Backend VISION_TIMEOUT_MS = 10s → Client read = 30s

***REMOVED******REMOVED******REMOVED*** 2. Progress State Machines

***REMOVED******REMOVED******REMOVED******REMOVED*** VisionEnrichmentState (ml/VisionEnrichmentState.kt)

State machine for vision insights extraction:

```
IDLE → ENRICHING → READY / FAILED
```

**States:**
- `Idle`: No enrichment in progress
- `Enriching`: "Extracting info from photo…" (shows timestamp for timeout detection)
- `Ready`: Enrichment completed successfully (no message - UI updates)
- `Failed`: "Couldn't extract details from the photo" (specific error, retryable flag)

**Helper Methods:**
- `getStatusMessage()`: Returns user-friendly progress/error message
- `itemIdOrNull()`: Gets associated item ID
- `transitionTo()`: Validates state transitions

***REMOVED******REMOVED******REMOVED******REMOVED*** ExportAssistantState (items/edit/ExportAssistantViewModel.kt)

Enhanced assistant export state with progress messages:

```
IDLE → GENERATING → SUCCESS / ERROR
```

**New Helper Methods:**
- `getStatusMessage()`: Returns "Drafting description…" during generation
- `isLongRunning()`: Returns true after 10 seconds (for "Still working…" indicator)

***REMOVED******REMOVED******REMOVED*** 3. Applied Unified Timeouts

Updated clients to use centralized policy:

**Before:**
```kotlin
private val client = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)  // Hardcoded
    .readTimeout(30, TimeUnit.SECONDS)     // Hardcoded
    .build()
```

**After:**
```kotlin
private val client = AssistantOkHttpClientFactory.create(
    config = AssistantHttpConfig.VISION,  // Centralized policy
    logStartupPolicy = false
)
```

**Updated Clients:**
- `VisionInsightsRepository` → Uses `AssistantHttpConfig.VISION`
- `EnrichmentRepository` → Uses `AssistantHttpConfig.VISION`
- `AssistantRepository` → Uses `AssistantHttpConfig.DEFAULT`

---

***REMOVED******REMOVED*** Verification Steps

***REMOVED******REMOVED******REMOVED*** 1. Unit Tests (Automated)

Run the test suite:

```bash
cd /Users/family/dev/scanium

***REMOVED*** Run all new tests
./gradlew :androidApp:testDevDebugUnitTest --tests "*VisionEnrichmentStateTest" --tests "*ExportAssistantStateTest" --tests "*AssistantTimeoutPolicyTest"

***REMOVED*** Expected: BUILD SUCCESSFUL, all tests pass
```

**Tests verify:**
- ✅ State machine transitions work correctly
- ✅ Status messages are accurate ("Extracting info…", "Drafting description…")
- ✅ Timestamp recording works
- ✅ Client timeouts > backend timeouts (no false "unavailable")
- ✅ Retry configuration correct (vision=1, preflight=0)
- ✅ Moderate delays complete without timeout
- ✅ Diagnostic info includes all configs

***REMOVED******REMOVED******REMOVED*** 2. Manual Testing - Vision Enrichment

**Objective:** Verify "Extracting info from photo…" message appears without "unavailable" error

1. Open the Scanium app
2. **Capture a photo** of an item with visible text/logos (e.g., Nike shoe)
3. **Watch for progress:**
   - Should see: "Extracting info from photo…"
   - Should NOT see: "Vision temporarily unavailable"
   - After ~2-5 seconds: Attributes appear (Brand: Nike, etc.)
4. **Verify logs:**
   ```bash
   adb logcat -s VisionInsightsRepo:I | grep -E "(Extracting|success)"
   ```
   - Should see successful extraction with no timeout errors

**Success Criteria:**
- ✅ Progress message shown during extraction
- ✅ Attributes appear after completion
- ✅ No premature "unavailable" errors

***REMOVED******REMOVED******REMOVED*** 3. Manual Testing - Assistant Export

**Objective:** Verify "Drafting description…" message and no premature failures

1. Open an item with attributes filled in
2. Tap **Edit** → Fill in structured fields (Brand, Color, Size, etc.)
3. Tap **AI** button (AutoAwesome icon)
4. **Watch for progress:**
   - Should see: "Drafting description…"
   - Should NOT see: "Assistant temporarily unavailable" (unless truly unavailable)
   - After ~3-10 seconds: Description appears
5. **For long operations (>10s):**
   - Should see: "Still working…" indicator
   - User can still type in other fields (non-blocking)
6. **Verify logs:**
   ```bash
   adb logcat -s ScaniumAssist:I | grep -E "(Drafting|reply)"
   ```
   - Should see successful generation with correlationId

**Success Criteria:**
- ✅ Progress message shown during generation
- ✅ "Still working…" appears after 10 seconds
- ✅ UI doesn't block typing during generation
- ✅ No premature "unavailable" errors

***REMOVED******REMOVED******REMOVED*** 4. Backend Timeout Alignment

**Verify client > backend timeout:**

```bash
***REMOVED*** Check backend timeouts
grep -E "(ASSIST_PROVIDER_TIMEOUT_MS|VISION_TIMEOUT_MS)" backend/.env.example

***REMOVED*** Expected:
***REMOVED*** VISION_TIMEOUT_MS=10000          (10 seconds)
***REMOVED*** ASSIST_PROVIDER_TIMEOUT_MS=30000 (30 seconds)

***REMOVED*** Check client timeouts (already verified in tests)
***REMOVED*** Vision client read: 30s > backend 10s ✅
***REMOVED*** Assistant client read: 60s > backend 30s ✅
```

***REMOVED******REMOVED******REMOVED*** 5. Preflight Performance

**Verify preflight doesn't block UI:**

1. Open Settings → Assistant
2. Toggle "Enable AI Assistant" switch
3. **Watch for preflight check:**
   - Should complete within 2-5 seconds
   - Should NOT hang for 10+ seconds
4. **Verify logs:**
   ```bash
   adb logcat -s AssistantPreflight:I | grep -E "(healthy|preflight)"
   ```

**Success Criteria:**
- ✅ Preflight completes quickly (< 5 seconds)
- ✅ UI remains responsive during check

---

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** Issue: "Extracting info from photo…" hangs forever

**Debug:**
```bash
adb logcat -s VisionInsightsRepo:I | grep -E "(timeout|error)"
```

**Possible causes:**
- Backend Vision service is down
- Network connectivity issue
- Backend taking > 30 seconds (exceeds timeout)

**Fix:**
- Check backend logs: `ssh nas "docker logs --tail 100 scanium-backend-prod | grep vision"`
- Verify network: `adb shell ping scanium.gtemp1.com`
- Increase client timeout if backend legitimately takes longer

***REMOVED******REMOVED******REMOVED*** Issue: "Drafting description…" shows "unavailable" after 5 seconds

**Debug:**
```bash
adb logcat -s ScaniumAssist:I | grep -E "(timeout|VALIDATION)"
```

**Possible causes:**
- Client timeout < backend timeout (shouldn't happen with new policy)
- Backend validation error (check backend logs)
- Network issue during multipart upload

**Fix:**
- Verify client timeout: AssistantHttpConfig.DEFAULT.readTimeoutSeconds should be 60
- Check backend: `ssh nas "docker logs --tail 100 scanium-backend-prod | grep ASSIST"`
- Test with curl (see docs/assistant/CURL_VERIFICATION_EXAMPLE.md)

***REMOVED******REMOVED******REMOVED*** Issue: Tests fail with "initializationError"

**Cause:** Robolectric not initialized properly

**Fix:**
```bash
***REMOVED*** Clean and rebuild
./gradlew clean
./gradlew :androidApp:testDevDebugUnitTest --tests "*VisionEnrichmentStateTest"
```

---

***REMOVED******REMOVED*** Backend Alignment Matrix

| Operation | Backend Timeout | Client Read Timeout | Buffer | Aligned? |
|-----------|----------------|---------------------|--------|----------|
| Vision Extraction | 10s (VISION_TIMEOUT_MS) | 30s | 20s | ✅ |
| AI Assistant | 30s (ASSIST_PROVIDER_TIMEOUT_MS) | 60s | 30s | ✅ |
| Preflight | N/A (fast) | 8s | N/A | ✅ |

**Verification Command:**
```bash
cd /Users/family/dev/scanium
./gradlew :androidApp:testDevDebugUnitTest --tests "*AssistantTimeoutPolicyTest.client timeout is greater than backend timeout*"

***REMOVED*** Expected: PASS
```

---

***REMOVED******REMOVED*** Diagnostic Commands

***REMOVED******REMOVED******REMOVED*** Check Timeout Configuration at Runtime

```bash
***REMOVED*** Android logs will show at startup:
adb logcat -s AssistantHttp:I | grep "Policy Initialized"

***REMOVED*** Expected output:
***REMOVED*** AssistantHttp: Assistant HTTP Policy Initialized:
***REMOVED***   Version: 1.0.0
***REMOVED***   Timeouts: connect=15s, read=60s, write=30s, call=75s, retries=1
***REMOVED***   Retry: 1x on transient errors (502/503/504, timeout, network)
***REMOVED***   Non-retryable: 400/401/403/404/429
```

***REMOVED******REMOVED******REMOVED*** Check State Machine Transitions

```bash
***REMOVED*** Vision enrichment state changes:
adb logcat -s VisionInsightsRepo:D | grep -E "(IDLE|ENRICHING|READY|FAILED)"

***REMOVED*** Assistant export state changes:
adb logcat -s ExportAssistantVM:D | grep -E "(Idle|Generating|Success|Error)"
```

***REMOVED******REMOVED******REMOVED*** Verify No "Unavailable" Errors During Valid Requests

```bash
***REMOVED*** Monitor for false "unavailable" errors:
adb logcat | grep -i "temporarily unavailable"

***REMOVED*** Should NOT appear during:
***REMOVED*** - Vision extraction (within 30s)
***REMOVED*** - AI generation (within 60s)
***REMOVED*** - Normal operation with backend reachable
```

---

***REMOVED******REMOVED*** Success Criteria Checklist

- ✅ **Unit tests pass** (state machines + timeout policy)
- ✅ **Vision enrichment** shows progress and completes without "unavailable"
- ✅ **Assistant export** shows progress and completes without "unavailable"
- ✅ **Long operations (>10s)** show "Still working…" indicator
- ✅ **Preflight checks** complete quickly (< 5s)
- ✅ **Client timeouts > backend timeouts** (verified in tests)
- ✅ **No regression** to premature "unavailable" errors

---

***REMOVED******REMOVED*** Files Changed

**Timeout Policy:**
- `androidApp/src/main/java/com/scanium/app/selling/assistant/network/AssistantHttpConfig.kt` (added VISION config)
- `androidApp/src/main/java/com/scanium/app/selling/assistant/network/AssistantOkHttpClientFactory.kt` (added VISION to diagnostics)

**Applied to Clients:**
- `androidApp/src/main/java/com/scanium/app/ml/VisionInsightsRepository.kt` (uses VISION config)
- `androidApp/src/main/java/com/scanium/app/enrichment/EnrichmentRepository.kt` (uses VISION config)

**State Machines:**
- `androidApp/src/main/java/com/scanium/app/ml/VisionEnrichmentState.kt` (new)
- `androidApp/src/main/java/com/scanium/app/items/edit/ExportAssistantViewModel.kt` (added helper methods)

**Tests:**
- `androidApp/src/test/java/com/scanium/app/ml/VisionEnrichmentStateTest.kt` (new)
- `androidApp/src/test/java/com/scanium/app/items/edit/ExportAssistantStateTest.kt` (new)
- `androidApp/src/test/java/com/scanium/app/selling/assistant/AssistantTimeoutPolicyTest.kt` (new)

---

***REMOVED******REMOVED*** Next Steps

After verification:

1. **Monitor production logs** for timeout patterns:
   ```bash
   ssh nas "docker logs -f scanium-backend-prod | grep -E '(timeout|ASSIST|vision)'"
   ```

2. **Adjust timeouts** if needed based on real-world data:
   - If backend consistently takes > 25s for vision: increase VISION_TIMEOUT_MS
   - If backend consistently takes > 55s for AI: increase ASSIST_PROVIDER_TIMEOUT_MS
   - Always ensure client timeout > backend timeout + 10s buffer

3. **UI enhancements** (future):
   - Add cancel button for long-running operations
   - Show estimated time remaining based on historical data
   - Implement retry with exponential backoff for transient errors

---

***REMOVED******REMOVED*** Contact

For issues or questions about timeout policy:
- Check this guide first
- Review test suite: `*AssistantTimeoutPolicyTest`, `*VisionEnrichmentStateTest`
- Check backend logs for actual timeout duration
- Verify network connectivity with `adb shell ping`
