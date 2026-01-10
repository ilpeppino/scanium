***REMOVED*** Edit Item V3 Testing Guide

This guide provides step-by-step instructions for testing the new structured fields Edit Item UI (V3) and verifying that the AI assistant export correctly uses structured attributes and images.

***REMOVED******REMOVED*** What Was Changed

***REMOVED******REMOVED******REMOVED*** 1. New Edit Item Screen (EditItemScreenV3.kt)

**Features:**
- **Labeled Fields:** Brand, Product/Type, Model, Color, Size, Material, Condition, Notes
- **Inline Clear:** Each field has an "X" button to clear it
- **Focus Navigation:** "Next" button moves between fields; Notes allows newlines
- **Photos Row:** Display primary and additional photos at the top
- **AI Button:** Saves current field values to attributes before calling AI

**Key Behavior:**
- Fields are prefilled from `item.attributes[]`
- Clicking "AI" or "Save" updates attributes with USER source
- Blank fields are not sent (no empty strings)
- Notes field maps to `item.displayLabel`

***REMOVED******REMOVED******REMOVED*** 2. Backend Contract (Already Correct)

The existing `AssistantRepository.kt` and backend are already correct:
- **Headers:** `X-API-Key`, `X-Scanium-Device-Id`
- **Multipart:** `payload` field (JSON), `itemImages[<itemId>]` (images)
- **Payload:** `items[]` with `itemId`, `message` non-empty, optional `history`

***REMOVED******REMOVED******REMOVED*** 3. Existing Tests (Already Passing)

- `AssistantRequestSchemaTest`: Verifies payload schema matches backend Zod schema
- `AssistantRepositoryMultipartTest`: Verifies multipart format and image attachments

***REMOVED******REMOVED*** Unit Tests Verification

Run the assistant tests to confirm everything passes:

```bash
cd /Users/family/dev/scanium

***REMOVED*** Run the schema and multipart tests
./gradlew :androidApp:testDevDebugUnitTest --tests "*AssistantRequestSchemaTest" --tests "*AssistantRepositoryMultipartTest"

***REMOVED*** Expected output:
***REMOVED*** BUILD SUCCESSFUL in Xs
***REMOVED*** All tests should pass
```

**What These Tests Verify:**
- ✅ `itemId` is present in items array
- ✅ `message` is non-empty
- ✅ Attributes don't contain blank/null values
- ✅ Multipart `payload` field is correct
- ✅ Multipart `itemImages[<itemId>]` field naming is correct
- ✅ Headers are included

***REMOVED******REMOVED*** Manual Testing Steps

***REMOVED******REMOVED******REMOVED*** Test 1: Edit Fields and Save

**Objective:** Verify fields are saved to item attributes

1. **Open the app** and navigate to an item
2. **Edit the item** using EditItemScreenV3
3. **Fill in fields:**
   - Brand: "Nike"
   - Product/Type: "Sneakers"
   - Model: "Air Max 90"
   - Color: "Black"
   - Size: "US 10"
   - Condition: "Used - Good"
   - Notes: "Comfortable shoes, barely worn"
4. **Click "Save"**
5. **Re-open the item** and verify:
   - All fields are pre-filled with the values you entered
   - No data was lost

**Expected Result:** ✅ Fields persist and are loaded correctly

---

***REMOVED******REMOVED******REMOVED*** Test 2: AI Export Uses Structured Attributes

**Objective:** Verify AI button sends structured attributes + images

1. **Open an item** with a photo
2. **Fill in structured fields:**
   - Brand: "Adidas"
   - Product/Type: "T-Shirt"
   - Color: "Red"
   - Size: "M"
   - Condition: "New"
3. **Click "AI" button** (AutoAwesome icon)
4. **Check Android logs** for the request:
   ```bash
   adb logcat -s ScaniumAssist:I ScaniumNet:D
   ```
5. **Look for log lines:**
   ```
   AssistantRepo: endpoint=https://your-nas/api/v1/assist/chat
   Request: items=1 history=0 message.length=<length> hasExportProfile=true hasPrefs=false
   Multipart request: correlationId=<id> imageCount=1 totalBytes=<bytes>
   ```

**Expected Result:** ✅ Request includes:
- `items[0].itemId` = item's ID
- `items[0].attributes[]` contains your filled fields with `source: "USER"`
- `message` is the export prompt (non-empty)
- Image is attached as `itemImages[<itemId>]`

---

***REMOVED******REMOVED******REMOVED*** Test 3: Backend Receives Correct Payload

**Objective:** Verify backend logs show correct request shape

1. **SSH into NAS:**
   ```bash
   ssh nas
   ```

2. **Watch backend logs:**
   ```bash
   docker logs -f --tail 50 scanium-backend-prod | grep -E "(assist|VALIDATION)"
   ```

3. **Trigger AI export** from the app (Test 2)

4. **Check logs for:**
   ```
   Assistant request
   itemCount: 1
   messageLength: <length>
   historyLength: 0
   visionExtractions: <count>
   imageCount: <count>
   ```

**Expected Result:** ✅ No `VALIDATION_ERROR` - backend accepts the request

---

***REMOVED******REMOVED******REMOVED*** Test 4: Clear Fields

**Objective:** Verify clear "X" buttons work

1. **Open an item** and edit it
2. **Fill in a few fields**
3. **Click "X"** on the Brand field
4. **Verify:** Brand field is now empty
5. **Click "X"** on multiple fields
6. **Click "Save"**
7. **Re-open the item**

**Expected Result:** ✅ Cleared fields are empty when re-opened

