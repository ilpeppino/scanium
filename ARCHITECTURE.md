***REMOVED*** Objecta - Architecture Documentation

***REMOVED******REMOVED*** Overview

Objecta is a camera-first Android application that demonstrates real-time object detection and price estimation for the EU second-hand market. The app uses Google ML Kit for on-device object detection and provides a proof-of-concept for scanning physical items and estimating their resale value.

**Project Name:** Objecta (formerly ResaleVision)
**Package:** `com.example.objecta`
**Minimum SDK:** 24 (Android 7.0)
**Target SDK:** 34 (Android 14)
**Language:** Kotlin
**UI Framework:** Jetpack Compose with Material 3

---

***REMOVED******REMOVED*** Table of Contents

1. [High-Level Architecture](***REMOVED***high-level-architecture)
2. [Architectural Pattern](***REMOVED***architectural-pattern)
3. [Module Organization](***REMOVED***module-organization)
4. [Layer Architecture](***REMOVED***layer-architecture)
5. [Key Architectural Decisions](***REMOVED***key-architectural-decisions)
6. [Data Flow](***REMOVED***data-flow)
7. [Technology Stack](***REMOVED***technology-stack)
8. [Design Patterns](***REMOVED***design-patterns)
9. [Future Considerations](***REMOVED***future-considerations)

---

***REMOVED******REMOVED*** High-Level Architecture

