-- Phase C: Add refresh token support to sessions table

ALTER TABLE "sessions" ADD COLUMN "refreshTokenHash" TEXT;
ALTER TABLE "sessions" ADD COLUMN "refreshTokenExpiresAt" TIMESTAMP(3);

-- Create unique index on refreshTokenHash (nullable)
CREATE UNIQUE INDEX "sessions_refreshTokenHash_key" ON "sessions"("refreshTokenHash") WHERE "refreshTokenHash" IS NOT NULL;
