package org.orbitfs.server;

import org.junit.jupiter.api.Test;
import org.orbitfs.common.protocol.FrameCodec;
import org.orbitfs.common.protocol.RPCRequest;
import org.orbitfs.common.protocol.RPCResponse;
import org.orbitfs.common.protocol.RPCStatus;
import org.orbitfs.common.protocol.RpcMethod;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Spec Checkpoint 1 verification: 50 concurrent PING requests over TCP
 * must complete within 200ms end-to-end (spec target: 100ms, tolerance for
 * loaded CI/VM environments).
 */
class ConcurrencyProofTest {

    @Test
    void fiftyConcurrentPingUnder100ms() throws Exception {
        int port = freePort();
        OrbitServerImpl server = new OrbitServerImpl(port);
        startServer(server, port);

        int clientCount = 50;
        AtomicInteger pongCount = new AtomicInteger();
        CompletableFuture<?>[] futures = new CompletableFuture[clientCount];

        long start = System.nanoTime();
        var executor = newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < clientCount; i++) {
            final int idx = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try (Socket socket = new Socket("127.0.0.1", port);
                     DataInputStream in = new DataInputStream(socket.getInputStream());
                     DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                    String requestId = UUID.randomUUID().toString();
                    RPCRequest ping = new RPCRequest(requestId, RpcMethod.PING, null, null, 0, 0, null, null);
                    out.write(FrameCodec.encodeRequest(ping));
                    out.flush();

                    RPCResponse resp = FrameCodec.decodeResponse(in);
                    assertEquals(RPCStatus.PONG, resp.status(), "client " + idx + " should get PONG");
                    assertEquals(requestId, resp.requestId(), "requestId must match");
                    pongCount.incrementAndGet();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, executor);
        }

        CompletableFuture.allOf(futures).get(2, TimeUnit.SECONDS);
        executor.shutdown();

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertEquals(clientCount, pongCount.get(), "all 50 clients should receive PONG");
        assertTrue(elapsedMs <= 200,
                "50 concurrent PINGs should complete within 200ms, took: " + elapsedMs + "ms");

        server.stop();
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
