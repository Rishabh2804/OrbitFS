package org.orbitfs.common.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;

/**
 * RPC response: requestId + status + method-specific fields.
 */
public record RPCResponse(
        @JsonProperty("requestId") String requestId,
        @JsonProperty("status") RPCStatus status,
        @JsonProperty("errorCode") int errorCode,
        @JsonProperty("bytesProcessed") long bytesProcessed,
        @JsonProperty("fd") String fd,
        @JsonProperty("dataBase64") String dataBase64,
        @JsonProperty("stat") RPCStat stat,
        @JsonProperty("listing") List<RPCEntry> listing) {

    @JsonCreator
    public RPCResponse {
    }

    public static RPCResponse error(String requestId, int errorCode) {
        return new RPCResponse(requestId, RPCStatus.ERROR, errorCode, 0, null, null, null, null);
    }

    public record RPCStat(
            @JsonProperty("size") long size,
            @JsonProperty("isDir") boolean isDir,
            @JsonProperty("lastModified") long lastModified,
            @JsonProperty("created") long created,
            @JsonProperty("extension") String extension,
            @JsonProperty("mimeType") String mimeType,
            @JsonProperty("permissions") String permissions,
            @JsonProperty("owner") String owner) {

        @JsonCreator
        public RPCStat {
        }
    }

    public record RPCEntry(
            @JsonProperty("name") String name,
            @JsonProperty("isDir") boolean isDir,
            @JsonProperty("size") long size,
            @JsonProperty("lastModified") long lastModified,
            @JsonProperty("extension") String extension,
            @JsonProperty("mimeType") String mimeType,
            @JsonProperty("permissions") String permissions,
            @JsonProperty("owner") String owner) {

        @JsonCreator
        public RPCEntry {
        }
    }
}
