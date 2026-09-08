package org.orbitfs.common.model;

/**
 * A dirty chunk ready to be flushed to the server.
 *
 * @param path        the file path
 * @param chunkIndex  the chunk (block) index within the file
 * @param data        the chunk bytes
 */
public record DirtyChunk(
        String path,
        long chunkIndex,
        byte[] data) {
}
