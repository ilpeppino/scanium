***REMOVED*** Release v1.6.0 (Build 100016)

**Release Date:** January 20, 2026
**Commit:** 27f919f6
**Previous Release:** v1.3.1

***REMOVED******REMOVED*** Summary

This release introduces automated build tooling, improved developer workflows, and enhanced
build verification. It includes critical bug fixes for export assistant routing and FTUE overlays.

***REMOVED******REMOVED*** Key Changes

***REMOVED******REMOVED******REMOVED*** Build & Release Automation

- Automated AAB build script with version management (`build_release_aab.sh`)
- Production flavor install script with SHA verification (`install_prod_debug.sh`)
- Version tracking via `version.properties` file
- Force rebuild in all install scripts to prevent SHA mismatch
- Deterministic build+install+verify system enhancements

***REMOVED******REMOVED******REMOVED*** Export & AI Assistant

- Fixed export assistant routing to correct Settings page based on error type
- Added Settings deep link when AI is disabled
- Added Settings deep link to preflight unauthorized warning
- Improved unauthorized banner with actionable navigation

***REMOVED******REMOVED******REMOVED*** Developer Experience

- Added jvmTest dependencies to test-utils module
- KtLint auto-fixes and manual violation resolution
- Import migration to KMP modules
- Comprehensive FTUE flows reference documentation

***REMOVED******REMOVED******REMOVED*** Bug Fixes

- Fixed FTUE overlays on Items List after tour completion
- Prevented Camera UI FTUE from overlaying welcome screen
- Removed estimated price from items list display
- Corrected Alloy spanmetrics connector output configuration
- Resolved ktlint and iOS test compatibility issues

***REMOVED******REMOVED******REMOVED*** Code Quality

- Removed all debug elements and force flags from FTUE
- Removed debug instrumentation for cleaner production builds

***REMOVED******REMOVED*** Known Limitations

- Build verification examples in documentation reference old version numbers

***REMOVED******REMOVED*** Rollback Instructions

If issues are discovered after deployment:

```bash
***REMOVED*** Halt rollout in Play Console immediately

***REMOVED*** To rollback to previous version:
git checkout v1.3.1
./gradlew bundleRelease
***REMOVED*** Upload previous AAB to Play Console and promote

***REMOVED*** For hotfix (do NOT commit directly to main):
git checkout -b hotfix/v1.6.1 v1.6.0
***REMOVED*** Make fix
***REMOVED*** Create PR for review
```

***REMOVED******REMOVED*** Build Reproduction

```bash
***REMOVED*** Option 1: Using automated build script (recommended)
./scripts/dev/build_release_aab.sh prod --version-name 1.6.0 --skip-increment

***REMOVED*** Option 2: Manual build with Gradle
***REMOVED*** Ensure local.properties has:
***REMOVED*** scanium.version.code=100016
***REMOVED*** scanium.version.name=1.6.0

git checkout v1.6.0
./gradlew clean test bundleRelease

***REMOVED*** Output: androidApp/build/outputs/bundle/prodRelease/androidApp-prod-release.aab
```
