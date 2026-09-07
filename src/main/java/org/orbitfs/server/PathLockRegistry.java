package org.orbitfs.server;

import org.orbitfs.common.model.LockResult;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe registry mapping canonical paths to {@link ReentrantReadWriteLock} instances.
 * Allows multiple concurrent readers, exclusive writers.
 * Auto-cleans lock entries when no holders or waiters remain.
 */
public class PathLockRegistry {

    private final Map<Path, ReadWriteLock> locks = new ConcurrentHashMap<>();

    /**
     * Returns the shared (read) lock for the given path.
     * Multiple readers can hold this lock concurrently.
     */
    public LockResult readLock(Path path) {
        ReadWriteLock lock = getOrCreate(path);
        lock.readLock().lock();
        return LockResult.granted(lock.readLock());
    }

    /**
     * Returns the exclusive (write) lock for the given path.
     * Only one writer can hold this lock, excluding all readers.
     */
    public LockResult writeLock(Path path) {
        ReadWriteLock lock = getOrCreate(path);
        lock.writeLock().lock();
        return LockResult.granted(lock.writeLock());
    }

    /**
     * Attempts to acquire the read lock non-blocking.
     * Returns GRANTED if acquired, BUSY otherwise.
     */
    public LockResult tryReadLock(Path path) {
        ReadWriteLock lock = getOrCreate(path);
        if (lock.readLock().tryLock()) {
            return LockResult.granted(lock.readLock());
        }
        return LockResult.busy(lock.readLock());
    }

    /**
     * Attempts to acquire the write lock non-blocking.
     * Returns GRANTED if acquired, BUSY otherwise.
     */
    public LockResult tryWriteLock(Path path) {
        ReadWriteLock lock = getOrCreate(path);
        if (lock.writeLock().tryLock()) {
            return LockResult.granted(lock.writeLock());
        }
        return LockResult.busy(lock.writeLock());
    }

    /**
     * Attempts to acquire the read lock with timeout.
     * Returns GRANTED if acquired within timeout, BUSY otherwise.
     */
    public LockResult tryReadLock(Path path, long timeout, TimeUnit unit) throws InterruptedException {
        ReadWriteLock lock = getOrCreate(path);
        if (lock.readLock().tryLock(timeout, unit)) {
            return LockResult.granted(lock.readLock());
        }
        return LockResult.busy(lock.readLock());
    }

    /**
     * Attempts to acquire the write lock with timeout.
     * Returns GRANTED if acquired within timeout, BUSY otherwise.
     */
    public LockResult tryWriteLock(Path path, long timeout, TimeUnit unit) throws InterruptedException {
        ReadWriteLock lock = getOrCreate(path);
        if (lock.writeLock().tryLock(timeout, unit)) {
            return LockResult.granted(lock.writeLock());
        }
        return LockResult.busy(lock.writeLock());
    }

    /**
     * Releases the read lock for the given path.
     * Auto-cleans the lock entry if no holders or waiters remain.
     */
    public void unlockRead(Path path) {
        ReadWriteLock lock = locks.get(canonical(path));
        if (lock != null) {
            lock.readLock().unlock();
            cleanup(canonical(path));
        }
    }

    /**
     * Releases the write lock for the given path.
     * Auto-cleans the lock entry if no holders or waiters remain.
     */
    public void unlockWrite(Path path) {
        ReadWriteLock lock = locks.get(canonical(path));
        if (lock != null) {
            lock.writeLock().unlock();
            cleanup(canonical(path));
        }
    }

    /**
     * Returns the number of active locks.
     */
    public int size() {
        return locks.size();
    }

    /**
     * Checks if a lock exists for the given path.
     */
    public boolean containsLock(Path path) {
        return locks.containsKey(canonical(path));
    }

    /**
     * Normalizes a path to its canonical form for consistent keying.
     */
    private Path canonical(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private ReadWriteLock getOrCreate(Path path) {
        Path key = canonical(path);
        return locks.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
    }

    /**
     * Removes the lock entry if no holders and no queued threads.
     * Safe to call concurrently; ConcurrentHashMap handles races.
     */
    private void cleanup(Path key) {
        ReadWriteLock lock = locks.get(key);
        if (lock != null) {
            ReentrantReadWriteLock rw = (ReentrantReadWriteLock) lock;
            if (rw.getReadLockCount() == 0
                    && rw.getWriteHoldCount() == 0
                    && !rw.hasQueuedThreads()) {
                locks.remove(key, lock);
            }
        }
    }
}