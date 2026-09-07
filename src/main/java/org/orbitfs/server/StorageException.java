package org.orbitfs.server;

/**
 * Wraps {@link java.io.IOException} from underlying {@link java.nio.channels.FileChannel} ops.
 * Keeps callers free from checked exceptions while preserving cause.
 */
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
    public StorageException(String message) {
        super(message);
    }
}
