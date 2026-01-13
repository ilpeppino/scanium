***REMOVED*** Android Multi-Device Sync (Phase E)

***REMOVED******REMOVED*** Overview

The Scanium Android app implements offline-first synchronization with the backend Items API. Items are created and edited locally, then synchronized to the cloud when online.

**Key Features:**
- Offline-first: Create/edit items without network
- Automatic background sync every 15 minutes
- First sign-in auto-sync for existing items
- Last-write-wins conflict resolution
- Tombstone-based soft delete
- Local ID → Server ID mapping

***REMOVED******REMOVED*** Architecture

***REMOVED******REMOVED******REMOVED*** Components

```
┌─────────────────────────────────────────────────────┐
│ UI Layer (ViewModels, Composables)                  │
├─────────────────────────────────────────────────────┤
│ Repository Layer                                     │
│ ├─ ScannedItemRepository (CRUD operations)          │
│ └─ ScannedItemSyncer (sync interface)               │
├─────────────────────────────────────────────────────┤
│ Sync Layer                                           │
│ ├─ ItemSyncManager (push-pull sync orchestrator)    │
│ ├─ ItemSyncWorker (periodic background sync)        │
│ └─ FirstSyncManager (first sign-in migration)       │
├─────────────────────────────────────────────────────┤
│ Network Layer                                        │
│ ├─ ItemsApi (Retrofit-style HTTP client)            │
│ └─ AuthTokenInterceptor (auth + silent renewal)     │
├─────────────────────────────────────────────────────┤
│ Database Layer (Room)                                │
│ ├─ ScannedItemEntity (40+ fields + sync metadata)   │
│ ├─ ScannedItemDao (queries + sync operations)       │
│ └─ MIGRATION_9_10 (adds sync fields)                │
└─────────────────────────────────────────────────────┘
```

***REMOVED******REMOVED*** Database Schema

***REMOVED******REMOVED******REMOVED*** Sync Fields (v10)

```kotlin
@Entity(tableName = "scanned_items")
data class ScannedItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    // ... existing 40+ fields ...

    // Sync metadata (Phase E: v10)
    val serverId: String? = null,              // Backend Item.id
    val needsSync: Int = 1,                     // 0 = synced, 1 = needs push
    val lastSyncedAt: Long? = null,             // Last successful sync timestamp
    val syncVersion: Int = 1,                   // Optimistic locking version
    val clientUpdatedAt: Long = System.currentTimeMillis(),  // Client update time
    val deletedAt: Long? = null,                // Soft delete timestamp
)
```

***REMOVED******REMOVED******REMOVED*** Migration (v9 → v10)

**File:** `ScannedItemDatabase.kt`

```kotlin
private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add sync columns to scanned_items
        db.execSQL("ALTER TABLE scanned_items ADD COLUMN serverId TEXT")
        db.execSQL("ALTER TABLE scanned_items ADD COLUMN needsSync INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE scanned_items ADD COLUMN lastSyncedAt INTEGER")
        db.execSQL("ALTER TABLE scanned_items ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE scanned_items ADD COLUMN clientUpdatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE scanned_items ADD COLUMN deletedAt INTEGER")

        // Set clientUpdatedAt to timestamp for existing items
        db.execSQL("UPDATE scanned_items SET clientUpdatedAt = timestamp WHERE clientUpdatedAt = 0")

        // Mark all existing items for sync
        db.execSQL("UPDATE scanned_items SET needsSync = 1 WHERE serverId IS NULL")

        // Create indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS index_scanned_items_serverId ON scanned_items(serverId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_scanned_items_needsSync ON scanned_items(needsSync)")

        // Same for scanned_item_history table
        // [... similar ALTERs for history table ...]
    }
}
```

***REMOVED******REMOVED*** Sync Triggers

***REMOVED******REMOVED******REMOVED*** 1. On Sign-In (First Sync)

**File:** `FirstSyncManager.kt`

```kotlin
@Singleton
class FirstSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val itemDao: ScannedItemDao,
    private val syncManager: ItemSyncManager,
) {
    suspend fun handleFirstSignIn(): Pair<Int, Boolean> {
        if (isFirstSyncCompleted()) return 0 to true

        val itemCount = itemDao.countItems()
        if (itemCount == 0) {
            markFirstSyncCompleted()
            return 0 to true
        }

        // Mark all items for sync
        itemDao.markAllForSync()

        // Trigger sync
        when (val result = syncManager.syncAll()) {
            is SyncResult.Success -> {
                markFirstSyncCompleted()
                itemCount to true
            }
            else -> itemCount to false
        }
    }
}
```

