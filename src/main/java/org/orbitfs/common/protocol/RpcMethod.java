package org.orbitfs.common.protocol;

/**
 * Fixed set of RPC methods — replaces Stringly-typed "OPEN"/"PING".
 * Jackson serializes enum as name() string, so wire stays "PING".
 */
public enum RpcMethod {
    PING, PONG,
    OPEN, READ, WRITE, CLOSE, STAT,
    FILE_WRITE // legacy from RpcDtoTest
}
