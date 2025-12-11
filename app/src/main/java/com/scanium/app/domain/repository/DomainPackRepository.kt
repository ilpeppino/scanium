package com.scanium.app.domain.repository

import com.scanium.app.domain.config.DomainPack

/**
 * Repository interface for loading Domain Pack configurations.
 *
 * Domain Packs define category taxonomies, attributes, and extraction methods
 * for different business domains (e.g., "home_resale", "retail_inventory").
 *
 * Implementations:
 * - [LocalDomainPackRepository]: Loads from app resources (res/raw)
 * - Future: RemoteDomainPackRepository (fetch from API)
 * - Future: CachedDomainPackRepository (local + remote with caching)
 *
 * The repository pattern provides:
 * - Abstraction over data source (local, remote, cache)
 * - Single source of truth for Domain Pack configuration
 * - Easy testing with fake/mock implementations
 */
interface DomainPackRepository {
    /**
     * Get the currently active Domain Pack.
     *
     * This method may involve disk I/O or network calls, so it's suspending.
     * Implementations should cache the result to avoid repeated parsing.
     *
     * @return The active DomainPack
     * @throws DomainPackLoadException if the pack cannot be loaded or parsed
     */
    suspend fun getActiveDomainPack(): DomainPack
}

/**
 * Exception thrown when a Domain Pack cannot be loaded or parsed.
 */
class DomainPackLoadException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
