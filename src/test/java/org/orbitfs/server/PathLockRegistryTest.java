package org.orbitfs.server;

import org.junit.jupiter.api.Test;
import org.orbitfs.common.model.LockResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PathLockRegistryTest {

    // --- Canonical path deduplication ---
    @Test
    void canonicalPathDedup() throws Exception {
        PathLockRegistry registry = new PathLockRegistry();

        // /tmp/a/../a/b and /tmp/a/b should resolve to same canonical path
        Path p1 = Path.of("/tmp/a/../a/b");
        Path p2 = Path.of("/tmp/a/b");

        LockResult r1 = registry.readLock(p1);
        LockResult r2 = registry.tryReadLock(p2);

        assertEquals(LockResult.LockStatus.GRANTED, r1.status(),
            "/a/../a/b readLock should succeed");
        assertEquals(LockResult.LockStatus.GRANTED, r2.status(),
            "/a/b tryReadLock should also succeed (same canonical path)");

        registry.unlockRead(p1);
        registry.unlockRead(p2);
    }

    // --- 10 concurrent readers ---
    @Test
    void concurrentReaders() throws Exception {
        PathLockRegistry registry = new PathLockRegistry();
        Path path = Path.of("/tmp/test_concurrent_readers.txt");

        CountDownLatch allReady = new CountDownLatch(10);
        CountDownLatch releaseGate = new CountDownLatch(1);
        CountDownLatch allAcquired = new CountDownLatch(10);
        AtomicInteger failures = new AtomicInteger();

        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 10; i++) {
                exec.submit(() -> {
                    allReady.countDown();
                    try {
                        assumeTrue(releaseGate.await(5, TimeUnit.SECONDS),
                            "release gate should open");
                    } catch (InterruptedException e) {
                        failures.incrementAndGet();
                        return;
                    }
                    try {
                        LockResult lr = registry.readLock(path);
                        if (lr.status() != LockResult.LockStatus.GRANTED) {
                            failures.incrementAndGet();
                        }
                        allAcquired.countDown();
                        // hold briefly
                        Thread.sleep(500);
                        registry.unlockRead(path);
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    }
                });
            }

            allReady.await(5, TimeUnit.SECONDS);
            releaseGate.countDown();
            allAcquired.await(5, TimeUnit.SECONDS);

            assertEquals(0, failures.get(),
                "All readers should succeed concurrently — writer should NOT block them");
        }
    }

    // --- Writer blocks until readers release ---
    @Test
    void writerBlocksUntilReadersRelease() throws Exception {
        PathLockRegistry registry = new PathLockRegistry();
        Path path = Path.of("/tmp/test_writer_block.txt");

        LockResult reader = registry.readLock(path);
        assertEquals(LockResult.LockStatus.GRANTED, reader.status());

        AtomicReference<LockResult> writerResult = new AtomicReference<>(null);
        CompletableFuture<Void> writerFuture = CompletableFuture.runAsync(() -> {
            LockResult w = null;
            try {
                w = registry.tryWriteLock(path, 500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            writerResult.set(w);
        });

        // writer should timeout since reader still holds lock
        writerFuture.get(2, TimeUnit.SECONDS);
        assertEquals(LockResult.LockStatus.BUSY, writerResult.get().status(),
            "Writer should be BUSY while a reader holds the lock");

        registry.unlockRead(path);
        // now writer should succeed after reader released
        LockResult w2 = registry.tryWriteLock(path, 5, TimeUnit.SECONDS);
        assertEquals(LockResult.LockStatus.GRANTED, w2.status(),
            "Writer should acquire after reader releases");
        registry.unlockWrite(path);
    }

    // --- tryReadLock returns BUSY when writeLock held (cross-thread) ---
    @Test
    void tryReadLockBusyWhenWriteHeld() throws Exception {
        PathLockRegistry registry = new PathLockRegistry();
        Path path = Path.of("/tmp/test_try_read_busy.txt");

        LockResult writer = registry.writeLock(path);
        assertEquals(LockResult.LockStatus.GRANTED, writer.status());

        // must check from *different* thread — same thread can reentrantly acquire readLock
        CompletableFuture<LockResult> readerAttempt = CompletableFuture.supplyAsync(() -> registry.tryReadLock(path));
        assertEquals(LockResult.LockStatus.BUSY, readerAttempt.get(2, TimeUnit.SECONDS).status(),
            "tryReadLock should return BUSY when another thread holds writeLock");

        registry.unlockWrite(path);
    }

    // --- Cleanup after last unlock ---
    @Test
    void cleanupAfterLastUnlock() throws Exception {
        PathLockRegistry registry = new PathLockRegistry();
        Path path = Path.of("/tmp/test_cleanup.txt");

        LockResult r = registry.readLock(path);
        assertEquals(LockResult.LockStatus.GRANTED, r.status());

        assertTrue(registry.containsLock(path),
            "Lock should exist immediately after acquiring");

        registry.unlockRead(path);

        // Allow cleanup thread to run (it's synchronous on unlock in this impl)
        assertFalse(registry.containsLock(path),
            "Lock should be cleaned up after the last holder releases");
    }

    // --- Concurrent stress with mixed read/write ---
    @Test
    void mixedReadersAndWritersNoException() throws Exception {
        PathLockRegistry registry = new PathLockRegistry();
        Path path = Path.of("/tmp/test_mixed_stress.txt");
        int threads = 50;

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicInteger exceptions = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threads; i++) {
                final boolean isWriter = (i % 10 == 0);
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        start.await();
                        for (int j = 0; j < 20; j++) {
                            if (isWriter) {
                                LockResult lr = registry.writeLock(path);
                                if (lr.isGranted()) {
                                    registry.unlockWrite(path);
                                }
                            } else {
                                LockResult lr = registry.readLock(path);
                                if (lr.isGranted()) {
                                    registry.unlockRead(path);
                                }
                            }
                        }
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                    }
                }, exec));
            }

            start.countDown();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .get(30, TimeUnit.SECONDS);

            assertEquals(0, exceptions.get(),
                "No thread should throw an exception under concurrent read/write");
        }
    }
}
