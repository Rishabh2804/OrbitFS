package org.orbitfs.client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for NetworkTransportClient against an in-process FrameCodec mock server.
 * Run: ./gradlew test --tests "org.orbitfs.client.ClientTest"
 */
class ClientTest {

    private static final int PORT;
    private static Thread serverThread;
    private static final CountDownLatch ready = new CountDownLatch(1);
    private static final AtomicReference<Throwable> serverError = new AtomicReference<>();

    static {
        int port = 0;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
            probe.close();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        PORT = port;
    }

    @BeforeAll
    static void startServer() throws InterruptedException {
        serverThread = Thread.ofVirtual().start(() -> {
            try (ServerSocket ss = new ServerSocket(PORT)) {
                ready.countDown();
                while (!ss.isClosed()) {
                    Socket socket = ss.accept();
                    Thread.ofVirtual().start(() -> {
                        try {
                            DataInputStream in = new DataInputStream(socket.getInputStream());
                            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                            while (!socket.isClosed()) {
                                RPCRequest req = FrameCodec.decodeRequest(in);
                                handleRequest(req, out);
                            }
                        } catch (IOException ignored) {
                        } finally {
                            try { socket.close(); } catch (IOException ignored) {}
                        }
                    });
                }
            } catch (IOException e) {
                serverError.set(e);
            }
        });
        assertTrue(ready.await(5, TimeUnit.SECONDS), "mock server should start");
    }

    @AfterAll
    static void shutdownServer() throws IOException, InterruptedException {
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread.join(Duration.ofSeconds(2).toMillis());
        }
    }

/**
 * Handles RPCRequest and sends back RPCResponse with method-specific logic.
 */
    private static void handleRequest(RPCRequest req, DataOutputStream out) throws IOException {
        RPCResponse resp = switch (req.method()) {
            case PING -> new RPCResponse(req.requestId(), RPCStatus.PONG, 0, 0,
                    null, null, null);
            case OPEN -> new RPCResponse(req.requestId(), RPCStatus.OK, 0, 0,
                    "mock-fd-" + System.nanoTime(), null, null);
            case READ -> new RPCResponse(req.requestId(), RPCStatus.OK, 0, req.count(),
                    null, Base64.getEncoder().encodeToString(new byte[req.count()]), null);
            case WRITE -> {
                int written = req.dataBase64() != null
                        ? Base64.getDecoder().decode(req.dataBase64()).length
                        : 0;
                yield new RPCResponse(req.requestId(), RPCStatus.OK, 0, written,
                        null, null, null);
            }
            case CLOSE -> new RPCResponse(req.requestId(), RPCStatus.OK, 0, 0,
                    null, null, null);
            case STAT -> new RPCResponse(req.requestId(), RPCStatus.OK, 0, 0,
                    null, null, new RPCResponse.RPCStat(4096, false, System.currentTimeMillis()));
            default -> RPCResponse.error(req.requestId(), 1);
        };
        out.write(FrameCodec.encodeResponse(resp));
        out.flush();
    }

    @Test
    void pingPong() throws Exception {
        // PING/PONG is server-side health-check. The client interface (OrbitFSClient)
        // does not expose ping directly, so we verify connect + close works cleanly.
        NetworkTransportClient client = new NetworkTransportClient("localhost", PORT);
        client.connect();
        client.close();
    }

    @Test
    void openReturnsHandle() throws Exception {
        NetworkTransportClient client = new NetworkTransportClient("localhost", PORT);
        client.connect();
        try {
            String handleId = client.open("/tmp/test.txt");
            assertNotNull(handleId);
            assertTrue(handleId.startsWith("mock-fd"));
        } finally {
            client.close();
        }
    }

    @Test
    void readReturnsBytes() throws Exception {
        NetworkTransportClient client = new NetworkTransportClient("localhost", PORT);
        client.connect();
        try {
            String handleId = client.open("/tmp/test.txt");
            byte[] data = client.read(handleId, 0, 10);
            assertEquals(10, data.length);
        } finally {
            client.close();
        }
    }

    @Test
    void writeReturnsBytesWritten() throws Exception {
        NetworkTransportClient client = new NetworkTransportClient("localhost", PORT);
        client.connect();
        try {
            String handleId = client.open("/tmp/test.txt");
            int written = client.write(handleId, 0, new byte[]{1, 2});
            assertEquals(2, written);
        } finally {
            client.close();
        }
    }

    @Test
    void statReturnsFileStat() throws Exception {
        NetworkTransportClient client = new NetworkTransportClient("localhost", PORT);
        client.connect();
        try {
            String handleId = client.open("/tmp/test.txt");
            var stat = client.stat(handleId);
            assertEquals(4096, stat.size());
            assertFalse(stat.isDirectory());
        } finally {
            client.close();
        }
    }

    @Test
    void closeHandleThenCloseConnection() throws Exception {
        NetworkTransportClient client = new NetworkTransportClient("localhost", PORT);
        client.connect();
        String handleId = client.open("/tmp/test.txt");
        client.close(handleId);
        client.close();
        // second close should be a no-op (not throw)
        client.close();
        assertNull(serverError.get(), "server should not error: " + serverError.get());
    }

    @Test
    void concurrentRequestsMultiplexOverOneSocket() throws Exception {
        NetworkTransportClient client = new NetworkTransportClient("localhost", PORT, 5_000L);
        client.connect();
        try {
            int count = 20;
            CountDownLatch allDone = new CountDownLatch(count);
            for (int i = 0; i < count; i++) {
                Thread.ofVirtual().start(() -> {
                    try {
                        String handleId = client.open("/tmp/concurrent.txt");
                        assertNotNull(handleId);
                        byte[] data = client.read(handleId, 0, 5);
                        assertEquals(5, data.length);
                        client.close(handleId);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        allDone.countDown();
                    }
                });
            }
            assertTrue(allDone.await(5, TimeUnit.SECONDS), "all concurrent requests should complete");
        } finally {
            client.close();
        }
    }

    @Test
    void timeoutWhenServerDoesNotRespond() throws Exception {
        CountDownLatch accepted = new CountDownLatch(1);
        try (ServerSocket blocker = new ServerSocket(0)) {
            int blockerPort = blocker.getLocalPort();
            Thread.ofVirtual().start(() -> {
                try {
                    Socket s = blocker.accept();
                    accepted.countDown();
                    // Read requests but never send responses — client will time out
                    DataInputStream in = new DataInputStream(s.getInputStream());
                    while (!s.isClosed()) {
                        FrameCodec.decodeRequest(in);
                    }
                } catch (IOException ignored) {
                }
            });

            NetworkTransportClient client = new NetworkTransportClient(
                    "localhost", blockerPort, 500L);
            client.connect();
            try {
                assertTrue(accepted.await(2, TimeUnit.SECONDS), "blocker should accept");
                assertThrows(RuntimeException.class,
                        () -> client.open("/tmp/timeout.txt"),
                        "should time out");
            } finally {
                client.close();
            }
        }
    }
}
