***REMOVED*** Documentation Cleanup Report - 2025-12-27

***REMOVED******REMOVED*** Summary

This report documents the documentation cleanup performed on 2025-12-27 to consolidate the canonical documentation set and organize the archive.

***REMOVED******REMOVED*** What Changed

***REMOVED******REMOVED******REMOVED*** Files Kept in `/docs` (11 Canonical Documents)

| File | Type | Purpose |
|------|------|---------|
| `INDEX.md` | Canonical | Entry point, canonical list, archive policy |
| `ARCHITECTURE.md` | Canonical | System + app architecture, modules, flows |
| `CODEX_CONTEXT.md` | Canonical | Agent quickmap for AI assistants |
| `DEV_GUIDE.md` | Canonical | Development workflow, setup, commands |
| `PRODUCT.md` | Canonical | Current app behavior, screens, user flows |
| `SECURITY.md` | Canonical | Privacy/security posture, data handling |
| `CI_CD.md` | Canonical | GitHub Actions, artifacts, security scans |
| `RELEASE_CHECKLIST.md` | Canonical | Build, sign, and distribute process |
| `MANUAL_GOLDEN_RUNBOOK.md` | Canonical | QA regression test runbook |
| `DECISIONS.md` | Canonical | ADR-lite list of notable choices |
| `CLEANUP_REPORT.md` | Canonical | Doc migration tracking |
| `REVIEW_REPORT.md` | Canonical | Architectural and security review (2025-12-24) |

***REMOVED******REMOVED******REMOVED*** Files Moved to Archive with DONE_ Prefix

| Original Path | Archive Path | Verification |
|---------------|--------------|--------------|
| `docs/AI_GATEWAY.md` | `docs/_archive/2025-12/notes/DONE_AI_GATEWAY.md` | Backend endpoint `/v1/assist/chat` exists in `backend/src/modules/assistant/routes.ts`; Android `AssistantRepository.kt` implemented |
| `docs/ARCHITECTURE_MAP_FOR_TESTING.md` | `docs/_archive/2025-12/notes/DONE_ARCHITECTURE_MAP_FOR_TESTING.md` | Testing architecture fully documented |
| `docs/FEATURES_COPY_SHARE.md` | `docs/_archive/2025-12/notes/DONE_FEATURES_COPY_SHARE.md` | `ListingShareHelperTest.kt` exists; share functionality implemented |
| `docs/VOICE_MODE_VALIDATION.md` | `docs/_archive/2025-12/notes/DONE_VOICE_MODE_VALIDATION.md` | `AssistantVoiceController.kt`, `VoiceSettingsRepositoryTest.kt` exist; voice mode implemented |
| `docs/logcat-commands.md` | `docs/_archive/2025-12/notes/logcat-commands.md` | Reference notes for debugging |

***REMOVED******REMOVED******REMOVED*** Folders Moved to Archive

| Original Path | Archive Path | Status |
|---------------|--------------|--------|
| `docs/release/` | `docs/_archive/2025-12/release/` | Detailed release checklists (supplementary) |
| `docs/go-live/` | `docs/_archive/2025-12/go-live/` | Production readiness (WIP - not ready) |

***REMOVED******REMOVED*** Documents Updated

| File | Changes |
|------|---------|
| `docs/INDEX.md` | Added CODEX_CONTEXT, SECURITY, MANUAL_GOLDEN_RUNBOOK, RELEASE_CHECKLIST to canonical list; organized into sections (Core Reference, Operations & Process, Decision Log & Reports); updated archive structure documentation |
| `docs/CLEANUP_REPORT.md` | Added 2025-12-27 cleanup section with migration table |

***REMOVED******REMOVED*** Verification Evidence

***REMOVED******REMOVED******REMOVED*** AI Gateway (DONE)
- Backend: `backend/src/modules/assistant/routes.ts` - `/v1/assist/chat` endpoint
- Backend: `backend/src/modules/assistant/routes.test.ts` - Tests exist
- Android: `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantRepository.kt`
- Android: `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantScreen.kt`

***REMOVED******REMOVED******REMOVED*** Copy/Share Features (DONE)
- Test: `androidApp/src/test/java/com/scanium/app/selling/util/ListingShareHelperTest.kt`

***REMOVED******REMOVED******REMOVED*** Voice Mode (DONE)
- Controller: `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantVoiceController.kt`
- Settings: `androidApp/src/main/java/com/scanium/app/data/SettingsRepository.kt` (voice mode toggles)
- Tests: `androidApp/src/test/java/com/scanium/app/data/VoiceSettingsRepositoryTest.kt`

***REMOVED******REMOVED******REMOVED*** Accessibility (95 contentDescription usages across 20 files)
- CameraScreen.kt: 7 usages
- ItemsListScreen.kt: 16 usages
- SettingsScreen.kt: 7 usages
- DeveloperOptionsScreen.kt: 7 usages
- AssistantScreen.kt (selling): 14 usages

***REMOVED******REMOVED*** Ambiguous Items (Not Modified)

The following archived issue docs were reviewed but not renamed with DONE_/WIP_ prefixes because they already contain status indicators in their content:

| File | Current Status in Doc |
|------|----------------------|
| `issues/001-remove-duplicate-candidatetracker-dead-code.md` | "Status: Resolved" - CandidateTracker removed |
| `issues/004-fix-cameraxmanager-comment-references-wrong-tracker.md` | Related to 001, likely resolved |
| `issues/005-remove-disabled-code-block-if-false.md` | Unknown status |
| `issues/006-extract-hardcoded-configuration-values.md` | Unknown status |
| `issues/008-add-missing-fields-to-scanneditem-entity.md` | Unknown status |
| `issues/013-onconflict-replace-strategy-risk.md` | Unknown status |
| `issues/014-classification-system-incomplete-placeholders.md` | WIP - classification system still evolving |
| `issues/015-missing-accessibility-features.md` | Partially addressed (95 contentDescriptions exist) |
| `issues/017-no-user-feedback-for-classification-mode-switch.md` | Unknown status |

***REMOVED******REMOVED*** Archive Structure (After Cleanup)

```
docs/_archive/2025-12/
├── ADR/                    ***REMOVED*** Architecture Decision Records
├── apk/                    ***REMOVED*** APK distribution notes
├── backend/                ***REMOVED*** Backend reference docs
├── features/               ***REMOVED*** Feature design docs
├── go-live/                ***REMOVED*** Production readiness (WIP)
├── issues/                 ***REMOVED*** Issue tracking docs
├── kmp-migration/          ***REMOVED*** KMP migration plans
├── md/                     ***REMOVED*** Misc markdown docs
│   ├── architecture/
│   ├── backend/
│   ├── debugging/
│   ├── features/
│   ├── fixes/
│   ├── improvements/
│   └── testing/
├── notes/                  ***REMOVED*** Implemented feature docs (DONE_*)
├── parity/                 ***REMOVED*** Platform parity plans
├── pr/                     ***REMOVED*** PR-related docs
├── release/                ***REMOVED*** Release checklists
└── security/               ***REMOVED*** Security assessments
```

***REMOVED******REMOVED*** Metrics

| Metric | Count |
|--------|-------|
| Canonical docs kept in /docs | 12 |
| Files moved to archive with DONE_ prefix | 4 |
| Files moved to archive without prefix | 1 |
| Folders moved to archive | 2 |
| Docs updated | 2 |

---

**Generated:** 2025-12-27
**Verified by:** Documentation cleanup automation
