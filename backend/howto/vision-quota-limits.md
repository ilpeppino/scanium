***REMOVED*** Vision API Quota Limits

***REMOVED******REMOVED*** Overview

The Vision API quota system enforces per-user daily limits on Google Vision API requests to control costs and prevent abuse. Each authenticated user is allowed a maximum of 50 Vision API requests per day (configurable).

***REMOVED******REMOVED*** Features

- **Per-User Tracking**: Quota is tracked per authenticated user (via Bearer token)
- **Daily Reset**: Quotas automatically reset at midnight UTC
- **Persistent Storage**: Usage data stored in PostgreSQL via Prisma
- **Rate Limit Headers**: Responses include standard rate limit headers
- **Graceful Errors**: Clear error messages when quota exceeded

***REMOVED******REMOVED*** Configuration

Set the daily quota limit via environment variable:

```bash
VISION_DAILY_QUOTA_LIMIT=50  ***REMOVED*** Default: 50 requests per user per day
```

Or in `.env` file:

```env
VISION_DAILY_QUOTA_LIMIT=50
```

The limit can be set between 1 and 1000 requests per day.

***REMOVED******REMOVED*** Protected Endpoints

The following endpoints enforce quota limits:

***REMOVED******REMOVED******REMOVED*** `/v1/vision/insights`

- **Authentication Required**: Yes (Bearer token)
- **Quota Cost**: 1 request per call (including cache hits)
- **Response Headers**:
  - `X-RateLimit-Limit`: Maximum requests per day
  - `X-RateLimit-Remaining`: Remaining requests for today
  - `X-RateLimit-Reset`: Unix timestamp when quota resets

***REMOVED******REMOVED*** Error Responses

When quota is exceeded, the API returns:

```http
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 50
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1737504000
Retry-After: 43200

{
  "success": false,
  "error": {
    "code": "QUOTA_EXCEEDED",
    "message": "Daily quota limit of 50 requests exceeded. Resets at 2026-01-22T00:00:00.000Z",
    "correlationId": "..."
  }
}
```

***REMOVED******REMOVED*** Implementation Details

***REMOVED******REMOVED******REMOVED*** Database Schema

The `vision_quotas` table tracks daily usage:

```sql
CREATE TABLE "vision_quotas" (
    "id" TEXT PRIMARY KEY,
    "userId" TEXT NOT NULL,
    "date" DATE NOT NULL,
    "count" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "vision_quotas_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE,
    UNIQUE ("userId", "date")
);
```

***REMOVED******REMOVED******REMOVED*** Service API

The `VisionQuotaService` provides the following methods:

```typescript
// Check if user has available quota
const result = await quotaService.checkQuota(userId);
// Returns: { allowed: boolean, currentCount: number, limit: number, resetAt: Date }

// Increment quota after successful request
await quotaService.incrementQuota(userId);

// Get current usage (for informational purposes)
const usage = await quotaService.getQuotaUsage(userId);
// Returns: { used: number, limit: number, resetAt: Date }

// Cleanup old records (maintenance task)
const deletedCount = await quotaService.cleanupOldRecords(7); // Keep last 7 days
```

***REMOVED******REMOVED*** Request Flow

1. **Authentication**: User provides Bearer token
2. **Quota Check**: System checks if user has available quota
3. **Request Processing**: If quota available, process the Vision API request
4. **Quota Increment**: After successful processing, increment user's quota count
5. **Response**: Return result with quota headers

***REMOVED******REMOVED*** Cache Behavior

- **Cache Hits**: Quota is still incremented for cached responses
- **Rationale**: The user made a request, even if served from cache
- **Benefit**: Prevents abuse via repeated identical requests

***REMOVED******REMOVED*** Quota Reset

- **Reset Time**: Midnight UTC (00:00:00 UTC)
- **Automatic**: No manual intervention required
- **Timezone**: All dates stored in UTC

***REMOVED******REMOVED*** Monitoring

Track quota usage via logs:

```json
{
  "level": "info",
  "correlationId": "...",
  "userId": "...",
  "quotaUsed": 45,
  "quotaLimit": 50,
  "msg": "Vision insights extracted"
}
```

***REMOVED******REMOVED*** Maintenance

Periodically cleanup old quota records to prevent table bloat:

```typescript
// Keep last 7 days of quota records
await quotaService.cleanupOldRecords(7);
```

Consider running this as a scheduled job (e.g., daily cron).

***REMOVED******REMOVED*** Testing

Run quota service tests:

```bash
cd backend
npm test -- quota-service.test.ts
```

***REMOVED******REMOVED*** Migration

Apply the database migration:

```bash
cd backend
npm run prisma:migrate dev
```

Or in production:

```bash
npm run prisma:migrate deploy
```

***REMOVED******REMOVED*** Future Enhancements

Potential improvements:

- **Tiered Quotas**: Different limits for different user tiers
- **Overage Allowance**: Allow temporary quota overages
- **Usage Analytics**: Dashboard for quota usage patterns
- **Per-Feature Quotas**: Separate quotas for OCR, labels, logos, etc.
- **Rate Limiting**: Add time-based rate limiting (requests per minute)
