/**
 * Dominant Color Extractor
 *
 * Lightweight server-side color extraction using Sharp.
 * No ML required - uses simple quantization/clustering.
 */

import sharp from 'sharp';
import { DominantColor } from './types.js';

/**
 * Basic color names mapped to RGB ranges.
 * Used to convert RGB values to human-readable color names.
 */
const COLOR_DEFINITIONS: Array<{
  name: string;
  hueRange?: [number, number];
  satRange?: [number, number];
  lightRange?: [number, number];
}> = [
  // Achromatic colors (based on saturation and lightness)
  { name: 'white', satRange: [0, 0.15], lightRange: [0.85, 1.0] },
  { name: 'black', satRange: [0, 1], lightRange: [0, 0.15] },
  { name: 'gray', satRange: [0, 0.15], lightRange: [0.15, 0.85] },
  // Chromatic colors (based on hue)
  { name: 'red', hueRange: [350, 360], satRange: [0.15, 1], lightRange: [0.15, 0.85] },
  { name: 'red', hueRange: [0, 10], satRange: [0.15, 1], lightRange: [0.15, 0.85] },
  { name: 'orange', hueRange: [10, 40], satRange: [0.15, 1], lightRange: [0.15, 0.85] },
  { name: 'yellow', hueRange: [40, 70], satRange: [0.15, 1], lightRange: [0.15, 0.85] },
  { name: 'green', hueRange: [70, 170], satRange: [0.15, 1], lightRange: [0.15, 0.85] },
  { name: 'blue', hueRange: [170, 260], satRange: [0.15, 1], lightRange: [0.15, 0.85] },
  { name: 'purple', hueRange: [260, 290], satRange: [0.15, 1], lightRange: [0.15, 0.85] },
  { name: 'pink', hueRange: [290, 350], satRange: [0.15, 1], lightRange: [0.5, 0.85] },
  { name: 'brown', hueRange: [10, 40], satRange: [0.15, 0.6], lightRange: [0.15, 0.5] },
];

/**
 * Convert RGB to HSL color space.
 */
function rgbToHsl(r: number, g: number, b: number): { h: number; s: number; l: number } {
  const rNorm = r / 255;
  const gNorm = g / 255;
  const bNorm = b / 255;

  const max = Math.max(rNorm, gNorm, bNorm);
  const min = Math.min(rNorm, gNorm, bNorm);
  const l = (max + min) / 2;

  if (max === min) {
    return { h: 0, s: 0, l };
  }

  const d = max - min;
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);

  let h = 0;
  if (max === rNorm) {
    h = ((gNorm - bNorm) / d + (gNorm < bNorm ? 6 : 0)) / 6;
  } else if (max === gNorm) {
    h = ((bNorm - rNorm) / d + 2) / 6;
  } else {
    h = ((rNorm - gNorm) / d + 4) / 6;
  }

  return { h: h * 360, s, l };
}

/**
 * Get the basic color name for an RGB value.
 */
export function getColorName(r: number, g: number, b: number): string {
  const { h, s, l } = rgbToHsl(r, g, b);

  for (const def of COLOR_DEFINITIONS) {
    const hueMatch =
      !def.hueRange ||
      (h >= def.hueRange[0] && h < def.hueRange[1]);
    const satMatch =
      !def.satRange ||
      (s >= def.satRange[0] && s <= def.satRange[1]);
    const lightMatch =
      !def.lightRange ||
      (l >= def.lightRange[0] && l <= def.lightRange[1]);

    if (hueMatch && satMatch && lightMatch) {
      return def.name;
    }
  }

  return 'gray'; // Default fallback
}

/**
 * Convert RGB to hex string.
 */
export function rgbToHex(r: number, g: number, b: number): string {
  return '***REMOVED***' + [r, g, b].map((x) => x.toString(16).padStart(2, '0')).join('').toUpperCase();
}

/**
 * Calculate Euclidean distance between two RGB colors.
 */
function colorDistance(
  c1: { r: number; g: number; b: number },
  c2: { r: number; g: number; b: number }
): number {
  return Math.sqrt(
    Math.pow(c1.r - c2.r, 2) +
      Math.pow(c1.g - c2.g, 2) +
      Math.pow(c1.b - c2.b, 2)
  );
}

/**
 * Simple k-means clustering for color quantization.
 * CPU/time bounded for safety.
 */
