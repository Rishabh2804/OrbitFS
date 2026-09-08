package org.orbitfs.common.protocol;

/**
 * Wire status for {@link RPCResponse}.
 * Jackson serializes enums as their name() by default, so wire values stay "OK"/"ERROR"/"PONG".
 */
public enum RPCStatus {
    OK(1),
    ERROR(2),
    PONG(3);

    private final int statusCode;

    RPCStatus(int statusCode) {
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
