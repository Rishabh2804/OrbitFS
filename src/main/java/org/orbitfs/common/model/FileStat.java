package org.orbitfs.common.model;

import org.orbitfs.server.StorageException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Metadata for an open handle.
 * Returned by {@link org.orbitfs.server.StorageEngine#stat(String)}.
 *
 * @param size               file size in bytes
 * @param isDirectory        true if path is a directory
 * @param lastModifiedMillis last-modified epoch millis
 * @param createdMillis      creation epoch millis
 * @param extension          file extension (without dot), or empty for directories
 * @param mimeType           detected MIME type
 * @param permissions        POSIX-style permission string (e.g., "rw-r--r--")
 * @param owner              file owner name
 */
public record FileStat(
        long size,
        boolean isDirectory,
        long lastModifiedMillis,
        long createdMillis,
        String extension,
        String mimeType,
        String permissions,
        String owner
) {
    public static FileStat getFileStat(String path) {
        try {
            Path p = Path.of(path);
            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
            boolean isDir = attrs.isDirectory();
            long size = isDir ? 0L : attrs.size();
            long lastMod = attrs.lastModifiedTime().toMillis();
            long created = attrs.creationTime().toMillis();

            String ext = isDir ? "" : getFileExtension(p.getFileName().toString());
            String mime = "application/octet-stream";
            try { mime = Files.probeContentType(p); } catch (Exception ignored) {}
            if (mime == null) mime = "application/octet-stream";
            String perms = getPermissions(p, isDir);
            String fileOwner = getOwner(p);

            return new FileStat(size, isDir, lastMod, created, ext, mime, perms, fileOwner);
        } catch (java.io.IOException e) {
            throw new StorageException("Stat failed: " + path, e);
        }
    }

    private static String getFileExtension(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0 && dot < name.length() - 1) ? name.substring(dot + 1).toLowerCase() : "";
    }

    private static String getPermissions(Path p, boolean isDir) {
        try {
            PosixFileAttributes attrs = Files.readAttributes(p, PosixFileAttributes.class);
            Set<PosixFilePermission> perms = attrs.permissions();
            return PosixFilePermissions.toString(perms);
        } catch (Exception e) {
            return isDir ? "drwxr-xr-x" : "-rw-r--r--";
        }
    }

    private static String getOwner(Path p) {
        try {
            return Files.getOwner(p).getName();
        } catch (Exception e) {
            return "";
        }
    }
}
