package org.orbitfs.server;

/**
 * Thrown when a requested file channel handle does not exist in the registry.
 * Kept for compat — prefer {@link HandleNotFoundException}.
 */
public class FileChannelNotFoundException extends HandleNotFoundException {

    public FileChannelNotFoundException(String handleId) {
        super("File channel handle not found: " + handleId);
    }
}