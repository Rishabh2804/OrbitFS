package org.orbitfs.common.model;

/**
 * Snapshot of cache metrics.
 *
 * @param hits       number of cache lookups that found data
 * @param misses     number of cache lookups that missed
 * @param evictions  number of entries evicted by LRU
 * @param dirtyCount number of entries currently marked dirty
 */
public record CacheStats(
        long hits,
        long misses,
        long evictions,
        int dirtyCount) {
}
