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
        // Empty path = root directory (common for initial listing)
        if (path == null || path.isEmpty()) {
            return root;
        }

        // Normalize separators
        String normalized = path.replace('\\', '/').trim();
        if (normalized.equals("/") || normalized.isEmpty()) {
            return root;
        }

        // Handle absolute paths — if within root, use directly
        Path pathObj = Path.of(normalized);
        if (pathObj.isAbsolute()) {
            Path resolved = pathObj.normalize();
            if (resolved.startsWith(root)) {
                return resolved;
            }
            throw new SecurityException("Path escape detected: " + path + " is outside sandbox root " + root);
        }

        // Relative path — resolve within root
        Path resolved = root.resolve(normalized).normalize();

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