**Usage:**
```kotlin
// In ViewModel after successful sign-in
viewModelScope.launch {
    val (itemCount, success) = firstSyncManager.handleFirstSignIn()
    if (success && itemCount > 0) {
        showSnackbar("Synced $itemCount items to cloud")
    }
}
```

***REMOVED******REMOVED******REMOVED*** 2. After Item Save

```kotlin
// In ScannedItemRepository
suspend fun saveItem(item: ScannedItemEntity) {
    val updated = item.copy(
        needsSync = 1,
        clientUpdatedAt = System.currentTimeMillis()
    )
    dao.upsertAll(listOf(updated))

    // Trigger immediate background sync
    triggerImmediateSync()
}
```

***REMOVED******REMOVED******REMOVED*** 3. On App Resume

```kotlin
// In MainActivity or root ViewModel
override fun onResume() {
    super.onResume()
    val lastSync = getLastSyncTimestamp()
    val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)

    if (lastSync < fiveMinutesAgo) {
        triggerSync()
    }
}
```

***REMOVED******REMOVED******REMOVED*** 4. Periodic Background Sync

**File:** `WorkManagerModule.kt`

```kotlin
class PeriodicSyncInitializer(private val workManager: WorkManager) {
    fun initialize() {
        val syncWorkRequest = PeriodicWorkRequestBuilder<ItemSyncWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
        )
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build(),
        ).build()

        workManager.enqueueUniquePeriodicWork(
            ItemSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncWorkRequest,
        )
    }
}
```

**Call in Application.onCreate():**
```kotlin
class ScaniumApp : Application(), HiltWorkManagerConfiguration {
    @Inject lateinit var periodicSyncInitializer: PeriodicSyncInitializer

    override fun onCreate() {
        super.onCreate()
        periodicSyncInitializer.initialize()
    }
}
```

***REMOVED******REMOVED*** Sync Algorithm

***REMOVED******REMOVED******REMOVED*** ItemSyncManager.syncAll()

**File:** `ItemSyncManager.kt`

```kotlin
suspend fun syncAll(): SyncResult {
    // 1. Check authentication
    if (!authRepository.isSignedIn()) return SyncResult.NotAuthenticated

    // 2. PUSH: Send local changes to server
    val pendingItems = itemDao.getPendingSync()  // needsSync = 1
    val pushResults = pushToServer(pendingItems)

    // 3. PULL: Fetch server changes since last sync
    val lastSyncTime = getLastSyncTimestamp()
    val serverItems = pullFromServer(lastSyncTime)

    // 4. Resolve conflicts and apply server changes
    val conflicts = applyServerChanges(serverItems)

    // 5. Update last sync timestamp
    setLastSyncTimestamp(System.currentTimeMillis())

    return SyncResult.Success(
        itemsPushed = pushResults.size,
        itemsPulled = serverItems.size,
        conflicts = conflicts
    )
}
```

***REMOVED******REMOVED******REMOVED*** Push to Server

```kotlin
private suspend fun pushToServer(items: List<ScannedItemEntity>): List<String> {
    val pushed = mutableListOf<String>()

    for (item in items) {
        try {
            when {
                // CREATE: No server ID yet
                item.serverId == null && item.deletedAt == null -> {
                    val response = itemsApi.createItem(item.toCreateRequest())
                    itemDao.updateServerId(item.id, response.item.id)
                    itemDao.updateSyncState(item.id, needsSync = 0, lastSyncedAt = now())
                    pushed.add(item.id)
                }

                // UPDATE: Has server ID, not deleted
                item.serverId != null && item.deletedAt == null -> {
                    itemsApi.updateItem(item.serverId, item.toUpdateRequest())
                    itemDao.updateSyncState(item.id, needsSync = 0, lastSyncedAt = now())
                    pushed.add(item.id)
                }

                // DELETE: Has server ID, soft deleted
                item.serverId != null && item.deletedAt != null -> {
                    itemsApi.deleteItem(item.serverId)
                    itemDao.updateSyncState(item.id, needsSync = 0, lastSyncedAt = now())
                    pushed.add(item.id)
                }
            }
        } catch (e: HttpException) {
            if (e.code() == 409) {
                // Conflict - will be resolved in pull phase
                Log.w(TAG, "Conflict pushing item ${item.id}", e)
            } else {
                throw e
            }
        }
    }

    return pushed
}
```

