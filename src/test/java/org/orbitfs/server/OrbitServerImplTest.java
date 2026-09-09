package org.orbitfs.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OrbitServerImplTest {

    @TempDir
    private static Path tmpDir;

    @Test
    void startRespondsToPingAndStopsCleanly() throws Exception {
        int port = freePort();
        OrbitServerImpl server = new OrbitServerImpl(port, tmpDir);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        serverThread.start();
        waitForPort(port);

        try (Socket socket = new Socket("127.0.0.1", port);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            String requestId = UUID.randomUUID().toString();
            RPCRequest ping = new RPCRequest(requestId, RpcMethod.PING, null, null, 0, 0, null);
            out.write(FrameCodec.encodeRequest(ping));
            out.flush();

            RPCResponse resp = FrameCodec.decodeResponse(in);
            assertEquals(requestId, resp.requestId());
            assertEquals(RPCStatus.PONG, resp.status());
        }

        server.stop();
        serverThread.join(Duration.ofSeconds(2).toMillis());
        assertFalse(serverThread.isAlive(), "server thread should stop");
        assertNull(failure.get(), "server should not fail: " + failure.get());
    }

    @Test
    void fileLifecycleThroughRpc() throws Exception {
        Path file = tmpDir.resolve("rpc_lifecycle_" + UUID.randomUUID() + ".dat");
        int port = freePort();

        OrbitServerImpl server = new OrbitServerImpl(port, tmpDir);
        startServer(server, port);

        try (Socket socket = new Socket("127.0.0.1", port);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            // OPEN
            out.write(FrameCodec.encodeRequest(req(RpcMethod.OPEN, file.toString())));
            out.flush();
            String fd = FrameCodec.decodeResponse(in).fd();
            assertNotNull(fd);

            // WRITE "hello-orbitfs" at offset 0
            byte[] payload = "hello-orbitfs".getBytes();
            out.write(FrameCodec.encodeRequest(req(RpcMethod.WRITE, fd, 0, 0,
                    Base64.getEncoder().encodeToString(payload))));
            out.flush();
            RPCResponse writeResp = FrameCodec.decodeResponse(in);
            assertEquals(payload.length, writeResp.bytesProcessed());

            // READ back
            out.write(FrameCodec.encodeRequest(req(RpcMethod.READ, fd, 0, 13)));
            out.flush();
            RPCResponse readResp = FrameCodec.decodeResponse(in);
            assertArrayEquals(payload, Base64.getDecoder().decode(readResp.dataBase64()));

            // STAT
            out.write(FrameCodec.encodeRequest(req(RpcMethod.STAT, fd, 0, 0)));
            out.flush();
            RPCResponse statResp = FrameCodec.decodeResponse(in);
            assertEquals(payload.length, statResp.stat().size());

            // CLOSE
            out.write(FrameCodec.encodeRequest(req(RpcMethod.CLOSE, fd, 0, 0)));
            out.flush();
            RPCResponse closeResp = FrameCodec.decodeResponse(in);
            assertEquals(RPCStatus.OK, closeResp.status());
        } finally {
            server.stop();
        }
    }

    @Test
    void invalidHandleReturnsError() throws Exception {
        int port = freePort();
        OrbitServerImpl server = new OrbitServerImpl(port, tmpDir);
        startServer(server, port);

        try (Socket socket = new Socket("127.0.0.1", port);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            out.write(FrameCodec.encodeRequest(req(RpcMethod.READ, "nonexistent-fd", 0, 10)));
            out.flush();

            RPCResponse resp = FrameCodec.decodeResponse(in);
            assertEquals(RPCStatus.ERROR, resp.status());
        } finally {
            server.stop();
        }
    }

    private static RPCRequest req(RpcMethod method, String path) {
        return new RPCRequest(UUID.randomUUID().toString(), method, path, null, 0, 0, null);
    }

    private static RPCRequest req(RpcMethod method, String fd, long offset, int count) {
        return new RPCRequest(UUID.randomUUID().toString(), method, null, fd, offset, count, null);
    }

    private static RPCRequest req(RpcMethod method, String fd, long offset, int count, String dataB64) {
        return new RPCRequest(UUID.randomUUID().toString(), method, null, fd, offset, count, dataB64);
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
