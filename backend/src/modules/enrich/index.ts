/**
 * Enrichment Module
 *
 * Provides automatic scan-to-enrichment pipeline:
 * - Vision facts extraction (Google Vision)
 * - Attribute normalization (rules + LLM)
 * - Draft generation (OpenAI)
 */

export { enrichRoutes } from './routes.js';
export { EnrichmentManager, getEnrichmentManager, shutdownEnrichmentManager } from './enrich-manager.js';
export { runEnrichmentPipeline } from './pipeline.js';
export * from './types.js';