***REMOVED******REMOVED******REMOVED*** Pull from Server

```kotlin
private suspend fun pullFromServer(since: Long?): List<ItemDto> {
    val sinceIso = since?.let { Instant.ofEpochMilli(it).toString() }
    val response = itemsApi.getItems(
        since = sinceIso,
        limit = 100,
        includeDeleted = true
    )
    return response.items
}
```

***REMOVED******REMOVED******REMOVED*** Conflict Resolution

```kotlin
private suspend fun applyServerChanges(serverItems: List<ItemDto>): Int {
    var conflicts = 0

    for (serverItem in serverItems) {
        // Find local item by serverId
        val localItem = itemDao.getByServerId(serverItem.id)

        when {
            // Server item doesn't exist locally → INSERT
            localItem == null -> {
                val entity = serverItem.toEntity(localId = UUID.randomUUID().toString())
                itemDao.upsertAll(listOf(entity))
            }

            // No conflict → UPDATE
            !hasConflict(localItem, serverItem) -> {
                val entity = serverItem.toEntity(localId = localItem.id)
                itemDao.upsertAll(listOf(entity))
            }

            // Conflict → RESOLVE
            else -> {
                conflicts++
                resolveConflict(localItem, serverItem)
            }
        }
    }

    return conflicts
}

private fun hasConflict(local: ScannedItemEntity, server: ItemDto): Boolean {
    return local.needsSync == 1 && local.syncVersion != server.syncVersion
}

private suspend fun resolveConflict(local: ScannedItemEntity, server: ItemDto) {
    val localTime = local.clientUpdatedAt
    val serverTime = server.clientUpdatedAt?.let { Instant.parse(it).toEpochMilli() } ?: 0L

    when {
        // Client wins: Keep local, will push on next sync
        localTime > serverTime -> {
            Log.i(TAG, "Conflict: Client wins (${local.id})")
            // Keep local data, mark for sync
            itemDao.updateSyncState(local.id, needsSync = 1)
        }

        // Server wins: Overwrite local with server data
        else -> {
            Log.i(TAG, "Conflict: Server wins (${local.id})")
            val entity = server.toEntity(localId = local.id)
            itemDao.upsertAll(listOf(entity))
        }
    }
}
```

***REMOVED******REMOVED*** Dependency Injection

***REMOVED******REMOVED******REMOVED*** AuthModule.kt

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    // HTTP client for business API calls (includes AuthTokenInterceptor)
    @Provides
    @Singleton
    @AuthHttpClient
    fun provideAuthHttpClient(
        authTokenInterceptor: AuthTokenInterceptor,
    ): OkHttpClient {
        return AssistantOkHttpClientFactory.create(
            config = AssistantHttpConfig.DEFAULT,
            additionalInterceptors = listOf(authTokenInterceptor),
        )
    }

    @Provides
    @Singleton
    fun provideItemsApi(
        @AuthHttpClient httpClient: OkHttpClient,
    ): ItemsApi {
        return ItemsApi(httpClient)
    }
}
```

***REMOVED******REMOVED******REMOVED*** DatabaseModule.kt

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideItemSyncManager(
        @ApplicationContext context: Context,
        dao: ScannedItemDao,
        itemsApi: ItemsApi,
        authRepository: AuthRepository,
    ): ItemSyncManager {
        return ItemSyncManager(context, dao, itemsApi, authRepository)
    }

    @Provides
    @Singleton
    fun provideScannedItemSyncer(syncManager: ItemSyncManager): ScannedItemSyncer {
        return syncManager  // ItemSyncManager implements ScannedItemSyncer
    }
}
```

***REMOVED******REMOVED******REMOVED*** WorkManagerModule.kt

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context,
    ): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun providePeriodicSyncInitializer(
        workManager: WorkManager,
    ): PeriodicSyncInitializer {
        return PeriodicSyncInitializer(workManager)
    }
}
```

***REMOVED******REMOVED*** Network Layer

***REMOVED******REMOVED******REMOVED*** ItemsApi.kt

```kotlin
class ItemsApi(private val httpClient: OkHttpClient) {
    private val baseUrl = BuildConfig.SCANIUM_API_BASE_URL
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getItems(
        since: String? = null,
        limit: Int? = null,
        includeDeleted: Boolean? = null,
    ): GetItemsResponse = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$baseUrl/v1/items?")
            since?.let { append("since=$it&") }
            limit?.let { append("limit=$it&") }
            includeDeleted?.let { append("includeDeleted=$it&") }
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = httpClient.newCall(request).await()
        if (!response.isSuccessful) {
            throw HttpException(response)
        }

