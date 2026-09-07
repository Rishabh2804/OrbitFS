package org.orbitfs.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.orbitfs.common.model.FileStat;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OFS-106 Judge — 5 cases. Implement StorageEngine to make these green.
 * Run: ./gradlew test --tests "org.orbitfs.server.StorageEngineTest"
 */
class StorageEngineTest {

    @TempDir Path tmp;
    StorageEngine engine;

    @BeforeEach
    void setUp() {
        engine = new StorageEngine();
    }

    @Test
    void openReadWriteClose() throws Exception {
        Path file = tmp.resolve("cycle.dat");
        String h = engine.open(file.toString());

        assertNotNull(h, "open should return handleId");
        assertTrue(h.length() > 5);

        byte[] payload = "hello-orbitfs".getBytes();
        int n = engine.write(h, 0, payload);
        assertEquals(payload.length, n);

        byte[] got = engine.read(h, 0, payload.length);
        assertArrayEquals(payload, got, "read back what was written");

        engine.close(h);
        assertThrows(StorageException.class, () -> engine.read(h, 0, 1),
            "read after close should throw StorageException");
    }

    @Test
    void readAtOffset() throws Exception {
        Path file = tmp.resolve("offset.dat");
        String h = engine.open(file.toString());

        engine.write(h, 100, "world".getBytes());
        byte[] got = engine.read(h, 100, 5);
        assertArrayEquals("world".getBytes(), got);

        byte[] gap = engine.read(h, 0, 5);
        // gap before offset should be zero-filled (FileChannel sparse)
        assertEquals(5, gap.length);

        engine.close(h);
    }

    @Test
    void statAfterWrite() throws Exception {
        Path file = tmp.resolve("stat.dat");
        String h = engine.open(file.toString());

        engine.write(h, 0, "12345".getBytes());
        FileStat st = engine.stat(h);

        assertEquals(5, st.size());
        assertFalse(st.isDirectory());
        assertTrue(st.lastModifiedMillis() > 0);

        // also via size() alias
        assertEquals(5, engine.size(h));

        engine.close(h);
    }

    @Test
    void closeReleasesHandle() throws Exception {
        Path file = tmp.resolve("close.dat");
        String h = engine.open(file.toString());
        engine.close(h);

        assertThrows(StorageException.class, () -> engine.close(h),
            "second close should throw StorageException");
        assertThrows(StorageException.class, () -> engine.stat(h),
            "stat after close should throw StorageException");
    }

    @Test
    void seekMovesPosition() throws Exception {
        Path file = tmp.resolve("seek.dat");
        String h = engine.open(file.toString());

        engine.write(h, 0, "abcdefghij".getBytes());
        long pos = engine.seek(h, 5);
        assertEquals(5, pos);

        // read from seeked position via absolute read should still work
        byte[] got = engine.read(h, pos, 3);
        assertArrayEquals("fgh".getBytes(), got);

        engine.close(h);
    }
}
