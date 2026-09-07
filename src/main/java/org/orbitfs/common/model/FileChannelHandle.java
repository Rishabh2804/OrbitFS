package org.orbitfs.common.model;

import org.orbitfs.server.StorageIOException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.UUID;

import static java.nio.file.StandardOpenOption.*;

public class FileChannelHandle {

    private final String path;
    private final String handleId;

    private volatile FileChannel fileChannel;
    public final HandleStats stats = new HandleStats();

    public FileChannelHandle(String path) {
        this.path = path;
        this.handleId = generateHandleId(path);
        // ponytail: hardcoded CREATE,READ,WRITE — parameterize OpenOption... if read-only needed
        try {
            this.fileChannel = FileChannel.open(Path.of(path), CREATE, READ, WRITE);
        } catch (IOException e) {
            throw new StorageIOException(path, e);
        }
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

    // --- I/O owned by handle — StorageEngine just delegates ---
    public byte[] read(long offset, int count) {
        try {
            ByteBuffer buf = ByteBuffer.allocate(count);
            int n = getChannel().read(buf, offset);
            if (n == -1) return new byte[0];
            buf.flip();
            byte[] out = new byte[n];
            buf.get(out);
            recordRead(n);
            touch();
            return out;
        } catch (IOException e) {
            throw new StorageIOException("read", handleId, e);
        }
    }

    public int write(long offset, byte[] data) {
        try {
            int n = getChannel().write(ByteBuffer.wrap(data), offset);
            recordWrite(n);
            touch();
            return n;
        } catch (IOException e) {
            throw new StorageIOException("write", handleId, e);
        }
    }

    public long seek(long offset) {
        try {
            FileChannel ch = getChannel();
            ch.position(offset);
            touch();
            return ch.position();
        } catch (IOException e) {
            throw new StorageIOException("seek", handleId, e);
        }
    }

    public void closeChannel() {
        FileChannel ch = this.fileChannel;
        if (ch == null) return;
        synchronized (this) {
            ch = this.fileChannel;
            if (ch == null) return;
            try {
                ch.close();
            } catch (IOException e) {
                throw new StorageIOException("close", handleId, e);
            } finally {
                this.fileChannel = null;
            }
        }
    }
}
