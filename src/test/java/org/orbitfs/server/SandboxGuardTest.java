package org.orbitfs.server;

import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SandboxGuardTest {

    @TempDir
    private static Path tmpDir;

    @Test
    void resolvesPathWithinRoot() {
        SandboxGuard guard = new SandboxGuard(tmpDir);
        Path resolved = guard.resolve("myfile.txt");
        assertTrue(resolved.startsWith(tmpDir));
    }

    @Test
    void rejectsPathTraversal() {
        SandboxGuard guard = new SandboxGuard(tmpDir);
        assertThrows(SecurityException.class, () -> guard.resolve("../../etc/passwd"));
        assertThrows(SecurityException.class, () -> guard.resolve("../"));
        assertThrows(SecurityException.class, () -> guard.resolve("subdir/../../.."));
    }

    @Test
    void rejectsHiddenFiles() {
        SandboxGuard guard = new SandboxGuard(tmpDir);
        assertThrows(SecurityException.class, () -> guard.resolve(".bashrc"));
        assertThrows(SecurityException.class, () -> guard.resolve("subdir/.hidden"));
    }

    @Test
    void allowsNonHiddenSubdirectories() {
        SandboxGuard guard = new SandboxGuard(tmpDir);
        assertDoesNotThrow(() -> guard.resolve("subdir/file.txt"));
    }

    @Test
    void acceptsEmptyPathAsRoot() {
        SandboxGuard guard = new SandboxGuard(tmpDir);
        Path resolved = guard.resolve("");
        assertEquals(tmpDir, resolved);
    }
}
