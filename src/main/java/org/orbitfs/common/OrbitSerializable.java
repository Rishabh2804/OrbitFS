package org.orbitfs.common;

import jdk.jshell.spi.ExecutionControl.NotImplementedException;

public interface OrbitSerializable {

    public String serialize() throws Exception;

    public static OrbitSerializable deserialize(String data) throws Exception {
        throw new NotImplementedException("Deserialization not implemented yet.");
    }
}