Objecta follows a **simplified MVVM (Model-View-ViewModel)** architecture with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                            │
│  (Jetpack Compose Screens + Navigation)                     │
│  - CameraScreen                                             │
│  - ItemsListScreen                                          │
│  - ItemDetailDialog                                         │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  ↓
┌─────────────────────────────────────────────────────────────┐
│                    ViewModel Layer                          │
│  (State Management)                                         │
│  - ItemsViewModel (shared across screens)                  │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  ↓
┌─────────────────────────────────────────────────────────────┐
│                   Domain/Business Logic                     │
│  - CameraXManager (camera lifecycle & analysis)            │
│  - ObjectDetectorClient (ML Kit wrapper)                   │
│  - PricingEngine (price generation logic)                  │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  ↓
┌─────────────────────────────────────────────────────────────┐
│                     Data Layer                              │
│  - ScannedItem (data model)                                │
│  - ItemCategory (enum)                                      │
│  - In-memory state (StateFlow)                             │
└─────────────────────────────────────────────────────────────┘
```

---

***REMOVED******REMOVED*** Architectural Pattern

***REMOVED******REMOVED******REMOVED*** MVVM (Model-View-ViewModel)

**Why MVVM?**
- **Separation of Concerns:** UI logic is separated from business logic and data management
- **Testability:** ViewModels can be unit tested independently of Android framework
- **Lifecycle Awareness:** ViewModels survive configuration changes (rotation, etc.)
- **Compose Integration:** Perfect fit with Compose's reactive state management
- **Scalability:** Easy to extend with additional screens and features

**Implementation Details:**
- **View (UI):** Jetpack Compose screens (`CameraScreen`, `ItemsListScreen`)
- **ViewModel:** `ItemsViewModel` manages shared state across screens
- **Model:** `ScannedItem` data class, `ItemCategory` enum
- **State Management:** Kotlin `StateFlow` for reactive updates

**What problems does this solve?**
- Prevents tight coupling between UI and business logic
- Survives configuration changes without data loss
- Enables reactive UI updates when data changes
- Provides a clear contract between layers

---

***REMOVED******REMOVED*** Module Organization

***REMOVED******REMOVED******REMOVED*** Single-Module Architecture

The project uses a **single-module** structure for simplicity:

```
app/
├── src/main/
│   ├── java/com/example/objecta/
│   │   ├── MainActivity.kt                 ***REMOVED*** Entry point
│   │   ├── ObjectaApp.kt                   ***REMOVED*** Root composable
│   │   ├── camera/                         ***REMOVED*** Camera-related code
│   │   │   ├── CameraScreen.kt            ***REMOVED*** Camera UI
│   │   │   └── CameraXManager.kt          ***REMOVED*** Camera logic
│   │   ├── items/                          ***REMOVED*** Items/detection results
│   │   │   ├── ScannedItem.kt             ***REMOVED*** Data model
│   │   │   ├── ItemsViewModel.kt          ***REMOVED*** State management
│   │   │   ├── ItemsListScreen.kt         ***REMOVED*** List UI
│   │   │   └── ItemDetailDialog.kt        ***REMOVED*** Detail dialog
│   │   ├── ml/                             ***REMOVED*** Machine learning
│   │   │   ├── ObjectDetectorClient.kt    ***REMOVED*** ML Kit wrapper
│   │   │   ├── ItemCategory.kt            ***REMOVED*** Category enum
│   │   │   └── PricingEngine.kt           ***REMOVED*** Price logic
│   │   ├── navigation/                     ***REMOVED*** Navigation
│   │   │   └── NavGraph.kt                ***REMOVED*** Navigation setup
│   │   └── ui/theme/                       ***REMOVED*** Material 3 theme
│   │       ├── Color.kt
│   │       ├── Theme.kt
│   │       └── Type.kt
│   ├── AndroidManifest.xml
│   └── res/                                ***REMOVED*** Resources
└── build.gradle.kts
```

**Why Single-Module?**
- **Simplicity:** Appropriate for PoC/prototype scope
- **Fast Builds:** No multi-module compilation overhead
- **Easy Navigation:** All code in one place
- **Reduced Complexity:** No module dependency management

**Package Organization Rationale:**
- **Feature-based packages** (`camera/`, `items/`, `ml/`) for logical grouping
- **Shared infrastructure** (`navigation/`, `ui/theme/`) in dedicated packages
- Each package contains related UI, logic, and models together
- Clear ownership boundaries between features

---

***REMOVED******REMOVED*** Layer Architecture

***REMOVED******REMOVED******REMOVED*** 1. UI Layer (Jetpack Compose)

**Components:**
- `CameraScreen` - Full-screen camera with gesture detection
- `ItemsListScreen` - List of detected items
- `ItemDetailDialog` - Detail view for individual items
- `ObjectaApp` - Root composable, navigation setup

**Responsibilities:**
- Render UI based on ViewModel state
- Handle user interactions (tap, long-press, navigation)
- Request runtime permissions
- Display loading/error states

**Key Technologies:**
- Jetpack Compose (declarative UI)
- Material 3 design system
- AndroidView for CameraX preview integration
- Gesture detection via `Modifier.pointerInput`

**Why Compose?**
- Modern, declarative UI paradigm
- Less boilerplate than XML layouts
- Better state management integration
- Excellent preview/tooling support
- Future-proof (Google's recommended approach)

***REMOVED******REMOVED******REMOVED*** 2. ViewModel Layer

**Components:**
- `ItemsViewModel` - Manages detected items state

**Responsibilities:**
- Hold UI state (list of scanned items)
- Provide operations (add, remove, clear items)
- Deduplication logic (tracking IDs)
- Survive configuration changes

**State Management:**
```kotlin
private val _items = MutableStateFlow<List<ScannedItem>>(emptyList())
val items: StateFlow<List<ScannedItem>> = _items.asStateFlow()
```

**Why StateFlow?**
- Reactive updates to UI
- Lifecycle-aware collection in Compose
- Type-safe state representation
- Built-in backpressure handling

**Shared ViewModel Pattern:**
- Single `ItemsViewModel` instance shared between screens
- Initialized at app root (`ObjectaApp`)
- Passed to screens via navigation
- Enables communication between camera and list screens

***REMOVED******REMOVED******REMOVED*** 3. Domain/Business Logic Layer

**Components:**

***REMOVED******REMOVED******REMOVED******REMOVED*** CameraXManager
- Manages CameraX lifecycle and configuration
- Binds camera preview to `PreviewView`
- Handles image analysis pipeline
- Coordinates single-shot capture vs continuous scanning
- Image preprocessing (rotation, format conversion)

**Why separate manager class?**
- Encapsulates complex CameraX setup
- Reusable across different screens
- Testable independently of UI
- Clear lifecycle management

***REMOVED******REMOVED******REMOVED******REMOVED*** ObjectDetectorClient
- Wraps Google ML Kit Object Detection API
- Configures detector for multiple objects + classification
- Converts ML Kit results to app domain models
- Handles thumbnail cropping from detected bounding boxes
- Maps ML Kit categories to app categories

**Why wrapper pattern?**
- Isolates ML Kit dependencies
- Makes it easy to swap ML providers
- Provides domain-specific API
- Simplifies error handling

***REMOVED******REMOVED******REMOVED******REMOVED*** PricingEngine
- Generates mock price ranges based on category
- Takes bounding box size into account
- Adds randomization for realistic variance
- Returns EUR-formatted prices

**Why separate engine?**
- Centralizes pricing logic
- Easy to replace with real API later
- Testable business rules
- Clear pricing strategy

***REMOVED******REMOVED******REMOVED*** 4. Data Layer

**Components:**
- `ScannedItem` - Detected object data model
- `ItemCategory` - Category enum with ML Kit mapping
- In-memory state (no persistence)

**Data Model:**
```kotlin
data class ScannedItem(
    val id: String,                      // Tracking ID
    val thumbnail: Bitmap?,              // Cropped image
    val category: ItemCategory,          // Classification
    val priceRange: Pair<Double, Double>, // EUR prices
    val timestamp: Long                  // Detection time
)
```

**Why this structure?**
- Immutable data classes (thread-safe)
- All info needed for UI in one place
- Includes formatted price getter for convenience
- Timestamp enables sorting/filtering

**No Persistence Layer:**
- PoC scope - ephemeral data
- Items lost on app close (intentional)
- Future: could add Room database or remote sync

---

***REMOVED******REMOVED*** Key Architectural Decisions

***REMOVED******REMOVED******REMOVED*** 1. No Dependency Injection Framework

**Decision:** Use manual dependency injection (constructor injection)

**Rationale:**
- **Simplicity:** PoC doesn't need Hilt/Koin complexity
- **Transparency:** Clear dependency graph
- **Learning:** Easier to understand flow
- **Performance:** No DI overhead

**Implementation:**
```kotlin
val cameraManager = remember {
    CameraXManager(context, lifecycleOwner)
}
```

**When to change:** If app grows beyond 5-10 screens or needs complex scoping

***REMOVED******REMOVED******REMOVED*** 2. Single-Activity Architecture

**Decision:** One `MainActivity` with Compose navigation

**Rationale:**
- Modern Android best practice
- Simplified lifecycle management
- Shared element transitions (future)
- Reduced memory overhead
- Compose navigation integration

**What problems does this solve?**
- No complex fragment transactions
- Single source of truth for navigation
- Easier deep linking
- Better state preservation

***REMOVED******REMOVED******REMOVED*** 3. Camera-First UX with CameraX

**Decision:** App opens directly to camera screen using CameraX

**Rationale:**
- **CameraX Benefits:**
  - Abstracts Camera2 complexity
  - Lifecycle-aware
  - Consistent behavior across devices
  - Automatic camera selection
  - Built-in image analysis pipeline

**Why camera-first?**
- Primary use case is scanning
- Reduces friction (no extra taps)
- Clear value proposition
- Modern app pattern (like Instagram, Snapchat)

**Integration with Compose:**
```kotlin
AndroidView(factory = { PreviewView(it) })
```
- Bridges imperative CameraX with declarative Compose
- Full gesture support via `Modifier.pointerInput`

***REMOVED******REMOVED******REMOVED*** 4. On-Device ML with ML Kit

**Decision:** Google ML Kit Object Detection (on-device)

**Rationale:**
- **Privacy:** No images sent to cloud
- **Performance:** Real-time processing
- **Offline:** Works without internet
- **Cost:** No API fees
- **Accuracy:** Good enough for PoC

**Configuration:**
```kotlin
ObjectDetectorOptions.Builder()
    .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
    .enableMultipleObjects()
    .enableClassification()
