package org.orbitfs.server;

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
     * <p>Path traversal via {@code ..} is clamped to the root — extra leading
     * {@code ..} components that would escape above root are silently dropped,
     * so paths like {@code "Downloads/../.."} resolve to {@code root}.</p>
     *
     * <p>Absolute paths within the sandbox root are accepted directly.
     * Absolute paths outside root are rejected.</p>
     *
     * @param path the client-provided path
     * @return the resolved, validated absolute path
     * @throws SecurityException if an absolute path escapes the sandbox
     */
    public Path resolve(String path) {
        if (path == null || path.isEmpty()) {
            return root;
        }

        String normalized = path.replace('\\', '/').trim();
        if (normalized.equals("/") || normalized.isEmpty()) {
            return root;
        }

        // Handle absolute paths: within root is allowed, outside root is rejected
        if (normalized.startsWith("/")) {
            Path resolved = Path.of(normalized).normalize();
            if (resolved.startsWith(root)) {
                return resolved;
            }
            throw new SecurityException("Path escape detected: " + path + " is outside sandbox root " + root);
        }

        // Build a stack of path components relative to root, clamping .. to root
        var components = new java.util.ArrayDeque<String>();
        for (String part : normalized.split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!components.isEmpty()) {
                    components.removeLast();
                }
                continue;
            }
            components.add(part);
        }

        Path result = root;
        for (String component : components) {
            result = result.resolve(component);
        }
        return result;
    }

    public Path getRoot() {
        return root;
    }
}
