/**
 * Maps eBay category paths to Scanium ItemCategory enums
 * Uses configurable pattern-based rules from YAML file
 */

import { readFileSync } from 'node:fs';
import { parse as parseYaml } from 'yaml';
import type { ScaniumItemCategory } from '../types/domain-pack.js';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

interface MappingRule {
  pattern: string;
  itemCategoryName: ScaniumItemCategory;
  description: string;
}

interface MappingRules {
  rules: MappingRule[];
}

export class CategoryMapper {
  private rules: MappingRule[];
  private compiledPatterns: { regex: RegExp; rule: MappingRule }[];

  constructor(rulesPath?: string) {
    const defaultRulesPath = join(__dirname, '../../category-mapping-rules.yaml');
    const path = rulesPath || defaultRulesPath;

    try {
      const yamlContent = readFileSync(path, 'utf-8');
      const parsed = parseYaml(yamlContent) as MappingRules;
      this.rules = parsed.rules;

      // Pre-compile regex patterns for performance
      this.compiledPatterns = this.rules.map((rule) => ({
        regex: new RegExp(rule.pattern, 'i'), // case-insensitive
        rule,
      }));
    } catch (error) {
      throw new Error(
        `Failed to load category mapping rules from ${path}: ${
          error instanceof Error ? error.message : String(error)
        }`
      );
    }
  }

  /**
   * Map an eBay category path to a Scanium ItemCategory
   * @param categoryPath Array of category names from root to leaf
   * @returns Scanium ItemCategory enum value
   */
  mapCategoryPath(categoryPath: string[]): ScaniumItemCategory {
    // Join the category path with " > " for pattern matching
    const pathString = categoryPath.join(' > ');

    // Also try matching just the root category
    const rootCategory = categoryPath[0] || '';

    // Try to match the full path first, then root category
    for (const { regex, rule } of this.compiledPatterns) {
      if (regex.test(pathString) || regex.test(rootCategory)) {
        return rule.itemCategoryName;
      }
    }

    // Should never reach here if rules have a catch-all pattern
    console.warn(`No mapping found for category path: ${pathString}`);
    return 'OTHER';
  }

  /**
   * Get all mapping rules (for debugging/testing)
   */
  getRules(): MappingRule[] {
    return [...this.rules];
  }
}
