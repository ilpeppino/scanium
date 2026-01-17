# Release v1.3.1 (Build 13)

**Release Date:** January 17, 2026
**Commit:** 3c80d81
**Previous Release:** v1.3.0

## Summary

This release stabilizes the localization infrastructure and fixes camera issues introduced in v1.3.0. It marks the last-known-good state of main before freezing for production release.

## Key Changes

### Localization & Internationalization
- Structured pricing fields for proper localization (no hardcoded currency/format strings)
- Placeholder locale resources generated for all supported languages
- Storage & Export settings fully localized
- Customer-safe copy formatter for display text

### Camera & Vision
- WYSIWYG structural fix for camera overlay alignment
- Portrait mode bbox normalization corrected
- Preview and ImageAnalysis aspect ratios aligned
- YUV to Bitmap conversion stride handling fixed (regression from v1.1.0)

### AI Assistant
- First-click trigger reliability improved
- Unified language setting propagation
- Customer-safe response shaping
- Assist mode for structured listing sections

### Authentication
- Reactive auth state management
- UNAUTHORIZED error dialog
- Settings deep link for AI gate

### Stability
- Release blocker camera fix merged
- Debug instrumentation disabled for release builds
- Camera filename collision bug guarded
- Golden dataset regression tests added

## Known Limitations
- 182 pre-existing lint warnings (no baseline configured)
- Legacy pricing test format deprecated (now uses structured PricingDisplay)

## Rollback Instructions

If issues are discovered after deployment:

```bash
# Halt rollout in Play Console immediately

# To rollback to previous version:
git checkout v1.3.0
./gradlew bundleRelease
# Upload previous AAB to Play Console and promote

# For hotfix (do NOT commit directly to main):
git checkout -b hotfix/v1.3.2 v1.3.1
# Make fix
# Create PR for review
```

## Build Reproduction

```bash
# Ensure local.properties has:
# scanium.version.code=13
# scanium.version.name=1.3.1

git checkout v1.3.1
./gradlew clean test bundleRelease

# Output: androidApp/build/outputs/bundle/prodRelease/androidApp-prod-release.aab
```
