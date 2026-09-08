package org.orbitfs.common.protocol;

/**
 * RPC request: requestId + method + method-specific fields.
 */
public record RPCRequest(
        String requestId,
        RpcMethod method,
        String path,
        String fd,
        long offset,
        int count,
        String dataBase64) {
}
