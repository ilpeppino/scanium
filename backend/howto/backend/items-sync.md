***REMOVED*** Items API - Multi-Device Sync (Phase E)

***REMOVED******REMOVED*** Overview

The Items API provides user-scoped storage and multi-device synchronization for scanned items. Items are owned by users and synchronized across devices using last-write-wins conflict resolution.

**Key Features:**
- User-scoped ownership and access control
- Offline-first with bidirectional sync
- Optimistic locking with `syncVersion`
- Soft delete with tombstones
- Last-write-wins conflict resolution
- Incremental sync with `since` parameter
- Batch sync operations

***REMOVED******REMOVED*** Data Model

***REMOVED******REMOVED******REMOVED*** Item Schema (`prisma/schema.prisma`)

```prisma
model Item {
  id        String   @id @default(uuid())
  userId    String
  createdAt DateTime @default(now())
  updatedAt DateTime @updatedAt
  deletedAt DateTime? // Soft delete tombstones

  // Core metadata
  title                String?
  description          String?
  category             String?
  confidence           Float?
  priceEstimateLow     Float?
  priceEstimateHigh    Float?
  userPriceCents       BigInt?
  condition            String?

  // Attributes (JSON for flexibility)
  attributesJson         Json?
  detectedAttributesJson Json?
  visionAttributesJson   Json?
  enrichmentStatusJson   Json?

  // Quality metrics
  completenessScore      Int      @default(0)
  missingAttributesJson  Json?
  capturedShotTypesJson  Json?
  isReadyForListing      Boolean  @default(false)
  lastEnrichedAt         DateTime?

  // Export Assistant
  exportTitle            String?
  exportDescription      String?
  exportBulletsJson      Json?
  exportGeneratedAt      DateTime?
  exportFromCache        Boolean  @default(false)
  exportModel            String?
  exportConfidenceTier   String?

  // Classification
  classificationStatus       String @default("PENDING")
  domainCategoryId           String?
  classificationErrorMessage String?
  classificationRequestId    String?

  // Photo metadata (not actual images)
  photosMetadataJson     Json?

  // Multi-object scanning
  attributesSummaryText  String?
  summaryTextUserEdited  Boolean @default(false)
  sourcePhotoId          String?

  // Listing associations
  listingStatus String  @default("NOT_LISTED")
  listingId     String?
  listingUrl    String?

  // OCR/barcode
  recognizedText String?
  barcodeValue   String?
  labelText      String?

  // Sync metadata
  syncVersion     Int       @default(1)
  clientUpdatedAt DateTime?

  user User @relation(fields: [userId], references: [id], onDelete: Cascade)

  @@index([userId])
  @@index([userId, updatedAt])
  @@index([userId, deletedAt])
  @@map("items")
}
```

***REMOVED******REMOVED******REMOVED*** Photo Metadata Structure

Photos are not stored on the server (Phase E). Only metadata is synchronized:

```typescript
{
  photos: [
    {
      id: string;           // Local photo UUID
      type: 'primary' | 'additional' | 'closeup';
      capturedAt: string;   // ISO 8601 timestamp
      hash: string;         // SHA-256 hash of image data
      width?: number;
      height?: number;
      mimeType?: string;    // 'image/jpeg', 'image/png'
    }
  ]
}
```

**Benefits:**
- Future deduplication (detect identical photos across items)
- Integrity checking when photos uploaded to S3 later (Phase F)
- Minimal overhead (hash computed once at capture time)

***REMOVED******REMOVED*** API Endpoints

All endpoints require authentication via `Authorization: Bearer <token>` header.

***REMOVED******REMOVED******REMOVED*** GET /v1/items

List user's items with optional filtering and pagination.

**Query Parameters:**
- `since` (optional): ISO 8601 timestamp - only return items updated after this time
- `limit` (optional): Max items to return (default 100, max 500)
- `includeDeleted` (optional): Include soft-deleted items (default true)

**Response:**
```typescript
{
  items: Array<Item>;
  hasMore: boolean;
  nextSince: string | null;
  correlationId: string;
}
```

**Example:**
```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://api.scanium.dev/v1/items?since=2025-01-01T00:00:00Z&limit=50"
```

**Rate Limit:** 100 req/min per user

***REMOVED******REMOVED******REMOVED*** GET /v1/items/:id

Fetch single item by ID. Enforces user ownership.

**Response:**
```typescript
{
  item: Item;
  correlationId: string;
}
```

