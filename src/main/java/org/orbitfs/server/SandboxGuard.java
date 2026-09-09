package org.orbitfs.server;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Validates that all file paths are confined within the virtual root.
 * Rejects path traversal attempts (../../) and paths outside root.
 */
public final class SandboxGuard {

    private final Path root;

    public SandboxGuard(String rootPath) {
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
    }

    public SandboxGuard(Path rootPath) {
        this.root = rootPath.toAbsolutePath().normalize();
    }

    /**
     * Resolves the given path within the sandbox.
     *
     * @param path the client-provided path
     * @return the resolved, validated absolute path
     * @throws SecurityException if the path escapes the sandbox
     */
    public Path resolve(String path) {
        if (path == null || path.isEmpty()) {
            throw new SecurityException("Empty path");
        }

        // Reject absolute paths that try to escape — resolve relative to root
        Path resolved = root.resolve(path).normalize();

        // Check the resolved path is within root
        if (!resolved.startsWith(root)) {
            throw new SecurityException("Path escape detected: " + path + " resolves outside sandbox root " + root);
        }

        // Reject hidden files (starting with .)
        Path relative = root.relativize(resolved);
        for (Path component : relative) {
            String name = component.toString();
            if (name.startsWith(".") && !name.equals(".") && !name.equals("..")) {
                throw new SecurityException("Access denied to hidden file/dir: " + name);
            }
        }

        return resolved;
    }

    public Path getRoot() {
        return root;
    }
}
