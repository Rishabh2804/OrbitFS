package org.orbitfs.server;

import org.orbitfs.common.model.FileStat;
import org.orbitfs.common.model.LockResult;
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
import java.net.SocketException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static org.orbitfs.common.OrbitCore.LOGGER;

/**
 * Server: accepts connections, dispatches RPC frames to StorageEngine.
 * Each connection handled by a virtual thread.
 */
public class OrbitServerImpl implements OrbitServer {

    private final int port;
    private final ExecutorService executor;
    private final StorageEngine engine;
    private final PathLockRegistry lockRegistry;

    /** Map of RpcMethod → handler. Extensible for future methods. */
    private final Map<RpcMethod, BiFunction<RPCRequest, StorageEngine, RPCResponse>> handlers;

    private volatile ServerSocket serverSocket;
    private final AtomicLong requestCount = new AtomicLong();

    public OrbitServerImpl(int port) {
        this(port, new StorageEngine(), new PathLockRegistry());
    }

    public OrbitServerImpl(int port, StorageEngine engine, PathLockRegistry lockRegistry) {
        this.port = port;
        this.engine = engine;
        this.lockRegistry = lockRegistry;
        this.executor = newVirtualThreadPerTaskExecutor();
        this.handlers = new HashMap<>();
        registerHandlers();
    }

    private void registerHandlers() {
        handlers.put(RpcMethod.PING, (req, eng) ->
                new RPCResponse(req.requestId(), RPCStatus.PONG, 0, 0, null, null, null));

        handlers.put(RpcMethod.OPEN, (req, eng) -> {
            String fd = withLock(req.path(), false, () -> eng.open(req.path()));
            return new RPCResponse(req.requestId(), RPCStatus.OK, 0, 0, fd, null, null);
        });

        handlers.put(RpcMethod.READ, (req, eng) -> {
            byte[] data = withLock(req.fd(), false, () -> eng.read(req.fd(), req.offset(), req.count()));
            String dataB64 = data.length > 0 ? Base64.getEncoder().encodeToString(data) : null;
            return new RPCResponse(req.requestId(), RPCStatus.OK, 0, data.length, null, dataB64, null);
        });

        handlers.put(RpcMethod.WRITE, (req, eng) -> {
            byte[] data = req.dataBase64() != null
                    ? Base64.getDecoder().decode(req.dataBase64())
                    : new byte[0];
            int written = withLock(req.fd(), true, () -> eng.write(req.fd(), req.offset(), data));
            return new RPCResponse(req.requestId(), RPCStatus.OK, 0, written, null, null, null);
        });

        handlers.put(RpcMethod.CLOSE, (req, eng) -> {
            withLock(req.fd(), true, () -> { eng.close(req.fd()); return null; });
            return new RPCResponse(req.requestId(), RPCStatus.OK, 0, 0, null, null, null);
        });

        handlers.put(RpcMethod.STAT, (req, eng) -> {
            FileStat stat = withLock(req.fd(), false, () -> eng.stat(req.fd()));
            RPCResponse.RPCStat rpcStat = new RPCResponse.RPCStat(
                    stat.size(), stat.isDirectory(), stat.lastModifiedMillis());
            return new RPCResponse(req.requestId(), RPCStatus.OK, 0, 0, null, null, rpcStat);
        });
    }

    /**
     * Acquires the appropriate lock for the path/handle, runs the operation,
     * then releases the lock. Read lock for read/stat, write lock for write/close/open.
     */
    private <T> T withLock(String handleOrPath, boolean write, java.util.concurrent.Callable<T> op) {
        if (lockRegistry == null || handleOrPath == null) {
            try {
                return op.call();
            } catch (Exception e) {
                if (e instanceof RuntimeException re) throw re;
                throw new RuntimeException(e);
            }
        }

        Path lockPath = extractPath(handleOrPath);
        LockResult lockResult = write ? lockRegistry.tryWriteLock(lockPath) : lockRegistry.tryReadLock(lockPath);
        try {
            return op.call();
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        } finally {
            if (write) lockRegistry.unlockWrite(lockPath);
            else lockRegistry.unlockRead(lockPath);
        }
    }

    /**
     * Extracts the file path from a handle id or path string.
     * Handle format: "{path}_{uuid}". Falls back to treating input as a path.
     */
    private Path extractPath(String handleOrPath) {
        // If this is a handleId (contains "_"), try to extract the path portion
        int underscoreIdx = handleOrPath.lastIndexOf("_");
        if (underscoreIdx > 0) {
            String pathPart = handleOrPath.substring(0, underscoreIdx);
            try {
                return Path.of(pathPart);
            } catch (Exception ignored) {}
        }
        return Path.of(handleOrPath);
    }

    @Override
    public void start() throws IOException {
        try (ServerSocket listeningSocket = new ServerSocket(port)) {
            serverSocket = listeningSocket;

            while (!listeningSocket.isClosed()) {
                Socket socket = listeningSocket.accept();
                executor.submit(() -> handleAcceptedConnection(socket));
            }
        } catch (SocketException e) {
            if (!isShuttingDown()) throw e;
        } finally {
            tearDown();
        }
    }

    private void handleAcceptedConnection(Socket socket) {
        try {
            handleConnection(socket);
        } catch (IOException ioe) {
            LOGGER.info("Connection closed or interrupted: {}", socket, ioe);
        }
    }

    @Override
    public void handleConnection(Socket socket) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        while (!socket.isClosed()) {
            RPCRequest request = FrameCodec.decodeRequest(in);
            RPCResponse response = dispatch(request);
            out.write(FrameCodec.encodeResponse(response));
            out.flush();
        }
    }

    private RPCResponse dispatch(RPCRequest request) {
        requestCount.incrementAndGet();
        try {
            BiFunction<RPCRequest, StorageEngine, RPCResponse> handler = handlers.get(request.method());
            if (handler == null) {
                return RPCResponse.error(request.requestId(), 2);
            }
            return handler.apply(request, engine);
        } catch (StorageException e) {
            return RPCResponse.error(request.requestId(), 1);
        }
    }

    @Override
    public void stop() throws IOException {
        ServerSocket current = serverSocket;
        if (current != null && !current.isClosed()) {
            try {
                current.close();
            } catch (IOException ignored) {
            } finally {
                LOGGER.info("Server socket closed.");
            }
        }
        tearDown();
    }

    private void tearDown() {
        serverSocket = null;
        executor.shutdown();
    }

    private boolean isShuttingDown() {
        ServerSocket current = serverSocket;
        return current == null || current.isClosed();
    }

    public long getRequestCount() {
        return requestCount.get();
    }
}