**Errors:**
- `403 Forbidden` - Item belongs to another user
- `404 Not Found` - Item doesn't exist

**Rate Limit:** 100 req/min per user

***REMOVED******REMOVED******REMOVED*** POST /v1/items

Create new item for authenticated user.

**Request:**
```typescript
{
  localId: string;  // Client UUID for correlation
  title?: string;
  description?: string;
  category?: string;
  // ... all item fields
  clientUpdatedAt: string; // ISO 8601
}
```

**Response:**
```typescript
{
  item: Item;           // Created item with server-generated id
  localId: string;      // Echo for client mapping
  correlationId: string;
}
```

**Example:**
```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "localId": "local-uuid-123",
    "title": "Vintage Camera",
    "category": "Electronics",
    "clientUpdatedAt": "2025-01-13T10:00:00Z"
  }' \
  https://api.scanium.dev/v1/items
```

**Rate Limit:** 30 req/min per user

***REMOVED******REMOVED******REMOVED*** PATCH /v1/items/:id

Update item with optimistic locking.

**Request:**
```typescript
{
  title?: string;
  description?: string;
  // ... fields to update
  syncVersion: number;      // For optimistic locking
  clientUpdatedAt: string;  // ISO 8601
}
```

**Response:**
```typescript
{
  item: Item;           // Updated item with incremented syncVersion
  correlationId: string;
}
```

**Errors:**
- `409 Conflict` - syncVersion mismatch (conflict detected)
- `403 Forbidden` - Item belongs to another user
- `404 Not Found` - Item doesn't exist

**Example:**
```bash
curl -X PATCH -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Updated Title",
    "syncVersion": 1,
    "clientUpdatedAt": "2025-01-13T10:05:00Z"
  }' \
  https://api.scanium.dev/v1/items/item-uuid-456
```

**Rate Limit:** 30 req/min per user

***REMOVED******REMOVED******REMOVED*** DELETE /v1/items/:id

Soft delete item (sets `deletedAt`, increments `syncVersion`).

**Response:**
```typescript
{
  item: Item;           // Item with deletedAt timestamp
  correlationId: string;
}
```

**Note:** Item remains in database as tombstone for sync purposes.

**Rate Limit:** 30 req/min per user

***REMOVED******REMOVED******REMOVED*** POST /v1/items/sync

Batch synchronization - push local changes and receive server changes.

**Request:**
```typescript
{
  clientTimestamp: string;         // ISO 8601
  lastSyncTimestamp: string | null; // Last successful sync
  changes: Array<{
    action: 'CREATE' | 'UPDATE' | 'DELETE';
    localId: string;
    serverId?: string;
    syncVersion?: number;
    clientUpdatedAt: string;
    data?: Partial<Item>;
  }>;
}
```

**Response:**
```typescript
{
  results: Array<{
    localId: string;
    serverId?: string;
    status: 'SUCCESS' | 'CONFLICT' | 'ERROR';
    error?: string;
    conflictResolution?: 'SERVER_WINS' | 'CLIENT_WINS';
    item?: Item;
  }>;
  serverChanges: Array<Item>;  // Items updated on server since lastSyncTimestamp
  syncTimestamp: string;        // Use this as lastSyncTimestamp for next sync
  correlationId: string;
}
```

**Example:**
```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "clientTimestamp": "2025-01-13T10:00:00Z",
    "lastSyncTimestamp": "2025-01-12T15:30:00Z",
    "changes": [
      {
        "action": "CREATE",
        "localId": "local-123",
        "clientUpdatedAt": "2025-01-13T09:55:00Z",
        "data": {
          "title": "New Item",
          "category": "Books"
        }
      },
      {
        "action": "UPDATE",
        "localId": "local-456",
        "serverId": "server-uuid-789",
        "syncVersion": 2,
        "clientUpdatedAt": "2025-01-13T09:58:00Z",
        "data": {
          "title": "Updated Title"
        }
      }
    ]
  }' \
  https://api.scanium.dev/v1/items/sync
```

**Rate Limit:** 10 req/min per user

***REMOVED******REMOVED*** Conflict Resolution

**Strategy: Last-write-wins using `clientUpdatedAt` timestamps**

When a conflict occurs (syncVersion mismatch), the service compares `clientUpdatedAt` values:

1. **Client wins** if `client.clientUpdatedAt > server.clientUpdatedAt`
   - Server overwrites with client data
   - Increments `syncVersion`
   - Returns `conflictResolution: 'CLIENT_WINS'`

