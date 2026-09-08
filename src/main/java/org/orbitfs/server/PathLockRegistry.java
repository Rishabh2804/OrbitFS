package org.orbitfs.server;

import org.orbitfs.common.model.LockResult;
import org.orbitfs.common.model.LockResult.LockStatus;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe registry mapping canonical paths to {@link ReentrantReadWriteLock} instances.
 * Allows multiple concurrent readers, exclusive writers.
 * Auto-cleans lock entries when no holders or waiters remain.
 */
public final class PathLockRegistry {

    private final Map<Path, ReadWriteLock> locks = new ConcurrentHashMap<>();

    private enum LockMode {
        READ,
        WRITE
    }

    /**
     * Returns the shared (read) lock for the given path.
     * Multiple readers can hold this lock concurrently.
     */
    public LockResult readLock(Path path) {
        return lock(path, LockMode.READ);
    }

    /**
     * Returns the exclusive (write) lock for the given path.
     * Only one writer can hold this lock, excluding all readers.
     */
    public LockResult writeLock(Path path) {
        return lock(path, LockMode.WRITE);
    }

    /**
     * Attempts to acquire the read lock non-blocking.
     * Returns GRANTED if acquired, BUSY otherwise.
     */
    public LockResult tryReadLock(Path path) {
        return tryLock(path, LockMode.READ);
    }

    /**
     * Attempts to acquire the write lock non-blocking.
     * Returns GRANTED if acquired, BUSY otherwise.
     */
    public LockResult tryWriteLock(Path path) {
        return tryLock(path, LockMode.WRITE);
    }

    /**
     * Attempts to acquire the read lock with timeout.
     * Returns GRANTED if acquired within timeout, BUSY otherwise.
     */
    public LockResult tryReadLock(Path path, long timeout, TimeUnit unit)
            throws InterruptedException {
        return tryLock(path, LockMode.READ, timeout, unit);
    }

    /**
     * Attempts to acquire the write lock with timeout.
     * Returns GRANTED if acquired within timeout, BUSY otherwise.
     */
    public LockResult tryWriteLock(Path path, long timeout, TimeUnit unit)
            throws InterruptedException {
        return tryLock(path, LockMode.WRITE, timeout, unit);
    }

    /**
     * Releases the read lock for the given path.
     * Auto-cleans the lock entry if no holders or waiters remain.
     */
    public void unlockRead(Path path) {
        unlock(path, LockMode.READ);
    }

    /**
     * Releases the write lock for the given path.
     * Auto-cleans the lock entry if no holders or waiters remain.
     */
    public void unlockWrite(Path path) {
        unlock(path, LockMode.WRITE);
    }

    /**
     * Returns the number of active lock entries.
     */
    public int size() {
        return locks.size();
    }

    /**
     * Checks if a lock entry exists for the given path.
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

    private LockResult lock(Path path, LockMode mode) {
        ReadWriteLock rwLock = getOrCreate(path);
        Lock selected = modeLock(rwLock, mode);
        selected.lock();
        return LockResult.granted(wrap(selected, path, mode));
    }

    private LockResult tryLock(Path path, LockMode mode) {
        ReadWriteLock rwLock = getOrCreate(path);
        Lock selected = modeLock(rwLock, mode);
        boolean acquired = selected.tryLock();
        LockStatus status = acquired ? LockStatus.GRANTED : LockStatus.BUSY;
        return new LockResult(status, acquired ? wrap(selected, path, mode) : selected);
    }

    private LockResult tryLock(Path path, LockMode mode, long timeout, TimeUnit unit)
            throws InterruptedException {
        ReadWriteLock rwLock = getOrCreate(path);
        Lock selected = modeLock(rwLock, mode);
        boolean acquired = selected.tryLock(timeout, unit);
        LockStatus status = acquired ? LockStatus.GRANTED : LockStatus.BUSY;
        return new LockResult(status, acquired ? wrap(selected, path, mode) : selected);
    }

    private void unlock(Path path, LockMode mode) {
        Path key = canonical(path);
        ReadWriteLock lock = locks.get(key);
        if (lock == null) {
            return;
        }
        modeLock(lock, mode).unlock();
        cleanup(key);
    }

    /**
     * Removes the lock entry if no holders and no queued threads.
     */
    private void cleanup(Path key) {
        ReadWriteLock lock = locks.get(key);
        if (lock == null) {
            return;
        }
        ReentrantReadWriteLock rwLock = (ReentrantReadWriteLock) lock;
        if (rwLock.getReadLockCount() == 0
                && rwLock.getWriteHoldCount() == 0
                && !rwLock.hasQueuedThreads()) {
            locks.remove(key, lock);
        }
    }

    private Lock modeLock(ReadWriteLock lock, LockMode mode) {
        return switch (mode) {
            case READ -> lock.readLock();
            case WRITE -> lock.writeLock();
        };
    }

    /**
     * Wraps a {@link Lock} so that {@link Lock#unlock()} triggers cleanup
     * of the registry entry when no holders or waiters remain.
     */
    private Lock wrap(Lock delegate, Path path, LockMode mode) {
        Path key = canonical(path);
        return new Lock() {
            @Override
            public void lock() {
                delegate.lock();
            }

            @Override
            public void lockInterruptibly() throws InterruptedException {
                delegate.lockInterruptibly();
            }

            @Override
            public boolean tryLock() {
                return delegate.tryLock();
            }

            @Override
            public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
                return delegate.tryLock(time, unit);
            }

            @Override
            public void unlock() {
                delegate.unlock();
                cleanup(key);
            }

            @Override
            public Condition newCondition() {
                return delegate.newCondition();
            }
        };
    }
}
