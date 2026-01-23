# Scanium Versioning Strategy

This project uses a standard `versionCode` and `versionName` approach for Android releases.

## 1. Version Code (`versionCode`)

- **Definition:** A positive integer that increases with every release.
- **Requirement:** Google Play requires a higher `versionCode` for every new upload.
- **Auto-increment:** In CI, this should be driven by the build number or a manual override.
- **Local Dev:** Defaults to `1`.

## 2. Version Name (`versionName`)

- **Format:** `MAJOR.MINOR.PATCH` (e.g., `1.0.0`)
- **MAJOR:** Significant feature changes or breaking API updates.
- **MINOR:** New features, UI improvements, significant enhancements.
- **PATCH:** Bug fixes, performance tweaks, minor stabilization.

## 3. Configuration

Versions are read from `local.properties` or environment variables in `androidApp/build.gradle.kts`:

| Key          | Property (`local.properties`) | Env Var                | Default |
|--------------|-------------------------------|------------------------|---------|
| Version Code | `scanium.version.code`        | `SCANIUM_VERSION_CODE` | `1`     |
| Version Name | `scanium.version.name`        | `SCANIUM_VERSION_NAME` | `1.0`   |

## 4. Release Bumping

To prepare a new release:

1. Determine the new `versionName` based on changes.
2. Increment `versionCode`.
3. Update `CHANGELOG.md` (if applicable).
4. Tag the commit in git: `git tag -a v1.0.1 -m "Release 1.0.1"`
