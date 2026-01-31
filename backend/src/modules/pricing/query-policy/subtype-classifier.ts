import { SUBTYPE_CLASS_PATTERNS } from './subtype-class-map.js';
import { SubtypeClass } from './types.js';

export class SubtypeClassifier {
  private readonly patterns: Array<{ class: SubtypeClass; regexes: RegExp[] }>;

  constructor(config: typeof SUBTYPE_CLASS_PATTERNS = SUBTYPE_CLASS_PATTERNS) {
    this.patterns = config.map((entry) => ({
      class: entry.class,
      regexes: entry.patterns.map((pattern) => new RegExp(pattern, 'i')),
    }));
  }

  classify(subtype: string | null | undefined): SubtypeClass {
    const normalized = subtype?.trim().toLowerCase();
    if (!normalized) return 'other';

    for (const entry of this.patterns) {
      if (entry.regexes.some((regex) => regex.test(normalized))) {
        return entry.class;
      }
    }

    return 'other';
  }
}
