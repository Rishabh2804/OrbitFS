package org.orbitfs.server;

import org.orbitfs.common.exception.OrbitFSException;

/**
 * Legacy — prefer {@link StorageIOException} / {@link HandleNotFoundException}.
 * Kept for compat, now extends OrbitFSException.
 */
public class StorageException extends OrbitFSException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
    public StorageException(String message) {
        super(message);
    }
}
