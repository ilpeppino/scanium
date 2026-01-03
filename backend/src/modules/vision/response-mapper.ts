import { protos } from '@google-cloud/vision';
import { DominantColor, LabelHint, LogoHint, OcrSnippet } from './types.js';
import { getColorName, rgbToHex } from './color-extractor.js';

export type VisionResponseMappingOptions = {
  enableOcr: boolean;
  enableLabels: boolean;
  enableLogos: boolean;
  enableColors: boolean;
  maxOcrSnippets: number;
  maxLabelHints: number;
  maxLogoHints: number;
  maxColors: number;
  ocrMode: 'TEXT_DETECTION' | 'DOCUMENT_TEXT_DETECTION';
};

export type VisionResponseMappingConfig = {
  maxOcrSnippetLength: number;
  minOcrConfidence: number;
  minLabelConfidence: number;
  minLogoConfidence: number;
};

export type VisionResponseFacts = {
  dominantColors: DominantColor[];
  ocrSnippets: OcrSnippet[];
  labelHints: LabelHint[];
  logoHints: LogoHint[];
};

function normalizeOcrText(text: string, maxLength: number): string {
  return text
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, maxLength);
}

function extractOcrSnippetsFromText(
  text: string,
  options: { maxSnippets: number; maxLength: number }
): OcrSnippet[] {
  if (!text) return [];

  const lines = text
    .split('\n')
    .map((line) => normalizeOcrText(line, options.maxLength))
    .filter((line) => line.length >= 3);

  return lines.slice(0, options.maxSnippets).map((line) => ({
    text: line,
    confidence: 0.8,
  }));
}

function extractOcrSnippets(
  textAnnotations: protos.google.cloud.vision.v1.IEntityAnnotation[] | null | undefined,
  options: { maxSnippets: number; maxLength: number; minConfidence: number }
): OcrSnippet[] {
  if (!textAnnotations || textAnnotations.length === 0) {
    return [];
  }

  const snippets: OcrSnippet[] = [];

  for (let i = 1; i < textAnnotations.length && snippets.length < options.maxSnippets; i++) {
    const annotation = textAnnotations[i];
    const text = annotation.description?.trim();

    if (!text || text.length < 2) continue;
    if (text.length < 3 && /^\d+$/.test(text)) continue;

    const confidence = annotation.score ?? 0.8;
    if (confidence < options.minConfidence) continue;

    const normalizedText = normalizeOcrText(text, options.maxLength);
    if (normalizedText.length >= 2) {
      snippets.push({
        text: normalizedText,
        confidence: Math.round(confidence * 100) / 100,
      });
    }
  }

  if (snippets.length < 3 && textAnnotations[0]?.description) {
    const fullText = textAnnotations[0].description;
    const lines = fullText
      .split('\n')
      .map((line) => normalizeOcrText(line, options.maxLength))
      .filter((line) => line.length >= 3);

    for (const line of lines) {
      if (snippets.length >= options.maxSnippets) break;
      if (!snippets.some((s) => s.text.includes(line) || line.includes(s.text))) {
        snippets.push({ text: line, confidence: 0.8 });
      }
    }
  }

  return snippets.slice(0, options.maxSnippets);
}

function extractLabelHints(
  labelAnnotations: protos.google.cloud.vision.v1.IEntityAnnotation[] | null | undefined,
  options: { maxLabels: number; minConfidence: number }
): LabelHint[] {
  if (!labelAnnotations) return [];

  return labelAnnotations
    .filter((label) => (label.score ?? 0) >= options.minConfidence)
    .slice(0, options.maxLabels)
    .map((label) => ({
      label: label.description ?? 'unknown',
      score: Math.round((label.score ?? 0) * 100) / 100,
    }));
}

function extractLogoHints(
  logoAnnotations: protos.google.cloud.vision.v1.IEntityAnnotation[] | null | undefined,
  options: { maxLogos: number; minConfidence: number }
): LogoHint[] {
  if (!logoAnnotations) return [];

  return logoAnnotations
    .filter((logo) => (logo.score ?? 0) >= options.minConfidence)
    .slice(0, options.maxLogos)
    .map((logo) => ({
      brand: logo.description ?? 'unknown',
      score: Math.round((logo.score ?? 0) * 100) / 100,
    }));
}

function extractDominantColorsFromImageProperties(
  imageProperties: protos.google.cloud.vision.v1.IImageProperties | null | undefined,
  maxColors: number
): DominantColor[] {
  const colors = imageProperties?.dominantColors?.colors;
  if (!colors || colors.length === 0) return [];

  const mapped = colors
    .map((entry) => {
      const color = entry.color;
      if (!color) return null;
      const r = Math.round(color.red ?? 0);
      const g = Math.round(color.green ?? 0);
      const b = Math.round(color.blue ?? 0);
      const fraction = entry.pixelFraction ?? entry.score ?? 0;
      if (!Number.isFinite(fraction) || fraction <= 0) return null;
      const pct = Math.round(fraction * 100);
      return {
        name: getColorName(r, g, b),
        rgbHex: rgbToHex(r, g, b),
        pct,
      };
    })
    .filter((color): color is DominantColor => Boolean(color));

  return mapped
    .sort((a, b) => b.pct - a.pct)
    .slice(0, maxColors);
}

