***REMOVED*** Scanium Docs Index

Start here for up-to-date context. Keep reads under 3 minutes; follow links for depth.

***REMOVED******REMOVED*** How to use these docs
- Read this page first, then open **ARCHITECTURE** and **DEV_GUIDE** only as needed.
- Prefer code pointers over prose; cite paths/lines when updating docs.
- Update only the canonical docs below; do not add new scattered Markdown files.
- When unsure, mark TODO/VERIFY with a code pointer instead of guessing.

***REMOVED******REMOVED*** Canonical docs
- [ARCHITECTURE](./ARCHITECTURE.md) – modules, flows, key code locations.
- [DEV_GUIDE](./DEV_GUIDE.md) – local setup, commands, debugging, container dos/don'ts.
- [CI_CD](./CI_CD.md) – GitHub Actions, artifacts, security scans.
- [PRODUCT](./PRODUCT.md) – current app behavior, screens, and user flows.
- [SECURITY](./SECURITY.md) – security posture and follow-ups.
- [DECISIONS](./DECISIONS.md) – ADR-lite list of notable choices.
- [TESTING](./TESTING.md) – test commands, coverage thresholds, and guidance.

***REMOVED******REMOVED*** Docs maintenance checklist
- Feature change: update PRODUCT; adjust ARCHITECTURE if flows/modules shift.
- Build/CI change: update DEV_GUIDE and CI_CD.
- Major decision: add a bullet to DECISIONS.
- Security finding: record in SECURITY (and link to supporting issue/PR).

***REMOVED******REMOVED*** Token-minimization rules for agents
- Search first (`rg`), open minimal slices, and keep quoted snippets small.
- Prefer patch edits; avoid rewriting files end-to-end.
- Keep Android build green; avoid SDK installs inside the Codex container.

***REMOVED******REMOVED*** Docs migration (2025-12)
- Archived detailed docs to `docs/_archive/2025-12/` (features, issues, PR templates, security reports, KMP plans, legacy md/ folder, APK notes, ROADMAP/SETUP/BACKEND_DEPLOYMENT/CLAUDE).
- Canonical knowledge distilled into the files listed above; update those going forward.

| Old location | Action |
| --- | --- |
| `docs/CI_TESTING.md`, `docs/IMPROVEMENTS.md`, `docs/REVIEW_SUMMARY.md` | Archived to `docs/_archive/2025-12/` |
| `docs/features/*`, `docs/issues/*`, `docs/pr/*` | Archived to `docs/_archive/2025-12/` |
| `docs/kmp-migration/*`, `docs/security/*` | Archived to `docs/_archive/2025-12/` |
| `md/**` (architecture, fixes, testing, debugging) | Archived to `docs/_archive/2025-12/md/` |
| `ROADMAP.md`, `SETUP.md`, `BACKEND_DEPLOYMENT.md`, `CLAUDE.md` | Archived to `docs/_archive/2025-12/` |
| `apk/apk.md` | Archived to `docs/_archive/2025-12/apk/` |
