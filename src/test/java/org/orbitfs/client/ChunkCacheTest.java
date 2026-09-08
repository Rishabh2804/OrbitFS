package org.orbitfs.client;

import org.junit.jupiter.api.Test;
import org.orbitfs.common.model.CacheStats;
import org.orbitfs.common.model.DirtyChunk;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LRUChunkCache}.
 *
 * Run: ./gradlew test --tests "org.orbitfs.client.ChunkCacheTest"
 */
class ChunkCacheTest {

    @Test
    void putGetHitMiss() {
        LRUChunkCache cache = new LRUChunkCache(100);

        byte[] data = "hello-chunk".getBytes();
        cache.put("file.txt", 0, data);

        byte[] got = cache.get("file.txt", 0);
        assertNotNull(got, "should find cached chunk");
        assertArrayEquals(data, got);

        byte[] miss = cache.get("file.txt", 99);
        assertNull(miss, "absent chunk should be a miss");
    }

    @Test
    void lruEviction() {
        LRUChunkCache cache = new LRUChunkCache(3);

        cache.put("f.txt", 0, b("a"));
        cache.put("f.txt", 1, b("b"));
        cache.put("f.txt", 2, b("c"));

        // access chunk 0 to make it most-recently-used
        assertNotNull(cache.get("f.txt", 0));

        // add a 4th → evicts LRU (chunk 1, since 0 was just accessed)
        cache.put("f.txt", 3, b("d"));

        assertNull(cache.get("f.txt", 1), "chunk 1 should have been evicted");
        assertNotNull(cache.get("f.txt", 0), "chunk 0 should still be present");
        assertNotNull(cache.get("f.txt", 2));
        assertNotNull(cache.get("f.txt", 3));

        CacheStats stats = cache.stats();
        assertEquals(1, stats.evictions(), "should have 1 eviction");
    }

    @Test
    void writeMarksDirty() {
        LRUChunkCache cache = new LRUChunkCache(10);

        cache.put("f.txt", 0, b("original"));
        cache.write("f.txt", 0, b("modified"));

        // write() marks dirty; we verify via flush() returning the dirty chunk
        List<DirtyChunk> dirty = cache.flush();
        assertEquals(1, dirty.size(), "should have 1 dirty chunk after write");
        DirtyChunk dc = dirty.get(0);
        assertEquals("f.txt", dc.path());
        assertEquals(0, dc.chunkIndex());
        assertArrayEquals("modified".getBytes(), dc.data());
    }

    @Test
    void flushReturnsDirtyAndCleans() {
        LRUChunkCache cache = new LRUChunkCache(10);

        cache.write("a.txt", 0, b("a"));
        cache.write("a.txt", 1, b("b"));
        cache.write("a.txt", 2, b("c"));

        List<DirtyChunk> dirty = cache.flush();
        assertEquals(3, dirty.size(), "3 dirty chunks should be flushed");

        // second flush should be empty (all clean now)
        List<DirtyChunk> dirtyAgain = cache.flush();
        assertEquals(0, dirtyAgain.size(), "no dirty chunks after flush");

        // but data is still cached
        assertNotNull(cache.get("a.txt", 0));
    }

    @Test
    void statsTracking() {
        LRUChunkCache cache = new LRUChunkCache(10);

        cache.put("f.txt", 0, b("x"));
        cache.put("f.txt", 1, b("y"));

        cache.get("f.txt", 0); // hit
        cache.get("f.txt", 1); // hit
        cache.get("f.txt", 0); // hit
        cache.get("f.txt", 0); // hit
        cache.get("f.txt", 0); // hit

        cache.get("f.txt", 2); // miss
        cache.get("f.txt", 3); // miss

        CacheStats stats = cache.stats();
        assertEquals(5, stats.hits());
        assertEquals(2, stats.misses());
        assertEquals(0, stats.evictions());
    }

    @Test
    void closeAutoFlushes() {
        LRUChunkCache cache = new LRUChunkCache(10);

        cache.write("f.txt", 0, b("data0"));
        cache.write("f.txt", 1, b("data1"));

        assertEquals(2, cache.stats().dirtyCount(),
                "should have 2 dirty chunks before close");

        cache.close();

        // after close, cache is invalidated — no dirty chunks remain
        assertNull(cache.get("f.txt", 0), "cache should be empty after close");
        assertEquals(0, cache.stats().dirtyCount(),
                "close() should flush dirty chunks");

        // second close is a no-op (should not throw)
        cache.close();
    }

    private static byte[] b(String s) {
        return s.getBytes();
    }
}
