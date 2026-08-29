package org.orbitfs.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jdk.jshell.spi.ExecutionControl.NotImplementedException;

import java.io.IOException;

public final class OrbitSerializer {  // org.orbitfs.common.protocol
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static String toJson(Object o) throws IOException { return MAPPER.writeValueAsString(o); }
    public static <T> T fromJson(String in, Class<T> type) throws IOException { return MAPPER.readValue(in, type); }
}
