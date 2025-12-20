# Git Hooks for Scanium

This directory contains git hooks to help maintain code quality during development.

## Pre-Push Hook

The pre-push hook runs lightweight JVM-only validation before pushing to remote. This provides fast feedback without requiring a full Android SDK setup, making it ideal for:

- Container/Docker development environments
- CI pipelines
- Quick local validation before pushing

### What It Checks

1. **JVM Tests** - Runs all tests in shared modules using the JVM target
   - `shared:core-models:jvmTest`
   - `shared:core-tracking:jvmTest`
   - `shared:test-utils:jvmTest`

2. **Portability Checks** - Ensures core modules don't import Android platform types

3. **Legacy Import Checks** - Prevents usage of legacy `com.scanium.app.*` imports

### Installation

Run the installation script once after cloning:

```bash
./scripts/dev/install-hooks.sh
```

### Manual Testing

You can run the pre-push checks manually without pushing:

```bash
./gradlew prePushJvmCheck
```

### Bypassing the Hook

If you need to push without running checks (not recommended):

```bash
git push --no-verify
```

## Architecture Note

The shared KMP modules now include a JVM target in addition to Android and iOS targets. This allows:

- Running tests without Android SDK
- Faster compilation for shared business logic
- Better IDE support in non-Android environments

The JVM target only applies to shared modules (`shared:*`), not Android-specific modules.
