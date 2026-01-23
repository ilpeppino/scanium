# Release Freeze: main Branch

**Effective:** January 20, 2026
**Release:** v1.6.0 (Build 100016)

## Freeze Status

The `main` branch is now **FROZEN** for the v1.6.0 production release.

## Release Commit

| Property | Value     |
|----------|-----------|
| Tag      | `v1.6.0`  |
| Commit   | `27f919f6` |
| Branch   | `main`    |
| Build    | 100016    |

## Artifact Location

```
androidApp/build/outputs/bundle/prodRelease/androidApp-prod-release.aab
```

- Size: ~70 MB
- Signed: SHA256withRSA (Giuseppe Tempone)
- Certificate valid: 2026-01-05 to 2050-12-30

## Rebuild Instructions

```bash
# Option 1: Using automated build script (recommended)
git checkout v1.6.0
./scripts/dev/build_release_aab.sh prod --version-name 1.6.0 --skip-increment

# Option 2: Manual build with Gradle
# 1. Checkout the release tag
git checkout v1.6.0

# 2. Ensure local.properties has correct version
cat >> local.properties << 'EOF'
scanium.version.code=100016
scanium.version.name=1.6.0
EOF

# 3. Clean and build
./gradlew clean test bundleRelease

# 4. Verify artifact
ls -la androidApp/build/outputs/bundle/prodRelease/androidApp-prod-release.aab
```

## Rollback Procedure

### Halt Current Rollout

1. Go to Google Play Console > Release > Production
2. Click "Halt rollout"
3. Confirm halt

### Promote Previous Version

1. Go to Release history
2. Find previous stable release (v1.3.1 or earlier)
3. Create new release with that artifact
4. Set rollout percentage to 100%

### Hotfix Procedure

**NEVER commit directly to main during freeze.**

```bash
# 1. Create hotfix branch from release tag
git checkout -b hotfix/v1.6.1 v1.6.0

# 2. Make minimal fix
# ... edit files ...

# 3. Test thoroughly
./gradlew test

# 4. Bump version in local.properties and version.properties
# scanium.version.code=100017
# scanium.version.name=1.6.1

# 5. Build and verify
./gradlew bundleRelease

# 6. Create PR for review (do NOT push directly)
git push -u origin hotfix/v1.6.1
gh pr create --title "Hotfix v1.6.1: [description]" --body "..."

# 7. After review and approval, merge to main
# 8. Tag and release
```

## Branch Policy (Post-Freeze)

| Branch      | Purpose                      | Direct Push |
|-------------|------------------------------|-------------|
| `main`      | Production releases only     | **NO**      |
| `ebay`      | eBay integration experiments | Yes         |
| `hotfix/*`  | Critical fixes only          | Via PR      |
| `feature/*` | New development              | Via PR      |

## Recommended Repository Guardrails

Configure in GitHub Settings > Branches > Branch protection rules:

### For `main` branch:

- [x] Require pull request before merging
- [x] Require approvals (1+)
- [x] Require status checks to pass (tests)
- [x] Require branches to be up to date
- [x] Do not allow bypassing the above settings
- [x] Restrict force pushes
- [x] Restrict deletions

## Experimental Work

All new development should happen on feature branches:

```bash
# For eBay integration work
git checkout ebay

# For new features
git checkout -b feature/my-feature main
```

## Contact

Release manager: Giuseppe Tempone
Repository: github.com/ilpeppino/scanium