2. **Server wins** if `server.clientUpdatedAt >= client.clientUpdatedAt`
   - Server keeps its data
   - Returns current server state
   - Returns `conflictResolution: 'SERVER_WINS'`

3. **Deterministic tie-breaker**: If timestamps are equal, server wins

**Example Conflict Flow:**

```
Client: Update item X (syncVersion=1) → PATCH /v1/items/X
Server: syncVersion is now 2 (someone else updated it) → 409 Conflict

Client: Retry via sync endpoint with clientUpdatedAt
Server: Compares timestamps, resolves conflict, returns winning version
```

***REMOVED******REMOVED*** Ownership Enforcement

**All endpoints enforce user ownership:**

```typescript
// Automatic in service layer
const item = await prisma.item.findFirst({
  where: { id: itemId, userId: userId } // ← userId from JWT
});

if (!item) {
  throw new ForbiddenError('Item not found or access denied');
}
```

**Security guarantees:**
- Users can only access their own items
- User ID comes from verified JWT token
- Database queries always filter by `userId`
- No endpoint allows cross-user access

***REMOVED******REMOVED*** Testing

***REMOVED******REMOVED******REMOVED*** Run Tests

```bash
***REMOVED*** Run all tests
npm test

***REMOVED*** Run items API tests only
npm test -- items/routes.test.ts

***REMOVED*** Watch mode
npm test -- --watch
```

***REMOVED******REMOVED******REMOVED*** Test Coverage

**Test file:** `src/modules/items/routes.test.ts`

**Scenarios covered:**
- ✓ Create item for authenticated user
- ✓ Reject unauthenticated request
- ✓ Validate required fields
- ✓ Return only user's items (ownership)
- ✓ Incremental sync with `since` parameter
- ✓ Pagination with `limit`
- ✓ Include deleted items with flag
- ✓ Fetch single item
- ✓ Enforce ownership on read
- ✓ Return 404 for non-existent item
- ✓ Update with matching syncVersion
- ✓ Return 409 on syncVersion mismatch
- ✓ Enforce ownership on update
- ✓ Soft delete item
- ✓ Enforce ownership on delete
- ✓ Batch sync with no conflicts
- ✓ Handle UPDATE action
- ✓ Handle DELETE action
- ✓ Handle conflicts with resolution
- ✓ Return server changes for incremental sync

***REMOVED******REMOVED*** Migration

***REMOVED******REMOVED******REMOVED*** Adding Item Model

**Migration:** `prisma/migrations/20260113161300_add_item_sync/migration.sql`

```sql
CREATE TABLE "items" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    -- [40+ fields]
    "syncVersion" INTEGER NOT NULL DEFAULT 1,
    "clientUpdatedAt" TIMESTAMP(3),
    CONSTRAINT "items_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "items_userId_idx" ON "items"("userId");
CREATE INDEX "items_userId_updatedAt_idx" ON "items"("userId", "updatedAt");
CREATE INDEX "items_userId_deletedAt_idx" ON "items"("userId", "deletedAt");

ALTER TABLE "items" ADD CONSTRAINT "items_userId_fkey"
  FOREIGN KEY ("userId") REFERENCES "users"("id")
  ON DELETE CASCADE ON UPDATE CASCADE;
```

**Run migration:**
```bash
npx prisma migrate deploy  ***REMOVED*** Production
npx prisma migrate dev     ***REMOVED*** Development
```

***REMOVED******REMOVED*** Observability

***REMOVED******REMOVED******REMOVED*** Metrics

All endpoints emit metrics:
- `items_requests_total{method, endpoint, status}`
- `items_request_duration_seconds{method, endpoint}`
- `items_sync_conflicts_total{resolution}`

***REMOVED******REMOVED******REMOVED*** Logging

Structured logs include:
- Correlation ID (traces request across services)
- User ID (for debugging user-specific issues)
- Item ID (for debugging specific items)
- Sync conflicts with resolution details

**Example log:**
```json
{
  "level": "info",
  "msg": "Item sync completed",
  "correlationId": "req_abc123",
  "userId": "user_xyz",
  "itemsPushed": 5,
  "itemsPulled": 3,
  "conflicts": 1,
  "duration": 234
}
```

***REMOVED******REMOVED*** Rate Limiting

**Per-user rate limits:**
- GET endpoints: 100 req/min
- POST/PATCH/DELETE: 30 req/min
- Sync endpoint: 10 req/min

