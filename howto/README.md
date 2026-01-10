# Scanium How-To Documentation

This directory consolidates **all documentation, runbooks, operational scripts, and reports** for the Scanium project.

## Directory Structure

| Directory | Scope |
|-----------|-------|
| [app/](app/) | Android/KMP mobile app: releases, debugging, testing |
| [backend/](backend/) | Node/TS backend: deploy, Prisma/Postgres, API docs |
| [monitoring/](monitoring/) | Grafana, Mimir, Loki, Tempo, Alloy, dashboards, telemetry |
| [infra/](infra/) | NAS ops, Docker, compose, networking, security, secrets |
| [project/](project/) | Repo conventions, workflows, branching, contributor docs |
| [archive/](archive/) | Deprecated/obsolete docs (kept for historical reference) |

## Quick Links

### Operations
- [Backend Deploy Runbook](backend/deploy/)
- [Monitoring Scripts](monitoring/scripts/)
- [NAS Infrastructure](infra/deploy/)
- [Incident Reports](monitoring/incidents/)

### Development
- [Dev Guide](project/reference/DEV_GUIDE.md)
- [CI/CD Workflows](project/workflows/)
- [Security Guidelines](infra/security/)

### App
- [Release Checklist](app/releases/)
- [Debugging Guides](app/debugging/)

## Migration Note

This structure was created on 2026-01-10 to consolidate documentation that was previously scattered across:
- `docs/`
- `backend/docs/`
- `monitoring/`
- `scripts/` (README files)
- `deploy/*/README.md`
- Root-level `.md` files

See [MIGRATION_MAP.md](MIGRATION_MAP.md) for the complete old-to-new path mapping.
