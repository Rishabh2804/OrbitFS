package org.orbitfs.client;

import org.orbitfs.common.model.DirtyChunk;
import org.orbitfs.common.model.FileStat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caching decorator for OrbitFSClient. Wraps NetworkTransportClient + LRUChunkCache.
 * Read-through on cache miss, write-back on close.
 */
public final class CachingOrbitFSClient implements OrbitFSClient {

    private final OrbitFSClient delegate;
    private final LRUChunkCache cache;
    private final ConcurrentHashMap<String, String> handleToPath = new ConcurrentHashMap<>();

    public CachingOrbitFSClient(OrbitFSClient delegate, LRUChunkCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    public CachingOrbitFSClient(OrbitFSClient delegate) {
        this(delegate, new LRUChunkCache());
    }

    @Override
    public String open(String path) throws IOException {
        String handleId = delegate.open(path);
        handleToPath.put(handleId, path);
        return handleId;
    }

    @Override
    public byte[] read(String handleId, long offset, int count) throws IOException {
        String path = handleToPath.get(handleId);
        if (path == null) {
            return delegate.read(handleId, offset, count);
        }

        long chunkIndex = offset / LRUChunkCache.CHUNK_SIZE;
        byte[] cached = cache.get(path, chunkIndex);
        if (cached != null) {
            return slice(cached, offset, count);
        }

        byte[] chunk = delegate.read(handleId, chunkIndex * LRUChunkCache.CHUNK_SIZE, LRUChunkCache.CHUNK_SIZE);
        if (chunk.length == LRUChunkCache.CHUNK_SIZE) {
            cache.put(path, chunkIndex, chunk);
        }
        return slice(chunk, offset, count);
    }

    @Override
    public int write(String handleId, long offset, byte[] data) throws IOException {
        String path = handleToPath.get(handleId);
        int written = delegate.write(handleId, offset, data);

        if (path != null) {
            long chunkIndex = offset / LRUChunkCache.CHUNK_SIZE;
            cache.write(path, chunkIndex, data);
        }
        return written;
    }

    @Override
    public void close(String handleId) throws IOException {
        String path = handleToPath.get(handleId);
        if (path != null) {
            List<DirtyChunk> dirty = cache.flush();
            for (DirtyChunk chunk : dirty) {
                if (chunk.path().equals(path)) {
                    long chunkOffset = chunk.chunkIndex() * LRUChunkCache.CHUNK_SIZE;
                    delegate.write(handleId, chunkOffset, chunk.data());
                }
            }
            cache.invalidate(path);
        }
        handleToPath.remove(handleId);
        delegate.close(handleId);
    }

    @Override
    public FileStat stat(String handleId) throws IOException {
        return delegate.stat(handleId);
    }

    @Override
    public java.util.List<String> list(String handleId) throws IOException {
        return delegate.list(handleId);
    }

    @Override
    public void close() throws IOException {
        cache.close();
        delegate.close();
    }

    /**
     * Extracts the requested byte range from a cached chunk.
     */
    private static byte[] slice(byte[] chunk, long offset, int count) {
        int chunkOffset = (int) (offset % LRUChunkCache.CHUNK_SIZE);
        int available = chunk.length - chunkOffset;
        int toReturn = Math.min(count, available);
        if (toReturn <= 0) return new byte[0];

        byte[] result = new byte[toReturn];
        System.arraycopy(chunk, chunkOffset, result, 0, toReturn);
        return result;
    }
}
