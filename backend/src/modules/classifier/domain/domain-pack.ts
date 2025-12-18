import path from 'node:path';
import fs from 'node:fs';
import { createRequire } from 'module';

const require = createRequire(import.meta.url);
const bundledPack = require('./home-resale.json') as DomainPack;

export type DomainCategory = {
  id: string;
  label: string;
  tokens: string[];
  attributes?: Record<string, string>;
};

export type DomainPack = {
  id: string;
  name: string;
  threshold?: number;
  categories: DomainCategory[];
};

const packCache = new Map<string, DomainPack>();

export function loadDomainPack(packPath: string): DomainPack {
  const absolutePath = resolvePackPath(packPath);

  const cached = packCache.get(absolutePath);
  if (cached) return cached;

  let parsed: DomainPack;
  if (fs.existsSync(absolutePath)) {
    const contents = fs.readFileSync(absolutePath, 'utf-8');
    parsed = JSON.parse(contents) as DomainPack;
  } else {
    parsed = bundledPack;
  }

  if (!parsed.id || !parsed.categories?.length) {
    throw new Error(`Invalid domain pack at ${absolutePath}`);
  }

  packCache.set(absolutePath, parsed);
  return parsed;
}

export function clearDomainPackCache() {
  packCache.clear();
}

function resolvePackPath(packPath: string): string {
  const directPath = path.isAbsolute(packPath)
    ? packPath
    : path.join(process.cwd(), packPath);

  if (fs.existsSync(directPath)) {
    return directPath;
  }

  // Fallback to dist build relative lookup (tsc does not copy JSON by default)
  const distPath = path.join(process.cwd(), 'dist', packPath.replace(/^src[\\/]/, ''));
  if (fs.existsSync(distPath)) {
    return distPath;
  }

  // Return original path; caller will fallback to bundled default pack.
  return directPath;
}
