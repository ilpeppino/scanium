# Scanium Docs Index

Start here for up-to-date context. Keep reads under 3 minutes; follow links for depth.

## How to use these docs
- Read this page first, then open **ARCHITECTURE** and **DEV_GUIDE** only as needed.
- Prefer code pointers over prose; cite paths/lines when updating docs.
- Update only the canonical docs below; do not add new scattered Markdown files.
- When unsure, mark TODO/VERIFY with a code pointer instead of guessing.

## Canonical docs (keep these current)
- [ARCHITECTURE](./ARCHITECTURE.md) – modules, flows, key code locations.
- [DEV_GUIDE](./DEV_GUIDE.md) – local setup, commands, debugging, container dos/don'ts.
- [CI_CD](./CI_CD.md) – GitHub Actions, artifacts, security scans.
- [PRODUCT](./PRODUCT.md) – current app behavior, screens, and user flows.
- [DECISIONS](./DECISIONS.md) – ADR-lite list of notable choices.
- [CLEANUP_REPORT](./CLEANUP_REPORT.md) – mapping of this doc cleanup and where archived content lives.

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
- Historical docs, backend notes, parity plans, and ADRs now live under `docs/_archive/2025-12/` with pointers back to this index or DECISIONS.
- When retiring a doc, move it under `docs/_archive/YYYY-MM/` with an archive banner and add the mapping to CLEANUP_REPORT.
