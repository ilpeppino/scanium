import { describe, it, expect } from 'vitest';
import { MockClassifier } from './mock-classifier.js';

const baseRequest = {
  requestId: 'req-123',
  buffer: Buffer.from('123'),
  contentType: 'image/jpeg',
  fileName: 'fixture.jpg',
  domainPackId: 'home_resale',
};

describe('MockClassifier', () => {
  it('produces deterministic labels for the same seed/input', async () => {
    const classifier = new MockClassifier({ seed: 'seed' });

    const first = await classifier.classify(baseRequest);
    const second = await classifier.classify(baseRequest);

    expect(first.signals.labels[0].description).toEqual(
      second.signals.labels[0].description
    );
    expect(first.signals.labels[0].score).toEqual(
      second.signals.labels[0].score
    );
  });
});