**Implementation:**
- Uses `@fastify/rate-limit` plugin
- Keyed by user ID from JWT
- Returns `429 Too Many Requests` when exceeded
- Includes `Retry-After` header

***REMOVED******REMOVED*** Best Practices

***REMOVED******REMOVED******REMOVED*** Client Implementation

1. **Store server ID mapping**
   ```typescript
   // After CREATE
   localStorage.setItem(`item:${localId}:serverId`, response.item.id);
   ```

2. **Always send clientUpdatedAt**
   ```typescript
   const item = {
     ...data,
     clientUpdatedAt: new Date().toISOString()
   };
   ```

3. **Handle conflicts gracefully**
   ```typescript
   if (result.status === 'CONFLICT') {
     if (result.conflictResolution === 'SERVER_WINS') {
       // Merge server state into local DB
       await db.upsert(result.item);
     }
   }
   ```

4. **Use batch sync for efficiency**
   ```typescript
   // Instead of 10 PATCH requests
   await syncAll(); // Single POST /v1/items/sync
   ```

5. **Implement exponential backoff**
   ```typescript
   let retries = 0;
   while (retries < 3) {
     try {
       await syncAll();
       break;
     } catch (err) {
       if (err.statusCode === 429) {
         await sleep(Math.pow(2, retries) * 1000);
         retries++;
       } else {
         throw err;
       }
     }
   }
   ```

***REMOVED******REMOVED******REMOVED*** Server Deployment

1. **Run migrations before deployment**
   ```bash
   npx prisma migrate deploy
   ```

2. **Monitor sync conflicts**
   ```bash
   ***REMOVED*** Alert if conflicts spike
   rate(items_sync_conflicts_total[5m]) > 10
   ```

3. **Set up database indexes**
   - `(userId)` - Fast user filtering
   - `(userId, updatedAt)` - Incremental sync
   - `(userId, deletedAt)` - Tombstone queries

4. **Configure connection pooling**
   ```
   DATABASE_URL="postgresql://...?connection_limit=20&pool_timeout=10"
   ```

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** 409 Conflicts on Every Update

**Cause:** Client not incrementing syncVersion or not fetching latest version

**Fix:**
```typescript
// Always fetch latest before update
const latest = await GET('/v1/items/:id');
const updated = await PATCH('/v1/items/:id', {
  ...changes,
  syncVersion: latest.syncVersion  // ← Use server's version
});
```

***REMOVED******REMOVED******REMOVED*** Items Missing After Sync

**Cause:** Client using wrong `since` timestamp

**Fix:**
```typescript
// Save syncTimestamp from response
const response = await POST('/v1/items/sync', {
  lastSyncTimestamp: getLastSync(), // ← Use previous response's syncTimestamp
  ...
});
setLastSync(response.syncTimestamp); // ← Save for next sync
```

***REMOVED******REMOVED******REMOVED*** Rate Limit Errors

**Cause:** Too many individual requests instead of batch sync

**Fix:**
```typescript
// BAD: Multiple individual requests
for (const item of pendingItems) {
  await PATCH(`/v1/items/${item.id}`, item);
}

// GOOD: Single batch sync
await POST('/v1/items/sync', {
  changes: pendingItems.map(item => ({
    action: 'UPDATE',
    localId: item.localId,
    serverId: item.serverId,
    syncVersion: item.syncVersion,
    clientUpdatedAt: item.clientUpdatedAt,
    data: item
  }))
});
```

***REMOVED******REMOVED*** Future Enhancements

***REMOVED******REMOVED******REMOVED*** Phase F: Photo Upload to S3

- Store photos in S3 with signed URLs
- Sync photo metadata + URLs
- Client downloads photos on demand
- Use SHA-256 hashes for deduplication

***REMOVED******REMOVED******REMOVED*** Phase G: Real-time Sync

- WebSocket connection for instant updates
- Push notifications for cross-device changes
- Presence detection (show which devices are online)

***REMOVED******REMOVED******REMOVED*** Phase H: Conflict UI

- Show conflict resolution UI to user
- Allow manual merge of conflicting changes
- Provide diff view of local vs server state

***REMOVED******REMOVED*** Related Documentation

- [Android Sync Implementation](../../../androidApp/howto/app/sync.md)
- [Authentication Flow](./auth-flow.md)
- [API Security](./api-security.md)
- [Database Schema](./database-schema.md)
