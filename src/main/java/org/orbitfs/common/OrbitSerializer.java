package org.orbitfs.common;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.orbitfs.common.OrbitCore.LOGGER;

public final class OrbitSerializer {  // org.orbitfs.common.protocol
    private static ObjectMapper MAPPER;
    private static ObjectMapper getMapper() {
        if (MAPPER == null) {
            MAPPER = new ObjectMapper();
        }
        return MAPPER;
    }

    public static String toJson(Object o) throws IOException {
        LOGGER.debug("Serializing object to JSON: " + o);
        return getMapper().writeValueAsString(o);
    }

    public static <T> T fromJson(String in, Class<T> type) throws IOException {
        LOGGER.debug("Deserializing JSON to object of type " + type.getName() + ": " + in);
        return getMapper().readValue(in, type);
    }
}
