package org.orbitfs.common.protocol;

public record RPCResponse(
        String requestId,
        RPCStatus status,
        int errorCode,
        long bytesProcessed,
        String fd,
        String dataBase64,
        RPCStat stat) {

    public record RPCStat(long size, boolean isDir, long lastModified) {
    }
}