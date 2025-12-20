***REMOVED*** Cleanup & Docs Migration Report (2025-12-20)

***REMOVED******REMOVED*** What changed (summary)
- Centralized scripts under `scripts/` with a README; updated backend helpers to run from the correct working directory.
- Reduced the canonical doc set to six files and archived legacy/duplicate content under `docs/_archive/2025-12/` with banner notes.
- Refreshed core docs (INDEX, ARCHITECTURE, DEV_GUIDE, PRODUCT, DECISIONS) to match current Gradle versions, BuildConfig flags, and workflow behavior.
- Removed tracked crash artifacts (`crash.log`, `tombstone_23`) and tightened `.gitignore` for logs/build outputs/temp dirs.

***REMOVED******REMOVED*** Inventory (classification)
| Path / Scope | Classification |
| --- | --- |
| README.md; ai/CODEX_CONTEXT.md; android/ios source modules | KEEP |
| docs/INDEX.md, ARCHITECTURE.md, DEV_GUIDE.md, CI_CD.md, PRODUCT.md, DECISIONS.md, CLEANUP_REPORT.md | UPDATE/KEEP (canonical) |
| scripts/build.sh, scripts/backend/*, scripts/dev/*, scripts/tools/* | MOVE/UPDATE |
| hooks/README.md (hook guidance) | UPDATE |
| docs/_archive/2025-12/** (legacy feature/security/ADR/parity/test/back-end docs) | ARCHIVE |
| docs/_archive/2025-12/backend/** (all backend markdown) | ARCHIVE |
| docs/_archive/2025-12/parity/**, ADR/**, LISTING_TITLE_FIX_SUMMARY.md, test-refactoring files | ARCHIVE |
| crash.log, tombstone_23 | DELETE (tracked artifacts) |
| bicycle.jpg, local.properties.example | KEEP |

***REMOVED******REMOVED*** Old → New mapping (selected)
| Old location | Action | New location |
| --- | --- | --- |
| `build.sh` | MOVE | `scripts/build.sh` |
| `create-github-issues.sh` | MOVE | `scripts/tools/create-github-issues.sh` |
| `backend/start-dev.sh`, `backend/stop-dev.sh`, `backend/scripts/verify-setup.sh` | MOVE | `scripts/backend/` (paths preserved) |
| `hooks/install-hooks.sh` | MOVE | `scripts/dev/install-hooks.sh` |
| `test_ml_kit_detection.sh` | MOVE | `scripts/dev/test_ml_kit_detection.sh` |
| `backend/*.md`, `backend/docs/*`, `backend/src/modules/auth/ebay/README.md`, `backend/tools/ebay-domainpack-gen/*.md` | ARCHIVE | `docs/_archive/2025-12/backend/...` |
| `docs/BUILD_STABILITY.md`, `TESTING.md`, `SECURITY.md`, `COMPREHENSIVE_AUDIT_REPORT.md`, `REVIEW_REPORT.md`, `PROFILING_GUIDE.md`, `PACKAGE_BOUNDARIES.md`, `PLAN_ARCHITECTURE_REFACTOR.md`, `VERSION_COMPATIBILITY.md`, parity and test plans | ARCHIVE | `docs/_archive/2025-12/...` |
| `docs/ADR/*.md` | ARCHIVE | `docs/_archive/2025-12/ADR/` |
| `LISTING_TITLE_FIX_SUMMARY.md` | ARCHIVE | `docs/_archive/2025-12/LISTING_TITLE_FIX_SUMMARY.md` |
| `crash.log`, `tombstone_23` | DELETE | (removed; ignored via `.gitignore`) |

***REMOVED******REMOVED*** Canonical docs and audience
| Doc | Audience | Purpose |
| --- | --- | --- |
| docs/INDEX.md | All contributors/agents | Entry point, canonical list, archive policy. |
| docs/ARCHITECTURE.md | Engineers | Module layout, pipelines, security posture, platform boundaries. |
| docs/DEV_GUIDE.md | Developers | Setup, build/test commands, container limitations, cloud config. |
| docs/CI_CD.md | Maintainers | Workflow triggers, artifacts, security scans. |
| docs/PRODUCT.md | PM/QA/engineers | Current features, flows, modes, cloud gating. |
| docs/DECISIONS.md | Leads/architects | ADR-lite history and recent consolidation choices. |
| docs/CLEANUP_REPORT.md | Maintainers | Source-of-truth for this migration and future archiving rules. |

***REMOVED******REMOVED*** Maintenance rules
- Keep new documentation under `docs/`; archive retired material under `docs/_archive/YYYY-MM/` with an archive banner and update this report’s mapping.
- Prefer linking to canonical sections instead of duplicating instructions.
- Add new scripts under `scripts/` and document them in `scripts/README.md`; move deprecated scripts to `scripts/_archive/YYYY-MM/`.
- Keep `.gitignore` patterns minimal and remove tracked outputs promptly.