        json.decodeFromString(response.body!!.string())
    }

    suspend fun createItem(request: CreateItemRequest): CreateItemResponse
    suspend fun updateItem(itemId: String, request: UpdateItemRequest): UpdateItemResponse
    suspend fun deleteItem(itemId: String): DeleteItemResponse
    suspend fun syncItems(request: SyncRequest): SyncResponse
}
```

***REMOVED******REMOVED******REMOVED*** AuthTokenInterceptor.kt

```kotlin
class AuthTokenInterceptor(
    private val tokenProvider: () -> String?,
    private val authRepository: AuthRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider() ?: return chain.proceed(chain.request())

        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        val response = chain.proceed(request)

        // Silent session renewal on 401
        if (response.code == 401) {
            runBlocking {
                authRepository.refreshSession()
            }

            // Retry with new token
            val newToken = tokenProvider()
            if (newToken != null && newToken != token) {
                val retryRequest = chain.request().newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                return chain.proceed(retryRequest)
            }
        }

        return response
    }
}
```

***REMOVED******REMOVED*** Testing

***REMOVED******REMOVED******REMOVED*** Unit Tests

**File:** `ScannedItemSyncerTest.kt`

```kotlin
@Test
fun `syncAll should push local changes and pull server changes`() = runTest {
    // Arrange
    val localItem = ScannedItemEntity(
        id = "local-1",
        title = "Local Item",
        needsSync = 1,
        clientUpdatedAt = System.currentTimeMillis()
    )
    coEvery { itemDao.getPendingSync() } returns listOf(localItem)
    coEvery { itemsApi.createItem(any()) } returns CreateItemResponse(
        item = ItemDto(id = "server-1", title = "Local Item"),
        localId = "local-1",
        correlationId = "corr-1"
    )

    // Act
    val result = syncManager.syncAll()

    // Assert
    assertTrue(result is SyncResult.Success)
    coVerify { itemDao.updateServerId("local-1", "server-1") }
    coVerify { itemDao.updateSyncState("local-1", needsSync = 0, any()) }
}

@Test
fun `handleConflict should choose latest clientUpdatedAt`() = runTest {
    val now = System.currentTimeMillis()
    val localItem = ScannedItemEntity(
        id = "local-1",
        serverId = "server-1",
        title = "Client Version",
        syncVersion = 1,
        clientUpdatedAt = now + 1000,  // Client is newer
        needsSync = 1
    )
    val serverItem = ItemDto(
        id = "server-1",
        title = "Server Version",
        syncVersion = 2,
        clientUpdatedAt = Instant.ofEpochMilli(now).toString()
    )

    // Act
    syncManager.resolveConflict(localItem, serverItem)

    // Assert: Client wins, keep local data
    coVerify(exactly = 0) { itemDao.upsertAll(any()) }
}
```

***REMOVED******REMOVED******REMOVED*** Migration Tests

**File:** `MigrationTest.kt`

```kotlin
@Test
fun testMigration9To10() {
    // Create v9 database
    helper.createDatabase(TEST_DB, 9).apply {
        execSQL("""
            INSERT INTO scanned_items (id, title, timestamp)
            VALUES ('item-1', 'Test Item', 1234567890)
        """)
        close()
    }

    // Migrate to v10
    helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)

    // Verify sync fields added
    helper.openDatabase(TEST_DB, 10).query(
        "SELECT serverId, needsSync, syncVersion, clientUpdatedAt, deletedAt FROM scanned_items WHERE id = 'item-1'"
    ).use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertNull(cursor.getString(0))  // serverId
        assertEquals(1, cursor.getInt(1))  // needsSync
        assertEquals(1, cursor.getInt(2))  // syncVersion
        assertEquals(1234567890, cursor.getLong(3))  // clientUpdatedAt set to timestamp
        assertNull(cursor.getString(4))  // deletedAt
    }
}
```

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** Items Not Syncing

**Symptoms:** Items created offline don't appear on other devices

**Debug:**
```kotlin
// Check pending sync count
val pending = itemDao.getPendingSync()
Log.d("Sync", "Pending sync: ${pending.size} items")

