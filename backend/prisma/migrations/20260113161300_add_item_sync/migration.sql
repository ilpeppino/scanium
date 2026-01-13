-- Phase E: Add Item model for multi-device sync
-- Create items table with user scoping and sync fields

CREATE TABLE "items" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "deletedAt" TIMESTAMP(3),

    -- Core item metadata
    "title" TEXT,
    "description" TEXT,
    "category" TEXT,
    "confidence" DOUBLE PRECISION,
    "priceEstimateLow" DOUBLE PRECISION,
    "priceEstimateHigh" DOUBLE PRECISION,
    "userPriceCents" BIGINT,
    "condition" TEXT,

    -- Attributes (JSON fields)
    "attributesJson" JSONB,
    "detectedAttributesJson" JSONB,
    "visionAttributesJson" JSONB,
    "enrichmentStatusJson" JSONB,

    -- Quality metrics
    "completenessScore" INTEGER NOT NULL DEFAULT 0,
    "missingAttributesJson" JSONB,
    "capturedShotTypesJson" JSONB,
    "isReadyForListing" BOOLEAN NOT NULL DEFAULT false,
    "lastEnrichedAt" TIMESTAMP(3),

    -- Export Assistant fields
    "exportTitle" TEXT,
    "exportDescription" TEXT,
    "exportBulletsJson" JSONB,
    "exportGeneratedAt" TIMESTAMP(3),
    "exportFromCache" BOOLEAN NOT NULL DEFAULT false,
    "exportModel" TEXT,
    "exportConfidenceTier" TEXT,

    -- Classification
    "classificationStatus" TEXT NOT NULL DEFAULT 'PENDING',
    "domainCategoryId" TEXT,
    "classificationErrorMessage" TEXT,
    "classificationRequestId" TEXT,

    -- Photo metadata (not actual images)
    "photosMetadataJson" JSONB,

    -- Multi-object scanning
    "attributesSummaryText" TEXT,
    "summaryTextUserEdited" BOOLEAN NOT NULL DEFAULT false,
    "sourcePhotoId" TEXT,

    -- Listing associations
    "listingStatus" TEXT NOT NULL DEFAULT 'NOT_LISTED',
    "listingId" TEXT,
    "listingUrl" TEXT,

    -- OCR/barcode data
    "recognizedText" TEXT,
    "barcodeValue" TEXT,
    "labelText" TEXT,

    -- Sync metadata
    "syncVersion" INTEGER NOT NULL DEFAULT 1,
    "clientUpdatedAt" TIMESTAMP(3),

    CONSTRAINT "items_pkey" PRIMARY KEY ("id")
);

-- Create indexes for efficient queries
CREATE INDEX "items_userId_idx" ON "items"("userId");
CREATE INDEX "items_userId_updatedAt_idx" ON "items"("userId", "updatedAt");
CREATE INDEX "items_userId_deletedAt_idx" ON "items"("userId", "deletedAt");

-- Add foreign key constraint with cascade delete
ALTER TABLE "items" ADD CONSTRAINT "items_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;
