package org.orbitfs.common.model;

/**
 * A cached file chunk entry.
 *
 * @param data        the chunk bytes
 * @param dirty       true if the chunk has been modified and not yet flushed
 * @param lastAccessAt epoch-millis of last access (for LRU ordering)
 */
public record CacheEntry(
        byte[] data,
        boolean dirty,
        long lastAccessAt) {
}
