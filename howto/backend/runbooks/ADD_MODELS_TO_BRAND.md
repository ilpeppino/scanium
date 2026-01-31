# Scanium Backend — Complete NAS-Only Runbook (SINGLE CODE BLOCK)

This document contains ALL successful operations used to configure, migrate,
populate, and validate the Scanium backend exclusively on NAS using Docker.
No local database, no local Prisma, no Mac involvement.

================================================================================
ASSUMPTIONS
================================================================================

- Repo root: /volume1/docker/scanium/repo
- Backend path: /volume1/docker/scanium/repo/backend
- Docker network: backend_scanium-network
- Containers:
  - scanium-postgres
  - scanium-backend
- Database:
  - user: scanium
  - password: scanium
  - db: scanium
- Backend port: 8080
- No local DB / no local Prisma

================================================================================
0) ENTER REPOSITORY (NAS)
================================================================================

cd /volume1/docker/scanium/repo

================================================================================
1) VERIFY RUNNING CONTAINERS (POSTGRES IS DOCKER-ONLY)
================================================================================

docker ps --format "table {{.Names}}\t{{.Ports}}\t{{.Status}}"

Expected:
- scanium-postgres → 5432/tcp
- scanium-backend → 8080/tcp

================================================================================
2) INSPECT BACKEND CONTAINER FILESYSTEM
================================================================================

docker exec -it scanium-backend sh -lc '
pwd
ls -la
ls -la /app
'

Expected directories:
- /app
- /app/dist
- /app/node_modules
- /app/prisma

================================================================================
3) APPLY PRISMA MIGRATIONS ON NAS (DEBIAN + OPENSSL)
================================================================================

Creates required tables:
- CatalogBrandWikidataMap
- CatalogModel

docker run --rm -it \
  --network backend_scanium-network \
  -v "$PWD":/work \
  -w /work/backend \
  -e DATABASE_URL="postgresql://scanium:scanium@postgres:5432/scanium" \
  node:20-bullseye-slim \
  bash -lc "
    apt-get update &&
    apt-get install -y --no-install-recommends openssl ca-certificates &&
    rm -rf /var/lib/apt/lists/* &&
    npm ci --legacy-peer-deps &&
    npx prisma migrate deploy
  "

Expected:
- Datasource postgres:5432
- No pending migrations OR successful application

================================================================================
4) VERIFY TABLES WERE CREATED
================================================================================

docker exec -it scanium-postgres sh -lc '
psql -U scanium -d scanium -c "\dt"
'

Expected tables include:
- CatalogBrandWikidataMap
- CatalogModel
- _prisma_migrations
- items, users, sessions, etc.

================================================================================
5) RUN WIKIDATA CATALOG SYNC (MODEL INGESTION)
================================================================================

Populates models per category/brand into Postgres.

docker run --rm -it \
  --network backend_scanium-network \
  -v "$PWD":/work \
  -w /work/backend \
  -e DATABASE_URL="postgresql://scanium:scanium@postgres:5432/scanium" \
  -e WIKIDATA_USER_AGENT="ScaniumBot/1.0 (contact: you@example.com)" \
  -e BRAND_CATALOG_PATH="/work/core-domainpack/src/main/res/raw/brands_catalog_bundle_v1.json" \
  -e SUBTYPE="electronics_phone" \
  node:20-bullseye-slim \
  bash -lc "
    apt-get update &&
    apt-get install -y --no-install-recommends openssl ca-certificates &&
    rm -rf /var/lib/apt/lists/* &&
    npm ci --legacy-peer-deps &&
    npx prisma generate &&
    npx tsx src/modules/catalog/wikidata/cli-sync.ts
  "

Expected JSON output:
{
  "ok": true,
  "result": {
    "subtype": "electronics_phone",
    "brands": 20,
    "modelsUpserted": 1558
  }
}

================================================================================
6) VERIFY INGESTED DATA
================================================================================

Count models per subtype:

docker exec -it scanium-postgres sh -lc '
psql -U scanium -d scanium -c "
SELECT subtype, count(*) 
FROM \"CatalogModel\" 
GROUP BY subtype;
"
'

Inspect top brands:

docker exec -it scanium-postgres sh -lc '
psql -U scanium -d scanium -c "
SELECT \"brandQid\", count(*) AS models
FROM \"CatalogModel\"
WHERE subtype='\''electronics_phone'\''
GROUP BY \"brandQid\"
ORDER BY models DESC
LIMIT 15;
"
'

================================================================================
7) BUILD & START BACKEND (DOCKER COMPOSE)
================================================================================

cd /volume1/docker/scanium/repo/backend

docker-compose up -d --build api

Verify health:

docker exec -it scanium-backend sh -lc '
wget -qO- http://127.0.0.1:8080/health || true
'

================================================================================
8) TEST CATALOG ENDPOINTS (INSIDE CONTAINER)
================================================================================

docker exec -it scanium-backend sh -lc '
wget -qO- http://127.0.0.1:8080/v1/catalog/electronics_phone/brands || true
'

docker exec -it scanium-backend sh -lc '
wget -qO- "http://127.0.0.1:8080/v1/catalog/electronics_phone/models?brand=Samsung" || true
'

================================================================================
9) CONFIRMED WORKING STATE
================================================================================

- Prisma migrations applied on NAS
- Postgres schema correct
- Wikidata sync successful
- CatalogBrandWikidataMap populated
- CatalogModel populated (1000+ models)
- Backend serving catalog endpoints
- Android app autocomplete working with live data

================================================================================
10) EXTENDING MODELS FOR A CATEGORY (PROCEDURE)
================================================================================

1. Ensure brand exists in brands_catalog_bundle_v1.json
2. Run Wikidata sync with SUBTYPE set to desired category
3. Verify data via SQL
4. No local DB, no manual inserts
5. Restart backend if needed

================================================================================
END OF DOCUMENT
================================================================================