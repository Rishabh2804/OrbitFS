package org.orbitfs.common.protocol;

/**
 * RPC response: requestId + status + method-specific fields.
 */
public record RPCResponse(
        String requestId,
        RPCStatus status,
        int errorCode,
        long bytesProcessed,
        String fd,
        String dataBase64,
        RPCStat stat) {

    public static RPCResponse error(String requestId, int errorCode) {
        return new RPCResponse(requestId, RPCStatus.ERROR, errorCode, 0, null, null, null);
    }

    public record RPCStat(
            long size,
            boolean isDir,
            long lastModified) {
    }
}
