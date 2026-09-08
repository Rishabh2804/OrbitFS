package org.orbitfs.common.protocol;

/**
 * Request message sent from client to server.
 * Each request carries a unique {@code requestId} used to correlate the response.
 *
 * @param requestId  unique correlation id (UUID)
 * @param method     the RPC method to invoke
 * @param path       filesystem path (used by OPEN)
 * @param fd         file-descriptor handle id (used by READ/WRITE/CLOSE/STAT)
 * @param offset     byte offset into the file (used by READ/WRITE)
 * @param count      number of bytes to read (used by READ)
 * @param dataBase64 base64-encoded payload (used by WRITE)
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
