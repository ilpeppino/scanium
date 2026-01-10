> Archived on 2025-12-20: superseded by docs/INDEX.md.
***REMOVED*** Version Compatibility Matrix

***REMOVED******REMOVED*** Current Versions (2025-12-20)

This document tracks tested version combinations for critical build dependencies to prevent KMP/AGP compatibility warnings and build issues.

***REMOVED******REMOVED******REMOVED*** Build Tools & Plugins

| Tool | Version | Status | Notes |
|------|---------|--------|-------|
| AGP (Android Gradle Plugin) | 8.5.0 | ✅ Stable | Compatible with Kotlin 2.0.0 |
| Kotlin | 2.0.0 | ✅ Stable | All modules must use same version |
| Gradle | 8.7 | ✅ Stable | Auto-detected via wrapper |
| Kover | 0.7.4 | ✅ Stable | Coverage plugin |
| KSP | 2.0.0-1.0.24 | ✅ Stable | Must match Kotlin version |

***REMOVED******REMOVED******REMOVED*** Known Compatibility Issues

***REMOVED******REMOVED******REMOVED******REMOVED*** KMP + AGP 8.5.0 Warnings (RESOLVED)

**Issue**: Kotlin Multiplatform modules emitted compatibility warnings with AGP 8.5.0 when using mismatched Kotlin versions across modules.

**Root Cause**: `shared/core-tracking/build.gradle.kts` declared `kotlin("plugin.serialization") version "1.9.20"` while root project used Kotlin 2.0.0.

**Resolution**:
- Updated all Kotlin plugin declarations to use version 2.0.0
- Ensured consistency across all build.gradle.kts files

**Prevention**: CI coverage workflow now runs on all PRs to catch build warnings early.

***REMOVED******REMOVED******REMOVED*** Version Update Strategy

When updating major versions:

1. **Check official compatibility**:
   - [Kotlin/AGP compatibility](https://kotlinlang.org/docs/gradle-configure-project.html***REMOVED***apply-the-plugin)
   - [KMP release notes](https://kotlinlang.org/docs/multiplatform-compatibility-guide.html)
   - [AGP release notes](https://developer.android.com/build/releases/gradle-plugin)

2. **Pin tested combinations in CI**:
   - Update this document with tested version pairs
   - Add version assertions to CI workflows if warnings appear

3. **Test locally first**:
   ```bash
   ./gradlew clean build --warning-mode all
   ./gradlew koverVerify
   ```

4. **Monitor CI**:
   - All workflows (debug APK, coverage, security scan) must pass
   - Review build logs for deprecation warnings

***REMOVED******REMOVED******REMOVED*** Dependency Version Sources

| Dependency | Declaration Location |
|-----------|---------------------|
| AGP, Kotlin (root) | `build.gradle.kts:3-6` |
| Kotlin Serialization (androidApp) | `androidApp/build.gradle.kts:8` |
| Kotlin Serialization (core-tracking) | `shared/core-tracking/build.gradle.kts:3` |
| KSP | `androidApp/build.gradle.kts:7` |
| Kover | `build.gradle.kts:7` |

***REMOVED******REMOVED******REMOVED*** Version Lifecycle

- **Stable**: Tested and used in production
- **Beta**: Under evaluation, not for production
- **Deprecated**: Plan migration, do not use in new code

***REMOVED******REMOVED*** CI Enforcement

The following CI workflows validate version compatibility:

1. **Android Debug APK** (`.github/workflows/android-debug-apk.yml`)
   - Validates clean build with current versions
   - Catches AGP/Kotlin incompatibilities

2. **Code Coverage** (`.github/workflows/coverage.yml`)
   - Runs tests and koverVerify
   - Ensures no warnings break coverage collection

3. **Security CVE Scan** (`.github/workflows/security-cve-scan.yml`)
   - Dependency-check continues on error but logs warnings

***REMOVED******REMOVED*** Troubleshooting

***REMOVED******REMOVED******REMOVED*** "Kotlin version mismatch" warnings

**Symptom**: Build succeeds but logs show version conflicts between modules.

**Fix**:
1. Search all `build.gradle.kts` files for hardcoded Kotlin versions
2. Update to match root project version
3. Use `./gradlew --warning-mode all` to verify

***REMOVED******REMOVED******REMOVED*** AGP upgrade breaks KMP modules

**Symptom**: KMP modules fail to compile after AGP update.

**Fix**:
1. Check [Kotlin/AGP compatibility matrix](https://kotlinlang.org/docs/gradle-configure-project.html)
2. Update Kotlin version to compatible release
3. Test shared modules independently: `./gradlew :shared:core-models:build`

***REMOVED******REMOVED*** References

- [Kotlin Multiplatform Documentation](https://kotlinlang.org/docs/multiplatform.html)
- [Android Gradle Plugin Release Notes](https://developer.android.com/build/releases/gradle-plugin)
- [KSP Release Compatibility](https://github.com/google/ksp/releases)
- Review Report: `docs/REVIEW_REPORT.md` (Section G: CI/CD & Developer Experience)
