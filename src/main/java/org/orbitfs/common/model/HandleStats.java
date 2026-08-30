package org.orbitfs.common.model;

import org.orbitfs.common.OrbitCore;
import org.orbitfs.common.OrbitSerializer;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-handle usage statistics. Thread-safe counters so concurrent
 * readers/writers (virtual-thread server) can update without corruption.
 */
public class HandleStats {

    private final Instant createdAt = Instant.now();
    private volatile Instant lastAccessedAt = createdAt;
    private final AtomicLong readCount = new AtomicLong();
    private final AtomicLong writeCount = new AtomicLong();
    private final AtomicLong totalBytesRead = new AtomicLong();
    private final AtomicLong totalBytesWritten = new AtomicLong();

    public void recordRead(long bytes) {
        readCount.incrementAndGet();
        totalBytesRead.addAndGet(bytes);
    }

    public void recordWrite(long bytes) {
        writeCount.incrementAndGet();
        totalBytesWritten.addAndGet(bytes);
    }

    public void touch() {
        lastAccessedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public long getReadCount() {
        return readCount.get();
    }

    public long getWriteCount() {
        return writeCount.get();
    }

    public long getTotalBytesRead() {
        return totalBytesRead.get();
    }

    public long getTotalBytesWritten() {
        return totalBytesWritten.get();
    }

    public String getStats() {
        try {
            return OrbitSerializer.toJson(this);
        } catch (IOException e) {
            OrbitCore.LOGGER.error("Failed to serialize HandleStats to JSON", e);
            throw new RuntimeException(e);
        }
    }

    public String toString() {
        return getStats();
    }
}