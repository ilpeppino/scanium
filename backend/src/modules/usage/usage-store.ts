type UsageKey = string;

type UsageCounters = {
  date: string;
  apiKey: string;
  classificationRequests: number;
  assistantRequests: number;
  classificationErrors: number;
  assistantErrors: number;
  classifierFeature: Record<string, number>;
};

function todayKey(): string {
  const now = new Date();
  return now.toISOString().slice(0, 10);
}

function makeKey(apiKey: string, date: string): UsageKey {
  return `${date}:${apiKey}`;
}

function formatFeatureKey(feature: string | string[]): string {
  if (Array.isArray(feature)) {
    return [...feature].sort().join('+');
  }
  return feature;
}

export class UsageStore {
  private readonly store = new Map<UsageKey, UsageCounters>();

  recordClassification(apiKey: string, feature: string | string[], isError: boolean) {
    const counters = this.getCounters(apiKey);
    counters.classificationRequests += 1;
    const featureKey = formatFeatureKey(feature);
    counters.classifierFeature[featureKey] = (counters.classifierFeature[featureKey] ?? 0) + 1;
    if (isError) {
      counters.classificationErrors += 1;
    }
  }

  recordAssistant(apiKey: string, isError: boolean) {
    const counters = this.getCounters(apiKey);
    counters.assistantRequests += 1;
    if (isError) {
      counters.assistantErrors += 1;
    }
  }

  snapshot(): UsageCounters[] {
    return [...this.store.values()].map((entry) => ({
      ...entry,
      classifierFeature: { ...entry.classifierFeature },
    }));
  }

  private getCounters(apiKey: string): UsageCounters {
    const date = todayKey();
    const key = makeKey(apiKey, date);
    const existing = this.store.get(key);
    if (existing) return existing;
    const fresh: UsageCounters = {
      date,
      apiKey,
      classificationRequests: 0,
      assistantRequests: 0,
      classificationErrors: 0,
      assistantErrors: 0,
      classifierFeature: {},
    };
    this.store.set(key, fresh);
    return fresh;
  }
}

export const usageStore = new UsageStore();
