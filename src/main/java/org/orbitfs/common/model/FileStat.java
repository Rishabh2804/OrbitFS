package org.orbitfs.common.model;

import org.orbitfs.server.StorageException;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Metadata for an open handle.
 * Returned by {@link org.orbitfs.server.StorageEngine#stat(String)}.
 *
 * @param size               file size in bytes
 * @param isDirectory        true if path is a directory
 * @param lastModifiedMillis last-modified epoch millis
 */
public record FileStat(
        long size,
        boolean isDirectory,
        long lastModifiedMillis
) {
    public static FileStat getFileStat(String path) {
        try {
            Path p = Path.of(path);
            boolean isDir = Files.isDirectory(p);
            long size = isDir ? 0L : Files.size(p);
            long lastMod = Files.getLastModifiedTime(p).toMillis();

            return new FileStat(size, isDir, lastMod);
        } catch (java.io.IOException e) {
            throw new StorageException("Stat failed: " + path, e);
        }
    }
}
