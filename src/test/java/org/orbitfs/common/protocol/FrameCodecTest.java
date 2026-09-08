package org.orbitfs.common.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class FrameCodecTest {

    private static final RPCRequest VALID_REQUEST = new RPCRequest(
            "a1b2c3d4-e5f6-7890-1234-56789abcdef0",
            RpcMethod.FILE_WRITE,
            "/data/logs/app.log",
            "fd-99812-uuid",
            1024,
            512,
            "SGVsbG8gV29ybGQgRGlzdHJpYnV0ZWQgRmlsZSBTeXN0ZW0="
    );

    private static final RPCResponse VALID_RESPONSE = new RPCResponse(
            "a1b2c3d4-e5f6-7890-1234-56789abcdef0",
            RPCStatus.OK,
            0,
            512,
            "fd-99812-uuid",
            null,
            new RPCResponse.RPCStat(4096, false, 1772323200000L)
    );

    @Test
    void encodeDecodeRoundTripRequest() {
        try {
            byte[] frame = FrameCodec.encode(VALID_REQUEST);
            RPCRequest decoded = FrameCodec.decode(new DataInputStream(new ByteArrayInputStream(frame)), RPCRequest.class);
            assertEquals(VALID_REQUEST, decoded);
        } catch (IOException e) {
            fail("IOException: " + e.getMessage());
        }
    }

    @Test
    void encodeDecodeRoundTripResponse() {
        try {
            byte[] frame = FrameCodec.encode(VALID_RESPONSE);
            RPCResponse decoded = FrameCodec.decode(new DataInputStream(new ByteArrayInputStream(frame)), RPCResponse.class);
            assertEquals(VALID_RESPONSE, decoded);
        } catch (IOException e) {
            fail("IOException: " + e.getMessage());
        }
    }

    @Test
    void corruptMagicThrows() {
        try {
            byte[] frame = FrameCodec.encode(VALID_REQUEST);
            frame[0] ^= 0xFF; // corrupt magic
            assertThrows(IOException.class, () -> FrameCodec.decode(new DataInputStream(new ByteArrayInputStream(frame)), RPCResponse.class));
        } catch (IOException e) {
            fail("IOException during encode: " + e.getMessage());
        }
    }

    @Test
    void truncatedPayloadThrows() {
        try {
            byte[] frame = FrameCodec.encode(VALID_REQUEST);
            byte[] truncated = java.util.Arrays.copyOf(frame, frame.length - 5);
            assertThrows(IOException.class, () -> FrameCodec.decode(new DataInputStream(new ByteArrayInputStream(truncated)), RPCResponse.class));
        } catch (IOException e) {
            fail("IOException during encode: " + e.getMessage());
        }
    }

    @Test
    void emptyPayloadThrows() {
        byte[] empty = new byte[] {(byte)0x4F, (byte)0x52, (byte)0x42, (byte)0x54, 0, 0, 0, 0}; // ORBT + length=0
        assertThrows(IOException.class, () -> FrameCodec.decode(new DataInputStream(new ByteArrayInputStream(empty)), RPCResponse.class));
    }
}