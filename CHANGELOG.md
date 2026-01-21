***REMOVED*** Changelog

All notable changes to Scanium will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

***REMOVED******REMOVED*** [Unreleased]

***REMOVED******REMOVED*** [1.7.0 / backend-1.1.0] - 2026-01-21

***REMOVED******REMOVED******REMOVED*** Added
- **Vision API Quota System**: Implemented per-user daily quota limits (50 requests/day default) for Google Vision API to control costs
- **Quota Dialog with Donation Prompt**: When users exceed their quota, a friendly dialog encourages app support via PayPal donations (€2, €10, €20)
- **Backend Quota Service**: New `VisionQuotaService` for checking and tracking Vision API usage per user
- **Database Persistence**: Added `vision_quotas` PostgreSQL table for persistent quota tracking across app restarts
- **Rate Limit Headers**: Vision API responses now include standard rate limit headers (`X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`)
- **User Authentication**: Vision API `/v1/vision/insights` endpoint now requires user authentication via Bearer token
- **Configurable Quota**: Added `VISION_DAILY_QUOTA_LIMIT` environment variable for customizable quota limits

***REMOVED******REMOVED******REMOVED*** Changed
- Vision API cache hits now count towards quota (prevents abuse via repeated identical requests)
- Enhanced error handling for 429 responses with quota metadata extraction

***REMOVED******REMOVED******REMOVED*** Technical Details
- Android: v1.7.0 (build 100017)
- Backend: v1.1.0
- Database migration: `20260121105713_add_vision_quota`
- Automatic daily quota reset at midnight UTC

***REMOVED******REMOVED*** [1.6.0] - 2026-01-20

***REMOVED******REMOVED******REMOVED*** Added
- Integrated Camera UI into Guided Tour
- Streamlined First-Time User Experience (FTUE)

***REMOVED******REMOVED******REMOVED*** Fixed
- Corrected tooltip positioning in Settings and Edit Item FTUE overlays

***REMOVED******REMOVED*** [1.3.1] - 2025-01-XX

***REMOVED******REMOVED******REMOVED*** Added
- Initial version tracking
- Basic version.properties for AAB build automation

---

**Version Format**: `[Android vX.Y.Z / backend-vX.Y.Z]` - YYYY-MM-DD
