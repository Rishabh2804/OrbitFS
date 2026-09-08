package org.orbitfs.common.protocol;

/**
 * Fixed set of RPC methods understood by the server.
 * Jackson serializes enums as their {@code name()} string, so wire values stay "OPEN", "READ", etc.
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
