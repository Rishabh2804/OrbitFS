package org.orbitfs.common.model;

/**
 * Metadata for an open handle.
 * Returned by {@link org.orbitfs.server.StorageEngine#stat(String)}.
 *
 * @param size               file size in bytes
 * @param isDirectory        true if path is a directory
 * @param lastModifiedMillis last-modified epoch millis
 */
public record FileStat(long size, boolean isDirectory, long lastModifiedMillis) {}
