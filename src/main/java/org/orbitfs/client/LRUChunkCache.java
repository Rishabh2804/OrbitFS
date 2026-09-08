package org.orbitfs.client;

import org.orbitfs.common.model.CacheEntry;
import org.orbitfs.common.model.CacheStats;
import org.orbitfs.common.model.DirtyChunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client-side chunk cache with LRU eviction and write-back strategy.
 * <p>
 * Caches file chunks of {@value #CHUNK_SIZE} bytes. Uses a {@link LinkedHashMap}
 * in access-order for O(1) LRU eviction. Dirty entries are batched on
 * {@link #flush()} for a single RPC write payload.
 *
 * <p>Thread-safety: all public methods are {@code synchronized} on the instance.
 * Hot-path metrics use {@link AtomicLong} counters updated under the same lock,
 * which is acceptable given the 64KB block size (cache ops dominate I/O cost).
 */
public final class LRUChunkCache {

    /** Block size in bytes. */
    public static final int CHUNK_SIZE = 64 * 1024;

    private static final int DEFAULT_CAPACITY = 100;

    private final LinkedHashMap<String, CacheEntry> cache;
    private final int capacity;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();
    private boolean closed = false;

    /**
     * Creates a cache with the default capacity of {@value #DEFAULT_CAPACITY} entries.
     */
    public LRUChunkCache() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a cache with the specified capacity.
     *
     * @param capacity maximum number of entries before LRU eviction
     */
    public LRUChunkCache(int capacity) {
        this.capacity = capacity;
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                boolean evict = size() > LRUChunkCache.this.capacity;
                if (evict) {
                    evictions.incrementAndGet();
                }
                return evict;
            }
        };
    }

    /**
     * Returns the cached chunk for the given path and chunk index, or
     * {@code null} on a miss. Records hit/miss metrics.
     *
     * @param path        the file path
     * @param chunkIndex  the chunk index within the file
     * @return cached bytes, or {@code null} if not present
     */
    public synchronized byte[] get(String path, long chunkIndex) {
        String key = keyOf(path, chunkIndex);
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        cache.put(key, new CacheEntry(entry.data(), entry.dirty(), System.currentTimeMillis()));
        return entry.data();
    }

    /**
     * Inserts or updates a chunk in the cache (read result, not dirty).
     *
     * @param path        the file path
     * @param chunkIndex  the chunk index within the file
     * @param data        the chunk bytes
     */
    public synchronized void put(String path, long chunkIndex, byte[] data) {
        String key = keyOf(path, chunkIndex);
        cache.put(key, new CacheEntry(data.clone(), false, System.currentTimeMillis()));
    }

    /**
     * Updates a chunk and marks it dirty for write-back flushing.
     *
     * @param path        the file path
     * @param chunkIndex  the chunk index within the file
     * @param data        the chunk bytes (defensively copied)
     */
    public synchronized void write(String path, long chunkIndex, byte[] data) {
        String key = keyOf(path, chunkIndex);
        cache.put(key, new CacheEntry(data.clone(), true, System.currentTimeMillis()));
    }

    /**
     * Atomically snapshots all dirty chunks, returns them batched for a single
     * RPC write, and marks them clean.
     *
     * @return list of dirty chunks to flush (empty if none)
     */
    public synchronized List<DirtyChunk> flush() {
        List<DirtyChunk> result = new ArrayList<>();
        for (Map.Entry<String, CacheEntry> e : new ArrayList<>(cache.entrySet())) {
            CacheEntry entry = e.getValue();
            if (entry.dirty()) {
                String[] parts = e.getKey().split(":", 2);
                String path = parts[0];
                long chunkIndex = Long.parseLong(parts[1]);
                result.add(new DirtyChunk(path, chunkIndex, entry.data()));
                cache.put(e.getKey(), new CacheEntry(entry.data(), false, entry.lastAccessAt()));
            }
        }
        return result;
    }

    /**
     * Drops all chunks for the given path.
     *
     * @param path the file path to invalidate
     */
    public synchronized void invalidate(String path) {
        String prefix = path + ":";
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /**
     * Returns a snapshot of current cache metrics.
     *
     * @return cache statistics
     */
    public synchronized CacheStats stats() {
        int dirtyCount = 0;
        for (CacheEntry entry : cache.values()) {
            if (entry.dirty()) dirtyCount++;
        }
        return new CacheStats(hits.get(), misses.get(), evictions.get(), dirtyCount);
    }

    /**
     * Flushes all dirty chunks, then invalidates the entire cache.
     * Idempotent — subsequent calls are no-ops.
     */
    public synchronized void close() {
        if (closed) return;
        closed = true;
        flush();
        cache.clear();
    }

    /**
     * Constructs the cache key: {@code path:chunkIndex}.
     */
    private static String keyOf(String path, long chunkIndex) {
        return path + ":" + chunkIndex;
    }
}
