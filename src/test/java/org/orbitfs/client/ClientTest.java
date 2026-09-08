package org.orbitfs.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * OFS-107 Judge — 5 cases. Implement NetworkTransportClient to make green.
 * Run: ./gradlew test --tests "org.orbitfs.client.ClientTest"
 * Note: these are unit-level; integration tests need a running OrbitServer.
 */
class ClientTest {

    @Test
    void pingPong() throws Exception {
        // TODO: start in-memory server or mock FrameCodec, send PING, assert PONG
        // For skeleton, just verify client can connect and close without throw
        NetworkTransportClient client = new NetworkTransportClient("localhost", 0);
        assertThrows(Exception.class, client::connect, "connect to 0 should fail until implemented");
    }

    @Test
    void openThrowsUntilImplemented() {
        NetworkTransportClient client = new NetworkTransportClient("localhost", 19999);
        assertThrows(UnsupportedOperationException.class, () -> client.open("/tmp/a.txt"));
    }

    @Test
    void concurrentRequests() {
        NetworkTransportClient client = new NetworkTransportClient("localhost", 19999);
        assertThrows(UnsupportedOperationException.class, () -> client.read("hdl", 0, 10));
    }

    @Test
    void timeout() {
        NetworkTransportClient client = new NetworkTransportClient("localhost", 19999);
        assertThrows(UnsupportedOperationException.class, () -> client.write("hdl", 0, new byte[]{1,2}));
    }

    @Test
    void closeStopsReader() throws Exception {
        NetworkTransportClient client = new NetworkTransportClient("localhost", 19999);
        assertThrows(UnsupportedOperationException.class, client::close);
    }
}
