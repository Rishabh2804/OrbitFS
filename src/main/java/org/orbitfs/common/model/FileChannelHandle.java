package org.orbitfs.common.model;

import java.util.UUID;

public class FileChannelHandle {

    private static final long DEFAULT_TTL = 60; // 60 seconds

    private final String path;
    private final String handleId;

    private final HandleStats stats = new HandleStats();

    public FileChannelHandle(String path) {
        this.path = path;
        this.handleId = generateHandleId(path);
    }

    private String generateHandleId(String path) {
        // Generate a unique handle ID based on the file path and current timestamp
        UUID uuid = UUID.randomUUID();
        return path + "_" + uuid;
    }

    public String getPath() {
        return path;
    }

    public String getHandleId() {
        return handleId;
    }

    public void recordRead(long bytes) { stats.recordRead(bytes); }
    public void recordWrite(long bytes) { stats.recordWrite(bytes); }
    public void touch() { stats.touch(); }
}
