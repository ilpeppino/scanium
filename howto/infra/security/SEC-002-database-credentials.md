***REMOVED*** SEC-002 Remediation Plan – PostgreSQL Default Credentials

Docker Compose currently provisions PostgreSQL with the fallback user/password `scanium:scanium`. If an operator forgets to override the variables, the container boots with trivial credentials that an attacker can guess instantly.

***REMOVED******REMOVED*** Required Changes
1. Compose now requires `POSTGRES_USER` and `POSTGRES_PASSWORD` to be explicitly provided; the stack will fail fast if they are missing.
2. `DATABASE_URL` enforces TLS via `sslmode=require` so plaintext connections are rejected.
3. `.env.example` documents strong password generation using `openssl rand -base64 32` and no longer suggests weak defaults.

***REMOVED******REMOVED*** Operational Follow-Up
1. Generate new credentials per environment and store them in the password manager (NAS/staging/prod/local).
2. Update deployment automation and `.env` files with the rotated values before bringing up the stack.
3. Verify TLS is enabled by running `psql "postgresql://user@host:5432/db?sslmode=require" -c '\conninfo'` from the backend container—the output should show `SSL connection`.
4. Remove any legacy `.env` files or compose overrides that still reference `scanium:scanium`.
