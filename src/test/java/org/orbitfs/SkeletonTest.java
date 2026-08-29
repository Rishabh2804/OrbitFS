package org.orbitfs;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SkeletonTest {

    @Test
    void writesAndReads1KbThroughNio() throws Exception {
        byte[] payload = new byte[1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 256);
        }

        Path file = Files.createTempFile("orbitfs-skeleton", ".bin");
        Files.write(file, payload);
        byte[] readBack = Files.readAllBytes(file);

        assertArrayEquals(payload, readBack);
        Files.deleteIfExists(file);
    }
}