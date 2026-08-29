package org.orbitfs.common.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class FrameCodec {

    private static final byte[] MAGIC = {(byte) 0x4F, (byte) 0x52, (byte) 0x42, (byte) 0x54}; // "ORBT"
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FrameCodec() {}

    public static byte[] encode(Object payload) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.write(MAGIC);
        byte[] json = MAPPER.writeValueAsBytes(payload);
        dos.writeInt(json.length); // big-endian
        dos.write(json);
        dos.flush();
        return baos.toByteArray();
    }

    public static <T> T decode(DataInputStream dis, Class<T> type) throws IOException {
        byte[] magic = new byte[4];
        dis.readFully(magic);
        if (!java.util.Arrays.equals(magic, MAGIC)) {
            throw new IOException("Invalid magic header: expected ORBT, got " + bytesToHex(magic));
        }
        int length = dis.readInt(); // big-endian
        if (length < 0) {
            throw new IOException("Invalid payload length: " + length);
        }
        byte[] payload = new byte[length];
        dis.readFully(payload);
        return MAPPER.readValue(payload, type);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}