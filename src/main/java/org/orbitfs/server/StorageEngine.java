package org.orbitfs.server;

import org.orbitfs.common.model.FileStat;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * OFS-106 — Machine Coding Skeleton
 *
 * Core IO layer using {@link FileChannel} (not RandomAccessFile / FileInputStream).
 * Delegates handle tracking to {@link FileDescriptorTable}.
 *
 * <p><b>Your task:</b> implement the 7 methods below to make
 * {@code StorageEngineTest} green. Read the Javadoc + issue #15 constraints
 * before coding. Keep it thread-safe where noted.
 *
 * <p>Hints:
 * <ul>
 *   <li>Use {@code FileChannel.open(Path, CREATE, READ, WRITE)} for {@code open}</li>
 *   <li>Use {@code channel.read(ByteBuffer, position)} / {@code channel.write(ByteBuffer, position)} — absolute offsets, no implicit seek</li>
 *   <li>Wrap IOException in {@link StorageException}</li>
 *   <li>Handle registry: put handle on open, remove on close</li>
 * </ul>
 */
public class StorageEngine {

    private final FileDescriptorTable table;

    // Track channels — FileDescriptorTable currently holds only FileChannelHandle stubs;
    // keep channels here until table is extended (or extend table yourself if you prefer).
    private final Map<String, FileChannel> channels = new ConcurrentHashMap<>();

    public StorageEngine(FileDescriptorTable table) {
        this.table = table;
    }

    public StorageEngine() {
        this(new FileDescriptorTable());
    }

    /**
     * Opens or creates file at {@code path}, registers a handle, returns handleId.
     * Should be idempotent — second open on same path returns a *new* handleId.
     */
    public String open(String path) {
        // TODO: implement — open FileChannel, register via table.open(path), store channel, return handleId
        throw new UnsupportedOperationException("TODO: implement open");
    }

    /**
     * Reads up to {@code count} bytes at absolute {@code offset}.
     * Returns exact payload (fewer bytes at EOF). Empty array if offset >= size.
     */
    public byte[] read(String handleId, long offset, int count) {
        // TODO: implement — lookup channel, ByteBuffer.allocate(count), channel.read(buf, offset), return trimmed array
        throw new UnsupportedOperationException("TODO: implement read");
    }

    /**
     * Writes {@code data} at absolute {@code offset}. Returns bytes written.
     */
    public int write(String handleId, long offset, byte[] data) {
        // TODO: implement — channel.write(ByteBuffer.wrap(data), offset)
        throw new UnsupportedOperationException("TODO: implement write");
    }

    /**
     * Sets position cursor (for future relative ops). Returns new position.
     * Note: read/write above use absolute offsets — this is for seek semantics.
     */
    public long seek(String handleId, long offset) {
        // TODO: implement — channel.position(offset), return position
        throw new UnsupportedOperationException("TODO: implement seek");
    }

    /**
     * Returns metadata for the open handle's underlying file.
     */
    public FileStat stat(String handleId) {
        // TODO: implement — channel.size(), Files.isDirectory, Files.getLastModifiedTime
        throw new UnsupportedOperationException("TODO: implement stat");
    }

    /**
     * Returns file size (convenience alias for stat.size).
     */
    public long size(String handleId) {
        // TODO: implement — delegate to stat(handleId).size() or channel.size()
        throw new UnsupportedOperationException("TODO: implement size");
    }

    /**
     * Closes FileChannel and deregisters handle. Second close on same handle should throw StorageException.
     */
    public void close(String handleId) {
        // TODO: implement — channel.close(), channels.remove, table.close(handleId)
        throw new UnsupportedOperationException("TODO: implement close");
    }

    // Visible for testing
    FileChannel getChannel(String handleId) {
        return channels.get(handleId);
    }
}
