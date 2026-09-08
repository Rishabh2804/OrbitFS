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
 * Client-side chunk cache: 64KB blocks, LRU eviction via LinkedHashMap, write-back.
 */
public final class LRUChunkCache {

    public static final int CHUNK_SIZE = 64 * 1024;
    private static final int DEFAULT_CAPACITY = 100;

    private final LinkedHashMap<String, CacheEntry> cache;
    private final int capacity;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();
    private boolean closed = false;

    public LRUChunkCache() {
        this(DEFAULT_CAPACITY);
    }

    public LRUChunkCache(int capacity) {
        this.capacity = capacity;
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                boolean evict = size() > LRUChunkCache.this.capacity;
                if (evict) evictions.incrementAndGet();
                return evict;
            }
        };
    }

    public synchronized byte[] get(String path, long chunkIndex) {
        CacheEntry entry = cache.get(keyOf(path, chunkIndex));
        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry.data();
    }

    public synchronized void put(String path, long chunkIndex, byte[] data) {
        cache.put(keyOf(path, chunkIndex), new CacheEntry(data.clone(), false, System.currentTimeMillis()));
    }

    public synchronized void write(String path, long chunkIndex, byte[] data) {
        cache.put(keyOf(path, chunkIndex), new CacheEntry(data.clone(), true, System.currentTimeMillis()));
    }

    public synchronized List<DirtyChunk> flush() {
        List<DirtyChunk> result = new ArrayList<>();
        for (Map.Entry<String, CacheEntry> e : new ArrayList<>(cache.entrySet())) {
            CacheEntry entry = e.getValue();
            if (entry.dirty()) {
                String[] parts = e.getKey().split(":", 2);
                result.add(new DirtyChunk(parts[0], Long.parseLong(parts[1]), entry.data()));
                cache.put(e.getKey(), new CacheEntry(entry.data(), false, entry.lastAccessAt()));
            }
        }
        return result;
    }

    public synchronized void invalidate(String path) {
        cache.keySet().removeIf(k -> k.startsWith(path + ":"));
    }

    public synchronized CacheStats stats() {
        int dirtyCount = 0;
        for (CacheEntry entry : cache.values()) {
            if (entry.dirty()) dirtyCount++;
        }
        return new CacheStats(hits.get(), misses.get(), evictions.get(), dirtyCount);
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        flush();
        cache.clear();
    }

    private static String keyOf(String path, long chunkIndex) {
        return path + ":" + chunkIndex;
    }
}
