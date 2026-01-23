# SEC-001 Remediation Plan – Hardcoded Secrets in Git

The files `backend/.env.backup` and `backend/.env.last` contain active API keys, marketplace
credentials, and session secrets. Both files are tracked in git, so anyone with repository access
can recover the credentials from current or past revisions.

## Immediate Actions

1. **Revoke every key referenced in the leaked files.** Work with the eBay developer portal,
   Cloudflare Zero Trust dashboard, and any first-party service owners to invalidate the exposed
   credentials.
2. **Purge the leaked files from every environment.** Remove them from application servers, CI
   caches, and developer laptops to avoid reintroducing the secrets.

## Repository Cleanup

1. Ensure `.gitignore` blocks future commits (`backend/.env`, `backend/.env.backup`,
   `backend/.env.last`).
2. Use [`git filter-repo`](https://github.com/newren/git-filter-repo) (or BFG) to delete the files
   from the entire git history:
   ```bash
   pip install git-filter-repo  # if needed
   git filter-repo --invert-paths --path backend/.env.backup --path backend/.env.last
   git push --force --all
   git push --force --tags
   ```
3. Ask every collaborator to run `git fetch --all --prune` and reset local clones to the rewritten
   history.

## Secret Rotation

1. Generate replacements for every leaked credential (API keys, eBay OAuth credentials, Cloudflare
   tunnel token, Prisma session secrets).
2. Update the secure secret storage backend (preferred: 1Password vault + deployment automation).
   Never store production secrets directly in git.
3. Reconfigure all runtimes (NAS, staging, local dev) with the rotated values. Restart services to
   ensure new secrets load correctly.

## Verification

1. Run `git grep -n \"SCANIUM_API_KEY\"` (and similar tokens) to confirm no secrets remain.
2. Audit CI artifacts and shared storage for copies of the removed files.
3. Document the incident in the security runbook and monitor for suspicious API usage that might
   indicate credential abuse.
