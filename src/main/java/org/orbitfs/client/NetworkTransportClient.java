package org.orbitfs.client;

import org.orbitfs.common.model.FileStat;
import org.orbitfs.common.protocol.FrameCodec;
import org.orbitfs.common.protocol.RPCRequest;
import org.orbitfs.common.protocol.RpcMethod;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * OFS-107 — Machine Coding Skeleton
 * <p>
 * Persistent TCP client that multiplexes RPCs over one socket.
 * Uses FrameCodec (OFS-102) for length-prefixed framing and
 * correlates requestId → CompletableFuture.
 *
 * <p><b>Your task:</b> implement the 5 file ops + connect/close
 * to make ClientTest green. Hints inside.
 */
public class NetworkTransportClient implements OrbitFSClient {

    private final String host;
    private final int port;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private final ConcurrentHashMap<String, CompletableFuture<byte[]>> pending = new ConcurrentHashMap<>();
    private Thread readerThread;

    public NetworkTransportClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Opens socket and starts virtual reader thread.
     * Reader: while true { FrameCodec.decode(in, byte[].class) → complete pending future }
     */
    public void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);

        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());

        readerThread = Thread.ofVirtual().start(() -> {
            while (!socket.isClosed()) {
                try {
                    byte[] data = FrameCodec.decode(in, byte[].class);
                    // TODO: complete pending future
                } catch (IOException e) {
                    // TODO: fail pending futures
                }
            }
        });
    }

    // factory — single place to create RPCRequest, avoids repeating new RPCRequest(...) 5 times
    private RPCRequest newReq(RpcMethod method, String path, String fd, long offset, int count, String dataB64) {
        return new RPCRequest(nextId(), method.name(), path, fd, offset, count, dataB64);
    }

    @Override
    public String open(String path) throws IOException {
        RPCRequest request = newReq(RpcMethod.OPEN, path, null, 0, 0, null);
        // TODO: FrameCodec.encodeRequest(request) → out.write, await pending
        throw new UnsupportedOperationException("TODO: implement open");
    }

    @Override
    public byte[] read(String handleId, long offset, int count) throws IOException {
        RPCRequest request = newReq(RpcMethod.READ, null, handleId, offset, count, null);
        // TODO: send via FrameCodec, await
        throw new UnsupportedOperationException("TODO: implement read");
    }

    @Override
    public int write(String handleId, long offset, byte[] data) throws IOException {
        // TODO: send write request, await, return bytesWritten
        throw new UnsupportedOperationException("TODO: implement write");
    }

    @Override
    public void close(String handleId) throws IOException {
        // TODO: send close request, await
        throw new UnsupportedOperationException("TODO: implement close(handleId)");
    }

    @Override
    public FileStat stat(String handleId) throws IOException {
        // TODO: send stat request, await, decode FileStat
        throw new UnsupportedOperationException("TODO: implement stat");
    }

    @Override
    public void close() throws IOException {
        // TODO: close socket, interrupt readerThread, fail pending futures
        throw new UnsupportedOperationException("TODO: implement close()");
    }

    // visible for test — send helper
    private String nextId() {
        return UUID.randomUUID().toString();
    }
}
