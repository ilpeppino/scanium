***REMOVED*** Release Freeze: main Branch

**Effective:** January 17, 2026
**Release:** v1.3.1 (Build 13)

***REMOVED******REMOVED*** Freeze Status

The `main` branch is now **FROZEN** for the v1.3.1 production release.

***REMOVED******REMOVED*** Release Commit

| Property | Value |
|----------|-------|
| Tag | `v1.3.1` |
| Commit | `e457c4a` |
| Branch | `main` |
| Build | 13 |

***REMOVED******REMOVED*** Artifact Location

```
androidApp/build/outputs/bundle/prodRelease/androidApp-prod-release.aab
```

- Size: ~70 MB
- Signed: SHA256withRSA (Giuseppe Tempone)
- Certificate valid: 2026-01-05 to 2050-12-30

***REMOVED******REMOVED*** Rebuild Instructions

```bash
***REMOVED*** 1. Checkout the release tag
git checkout v1.3.1

***REMOVED*** 2. Ensure local.properties has correct version
cat >> local.properties << 'EOF'
scanium.version.code=13
scanium.version.name=1.3.1
EOF

***REMOVED*** 3. Clean and build
./gradlew clean test bundleRelease

***REMOVED*** 4. Verify artifact
ls -la androidApp/build/outputs/bundle/prodRelease/androidApp-prod-release.aab
```

***REMOVED******REMOVED*** Rollback Procedure

***REMOVED******REMOVED******REMOVED*** Halt Current Rollout
1. Go to Google Play Console > Release > Production
2. Click "Halt rollout"
3. Confirm halt

***REMOVED******REMOVED******REMOVED*** Promote Previous Version
1. Go to Release history
2. Find previous stable release (v1.3.0 or earlier)
3. Create new release with that artifact
4. Set rollout percentage to 100%

***REMOVED******REMOVED******REMOVED*** Hotfix Procedure

**NEVER commit directly to main during freeze.**

```bash
***REMOVED*** 1. Create hotfix branch from release tag
git checkout -b hotfix/v1.3.2 v1.3.1

***REMOVED*** 2. Make minimal fix
***REMOVED*** ... edit files ...

***REMOVED*** 3. Test thoroughly
./gradlew test

***REMOVED*** 4. Bump version in local.properties
***REMOVED*** scanium.version.code=14
***REMOVED*** scanium.version.name=1.3.2

***REMOVED*** 5. Build and verify
./gradlew bundleRelease

***REMOVED*** 6. Create PR for review (do NOT push directly)
git push -u origin hotfix/v1.3.2
gh pr create --title "Hotfix v1.3.2: [description]" --body "..."

***REMOVED*** 7. After review and approval, merge to main
***REMOVED*** 8. Tag and release
```

***REMOVED******REMOVED*** Branch Policy (Post-Freeze)

| Branch | Purpose | Direct Push |
|--------|---------|-------------|
| `main` | Production releases only | **NO** |
| `ebay` | eBay integration experiments | Yes |
| `hotfix/*` | Critical fixes only | Via PR |
| `feature/*` | New development | Via PR |

***REMOVED******REMOVED*** Recommended Repository Guardrails

Configure in GitHub Settings > Branches > Branch protection rules:

***REMOVED******REMOVED******REMOVED*** For `main` branch:
- [x] Require pull request before merging
- [x] Require approvals (1+)
- [x] Require status checks to pass (tests)
- [x] Require branches to be up to date
- [x] Do not allow bypassing the above settings
- [x] Restrict force pushes
- [x] Restrict deletions

***REMOVED******REMOVED*** Experimental Work

All new development should happen on feature branches:

```bash
***REMOVED*** For eBay integration work
git checkout ebay

***REMOVED*** For new features
git checkout -b feature/my-feature main
```

***REMOVED******REMOVED*** Contact

Release manager: Giuseppe Tempone
Repository: github.com/ilpeppino/scanium
