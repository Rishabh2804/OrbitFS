package org.orbitfs.common.protocol;

/**
 * Response message returned by the server.
 *
 * @param requestId     correlation id matching the originating {@link RPCRequest}
 * @param status        overall status (OK / ERROR / PONG)
 * @param errorCode     numeric error code, 0 when OK
 * @param bytesProcessed number of bytes read or written
 * @param fd            file-descriptor handle id returned by OPEN
 * @param dataBase64    base64-encoded payload (used by READ result)
 * @param stat          file metadata (used by STAT result)
 */
public record RPCResponse(
        String requestId,
        RPCStatus status,
        int errorCode,
        long bytesProcessed,
        String fd,
        String dataBase64,
        RPCStat stat) {

    /**
     * Convenience factory for an error response.
     */
    public static RPCResponse error(String requestId, int errorCode, String message) {
        return new RPCResponse(requestId, RPCStatus.ERROR, errorCode, 0, null, null, null);
    }

    /**
     * File metadata snapshot embedded in a STAT response.
     */
    public record RPCStat(
            long size,
            boolean isDir,
            long lastModified) {
    }
}
