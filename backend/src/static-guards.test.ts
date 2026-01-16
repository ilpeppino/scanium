/**
 * Static Source Code Guards
 *
 * Prevents hardcoded English utility strings from being introduced
 * in backend source code.
 *
 * These strings belong to Android resources for localization,
 * not in backend responses.
 */

import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync } from 'fs';
import { join, relative } from 'path';

// Prohibited phrases that should never appear in backend source
const PROHIBITED_PHRASES = [
  'Typical resale value',
  'Based on',
  'market conditions',
];

/**
 * Recursively find all TypeScript source files in a directory.
 * Excludes files that contain internal prompts (like AI/LLM instructions).
 */
function findTypeScriptFiles(dir: string, fileList: string[] = []): string[] {
  // Files/directories to exclude - these contain internal AI prompts, not client responses
  const excludePatterns = [
    'pipeline.ts', // Contains OpenAI prompts
    'prompt', // Files related to prompts
  ];

  try {
    const files = readdirSync(dir, { withFileTypes: true });

    for (const file of files) {
      // Skip node_modules, dist, build, .git, etc.
      if (['node_modules', 'dist', 'build', '.git', '.vscode', 'coverage'].includes(file.name)) {
        continue;
      }

      // Skip files that are known to contain internal prompts
      if (excludePatterns.some((pattern) => file.name.includes(pattern))) {
        continue;
      }

      const fullPath = join(dir, file.name);

      if (file.isDirectory()) {
        findTypeScriptFiles(fullPath, fileList);
      } else if (file.name.endsWith('.ts') && !file.name.endsWith('.test.ts') && !file.name.endsWith('.spec.ts')) {
        fileList.push(fullPath);
      }
    }
  } catch (error) {
    // Silently skip directories we can't read
  }

  return fileList;
}

describe('Static Source Code Guards', () => {
  describe('No hardcoded English utility strings in backend source', () => {
    it('should not contain prohibited phrases in pricing module', () => {
      const srcDir = join(process.cwd(), 'src', 'modules', 'pricing');
      const files = findTypeScriptFiles(srcDir);

      expect(files.length).toBeGreaterThan(0);

      const violations: Array<{ file: string; phrase: string; line: number }> = [];

      for (const file of files) {
        try {
          const content = readFileSync(file, 'utf-8');
          const lines = content.split('\n');

          for (let i = 0; i < lines.length; i++) {
            const line = lines[i];

            for (const phrase of PROHIBITED_PHRASES) {
              if (line.includes(phrase)) {
                violations.push({
                  file: relative(process.cwd(), file),
                  phrase,
                  line: i + 1,
                });
              }
            }
          }
        } catch (error) {
          // Silently skip files we can't read
        }
      }

      if (violations.length > 0) {
        const message = violations
          .map((v) => `${v.file}:${v.line} - "${v.phrase}"`)
          .join('\n');
        throw new Error(`Found prohibited phrases in backend source:\n${message}`);
      }

      expect(violations).toHaveLength(0);
    });

    it('should not contain prohibited phrases in items module', () => {
      const srcDir = join(process.cwd(), 'src', 'modules', 'items');
      const files = findTypeScriptFiles(srcDir);

      const violations: Array<{ file: string; phrase: string; line: number }> = [];

      for (const file of files) {
        try {
          const content = readFileSync(file, 'utf-8');
          const lines = content.split('\n');

          for (let i = 0; i < lines.length; i++) {
            const line = lines[i];

            for (const phrase of PROHIBITED_PHRASES) {
              if (line.includes(phrase)) {
                violations.push({
                  file: relative(process.cwd(), file),
                  phrase,
                  line: i + 1,
                });
              }
            }
          }
        } catch (error) {
          // Silently skip files we can't read
        }
      }

      expect(violations).toHaveLength(0);
    });

    it('should not contain prohibited phrases in any backend module', () => {
      const srcDir = join(process.cwd(), 'src', 'modules');
      const files = findTypeScriptFiles(srcDir);

      expect(files.length).toBeGreaterThan(0);

      const violations: Array<{ file: string; phrase: string; line: number }> = [];

      for (const file of files) {
        try {
          const content = readFileSync(file, 'utf-8');
          const lines = content.split('\n');

          for (let i = 0; i < lines.length; i++) {
            const line = lines[i];

            for (const phrase of PROHIBITED_PHRASES) {
              if (line.includes(phrase)) {
                violations.push({
                  file: relative(process.cwd(), file),
                  phrase,
                  line: i + 1,
                });
              }
            }
          }
        } catch (error) {
          // Silently skip files we can't read
        }
      }

      if (violations.length > 0) {
        const message = violations
          .map((v) => `${v.file}:${v.line} - "${v.phrase}"`)
          .join('\n');
        throw new Error(`Found prohibited phrases in backend modules:\n${message}`);
      }

      expect(violations).toHaveLength(0);
    });

    it('should not contain prohibited phrases in main app and routing code', () => {
      const srcDir = join(process.cwd(), 'src');
      const mainFiles = ['app.ts', 'main.ts'].map((f) => join(srcDir, f));

      const violations: Array<{ file: string; phrase: string; line: number }> = [];

      for (const file of mainFiles) {
        try {
          const content = readFileSync(file, 'utf-8');
          const lines = content.split('\n');

          for (let i = 0; i < lines.length; i++) {
            const line = lines[i];

            for (const phrase of PROHIBITED_PHRASES) {
              if (line.includes(phrase)) {
                violations.push({
                  file: relative(process.cwd(), file),
                  phrase,
                  line: i + 1,
                });
              }
            }
          }
        } catch (error) {
          // Silently skip files we can't read
        }
      }

      expect(violations).toHaveLength(0);
    });
  });
});
