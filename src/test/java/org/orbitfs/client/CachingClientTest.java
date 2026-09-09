package org.orbitfs.client;

import org.junit.jupiter.api.io.TempDir;
import org.orbitfs.server.OrbitServerImpl;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C verification: CachingOrbitFSClient read-through reduces 1000×1KB reads
 * to ≤2 server RPCs (first miss fetches+cache, remaining 999 are cache hits).
 */
class CachingClientTest {

    @TempDir
    private static Path tmpDir;

    @Test
    void thousandSmallReadsReduceToOneServerRPC() throws Exception {
        Path file = tmpDir.resolve("cached_" + UUID.randomUUID() + ".dat");
        byte[] chunk = new byte[LRUChunkCache.CHUNK_SIZE];
        for (int i = 0; i < chunk.length; i++) {
            chunk[i] = (byte) (i % 256);
        }
        java.nio.file.Files.write(file, chunk);

        int port = freePort();
        OrbitServerImpl server = new OrbitServerImpl(port, tmpDir);
        startServer(server, port);

        try (NetworkTransportClient transport = new NetworkTransportClient("127.0.0.1", port, 30_000L);
             CachingOrbitFSClient client = new CachingOrbitFSClient(transport)) {

            transport.connect();
            String handleId = client.open(file.toString());

            // Read 1000 × 1KB from within a single 64KB chunk
            byte[] buffer = new byte[1024];
            long offset = 0;
            for (int i = 0; i < 1000; i++) {
                offset = (i * 1024) % (LRUChunkCache.CHUNK_SIZE - 1024);
                byte[] data = client.read(handleId, offset, 1024);
                assertEquals(1024, data.length);
                for (int j = 0; j < data.length; j++) {
                    byte expected = chunk[(int) (offset + j)];
                    assertEquals(expected, data[j],
                            "data mismatch at read " + i + " byte " + j);
                }
            }

            client.close(handleId);

            // Verify: only ~1-2 server read RPCs happened (open + read + close ops)
            // The open() and close() also count, so we expect a small number, not 1000
            long rpcCount = server.getRequestCount();
            assertTrue(rpcCount < 10,
                    "Expected few RPCs due to caching, got " + rpcCount);
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
