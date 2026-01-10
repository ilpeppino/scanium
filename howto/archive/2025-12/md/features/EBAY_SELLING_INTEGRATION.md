# eBay Selling Integration (Mock)

**Status**: ✅ Implemented and working
**Version**: 1.0
**Date**: December 2024

## Overview

Complete end-to-end marketplace integration that connects real on-device ML Kit scanning with a mocked eBay API for demonstration and testing purposes. Users can scan items with the camera, select multiple items, review listing drafts, and post them to a simulated eBay marketplace with realistic behavior.

## Features

### User-Facing Features
- ✅ **Multi-selection UI**: Long-press to enter selection mode, tap to toggle items (defaults to **Sell on eBay** action for quicker listing flows)
- ✅ **Draft review screen**: Edit listing details before posting
- ✅ **Real-time status tracking**: Watch items transition through states (Posting → Listed/Failed)
- ✅ **Status badges**: Color-coded indicators on items list
- ✅ **View listings**: Open mock listing URLs in browser
- ✅ **High-quality images**: Automatic preparation for web/mobile viewing

### Technical Features
- ✅ **Background image processing**: All heavy work on `Dispatchers.IO`
- ✅ **Realistic mock behavior**: Configurable delays and failure modes
- ✅ **ViewModel communication**: Seamless status updates between screens
- ✅ **Debug settings**: Test various scenarios without code changes
- ✅ **Comprehensive logging**: Detailed logs for debugging and verification

## Architecture

### Package Structure

```
selling/
├── data/
│   ├── EbayApi.kt                    # Interface for eBay operations
│   ├── MockEbayApi.kt                # Mock implementation with realistic behavior
│   ├── MockEbayConfigManager.kt      # Singleton config manager
│   ├── EbayMarketplaceService.kt     # Orchestration layer
│   └── ListingRepository.kt          # In-memory listing cache
├── domain/
│   ├── Listing.kt                    # Listing and ListingDraft models
│   ├── ListingStatus.kt              # DRAFT, CREATING, ACTIVE, FAILED, ENDED
│   ├── ListingCondition.kt           # NEW, USED, REFURBISHED
│   ├── ListingImage.kt               # Image source and URI
│   ├── ListingError.kt               # Error types
│   └── ListingId.kt                  # Type-safe listing ID
├── ui/
│   ├── SellOnEbayScreen.kt           # Main sell screen UI
│   ├── ListingViewModel.kt           # Manages drafts and posting
│   ├── ListingViewModelFactory.kt    # Factory with dependencies
│   └── DebugSettingsDialog.kt        # Mock configuration UI
└── util/
    ├── ListingImagePreparer.kt       # Image quality optimization
    └── ListingDraftMapper.kt         # ScannedItem → ListingDraft
```

### Key Components

#### 1. ListingImagePreparer

Prepares high-quality images for listings with proper resolution and quality.

**Features**:
- Priority-based image selection: `fullImageUri` → `thumbnail` (scaled)
- Minimum resolution enforcement: 500×500
- Preferred resolution: 1600×1600
- JPEG compression: Quality 85
- Background processing: All work on `Dispatchers.IO`
- Comprehensive logging: Resolution, file size, quality, source

**Usage**:
```kotlin
val preparer = ListingImagePreparer(context)
val result = preparer.prepareListingImage(
    itemId = "item-123",
    fullImageUri = item.fullImageUri,
    thumbnail = item.thumbnail
)

when (result) {
    is PrepareResult.Success -> {
        Log.i(TAG, "Image prepared: ${result.width}×${result.height}, ${result.fileSizeBytes/1024}KB")
    }
    is PrepareResult.Failure -> {
        Log.e(TAG, "Image preparation failed: ${result.reason}")
    }
}
```

**Output Example**:
```
╔═══════════════════════════════════════════════════════════════
║ PREPARING LISTING IMAGE: item-abc123
║ fullImageUri: null
║ thumbnail: 300x300
║ Thumbnail too small (300x300), scaling up
║ Scaling: 300x300 → 1600x1600 (scale=5.33)
║ SUCCESS:
║   Source: thumbnail_scaled
║   Resolution: 1600x1600
║   File size: 245.73 KB
║   Quality: 85
║   URI: file:///cache/listing_images/item-abc123_listing.jpg
╚═══════════════════════════════════════════════════════════════
```

