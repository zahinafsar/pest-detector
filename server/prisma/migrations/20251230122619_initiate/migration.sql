-- CreateTable
CREATE TABLE "public"."DetectionReport" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "insectName" TEXT NOT NULL,
    "insectType" TEXT NOT NULL,
    "confidence" DOUBLE PRECISION NOT NULL,
    "imagePath" TEXT,
    "location" TEXT,
    "notes" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "DetectionReport_pkey" PRIMARY KEY ("id")
);

-- AddForeignKey
ALTER TABLE "public"."DetectionReport" ADD CONSTRAINT "DetectionReport_userId_fkey" FOREIGN KEY ("userId") REFERENCES "public"."User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
