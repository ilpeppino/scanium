import crypto from 'node:crypto';
import { ProviderResponse, ClassificationRequest } from '../types.js';

const MOCK_LABELS = [
  'chair',
  'table',
  'sofa',
  'lamp',
  'decor',
  'bed',
  'cabinet',
];

export type MockClassifierOptions = {
  seed: string;
};

export class MockClassifier {
  constructor(private readonly options: MockClassifierOptions) {}

  async classify(request: ClassificationRequest): Promise<ProviderResponse> {
    const signature = crypto
      .createHash('sha1')
      .update(`${this.options.seed}-${request.requestId}-${request.fileName}`)
      .digest('hex');

    const bucket = parseInt(signature.slice(0, 4), 16);
    const labelIndex = bucket % MOCK_LABELS.length;
    const score = 0.35 + (bucket % 50) / 100; // 0.35 - 0.84

    return {
      provider: 'mock',
      signals: {
        labels: [
          {
            description: MOCK_LABELS[labelIndex],
            score: Number(Math.min(0.95, score).toFixed(3)),
          },
        ],
      },
      visionMs: 1,
    };
  }
}