#### 2. MockEbayApi

Realistic eBay API simulation with configurable behavior.

**Configurable Parameters**:
- `simulateNetworkDelay`: Enable/disable delays (default: true)
- `minDelayMs`: Minimum delay (default: 400ms)
- `maxDelayMs`: Maximum delay (default: 1200ms)
- `failureMode`: Type of failure to simulate
- `failureRate`: Probability of failure (0.0-1.0)

**Failure Modes**:
- `NONE`: All requests succeed
- `NETWORK_TIMEOUT`: Simulates network timeout errors
- `VALIDATION_ERROR`: Simulates validation failures (empty title, etc.)
- `IMAGE_TOO_SMALL`: Simulates image quality rejections
- `RANDOM`: Random failures

**Mock Data**:
- Listing IDs: `EBAY-MOCK-{timestamp}-{random}` (e.g., `EBAY-MOCK-1702483920000-4567`)
- URLs: `https://mock.ebay.local/listing/{id}`
- Status: Always returns `ACTIVE` on success

**Example**:
```kotlin
val api = MockEbayApi(
    config = MockEbayConfig(
        simulateNetworkDelay = true,
        minDelayMs = 400,
        maxDelayMs = 1200,
        failureMode = MockFailureMode.VALIDATION_ERROR,
        failureRate = 0.2 // 20% failure rate
    )
)

val listing = api.createListing(draft, image) // May throw on failure
```

#### 3. MockEbayConfigManager

Singleton configuration manager with reactive updates.

**Usage**:
```kotlin
// Get current config
val config = MockEbayConfigManager.config.value

// Update config
MockEbayConfigManager.updateConfig(
    config.copy(
        failureMode = MockFailureMode.NETWORK_TIMEOUT,
        failureRate = 0.5
    )
)

// Or use convenience methods
MockEbayConfigManager.setNetworkDelayEnabled(false)
MockEbayConfigManager.setFailureMode(MockFailureMode.RANDOM, 0.3)
MockEbayConfigManager.resetToDefaults()

// Observe changes
MockEbayConfigManager.config.collect { config ->
    // React to config changes
}
```

#### 4. EbayMarketplaceService

Orchestrates the listing creation workflow.

**Workflow**:
1. Convert `ScannedItem` to `ListingDraft`
2. Prepare listing image (background thread)
3. Call eBay API to create listing
4. Cache result in `ListingRepository`
5. Return `ListingCreationResult`

**Error Handling**:
- Image preparation failures → `VALIDATION_ERROR`
- Network errors → `NETWORK_ERROR`
- Validation errors → `VALIDATION_ERROR`
- Other errors → `UNKNOWN_ERROR`

**Example**:
```kotlin
val service = EbayMarketplaceService(context, mockApi)

when (val result = service.createListingForItem(item)) {
    is ListingCreationResult.Success -> {
        val listing = result.listing
        // Update UI with success
    }
    is ListingCreationResult.Error -> {
        val error = result.error
        val message = result.message
        // Show error to user
    }
}
```

#### 5. ListingViewModel

Manages listing drafts and posting workflow.

**State Management**:
```kotlin
data class ListingDraftState(
    val draft: ListingDraft,
    val status: PostingStatus = PostingStatus.IDLE,
    val listing: Listing? = null,
    val error: ListingError? = null,
    val errorMessage: String? = null
)

data class ListingUiState(
    val drafts: List<ListingDraftState> = emptyList(),
    val isPosting: Boolean = false
)
```

**Key Methods**:
- `updateDraftTitle(itemId, title)`: Edit draft title
- `updateDraftPrice(itemId, priceText)`: Edit draft price
- `updateDraftCondition(itemId, condition)`: Edit draft condition
- `postSelectedToEbay()`: Post all drafts sequentially

**Communication with ItemsViewModel**:
```kotlin
// Before posting
itemsViewModel.updateListingStatus(itemId, ItemListingStatus.LISTING_IN_PROGRESS)

// After success
itemsViewModel.updateListingStatus(
    itemId = itemId,
    status = ItemListingStatus.LISTED_ACTIVE,
    listingId = listing.listingId.value,
    listingUrl = listing.externalUrl
)

// After failure
itemsViewModel.updateListingStatus(itemId, ItemListingStatus.LISTING_FAILED)
```