```

**Trade-offs:**
- Limited to coarse categories (not fine-grained product recognition)
- Less accurate than cloud models
- Device-dependent performance

**Future Path:** Could add custom TensorFlow Lite model for specific product categories

***REMOVED******REMOVED******REMOVED*** 5. Gesture-Based Interaction

**Decision:** Tap for single capture, long-press for continuous scan

**Rationale:**
- **Intuitive:** Familiar mobile patterns
- **Efficient:** No extra UI controls needed
- **Flexible:** Single item vs batch scanning
- **Visual feedback:** Scanning indicator

**Implementation:**
```kotlin
detectTapGestures(
    onTap = { /* capture */ },
    onLongPress = { /* start scan */ },
    onPress = {
        tryAwaitRelease()
        /* stop scan */
    }
)
```

**User Mental Model:**
- Tap = take a photo
- Hold = video scan mode

***REMOVED******REMOVED******REMOVED*** 6. Reactive State Management

**Decision:** Kotlin Flow + StateFlow for state

**Rationale:**
- **Native:** Part of Kotlin coroutines
- **Efficient:** Conflated (latest value only)
- **Lifecycle-aware:** `collectAsState()` in Compose
- **Type-safe:** Compile-time guarantees

**Pattern:**
```kotlin
// ViewModel
private val _items = MutableStateFlow<List<ScannedItem>>(emptyList())
val items: StateFlow<List<ScannedItem>> = _items.asStateFlow()

