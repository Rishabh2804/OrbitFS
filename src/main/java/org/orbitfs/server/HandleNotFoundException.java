package org.orbitfs.server;

/**
 * HandleId not in FileDescriptorTable — clear domain signal.
 * Extends StorageException so legacy `assertThrows(StorageException)` still passes.
 */
public class HandleNotFoundException extends StorageException {
    public HandleNotFoundException(String handleId) {
        super("handle not found: " + handleId, null);
    }
}