function kMeansColors(
  pixels: Array<{ r: number; g: number; b: number }>,
  k: number,
  maxIterations: number = 10
): Array<{ r: number; g: number; b: number; count: number }> {
  if (pixels.length === 0) return [];
  if (pixels.length <= k) {
    return pixels.map((p) => ({ ...p, count: 1 }));
  }

  // Initialize centroids by sampling evenly spaced pixels
  const step = Math.floor(pixels.length / k);
  let centroids = Array.from({ length: k }, (_, i) => ({
    r: pixels[i * step].r,
    g: pixels[i * step].g,
    b: pixels[i * step].b,
  }));

  for (let iter = 0; iter < maxIterations; iter++) {
    // Assign pixels to nearest centroid
    const clusters: Array<Array<{ r: number; g: number; b: number }>> = Array.from(
      { length: k },
      () => []
    );

    for (const pixel of pixels) {
      let minDist = Infinity;
      let minIdx = 0;
      for (let i = 0; i < centroids.length; i++) {
        const dist = colorDistance(pixel, centroids[i]);
        if (dist < minDist) {
          minDist = dist;
          minIdx = i;
        }
      }
      clusters[minIdx].push(pixel);
    }

    // Update centroids
    const newCentroids = clusters.map((cluster, i) => {
      if (cluster.length === 0) return centroids[i];
      const sum = cluster.reduce(
        (acc, p) => ({ r: acc.r + p.r, g: acc.g + p.g, b: acc.b + p.b }),
        { r: 0, g: 0, b: 0 }
      );
      return {
        r: Math.round(sum.r / cluster.length),
        g: Math.round(sum.g / cluster.length),
        b: Math.round(sum.b / cluster.length),
      };
    });

    // Check for convergence
    let converged = true;
    for (let i = 0; i < k; i++) {
      if (colorDistance(centroids[i], newCentroids[i]) > 1) {
        converged = false;
        break;
      }
    }

    centroids = newCentroids;
    if (converged) break;
  }

  // Count pixels in each cluster for percentages
  const clusterCounts = centroids.map(() => 0);
  for (const pixel of pixels) {
    let minDist = Infinity;
    let minIdx = 0;
    for (let i = 0; i < centroids.length; i++) {
      const dist = colorDistance(pixel, centroids[i]);
      if (dist < minDist) {
        minDist = dist;
        minIdx = i;
      }
    }
    clusterCounts[minIdx]++;
  }

  return centroids.map((c, i) => ({ ...c, count: clusterCounts[i] }));
}

/**
 * Merge similar colors to reduce noise.
 */
function mergeByColorName(
  colors: Array<{ r: number; g: number; b: number; count: number }>
): Array<{ name: string; r: number; g: number; b: number; count: number }> {
  const byName = new Map<string, { r: number; g: number; b: number; count: number }>();

  for (const color of colors) {
    const name = getColorName(color.r, color.g, color.b);
    const existing = byName.get(name);
    if (existing) {
      // Weighted average
      const totalCount = existing.count + color.count;
      byName.set(name, {
        r: Math.round((existing.r * existing.count + color.r * color.count) / totalCount),
        g: Math.round((existing.g * existing.count + color.g * color.count) / totalCount),
        b: Math.round((existing.b * existing.count + color.b * color.count) / totalCount),
        count: totalCount,
      });
    } else {
      byName.set(name, { ...color });
    }
  }

  return Array.from(byName.entries()).map(([name, color]) => ({
    name,
    ...color,
  }));
}

export type ColorExtractionOptions = {
  /** Target size for downscaling (default: 64) */
  targetSize?: number;
  /** Number of colors to extract (default: 5) */
  numColors?: number;
  /** Maximum processing time in ms (default: 500) */
  timeoutMs?: number;
};

/**
 * Extract dominant colors from an image buffer.
 *
 * @param buffer - Image buffer (JPEG or PNG)
 * @param options - Extraction options
 * @returns Array of dominant colors with percentages
 */
export async function extractDominantColors(
  buffer: Buffer,
  options: ColorExtractionOptions = {}
): Promise<{ colors: DominantColor[]; timingMs: number }> {
  const startTime = performance.now();
  const { targetSize = 64, numColors = 5, timeoutMs = 500 } = options;

  try {
    // Downscale aggressively for performance
    const { data } = await Promise.race([
      sharp(buffer)
        .resize(targetSize, targetSize, { fit: 'inside' })
        .removeAlpha()
        .raw()
        .toBuffer({ resolveWithObject: true }),
      new Promise<never>((_, reject) =>
        setTimeout(() => reject(new Error('Color extraction timeout')), timeoutMs)
      ),
    ]);

    // Convert raw buffer to pixel array
    const pixels: Array<{ r: number; g: number; b: number }> = [];
    for (let i = 0; i < data.length; i += 3) {
      pixels.push({
        r: data[i],
        g: data[i + 1],
        b: data[i + 2],
      });
    }

    // Run k-means clustering
    const clusteredColors = kMeansColors(pixels, numColors * 2, 10);

    // Merge by color name
    const mergedColors = mergeByColorName(clusteredColors);

    // Sort by count (descending) and take top N
    const totalPixels = pixels.length;
    const sortedColors = mergedColors
      .sort((a, b) => b.count - a.count)
      .slice(0, numColors);

    // Convert to DominantColor format
    const dominantColors: DominantColor[] = sortedColors.map((c) => ({
      name: c.name,
      rgbHex: rgbToHex(c.r, c.g, c.b),
      pct: Math.round((c.count / totalPixels) * 100),
    }));

    const timingMs = Math.round(performance.now() - startTime);
    return { colors: dominantColors, timingMs };
  } catch (error) {
    const timingMs = Math.round(performance.now() - startTime);
    // Return empty on timeout or error
    return { colors: [], timingMs };
  }
}