---

***REMOVED******REMOVED******REMOVED*** Test 5: Focus Navigation

**Objective:** Verify keyboard navigation works

1. **Open an item** and edit it
2. **Tap on Brand field** (keyboard opens)
3. **Press "Next"** on keyboard
4. **Verify:** Focus moves to Product/Type field
5. **Keep pressing "Next"** through all fields
6. **Verify:** Focus moves through: Brand → Product/Type → Model → Color → Size → Material → Condition → Notes
7. **In Notes field, press "Return"**
8. **Verify:** A newline is inserted (doesn't move to next field)

**Expected Result:** ✅ Focus navigation works as expected

---

***REMOVED******REMOVED******REMOVED*** Test 6: Photos Display

**Objective:** Verify photos row shows correctly

1. **Open an item** with a primary photo and additional photos
2. **Edit the item**
3. **Verify:**
   - Primary photo is shown with a blue border and "Primary" label
   - Additional photos are shown in the row
   - "Add" button is visible at the end

**Expected Result:** ✅ Photos row displays correctly

---

***REMOVED******REMOVED*** Backend Verification with Curl

If you encounter validation errors, use curl to verify the backend directly:

```bash
***REMOVED*** Copy the example from docs/assistant/CURL_VERIFICATION_EXAMPLE.md
***REMOVED*** Replace API_KEY and DEVICE_ID with your values

cd /Users/family/dev/scanium
cat docs/assistant/CURL_VERIFICATION_EXAMPLE.md

***REMOVED*** Run Example 1 to test multipart with images
***REMOVED*** Expected response: 200 OK with "reply" field
```

**Common Validation Errors:**

1. **Missing itemId:**
   - **Cause:** Items array doesn't include itemId
   - **Fix:** Verified in code - itemId is set at line 296 of ExportAssistantViewModel.kt

2. **Empty message:**
   - **Cause:** Message is blank
   - **Fix:** Verified in code - EXPORT_PROMPT is non-empty constant at line 35

3. **Wrong header name:**
   - **Cause:** Header is `X-Device-Id` instead of `X-Scanium-Device-Id`
   - **Fix:** Verified in code - correct header at line 350 of AssistantRepository.kt

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** Issue: Fields not saving

**Debug:**
```bash
adb logcat -s ItemsViewModel:D ItemsStateManager:D
```

**Look for:**
```
updateItemAttribute: itemId=<id> key=brand value=Nike
```

**Fix:** Ensure `saveFieldsToAttributes()` is called before AI button or Save button

---

***REMOVED******REMOVED******REMOVED*** Issue: AI export fails with VALIDATION_ERROR

**Debug:**
```bash
***REMOVED*** Check Android logs for request shape
adb logcat -s ScaniumAssist:I

***REMOVED*** Check backend logs
ssh nas "docker logs -f --tail 100 scanium-backend-prod | grep -A 10 'Validation failed'"
```

**Look for:**
- `Request: items=1` - should be at least 1
- `message.length=123` - should be > 0
- `zodErrors` in backend logs - shows exact validation failure

**Fix:** Check that:
1. Item has `itemId` set
2. Message is non-empty
3. Attributes have both `key` and `value`

---

***REMOVED******REMOVED******REMOVED*** Issue: Images not included

**Debug:**
```bash
adb logcat -s ScaniumAssist:I | grep -i image
```

**Look for:**
```
Multipart request: imageCount=1 totalBytes=12345
```

**Fix:** Ensure item has thumbnail:
- `item.thumbnail` or `item.thumbnailRef` is not null
- Thumbnail cache is populated

---

***REMOVED******REMOVED******REMOVED*** Issue: Backend rejects with 400

**Check backend logs:**
```bash
ssh nas "docker logs --tail 200 scanium-backend-prod | grep -E '(VALIDATION|zodErrors|requestShape)'"
```

**Common issues:**
- Missing `itemId` in items[0]
- `message` is empty or missing
- Invalid `role` in history (must be USER, ASSISTANT, or SYSTEM)
- Multipart missing `payload` field

---

***REMOVED******REMOVED*** Success Criteria

All tests should pass with these results:

- ✅ **Unit tests pass:** Schema and multipart tests succeed
- ✅ **Fields save:** Data persists across app restarts
- ✅ **AI export works:** Backend returns 200 OK with listing content
- ✅ **Attributes sent:** Structured attributes appear in backend logs
- ✅ **Images attached:** Multipart includes images
- ✅ **No validation errors:** Backend accepts the request

***REMOVED******REMOVED*** Next Steps

After verifying all tests pass:

1. **Update navigation** to use `EditItemScreenV3` instead of `EditItemScreenV2`
2. **Remove old screen** after confirming V3 works in production
3. **Monitor production logs** for any validation errors
4. **Test with real NAS** to ensure end-to-end flow works

***REMOVED******REMOVED*** Files Changed

- ✅ `androidApp/src/main/java/com/scanium/app/items/edit/EditItemScreenV3.kt` (new)
- ✅ `docs/assistant/CURL_VERIFICATION_EXAMPLE.md` (new)
- ✅ No backend changes required (already correct)
- ✅ Existing tests pass (AssistantRequestSchemaTest, AssistantRepositoryMultipartTest)

***REMOVED******REMOVED*** Contact

If you encounter issues not covered by this guide:
1. Check the curl examples in `docs/assistant/CURL_VERIFICATION_EXAMPLE.md`
2. Review backend logs for validation error details
3. Check Android logs for request building issues
4. Verify test coverage with unit tests
