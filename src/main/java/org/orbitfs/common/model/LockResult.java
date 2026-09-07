package org.orbitfs.common.model;

import java.util.concurrent.locks.Lock;

/**
 * Result of a lock acquisition attempt.
 */
public record LockResult(LockStatus status, Lock lock) {

    public enum LockStatus {
        GRANTED,  // lock acquired successfully
        BUSY      // could not acquire (timeout or contention)
    }

    public static LockResult granted(Lock lock) {
        return new LockResult(LockStatus.GRANTED, lock);
    }

    public static LockResult busy(Lock lock) {
        return new LockResult(LockStatus.BUSY, lock);
    }

    public boolean isGranted() {
        return status == LockStatus.GRANTED;
    }
}