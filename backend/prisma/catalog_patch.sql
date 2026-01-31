-- DropForeignKey
ALTER TABLE "vision_quotas" DROP CONSTRAINT "vision_quotas_userid_fkey";

-- DropIndex
DROP INDEX "vision_quotas_userid_date_key";

-- DropIndex
DROP INDEX "vision_quotas_userid_idx";

-- AlterTable
ALTER TABLE "vision_quotas" DROP COLUMN "createdat",
DROP COLUMN "updatedat",
DROP COLUMN "userid",
ADD COLUMN     "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN     "updatedAt" TIMESTAMP(3) NOT NULL,
ADD COLUMN     "userId" TEXT NOT NULL;

-- CreateTable
CREATE TABLE "CatalogBrandWikidataMap" (
    "id" BIGSERIAL NOT NULL,
    "subtype" TEXT NOT NULL,
    "brandString" TEXT NOT NULL,
    "wikidataQid" TEXT NOT NULL,
    "qidType" TEXT NOT NULL,
    "confidence" TEXT NOT NULL,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "CatalogBrandWikidataMap_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "CatalogModel" (
    "id" BIGSERIAL NOT NULL,
    "subtype" TEXT NOT NULL,
    "brandQid" TEXT NOT NULL,
    "modelQid" TEXT NOT NULL,
    "modelLabel" TEXT NOT NULL,
    "aliases" JSONB NOT NULL DEFAULT '[]',
    "source" TEXT NOT NULL,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "CatalogModel_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "CatalogBrandWikidataMap_subtype_brandString_idx" ON "CatalogBrandWikidataMap"("subtype", "brandString");

-- CreateIndex
CREATE INDEX "CatalogBrandWikidataMap_subtype_wikidataQid_idx" ON "CatalogBrandWikidataMap"("subtype", "wikidataQid");

-- CreateIndex
CREATE UNIQUE INDEX "CatalogBrandWikidataMap_subtype_brandString_key" ON "CatalogBrandWikidataMap"("subtype", "brandString");

-- CreateIndex
CREATE INDEX "CatalogModel_subtype_brandQid_modelLabel_idx" ON "CatalogModel"("subtype", "brandQid", "modelLabel");

-- CreateIndex
CREATE UNIQUE INDEX "CatalogModel_subtype_brandQid_modelQid_key" ON "CatalogModel"("subtype", "brandQid", "modelQid");

-- CreateIndex
CREATE UNIQUE INDEX "sessions_refreshTokenHash_key" ON "sessions"("refreshTokenHash");

-- CreateIndex
CREATE INDEX "vision_quotas_userId_idx" ON "vision_quotas"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "vision_quotas_userId_date_key" ON "vision_quotas"("userId", "date");

-- AddForeignKey
ALTER TABLE "vision_quotas" ADD CONSTRAINT "vision_quotas_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