// UI
val items by itemsViewModel.items.collectAsState()
```

**Why not LiveData?**
- Flow is Kotlin-first
- Better coroutine integration
- More operators (map, filter, etc.)
- Compose prefers Flow

***REMOVED******REMOVED******REMOVED*** 7. Navigation Architecture

**Decision:** Navigation Compose with centralized NavGraph

**Rationale:**
- **Type-safe:** Compile-time route validation
- **Declarative:** Matches Compose paradigm
- **Shared state:** Easy to pass ViewModels
- **Testable:** Can test navigation logic

**Structure:**
```kotlin
NavHost(startDestination = Routes.CAMERA) {
    composable(Routes.CAMERA) { CameraScreen(...) }
    composable(Routes.ITEMS_LIST) { ItemsListScreen(...) }
}
```

**Shared ViewModel Pattern:**
- ViewModel created at app root
- Passed to composables via parameters
- Both screens observe same state

***REMOVED******REMOVED******REMOVED*** 8. Mocked Pricing Logic

**Decision:** Local `PricingEngine` object with hardcoded ranges

**Rationale:**
- **PoC Scope:** No backend integration yet
- **Deterministic:** Easy to test
- **Extensible:** Clear interface for future API

**Design for Future:**
```kotlin
// Current
object PricingEngine {
    fun generatePriceRange(...): Pair<Double, Double>
}

// Future
interface PricingRepository {
    suspend fun getPriceEstimate(...): PriceEstimate
}
```

**Why not implement real API now?**
- Focuses on core ML/camera functionality
- Reduces complexity
- Faster iteration
- No backend dependency

***REMOVED******REMOVED******REMOVED*** 9. No Local Persistence

**Decision:** All data in memory (ViewModel state)

**Rationale:**
- **PoC Scope:** Sessions are short
- **Simplicity:** No database schema
- **Privacy:** Nothing stored on device
- **Performance:** Immediate access

**Trade-offs:**
- Items lost on app close
- Can't handle large item counts
- No historical data

**Future Options:**
- Room database for local cache
- DataStore for preferences
- Remote sync with backend

***REMOVED******REMOVED******REMOVED*** 10. Material 3 Design System

**Decision:** Use Material 3 (Material You) components

**Rationale:**
- **Modern:** Latest design language
- **Consistent:** Platform conventions
- **Accessible:** Built-in a11y features
- **Themeable:** Dynamic color support

**Benefits:**
- Professional appearance
- Less custom styling needed
- Familiar UX patterns
- Dark mode support (future)

---

***REMOVED******REMOVED*** Data Flow

***REMOVED******REMOVED******REMOVED*** Single-Shot Capture Flow

```
User taps screen
    ↓
CameraScreen detects tap gesture
    ↓
CameraXManager.captureSingleFrame()
    ↓
ImageAnalysis captures one frame
    ↓
Convert ImageProxy → Bitmap → InputImage
    ↓
ObjectDetectorClient.detectObjects()
    ↓
ML Kit processes image
    ↓
