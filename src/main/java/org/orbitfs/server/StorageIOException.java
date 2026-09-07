package org.orbitfs.server;

import java.io.IOException;

/**
 * Wraps IOException from FileChannel — distinct from handle-not-found.
 */
public class StorageIOException extends StorageException {
    public StorageIOException(String op, String handleId, IOException cause) {
        super(op + " failed for handle " + handleId + ": " + cause.getMessage(), cause);
    }
    public StorageIOException(String path, IOException cause) {
        super("io failed: " + path + ": " + cause.getMessage(), cause);
    }
}
