package org.orbitfs.client;

import org.orbitfs.common.model.FileStat;
import org.orbitfs.common.protocol.FrameCodec;
import org.orbitfs.common.protocol.RPCRequest;
import org.orbitfs.common.protocol.RPCResponse;
import org.orbitfs.common.protocol.RpcMethod;
import org.orbitfs.common.protocol.RPCStatus;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Persistent TCP client that multiplexes RPCs over a single socket.
 * <p>
 * Uses {@link FrameCodec} for length-prefixed framing and correlates
 * requestId → CompletableFuture so multiple threads can share one connection.
 */
public final class NetworkTransportClient implements OrbitFSClient {

    private static final long DEFAULT_TIMEOUT_MS = 10_000L;

    private final String host;
    private final int port;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private final ConcurrentHashMap<String, CompletableFuture<RPCResponse>> pending = new ConcurrentHashMap<>();
    private Thread readerThread;
    private volatile boolean closed = false;

    private final long timeout;

    public NetworkTransportClient(String host, int port) {
        this(host, port, DEFAULT_TIMEOUT_MS);
    }

    public NetworkTransportClient(String host, int port, long timeout) {
        this.host = host;
        this.port = port;
        this.timeout = timeout;
    }

    /**
     * Opens socket, wraps streams, and starts a virtual-thread reader that
     * decodes {@link RPCResponse} frames and completes the matching pending future.
     *
     * @throws IOException if the socket cannot be opened
     */
    public void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);

        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());

        readerThread = Thread.ofVirtual().start(this::readLoop);
    }

    /**
     * Background reader loop. Decodes one {@link RPCResponse} frame at a time
     * and completes the matching future in {@link #pending}, keyed by requestId.
     * When the stream dies, fails all outstanding futures.
     *
     * <p>This is called from the virtual-thread reader started in {@link #connect()}.
     */
    private void readLoop() {
        try {
            while (!socket.isClosed()) {
                RPCResponse response = FrameCodec.decodeResponse(in);
                CompletableFuture<RPCResponse> future = pending.remove(response.requestId());
                if (future != null) {
                    future.complete(response);
                }
            }
        } catch (IOException e) {
            failPending(new IOException("Connection closed", e));
        }
    }

    /**
     * Core send-and-await helper. Sends an RPC request and waits for the response,
     * keyed by a fresh {@code requestId}.
     *
     * @throws IOException if the request cannot be sent
     */
    private RPCResponse send(RpcMethod method, String path, String fd,
                             long offset, int count, byte[] data) throws IOException {
        ensureConnected();
        String requestId = nextId();
        String dataB64 = (data != null) ? Base64.getEncoder().encodeToString(data) : null;
        RPCRequest request = new RPCRequest(requestId, method, path, fd, offset, count, dataB64);

        CompletableFuture<RPCResponse> future = new CompletableFuture<>();
        pending.put(requestId, future);

        synchronized (out) {
            try {
                out.write(FrameCodec.encodeRequest(request));
                out.flush();
            } catch (IOException e) {
                pending.remove(requestId);
                throw new IOException("Failed to send request: " + e.getMessage(), e);
            }
        }

        try {
            RPCResponse response = future.get(this.timeout, TimeUnit.MILLISECONDS);
            if (response.status() == RPCStatus.ERROR) {
                throw new RuntimeException("server error (code " + response.errorCode() + ")");
            }
            return response;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            pending.remove(requestId);

            throw new RuntimeException("Interrupted while waiting for response", ie);
        } catch (TimeoutException toe) {
            pending.remove(requestId);
            throw new RuntimeException("Timed out after " + this.timeout + "ms", toe);
        } catch (ExecutionException execE) {
            Throwable cause = execE.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        }
    }

    private void ensureConnected() throws IOException {
        if (closed || socket == null || socket.isClosed()) {
            throw new IOException("Client is not connected");
        }
    }

    private void failPending(Throwable cause) {
        pending.values().forEach(f -> f.completeExceptionally(cause));
        pending.clear();
    }

    @Override
    public String open(String path) throws IOException {
        RPCResponse response = send(RpcMethod.OPEN, path, null, 0, 0, null);
        return response.fd();
    }

    @Override
    public byte[] read(String handleId, long offset, int count) throws IOException {
        RPCResponse response = send(RpcMethod.READ, null, handleId, offset, count, null);
        if (response.dataBase64() == null) {
            return new byte[0];  // empty buffer = no data
        }
        return Base64.getDecoder().decode(response.dataBase64());
    }

    @Override
    public int write(String handleId, long offset, byte[] data) throws IOException {
        RPCResponse response = send(RpcMethod.WRITE, null, handleId, offset, 0, data);
        return (int) response.bytesProcessed();
    }

    @Override
    public void close(String handleId) throws IOException {
        send(RpcMethod.CLOSE, null, handleId, 0, 0, null);
    }

    @Override
    public FileStat stat(String handleId) throws IOException {
        RPCResponse response = send(RpcMethod.STAT, null, handleId, 0, 0, null);
        RPCResponse.RPCStat rpcStat = response.stat();
        return new FileStat(rpcStat.size(), rpcStat.isDir(), rpcStat.lastModified());
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        failPending(new IOException("client closed"));
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        if (readerThread != null) {
            readerThread.interrupt();
        }
    }

    private String nextId() {
        return UUID.randomUUID().toString();
    }
}
