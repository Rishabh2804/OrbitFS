package org.orbitfs.server;

import java.io.IOException;
import java.net.Socket;

public interface OrbitServer {

    void start() throws IOException;
    void handleConnection(Socket socket) throws IOException;

    void stop() throws Exception;
}
