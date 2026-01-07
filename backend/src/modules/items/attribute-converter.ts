/**
 * Attribute Converter
 *
 * Converts between different attribute representations used in the codebase:
 * - NormalizedAttribute (from enrichment pipeline)
 * - StructuredAttribute (canonical format with full provenance)
 * - ResolvedAttribute (from vision attribute-resolver)
 */

import {
  StructuredAttribute,
  AttributeSource,
  AttributeConfidence,
  EvidenceType,
  EvidenceRef,
} from './attribute-types.js';
import { NormalizedAttribute, AttributeSource as EnrichSource } from '../enrich/types.js';
import {
  ResolvedAttribute,
  EvidenceRef as ResolverEvidenceRef,
  AttributeConfidenceTier,
} from '../vision/attribute-resolver.js';

/**
 * Map enrichment source to canonical AttributeSource.
 */
function mapEnrichSourceToCanonical(source: EnrichSource): AttributeSource {
  switch (source) {
    case 'USER':
      return 'USER';
    case 'VISION_OCR':
    case 'VISION_LOGO':
    case 'VISION_LABEL':
    case 'VISION_COLOR':
    case 'LLM_INFERRED':
      return 'DETECTED';
    default:
      return 'UNKNOWN';
  }
}

/**
 * Map enrichment source to evidence type.
 */
function mapEnrichSourceToEvidenceType(source: EnrichSource): EvidenceType {
  switch (source) {
    case 'VISION_OCR':
      return 'OCR';
    case 'VISION_LOGO':
      return 'LOGO';
    case 'VISION_LABEL':
      return 'LABEL';
    case 'VISION_COLOR':
      return 'COLOR';
    case 'LLM_INFERRED':
      return 'LLM';
    default:
      return 'RULE';
  }
}

/**
 * Map resolver evidence type to canonical EvidenceType.
 */
function mapResolverEvidenceType(type: 'logo' | 'ocr' | 'color' | 'label'): EvidenceType {
  switch (type) {
    case 'logo':
      return 'LOGO';
    case 'ocr':
      return 'OCR';
    case 'color':
      return 'COLOR';
    case 'label':
      return 'LABEL';
  }
}

/**
 * Map confidence tier string to canonical type.
 */
function mapConfidence(conf: AttributeConfidenceTier | string): AttributeConfidence {
  if (conf === 'HIGH' || conf === 'MED' || conf === 'LOW') {
    return conf;
  }
  // Handle MEDIUM from Android models
  if (conf === 'MEDIUM') {
    return 'MED';
  }
  return 'LOW';
}

/**
 * Convert NormalizedAttribute (from enrichment pipeline) to StructuredAttribute.
 */
export function normalizedToStructured(
  attr: NormalizedAttribute,
  timestamp?: number
): StructuredAttribute {
  const evidence: EvidenceRef[] = [];

  if (attr.evidence) {
    evidence.push({
      type: mapEnrichSourceToEvidenceType(attr.source),
      rawValue: attr.evidence,
    });
  }

  return {
    key: attr.key,
    value: attr.value,
    source: mapEnrichSourceToCanonical(attr.source),
    confidence: mapConfidence(attr.confidence),
    evidence: evidence.length > 0 ? evidence : undefined,
    updatedAt: timestamp ?? Date.now(),
  };
}

/**
 * Convert array of NormalizedAttributes to StructuredAttributes.
 */
export function normalizedArrayToStructured(
  attrs: NormalizedAttribute[],
  timestamp?: number
): StructuredAttribute[] {
  return attrs.map((attr) => normalizedToStructured(attr, timestamp));
}

/**
 * Convert ResolvedAttribute (from vision resolver) to StructuredAttribute.
 */
export function resolvedToStructured(
  key: string,
  resolved: ResolvedAttribute,
  timestamp?: number
): StructuredAttribute {
  const evidence: EvidenceRef[] = resolved.evidenceRefs.map((ref: ResolverEvidenceRef) => ({
    type: mapResolverEvidenceType(ref.type),
    rawValue: ref.value,
    score: ref.score,
  }));

  return {
    key,
    value: resolved.value,
    source: 'DETECTED',
    confidence: mapConfidence(resolved.confidence),
    evidence: evidence.length > 0 ? evidence : undefined,
    updatedAt: timestamp ?? Date.now(),
  };
}

/**
 * Convert StructuredAttribute back to NormalizedAttribute format.
 * Used for API responses that need backwards compatibility.
 */
export function structuredToNormalized(attr: StructuredAttribute): NormalizedAttribute {
  // Determine the best source type for the enrichment format
  let source: EnrichSource = 'USER';

  if (attr.source === 'DETECTED') {
    // Try to infer from evidence
    const evidenceType = attr.evidence?.[0]?.type;
    switch (evidenceType) {
      case 'OCR':
        source = 'VISION_OCR';
        break;
      case 'LOGO':
        source = 'VISION_LOGO';
        break;
      case 'LABEL':
        source = 'VISION_LABEL';
        break;
      case 'COLOR':
        source = 'VISION_COLOR';
        break;
      case 'LLM':
        source = 'LLM_INFERRED';
        break;
      default:
        source = 'VISION_LABEL'; // Default for detected
    }
  }

  return {
    key: attr.key,
    value: attr.value,
    confidence: attr.confidence,
    source,
    evidence: attr.evidence?.[0]?.rawValue,
  };
}

/**
 * Convert array of StructuredAttributes to NormalizedAttributes.
 */
export function structuredArrayToNormalized(attrs: StructuredAttribute[]): NormalizedAttribute[] {
  return attrs.map(structuredToNormalized);
}

/**
 * Create a StructuredAttribute from a simple key-value pair with USER source.
 */
export function createUserAttribute(
  key: string,
  value: string,
  timestamp?: number
): StructuredAttribute {
  return {
    key,
    value,
    source: 'USER',
    confidence: 'HIGH',
    updatedAt: timestamp ?? Date.now(),
  };
}

/**
 * Create a StructuredAttribute from a simple key-value pair with DEFAULT source.
 */
export function createDefaultAttribute(
  key: string,
  value: string,
  timestamp?: number
): StructuredAttribute {
  return {
    key,
    value,
    source: 'DEFAULT',
    confidence: 'LOW',
    updatedAt: timestamp ?? Date.now(),
  };
}
