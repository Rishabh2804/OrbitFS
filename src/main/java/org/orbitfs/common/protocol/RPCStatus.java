package org.orbitfs.common.protocol;

public enum RPCStatus {
    OK(1),
    ERROR(2),
    PONG(3),
    ;

    private final int statusCode;

    RPCStatus(int statusCode) {
        this.statusCode = statusCode;
    }
}