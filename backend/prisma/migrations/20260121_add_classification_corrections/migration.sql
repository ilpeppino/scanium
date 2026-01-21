-- CreateTable
CREATE TABLE "classification_corrections" (
    "id" TEXT NOT NULL,
    "userId" TEXT,
    "deviceId" TEXT,
    "imageHash" TEXT NOT NULL,
    "predictedCategory" TEXT NOT NULL,
    "predictedConfidence" DOUBLE PRECISION,
    "correctedCategory" TEXT NOT NULL,
    "correctionMethod" TEXT NOT NULL,
    "notes" TEXT,
    "perceptionSnapshot" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "classification_corrections_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "classification_corrections_userId_imageHash_idx" ON "classification_corrections"("userId", "imageHash");

-- CreateIndex
CREATE INDEX "classification_corrections_deviceId_imageHash_idx" ON "classification_corrections"("deviceId", "imageHash");

-- CreateIndex
CREATE INDEX "classification_corrections_predictedCategory_idx" ON "classification_corrections"("predictedCategory");

-- CreateIndex
CREATE INDEX "classification_corrections_correctedCategory_idx" ON "classification_corrections"("correctedCategory");
