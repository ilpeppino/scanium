# Git Hooks for Scanium

This directory contains git hooks to help maintain code quality during development.

## Installation

Run the installation script once after cloning the repository:

```bash
./scripts/dev/install-hooks.sh
```

This installs both the pre-commit (ktlint) and pre-push (JVM checks) hooks.

---

## Pre-Commit Hook (DX-002)

The pre-commit hook runs ktlint on staged Kotlin files to enforce consistent code style before
commits.

### What It Checks

- **Code Style** - Validates all staged `.kt` and `.kts` files against ktlint rules
- Follows the official Kotlin coding conventions with Android-specific rules enabled

### Auto-Fixing Issues

If ktlint reports style violations, you can auto-fix most issues:

```bash
./gradlew ktlintFormat
```

Then re-stage the fixed files and commit again.

### Manual Check

Run ktlint manually without committing:

```bash
./gradlew ktlintCheck
```

### Bypassing the Hook

If you need to commit without running ktlint (not recommended):

```bash
git commit --no-verify
```

---

## Pre-Push Hook

The pre-push hook runs lightweight JVM-only validation before pushing to remote. This provides fast
feedback without requiring a full Android SDK setup, making it ideal for:

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
