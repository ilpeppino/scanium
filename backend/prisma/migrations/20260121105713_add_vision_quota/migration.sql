-- CreateTable
CREATE TABLE "vision_quotas" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "date" DATE NOT NULL,
    "count" INTEGER NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "vision_quotas_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "vision_quotas_userId_idx" ON "vision_quotas"("userId");

-- CreateIndex
CREATE INDEX "vision_quotas_date_idx" ON "vision_quotas"("date");

-- CreateIndex
CREATE UNIQUE INDEX "vision_quotas_userId_date_key" ON "vision_quotas"("userId", "date");

-- AddForeignKey
ALTER TABLE "vision_quotas" ADD CONSTRAINT "vision_quotas_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
