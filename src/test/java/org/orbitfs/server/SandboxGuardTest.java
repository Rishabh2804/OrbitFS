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
    void traversalClampsToRoot() {
        SandboxGuard guard = new SandboxGuard(tmpDir);
        assertEquals(tmpDir, guard.resolve("../"));
        assertEquals(tmpDir, guard.resolve("subdir/../../.."));
        assertEquals(tmpDir, guard.resolve("Downloads/../.."));
        assertEquals(tmpDir, guard.resolve("Downloads/../.."));
        assertEquals(tmpDir, guard.resolve("../../"));
        assertTrue(guard.resolve("../../etc/passwd").startsWith(tmpDir));
    }

    @Test
    void allowsHiddenFiles() {
        SandboxGuard guard = new SandboxGuard(tmpDir);
        assertDoesNotThrow(() -> guard.resolve(".bashrc"));
        assertDoesNotThrow(() -> guard.resolve("subdir/.hidden"));
        assertDoesNotThrow(() -> guard.resolve("..."));
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

    @Test
    void handlesExcessiveDoubleDots() {
        SandboxGuard guard = new SandboxGuard(tmpDir);
        assertEquals(tmpDir, guard.resolve("Downloads/.." + "/.." + "/.." + "/.."));
        assertEquals(tmpDir, guard.resolve("../.."));
        assertEquals(tmpDir, guard.resolve("../../../../../../../.."));
    }

    @Test
    void handlesMixedSeparators() {
        SandboxGuard guard = new SandboxGuard(tmpDir);
        Path resolved = guard.resolve("Downloads\\..\\subdir");
        assertTrue(resolved.startsWith(tmpDir));
    }

    @Test
    void handlesExcessiveSlashes() {
        SandboxGuard guard = new SandboxGuard(tmpDir);
        Path resolved = guard.resolve("Downloads//../../subdir");
        assertTrue(resolved.startsWith(tmpDir));
    }
}
