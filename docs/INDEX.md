***REMOVED*** Scanium Docs Index

Start here for up-to-date context. Keep reads under 3 minutes; follow links for depth.

***REMOVED******REMOVED*** How to use these docs
- Read this page first, then open **ARCHITECTURE** and **DEV_GUIDE** only as needed.
- Prefer code pointers over prose; cite paths/lines when updating docs.
- Update only the canonical docs below; do not add new scattered Markdown files.
- When unsure, mark TODO/VERIFY with a code pointer instead of guessing.

***REMOVED******REMOVED*** Canonical docs (keep these current)

***REMOVED******REMOVED******REMOVED*** Core Reference
- [ARCHITECTURE](./ARCHITECTURE.md) – system + app architecture, modules, flows, key code locations.
- [CODEX_CONTEXT](./CODEX_CONTEXT.md) – agent quickmap for AI assistants.
- [DEV_GUIDE](./DEV_GUIDE.md) – local setup, commands, debugging, container dos/don'ts.
- [PRODUCT](./PRODUCT.md) – current app behavior, screens, and user flows.
- [SECURITY](./SECURITY.md) – privacy/security posture and data handling.

***REMOVED******REMOVED******REMOVED*** Operations & Process
- [CI_CD](./CI_CD.md) – GitHub Actions, artifacts, security scans.
- [RELEASE_CHECKLIST](./RELEASE_CHECKLIST.md) – build, sign, and distribute process.
- [MANUAL_GOLDEN_RUNBOOK](./MANUAL_GOLDEN_RUNBOOK.md) – QA regression test runbook.

***REMOVED******REMOVED******REMOVED*** Decision Log & Reports
- [DECISIONS](./DECISIONS.md) – ADR-lite list of notable choices.
- [CLEANUP_REPORT](./CLEANUP_REPORT.md) – mapping of doc cleanup and archive locations.
- [REVIEW_REPORT](./REVIEW_REPORT.md) – comprehensive architectural and security review (2025-12-24).

***REMOVED******REMOVED*** Docs maintenance checklist
- Feature change: update PRODUCT; adjust ARCHITECTURE if flows/modules shift.
- Build/CI change: update DEV_GUIDE and CI_CD.
- Major decision: add a bullet to DECISIONS.
- Cleanup/archiving: record mappings in CLEANUP_REPORT to avoid sprawl.

***REMOVED******REMOVED*** Token-minimization rules for agents
- Search first (`rg`), open minimal slices, and keep quoted snippets small.
- Prefer patch edits; avoid rewriting files end-to-end.
- Keep Android build green; avoid SDK installs inside the Codex container.

***REMOVED******REMOVED*** Archive policy
- Historical docs, backend notes, parity plans, and ADRs live under `docs/_archive/2025-12/`.
- Feature docs that are now implemented are prefixed with `DONE_` (e.g., `DONE_AI_GATEWAY.md`).
- Work-in-progress plans are prefixed with `WIP_` (e.g., `WIP_go-live/`).
- When retiring a doc, move it under `docs/_archive/YYYY-MM/` with an archive banner and add the mapping to CLEANUP_REPORT.

***REMOVED******REMOVED******REMOVED*** Archive structure (2025-12)
```
docs/_archive/2025-12/
├── ADR/           ***REMOVED*** Architecture Decision Records
├── backend/       ***REMOVED*** Backend reference docs
├── features/      ***REMOVED*** Feature design docs
├── go-live/       ***REMOVED*** Production readiness (WIP)
├── issues/        ***REMOVED*** Issue tracking docs
├── kmp-migration/ ***REMOVED*** KMP migration plans
├── notes/         ***REMOVED*** Implemented feature docs (DONE_*)
├── parity/        ***REMOVED*** Platform parity plans
├── pr/            ***REMOVED*** PR-related docs
├── release/       ***REMOVED*** Release checklists
└── security/      ***REMOVED*** Security assessments
```
