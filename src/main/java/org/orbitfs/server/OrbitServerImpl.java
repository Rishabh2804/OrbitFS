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
    private final SandboxGuard sandbox;
    private final boolean hideHiddenFiles;

    /** Map of RpcMethod → handler. Extensible for future methods. */
    private final Map<RpcMethod, BiFunction<RPCRequest, StorageEngine, RPCResponse>> handlers;

    private volatile ServerSocket serverSocket;
    private final AtomicLong requestCount = new AtomicLong();

    public OrbitServerImpl(int port) {
        this(port, new StorageEngine(), new PathLockRegistry(), new SandboxGuard(System.getProperty("user.home")), false);
    }

    public OrbitServerImpl(int port, Path root) {
        this(port, new StorageEngine(), new PathLockRegistry(), new SandboxGuard(root), false);
    }

    public OrbitServerImpl(int port, StorageEngine engine, PathLockRegistry lockRegistry) {
        this(port, engine, lockRegistry, new SandboxGuard(System.getProperty("user.home")), false);
    }

    public OrbitServerImpl(int port, StorageEngine engine, PathLockRegistry lockRegistry, SandboxGuard sandbox) {
        this(port, engine, lockRegistry, sandbox, false);
    }

    public OrbitServerImpl(int port, Path root, boolean hideHiddenFiles) {
        this(port, new StorageEngine(), new PathLockRegistry(), new SandboxGuard(root), hideHiddenFiles);
    }

    public OrbitServerImpl(int port, StorageEngine engine, PathLockRegistry lockRegistry, SandboxGuard sandbox, boolean hideHiddenFiles) {
        this.port = port;
        this.engine = engine;
        this.lockRegistry = lockRegistry;
        this.sandbox = sandbox;
        this.hideHiddenFiles = hideHiddenFiles;
        this.executor = newVirtualThreadPerTaskExecutor();
        this.handlers = new HashMap<>();
        registerHandlers();
    }

    private void registerHandlers() {
        handlers.put(RpcMethod.PING, (req, eng) ->
                new RPCResponse(req.requestId(), RPCStatus.PONG, 0, 0, null, null, null, null));

        handlers.put(RpcMethod.OPEN, (req, eng) -> {
            try {
                java.nio.file.Path resolved = sandbox.resolve(req.path());
                String validatedPath = resolved.toString();
                String fd = withLock(validatedPath, false, () -> {
                    if (java.nio.file.Files.isDirectory(resolved)) {
                        return validatedPath;
                    }
                    return eng.open(validatedPath);
                });
                return new RPCResponse(req.requestId(), RPCStatus.OK, 0, 0, fd, null, null, null);
            } catch (SecurityException e) {
                LOGGER.warn("Sandbox violation on OPEN: {}", e.getMessage());
                return RPCResponse.error(req.requestId(), 2);
            } catch (StorageException e) {
                return RPCResponse.error(req.requestId(), 1);
            }
        });

        handlers.put(RpcMethod.READ, (req, eng) -> {
            byte[] data = withLock(req.fd(), false, () -> eng.read(req.fd(), req.offset(), req.count()));
            String dataB64 = data.length > 0 ? Base64.getEncoder().encodeToString(data) : null;
            return new RPCResponse(req.requestId(), RPCStatus.OK, 0, data.length, null, dataB64, null, null);
        });

        handlers.put(RpcMethod.WRITE, (req, eng) -> {
            byte[] data = req.dataBase64() != null
                    ? Base64.getDecoder().decode(req.dataBase64())
                    : new byte[0];
            int written = withLock(req.fd(), true, () -> eng.write(req.fd(), req.offset(), data));
            return new RPCResponse(req.requestId(), RPCStatus.OK, 0, written, null, null, null, null);
        });

        handlers.put(RpcMethod.CLOSE, (req, eng) -> {
            try {
                if (!java.nio.file.Files.isDirectory(java.nio.file.Path.of(req.fd()))) {
                    withLock(req.fd(), true, () -> { eng.close(req.fd()); return null; });
                }
            } catch (StorageException e) {
                return RPCResponse.error(req.requestId(), 1);
            }
            return new RPCResponse(req.requestId(), RPCStatus.OK, 0, 0, null, null, null, null);
        });

        handlers.put(RpcMethod.STAT, (req, eng) -> {
            FileStat stat;
            try {
                stat = withLock(req.fd(), false, () -> {
                    if (java.nio.file.Files.isDirectory(java.nio.file.Path.of(req.fd()))) {
                        long size = java.nio.file.Files.list(java.nio.file.Path.of(req.fd())).count();
                        long mod = java.nio.file.Files.getLastModifiedTime(java.nio.file.Path.of(req.fd())).toMillis();
                        return new FileStat(size, true, mod);
                    }
                    return eng.stat(req.fd());
                });
            } catch (StorageException e) {
                return RPCResponse.error(req.requestId(), 1);
            }
            RPCResponse.RPCStat rpcStat = new RPCResponse.RPCStat(
                    stat.size(), stat.isDirectory(), stat.lastModifiedMillis());
            return new RPCResponse(req.requestId(), RPCStatus.OK, 0, 0, null, null, rpcStat, null);
        });

        handlers.put(RpcMethod.DELETE, (req, eng) -> {
            try {
                java.nio.file.Path resolved = sandbox.resolve(req.path());
                java.nio.file.Files.deleteIfExists(resolved);
                return new RPCResponse(req.requestId(), RPCStatus.OK, 0, 0, null, null, null, null);
            } catch (SecurityException e) {
                LOGGER.warn("Sandbox violation on DELETE: {}", e.getMessage());
                return RPCResponse.error(req.requestId(), 2);
            } catch (java.io.IOException e) {
                LOGGER.warn("Delete failed for {}: {}", req.path(), e.getMessage());
                return RPCResponse.error(req.requestId(), 1);
            }
        });

        handlers.put(RpcMethod.RENAME, (req, eng) -> {
            String newPath = req.fd();
            if (newPath == null) {
                return RPCResponse.error(req.requestId(), 2);
            }
            try {
                java.nio.file.Path resolved = sandbox.resolve(req.path());
                java.nio.file.Path resolvedNew = sandbox.resolve(newPath);
                java.nio.file.Files.move(resolved, resolvedNew);
                return new RPCResponse(req.requestId(), RPCStatus.OK, 0, 0, null, null, null, null);
            } catch (SecurityException e) {
                LOGGER.warn("Sandbox violation on RENAME: {}", e.getMessage());
                return RPCResponse.error(req.requestId(), 2);
            } catch (java.io.IOException e) {
                LOGGER.warn("Rename failed for {}: {}", req.path(), e.getMessage());
                return RPCResponse.error(req.requestId(), 1);
            }
        });

        handlers.put(RpcMethod.LIST, (req, eng) -> {
            try {
                boolean showHidden = req.showHidden() != null ? req.showHidden() : !hideHiddenFiles;
                java.util.List<RPCResponse.RPCEntry> entries = withLock(req.fd(), false, () -> {
                    java.nio.file.Path dir = java.nio.file.Path.of(req.fd());
                    if (!java.nio.file.Files.isDirectory(dir)) {
                        return java.util.List.of();
                    }
                    return java.nio.file.Files.list(dir)
                            .filter(p -> showHidden || !p.getFileName().toString().startsWith("."))
                            .map(p -> new RPCResponse.RPCEntry(
                                    p.getFileName().toString(),
                                    java.nio.file.Files.isDirectory(p),
                                    p.toFile().length()))
                            .sorted((a, b) -> {
                                boolean ad = a.isDir(), bd = b.isDir();
                                if (ad && !bd) return -1;
                                if (!ad && bd) return 1;
                                return a.name().compareTo(b.name());
                            })
                            .toList();
                });
                return new RPCResponse(req.requestId(), RPCStatus.OK, 0, 0, null, null, null, entries);
            } catch (StorageException e) {
                return RPCResponse.error(req.requestId(), 1);
            }
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
            String msg = ioe.getMessage();
            if (msg != null && msg.contains("Socket closed")) {
                // Expected during shutdown
            } else if (ioe instanceof java.io.EOFException) {
                LOGGER.info("Client disconnected: {}", socket);
            } else {
                LOGGER.warn("Connection error from {}: {}", socket, msg);
            }
        }
    }

    @Override
    public void handleConnection(Socket socket) throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        while (!socket.isClosed()) {
            try {
                RPCRequest request = FrameCodec.decodeRequest(in);
                RPCResponse response = dispatch(request);
                out.write(FrameCodec.encodeResponse(response));
                out.flush();
            } catch (java.io.EOFException | java.net.SocketException e) {
                break;
            } catch (Exception e) {
                LOGGER.warn("Error handling request: {}", e.getMessage());
                break;
            }
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
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof StorageException) {
                return RPCResponse.error(request.requestId(), 1);
            }
            throw e;
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
