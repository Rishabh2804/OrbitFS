package org.orbitfs.server;

import org.junit.jupiter.api.io.TempDir;
import org.orbitfs.client.NetworkTransportClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spec Checkpoint 2 verification: 10 writers + 20 readers on the same file,
 * verifying zero byte corruption under concurrent read/write load.
 */
class ConcurrencyStressTest {

    @TempDir
    private static Path tmpDir;

    @Test
    void tenWritersTwentyReadersZeroCorruption() throws Exception {
        Path file = tmpDir.resolve("stress_" + UUID.randomUUID() + ".dat");

        int port = freePort();
        OrbitServerImpl server = new OrbitServerImpl(port, tmpDir);
        startServer(server, port);

        int writerCount = 10;
        int readersPerWriter = 2;
        int chunkSize = 64 * 1024;

        AtomicInteger exceptions = new AtomicInteger();

        try (NetworkTransportClient client = new NetworkTransportClient("127.0.0.1", port, 30_000L)) {
            client.connect();
            String handleId = client.open(file.toString());

            // Phase 1: all 10 writers write their chunks concurrently
            CompletableFuture<?>[] writerFutures = new CompletableFuture[writerCount];
            for (int w = 0; w < writerCount; w++) {
                final int writerId = w;
                final long offset = (long) w * chunkSize;

                byte[] data = new byte[chunkSize];
                for (int i = 0; i < data.length; i++) {
                    data[i] = (byte) ((writerId + i) % 256);
                }

                writerFutures[w] = CompletableFuture.runAsync(() -> {
                    try {
                        int written = client.write(handleId, offset, data);
                        assertEquals(chunkSize, written, "writer " + writerId);
                    } catch (Exception e) {
                        exceptions.incrementAndGet();
                        throw new RuntimeException(e);
                    }
                });
            }

            CompletableFuture.allOf(writerFutures).get(30, TimeUnit.SECONDS);
            assertEquals(0, exceptions.get(), "writers failed");

            // Phase 2: all 20 readers verify their chunks concurrently
            CompletableFuture<?>[] readerFutures = new CompletableFuture[writerCount * readersPerWriter];
            int ri = 0;
            for (int w = 0; w < writerCount; w++) {
                final int writerId = w;
                final long offset = (long) w * chunkSize;

                for (int r = 0; r < readersPerWriter; r++) {
                    readerFutures[ri++] = CompletableFuture.runAsync(() -> {
                        try {
                            byte[] result = client.read(handleId, offset, chunkSize);
                            assertNotNull(result);
                            assertEquals(chunkSize, result.length);
                            for (int i = 0; i < result.length; i++) {
                                byte expected = (byte) ((writerId + i) % 256);
                                if (result[i] != expected) {
                                    throw new AssertionError(
                                            "Corruption at writer=" + writerId + " byte=" + i +
                                                    ": expected " + expected + " got " + result[i]);
                                }
                            }
                        } catch (AssertionError e) {
                            exceptions.incrementAndGet();
                            throw e;
                        } catch (Exception e) {
                            exceptions.incrementAndGet();
                            throw new RuntimeException(e);
                        }
                    });
                }
            }

            CompletableFuture.allOf(readerFutures).get(30, TimeUnit.SECONDS);
            assertEquals(0, exceptions.get(), "readers failed — zero corruption enforced");
        } finally {
            server.stop();
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private static void startServer(OrbitServerImpl server, int port) throws Exception {
        Thread.ofVirtual().start(() -> {
            try {
                server.start();
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        });
        waitForPort(port);
    }

    private static void waitForPort(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 2_000;
        IOException last = null;
        while (System.currentTimeMillis() < deadline) {
            try (Socket ignored = new Socket("127.0.0.1", port)) {
                return;
            } catch (IOException e) {
                last = e;
                Thread.sleep(25);
            }
        }
        throw new AssertionError("Server did not open port " + port, last);
    }
}
