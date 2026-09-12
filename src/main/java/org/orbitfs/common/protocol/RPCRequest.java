package org.orbitfs.common.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * RPC request: requestId + method + method-specific fields.
 */
public record RPCRequest(
        @JsonProperty("requestId") String requestId,
        @JsonProperty("method") RpcMethod method,
        @JsonProperty("path") String path,
        @JsonProperty("fd") String fd,
        @JsonProperty("offset") long offset,
        @JsonProperty("count") int count,
        @JsonProperty("dataBase64") String dataBase64,
        @JsonProperty("showHidden") Boolean showHidden) {

    @JsonCreator
    public RPCRequest {
    }
}
