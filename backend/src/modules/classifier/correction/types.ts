/**
 * Payload for submitting a classification correction.
 */
export type CorrectionPayload = {
  itemId: string;
  imageHash: string;
  predictedCategory: string | null;
  predictedConfidence?: number;
  correctedCategory: string;
  correctionMethod: 'tap_alternative' | 'manual_entry' | 'search';
  notes?: string;
  perceptionSnapshot?: Record<string, unknown>;
};

/**
 * Response after submitting a correction.
 */
export type CorrectionResponse = {
  success: boolean;
  correctionId: string;
  correlationId: string;
};

/**
 * Classification correction record.
 */
export type ClassificationCorrectionRecord = {
  id: string;
  userId: string | null;
  deviceId: string | null;
  imageHash: string;
  predictedCategory: string;
  predictedConfidence: number | null;
  correctedCategory: string;
  correctionMethod: string;
  notes: string | null;
  perceptionSnapshot: Record<string, unknown> | null;
  createdAt: Date;
};