function deduplicateSnippets(snippets: OcrSnippet[], maxCount: number): OcrSnippet[] {
  const unique: OcrSnippet[] = [];

  for (const snippet of snippets) {
    const isDuplicate = unique.some(
      (u) =>
        u.text.toLowerCase() === snippet.text.toLowerCase() ||
        u.text.toLowerCase().includes(snippet.text.toLowerCase()) ||
        snippet.text.toLowerCase().includes(u.text.toLowerCase())
    );

    if (!isDuplicate) {
      unique.push(snippet);
    }
  }

  return unique
    .sort((a, b) => (b.confidence ?? 0) - (a.confidence ?? 0))
    .slice(0, maxCount);
}

function deduplicateLabels(labels: LabelHint[], maxCount: number): LabelHint[] {
  const seen = new Map<string, LabelHint>();

  for (const label of labels) {
    const key = label.label.toLowerCase();
    const existing = seen.get(key);
    if (!existing || label.score > existing.score) {
      seen.set(key, label);
    }
  }

  return Array.from(seen.values())
    .sort((a, b) => b.score - a.score)
    .slice(0, maxCount);
}

function deduplicateLogos(logos: LogoHint[], maxCount: number): LogoHint[] {
  const seen = new Map<string, LogoHint>();

  for (const logo of logos) {
    const key = logo.brand.toLowerCase();
    const existing = seen.get(key);
    if (!existing || logo.score > existing.score) {
      seen.set(key, logo);
    }
  }

  return Array.from(seen.values())
    .sort((a, b) => b.score - a.score)
    .slice(0, maxCount);
}

export function mergeColors(colors: DominantColor[], maxCount: number): DominantColor[] {
  const byName = new Map<string, DominantColor & { totalPct: number; count: number }>();

  for (const color of colors) {
    const existing = byName.get(color.name);
    if (existing) {
      existing.totalPct += color.pct;
      existing.count++;
      if (color.pct > existing.pct) {
        existing.rgbHex = color.rgbHex;
      }
    } else {
      byName.set(color.name, { ...color, totalPct: color.pct, count: 1 });
    }
  }

  return Array.from(byName.values())
    .map((c) => ({
      name: c.name,
      rgbHex: c.rgbHex,
      pct: Math.round(c.totalPct / c.count),
    }))
    .sort((a, b) => b.pct - a.pct)
    .slice(0, maxCount);
}

export function extractVisualFactsFromResponses(
  responses: protos.google.cloud.vision.v1.IAnnotateImageResponse[],
  options: VisionResponseMappingOptions,
  config: VisionResponseMappingConfig
): VisionResponseFacts {
  const allOcrSnippets: OcrSnippet[] = [];
  const allLabelHints: LabelHint[] = [];
  const allLogoHints: LogoHint[] = [];
  const allColors: DominantColor[] = [];

  for (const response of responses) {
    if (options.enableOcr) {
      if (options.ocrMode === 'DOCUMENT_TEXT_DETECTION') {
        const snippets = extractOcrSnippetsFromText(
          response.fullTextAnnotation?.text ?? '',
          {
            maxSnippets: options.maxOcrSnippets,
            maxLength: config.maxOcrSnippetLength,
          }
        );

        if (snippets.length > 0) {
          allOcrSnippets.push(...snippets);
        } else if (response.textAnnotations && response.textAnnotations.length > 0) {
          allOcrSnippets.push(
            ...extractOcrSnippets(response.textAnnotations, {
              maxSnippets: options.maxOcrSnippets,
              maxLength: config.maxOcrSnippetLength,
              minConfidence: config.minOcrConfidence,
            })
          );
        }
      } else {
        allOcrSnippets.push(
          ...extractOcrSnippets(response.textAnnotations, {
            maxSnippets: options.maxOcrSnippets,
            maxLength: config.maxOcrSnippetLength,
            minConfidence: config.minOcrConfidence,
          })
        );
      }
    }

    if (options.enableLabels) {
      const labels = extractLabelHints(response.labelAnnotations, {
        maxLabels: options.maxLabelHints,
        minConfidence: config.minLabelConfidence,
      });
      allLabelHints.push(...labels);
    }

    if (options.enableLogos) {
      const logos = extractLogoHints(response.logoAnnotations, {
        maxLogos: options.maxLogoHints,
        minConfidence: config.minLogoConfidence,
      });
      allLogoHints.push(...logos);
    }

    if (options.enableColors) {
      const colors = extractDominantColorsFromImageProperties(
        response.imagePropertiesAnnotation,
        options.maxColors
      );
      allColors.push(...colors);
    }
  }

  return {
    ocrSnippets: deduplicateSnippets(allOcrSnippets, options.maxOcrSnippets),
    labelHints: deduplicateLabels(allLabelHints, options.maxLabelHints),
    logoHints: deduplicateLogos(allLogoHints, options.maxLogoHints),
    dominantColors: mergeColors(allColors, options.maxColors),
  };
}
