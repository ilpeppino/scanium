## Pre-launch Cleanup Plan (initial) – 2026-01-31

### Baseline inventory
- Branch: `cleanup-work` (from `main` @ af7922e2)
- Working tree: clean (`git status --porcelain` empty)
- Repo shape: top-level tree captured (depth 3); backend/src has 214 files (maxdepth 4); disk usage — backend 540M, androidApp 718M, core-domainpack 3.4M, howto 4.8M, deploy 204K, monitoring 584M.

### Diagnostics executed
- `npm run build` (backend) — ✅ TypeScript compile + postbuild copy succeeded.
- `npx depcheck` (backend) — exit code 255 but produced results: unused deps `@fastify/helmet`, `@fastify/rate-limit`, `@opentelemetry/instrumentation`, `@opentelemetry/sdk-trace-node`, `docker-compose`, `pino`, `pino-opentelemetry-transport`, `pino-pretty`; unused dev dep `@vitest/coverage-v8`; missing deps flagged `ioredis`, `fastify-plugin` (dynamic import + plugins). Needs manual verification before action.
- `npm install --legacy-peer-deps` (backend) — updated lockfile after adding explicit deps (`ioredis`, `fastify-plugin`) and removing unused ones; peer conflict (`pino-opentelemetry-transport` expects pino ^10) kept as legacy resolution to match existing runtime behavior.
- `npm test` (backend) — **fails** in current environment: Prisma needs local Postgres (`PrismaClientInitializationError: Can't reach database server at localhost:5432`); multiple suite failures (`Auth config not provided`, adapter URL expectations). Not addressed in this cleanup; tests must be rerun when DB + test config are available.

### Unused/obsolete candidates (evidence so far)
- `@fastify/helmet`, `@fastify/rate-limit` — no imports in `backend/src` (`rg "@fastify/helmet"` none; `rg "@fastify/rate-limit"` none). Candidate removal after confirm not pulled by config.
- `@opentelemetry/sdk-trace-node` — not imported in `backend/src` (`rg "sdk-trace-node"` none); telemetry uses `@opentelemetry/sdk-node` instead. Candidate removal.
- `docker-compose` (npm) — not imported in backend source (`rg "docker-compose" backend/src` none); Docker is managed via compose files; likely unused runtime dep.
- `pino`, `pino-pretty`, `pino-opentelemetry-transport` — actually used in `backend/src/app.ts` logger transport; depcheck false positive → keep.
- `@opentelemetry/instrumentation` — used in `backend/src/infra/telemetry/index.ts`; depcheck false positive → keep.
- `@vitest/coverage-v8` — dev dep not referenced in scripts; candidate removal.
- Missing deps: `ioredis` (dynamic import in `modules/vision/routes.ts`, `modules/classifier/routes.ts`, `modules/assistant/routes.ts`) — should be explicit dependency; 
  `fastify-plugin` used by HTTP plugins but currently resolved transitively; should be explicit dependency. Track for tooling commit.

### Candidate deletions (evidence-based; no behavior change)

### Candidate deletions (evidence-based; no behavior change)
- `backend/src/app.ts.bak.20260131-145312` and `backend/src/app.ts.bak.20260131-145404` — timestamped backups; primary `app.ts` exists; `rg "app.ts.bak"` shows no references.
- `backend/src/infra/db/prisma.ts.bak.20260131-144107` — backup of prisma helper; `rg "prisma.ts.bak.20260131-144107"` none; primary `prisma.ts` used.
- `backend/src/modules/catalog/index.ts.bak.20260131-150557` — backup of catalog barrel; `rg` shows no references; current `modules/catalog/index.ts` present.
- `backend/docker-compose.yml.bak.20260102-144858` and `backend/docker-compose.yml.bak.20260102-230347` — dated backups; not referenced (`rg "docker-compose.yml.bak.20260102-*"` none) and superseded by current `backend/docker-compose.yml` (howto/infra/scripts uses fresh backups on demand).
- `backend/dist.tar.gz` — archive artifact alongside built `dist/`; no references (`rg "dist.tar.gz"` none); likely accidental export, safe to remove after confirming build reproducibility.

### Candidate refactors (small, stability-focused)
- Normalize backend ESM imports to explicit file paths where directory imports exist (target brittle paths in backend/src modules) — reduces Docker/ESM resolution issues; risk: low, runtime-neutral if paths identical.
- Ensure Prisma client init/disconnect helpers are imported consistently from a single module to avoid engine/openssl mismatches in containers — risk: medium, controlled by tests.
- Remove stale commented-out debug logging in backend entrypoints if proven unused — risk: low.

### Tooling improvements
- Add/affirm backend TypeScript compile in CI (`npm run build` / `tsc --noEmit`) to surface dead exports early; quick run locally for diagnostics.
- Run `npm run lint` / `npm run test` in backend as preflight; document required commands per commit.

### Do-not-touch list (critical paths)
- `backend/prisma/migrations/**`, `deploy/**`, `monitoring/**`, `howto/**` docs unless duplicates, `security` docs, `docker-compose` files in use, `androidApp` flavors, `core-*` shared models.

### Next steps
1) Run backend TypeScript compile + lint/tests to surface dead exports and unused deps.
2) Map unused dependencies/assets/docs with evidence (import graph + ripgrep).
3) Propose first PR-sized change set (max ~20 files) starting with tooling diagnostics, then backup artifact deletions.

### Proposed first PR-sized batch
**Commit 1 (tooling/diagnostics, no deletions):**
- backend `package.json`/lock: add explicit deps `ioredis`, `fastify-plugin` (used via dynamic import/plugins); drop unused dev dep `@vitest/coverage-v8` (no script references); consider removing unused runtime deps `@fastify/helmet`, `@fastify/rate-limit`, `@opentelemetry/sdk-trace-node`, `docker-compose` after confirming no indirect usage (currently zero imports). Re-run `npm run build` + `npm test`.

**Commit 2 (deletions batch 1 - backend backups/artifacts):**
- Remove timestamped backups: `backend/src/app.ts.bak.20260131-145312`, `backend/src/app.ts.bak.20260131-145404`, `backend/src/infra/db/prisma.ts.bak.20260131-144107`, `backend/src/modules/catalog/index.ts.bak.20260131-150557` (no references).
- Remove `backend/docker-compose.yml.bak.20260102-144858`, `backend/docker-compose.yml.bak.20260102-230347` (unused backups) and `backend/dist.tar.gz` (archive artifact, no references). Validate `npm run build` still clean.

**Commit 3 (polish/refactor):**
- ESM import hygiene: ensure explicit file-path imports in backend modules where directory imports remain; standardize Prisma client helper usage. Remove truly obsolete commented debug logs if found. Re-run `npm run build` + `npm test` + smoke notes.

### Changelog
- 2026-01-31: Initial inventory + candidate list created.
- 2026-01-31: Added dependency hygiene plan; updated backend deps (add `ioredis`, `fastify-plugin`; drop `@fastify/helmet`, `@fastify/rate-limit`, `@opentelemetry/sdk-trace-node`, `docker-compose`, `@vitest/coverage-v8`); lockfile refreshed with legacy peer handling.
- 2026-01-31: `npm test` attempted — fails due to missing Postgres and auth config; suites with external expectations remain to be revisited when env is ready.
- 2026-01-31: Deleted unused backups/artifacts in backend (`src/app.ts.bak.*`, `src/infra/db/prisma.ts.bak.*`, `src/modules/catalog/index.ts.bak.*`, `docker-compose.yml.bak.*`, `dist.tar.gz`) per evidence.
