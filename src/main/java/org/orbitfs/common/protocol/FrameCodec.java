package org.orbitfs.common.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Length-prefixed frame codec.
 * Wire format: MAGIC(4) + LENGTH(4) + JSON payload.
 */
public final class FrameCodec {

    private static final int MAGIC = 0x4F524254; // "ORBT"
    private static final int HEADER_SIZE = 8;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new Jdk8Module());

    /** Max frame payload (JSON): 10MB */
    public static final int MAX_FRAME_SIZE = 10 * 1024 * 1024;

    private FrameCodec() {}

    public static byte[] encode(Object payload) throws IOException {
        if (payload == null) throw new IOException("payload is null");
        byte[] json = MAPPER.writeValueAsBytes(payload);
        return ByteBuffer.allocate(HEADER_SIZE + json.length)
                .putInt(MAGIC)
                .putInt(json.length)
                .put(json)
                .array();
    }

    public static <T> T decode(DataInputStream in, Class<T> type) throws IOException {
        int magic = in.readInt();
        if (magic != MAGIC) throw new IOException("bad magic: 0x" + Integer.toHexString(magic));
        int len = in.readInt();
        if (len <= 0) throw new IOException("bad length: " + len);
        if (len > MAX_FRAME_SIZE) throw new IOException("frame too large: " + len + " > " + MAX_FRAME_SIZE);
        byte[] body = in.readNBytes(len);
        if (body.length < len) throw new IOException("truncated payload: expected " + len + ", got " + body.length);
        return MAPPER.readValue(body, type);
    }

    public static byte[] encodeRequest(RPCRequest req) throws IOException { return encode(req); }
    public static byte[] encodeResponse(RPCResponse resp) throws IOException { return encode(resp); }
    public static RPCRequest decodeRequest(DataInputStream in) throws IOException { return decode(in, RPCRequest.class); }
    public static RPCResponse decodeResponse(DataInputStream in) throws IOException { return decode(in, RPCResponse.class); }
}