For each detected object:
    - Extract category from labels
    - Crop thumbnail from bounding box
    - Generate price range via PricingEngine
    - Create ScannedItem
    ↓
Return List<ScannedItem>
    ↓
ItemsViewModel.addItems() (with deduplication)
    ↓
StateFlow emits new list
    ↓
CameraScreen shows item count update
```

***REMOVED******REMOVED******REMOVED*** Continuous Scanning Flow

```
User long-presses screen
    ↓
CameraScreen detects long press
    ↓
CameraXManager.startScanning()
    ↓
ImageAnalysis analyzer set with 600ms throttle
    ↓
Every 600ms:
    - Capture frame
    - Run detection (same as single-shot)
    - Add items to ViewModel
    - Deduplicate via tracking IDs
    ↓
User releases press
    ↓
CameraXManager.stopScanning()
    ↓
Stop analyzer
```

***REMOVED******REMOVED******REMOVED*** Navigation Flow

```
App Launch
    ↓
MainActivity.onCreate()
    ↓
ObjectaApp composable
    ↓
Create shared ItemsViewModel
    ↓
NavHost → CameraScreen (start destination)
    ↓
User taps "Items (N)" button
    ↓
navController.navigate(Routes.ITEMS_LIST)
    ↓
ItemsListScreen (same ViewModel)
    ↓
User taps back arrow
    ↓
navController.popBackStack()
    ↓
Return to CameraScreen
```

---

***REMOVED******REMOVED*** Technology Stack

***REMOVED******REMOVED******REMOVED*** Core Android

| Technology | Version | Purpose |
|------------|---------|---------|
| Kotlin | 2.0.0 | Primary language |
| Android Gradle Plugin | 8.5.0 | Build system |
| Compile SDK | 34 | Target API level |
| Min SDK | 24 | Minimum support (Android 7.0) |

***REMOVED******REMOVED******REMOVED*** UI Layer

| Technology | Version | Purpose |
|------------|---------|---------|
| Jetpack Compose BOM | 2023.10.01 | Declarative UI |
| Compose Material 3 | - | Design system |
| Material Icons Extended | - | Icon library |
| Activity Compose | 1.8.2 | Compose-Activity bridge |

***REMOVED******REMOVED******REMOVED*** Architecture Components

| Technology | Version | Purpose |
|------------|---------|---------|
| Lifecycle Runtime KTX | 2.7.0 | Lifecycle awareness |
| ViewModel Compose | 2.7.0 | State management |
| Navigation Compose | 2.7.6 | Screen navigation |

***REMOVED******REMOVED******REMOVED*** Camera & ML

| Technology | Version | Purpose |
|------------|---------|---------|
| CameraX Core | 1.3.1 | Camera abstraction |
| CameraX Camera2 | 1.3.1 | Camera2 implementation |
| CameraX Lifecycle | 1.3.1 | Lifecycle integration |
| CameraX View | 1.3.1 | PreviewView component |
| ML Kit Object Detection | 17.0.1 | On-device ML |
| Coroutines Play Services | 1.7.3 | Async ML Kit operations |

***REMOVED******REMOVED******REMOVED*** Permissions

| Technology | Version | Purpose |
|------------|---------|---------|
| Accompanist Permissions | 0.32.0 | Permission handling in Compose |

***REMOVED******REMOVED******REMOVED*** Testing

| Technology | Version | Purpose |
|------------|---------|---------|
| JUnit | 4.13.2 | Unit testing |
| AndroidX Test | 1.1.5 | Instrumentation tests |
| Espresso | 3.5.1 | UI testing |
| Compose UI Test | - | Compose testing |

---

***REMOVED******REMOVED*** Design Patterns

***REMOVED******REMOVED******REMOVED*** 1. Repository Pattern (Simplified)

**Where:** `ItemsViewModel` acts as lightweight repository

**Why:** Centralizes data access and business logic

**Implementation:**
```kotlin
class ItemsViewModel : ViewModel() {
    private val _items = MutableStateFlow<List<ScannedItem>>(emptyList())
    val items: StateFlow<List<ScannedItem>> = _items.asStateFlow()

