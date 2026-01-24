# Changelog

All notable changes to Scanium will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.8.0 / backend-1.2.0] - 2026-01-24

### Added
- **Smart Merge Suggestions**: Automatic detection of duplicate items with merge review UI
- **Category Validation**: Auto-corrects classification mismatches (e.g., electronics incorrectly classified as fashion)
- **Enhanced Scan Zone**: Increased ROI from 65% to 80% for better object detection coverage
- **Localized Smart Merge Labels**: Proper internationalization for merge suggestions

### Fixed
- **Test Infrastructure**: Resolved 51 test failures by fixing mock configurations and aggregation settings
- **Domain Pack Classification**: Proper wiring of domain pack categories and vision enrichment
- **Duplicate Detection**: Prevented race condition causing duplicates from single capture frame
- **Category/Attribute Consistency**: Added validation to detect and log backend classification errors

### Changed
- Disabled item aggregation - each capture now creates a unique item (WYSIWYG behavior)
- Disabled long-press scanning on camera shutter for cleaner UX

### Technical Details
- Android: v1.8.0 (build 100023)
- Backend: v1.2.0
- Test pass rate improved from 95.1% to 98.9%
- Added defensive validation layer for classification responses

## [1.7.0 / backend-1.1.0] - 2026-01-21

### Added
- **Vision API Quota System**: Implemented per-user daily quota limits (50 requests/day default) for Google Vision API to control costs
- **Quota Dialog with Donation Prompt**: When users exceed their quota, a friendly dialog encourages app support via PayPal donations (€2, €10, €20)
- **Backend Quota Service**: New `VisionQuotaService` for checking and tracking Vision API usage per user
- **Database Persistence**: Added `vision_quotas` PostgreSQL table for persistent quota tracking across app restarts
- **Rate Limit Headers**: Vision API responses now include standard rate limit headers (`X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`)
- **User Authentication**: Vision API `/v1/vision/insights` endpoint now requires user authentication via Bearer token
- **Configurable Quota**: Added `VISION_DAILY_QUOTA_LIMIT` environment variable for customizable quota limits

### Changed
- Vision API cache hits now count towards quota (prevents abuse via repeated identical requests)
- Enhanced error handling for 429 responses with quota metadata extraction

### Technical Details
- Android: v1.7.0 (build 100017)
- Backend: v1.1.0
- Database migration: `20260121105713_add_vision_quota`
- Automatic daily quota reset at midnight UTC

## [1.6.0] - 2026-01-20

### Added
- Integrated Camera UI into Guided Tour
- Streamlined First-Time User Experience (FTUE)

### Fixed
- Corrected tooltip positioning in Settings and Edit Item FTUE overlays

## [1.3.1] - 2025-01-XX

### Added
- Initial version tracking
- Basic version.properties for AAB build automation

---

**Version Format**: `[Android vX.Y.Z / backend-vX.Y.Z]` - YYYY-MM-DD
