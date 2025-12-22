export type VisionFeature = 'LABEL_DETECTION' | 'OBJECT_LOCALIZATION';

export type ClassificationHints = Record<string, unknown>;

export type ClassificationRequest = {
  requestId: string;
  correlationId: string;
  imageHash: string;
  buffer: Buffer;
  contentType: string;
  fileName: string;
  domainPackId: string;
  hints?: ClassificationHints;
};

export type VisionLabel = {
  description: string;
  score: number;
};

export type ClassificationSignals = {
  labels: VisionLabel[];
};

export type ProviderResponse = {
  provider: 'google-vision' | 'mock';
  signals: ClassificationSignals;
  visionMs?: number;
};

export type ClassificationResult = {
  requestId: string;
  correlationId: string;
  domainPackId: string;
  domainCategoryId: string | null;
  confidence: number | null;
  label?: string | null;
  attributes: Record<string, string>;
  provider: ProviderResponse['provider'];
  providerUnavailable?: boolean;
  cacheHit?: boolean;
  timingsMs: {
    total: number;
    vision?: number;
    mapping?: number;
  };
};