// Check last sync time
val lastSync = prefs.getLong("last_sync_timestamp", 0)
val minutesAgo = (System.currentTimeMillis() - lastSync) / (60 * 1000)
Log.d("Sync", "Last sync: $minutesAgo minutes ago")

// Check auth status
val isSignedIn = authRepository.isSignedIn()
Log.d("Sync", "Signed in: $isSignedIn")
```

**Common Fixes:**
1. Sign in: Sync requires authentication
2. Enable network: Check airplane mode, WiFi
3. Check battery: WorkManager skips if battery low
4. Trigger manual sync: `syncManager.syncAll()`

***REMOVED******REMOVED******REMOVED*** Conflicts on Every Sync

**Symptoms:** Same item shows conflict repeatedly

**Cause:** Local `clientUpdatedAt` not being updated

**Fix:**
```kotlin
// Always update clientUpdatedAt when modifying item
val updated = item.copy(
    title = "New Title",
    clientUpdatedAt = System.currentTimeMillis(),  // ← Don't forget!
    needsSync = 1
)
dao.upsertAll(listOf(updated))
```

***REMOVED******REMOVED******REMOVED*** Server ID Mapping Lost

**Symptoms:** Duplicate items on server after reinstall

**Cause:** Local database cleared but server still has items

**Fix:**
```kotlin
// On first sync after reinstall, map by title/timestamp
val serverItems = itemsApi.getItems()
for (serverItem in serverItems) {
    val localMatch = itemDao.findByTitleAndTimestamp(
        serverItem.title,
        serverItem.createdAt
    )
    if (localMatch != null && localMatch.serverId == null) {
        itemDao.updateServerId(localMatch.id, serverItem.id)
    }
}
```

***REMOVED******REMOVED*** Performance Optimization

***REMOVED******REMOVED******REMOVED*** Batch Operations

```kotlin
// BAD: Update items one at a time
for (item in items) {
    dao.update(item)
}

// GOOD: Batch update
dao.upsertAll(items)
```

***REMOVED******REMOVED******REMOVED*** Pagination

```kotlin
// Fetch items in pages to avoid memory issues
suspend fun syncAllPaginated() {
    var nextSince: String? = getLastSyncTimestamp()?.let {
        Instant.ofEpochMilli(it).toString()
    }

    do {
        val response = itemsApi.getItems(since = nextSince, limit = 100)
        applyServerChanges(response.items)
        nextSince = if (response.hasMore) response.nextSince else null
    } while (nextSince != null)
}
```

***REMOVED******REMOVED******REMOVED*** Index Optimization

```kotlin
@Dao
interface ScannedItemDao {
    // Fast: Uses index_scanned_items_needsSync
    @Query("SELECT * FROM scanned_items WHERE needsSync = 1")
    suspend fun getPendingSync(): List<ScannedItemEntity>

    // Fast: Uses index_scanned_items_serverId
    @Query("SELECT * FROM scanned_items WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: String): ScannedItemEntity?
}
```

***REMOVED******REMOVED*** Best Practices

1. **Always set clientUpdatedAt**
   ```kotlin
   item.copy(clientUpdatedAt = System.currentTimeMillis())
   ```

2. **Mark for sync on every write**
   ```kotlin
   item.copy(needsSync = 1)
   ```

3. **Handle network errors gracefully**
   ```kotlin
   when (syncResult) {
       is SyncResult.NetworkError -> showSnackbar("Sync failed, will retry")
       is SyncResult.Success -> showSnackbar("Synced successfully")
   }
   ```

4. **Use WorkManager for background tasks**
   - Respects battery optimization
   - Survives app kills
   - Exponential backoff on failure

5. **Test with mock server**
   ```kotlin
   class MockItemsApi : ItemsApi {
       override suspend fun createItem(request: CreateItemRequest) =
           CreateItemResponse(
               item = ItemDto(id = "mock-${UUID.randomUUID()}", ...),
               localId = request.localId,
               correlationId = "mock-corr"
           )
   }
   ```

***REMOVED******REMOVED*** Related Documentation

- [Backend Items API](../../../backend/howto/backend/items-sync.md)
- [Authentication Flow](./auth-flow.md)
- [Offline-First Architecture](./offline-first.md)
- [WorkManager Integration](./workmanager.md)
