package org.orbitfs.common.protocol;

/**
 * RPC methods understood by the server.
 */
public enum RpcMethod {
    PING,
    PONG,
    OPEN,
    READ,
    WRITE,
    CLOSE,
    STAT,
    FILE_WRITE
}