#### 6. ItemsViewModel (Enhanced)

Added listing status tracking methods.

**New Methods**:
```kotlin
fun updateListingStatus(
    itemId: String,
    status: ItemListingStatus,
    listingId: String? = null,
    listingUrl: String? = null
)

fun getListingStatus(itemId: String): ItemListingStatus?

fun getItem(itemId: String): ScannedItem?
```

**Enhanced ScannedItem**:
```kotlin
data class ScannedItem(
    // ... existing fields
    val fullImageUri: Uri? = null,
    val listingStatus: ItemListingStatus = ItemListingStatus.NOT_LISTED,
    val listingId: String? = null,
    val listingUrl: String? = null
)

enum class ItemListingStatus {
    NOT_LISTED,
    LISTING_IN_PROGRESS,
    LISTED_ACTIVE,
    LISTING_FAILED
}
```

## User Journey

### Complete Flow

1. **Scan Items**
   - Point camera at objects
   - Items detected via ML Kit
   - Items appear in list

2. **Select Items**
   - Navigate to Items List screen
   - Long-press an item → Enter selection mode
   - Tap additional items to select
   - Selection count shown in top bar

3. **Review Drafts**
   - Tap the default "Sell on eBay" action or pick another bulk action from the dropdown
   - Navigate to Sell screen
   - See draft cards for each selected item:
     - Image preview
     - Editable title (prefilled from the specific classification label, e.g., "Vintage mug" instead of generic categories)
     - Editable price (prefilled)
     - Condition picker (NEW/USED/REFURBISHED)

4. **Post to eBay**
   - Tap "Post to eBay (Mock)"
   - Button disabled during posting
   - Watch per-item status updates:
     - "POSTING" → Shows spinner
     - "SUCCESS" → Shows checkmark
     - "FAILURE" → Shows error icon

5. **View Results**
   - Navigate back to Items List
   - See status badges on items:
     - 🟦 "Listed" (blue) - Active listing
     - 🟨 "Posting..." (yellow) - In progress
     - 🟥 "Failed" (red) - Failed to post
   - Tap "View" button on listed items
   - Opens mock listing URL in browser

## Debug Settings

Access debug settings to test different scenarios.

### Configurable Options

1. **Network Delay Simulation**
   - Toggle on/off
   - Default: On (400-1200ms random delay)

2. **Failure Mode**
   - NONE: All requests succeed
   - NETWORK_TIMEOUT: Simulates timeouts
   - VALIDATION_ERROR: Simulates validation failures
   - IMAGE_TOO_SMALL: Simulates image quality errors
   - RANDOM: Random failures

3. **Failure Rate**
   - Slider: 0% to 100%
   - Controls probability of failure
   - Only active when failure mode ≠ NONE

### Testing Scenarios

**Test successful posting**:
```
Failure Mode: NONE
Failure Rate: 0%
Result: All items post successfully
```

**Test intermittent failures**:
```
Failure Mode: RANDOM
Failure Rate: 30%
Result: ~30% of items fail randomly
```

**Test all failures**:
```
Failure Mode: VALIDATION_ERROR
Failure Rate: 100%
Result: All items fail with validation error
```

**Test fast posting (no delays)**:
```
Network Delay: OFF
Failure Mode: NONE
Result: Instant posting without delays
```

## Logging

Comprehensive logging for debugging and verification.

### Image Preparation Logs

```
ListingImagePreparer: ╔═══════════════════════════════════════════════
ListingImagePreparer: ║ PREPARING LISTING IMAGE: item-123
ListingImagePreparer: ║ fullImageUri: null
ListingImagePreparer: ║ thumbnail: 300x300
ListingImagePreparer: ║ SUCCESS:
ListingImagePreparer: ║   Source: thumbnail_scaled
ListingImagePreparer: ║   Resolution: 1600x1600
ListingImagePreparer: ║   File size: 245.73 KB
ListingImagePreparer: ║   Quality: 85
ListingImagePreparer: ╚═══════════════════════════════════════════════
```

