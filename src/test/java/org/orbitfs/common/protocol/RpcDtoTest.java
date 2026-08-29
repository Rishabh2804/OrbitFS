package org.orbitfs.common.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.orbitfs.common.OrbitSerializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RpcDtoTest {


    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requestRoundTripsLosslessly() throws Exception {
        RPCRequest in = new RPCRequest(
                "a1b2c3d4-e5f6-7890-1234-56789abcdef0",
                "FILE_WRITE",
                "/data/logs/app.log",
                "fd-99812-uuid",
                1024,
                512,
                "SGVsbG8gV29ybGQgRGlzdHJpYnV0ZWQgRmlsZSBTeXN0ZW0=");

        String json = OrbitSerializer.toJson(in);
        RPCRequest out = OrbitSerializer.fromJson(json, RPCRequest.class);

        assertEquals(in, out);
    }

    @Test
    void responseWithStatRoundTripsLosslessly() throws Exception {
        RPCResponse in = new RPCResponse(
                "a1b2c3d4-e5f6-7890-1234-56789abcdef0",
                RPCStatus.OK,
                0,
                512,
                "fd-99812-uuid",
                null,
                new RPCResponse.RPCStat(4096, false, 1772323200000L));

        String json = OrbitSerializer.toJson(in);
        RPCResponse out = OrbitSerializer.fromJson(json, RPCResponse.class);

        assertEquals(in, out);
    }

    @Test
    void nullablesSurviveRoundTrip() throws Exception {
        RPCResponse in = new RPCResponse(null, RPCStatus.ERROR, 1, 0, null, null, null);

        RPCResponse out = mapper.readValue(mapper.writeValueAsString(in), RPCResponse.class);

        assertEquals(in, out);
        assertNull(out.fd());
        assertNull(out.stat());
    }
}