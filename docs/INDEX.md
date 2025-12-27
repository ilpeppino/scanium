# Scanium Docs Index

Start here for up-to-date context. Keep reads under 3 minutes; follow links for depth.

## How to use these docs
- Read this page first, then open **ARCHITECTURE** and **DEV_GUIDE** only as needed.
- Prefer code pointers over prose; cite paths/lines when updating docs.
- Update only the canonical docs below; do not add new scattered Markdown files.
- When unsure, mark TODO/VERIFY with a code pointer instead of guessing.

## Canonical docs (keep these current)

### Core Reference
- [ARCHITECTURE](./ARCHITECTURE.md) – system + app architecture, modules, flows, key code locations.
- [CODEX_CONTEXT](./CODEX_CONTEXT.md) – agent quickmap for AI assistants.
- [DEV_GUIDE](./DEV_GUIDE.md) – local setup, commands, debugging, container dos/don'ts.
- [PRODUCT](./PRODUCT.md) – current app behavior, screens, and user flows.
- [SECURITY](./SECURITY.md) – privacy/security posture and data handling.

### Operations & Process
- [CI_CD](./CI_CD.md) – GitHub Actions, artifacts, security scans.
- [RELEASE_CHECKLIST](./RELEASE_CHECKLIST.md) – build, sign, and distribute process.
- [MANUAL_GOLDEN_RUNBOOK](./MANUAL_GOLDEN_RUNBOOK.md) – QA regression test runbook.

### Decision Log & Reports
- [DECISIONS](./DECISIONS.md) – ADR-lite list of notable choices.
- [CLEANUP_REPORT](./CLEANUP_REPORT.md) – mapping of doc cleanup and archive locations.
- [REVIEW_REPORT](./REVIEW_REPORT.md) – comprehensive architectural and security review (2025-12-24).

## Docs maintenance checklist
- Feature change: update PRODUCT; adjust ARCHITECTURE if flows/modules shift.
- Build/CI change: update DEV_GUIDE and CI_CD.
- Major decision: add a bullet to DECISIONS.
- Cleanup/archiving: record mappings in CLEANUP_REPORT to avoid sprawl.

## Token-minimization rules for agents
- Search first (`rg`), open minimal slices, and keep quoted snippets small.
- Prefer patch edits; avoid rewriting files end-to-end.
- Keep Android build green; avoid SDK installs inside the Codex container.

## Archive policy
- Historical docs, backend notes, parity plans, and ADRs live under `docs/_archive/2025-12/`.
- Feature docs that are now implemented are prefixed with `DONE_` (e.g., `DONE_AI_GATEWAY.md`).
- Work-in-progress plans are prefixed with `WIP_` (e.g., `WIP_go-live/`).
- When retiring a doc, move it under `docs/_archive/YYYY-MM/` with an archive banner and add the mapping to CLEANUP_REPORT.

### Archive structure (2025-12)
```
docs/_archive/2025-12/
├── ADR/           # Architecture Decision Records
├── backend/       # Backend reference docs
├── features/      # Feature design docs
├── go-live/       # Production readiness (WIP)
├── issues/        # Issue tracking docs
├── kmp-migration/ # KMP migration plans
├── notes/         # Implemented feature docs (DONE_*)
├── parity/        # Platform parity plans
├── pr/            # PR-related docs
├── release/       # Release checklists
└── security/      # Security assessments
```