### Mock eBay API Logs

```
MockEbayApi: Creating listing for item: item-123 (title: Used Laptop)
MockEbayApi: Simulating network delay: 847ms
MockEbayApi: ✓ Listing created successfully: EBAY-MOCK-1702483920000-4567
MockEbayApi:   URL: https://mock.ebay.local/listing/EBAY-MOCK-1702483920000-4567
```

### Marketplace Service Logs

```
EbayMarketplaceService: ═════════════════════════════════════════════════════
EbayMarketplaceService: Creating listing for item: item-123
EbayMarketplaceService: Draft: Used Laptop - €450.0
EbayMarketplaceService: ✓ Listing created: EBAY-MOCK-1702483920000-4567
EbayMarketplaceService: ═════════════════════════════════════════════════════
```

### Listing ViewModel Logs

```
ListingViewModel: ══════════════════════════════════════════════════════════
ListingViewModel: Starting batch listing for 3 items
ListingViewModel: Posting item: item-123
ListingViewModel: ✓ Success: EBAY-MOCK-1702483920000-4567
ListingViewModel: Posting item: item-456
ListingViewModel: ✗ Failed: VALIDATION_ERROR - Title cannot be empty
ListingViewModel: Batch complete: 2 success, 1 failed
ListingViewModel: ══════════════════════════════════════════════════════════
```

## Testing

### Unit Tests

**ListingImagePreparerTest.kt**:
- ✅ Valid thumbnail succeeds
- ✅ Small thumbnail scales up
- ✅ No sources fails gracefully

**MockEbayConfigManagerTest.kt**:
- ✅ Initial config has defaults
- ✅ Update methods work correctly
- ✅ Failure rate clamped to valid range
- ✅ Reset to defaults works

**ItemListingStatusTest.kt**:
- ✅ All statuses have display names
- ✅ Enum values correct

**ItemsViewModelListingStatusTest.kt**:
- ✅ Update status changes item
- ✅ Only affects target item
- ✅ Get methods work correctly

### Manual Testing

**Checklist**:
- [ ] Scan 3+ items
- [ ] Long-press to select
- [ ] Tap to multi-select
- [ ] Navigate to sell screen
- [ ] Edit draft titles
- [ ] Edit draft prices
- [ ] Change conditions
- [ ] Post with delays enabled
- [ ] Verify status badges
- [ ] Tap "View" button
- [ ] Test with failure mode enabled
- [ ] Verify error handling

## Future Enhancements

### Short-term
- [ ] Add retry mechanism for failed listings
- [ ] Support batch editing (set same price for all)
- [ ] Add listing preview before posting
- [ ] Persist listing status across app restarts

### Medium-term
- [ ] Real eBay API integration
- [ ] OAuth authentication
- [ ] Real listing ID parsing
- [ ] Actual image upload
- [ ] Category mapping to eBay taxonomy
- [ ] Shipping options
- [ ] Return policy configuration

### Long-term
- [ ] Analytics dashboard
- [ ] Price recommendations from eBay sold listings
- [ ] Automated title generation (ML-based)
- [ ] Multi-marketplace support (eBay, Mercari, Poshmark)
- [ ] Listing templates
- [ ] Scheduled listings

## Migration to Real eBay API

To replace the mock with real eBay API:

1. **Create EbayOAuthManager**:
   - Implement OAuth 2.0 flow
   - Store tokens securely
   - Handle token refresh

2. **Implement RealEbayApi**:
   ```kotlin
   class RealEbayApi(
       private val client: OkHttpClient,
       private val authManager: EbayOAuthManager
   ) : EbayApi {
       override suspend fun createListing(
           draft: ListingDraft,
           image: ListingImage?
       ): Listing {
           // Real API calls using Retrofit
       }
   }
   ```

3. **Update ScaniumApp**:
   ```kotlin
   val ebayApi = if (BuildConfig.USE_MOCK_EBAY) {
       MockEbayApi(config)
   } else {
       RealEbayApi(httpClient, authManager)
   }
   ```

4. **Handle real listing lifecycle**:
   - Track actual listing status changes
   - Handle draft/scheduled/active states
   - Implement listing updates and deletions
   - Add error recovery

## License

[Same as main project]
