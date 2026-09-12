package org.orbitfs.server;

import org.orbitfs.common.model.FileChannelHandle;
import org.orbitfs.common.model.FileStat;

/**
 * OFS-106 — Machine Coding Skeleton
 * <p>
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

    public StorageEngine(FileDescriptorTable table) {
        this.table = table;
    }

    public StorageEngine() {
        this(new FileDescriptorTable());
    }

    /**
     * Opens or creates file at {@code path}, registers a handle, returns handleId.
     * Should be idempotent — second open on same path returns a *new* handleId.
     * Eager open — FileChannel opened in FileChannelHandle ctor, fail-fast on permission.
     */
    public String open(String path) {
        // FileChannelHandle ctor eagerly opens channel (CREATE, READ, WRITE) — single source, no duplicate map
        // ponytail: hardcoded CREATE,READ,WRITE — parameterize OpenOption... if callers need read-only semantics
        return table.open(path);
    }

    /**
     * Reads up to {@code count} bytes at absolute {@code offset}.
     * Returns exact payload (fewer bytes at EOF). Empty array if offset >= size.
     * Delegates to handle — I/O owned by FileChannelHandle.
     */
    public byte[] read(String handleId, long offset, int count) {
        return table.getHandle(handleId).read(offset, count);
    }

    /**
     * Writes {@code data} at absolute {@code offset}. Returns bytes written.
     * Delegates to handle.
     */
    public int write(String handleId, long offset, byte[] data) {
        return table.getHandle(handleId).write(offset, data);
    }

    /**
     * Sets position cursor (for future relative ops). Returns new position.
     * Delegates to handle.
     */
    public long seek(String handleId, long offset) {
        return table.getHandle(handleId).seek(offset);
    }

    /**
     * Returns metadata for the open handle's underlying file.
     * Uses channel.size() for accurate open-file size, not stale Files.size.
     */
    public FileStat stat(String handleId) {
        FileChannelHandle handle = table.getHandle(handleId);
        return FileStat.getFileStat(handle.getPath());
    }

    /**
     * Returns file size (convenience alias for stat.size).
     */
    public long size(String handleId) {
        return stat(handleId).size();
    }

    /**
     * Closes FileChannel and deregisters handle. Second close on same handle should throw StorageException.
     */
    public void close(String handleId) {
        FileChannelHandle handle = table.getHandle(handleId);
        try {
            handle.closeChannel();
        } finally {
            table.close(handleId);
        }
    }
}
