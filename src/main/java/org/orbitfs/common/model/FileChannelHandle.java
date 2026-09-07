package org.orbitfs.common.model;

import org.orbitfs.server.StorageIOException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.UUID;

import static java.nio.file.StandardOpenOption.*;

public class FileChannelHandle {

    private static final long DEFAULT_TTL = 60; // 60 seconds

    private final String path;
    private final String handleId;

    private volatile FileChannel fileChannel;
    public final HandleStats stats = new HandleStats();

    public FileChannelHandle(String path) {
        this.path = path;
        this.handleId = generateHandleId(path);

        this.fileChannel = getChannel();
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

    public void recordRead(long bytes) {
        stats.recordRead(bytes);
    }

    public void recordWrite(long bytes) {
        stats.recordWrite(bytes);
    }

    public void touch() {
        stats.touch();
    }

    /**
     * Returns a reference to the underlying FileChannel.
     * If the channel is not yet open, it will be opened.
     *
     * @return the FileChannel
     */
    public FileChannel getChannel() {
        // TODO : Revisit options, as we may want to open the channel with different options based on the use case.
        FileChannel channel = this.fileChannel;
        if (channel == null) {
            synchronized (this) {
                if (fileChannel == null) {
                    try {
                        fileChannel = FileChannel.open(Path.of(path), CREATE, READ, WRITE);
                    } catch (IOException e) {
                        throw new StorageIOException(path, e);
                    }
                }
                channel = fileChannel;
            }
        }
        return channel;
    }
}