    fun addItems(newItems: List<ScannedItem>) { /* deduplication */ }
}
```

***REMOVED******REMOVED******REMOVED*** 2. Singleton Pattern

**Where:** `PricingEngine`, ML Kit detector

**Why:** Single instance, global access, stateless operations

**Implementation:**
```kotlin
object PricingEngine {
    fun generatePriceRange(...): Pair<Double, Double>
}
```

***REMOVED******REMOVED******REMOVED*** 3. Facade Pattern

**Where:** `CameraXManager`, `ObjectDetectorClient`

**Why:** Simplify complex subsystems (CameraX, ML Kit)

**Benefits:**
- Hide implementation details
- Provide domain-specific API
- Easier to test and mock

***REMOVED******REMOVED******REMOVED*** 4. Observer Pattern

**Where:** StateFlow/Compose state observation

**Why:** Reactive UI updates

**Implementation:**
```kotlin
val items by itemsViewModel.items.collectAsState()
```

***REMOVED******REMOVED******REMOVED*** 5. Factory Pattern

**Where:** `AndroidView` factory for PreviewView

**Why:** Lazy initialization of Android views in Compose

***REMOVED******REMOVED******REMOVED*** 6. Builder Pattern

**Where:** ML Kit `ObjectDetectorOptions`, CameraX builders

**Why:** Fluent API for complex configuration

---

***REMOVED******REMOVED*** Future Considerations

***REMOVED******REMOVED******REMOVED*** Scalability Improvements

1. **Multi-Module Architecture**
   - `:app` - UI & navigation
   - `:feature:camera` - Camera feature
   - `:feature:items` - Items feature
   - `:core:ml` - ML Kit wrapper
   - `:core:data` - Data layer
   - `:core:ui` - Shared UI components

2. **Dependency Injection**
   - Add Hilt when complexity increases
   - Better testability with mock dependencies
   - Scoped dependencies (screen-level, app-level)

3. **Local Persistence**
   - Room database for item history
   - DataStore for user preferences
   - Paging 3 for large lists

4. **Remote Backend**
   - Retrofit + OkHttp for API calls
   - Real pricing API integration
   - User accounts and cloud sync
   - Repository pattern with remote/local sources

5. **Advanced ML**
   - Custom TensorFlow Lite model
   - Fine-grained product recognition
   - Brand detection
   - Condition assessment (good/fair/poor)

6. **Testing**
   - Unit tests for ViewModels
   - Compose UI tests
   - Screenshot tests
   - End-to-end integration tests

7. **Performance**
   - Image optimization (reduce memory)
   - Background ML processing
   - Result caching
   - LazyList optimizations

8. **UX Enhancements**
   - Onboarding flow
   - Settings screen
   - Item history/search
   - Price trends
   - Share functionality
   - Accessibility improvements

9. **Analytics & Monitoring**
   - Firebase Analytics
   - Crashlytics
   - Performance monitoring
   - User behavior tracking

10. **CI/CD**
    - GitHub Actions / GitLab CI
    - Automated testing
    - Release automation
    - Version management

---

***REMOVED******REMOVED*** Summary

Objecta demonstrates a **clean, pragmatic architecture** suitable for a proof-of-concept Android application. The architecture prioritizes:

- **Simplicity** - No unnecessary abstractions or frameworks
- **Modularity** - Clear separation of concerns via packages
- **Testability** - Dependency injection, pure functions
- **Maintainability** - Consistent patterns, clear naming
- **Extensibility** - Easy to add features or swap implementations
- **Modern practices** - Jetpack Compose, Kotlin Flow, CameraX, ML Kit

The single-module MVVM approach with feature-based packages provides the right balance of structure and simplicity for the current scope, while the clean interfaces and separation of concerns make it straightforward to evolve into a multi-module architecture or add backend integration when needed.

**Key Strengths:**
- Camera-first UX with intuitive gestures
- On-device ML (privacy, performance)
- Reactive state management
- Material 3 design
- Clear data flow

**Known Limitations:**
- No persistence (intentional for PoC)
- Mocked pricing
- Coarse object categories
- No backend integration
- No user accounts

This architecture serves as a solid foundation for future enhancements while remaining simple enough to understand and modify quickly.
