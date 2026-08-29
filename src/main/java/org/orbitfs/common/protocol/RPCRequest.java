package org.orbitfs.common.protocol;

public record RPCRequest(
        String requestId,
        String method,
        String path,
        String fd,
        long offset,
        int count,
        String dataBase64) {
}