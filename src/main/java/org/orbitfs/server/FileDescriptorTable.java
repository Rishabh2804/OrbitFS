package org.orbitfs.server;

import org.orbitfs.common.model.FileChannelHandle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry mapping client-provided handle IDs to {@link FileChannelHandle} instances.
 * Used by the server to track open file handles across concurrent virtual-thread requests.
 */
public class FileDescriptorTable {

    private final Map<String, FileChannelHandle> handles = new ConcurrentHashMap<>();

    /**
     * Opens a new handle for the given file path.
     *
     * @param path the file path
     * @return the unique handle ID
     */
    public String open(String path) {
        FileChannelHandle handle = new FileChannelHandle(path);
        handles.put(handle.getHandleId(), handle);
        return handle.getHandleId();
    }

    /**
     * Closes and removes a handle from the registry.
     *
     * @param handleId the handle ID to close
     * @return the removed handle, or {@code null} if not found
     */
    public FileChannelHandle close(String handleId) {
        return handles.remove(handleId);
    }

    /**
     * Retrieves a handle by its ID.
     *
     * @param handleId the handle ID
     * @return the handle
     * @throws FileChannelNotFoundException if the handle does not exist
     */
    public FileChannelHandle getHandle(String handleId) {
        if (!handles.containsKey(handleId)) {
            throw new FileChannelNotFoundException(handleId);
        }
        return handles.get(handleId);
    }

    /**
     * Returns the current number of open handles.
     */
    public int size() {
        return handles.size();
    }

    /**
     * Checks if a handle exists.
     */
    public boolean containsHandle(String handleId) {
        return handles.containsKey(handleId);
    }
}