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
 * Thread-safe registry: canonical paths → ReentrantReadWriteLock instances.
 * Allows concurrent readers, exclusive writers. Auto-cleans on last unlock.
 */
public final class PathLockRegistry {

    private final Map<Path, ReadWriteLock> locks = new ConcurrentHashMap<>();

    private enum LockMode { READ, WRITE }

    public LockResult readLock(Path path) { return lock(path, LockMode.READ); }
    public LockResult writeLock(Path path) { return lock(path, LockMode.WRITE); }
    public LockResult tryReadLock(Path path) { return tryLock(path, LockMode.READ); }
    public LockResult tryWriteLock(Path path) { return tryLock(path, LockMode.WRITE); }

    public LockResult tryReadLock(Path path, long timeout, TimeUnit unit) throws InterruptedException {
        return tryLock(path, LockMode.READ, timeout, unit);
    }

    public LockResult tryWriteLock(Path path, long timeout, TimeUnit unit) throws InterruptedException {
        return tryLock(path, LockMode.WRITE, timeout, unit);
    }

    public void unlockRead(Path path) { unlock(path, LockMode.READ); }
    public void unlockWrite(Path path) { unlock(path, LockMode.WRITE); }

    public int size() { return locks.size(); }
    public boolean containsLock(Path path) { return locks.containsKey(canonical(path)); }

    private Path canonical(Path path) { return path.toAbsolutePath().normalize(); }

    private ReadWriteLock getOrCreate(Path path) {
        return locks.computeIfAbsent(canonical(path), k -> new ReentrantReadWriteLock());
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
        return new LockResult(acquired ? LockStatus.GRANTED : LockStatus.BUSY,
                acquired ? wrap(selected, path, mode) : selected);
    }

    private LockResult tryLock(Path path, LockMode mode, long timeout, TimeUnit unit)
            throws InterruptedException {
        ReadWriteLock rwLock = getOrCreate(path);
        Lock selected = modeLock(rwLock, mode);
        boolean acquired = selected.tryLock(timeout, unit);
        return new LockResult(acquired ? LockStatus.GRANTED : LockStatus.BUSY,
                acquired ? wrap(selected, path, mode) : selected);
    }

    private void unlock(Path path, LockMode mode) {
        Path key = canonical(path);
        ReadWriteLock lock = locks.get(key);
        if (lock == null) return;
        modeLock(lock, mode).unlock();
        cleanup(key);
    }

    private void cleanup(Path key) {
        ReadWriteLock lock = locks.get(key);
        if (lock == null) return;
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
     * Wraps a Lock so unlock() triggers cleanup of the registry entry.
     */
    private Lock wrap(Lock delegate, Path path, LockMode mode) {
        Path key = canonical(path);
        return new Lock() {
            @Override public void lock() { delegate.lock(); }
            @Override public void lockInterruptibly() throws InterruptedException { delegate.lockInterruptibly(); }
            @Override public boolean tryLock() { return delegate.tryLock(); }
            @Override public boolean tryLock(long time, TimeUnit unit) throws InterruptedException { return delegate.tryLock(time, unit); }
            @Override public void unlock() {
                delegate.unlock();
                cleanup(key);
            }
            @Override public Condition newCondition() { return delegate.newCondition(); }
        };
    }
}
