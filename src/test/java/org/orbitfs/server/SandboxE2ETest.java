package org.orbitfs.server;

import org.junit.jupiter.api.io.TempDir;
import org.orbitfs.client.NetworkTransportClient;
import org.orbitfs.common.protocol.RPCStatus;
import org.orbitfs.common.protocol.RPCResponse;
import org.orbitfs.common.protocol.RPCRequest;
import org.orbitfs.common.protocol.RpcMethod;
import org.orbitfs.common.protocol.FrameCodec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SandboxE2ETest {

    @TempDir
    private static Path tmpDir;

    @Test
    void serverRejectsPathTraversal() throws Exception {
        int port = freePort();
        OrbitServerImpl server = new OrbitServerImpl(port, tmpDir);
        startServer(server, port);

        try (Socket socket = new Socket("127.0.0.1", port);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            String requestId = UUID.randomUUID().toString();
            RPCRequest req = new RPCRequest(requestId, RpcMethod.OPEN,
                    "../../etc/passwd", null, 0, 0, null);
            out.write(FrameCodec.encodeRequest(req));
            out.flush();

            RPCResponse resp = FrameCodec.decodeResponse(in);
            assertEquals(RPCStatus.ERROR, resp.status(),
                    "Path traversal must be rejected with ERROR");
        } finally {
            server.stop();
        }
    }

    @Test
    void serverRejectsHiddenFiles() throws Exception {
        int port = freePort();
        OrbitServerImpl server = new OrbitServerImpl(port, tmpDir);
        startServer(server, port);

        try (Socket socket = new Socket("127.0.0.1", port);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            String requestId = UUID.randomUUID().toString();
            RPCRequest req = new RPCRequest(requestId, RpcMethod.OPEN,
                    ".bashrc", null, 0, 0, null);
            out.write(FrameCodec.encodeRequest(req));
            out.flush();

            RPCResponse resp = FrameCodec.decodeResponse(in);
            assertEquals(RPCStatus.ERROR, resp.status(),
                    "Hidden files must be rejected with ERROR");
        } finally {
            server.stop();
        }
    }

    @Test
    void serverAllowsFilesWithinRoot() throws Exception {
        Path file = tmpDir.resolve("allowed.txt");
        java.nio.file.Files.write(file, "hello".getBytes());

        int port = freePort();
        OrbitServerImpl server = new OrbitServerImpl(port, tmpDir);
        startServer(server, port);

        try (NetworkTransportClient client = new NetworkTransportClient("127.0.0.1", port, 30_000L)) {
            client.connect();
            String handle = client.open(file.toString());
            byte[] data = client.read(handle, 0, 5);
            assertArrayEquals("hello".getBytes(), data);
            client.close(handle);
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
        while (System.currentTimeMillis() < deadline) {
            try (Socket ignored = new Socket("127.0.0.1", port)) {
                return;
            } catch (IOException e) {
                Thread.sleep(25);
            }
        }
        throw new AssertionError("Server did not open port " + port);
    }
}
