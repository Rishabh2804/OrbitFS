package org.orbitfs.client;

import org.orbitfs.common.model.FileStat;
import java.io.IOException;

/**
 * OFS-107 — Client proxy interface.
 * High-level file ops that hide TCP framing.
 */
public interface OrbitFSClient extends AutoCloseable {
    String open(String path) throws IOException;
    byte[] read(String handleId, long offset, int count) throws IOException;
    int write(String handleId, long offset, byte[] data) throws IOException;
    void close(String handleId) throws IOException;
    FileStat stat(String handleId) throws IOException;

    /**
     * List directory contents. Returns entry names.
     */
    java.util.List<String> list(String handleId) throws IOException;

    /**
     * List directory contents with stat info. Returns entries with name,
     * isDir, and size — avoids a separate stat() per entry.
     */
    java.util.List<org.orbitfs.common.protocol.RPCResponse.RPCEntry> listWithStat(String handleId) throws IOException;

    @Override void close() throws IOException;
}
