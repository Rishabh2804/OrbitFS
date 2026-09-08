package org.orbitfs.common.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Length-prefixed frame codec.
 * <p>
 * Wire format per frame:
 * <pre>
 *   +----------+----------+--------------------+
 *   |  MAGIC   |  LENGTH  |     payload (json) |
 *   |  4 bytes |  4 bytes |     LENGTH bytes   |
 *   +----------+----------+--------------------+
 * </p>
 * MAGIC = 0x4F524254 ("ORBT"), LENGTH is the payload size in bytes.
 * Payload is a Jackson-serialized {@link RPCRequest} or {@link RPCResponse}.
 */
public final class FrameCodec {

    private static final int MAGIC = 0x4F524254; // "ORBT"
    private static final int HEADER_SIZE = 8; // magic(4) + length(4)
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FrameCodec() {}

    /**
     * Encodes any Jackson-serializable payload into a single frame.
     *
     * @param payload object to serialize and frame
     * @return framed byte array (header + json payload)
     * @throws IOException if serialization fails
     */
    public static byte[] encode(Object payload) throws IOException {
        if (payload == null) {
            throw new IOException("payload is null");
        }
        byte[] json = MAPPER.writeValueAsBytes(payload);
        return ByteBuffer.allocate(HEADER_SIZE + json.length)
                .putInt(MAGIC)
                .putInt(json.length)
                .put(json)
                .array();
    }

    /**
     * Decodes a single frame from the stream into the given type.
     *
     * @param in   stream to read from (must be {@link DataInputStream}-buffered)
     * @param type target class
     * @param <T>  target type
     * @return deserialized payload
     * @throws IOException if the frame is corrupt, truncated, or deserialization fails
     */
    public static <T> T decode(DataInputStream in, Class<T> type) throws IOException {
        int magic = in.readInt();
        if (magic != MAGIC) {
            throw new IOException("bad magic: 0x" + Integer.toHexString(magic));
        }
        int len = in.readInt();
        if (len <= 0) {
            throw new IOException("bad length: " + len);
        }
        byte[] body = in.readNBytes(len);
        if (body.length < len) {
            throw new IOException("truncated payload: expected " + len + " bytes, got " + body.length);
        }
        return MAPPER.readValue(body, type);
    }

    /**
     * Typed convenience for encoding an {@link RPCRequest}.
     */
    public static byte[] encodeRequest(RPCRequest req) throws IOException {
        return encode(req);
    }

    /**
     * Typed convenience for encoding an {@link RPCResponse}.
     */
    public static byte[] encodeResponse(RPCResponse resp) throws IOException {
        return encode(resp);
    }

    /**
     * Typed convenience for decoding an {@link RPCRequest}.
     */
    public static RPCRequest decodeRequest(DataInputStream in) throws IOException {
        return decode(in, RPCRequest.class);
    }

    /**
     * Typed convenience for decoding an {@link RPCResponse}.
     */
    public static RPCResponse decodeResponse(DataInputStream in) throws IOException {
        return decode(in, RPCResponse.class);
    }
}
